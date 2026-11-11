package pt.daaz.bookmap.processor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Reads annotations through {@code javax.lang.model}. It never loads annotation classes.
 */
final class Mirrors {

    /**
     * Marks an attribute value that javac could not resolve, for example {@code Missing.class} or
     * an unknown constant. javac reports the problem itself.
     */
    static final Object UNRESOLVED = new Object();

    private static final String JAVAC_ERROR_VALUE = "<error>";

    private Mirrors() {
    }

    /**
     * Finds an annotation on an element by its qualified name.
     *
     * @param element the annotated element
     * @param qualifiedName the qualified name of the annotation type
     * @return the annotation, or empty if the element does not have it
     */
    static Optional<AnnotationMirror> find(Element element, String qualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (type.getQualifiedName().contentEquals(qualifiedName)) {
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    /**
     * Tells the qualified name of a type, without type annotations and type arguments.
     * A primitive type gives its keyword, for example {@code int}. Other types give their text.
     *
     * @param type the type
     * @return the name
     */
    static String typeName(TypeMirror type) {
        if (type instanceof DeclaredType declared) {
            return ((TypeElement) declared.asElement()).getQualifiedName().toString();
        }
        return type.getKind().isPrimitive()
            ? type.getKind().name().toLowerCase(Locale.ROOT) : type.toString();
    }

    /**
     * Gives the attributes that the source code sets explicitly.
     *
     * @param mirror the annotation
     * @return the explicit values, by attribute name. An unresolved value is {@link #UNRESOLVED}.
     */
    static Map<String, Object> explicitValues(AnnotationMirror mirror) {
        Map<String, Object> values = new HashMap<>();
        mirror.getElementValues().forEach((method, value) ->
            values.put(method.getSimpleName().toString(), value(value)));
        return values;
    }

    private static Object value(AnnotationValue value) {
        Object raw = value.getValue();
        // javac gives the text "<error>" for an unresolved value. A string literal with the
        // same text is a correct value: its source form has quotes.
        if (JAVAC_ERROR_VALUE.equals(raw) && JAVAC_ERROR_VALUE.equals(value.toString())) {
            return UNRESOLVED;
        }
        return raw instanceof Element constant ? constant.getSimpleName().toString() : raw;
    }
}
