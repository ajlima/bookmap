package pt.dazz.copymapper.runtime.layout;

/** The kind of an open decision of a mapping spec. */
public enum OpenDecisionKind {
    /** The runtime does not support a feature of the item. */
    UNSUPPORTED_TYPE,
    /** The COBOL side of the item is not valid. */
    INVALID_COBOL,
    /** A REDEFINES cannot be resolved. */
    UNRESOLVED_REDEFINES,
    /** A variant is not valid. */
    INVALID_VARIANT,
    /** A table is not valid. */
    INVALID_TABLE,
    /** The JSON name is missing or not unique. */
    INVALID_JSON_NAME,
    /** The direction is not valid. */
    INVALID_DIRECTION,
    /** The JSON type is missing or does not fit the picture. */
    INVALID_TYPE
}
