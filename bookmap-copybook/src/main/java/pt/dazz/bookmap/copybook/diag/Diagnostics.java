package pt.dazz.bookmap.copybook.diag;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the diagnostics of one step. A step does not stop at the first error.
 */
public final class Diagnostics {

    private final List<Diagnostic> all = new ArrayList<>();

    /**
     * Creates an empty collection.
     */
    public Diagnostics() {
        // No diagnostics at the start.
    }

    /**
     * Adds an error.
     *
     * @param source the source name
     * @param line the line number
     * @param column the column number
     * @param message the message
     */
    public void error(String source, int line, int column, String message) {
        all.add(new Diagnostic(Severity.ERROR, source, line, column, message));
    }

    /**
     * Adds a warning.
     *
     * @param source the source name
     * @param line the line number
     * @param column the column number
     * @param message the message
     */
    public void warning(String source, int line, int column, String message) {
        all.add(new Diagnostic(Severity.WARNING, source, line, column, message));
    }

    /**
     * Adds a note.
     *
     * @param source the source name
     * @param line the line number
     * @param column the column number
     * @param message the message
     */
    public void info(String source, int line, int column, String message) {
        all.add(new Diagnostic(Severity.INFO, source, line, column, message));
    }

    /**
     * Gives all diagnostics, in the order of addition.
     *
     * @return an unmodifiable copy
     */
    public List<Diagnostic> all() {
        return List.copyOf(all);
    }

    /**
     * Tells if an error exists.
     *
     * @return {@code true} if at least one diagnostic is an error
     */
    public boolean hasErrors() {
        return all.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
