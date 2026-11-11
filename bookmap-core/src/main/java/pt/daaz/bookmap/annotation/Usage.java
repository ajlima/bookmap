package pt.daaz.bookmap.annotation;

/**
 * Gives the COBOL storage format of a numeric field.
 */
public enum Usage {
    /** Zoned decimal. One character for each digit. */
    DISPLAY,
    /** Packed decimal (COMP-3). */
    COMP3,
    /** Binary integer (COMP). */
    COMP
}
