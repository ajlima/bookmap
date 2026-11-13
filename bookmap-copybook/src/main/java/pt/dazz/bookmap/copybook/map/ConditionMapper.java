package pt.dazz.bookmap.copybook.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.ast.ConditionValue;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Literal;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
import pt.dazz.bookmap.copybook.model.ConditionMember;
import pt.dazz.bookmap.copybook.model.EnumConstant;
import pt.dazz.bookmap.copybook.model.EnumModel;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.ValueRange;
import pt.dazz.bookmap.copybook.parse.Picture;

/**
 * Maps the level 88 items and the {@code VALUE} clause of an elementary item: an enum,
 * constants and boolean methods, and the initial value of the field.
 */
final class ConditionMapper {

    /**
     * Gives the mapping of one elementary item.
     *
     * @param javaType the Java type of the field
     * @param enumType the enum name, or {@code null}
     * @param initialValue the initial value, or {@code null}
     * @param conditions the constants and boolean methods
     */
    record Decision(JavaType javaType, String enumType, String initialValue,
                    List<ConditionMember> conditions) {
    }

    /** One value of a field, or the problem that prevents it. */
    private record Value(String value, String problem) {
    }

    private final ModelMapper mapper;
    final List<EnumModel> enums = new ArrayList<>();
    private final Map<DataItem, ConditionMode> modes = new IdentityHashMap<>();
    /** The items that must keep their Java type: discriminators and count items. */
    private final Set<DataItem> restricted = Collections.newSetFromMap(new IdentityHashMap<>());

    ConditionMapper(ModelMapper mapper, Copybook copybook) {
        this.mapper = mapper;
        mapper.options.subTypes().values().forEach(decision -> {
            try {
                restrict(QualifiedName.parse(decision.discriminator()));
            } catch (IllegalArgumentException e) {
                // The REDEFINES resolver reports the name.
            }
        });
        copybook.records().forEach(this::restrictCounts);
    }

    private void restrictCounts(DataItem item) {
        if (item.occurs() != null && item.occurs().dependingOn() != null) {
            restrict(item.occurs().dependingOn());
        }
        item.children().forEach(this::restrictCounts);
    }

    private void restrict(QualifiedName name) {
        if (mapper.index.find(name) instanceof ItemIndex.Found found) {
            restricted.add(found.item());
        }
    }

    /** Resolves the names of the {@code conditions} option. */
    void checkOptions() {
        mapper.options.conditions().forEach((name, mode) -> {
            DataItem item = mapper.redefines.find("condition mode", name);
            if (item != null && (item.isGroup() || item.conditions().isEmpty())) {
                mapper.diagnostics.error(mapper.sourceName, 1, 1, "condition mode: " + name
                    + " is not an elementary item with level 88 items");
            } else if (item != null) {
                modes.put(item, mode);
            }
        });
    }

    /**
     * Maps the level 88 items and the {@code VALUE} clause of an elementary item.
     *
     * @param item the item
     * @param owner the class of the field
     * @param type the Java type without level 88 items
     * @return the decision
     */
    Decision decide(DataItem item, ModelMapper.ClassBuilder owner, JavaType type) {
        Value initial = item.value() == null ? null : value(item.value(), item.picture());
        if (initial != null && initial.problem() != null) {
            mapper.warning(item, "the VALUE " + initial.value() + " of " + item.name() + " "
                + initial.problem()
                + ". The field has no initial value.");
            initial = null;
        }
        String initialValue = initial == null ? null : initial.value();
        if (item.conditions().isEmpty()) {
            return new Decision(type, null, initialValue, List.of());
        }
        ConditionMode explicit = modes.get(item);
        List<List<ValueRange>> values = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Condition condition : item.conditions()) {
            List<ValueRange> ranges = new ArrayList<>();
            for (ConditionValue range : condition.values()) {
                Value from = value(range.from(), item.picture());
                Value thru = range.thru() == null ? null : value(range.thru(), item.picture());
                for (Value bound : thru == null ? List.of(from) : List.of(from, thru)) {
                    if (bound.problem() != null) {
                        problems.add("condition " + condition.name() + ": the value "
                            + bound.value() + " " + bound.problem());
                    }
                }
                ranges.add(new ValueRange(from.value(), thru == null ? null : thru.value()));
            }
            values.add(ranges);
        }
        String blocking = blocking(item, values, problems);
        ConditionMode mode = explicit;
        if (explicit == ConditionMode.ENUM && blocking != null) {
            mapper.error(item, item.name() + ": the level 88 items cannot be an enum: "
                + blocking);
            return new Decision(type, null, initialValue, List.of());
        }
        if (explicit == null) {
            String reason = blocking != null ? blocking : item.conditions().size() < 2
                ? "it has only one level 88 item"
                : values.stream().flatMap(List::stream).anyMatch(range -> range.thru() != null)
                    ? "a level 88 item has a THRU range" : null;
            mode = reason == null ? ConditionMode.ENUM : ConditionMode.CONSTANTS;
            if (reason != null) {
                mapper.diagnostics.info(item.source(), item.line(), item.column(), item.name()
                    + ": the level 88 items give constants, not an enum, because " + reason
                    + ". Set the condition mode of " + item.name() + " to change this.");
            }
        }
        return switch (mode) {
            case ENUM -> enumDecision(item, owner, values, initialValue);
            case CONSTANTS -> new Decision(type, null, initialValue,
                constants(item, owner, values, problems));
            case NONE -> {
                item.conditions().forEach(condition -> mapper.diagnostics.info(item.source(),
                    condition.line(), condition.column(),
                    "condition " + condition.name() + " is ignored"));
                yield new Decision(type, null, initialValue, List.of());
            }
        };
    }

    /** Gives the reason why the level 88 items cannot be an enum, or {@code null}. */
    private String blocking(DataItem item, List<List<ValueRange>> values,
                            List<String> problems) {
        if (item.isFiller()) {
            return "the item is a FILLER";
        }
        if (restricted.contains(item)) {
            return "the item is a discriminator or a DEPENDING ON item";
        }
        if (!problems.isEmpty()) {
            return problems.get(0);
        }
        return overlap(item, values);
    }

    /** Gives the first value that two level 88 items share, or {@code null}. */
    private String overlap(DataItem item, List<List<ValueRange>> values) {
        boolean numeric = item.picture().numeric();
        for (int i = 0; i < values.size(); i++) {
            for (int j = i + 1; j < values.size(); j++) {
                for (ValueRange first : values.get(i)) {
                    for (ValueRange second : values.get(j)) {
                        if (compare(first.from(), high(second), numeric) <= 0
                            && compare(second.from(), high(first), numeric) <= 0) {
                            return "the level 88 items " + item.conditions().get(i).name()
                                + " and " + item.conditions().get(j).name()
                                + " share a value";
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String high(ValueRange range) {
        return range.thru() == null ? range.from() : range.thru();
    }

    private static int compare(String left, String right, boolean numeric) {
        return numeric ? new BigDecimal(left).compareTo(new BigDecimal(right))
            : left.compareTo(right);
    }

    private Decision enumDecision(DataItem item, ModelMapper.ClassBuilder owner,
                                  List<List<ValueRange>> values, String initialValue) {
        String name = mapper.uniqueClassName(mapper.names.className(item.name()), owner.name);
        List<String> names = constantNames(item.conditions());
        List<EnumConstant> constants = new ArrayList<>();
        String initialConstant = null;
        boolean numeric = item.picture().numeric();
        for (int i = 0; i < names.size(); i++) {
            constants.add(new EnumConstant(names.get(i), item.conditions().get(i).name(),
                values.get(i)));
            for (ValueRange range : values.get(i)) {
                if (initialValue != null && initialConstant == null
                    && compare(range.from(), initialValue, numeric) <= 0
                    && compare(initialValue, high(range), numeric) <= 0) {
                    initialConstant = names.get(i);
                }
            }
        }
        if (initialValue != null && initialConstant == null) {
            mapper.warning(item, "the VALUE of " + item.name()
                + " is not a value of a level 88 item. The field has no initial value.");
        }
        enums.add(new EnumModel(name, item.name(), constants));
        return new Decision(JavaType.ENUM, name, initialConstant, List.of());
    }

    /**
     * Gives the constant names: the condition names without the longest common prefix of
     * hyphen-separated parts. The full names stay if a short name is empty, starts with a
     * digit, or is not unique.
     */
    static List<String> constantNames(List<Condition> conditions) {
        List<String[]> parts = conditions.stream().map(c -> c.name().split("-")).toList();
        int common = parts.stream().mapToInt(p -> p.length - 1).min().orElseThrow();
        for (int i = 0; i < common; i++) {
            int index = i;
            if (parts.stream().map(p -> p[index]).distinct().count() > 1) {
                common = i;
            }
        }
        int skip = conditions.size() < 2 ? 0 : common;
        List<String> names = parts.stream()
            .map(p -> String.join("_", List.of(p).subList(skip, p.length))).toList();
        if (new HashSet<>(names).size() < names.size()
            || names.stream().anyMatch(name -> Character.isDigit(name.charAt(0)))) {
            names = parts.stream().map(p -> String.join("_", p)).toList();
        }
        Set<String> used = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String name : names) {
            String base = Character.isDigit(name.charAt(0)) ? "VALUE_" + name : name;
            String candidate = base;
            for (int n = 2; !used.add(candidate); n++) {
                candidate = base + n;
            }
            unique.add(candidate);
        }
        return unique;
    }

    private List<ConditionMember> constants(DataItem item, ModelMapper.ClassBuilder owner,
                                            List<List<ValueRange>> values,
                                            List<String> problems) {
        List<ConditionMember> members = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Condition condition = item.conditions().get(i);
            String prefix = "condition " + condition.name() + ":";
            List<String> own = problems.stream().filter(p -> p.startsWith(prefix)).toList();
            if (!own.isEmpty()) {
                mapper.diagnostics.warning(item.source(), condition.line(), condition.column(),
                    own.get(0) + ". The condition has no constant.");
                continue;
            }
            String field = mapper.names.fieldName(condition.name());
            String constant = owner.uniqueConditionName(upperSnake(field));
            String method = owner.uniqueConditionName("is"
                + Character.toUpperCase(field.charAt(0)) + field.substring(1));
            members.add(new ConditionMember(condition.name(), constant, method, values.get(i)));
        }
        return members;
    }

    private static String upperSnake(String camel) {
        return camel.chars().mapToObj(c -> Character.isUpperCase(c)
                ? "_" + (char) c : String.valueOf((char) c))
            .collect(Collectors.joining()).toUpperCase(Locale.ROOT);
    }

    /** Converts a literal to a value of the item, with the rules of the design. */
    private static Value value(Literal literal, Picture picture) {
        String shown = literal.kind() == Literal.Kind.TEXT ? "'" + literal.text() + "'"
            : literal.text();
        if (literal.kind() == Literal.Kind.FIGURATIVE) {
            return switch (literal.text()) {
                case "SPACE" -> picture.numeric()
                    ? new Value(shown, "is not valid for a numeric item")
                    : new Value("", null);
                case "ZERO" -> new Value(picture.numeric() ? "0"
                    : "0".repeat(picture.length()), null);
                default -> new Value(shown, "is not supported");
            };
        }
        if (picture.numeric() != (literal.kind() == Literal.Kind.NUMBER)) {
            return new Value(shown, picture.numeric() ? "is text, but the item is numeric"
                : "is a number, but the item is text");
        }
        if (!picture.numeric()) {
            String text = literal.text().stripTrailing();
            return text.length() > picture.length()
                ? new Value(shown, "is longer than " + picture.length() + " characters")
                : new Value(text, null);
        }
        BigDecimal number = new BigDecimal(literal.text());
        if (number.signum() < 0 && !picture.signed()) {
            return new Value(shown, "is negative, but the item has no sign");
        }
        if (number.stripTrailingZeros().scale() > picture.scale()) {
            return new Value(shown, "has more than " + picture.scale() + " decimal places");
        }
        if (number.setScale(picture.scale(), RoundingMode.UNNECESSARY).unscaledValue().abs()
            .toString().length() > picture.length()) {
            return new Value(shown, "has more than " + picture.length() + " digits");
        }
        return new Value(number.toPlainString(), null);
    }
}
