package pt.dazz.copymapper.copybook.spec;

/** The JSON type of a field. */
public enum JsonType {
    /** A JSON string. */
    TEXT,
    /** A JSON number. */
    NUMBER,
    /** A decimal number written as a JSON string. */
    DECIMAL_STRING,
    /** A date. */
    DATE,
    /** A time. */
    TIME,
    /** A date and time. */
    DATE_TIME
}
