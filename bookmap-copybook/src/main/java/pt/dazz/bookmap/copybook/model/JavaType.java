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
    /** {@code java.time.LocalDate}, from a date hint. */
    LOCAL_DATE,
    /** {@code java.time.LocalTime}, from a date hint. */
    LOCAL_TIME,
    /** {@code java.time.LocalDateTime}, from a date hint. */
    LOCAL_DATE_TIME;

    /**
     * Tells if the type is a date or a time.
     *
     * @return {@code true} for {@link #LOCAL_DATE}, {@link #LOCAL_TIME}, and
     *     {@link #LOCAL_DATE_TIME}
     */
    public boolean temporal() {
        return ordinal() >= LOCAL_DATE.ordinal();
    }
}
