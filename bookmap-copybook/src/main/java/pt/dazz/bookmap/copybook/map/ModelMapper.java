package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.diag.Diagnostics;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.MemberModel;
import pt.dazz.bookmap.copybook.model.SubTypes;
import pt.dazz.bookmap.copybook.parse.Picture;

/**
 * Applies the BookMap rules to the data items of a copybook and gives the model.
 */
public final class ModelMapper {

    private static final int ORDER_STEP = 10;
    private static final int MAX_INT_DIGITS = 9;
    private static final int MAX_LONG_DIGITS = 18;

    /**
     * The state of a walk through a group.
     *
     * @param usage the inherited usage
     * @param direction the direction of the items
     * @param occurs the name of the enclosing {@code OCCURS} item, or {@code null}
     * @param groups the flattened groups in the current class, from outer to inner
     */
    record Context(Usage usage, Direction direction, String occurs, List<DataItem> groups) {

        Context withGroup(DataItem group, Usage groupUsage) {
            List<DataItem> inner = new ArrayList<>(groups);
            inner.add(group);
            return new Context(groupUsage, direction, occurs, List.copyOf(inner));
        }

        Context withDirection(Direction newDirection) {
            return new Context(usage, newDirection, occurs, groups);
        }
    }

    /** A class while the mapper fills it. */
    final class ClassBuilder {
        final String name;
        final ClassKind kind;
        String superclass;
        SubTypes subTypes;
        final List<MemberModel> members = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        int fillers;
        int order;

        ClassBuilder(String name, ClassKind kind) {
            this.name = name;
            this.kind = kind;
        }

        int nextOrder() {
            order += ORDER_STEP;
            return order;
        }

        String fieldName(String base, List<DataItem> groups) {
            String name = base;
            for (int i = groups.size() - 1; i >= 0 && used.contains(name); i--) {
                String group = names.fieldName(groups.get(i).name());
                name = group + Character.toUpperCase(base.charAt(0)) + base.substring(1);
            }
            String unique = name;
            for (int n = 2; used.contains(unique); n++) {
                unique = name + n;
            }
            used.add(unique);
            return unique;
        }

        ClassModel build() {
            return new ClassModel(name, kind, superclass, subTypes, members);
        }
    }

    /** A field and the class that holds it. */
    record Placed(ClassBuilder owner, FieldMember field, DataItem item, Usage usage) {
    }

    final String sourceName;
    final MappingOptions options;
    final Names names;
    final ItemIndex index;
    final Diagnostics diagnostics = new Diagnostics();
    final List<ClassBuilder> classes = new ArrayList<>();
    final Set<String> classNames = new HashSet<>();
    final Map<DataItem, Placed> placed = new IdentityHashMap<>();
    private final RedefinesResolver redefines;
    private DateHints dates;

    private ModelMapper(Copybook copybook, MappingOptions options) {
        this.sourceName = copybook.sourceName();
        this.options = options;
        this.names = new Names(options.stripPrefixes());
        this.index = new ItemIndex(copybook);
        this.redefines = new RedefinesResolver(this);
    }

    /**
     * Maps a copybook to a model.
     *
     * @param copybook the copybook
     * @param options the mapping options
     * @return the result
     */
    public static MapResult map(Copybook copybook, MappingOptions options) {
        ModelMapper mapper = new ModelMapper(copybook, options);
        mapper.redefines.checkDecisions();
        mapper.dates = new DateHints(mapper, mapper.redefines);
        for (DataItem record : copybook.records()) {
            info(mapper, record);
            ClassBuilder operation = mapper.newClass(mapper.names.className(record.name()),
                null, ClassKind.OPERATION);
            Usage usage = record.usage() != null ? record.usage() : Usage.DISPLAY;
            mapper.walk(record.children(), operation,
                new Context(usage, Direction.INPUT_OUTPUT, null, List.of()));
        }
        mapper.redefines.checkSubTypeAreas();
        mapper.dates.checkUnused();
        BookModel model = new BookModel(BookModel.FORMAT_VERSION, copybook.sourceName(),
            mapper.classes.stream().map(ClassBuilder::build).toList());
        return new MapResult(mapper.diagnostics.hasErrors() ? null : model,
            mapper.diagnostics.all());
    }

    private static void info(ModelMapper mapper, DataItem item) {
        for (Condition condition : item.conditions()) {
            mapper.diagnostics.info(item.source(), item.line(), item.column(),
                "condition " + condition.name() + " is ignored");
        }
    }

    /** Creates a class with a unique name. The class keeps its place in source order. */
    ClassBuilder newClass(String base, String parent, ClassKind kind) {
        String name = base;
        if (classNames.contains(name.toLowerCase(Locale.ROOT)) && parent != null) {
            name = parent + base;
        }
        String unique = name;
        for (int n = 2; classNames.contains(unique.toLowerCase(Locale.ROOT)); n++) {
            unique = name + n;
        }
        classNames.add(unique.toLowerCase(Locale.ROOT));
        ClassBuilder type = new ClassBuilder(unique, kind);
        classes.add(type);
        return type;
    }

    /** Maps the items of a group into a class. */
    void walk(List<DataItem> items, ClassBuilder owner, Context context) {
        for (int i = 0; i < items.size(); i++) {
            DataItem item = items.get(i);
            info(this, item);
            Usage usage = item.usage() != null ? item.usage() : context.usage();
            if (item.redefines() != null) {
                redefines.redefiningItem(item, items, context.usage());
                continue;
            }
            List<DataItem> alternatives = RedefinesResolver.alternatives(item, items);
            if (item.isGroup() && !alternatives.isEmpty()) {
                redefines.area(item, alternatives, items.subList(i + 1, items.size()), owner,
                    context);
            } else if (item.occurs() != null) {
                collection(item, owner, context, usage);
            } else if (item.isGroup()) {
                walk(item.children(), owner, context.withGroup(item, usage));
            } else {
                field(item, owner, context, usage, null);
            }
        }
    }

    /** Maps an elementary item to a field. */
    void field(DataItem item, ClassBuilder owner, Context context, Usage usage,
               String forcedName) {
        Picture picture = item.picture();
        if (!picture.numeric() && usage != Usage.DISPLAY) {
            error(item, item.name() + " has a text picture and must not have COMP or COMP-3");
            return;
        }
        if (usage == Usage.COMP && picture.length() > MAX_LONG_DIGITS) {
            error(item, item.name() + ": COMP supports at most 18 digits");
            return;
        }
        String name;
        if (forcedName != null) {
            name = owner.fieldName(forcedName, List.of());
        } else if (item.isFiller()) {
            name = owner.fieldName("filler" + ++owner.fillers, List.of());
        } else {
            name = owner.fieldName(names.fieldName(item.name()), context.groups());
        }
        DateHints.Applied date = dates.apply(item, usage);
        FieldMember field = new FieldMember(name,
            date != null ? date.type() : javaType(picture), owner.nextOrder(),
            PicText.of(picture, usage), context.direction(), item.name(), item.declaration(),
            date != null ? date.pattern() : null);
        owner.members.add(field);
        placed.put(item, new Placed(owner, field, item, usage));
    }

    private static JavaType javaType(Picture picture) {
        if (!picture.numeric()) {
            return JavaType.STRING;
        }
        if (picture.scale() > 0 || picture.length() > MAX_LONG_DIGITS) {
            return JavaType.BIG_DECIMAL;
        }
        return picture.length() <= MAX_INT_DIGITS ? JavaType.INT : JavaType.LONG;
    }

    /** Maps an {@code OCCURS} item to a collection and an item class. */
    private void collection(DataItem item, ClassBuilder owner, Context context, Usage usage) {
        if (context.occurs() != null) {
            error(item, "OCCURS " + item.name() + " is inside OCCURS " + context.occurs()
                + ". BookMap does not support nested collections.");
            return;
        }
        String dependsOn = null;
        if (item.occurs().dependingOn() != null) {
            dependsOn = countField(item, owner);
            if (dependsOn == null) {
                return;
            }
        }
        String name = item.isFiller()
            ? owner.fieldName("filler" + ++owner.fillers, List.of())
            : owner.fieldName(names.fieldName(item.name()), context.groups());
        int order = owner.nextOrder();
        ClassBuilder type = newClass(names.className(item.name()), owner.name, ClassKind.ITEM);
        Context inner = new Context(usage, Direction.INPUT_OUTPUT, item.name(), List.of());
        if (item.isGroup()) {
            walk(item.children(), type, inner);
        } else {
            field(item, type, inner, usage, "value");
        }
        boolean fixed = dependsOn == null;
        // An item class has only fields: a nested OCCURS is an error above.
        if (fixed && type.members.stream()
            .anyMatch(member -> ((FieldMember) member).pic().endsWith(" COMP"))) {
            diagnostics.info(item.source(), item.line(), item.column(), item.name()
                + ": a COMP value of 0 is all LOW-VALUES and cannot be written."
                + " Add dependsOn if the program has a count field.");
        }
        owner.members.add(new CollectionMember(name, order, context.direction(), item.name(),
            item.declaration(), item.occurs().max(), fixed, dependsOn, type.name));
    }

    /** Resolves the {@code DEPENDING ON} item. Gives its field name, or {@code null}. */
    private String countField(DataItem table, ClassBuilder owner) {
        String target = table.occurs().dependingOn().toString();
        return switch (index.find(table.occurs().dependingOn())) {
            case ItemIndex.NotFound notFound -> {
                error(table, "DEPENDING ON item " + target + " does not exist");
                yield null;
            }
            case ItemIndex.Ambiguous ambiguous -> {
                error(table, "DEPENDING ON item " + target + " is ambiguous. Qualify it: "
                    + String.join(", ", ambiguous.matches()));
                yield null;
            }
            case ItemIndex.Found found -> {
                Placed count = placed.get(found.item());
                if (count == null || count.owner() != owner) {
                    error(table, "DEPENDING ON item " + target
                        + " must be in the same class, before the table");
                    yield null;
                }
                if (count.field().javaType() != JavaType.INT
                    && count.field().javaType() != JavaType.LONG) {
                    error(table, "DEPENDING ON item " + target + " must be an integer");
                    yield null;
                }
                int digits = found.item().picture().length();
                if (digits < MAX_INT_DIGITS
                    && Math.pow(10, digits) - 1 < table.occurs().max()) {
                    error(table, "DEPENDING ON item " + target + " has " + digits
                        + " digits and cannot hold " + table.occurs().max());
                    yield null;
                }
                yield count.field().name();
            }
        };
    }

    void error(DataItem item, String message) {
        diagnostics.error(item.source(), item.line(), item.column(), message);
    }

    void warning(DataItem item, String message) {
        diagnostics.warning(item.source(), item.line(), item.column(), message);
    }
}
