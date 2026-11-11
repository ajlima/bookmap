package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

/**
 * Maps the {@code REDEFINES} items of a copybook. A group {@code REDEFINES} area becomes a
 * direction split or subtypes. All other {@code REDEFINES} items are skipped.
 */
final class RedefinesResolver {

    private final ModelMapper mapper;
    private final Map<DataItem, Direction> directions = new IdentityHashMap<>();
    private final Map<DataItem, SubTypeDecision> subTypes = new IdentityHashMap<>();
    /** The number of members of a base class at the end of its subtype area. */
    private final Map<ModelMapper.ClassBuilder, DataItem> subTypeAreas = new IdentityHashMap<>();
    private final Map<ModelMapper.ClassBuilder, Integer> subTypeEnds = new IdentityHashMap<>();

    RedefinesResolver(ModelMapper mapper) {
        this.mapper = mapper;
    }

    // ---- decisions ----

    /** Resolves the names in the direction and subtype decisions. */
    void checkDecisions() {
        mapper.options.directions().forEach((name, direction) -> {
            DataItem item = alternativeNamed("direction decision", name);
            if (item != null) {
                directions.put(item, direction);
            }
        });
        mapper.options.subTypes().forEach((name, decision) -> {
            DataItem item = alternativeNamed("subtype decision", name);
            if (item != null && subTypes.put(baseOf(item), decision) != null) {
                decisionError("subtype decision: two decisions name the REDEFINES area "
                    + baseOf(item).name());
            }
        });
    }

    /** Finds an alternative group by name, or reports an error. */
    private DataItem alternativeNamed(String kind, String name) {
        DataItem item = find(kind, name);
        if (item == null) {
            return null;
        }
        if (mapper.index.pathTo(item).stream().anyMatch(this::isAlternative)) {
            decisionError(kind + ": " + name + " is inside a REDEFINES alternative."
                + " Set the decision on the alternative group.");
            return null;
        }
        if (!isAlternative(item)) {
            decisionError(kind + ": " + name + " is not a REDEFINES alternative. Outside a"
                + " REDEFINES area, a direction would move the offsets of the items after it.");
            return null;
        }
        return item;
    }

    /** Finds an item by a bare or qualified name, or reports an error. */
    private DataItem find(String kind, String name) {
        QualifiedName qualified;
        try {
            qualified = QualifiedName.parse(name);
        } catch (IllegalArgumentException e) {
            decisionError(kind + ": " + e.getMessage());
            return null;
        }
        return switch (mapper.index.find(qualified)) {
            case ItemIndex.Found found -> found.item();
            case ItemIndex.NotFound notFound -> {
                decisionError(kind + ": item " + name + " does not exist");
                yield null;
            }
            case ItemIndex.Ambiguous ambiguous -> {
                decisionError(kind + ": item " + name + " is ambiguous. Qualify it: "
                    + String.join(", ", ambiguous.matches()));
                yield null;
            }
        };
    }

    private void decisionError(String message) {
        mapper.diagnostics.error(mapper.sourceName, 1, 1, message);
    }

    private List<DataItem> siblings(DataItem item) {
        List<DataItem> path = mapper.index.pathTo(item);
        return path.isEmpty() ? List.of(item) : path.get(path.size() - 1).children();
    }

    private boolean isAlternative(DataItem item) {
        if (!item.isGroup()) {
            return false;
        }
        DataItem base = baseOf(item);
        return base != null && base.isGroup()
            && (base != item || !alternatives(item, siblings(item)).isEmpty());
    }

    /** Gives the redefined item of an area, or the item itself. */
    private DataItem baseOf(DataItem item) {
        if (item.redefines() == null) {
            return item;
        }
        return siblings(item).stream()
            .filter(sibling -> sibling.name().equals(item.redefines().name()))
            .findFirst().orElse(null);
    }

    // ---- walk hooks ----

    /** Gives the later siblings that redefine an item. */
    static List<DataItem> alternatives(DataItem item, List<DataItem> siblings) {
        return siblings.stream().filter(sibling -> sibling.redefines() != null
            && sibling.redefines().name().equals(item.name())).toList();
    }

    /**
     * Handles an item with {@code REDEFINES}. An elementary item is skipped. A group is part of
     * the area of the item that it redefines.
     */
    void redefiningItem(DataItem item, List<DataItem> siblings) {
        DataItem base = siblings.stream()
            .filter(sibling -> sibling.name().equals(item.redefines().name()))
            .findFirst().orElse(null);
        if (!item.isGroup()) {
            mapper.warning(item, item.name() + " redefines " + item.redefines()
                + " and is skipped");
        } else if (base == null || !base.isGroup()) {
            mapper.error(item, item.name() + " redefines " + item.redefines()
                + ", which is not a group with the same parent");
        }
    }

    /** Maps a group {@code REDEFINES} area. */
    void area(DataItem base, List<DataItem> redefiners, List<DataItem> following,
              ModelMapper.ClassBuilder owner, ModelMapper.Context context) {
        List<DataItem> all = new ArrayList<>();
        all.add(base);
        all.addAll(redefiners);
        if (context.direction() != Direction.INPUT_OUTPUT || subTypeAreas.containsKey(owner)
            || owner.kind == ClassKind.SUBTYPE) {
            mapper.error(base, "the REDEFINES area " + base.name()
                + " is inside another REDEFINES area. BookMap does not support this.");
            return;
        }
        for (DataItem alternative : all) {
            if (alternative.occurs() != null) {
                mapper.error(alternative, "the REDEFINES alternative " + alternative.name()
                    + " has OCCURS. BookMap does not support this.");
                return;
            }
        }
        List<DataItem> rest = following.stream().filter(item -> !contains(all, item)).toList();
        SubTypeDecision decision = subTypes.get(base);
        if (decision != null) {
            subTypes(base, all, decision, owner, context);
            return;
        }
        List<Direction> resolved = all.stream().map(this::direction).toList();
        if (all.size() == 2 && resolved.contains(Direction.INPUT)
            && resolved.contains(Direction.OUTPUT)) {
            if (context.occurs() != null) {
                mapper.error(base, "the REDEFINES area " + base.name() + " is inside OCCURS "
                    + context.occurs() + ". A direction split is not possible in an item class."
                    + " Use subtypes, or edit the class by hand.");
                return;
            }
            split(all, resolved, rest, owner, context);
            return;
        }
        String first = all.get(0).name();
        String second = all.get(1).name();
        mapper.error(base, "REDEFINES area " + first + " at line " + base.line()
            + " needs a decision. Add a direction for each alternative, for example"
            + " \"directions\": {\"" + first + "\": \"INPUT\", \"" + second + "\": \"OUTPUT\"}."
            + " Or add a subtype decision, for example \"subTypes\": {\"" + first
            + "\": {\"discriminator\": \"...\", \"values\": {\"" + first + "\": \"01\", \""
            + second + "\": \"02\"}}}.");
    }

    /** Gives the direction of an alternative from a decision or a marker, or {@code null}. */
    private Direction direction(DataItem alternative) {
        Direction explicit = directions.get(alternative);
        if (explicit != null) {
            return explicit;
        }
        boolean input = false;
        boolean output = false;
        for (String part : alternative.name().split("-")) {
            input |= mapper.options.inputMarkers().contains(part);
            output |= mapper.options.outputMarkers().contains(part);
        }
        if (input && output) {
            mapper.warning(alternative, alternative.name()
                + " has an input marker and an output marker. Its direction is not clear.");
            return null;
        }
        return input ? Direction.INPUT : output ? Direction.OUTPUT : null;
    }

    private void split(List<DataItem> all, List<Direction> resolved, List<DataItem> rest,
                       ModelMapper.ClassBuilder owner, ModelMapper.Context context) {
        int[] lengths = all.stream().mapToInt(item -> length(item, context.usage())).toArray();
        int longest = Math.max(lengths[0], lengths[1]);
        for (int i = 0; i < all.size(); i++) {
            DataItem alternative = all.get(i);
            Usage usage = usageOf(alternative, context.usage());
            mapper.walk(alternative.children(), owner,
                context.withGroup(alternative, usage).withDirection(resolved.get(i)));
            if (!rest.isEmpty() && lengths[i] < longest) {
                int pad = longest - lengths[i];
                DataItem other = all.get(1 - i);
                owner.members.add(new FieldMember(
                    owner.fieldName("filler" + ++owner.fillers, List.of()), JavaType.STRING,
                    owner.nextOrder(), "X(" + pad + ")", resolved.get(i), DataItem.FILLER,
                    "FILLER PIC X(" + pad + "). Added to give " + alternative.name()
                        + " the length of " + other.name() + "."));
            }
        }
    }

    private void subTypes(DataItem base, List<DataItem> all, SubTypeDecision decision,
                          ModelMapper.ClassBuilder owner, ModelMapper.Context context) {
        String discriminator = discriminator(base, decision, owner);
        Map<DataItem, String> values = byAlternative("subtype value", base, all,
            decision.values());
        Map<DataItem, String> classNames = byAlternative("subtype class name", base, all,
            decision.classNames());
        for (DataItem alternative : all) {
            if (!values.containsKey(alternative)) {
                mapper.error(alternative, "the REDEFINES alternative " + alternative.name()
                    + " has no discriminator value");
            }
        }
        if (discriminator == null || values.size() != all.size()) {
            return;
        }
        List<SubTypeEntry> entries = new ArrayList<>();
        for (DataItem alternative : all) {
            String name = classNames.getOrDefault(alternative,
                mapper.names.className(alternative.name()));
            ModelMapper.ClassBuilder sub = mapper.newClass(name, owner.name, ClassKind.SUBTYPE);
            sub.superclass = owner.name;
            sub.order = owner.order;
            sub.used.addAll(owner.used);
            Usage usage = usageOf(alternative, context.usage());
            mapper.walk(alternative.children(), sub, context.withGroup(alternative, usage));
            entries.add(new SubTypeEntry(values.get(alternative), sub.name));
        }
        owner.subTypes = new SubTypes(discriminator, entries);
        subTypeAreas.put(owner, base);
        subTypeEnds.put(owner, owner.members.size());
    }

    /** Resolves the discriminator. Gives its field name, or {@code null} after an error. */
    private String discriminator(DataItem base, SubTypeDecision decision,
                                 ModelMapper.ClassBuilder owner) {
        String name = decision.discriminator();
        DataItem item = find("subtype decision for " + base.name(), name);
        if (item == null) {
            return null;
        }
        ModelMapper.Placed field = mapper.placed.get(item);
        if (field == null || field.owner() != owner) {
            mapper.error(base, "the discriminator " + name + " must be an item before the"
                + " REDEFINES area " + base.name() + ", in the same class");
            return null;
        }
        if (field.field().javaType() == JavaType.BIG_DECIMAL) {
            mapper.error(base, "the discriminator " + name + " must be text or an integer");
            return null;
        }
        return field.field().name();
    }

    /** Resolves names of alternatives in a decision map. */
    private Map<DataItem, String> byAlternative(String kind, DataItem base, List<DataItem> all,
                                                Map<String, String> byName) {
        Map<DataItem, String> resolved = new IdentityHashMap<>();
        byName.forEach((name, value) -> {
            DataItem item = find(kind, name);
            if (item != null && !contains(all, item)) {
                mapper.error(base, kind + ": " + name + " is not an alternative of the"
                    + " REDEFINES area " + base.name());
            } else if (item != null) {
                resolved.put(item, value);
            }
        });
        return resolved;
    }

    /** Reports members that come after a subtype area in its base class. */
    void checkSubTypeAreas() {
        subTypeAreas.forEach((owner, base) -> {
            int end = subTypeEnds.get(owner);
            if (owner.members.size() > end) {
                mapper.error(base, owner.members.get(end).cobolName() + " comes after the"
                    + " REDEFINES area " + base.name() + ". BookMap needs the redefined area at"
                    + " the end of the layout.");
            }
        });
    }

    private static boolean contains(List<DataItem> items, DataItem item) {
        return items.stream().anyMatch(candidate -> candidate == item);
    }

    // ---- lengths ----

    private static Usage usageOf(DataItem item, Usage inherited) {
        return item.usage() != null ? item.usage() : inherited;
    }

    /** Gives the number of bytes of an item with the BookMap rules. */
    static int length(DataItem item, Usage inherited) {
        Usage usage = usageOf(item, inherited);
        int single = item.isGroup() ? groupLength(item.children(), usage)
            : PicText.byteLength(item.picture(), usage);
        return item.occurs() == null ? single : single * item.occurs().max();
    }

    private static int groupLength(List<DataItem> children, Usage usage) {
        int total = 0;
        for (DataItem child : children) {
            if (child.redefines() == null) {
                int longest = length(child, usage);
                for (DataItem alternative : alternatives(child, children)) {
                    longest = Math.max(longest, length(alternative, usage));
                }
                total += longest;
            }
        }
        return total;
    }
}
