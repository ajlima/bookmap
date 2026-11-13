package pt.dazz.bookmap.copybook.model;

/**
 * Gives the Java type of a generated field.
 */
public enum JavaType {
    /** {@code String}. */
    STRING,
    /** {@code int}. */
    INT,
    /** {@code long}. */
    LONG,
    /** {@code java.math.BigDecimal}. */
    BIG_DECIMAL,
    /** A generated enum. {@link FieldMember#enumType()} gives its name. */
    ENUM
}
