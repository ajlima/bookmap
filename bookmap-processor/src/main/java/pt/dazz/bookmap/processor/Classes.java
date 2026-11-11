package pt.dazz.bookmap.processor;

import java.util.List;
import java.util.Map;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * Checks if the generated code can make instances of a class.
 *
 * <p>Lombok rule: a class with {@code lombok.NoArgsConstructor} (public access, no
 * {@code staticName}) has a public no-argument constructor. Lombok can generate it later in the
 * same compilation. {@code lombok.NoArgsConstructor}, {@code lombok.AllArgsConstructor},
 * {@code lombok.RequiredArgsConstructor}, and {@code lombok.Builder} remove the default
 * constructor that javac adds. Thus, the check does not count the default constructor of such a
 * class. The check finds the Lombok annotations by name. It does not depend on Lombok.
 */
final class Classes {

    private static final String NO_ARGS_CONSTRUCTOR = "lombok.NoArgsConstructor";
    private static final List<String> REMOVE_DEFAULT_CONSTRUCTOR = List.of(NO_ARGS_CONSTRUCTOR,
        "lombok.AllArgsConstructor", "lombok.RequiredArgsConstructor", "lombok.Builder");
    private static final String PUBLIC = "PUBLIC";

    private Classes() {
    }

    /**
     * Tells if the generated code can call {@code new} on a class.
     *
     * @param element the class
     * @param elements the element utilities of the compilation
     * @return {@code true} for a public, concrete, top-level or static class with a public
     *     no-argument constructor
     */
    static boolean instantiable(TypeElement element, Elements elements) {
        return element.getKind() == ElementKind.CLASS
            && element.getModifiers().contains(Modifier.PUBLIC)
            && !element.getModifiers().contains(Modifier.ABSTRACT)
            && topLevelOrStatic(element)
            && hasPublicNoArgConstructor(element, elements);
    }

    /**
     * Tells if a class is top-level or a static nested class.
     *
     * @param element the class
     * @return {@code true} if the class does not need an enclosing instance
     */
    static boolean topLevelOrStatic(TypeElement element) {
        return element.getNestingKind() != NestingKind.MEMBER
            || element.getModifiers().contains(Modifier.STATIC);
    }

    /**
     * Tells if a class has a public no-argument constructor. The check applies the Lombok rule.
     *
     * @param element the class
     * @param elements the element utilities of the compilation
     * @return {@code true} if the constructor is available
     */
    static boolean hasPublicNoArgConstructor(TypeElement element, Elements elements) {
        if (Mirrors.find(element, NO_ARGS_CONSTRUCTOR).map(Mirrors::explicitValues)
            .filter(Classes::publicWithoutStaticName).isPresent()) {
            return true;
        }
        boolean lombokConstructors = REMOVE_DEFAULT_CONSTRUCTOR.stream()
            .anyMatch(name -> Mirrors.find(element, name).isPresent());
        return ElementFilter.constructorsIn(element.getEnclosedElements()).stream()
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && constructor.getModifiers().contains(Modifier.PUBLIC)
                && !(lombokConstructors
                    && elements.getOrigin(constructor) == Elements.Origin.MANDATED));
    }

    /** With {@code staticName}, Lombok makes the constructor private. */
    private static boolean publicWithoutStaticName(Map<String, Object> values) {
        return PUBLIC.equals(values.getOrDefault("access", PUBLIC))
            && "".equals(values.getOrDefault("staticName", ""));
    }
}
