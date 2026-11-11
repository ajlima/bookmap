package pt.dazz.bookmap.copybook.diag;

/**
 * Gives the importance of a diagnostic.
 */
public enum Severity {
    /** The step cannot give a result. */
    ERROR,
    /** The step gives a result, but the developer must check it. */
    WARNING,
    /** A note about an item that the step ignores. */
    INFO
}
