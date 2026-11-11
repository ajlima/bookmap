package pt.daaz.bookmap.convert;

/**
 * Converts one field between bytes and a Java value.
 *
 * @param <J> the Java type of the field
 */
public interface FieldConverter<J> {

    /**
     * Reads a Java value from bytes.
     *
     * @param bytes the bytes of the field
     * @param context the description of the field
     * @return the value
     */
    J decode(byte[] bytes, FieldContext context);

    /**
     * Writes a Java value to bytes.
     *
     * @param value the value
     * @param context the description of the field
     * @return the bytes of the field
     */
    byte[] encode(J value, FieldContext context);
}
