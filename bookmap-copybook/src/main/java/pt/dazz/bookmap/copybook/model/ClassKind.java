package pt.dazz.bookmap.copybook.model;

/**
 * Gives the role of a generated class.
 */
public enum ClassKind {
    /** A class with {@code @BookOperation}, from a record. */
    OPERATION,
    /** The item class of a collection. */
    ITEM,
    /** A subclass for one alternative of a {@code REDEFINES} area. */
    SUBTYPE
}
