package pt.dazz.copymapper.processor;

import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Finds an annotation of another library with the same simple name as a CopyMapper annotation.
 * An IDE can import such an annotation by mistake. For example, the {@code @Field} of Spring Data
 * MongoDB also has the attribute {@code order}, so the code compiles, but the member is not in
 * the layout.
 */
final class ForeignAnnotations {

    /** The package of the CopyMapper annotations. */
    static final String PACKAGE = "pt.dazz.copymapper.annotation";

    private static final Set<String> MEMBER_NAMES = Set.of("Field", "Occurs", "Group");
    private static final Set<String> CLASS_NAMES = Set.of("SubTypes", "Defaults");

    private ForeignAnnotations() {
    }

    /**
     * Checks a field that has no CopyMapper annotation.
     *
     * @param field the field
     * @return the problem, or empty if the field has no foreign {@code Field}, {@code Occurs}, or
     *     {@code Group} annotation
     */
    static Optional<String> memberProblem(VariableElement field) {
        return problem(field, MEMBER_NAMES);
    }

    /**
     * Checks a class that the processor maps.
     *
     * @param type the class
     * @return the problem, or empty if each foreign {@code SubTypes} or {@code Defaults}
     *     annotation of the class has a CopyMapper annotation with the same simple name
     */
    static Optional<String> classProblem(TypeElement type) {
        return problem(type, CLASS_NAMES);
    }

    private static Optional<String> problem(Element element, Set<String> names) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotation = (TypeElement) mirror.getAnnotationType().asElement();
            String simpleName = annotation.getSimpleName().toString();
            String own = PACKAGE + "." + simpleName;
            if (names.contains(simpleName)
                && !annotation.getQualifiedName().contentEquals(own)
                && Mirrors.find(element, own).isEmpty()) {
                return Optional.of(annotation.getQualifiedName() + " is not " + own
                    + ". Change the import.");
            }
        }
        return Optional.empty();
    }
}
