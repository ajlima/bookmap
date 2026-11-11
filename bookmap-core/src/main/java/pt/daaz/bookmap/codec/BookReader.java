package pt.daaz.bookmap.codec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.internal.BinaryInteger;
import pt.daaz.bookmap.codec.internal.PackedDecimal;
import pt.daaz.bookmap.codec.internal.TemporalCodec;
import pt.daaz.bookmap.codec.internal.TextCodec;
import pt.daaz.bookmap.codec.internal.ZonedDecimal;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.convert.FieldContext;
import pt.daaz.bookmap.convert.FieldConverter;
import pt.daaz.bookmap.exception.BookmapException;
import pt.daaz.bookmap.exception.MappingException;

/**
 * Reads the fields of a book from a byte array. Generated codecs call this class.
 */
public final class BookReader {

    private final byte[] data;
    private final CodecContext ctx;
    private int position;

    /**
     * Creates a reader.
     *
     * @param data the response bytes
     * @param ctx the codec context
     */
    public BookReader(byte[] data, CodecContext ctx) {
        this.data = data;
        this.ctx = ctx;
    }

    /**
     * Reads a text field. The method removes spaces on the right.
     *
     * @param field the field descriptor, for error messages
     * @param length the field length in bytes
     * @return the text, never null
     * @throws MappingException if the response is too short or the charset cannot decode a byte
     */
    public String readText(String field, int length) {
        int from = take(field, length);
        return TextCodec.decode(field, data, from, length, ctx.charset());
    }

    /**
     * Reads a numeric field.
     *
     * @param field the field descriptor, for error messages
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param usage the storage format
     * @return the number, or null if the field is blank
     */
    public BigDecimal readNumber(String field, int length, int scale, boolean signed,
                                 Usage usage) {
        int size = BookWriter.byteLength(usage, length);
        int from = take(field, size);
        return switch (usage) {
            case DISPLAY -> ZonedDecimal.decode(field, data, from, length, scale, signed,
                ctx.charset());
            case COMP3 -> PackedDecimal.decode(field, data, from, length, scale, signed,
                ctx.charset());
            case COMP -> BinaryInteger.decode(data, from, size, scale, signed);
        };
    }

    /**
     * Reads a date field.
     *
     * @param field the field descriptor, for error messages
     * @param length the field length in bytes
     * @param formatter the formatter, from {@link Formats#of(String)}
     * @return the date, or null if the field has only spaces, low-values, or zeros
     * @throws MappingException if the text is not a valid date
     */
    public LocalDate readDate(String field, int length, DateTimeFormatter formatter) {
        int from = take(field, length);
        return TemporalCodec.decode(field, data, from, length, formatter, ctx.charset(),
            LocalDate::from, null);
    }

    /**
     * Reads a time field.
     *
     * @param field the field descriptor, for error messages
     * @param length the field length in bytes
     * @param formatter the formatter, from {@link Formats#of(String)}
     * @return the time, midnight if the field has only zeros,
     *     or null if the field has only spaces or low-values
     * @throws MappingException if the text is not a valid time
     */
    public LocalTime readTime(String field, int length, DateTimeFormatter formatter) {
        int from = take(field, length);
        return TemporalCodec.decode(field, data, from, length, formatter, ctx.charset(),
            LocalTime::from, LocalTime.MIDNIGHT);
    }

    /**
     * Reads a date and time field.
     *
     * @param field the field descriptor, for error messages
     * @param length the field length in bytes
     * @param formatter the formatter, from {@link Formats#of(String)}
     * @return the date and time, or null if the field has only spaces, low-values, or zeros
     * @throws MappingException if the text is not a valid date and time
     */
    public LocalDateTime readDateTime(String field, int length, DateTimeFormatter formatter) {
        int from = take(field, length);
        return TemporalCodec.decode(field, data, from, length, formatter, ctx.charset(),
            LocalDateTime::from, null);
    }

    /**
     * Reads a field with a custom converter. The converter gets exactly
     * {@code context.byteLength()} bytes.
     *
     * @param <J> the Java type of the field
     * @param field the field descriptor, for error messages
     * @param converter the converter
     * @param context the description of the field
     * @return the value from the converter
     * @throws MappingException if the converter fails
     */
    public <J> J readConverted(String field, FieldConverter<J> converter,
                               FieldContext context) {
        int from = take(field, context.byteLength());
        byte[] bytes = Arrays.copyOfRange(data, from, from + context.byteLength());
        try {
            return converter.decode(bytes, context);
        } catch (BookmapException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MappingException(field + ": converter failed (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Gives the number of bytes read so far.
     *
     * @return the current position
     */
    public int position() {
        return position;
    }

    private int take(String field, int bytes) {
        if (data.length - position < bytes) {
            throw new MappingException(field + ": response too short, need " + bytes
                + " bytes at position " + position + " but only "
                + (data.length - position) + " remain");
        }
        int from = position;
        position += bytes;
        return from;
    }
}
