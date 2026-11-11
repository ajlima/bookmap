package pt.daaz.bookmap.codec;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.internal.BinaryInteger;
import pt.daaz.bookmap.codec.internal.PackedDecimal;
import pt.daaz.bookmap.codec.internal.TextCodec;
import pt.daaz.bookmap.codec.internal.ZonedDecimal;
import pt.daaz.bookmap.convert.CodecContext;

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
     * Gives the bytes written so far.
     *
     * @return a copy of the bytes
     */
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
