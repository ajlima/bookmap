package pt.dazz.copymapper.copybook.spec;

/** Tells how the end of a table (OCCURS) is known. */
public enum TableMode {
    /** A counter field holds the number of used rows. */
    COUNT,
    /** A row of low values ends the table. */
    LOW_VALUES,
    /** A row of spaces ends the table. */
    SPACES,
    /** A key field marks the unused rows. */
    KEY
}
