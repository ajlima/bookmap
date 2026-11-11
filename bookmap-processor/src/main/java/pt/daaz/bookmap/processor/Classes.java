package pt.daaz.bookmap.processor;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Checks if the generated code can make instances of a class.
 */
final class Classes {

    private Classes() {
    }

    /**
     * Tells if the generated code can call {@code new} on a class.
     *
     * @param element the class
     * @return {@code true} for a public, concrete, top-level or static class with a public
     *     no-argument constructor
     */
    static boolean instantiable(TypeElement element) {
        return element.getKind() == ElementKind.CLASS
            && element.getModifiers().contains(Modifier.PUBLIC)
            && !element.getModifiers().contains(Modifier.ABSTRACT)
            && topLevelOrStatic(element)
            && hasPublicNoArgConstructor(element);
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
     * Tells if a class has a public no-argument constructor.
     *
     * @param element the class
     * @return {@code true} if the constructor is available
     */
    static boolean hasPublicNoArgConstructor(TypeElement element) {
        return ElementFilter.constructorsIn(element.getEnclosedElements()).stream()
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && constructor.getModifiers().contains(Modifier.PUBLIC));
    }
}
