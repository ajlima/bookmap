package pt.dazz.bookmap.processor;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Collects the problems that one build of an operation class finds.
 * The {@link ModelBuilder} decides later which problems to report.
 */
final class Problems {

    /**
     * Gives one problem.
     *
     * @param element the element that gets the message
     * @param message the full message
     */
    record Problem(Element element, String message) {
    }

    final List<Problem> errors = new ArrayList<>();
    final List<Problem> accessorErrors = new ArrayList<>();
    boolean unresolved;

    /**
     * Adds an error about a member. The message starts with the member descriptor.
     *
     * @param member the member
     * @param message the problem
     */
    void error(AnnotatedMember member, String message) {
        errors.add(new Problem(member.field(), member.descriptor() + ": " + message));
    }

    /**
     * Adds an error about a class. The message starts with the simple class name.
     *
     * @param type the class
     * @param message the problem
     */
    void classError(TypeElement type, String message) {
        errors.add(new Problem(type, type.getSimpleName() + ": " + message));
    }

    /**
     * Adds an error about a missing getter or setter.
     *
     * @param member the member
     * @param message the problem
     */
    void accessorError(AnnotatedMember member, String message) {
        accessorErrors.add(new Problem(member.field(), member.descriptor() + ": " + message));
    }

    /**
     * Gives the number of errors, without the accessor errors.
     *
     * @return the number of errors
     */
    int count() {
        return errors.size();
    }
}
