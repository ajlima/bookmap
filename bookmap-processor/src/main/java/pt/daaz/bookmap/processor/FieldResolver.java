package pt.daaz.bookmap.processor;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import pt.daaz.bookmap.annotation.Direction;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.BookWriter;
import pt.daaz.bookmap.processor.model.FieldModel;
import pt.daaz.bookmap.processor.model.FieldModel.JavaKind;

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

    private final Elements elements;
    private final Types types;
    private final AccessorResolver accessors;

    /**
     * Makes a resolver.
     *
     * @param elements the element utilities
     * @param types the type utilities
     */
    FieldResolver(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
        this.accessors = new AccessorResolver(elements);
    }

    /**
     * Builds a field model.
     *
     * @param owner the class that the generated code uses for the accessors
     * @param member the annotated field
     * @param itemDirection the collection direction for an item field, or null
     * @param problems the problems of the build
     * @return the model, or null if the field has problems
     */
    FieldModel resolve(TypeElement owner, AnnotatedMember member, Direction itemDirection,
                       Problems problems) {
        int before = problems.count();
        Map<String, Object> values = member.values();
        Direction direction;
        if (itemDirection == null) {
            direction = Direction.valueOf(
                (String) values.getOrDefault("direction", Direction.INPUT_OUTPUT.name()));
        } else {
            if (values.containsKey("direction")) {
                problems.error(member, "direction must not be set on an item field");
            }
            direction = itemDirection;
        }
        Usage usage = Usage.valueOf(
            (String) values.getOrDefault("usage", Usage.DISPLAY.name()));
        int scale = (Integer) values.getOrDefault("scale", 0);
        boolean signed = (Boolean) values.getOrDefault("signed", false);
        String pattern = (String) values.getOrDefault("pattern", DEFAULT_PATTERN);
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
        Integer length = (Integer) values.get("length");
        if (length == null) {
            problems.error(member, "length is missing");
        } else if (length < 1) {
            problems.error(member, "length must be at least 1");
        }
        if (scale < 0) {
            problems.error(member, "scale must not be negative");
        }
        if (problems.count() == before) {
            checkTypeAndUsage(member, kind, fieldType, usage, length, scale, problems);
        }
        Optional<String> getter = getter(owner, member, problems);
        Optional<String> setter = setter(owner, member, problems);
        if (problems.count() != before || getter.isEmpty() || setter.isEmpty()) {
            return null;
        }
        return new FieldModel(member.owner(), member.name(), member.order(), length,
            direction, usage, scale, signed, pattern, kind, fieldType, getter.get(),
            setter.get(), converter, BookWriter.byteLength(usage, length));
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
        if (!Classes.instantiable(converter)) {
            problems.error(member, "converter " + converter.getQualifiedName()
                + " must be a public class with a public no-argument constructor");
        }
        TypeMirror javaType = converterJavaType(converter.asType());
        TypeMirror boxed = fieldType.getKind().isPrimitive()
            ? types.boxedClass((PrimitiveType) fieldType).asType() : fieldType;
        if (!types.isSameType(javaType, boxed)) {
            problems.error(member, "converter type " + javaType
                + " does not agree with field type " + boxed);
        }
    }

    /** Finds {@code J} of {@code FieldConverter<J>} in the supertypes of a converter. */
    private TypeMirror converterJavaType(TypeMirror type) {
        for (TypeMirror supertype : types.directSupertypes(type)) {
            DeclaredType declared = (DeclaredType) supertype;
            if (((TypeElement) declared.asElement()).getQualifiedName()
                .contentEquals(FIELD_CONVERTER)) {
                return declared.getTypeArguments().isEmpty()
                    ? elements.getTypeElement("java.lang.Object").asType()
                    : declared.getTypeArguments().get(0);
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
     * @param problems the problems of the build
     * @return the getter name, or empty
     */
    Optional<String> getter(TypeElement owner, AnnotatedMember member, Problems problems) {
        Optional<String> getter = accessors.getter(owner, member.field());
        if (getter.isEmpty()) {
            problems.accessorError(member, "missing getter get" + capitalized(member));
        }
        return getter;
    }

    /**
     * Finds the setter of a member. A missing setter is an accessor error.
     *
     * @param owner the class that the generated code uses
     * @param member the member
     * @param problems the problems of the build
     * @return the setter name, or empty
     */
    Optional<String> setter(TypeElement owner, AnnotatedMember member, Problems problems) {
        Optional<String> setter = accessors.setter(owner, member.field());
        if (setter.isEmpty()) {
            problems.accessorError(member, "missing setter set" + capitalized(member));
        }
        return setter;
    }

    private static String capitalized(AnnotatedMember member) {
        String name = member.name();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
