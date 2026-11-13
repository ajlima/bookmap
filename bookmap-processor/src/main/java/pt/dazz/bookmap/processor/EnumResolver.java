package pt.dazz.bookmap.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import pt.dazz.bookmap.processor.model.EnumModel;
import pt.dazz.bookmap.processor.model.EnumModel.Constant;
import pt.dazz.bookmap.processor.model.EnumModel.Range;

/**
 * Reads the {@code @CobolValue} and {@code @CobolUnknown} annotations of an enum type and
 * checks the values against one field.
 */
final class EnumResolver {

    private static final String COBOL_VALUE = "pt.dazz.bookmap.annotation.CobolValue";
    private static final String COBOL_UNKNOWN = "pt.dazz.bookmap.annotation.CobolUnknown";
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    /** The attributes of a numeric field that the values must fit. */
    record Numeric(int length, int scale, boolean signed) {
    }

    /** One value or range of a constant, as comparable bounds. */
    private record Interval(String constant, String text, Comparable<Object> low,
                            Comparable<Object> high) {
    }

    private final Elements elements;
    private final AnnotatedMember member;
    private final TypeElement type;
    private final Problems problems;
    private final String prefix;
    private final int length;
    private final Numeric numeric;

    private EnumResolver(Elements elements, AnnotatedMember member, TypeElement type,
                         int length, Numeric numeric, Problems problems) {
        this.elements = elements;
        this.member = member;
        this.type = type;
        this.length = length;
        this.numeric = numeric;
        this.problems = problems;
        this.prefix = "enum " + type.getSimpleName() + ": ";
    }

    /**
     * Builds the enum model of a field and reports each problem.
     *
     * @param elements the element utilities
     * @param member the enum field
     * @param type the enum type
     * @param length the number of characters of a text field
     * @param numeric the attributes of a numeric field, or null for a text field
     * @param codecPackage the package of the generated codec
     * @param problems the problems of the build
     * @return the model, or null if the enum has problems
     */
    static EnumModel resolve(Elements elements, AnnotatedMember member, TypeElement type,
                             int length, Numeric numeric, PackageElement codecPackage,
                             Problems problems) {
        return new EnumResolver(elements, member, type, length, numeric, problems)
            .build(codecPackage);
    }

    private EnumModel build(PackageElement codecPackage) {
        int before = problems.count();
        if (!accessible(type, codecPackage)) {
            error("the enum must be public, or in the package " + codecPackage.getQualifiedName());
        }
        List<Constant> constants = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        boolean annotated = false;
        for (Element element : type.getEnclosedElements()) {
            if (element.getKind() == ElementKind.ENUM_CONSTANT) {
                annotated |= constant(element, constants, unknown);
            }
        }
        if (!annotated) {
            error("no constant has @CobolValue");
        }
        if (unknown.size() > 1) {
            error("only one constant can have @CobolUnknown: " + String.join(", ", unknown));
        }
        checkOverlap(constants);
        if (problems.count() != before) {
            return null;
        }
        return new EnumModel(type, numeric != null, constants,
            unknown.isEmpty() ? null : unknown.get(0));
    }

    /** Tells if the codec in a package can refer to a type and its enclosing types. */
    private boolean accessible(TypeElement element, PackageElement codecPackage) {
        boolean samePackage = elements.getPackageOf(element).equals(codecPackage);
        for (Element current = element; current instanceof TypeElement;
             current = current.getEnclosingElement()) {
            if (current.getModifiers().contains(Modifier.PRIVATE)
                || !current.getModifiers().contains(Modifier.PUBLIC) && !samePackage) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads one constant. A constant with values that are not correct is not in the list.
     *
     * @return {@code true} if the constant has {@code @CobolValue}
     */
    private boolean constant(Element element, List<Constant> constants, List<String> unknown) {
        String name = element.getSimpleName().toString();
        boolean fallback = Mirrors.find(element, COBOL_UNKNOWN).isPresent();
        if (fallback) {
            unknown.add(name);
        }
        Optional<AnnotationMirror> annotation = Mirrors.find(element, COBOL_VALUE);
        if (annotation.isEmpty()) {
            if (!fallback) {
                error("the constant " + name + " has no @CobolValue");
            }
            return false;
        }
        List<String> values = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();
        int count = 0;
        for (var entry : annotation.get().getElementValues().entrySet()) {
            for (AnnotationValue item : list(entry.getValue())) {
                count++;
                if (entry.getKey().getSimpleName().contentEquals("value")) {
                    value(name, item).ifPresent(values::add);
                } else {
                    range(name, (AnnotationMirror) item.getValue()).ifPresent(ranges::add);
                }
            }
        }
        if (count == 0) {
            error("the constant " + name + " has no value");
        } else if (values.size() + ranges.size() == count) {
            constants.add(new Constant(name, values, ranges));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<? extends AnnotationValue> list(AnnotationValue value) {
        return (List<? extends AnnotationValue>) value.getValue();
    }

    /** Normalizes and checks one value. Gives empty if the value is not correct. */
    private Optional<String> value(String constant, AnnotationValue value) {
        if (!(Mirrors.value(value) instanceof String text)) {
            // javac reports the problem of an unresolved value itself.
            problems.unresolved = true;
            return Optional.empty();
        }
        return numeric == null ? text(constant, text) : number(constant, text);
    }

    private Optional<String> text(String constant, String text) {
        String value = text.stripTrailing();
        if (value.length() > length) {
            error("the value '" + value + "' of " + constant + " is longer than " + length
                + " characters");
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private Optional<String> number(String constant, String text) {
        if (!NUMBER.matcher(text).matches()) {
            error("the value '" + text + "' of " + constant + " is not a decimal number");
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(text);
        String problem = null;
        if (value.signum() < 0 && !numeric.signed()) {
            problem = "is negative, but the field has no sign";
        } else if (value.stripTrailingZeros().scale() > numeric.scale()) {
            problem = "has more than " + numeric.scale() + " decimal places";
        } else if (value.setScale(numeric.scale(), RoundingMode.UNNECESSARY).unscaledValue()
            .abs().toString().length() > numeric.length()) {
            problem = "has more than " + numeric.length() + " digits";
        }
        if (problem != null) {
            error("the value '" + text + "' of " + constant + " " + problem);
            return Optional.empty();
        }
        return Optional.of(value.toPlainString());
    }

    private Optional<Range> range(String constant, AnnotationMirror range) {
        Optional<String> from = Optional.empty();
        Optional<String> thru = Optional.empty();
        for (var entry : range.getElementValues().entrySet()) {
            Optional<String> bound = value(constant, entry.getValue());
            if (entry.getKey().getSimpleName().contentEquals("from")) {
                from = bound;
            } else {
                thru = bound;
            }
        }
        if (from.isEmpty() || thru.isEmpty()) {
            return Optional.empty();
        }
        if (compare(from.get(), thru.get()) > 0) {
            error("the range '" + from.get() + "' THRU '" + thru.get() + "' of " + constant
                + " is empty: the first value is more than the last value");
            return Optional.empty();
        }
        return Optional.of(new Range(from.get(), thru.get()));
    }

    private int compare(String left, String right) {
        return key(left).compareTo(key(right));
    }

    @SuppressWarnings("unchecked")
    private Comparable<Object> key(String value) {
        return (Comparable<Object>) (Comparable<?>) (numeric == null ? value
            : new BigDecimal(value));
    }

    /** Reports each value of a constant that another constant also contains. */
    private void checkOverlap(List<Constant> constants) {
        List<Interval> intervals = new ArrayList<>();
        for (Constant constant : constants) {
            constant.values().forEach(value -> intervals.add(new Interval(constant.name(),
                "value '" + value + "'", key(value), key(value))));
            constant.ranges().forEach(range -> intervals.add(new Interval(constant.name(),
                "range '" + range.from() + "' THRU '" + range.thru() + "'", key(range.from()),
                key(range.thru()))));
        }
        intervals.sort(Comparator.comparing(Interval::low));
        for (int i = 0; i < intervals.size(); i++) {
            Interval first = intervals.get(i);
            for (int j = i + 1; j < intervals.size()
                && intervals.get(j).low().compareTo(first.high()) <= 0; j++) {
                Interval second = intervals.get(j);
                if (!first.constant().equals(second.constant())) {
                    error("the " + second.text() + " of " + second.constant()
                        + " overlaps the " + first.text() + " of " + first.constant());
                }
            }
        }
    }

    private void error(String message) {
        problems.error(member, prefix + message);
    }
}
