package pt.daaz.bookmap.codec;

import java.math.BigDecimal;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.internal.BinaryInteger;
import pt.daaz.bookmap.codec.internal.PackedDecimal;
import pt.daaz.bookmap.codec.internal.TextCodec;
import pt.daaz.bookmap.codec.internal.ZonedDecimal;
import pt.daaz.bookmap.convert.CodecContext;
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
     */
    public String readText(String field, int length) {
        int from = take(field, length);
        return TextCodec.decode(data, from, length, ctx.charset());
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
