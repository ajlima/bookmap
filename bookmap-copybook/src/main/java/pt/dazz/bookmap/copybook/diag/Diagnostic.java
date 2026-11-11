package pt.dazz.bookmap.copybook.diag;

import java.util.Locale;

/**
 * Describes one problem or note in a copybook.
 *
 * @param severity the severity
 * @param source the source name (a file name without a directory)
 * @param line the line number, from 1
 * @param column the column number, from 1
 * @param message the message
 */
public record Diagnostic(Severity severity, String source, int line, int column, String message) {

    /**
     * Gives the diagnostic as one line of text.
     *
     * @return the text, in the format {@code <source>:<line>:<column>: <severity>: <message>}
     */
    public String format() {
        return source + ":" + line + ":" + column + ": "
            + severity.name().toLowerCase(Locale.ROOT) + ": " + message;
    }
}
