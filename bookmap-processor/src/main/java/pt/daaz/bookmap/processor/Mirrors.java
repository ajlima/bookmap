package pt.daaz.bookmap.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Reads annotations through {@code javax.lang.model}. It never loads annotation classes.
 */
final class Mirrors {

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
     * Gives the attributes that the source code sets explicitly.
     *
     * @param mirror the annotation
     * @return the explicit values, by attribute name
     */
    static Map<String, Object> explicitValues(AnnotationMirror mirror) {
        Map<String, Object> values = new HashMap<>();
        mirror.getElementValues().forEach((method, value) ->
            values.put(method.getSimpleName().toString(), value(value)));
        return values;
    }

    private static Object value(AnnotationValue value) {
        Object raw = value.getValue();
        return raw instanceof Element constant ? constant.getSimpleName().toString() : raw;
    }
}
