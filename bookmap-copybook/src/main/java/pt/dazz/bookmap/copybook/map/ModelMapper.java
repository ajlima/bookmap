package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
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
        String continuable;
        String validatable;
        final List<MemberModel> members = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        /** The names of the constants and boolean methods of level 88 items. */
        final Set<String> conditionNames = new HashSet<>();
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

        String uniqueConditionName(String name) {
            String unique = name;
            for (int n = 2; !conditionNames.add(unique); n++) {
                unique = name + n;
            }
            return unique;
        }

        ClassModel build() {
            return new ClassModel(name, kind, superclass, subTypes, members, continuable,
                validatable);
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
    /** The handler of the record that the mapper maps now, if the record is Continuable. */
    private String continuable;
    private final CountFieldResolver countFields;
    private final EmptyItemResolver emptyItems;
    private final ConditionMapper conditions;

    private ModelMapper(Copybook copybook, MappingOptions options) {
        this.sourceName = copybook.sourceName();
        this.options = options;
        this.names = new Names(options.stripPrefixes());
        this.index = new ItemIndex(copybook);
        this.redefines = new RedefinesResolver(this);
        this.countFields = new CountFieldResolver(this);
        this.emptyItems = new EmptyItemResolver(this);
        this.conditions = new ConditionMapper(this, copybook);
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
        mapper.dates = new DateHints(mapper);
        OperationHandlers handlers = new OperationHandlers(mapper, copybook.records());
        mapper.countFields.checkOptions();
        mapper.emptyItems.checkOptions(mapper.countFields);
        mapper.conditions.checkOptions();
        for (DataItem record : copybook.records()) {
            info(mapper, record);
            RecordLength.check(record, options.maxRecordLength(), mapper.diagnostics);
            ClassBuilder operation = mapper.newClass(mapper.names.className(record.name()),
                null, ClassKind.OPERATION);
            operation.continuable = handlers.continuable(record);
            operation.validatable = handlers.validatable(record);
            mapper.continuable = operation.continuable;
            Usage usage = record.usage() != null ? record.usage() : Usage.DISPLAY;
            mapper.walk(record.children(), operation,
                new Context(usage, Direction.INPUT_OUTPUT, null, List.of()));
        }
        mapper.redefines.checkSubTypeAreas();
        mapper.dates.checkUnused();
        mapper.countFields.checkUnmapped();
        mapper.emptyItems.checkUnmapped();
        BookModel model = new BookModel(BookModel.FORMAT_VERSION, copybook.sourceName(),
            mapper.classes.stream().map(ClassBuilder::build).toList(), mapper.conditions.enums);
        return new MapResult(mapper.diagnostics.hasErrors() ? null : model,
            mapper.diagnostics.all());
    }

    /**
     * Reports the level 88 items of an item that is not a field: a group or a skipped
     * {@code REDEFINES} item. The condition mapper handles the level 88 items of a field.
     */
    private static void info(ModelMapper mapper, DataItem item) {
        if (!item.isGroup() && item.redefines() == null) {
            return;
        }
        for (Condition condition : item.conditions()) {
            mapper.diagnostics.info(item.source(), item.line(), item.column(),
                "condition " + condition.name() + " is ignored");
        }
    }

    /** Creates a class with a unique name. The class keeps its place in source order. */
    ClassBuilder newClass(String base, String parent, ClassKind kind) {
        ClassBuilder type = new ClassBuilder(uniqueClassName(base, parent), kind);
        classes.add(type);
        return type;
    }

    /**
     * Gives a class or enum name that no other class or enum of the copybook has. A name that
     * is in use gets the parent name as a prefix, then a number.
     */
    String uniqueClassName(String base, String parent) {
        String name = base;
        if (classNames.contains(name.toLowerCase(Locale.ROOT)) && parent != null) {
            name = parent + base;
        }
        String unique = name;
        for (int n = 2; classNames.contains(unique.toLowerCase(Locale.ROOT)); n++) {
            unique = name + n;
        }
        classNames.add(unique.toLowerCase(Locale.ROOT));
        return unique;
    }

    /** Maps the items of a group into a class. */
    void walk(List<DataItem> items, ClassBuilder owner, Context context) {
        for (int i = 0; i < items.size(); i++) {
            DataItem item = items.get(i);
            info(this, item);
            if (item.redefines() != null) {
                redefines.redefiningItem(item, items, context.usage());
                continue;
            }
            List<DataItem> alternatives = RedefinesResolver.alternatives(item, items);
            DataItem previous = i > 0 ? items.get(i - 1) : null;
            if (item.isGroup() && !alternatives.isEmpty()) {
                redefines.area(item, previous, alternatives, items.subList(i + 1, items.size()),
                    owner, context);
            } else {
                item(item, previous, owner, context);
            }
        }
    }

    /**
     * Maps an item without a {@code REDEFINES} area: a collection, a group, or a field. The
     * previous item is the item just before it in the same group, or {@code null}.
     */
    void item(DataItem item, DataItem previous, ClassBuilder owner, Context context) {
        Usage usage = item.usage() != null ? item.usage() : context.usage();
        if (item.occurs() != null) {
            collection(item, previous, owner, context, usage);
        } else if (item.isGroup()) {
            walk(item.children(), owner, context.withGroup(item, usage));
        } else {
            field(item, owner, context, usage, null);
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
        if (picture.edited() != null && usage != Usage.DISPLAY) {
            error(item, item.name() + " has an edited picture and must have usage DISPLAY");
            return;
        }
        if (usage != Usage.DISPLAY && usage != Usage.COMP3
            && picture.length() > MAX_LONG_DIGITS) {
            error(item, item.name() + ": COMP supports at most 18 digits");
            return;
        }
        if (usage == Usage.COMP5) {
            warning(item, "COMP-5 on " + item.name() + " is mapped as COMP. BookMap reads all"
                + " binary values, but writes only values with at most " + picture.length()
                + " digits.");
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
        FieldMember field;
        if (date != null) {
            // A date or time field has no constants and no enum. Tell what is ignored.
            ignored(item, "has a date hint");
            field = new FieldMember(name, date.type(), owner.nextOrder(),
                PicText.of(picture, usage, item.sign()), context.direction(), item.name(), item.declaration(),
                date.pattern());
        } else if (picture.edited() != null) {
            // The VALUE of an edited item is text, and its conditions compare text.
            ignored(item, "has an edited picture");
            field = new FieldMember(name, javaType(picture, usage), owner.nextOrder(),
                PicText.of(picture, usage, item.sign()), context.direction(), item.name(),
                item.declaration(), null, null, List.of());
        } else {
            ConditionMapper.Decision decision = conditions.decide(item, owner,
                javaType(picture, usage));
            field = new FieldMember(name, decision.javaType(), owner.nextOrder(),
                PicText.of(picture, usage, item.sign()), context.direction(), item.name(), item.declaration(),
                decision.enumType(), decision.initialValue(), decision.conditions());
        }
        owner.members.add(field);
        placed.put(item, new Placed(owner, field, item, usage));
    }

    /** Tells that the conditions and the {@code VALUE} of an item are ignored, and why. */
    private void ignored(DataItem item, String reason) {
        item.conditions().forEach(condition -> diagnostics.info(item.source(),
            condition.line(), condition.column(), "condition " + condition.name()
                + " is ignored, because " + item.name() + " " + reason));
        if (item.value() != null) {
            diagnostics.info(item.source(), item.line(), item.column(), "the VALUE of "
                + item.name() + " is ignored, because " + item.name() + " " + reason);
        }
    }

    private static JavaType javaType(Picture picture, Usage usage) {
        if (!picture.numeric()) {
            return JavaType.STRING;
        }
        if (picture.scale() > 0 || picture.length() > MAX_LONG_DIGITS) {
            return JavaType.BIG_DECIMAL;
        }
        if (usage == Usage.COMP5) {
            // The type holds all values of the bytes, not only the digits of the picture.
            return switch (picture.byteLength(usage, null)) {
                case 2 -> JavaType.INT;
                case 4 -> picture.signed() ? JavaType.INT : JavaType.LONG;
                default -> picture.signed() ? JavaType.LONG : JavaType.BIG_DECIMAL;
            };
        }
        return picture.length() <= MAX_INT_DIGITS ? JavaType.INT : JavaType.LONG;
    }

    /**
     * Maps an {@code OCCURS} item to a collection and an item class. The previous item is the
     * item just before the table in the same group, or {@code null}.
     */
    private void collection(DataItem item, DataItem previous, ClassBuilder owner,
                            Context context, Usage usage) {
        if (context.occurs() != null) {
            error(item, "OCCURS " + item.name() + " is inside OCCURS " + context.occurs()
                + ". BookMap does not support nested collections. See issue #43.");
            return;
        }
        String dependsOn = null;
        DataItem linked = countFields.countOf(item);
        if (item.occurs().dependingOn() != null) {
            dependsOn = dependingOn(item, owner);
            if (dependsOn == null) {
                return;
            }
        } else if (linked != null) {
            dependsOn = countField(item, linked, "count field " + linked.name(), owner);
            if (dependsOn == null) {
                return;
            }
        } else {
            countFields.suggest(item, previous);
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
        if (continuable != null && context.direction() == Direction.INPUT_OUTPUT) {
            error(item, item.name() + " is an INPUT_OUTPUT collection. BookMap does not permit"
                + " it in a Continuable class. Put the table in the output alternative of a"
                + " REDEFINES area.");
        }
        boolean fixed = item.occurs().dependingOn() == null;
        EmptyItem emptyItem = emptyItems.fillOf(item);
        String emptyKey = emptyItems.keyOf(item, type);
        // An item class has only fields: a nested OCCURS is an error above.
        if (fixed && dependsOn == null && !emptyItems.hasKey(item) && type.members.stream()
            .anyMatch(member -> ((FieldMember) member).pic().endsWith(" COMP"))) {
            diagnostics.info(item.source(), item.line(), item.column(), item.name()
                + ": a COMP value of 0 is all LOW-VALUES and cannot be written."
                + " Link the count field with the option countFields, or the key item with"
                + " the option emptyKeys, if the program has one.");
        }
        owner.members.add(new CollectionMember(name, order, context.direction(), item.name(),
            item.declaration(), item.occurs().max(), fixed, dependsOn, type.name, emptyItem,
            emptyKey));
    }

    /** Resolves the {@code DEPENDING ON} item. Gives its field name, or {@code null}. */
    private String dependingOn(DataItem table, ClassBuilder owner) {
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
            case ItemIndex.Found found -> countField(table, found.item(),
                "DEPENDING ON item " + target, owner);
        };
    }

    /**
     * Checks the count item of a table. Gives the field name of the count item, or
     * {@code null} after an error.
     *
     * @param label the count item in the messages, for example {@code DEPENDING ON item N}
     */
    private String countField(DataItem table, DataItem countItem, String label,
                              ClassBuilder owner) {
        Placed count = placed.get(countItem);
        if (count == null || count.owner() != owner) {
            error(table, label + " must be in the same class, before the table");
            return null;
        }
        if (count.field().javaType() != JavaType.INT
            && count.field().javaType() != JavaType.LONG) {
            error(table, label + " must be an integer");
            return null;
        }
        if (countItem.picture().edited() != null) {
            error(table, label + " must not have an edited picture");
            return null;
        }
        int digits = countItem.picture().length();
        if (digits < MAX_INT_DIGITS && Math.pow(10, digits) - 1 < table.occurs().max()) {
            error(table, label + " has " + digits + " digits and cannot hold "
                + table.occurs().max());
            return null;
        }
        return count.field().name();
    }

    /**
     * Finds an item that an option names, by a bare or a qualified name. Reports an error if
     * no item or more than one item matches.
     *
     * @param kind the option in the messages, for example {@code direction decision}
     * @param name the name in the option
     * @return the item, or {@code null} after an error
     */
    DataItem findNamed(String kind, String name) {
        QualifiedName qualified;
        try {
            qualified = QualifiedName.parse(name);
        } catch (IllegalArgumentException e) {
            optionError(kind + ": " + e.getMessage());
            return null;
        }
        return switch (index.find(qualified)) {
            case ItemIndex.Found found -> found.item();
            case ItemIndex.NotFound notFound -> {
                optionError(kind + ": item " + name + " does not exist");
                yield null;
            }
            case ItemIndex.Ambiguous ambiguous -> {
                optionError(kind + ": item " + name + " is ambiguous. Qualify it: "
                    + String.join(", ", ambiguous.matches()));
                yield null;
            }
        };
    }

    /** Reports an error in an option. The error has the position of the copybook start. */
    void optionError(String message) {
        diagnostics.error(sourceName, 1, 1, message);
    }

    void error(DataItem item, String message) {
        diagnostics.error(item.source(), item.line(), item.column(), message);
    }

    void warning(DataItem item, String message) {
        diagnostics.warning(item.source(), item.line(), item.column(), message);
    }
}
