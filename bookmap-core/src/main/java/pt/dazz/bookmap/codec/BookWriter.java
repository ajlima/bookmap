package pt.dazz.bookmap.codec;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.codec.internal.BinaryInteger;
import pt.dazz.bookmap.codec.internal.PackedDecimal;
import pt.dazz.bookmap.codec.internal.TemporalCodec;
import pt.dazz.bookmap.codec.internal.TextCodec;
import pt.dazz.bookmap.codec.internal.ZonedDecimal;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.convert.FieldContext;
import pt.dazz.bookmap.convert.FieldConverter;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.MappingException;

/**
 * Writes the fields of a book into a byte array. Generated codecs call this class.
 */
public final class BookWriter {

    private final CodecContext ctx;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * Creates a writer.
     *
     * @param ctx the codec context
     */
    public BookWriter(CodecContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Gives the length in bytes of a numeric field.
     * {@code DISPLAY} uses one byte for each digit.
     * {@code COMP3} uses {@code length / 2 + 1} bytes.
     * {@code COMP} uses 2, 4, or 8 bytes for 1-4, 5-9, or 10-18 digits.
     *
     * @param usage the storage format
     * @param length the number of digits
     * @return the length in bytes
     */
    public static int byteLength(Usage usage, int length) {
        return switch (usage) {
            case DISPLAY -> length;
            case COMP3 -> length / 2 + 1;
            case COMP -> length <= 4 ? 2 : length <= 9 ? 4 : 8;
        };
    }

    /**
     * Writes a text field. The method adds spaces on the right.
     *
     * @param field the field descriptor, for error messages
     * @param value the text, or null
     * @param length the field length in bytes
     */
    public void writeText(String field, String value, int length) {
        out.writeBytes(TextCodec.encode(field, value, length, ctx.charset()));
    }

    /**
     * Writes a numeric field.
     *
     * @param field the field descriptor, for error messages
     * @param value the number, or null
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param usage the storage format
     */
    public void writeNumber(String field, BigDecimal value, int length, int scale,
                            boolean signed, Usage usage) {
        out.writeBytes(switch (usage) {
            case DISPLAY -> ZonedDecimal.encode(field, value, length, scale, signed,
                ctx.charset());
            case COMP3 -> PackedDecimal.encode(field, value, length, scale, signed);
            case COMP -> BinaryInteger.encode(field, value, length, scale, signed,
                byteLength(usage, length));
        });
    }

    /**
     * Writes a date or time field as text. A null value writes zeros.
     *
     * @param field the field descriptor, for error messages
     * @param value the date or time, or null
     * @param length the field length in bytes, equal to the length of the formatted text
     * @param formatter the formatter, from {@link Formats#of(String)}
     * @throws MappingException if the pattern cannot keep the value (for example, a time with
     *     seconds and the pattern {@code HHmm}), or the text does not fit the length
     */
    public void writeTemporal(String field, TemporalAccessor value, int length,
                              DateTimeFormatter formatter) {
        out.writeBytes(TemporalCodec.encode(field, value, length, formatter, ctx.charset()));
    }

    /**
     * Writes a field with a custom converter. The converter must return exactly
     * {@code context.byteLength()} bytes.
     *
     * @param <J> the Java type of the field
     * @param field the field descriptor, for error messages
     * @param value the value
     * @param converter the converter
     * @param context the description of the field
     * @throws MappingException if the converter fails or returns a wrong number of bytes
     */
    public <J> void writeConverted(String field, J value, FieldConverter<J> converter,
                                   FieldContext context) {
        byte[] bytes;
        try {
            bytes = converter.encode(value, context);
        } catch (BookmapException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MappingException(field + ": converter failed (" + e.getMessage() + ")", e);
        }
        if (bytes == null || bytes.length != context.byteLength()) {
            throw new MappingException(field + ": converter must return "
                + context.byteLength() + " bytes but returned "
                + (bytes == null ? "null" : bytes.length + " bytes"));
        }
        out.writeBytes(bytes);
    }

    /**
     * Writes spaces of the context charset. A codec uses this method to fill the end of a
     * subtype that is shorter than the longest subtype.
     *
     * @param length the number of bytes
     */
    public void writeFiller(int length) {
        writeText("filler", null, length);
    }

    /**
     * Writes LOW-VALUES (bytes {@code 0x00}). A codec uses this method to fill the unused slots
     * of a fixed collection.
     *
     * @param length the number of bytes
     */
    public void writeLowValues(int length) {
        out.write(new byte[length], 0, length);
    }

    /**
     * Tells if all bytes from {@code start} to the current position are LOW-VALUES. A codec
     * uses this method to reject an item that a read would find empty.
     *
     * @param start the position of the first byte
     * @return {@code true} if at least one byte is in the range and all bytes are {@code 0x00}
     */
    public boolean isLowValuesFrom(int start) {
        byte[] bytes = out.toByteArray();
        if (start >= bytes.length) {
            return false;
        }
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes unused slots of a fixed collection. The marker gives the bytes of each slot.
     *
     * @param marker the marker of an empty slot
     * @param slotLength the number of bytes of one slot
     * @param slots the number of unused slots
     */
    public void writeEmptySlots(EmptySlot marker, int slotLength, int slots) {
        byte[] slot = marker.fill(slotLength, ctx.charset());
        for (int i = 0; i < slots; i++) {
            out.writeBytes(slot);
        }
    }

    /**
     * Tells if the bytes from {@code start} to the current position make an empty slot. A
     * codec uses this method to reject an item that a read would find empty.
     *
     * @param field the descriptor of the slot, for error messages
     * @param marker the marker of an empty slot
     * @param start the position of the first byte of the slot
     * @return {@code true} if at least one byte is in the range and the marker finds the slot
     *     empty
     */
    public boolean isEmptySlotFrom(String field, EmptySlot marker, int start) {
        byte[] bytes = out.toByteArray();
        return start < bytes.length
            && marker.isEmpty(field, bytes, start, bytes.length - start, ctx.charset());
    }

    /**
     * Gives the number of bytes written so far.
     *
     * @return the current position
     */
    public int position() {
        return out.size();
    }

    /**
     * Gives the bytes written so far.
     *
     * @return a copy of the bytes
     */
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
