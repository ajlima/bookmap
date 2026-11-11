package pt.dazz.bookmap.processor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.FieldModel.JavaKind;

/**
 * Reads the {@code @BookSubTypes} annotation and checks the discriminator values.
 */
final class SubTypesResolver {

    static final String BOOK_SUB_TYPES = "pt.dazz.bookmap.annotation.BookSubTypes";

    /** The Java kinds of a numeric discriminator. */
    static final Set<JavaKind> NUMERIC_KINDS = EnumSet.of(JavaKind.INT, JavaKind.INTEGER,
        JavaKind.LONG, JavaKind.LONG_BOXED);

    /**
     * The content of one {@code @BookSubTypes} annotation.
     *
     * @param discriminator the name of the discriminator field
     * @param entries the subtypes, in the order of the annotation
     */
    record Declared(String discriminator, List<Entry> entries) {
    }

    /**
     * One {@code @BookSubTypes.Type}.
     *
     * @param value the discriminator value, as written
     * @param type the subtype
     */
    record Entry(String value, TypeMirror type) {
    }

    private SubTypesResolver() {
    }

    /**
     * Tells if a class has {@code @BookSubTypes}.
     *
     * @param type the class
     * @return {@code true} if the class has the annotation
     */
    static boolean present(TypeElement type) {
        return Mirrors.find(type, BOOK_SUB_TYPES).isPresent();
    }

    /**
     * Reads the {@code @BookSubTypes} annotation of a base class. A value that javac cannot
     * resolve yet makes the problems unresolved.
     *
     * @param base the base class
     * @param problems the problems of the build
     * @return the content, or empty if a value is not resolved
     */
    static Optional<Declared> read(TypeElement base, Problems problems) {
        AnnotationMirror mirror = Mirrors.find(base, BOOK_SUB_TYPES).orElseThrow();
        Map<String, Object> values = Mirrors.explicitValues(mirror);
        if (values.containsValue(Mirrors.UNRESOLVED)) {
            problems.unresolved = true;
            return Optional.empty();
        }
        List<Entry> entries = new ArrayList<>();
        for (Object item : (List<?>) values.get("value")) {
            Map<String, Object> entry = Mirrors.explicitValues(
                (AnnotationMirror) ((AnnotationValue) item).getValue());
            Object type = entry.get("type");
            // javac gives "<error>" for a class that does not exist yet.
            if (type == Mirrors.UNRESOLVED || entry.get("value") == Mirrors.UNRESOLVED) {
                problems.unresolved = true;
                return Optional.empty();
            }
            entries.add(new Entry((String) entry.get("value"), (TypeMirror) type));
        }
        return Optional.of(new Declared((String) values.get("discriminator"), entries));
    }

    /**
     * Checks a discriminator value and gives its normalized form. A numeric value loses its
     * leading zeros. A text value loses its spaces on the right.
     *
     * @param discriminator the discriminator field
     * @param member the annotated discriminator, for the error messages
     * @param value the value as written
     * @param problems the problems of the build
     * @return the normalized value, or empty after an error
     */
    static Optional<String> normalize(FieldModel discriminator, AnnotatedMember member,
                                      String value, Problems problems) {
        if (value.isBlank()) {
            problems.error(member, "discriminator value must not be blank");
            return Optional.empty();
        }
        if (!NUMERIC_KINDS.contains(discriminator.kind())) {
            String text = value.stripTrailing();
            return fits(text.length() <= discriminator.length(), member, value, problems, text);
        }
        long number;
        try {
            number = Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            problems.error(member, "discriminator value '" + value + "' is not a number");
            return Optional.empty();
        }
        String digits = Long.toString(Math.abs(number));
        boolean fits = digits.length() <= discriminator.length()
            && (number >= 0 || discriminator.signed());
        return fits(fits, member, value, problems, Long.toString(number));
    }

    private static Optional<String> fits(boolean fits, AnnotatedMember member, String value,
                                         Problems problems, String normalized) {
        if (!fits) {
            problems.error(member, "discriminator value '" + value + "' does not fit the field");
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
