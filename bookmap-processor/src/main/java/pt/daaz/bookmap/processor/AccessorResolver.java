package pt.daaz.bookmap.processor;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * Finds the getter and the setter of an annotated field.
 *
 * <p>Lombok rule: if the field, or the class that declares the field, has
 * {@code lombok.Getter}, {@code lombok.Setter}, or {@code lombok.Data}, the resolver gives the
 * conventional name and does not look for the method. Lombok can generate the method later in
 * the same compilation. The getter of a primitive {@code boolean} field is {@code is<Name>}.
 * As Lombok does, a primitive {@code boolean} field {@code isActive} has the accessors
 * {@code isActive} and {@code setActive}.
 * An annotation on the field has priority over an annotation on the class.
 * {@code AccessLevel.NONE} and {@code AccessLevel.PRIVATE} give no accessor, because the generated
 * codec cannot call them. A {@code final} field gets no setter. With
 * {@code lombok.experimental.Accessors}, the resolver does not assume names. The resolver finds
 * the Lombok annotations by name. It does not depend on Lombok.
 *
 * <p>Without the Lombok rule, the resolver looks for a public instance method on the owner class
 * or its superclasses. A getter has no parameters and returns the field type. For a primitive
 * {@code boolean} field, the resolver tries {@code is<Name>} first, then {@code get<Name>}.
 * A setter has one parameter of the field type. The return type of a setter is not checked.
 */
public final class AccessorResolver {

    private static final String GETTER = "lombok.Getter";
    private static final String SETTER = "lombok.Setter";
    private static final String DATA = "lombok.Data";
    private static final String ACCESSORS = "lombok.experimental.Accessors";
    private static final Set<String> NOT_CALLABLE = Set.of("NONE", "PRIVATE");

    private final Elements elements;

    /**
     * Makes a resolver.
     *
     * @param elements the element utilities of the compilation
     */
    public AccessorResolver(Elements elements) {
        this.elements = elements;
    }

    /**
     * Finds the getter of a field.
     *
     * @param owner the class that the generated code uses: the operation or item class
     * @param field the field, declared in the owner or in a superclass
     * @return the getter name, or empty if no getter is available
     */
    public Optional<String> getter(TypeElement owner, VariableElement field) {
        String name = getterName(field);
        if (lombokGenerates(field, GETTER)) {
            return Optional.of(name);
        }
        TypeMirror type = field.asType();
        Predicate<ExecutableElement> signature =
            method -> method.getParameters().isEmpty() && same(method.getReturnType(), type);
        Optional<String> found = find(owner, name, signature);
        return isBoolean(field)
            ? found.or(() -> find(owner, "get" + property(field), signature)) : found;
    }

    /**
     * Finds the setter of a field.
     *
     * @param owner the class that the generated code uses: the operation or item class
     * @param field the field, declared in the owner or in a superclass
     * @return the setter name, or empty if no setter is available
     */
    public Optional<String> setter(TypeElement owner, VariableElement field) {
        String name = setterName(field);
        if (!field.getModifiers().contains(Modifier.FINAL) && lombokGenerates(field, SETTER)) {
            return Optional.of(name);
        }
        TypeMirror type = field.asType();
        return find(owner, name, method -> method.getParameters().size() == 1
            && same(method.getParameters().get(0).asType(), type));
    }

    /**
     * Gives the conventional getter name of a field: {@code is<Name>} for a primitive
     * {@code boolean} field (as Lombok makes it), else {@code get<Name>}.
     *
     * @param field the field
     * @return the getter name
     */
    static String getterName(VariableElement field) {
        return (isBoolean(field) ? "is" : "get") + property(field);
    }

    /**
     * Gives the conventional setter name of a field.
     *
     * @param field the field
     * @return the setter name {@code set<Name>}
     */
    static String setterName(VariableElement field) {
        return "set" + property(field);
    }

    private static boolean isBoolean(VariableElement field) {
        return field.asType().getKind() == TypeKind.BOOLEAN;
    }

    /**
     * Gives the field name with a capital first letter. As Lombok does, the name of a primitive
     * {@code boolean} field such as {@code isActive} loses the {@code is} prefix: the
     * accessors are {@code isActive} and {@code setActive}.
     */
    private static String property(VariableElement field) {
        String name = field.getSimpleName().toString();
        if (isBoolean(field) && name.startsWith("is") && name.length() > 2
            && Character.isUpperCase(name.charAt(2))) {
            return name.substring(2);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static boolean lombokGenerates(VariableElement field, String annotation) {
        Element declaringClass = field.getEnclosingElement();
        if (Mirrors.find(field, ACCESSORS).isPresent()
            || Mirrors.find(declaringClass, ACCESSORS).isPresent()) {
            return false;
        }
        Optional<AnnotationMirror> onField = Mirrors.find(field, annotation);
        if (onField.isPresent()) {
            return callable(onField.get());
        }
        Optional<AnnotationMirror> onClass = Mirrors.find(declaringClass, annotation);
        if (onClass.isPresent()) {
            return callable(onClass.get());
        }
        return Mirrors.find(declaringClass, DATA).isPresent();
    }

    private static boolean callable(AnnotationMirror mirror) {
        // The generated codec is in the same package, so only PRIVATE and NONE are not callable.
        return !NOT_CALLABLE.contains(Mirrors.explicitValues(mirror).getOrDefault("value", ""));
    }

    private Optional<String> find(TypeElement owner, String name,
                                  Predicate<ExecutableElement> signature) {
        for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers(owner))) {
            if (method.getSimpleName().contentEquals(name)
                && method.getModifiers().contains(Modifier.PUBLIC)
                && !method.getModifiers().contains(Modifier.STATIC)
                && signature.test(method)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    // The resolver has only Elements, not Types. It compares the qualified names without type
    // annotations and type arguments, which is sufficient for the field types that bookmap supports.
    private static boolean same(TypeMirror a, TypeMirror b) {
        return Mirrors.typeName(a).equals(Mirrors.typeName(b));
    }
}
