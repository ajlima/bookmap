package pt.daaz.bookmap.convert;

import java.nio.charset.Charset;
import pt.daaz.bookmap.annotation.Usage;

/**
 * Describes one field to a {@link FieldConverter}.
 *
 * @param field the field name
 * @param length the number of digits or characters
 * @param scale the number of decimal places
 * @param signed true if the field has a sign
 * @param usage the storage format
 * @param pattern the date or time pattern
 * @param byteLength the length of the field in bytes
 * @param charset the charset of the text fields
 */
public record FieldContext(String field, int length, int scale, boolean signed, Usage usage,
                           String pattern, int byteLength, Charset charset) {
}
