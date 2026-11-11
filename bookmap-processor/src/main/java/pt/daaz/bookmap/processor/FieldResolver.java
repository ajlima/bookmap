package pt.daaz.bookmap.processor;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import pt.daaz.bookmap.annotation.Direction;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.BookWriter;
import pt.daaz.bookmap.codec.Formats;
import pt.daaz.bookmap.processor.model.FieldModel;
import pt.daaz.bookmap.processor.model.FieldModel.JavaKind;
import pt.daaz.bookmap.processor.pic.PicParser;
import pt.daaz.bookmap.processor.pic.PicSpec;
import pt.daaz.bookmap.processor.pic.PicSpec.Category;
import pt.daaz.bookmap.processor.pic.PicSyntaxException;

/**
 * Resolves the attributes of one {@code @BookField} and checks them against the Java type.
 */
final class FieldResolver {

    private static final String NO_CONVERTER = "pt.daaz.bookmap.convert.NoConverter";
    private static final String FIELD_CONVERTER = "pt.daaz.bookmap.convert.FieldConverter";
    private static final String DEFAULT_PATTERN = "yyyyMMdd";
    private static final int MAX_COMP_DIGITS = 18;

    private static final Map<String, JavaKind> KINDS = Map.of(
        "java.lang.String", JavaKind.STRING,
        "int", JavaKind.INT,
        "java.lang.Integer", JavaKind.INTEGER,
        "long", JavaKind.LONG,
        "java.lang.Long", JavaKind.LONG_BOXED,
        "java.math.BigDecimal", JavaKind.BIG_DECIMAL,
        "java.time.LocalDate", JavaKind.LOCAL_DATE,
        "java.time.LocalTime", JavaKind.LOCAL_TIME,
        "java.time.LocalDateTime", JavaKind.LOCAL_DATE_TIME);

    private static final Set<JavaKind> DISPLAY_ONLY = EnumSet.of(JavaKind.STRING,
        JavaKind.LOCAL_DATE, JavaKind.LOCAL_TIME, JavaKind.LOCAL_DATE_TIME);

    private static final Map<JavaKind, Integer> MAX_DIGITS = Map.of(
        JavaKind.INT, 9, JavaKind.INTEGER, 9, JavaKind.LONG, 18, JavaKind.LONG_BOXED, 18);

    private static final Set<JavaKind> NUMERIC = EnumSet.of(JavaKind.INT, JavaKind.INTEGER,
        JavaKind.LONG, JavaKind.LONG_BOXED, JavaKind.BIG_DECIMAL);

    private static final Set<JavaKind> TEMPORAL = EnumSet.of(JavaKind.LOCAL_DATE,
        JavaKind.LOCAL_TIME, JavaKind.LOCAL_DATE_TIME);

    private static final List<String> PIC_ATTRIBUTES = List.of("length", "scale", "signed",
        "usage");

    /** The attributes that only numeric fields and converted fields can use. */
    private static final List<String> NUMERIC_ATTRIBUTES = List.of("signed", "scale");

    // The first sample has two-digit values. The second sample has one-digit values. A pattern
    // that gives different text lengths for them (such as "d", "M", or "H") has no fixed length.
    // With Locale.ROOT, month and day names ("MMMM", "EEEE") have three letters.
    private static final List<LocalDateTime> SAMPLES = List.of(
        LocalDateTime.of(2026, 12, 31, 23, 59, 58),
        LocalDateTime.of(2026, 1, 1, 1, 1, 1));

    private static final Map<JavaKind, Function<LocalDateTime, TemporalAccessor>> SAMPLE_VALUES =
        Map.of(
            JavaKind.LOCAL_DATE, LocalDateTime::toLocalDate,
            JavaKind.LOCAL_TIME, LocalDateTime::toLocalTime,
            JavaKind.LOCAL_DATE_TIME, sample -> sample);

    private static final Map<JavaKind, TemporalQuery<?>> QUERIES = Map.of(
        JavaKind.LOCAL_DATE, LocalDate::from,
        JavaKind.LOCAL_TIME, LocalTime::from,
        JavaKind.LOCAL_DATE_TIME, LocalDateTime::from);

    private final Elements elements;
    private final Types types;
    private final AccessorResolver accessors;
    private final DefaultsResolver defaultsResolver;

    /**
     * Makes a resolver.
     *
     * @param elements the element utilities
     * @param types the type utilities
     * @param defaultsResolver the resolver of the {@code @BookDefaults} values
     */
    FieldResolver(Elements elements, Types types, DefaultsResolver defaultsResolver) {
        this.elements = elements;
        this.types = types;
        this.accessors = new AccessorResolver(elements);
        this.defaultsResolver = defaultsResolver;
    }

    /**
     * Builds a field model. Each attribute comes from the explicit value, then from {@code pic},
     * then from the {@code @BookDefaults} of the declaring class, then from the built-in default.
     * The {@code usage} and {@code signed} defaults apply only to numeric types. The
     * {@code pattern} default applies only to date and time types.
     *
     * @param owner the class that the generated code uses for the accessors
     * @param member the annotated field
     * @param itemDirection the collection direction for an item field, or null
     * @param codecPackage the package of the generated codec
     * @param problems the problems of the build
     * @return the model, or null if the field has problems
     */
    FieldModel resolve(TypeElement owner, AnnotatedMember member, Direction itemDirection,
                       PackageElement codecPackage, Problems problems) {
        int before = problems.count();
        Map<String, Object> values = member.values();
        Direction direction = direction(member, itemDirection, problems);
        TypeMirror fieldType = member.field().asType();
        TypeElement converter = converter(values);
        JavaKind kind;
        if (converter == null) {
            kind = KINDS.get(Mirrors.typeName(fieldType));
            if (kind == null) {
                problems.error(member, "type " + Mirrors.typeName(fieldType)
                    + " is not supported without a converter");
            }
        } else {
            kind = JavaKind.CONVERTED;
            checkConverter(member, converter, fieldType, problems);
        }
        boolean numeric = NUMERIC.contains(kind);
        boolean temporal = TEMPORAL.contains(kind);
        ResolvedDefaults defaults = defaultsResolver.forDeclaringClass(
            (TypeElement) member.field().getEnclosingElement());
        Optional<PicSpec> pic = pic(member, kind, fieldType, problems);
        Usage usage = Optional.ofNullable((String) values.get("usage")).map(Usage::valueOf)
            .or(() -> pic.flatMap(PicSpec::usage))
            .or(() -> numeric ? defaults.usage() : Optional.empty())
            .orElse(Usage.DISPLAY);
        boolean signed = Optional.ofNullable((Boolean) values.get("signed"))
            .or(() -> pic.map(PicSpec::signed))
            .or(() -> numeric ? defaults.signed() : Optional.empty())
            .orElse(false);
        int scale = Optional.ofNullable((Integer) values.get("scale"))
            .or(() -> pic.map(PicSpec::scale))
            .orElse(0);
        String pattern = Optional.ofNullable((String) values.get("pattern"))
            .or(() -> temporal ? defaults.pattern() : Optional.empty())
            .orElse(DEFAULT_PATTERN);
        Integer length = Optional.ofNullable((Integer) values.get("length"))
            .or(() -> pic.map(PicSpec::length))
            .orElse(null);
        Integer textLength = temporal ? textLength(member, kind, fieldType, pattern, problems)
            : null;
        if (length == null) {
            length = textLength;
        }
        if (length == null) {
            // A date or time field has a pattern error. A field with a pic has a pic error.
            if (!temporal && picText(values).isEmpty()) {
                problems.error(member, "pic or length is missing");
            }
        } else if (length < 1) {
            problems.error(member, "length must be at least 1");
        } else if (textLength != null && !length.equals(textLength)) {
            problems.error(member, "length " + length + " does not agree with pattern '"
                + pattern + "' (" + textLength + " characters)");
        }
        if (scale < 0) {
            problems.error(member, "scale must not be negative");
        }
        if (DISPLAY_ONLY.contains(kind)) {
            NUMERIC_ATTRIBUTES.stream().filter(values::containsKey).forEach(attribute ->
                problems.error(member, attribute + " is not permitted for "
                    + simpleName(fieldType)));
        }
        if (problems.count() == before) {
            checkTypeAndUsage(member, kind, fieldType, usage, length, scale, problems);
        }
        Optional<String> getter = getter(owner, member, codecPackage, problems);
        Optional<String> setter = setter(owner, member, codecPackage, problems);
        if (problems.count() != before || getter.isEmpty() || setter.isEmpty()) {
            return null;
        }
        return new FieldModel(member.owner(), member.name(), member.order(), length,
            direction, usage, scale, signed, pattern, kind, fieldType, getter.get(),
            setter.get(), converter, BookWriter.byteLength(usage, length));
    }

    private static Direction direction(AnnotatedMember member, Direction itemDirection,
                                       Problems problems) {
        Map<String, Object> values = member.values();
        if (itemDirection == null) {
            return Direction.valueOf(
                (String) values.getOrDefault("direction", Direction.INPUT_OUTPUT.name()));
        }
        if (values.containsKey("direction")) {
            problems.error(member, "direction must not be set on an item field");
        }
        return itemDirection;
    }

    /**
     * Parses the {@code pic} attribute and checks it against the Java type.
     *
     * @return the parsed {@code pic}, or empty if the field has no {@code pic} or it is not
     *     correct
     */
    private static Optional<PicSpec> pic(AnnotatedMember member, JavaKind kind,
                                         TypeMirror fieldType, Problems problems) {
        Map<String, Object> values = member.values();
        String text = picText(values);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        List<String> combined = PIC_ATTRIBUTES.stream().filter(values::containsKey).toList();
        if (!combined.isEmpty()) {
            problems.error(member, "pic must not be combined with " + String.join(", ", combined));
        }
        PicSpec spec;
        try {
            spec = PicParser.parse(text);
        } catch (PicSyntaxException e) {
            problems.error(member, e.getMessage());
            return Optional.empty();
        }
        String simpleName = simpleName(fieldType);
        if (spec.category() == Category.TEXT) {
            if (NUMERIC.contains(kind)) {
                problems.error(member, "a text pic is not permitted for " + simpleName);
            }
        } else if (kind == JavaKind.STRING) {
            problems.error(member, "a numeric pic is not permitted for String");
        } else if (TEMPORAL.contains(kind) && (spec.signed() || spec.scale() > 0)) {
            problems.error(member, "a signed or decimal pic is not permitted for " + simpleName);
        }
        return Optional.of(spec);
    }

    /** Gives the {@code pic} text. An empty text means that the field has no {@code pic}. */
    private static String picText(Map<String, Object> values) {
        return (String) values.getOrDefault("pic", "");
    }

    /**
     * Makes the formatter of a date or time field and formats a sample value of the field type.
     * The formatted text must also parse back to the field type. Other samples with one-digit
     * values must give text of the same length.
     *
     * @return the length of the formatted text, or null if the pattern is not correct
     */
    private static Integer textLength(AnnotatedMember member, JavaKind kind,
                                      TypeMirror fieldType, String pattern, Problems problems) {
        DateTimeFormatter formatter;
        try {
            formatter = Formats.of(pattern);
        } catch (IllegalArgumentException e) {
            problems.error(member, "pattern '" + pattern + "' is not valid: " + e.getMessage());
            return null;
        }
        Function<LocalDateTime, TemporalAccessor> value = SAMPLE_VALUES.get(kind);
        try {
            String text = formatter.format(value.apply(SAMPLES.get(0)));
            formatter.parse(text, QUERIES.get(kind));
            for (LocalDateTime sample : SAMPLES) {
                if (formatter.format(value.apply(sample)).length() != text.length()) {
                    problems.error(member, "pattern '" + pattern
                        + "' gives text of variable length for " + simpleName(fieldType));
                    return null;
                }
            }
            return text.length();
        } catch (DateTimeException e) {
            problems.error(member, "pattern '" + pattern + "' does not fit "
                + simpleName(fieldType) + ": " + e.getMessage());
            return null;
        }
    }

    private void checkTypeAndUsage(AnnotatedMember member, JavaKind kind, TypeMirror fieldType,
                                   Usage usage, int length, int scale, Problems problems) {
        String simpleName = simpleName(fieldType);
        if (DISPLAY_ONLY.contains(kind) && usage != Usage.DISPLAY) {
            problems.error(member, usage + " is not permitted for " + simpleName);
        }
        Integer maxDigits = MAX_DIGITS.get(kind);
        if (maxDigits != null) {
            if (scale != 0) {
                problems.error(member, "scale must be 0 for " + simpleName);
            }
            if (length > maxDigits) {
                problems.error(member, "length " + length + " is more than " + maxDigits
                    + " digits for " + simpleName);
            }
        }
        if (kind == JavaKind.BIG_DECIMAL && scale > length) {
            problems.error(member, "scale must not be more than length");
        }
        if (usage == Usage.COMP && length > MAX_COMP_DIGITS) {
            problems.error(member, "COMP length must not be more than " + MAX_COMP_DIGITS);
        }
    }

    private static String simpleName(TypeMirror type) {
        String typeName = Mirrors.typeName(type);
        return typeName.substring(typeName.lastIndexOf('.') + 1);
    }

    private TypeElement converter(Map<String, Object> values) {
        Object value = values.get("converter");
        if (value == null) {
            return null;
        }
        TypeElement converter = (TypeElement) types.asElement((TypeMirror) value);
        return converter.getQualifiedName().contentEquals(NO_CONVERTER) ? null : converter;
    }

    private void checkConverter(AnnotatedMember member, TypeElement converter,
                                TypeMirror fieldType, Problems problems) {
        if (!Classes.instantiable(converter, elements)) {
            problems.error(member, "converter " + converter.getQualifiedName()
                + " must be a public class with a public no-argument constructor");
        }
        if (!converter.getTypeParameters().isEmpty()) {
            problems.error(member, "converter " + converter.getQualifiedName()
                + " must not have type parameters");
        }
        TypeMirror javaType = converterJavaType(converter.asType());
        if (javaType == null) {
            // A raw FieldConverter makes unchecked calls in the generated codec.
            problems.error(member, "converter " + converter.getQualifiedName()
                + " must implement FieldConverter with a type argument");
            return;
        }
        TypeMirror boxed = fieldType.getKind().isPrimitive()
            ? types.boxedClass((PrimitiveType) fieldType).asType() : fieldType;
        if (!types.isSameType(javaType, boxed)) {
            problems.error(member, "converter type " + javaType
                + " does not agree with field type " + boxed);
        }
    }

    /**
     * Finds {@code J} of {@code FieldConverter<J>} in the supertypes of a converter. Gives
     * {@code null} if the converter implements the raw type {@code FieldConverter}.
     */
    private TypeMirror converterJavaType(TypeMirror type) {
        for (TypeMirror supertype : types.directSupertypes(type)) {
            DeclaredType declared = (DeclaredType) supertype;
            if (((TypeElement) declared.asElement()).getQualifiedName()
                .contentEquals(FIELD_CONVERTER)) {
                return declared.getTypeArguments().isEmpty()
                    ? null : declared.getTypeArguments().get(0);
            }
            TypeMirror found = converterJavaType(supertype);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds the getter of a member. A missing getter is an accessor error.
     *
     * @param owner the class that the generated code uses
     * @param member the member
     * @param codecPackage the package of the generated codec
     * @param problems the problems of the build
     * @return the getter name, or empty
     */
    Optional<String> getter(TypeElement owner, AnnotatedMember member,
                            PackageElement codecPackage, Problems problems) {
        Optional<String> getter = accessors.getter(owner, member.field(), codecPackage);
        if (getter.isEmpty()) {
            problems.accessorError(member, "missing getter "
                + AccessorResolver.getterName(member.field()));
        }
        return getter;
    }

    /**
     * Finds the setter of a member. A missing setter is an accessor error.
     *
     * @param owner the class that the generated code uses
     * @param member the member
     * @param codecPackage the package of the generated codec
     * @param problems the problems of the build
     * @return the setter name, or empty
     */
    Optional<String> setter(TypeElement owner, AnnotatedMember member,
                            PackageElement codecPackage, Problems problems) {
        Optional<String> setter = accessors.setter(owner, member.field(), codecPackage);
        if (setter.isEmpty()) {
            problems.accessorError(member, "missing setter "
                + AccessorResolver.setterName(member.field()));
        }
        return setter;
    }
}
