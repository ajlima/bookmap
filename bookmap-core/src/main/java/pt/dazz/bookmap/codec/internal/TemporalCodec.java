package pt.dazz.bookmap.codec.internal;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import pt.dazz.bookmap.exception.MappingException;

/**
 * Converts date and time fields between Java values and bytes: text or packed decimal.
 */
public final class TemporalCodec {

    private TemporalCodec() {
    }

    /**
     * Formats a value and encodes the text. A null value gives zeros.
     *
     * <p>The method parses the text again and compares the result with the value. If they are
     * different, the pattern cannot keep the value. Examples: a time with seconds and the
     * pattern {@code HHmm}, a time with nanoseconds and the pattern {@code HHmmss}, or the year
     * 1975 and the pattern {@code yyMMdd} (a two-digit year reads as 2000 to 2099).
     * Then the method throws an exception. It never truncates a value silently.
     *
     * @param field the field descriptor, for error messages
     * @param value the date or time, or null
     * @param length the field length in bytes
     * @param formatter the formatter
     * @param cs the single-byte charset
     * @return exactly {@code length} bytes
     * @throws MappingException if the pattern cannot keep the value, or the value does not
     *     fit the length
     */
    public static byte[] encode(String field, TemporalAccessor value, int length,
                                DateTimeFormatter formatter, Charset cs) {
        String text = value == null ? "0".repeat(length)
            : format(field, value, length, formatter);
        return TextCodec.encode(field, text, length, cs);
    }

    /**
     * Formats a value and encodes the digits as a packed decimal. A null value gives zero.
     * The text checks are the same as in
     * {@link #encode(String, TemporalAccessor, int, DateTimeFormatter, Charset)}.
     *
     * @param field the field descriptor, for error messages
     * @param value the date or time, or null
     * @param length the number of digits
     * @param signed true if the field has a sign: the sign nibble is {@code C}, else {@code F}
     * @param formatter the formatter. It must give digits only
     * @return exactly {@code length / 2 + 1} bytes
     * @throws MappingException if the pattern cannot keep the value, or the value does not
     *     fit the length
     */
    public static byte[] encodePacked(String field, TemporalAccessor value, int length,
                                      boolean signed, DateTimeFormatter formatter) {
        BigDecimal number = value == null ? BigDecimal.ZERO
            : new BigDecimal(format(field, value, length, formatter));
        return PackedDecimal.encode(field, number, length, 0, signed);
    }

    /**
     * Decodes the text of a field and parses it.
     *
     * @param <T> the Java type
     * @param field the field descriptor, for error messages
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param length the field length in bytes
     * @param formatter the formatter
     * @param cs the single-byte charset
     * @param query makes the Java value from the parsed fields
     * @param zeros the value for a field with only zeros
     * @return the value, or null for a blank field
     * @throws MappingException if the text is not a valid date or time, or the charset cannot
     *     decode a byte
     */
    public static <T> T decode(String field, byte[] bytes, int from, int length,
                               DateTimeFormatter formatter, Charset cs,
                               Function<TemporalAccessor, T> query, T zeros) {
        if (Blank.isBlank(bytes, from, length, cs)) {
            return null;
        }
        String text = TextCodec.decodeStrict(field, bytes, from, length, cs);
        return parse(field, text, formatter, query, zeros);
    }

    /**
     * Decodes a packed decimal and parses its digits. The text has {@code length} digits,
     * with the leading zeros. A negative sign nibble ({@code B} or {@code D}) is an error,
     * also with zero digits.
     *
     * @param <T> the Java type
     * @param field the field descriptor, for error messages
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param length the number of digits
     * @param signed true if the field has a sign
     * @param formatter the formatter
     * @param cs the charset that defines the space
     * @param query makes the Java value from the parsed fields
     * @param zeros the value for a field with only zeros
     * @return the value, or null for a field with only spaces or low-values
     * @throws MappingException if a nibble is not valid, the sign is negative, or the digits
     *     are not a valid date or time
     */
    public static <T> T decodePacked(String field, byte[] bytes, int from, int length,
                                     boolean signed, DateTimeFormatter formatter, Charset cs,
                                     Function<TemporalAccessor, T> query, T zeros) {
        int size = length / 2 + 1;
        if (Blank.isBlank(bytes, from, size, cs)) {
            return null;
        }
        int sign = bytes[from + size - 1] & 0x0F;
        if (sign == 0xB || sign == 0xD) {
            throw new MappingException(field + ": a date or time must not be negative (sign"
                + " nibble " + Integer.toHexString(sign).toUpperCase(Locale.ROOT) + ")");
        }
        BigDecimal number = PackedDecimal.decode(field, bytes, from, length, 0, signed, cs);
        String digits = number.toPlainString();
        String text = "0".repeat(length - digits.length()) + digits;
        return parse(field, text, formatter, query, zeros);
    }

    /** Formats a value, checks the length, and checks that the text gives the same value. */
    private static String format(String field, TemporalAccessor value, int length,
                                 DateTimeFormatter formatter) {
        String text;
        try {
            text = formatter.format(value);
        } catch (DateTimeException e) {
            throw new MappingException(field + ": value cannot be formatted with the "
                + "pattern (" + e.getMessage() + ")", e);
        }
        if (text.length() != length) {
            throw new MappingException(field + ": formatted value has " + text.length()
                + " characters but the field length is " + length);
        }
        checkRoundTrip(field, value, text, formatter);
        return text;
    }

    /** Parses a text. A text with only zeros gives {@code zeros}. */
    private static <T> T parse(String field, String text, DateTimeFormatter formatter,
                               Function<TemporalAccessor, T> query, T zeros) {
        if (text.chars().allMatch(c -> c == '0')) {
            return zeros;
        }
        try {
            return query.apply(formatter.parse(text));
        } catch (DateTimeException e) {
            throw new MappingException(field + ": '" + text + "' is not a valid value for "
                + "the pattern (" + e.getMessage() + ")", e);
        }
    }

    /** Parses the formatted text and makes sure that it gives the same date and time. */
    private static void checkRoundTrip(String field, TemporalAccessor value, String text,
                                       DateTimeFormatter formatter) {
        String problem = field + ": value " + value + " cannot be represented by the pattern";
        TemporalAccessor parsed;
        try {
            parsed = formatter.parse(text);
        } catch (DateTimeException e) {
            throw new MappingException(problem + " (" + e.getMessage() + ")", e);
        }
        if (!Objects.equals(value.query(TemporalQueries.localDate()),
                parsed.query(TemporalQueries.localDate()))
            || !Objects.equals(value.query(TemporalQueries.localTime()),
                parsed.query(TemporalQueries.localTime()))) {
            throw new MappingException(problem + " (the text '" + text
                + "' gives a different value)");
        }
    }
}
