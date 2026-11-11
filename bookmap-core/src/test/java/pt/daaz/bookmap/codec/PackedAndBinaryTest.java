package pt.daaz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.exception.MappingException;

class PackedAndBinaryTest {

    static final CodecContext EBCDIC = CodecContext.defaults();
    static final String F = "Invoice.total (order 20)";

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] write(String value, int length, int scale, boolean signed, Usage usage) {
        var w = new BookWriter(EBCDIC);
        w.writeNumber(F, value == null ? null : new BigDecimal(value), length, scale, signed,
            usage);
        return w.toByteArray();
    }

    private static BigDecimal read(CodecContext ctx, byte[] data, int length, int scale,
                                   boolean signed, Usage usage) {
        return new BookReader(data, ctx).readNumber(F, length, scale, signed, usage);
    }

    private static BigDecimal read(byte[] data, int length, int scale, boolean signed,
                                   Usage usage) {
        return read(EBCDIC, data, length, scale, signed, usage);
    }

    // ---- packed ----

    @Test void packedSignedPositive() {
        assertThat(write("12345", 5, 0, true, Usage.COMP3)).containsExactly(bytes(0x12, 0x34, 0x5C));
    }

    @Test void packedSignedNegative() {
        assertThat(write("-12345", 5, 0, true, Usage.COMP3)).containsExactly(bytes(0x12, 0x34, 0x5D));
    }

    @Test void packedUnsigned() {
        assertThat(write("123", 3, 0, false, Usage.COMP3)).containsExactly(bytes(0x12, 0x3F));
    }

    @Test void packedEvenLength() {
        assertThat(write("1234", 4, 0, true, Usage.COMP3)).containsExactly(bytes(0x01, 0x23, 0x4C));
    }

    @Test void packedWithScale() {
        byte[] packed = write("-1234567.89", 15, 2, true, Usage.COMP3);
        assertThat(packed).hasSize(8);
        assertThat(read(packed, 15, 2, true, Usage.COMP3)).isEqualByComparingTo("-1234567.89");
    }

    @Test void packedRoundTripKeepsScale() {
        assertThat(read(bytes(0x01, 0x23, 0x4C), 4, 2, true, Usage.COMP3).toPlainString())
            .isEqualTo("12.34");
    }

    @Test void packedReadAcceptsAlternativeSigns() {
        assertThat(read(bytes(0x12, 0x3F), 3, 0, false, Usage.COMP3)).isEqualByComparingTo("123");
        assertThat(read(bytes(0x12, 0x3A), 3, 0, true, Usage.COMP3)).isEqualByComparingTo("123");
        assertThat(read(bytes(0x12, 0x3B), 3, 0, true, Usage.COMP3)).isEqualByComparingTo("-123");
        assertThat(read(bytes(0x12, 0x3E), 3, 0, true, Usage.COMP3)).isEqualByComparingTo("123");
    }

    @Test void packedInvalidNibbleFails() {
        assertThatThrownBy(() -> read(bytes(0x1A, 0x3C), 3, 0, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedInvalidSignNibbleFails() {
        assertThatThrownBy(() -> read(bytes(0x12, 0x30), 3, 0, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedNegativeInUnsignedFieldFails() {
        assertThatThrownBy(() -> read(bytes(0x12, 0x3D), 3, 0, false, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedNonZeroPadNibbleFails() {
        assertThatThrownBy(() -> read(bytes(0x11, 0x23, 0x4C), 4, 0, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedLowValuesReadAsNull() {
        assertThat(read(bytes(0x00, 0x00), 3, 0, true, Usage.COMP3)).isNull();
    }

    @Test void packedSpacesReadAsNull() {
        assertThat(read(bytes(0x40, 0x40), 3, 0, true, Usage.COMP3)).isNull();
        var ascii = new CodecContext(StandardCharsets.US_ASCII);
        assertThat(read(ascii, bytes(0x20, 0x20), 3, 0, true, Usage.COMP3)).isNull();
    }

    @Test void packedNullWritesZeroWithPositiveSign() {
        assertThat(write(null, 5, 0, true, Usage.COMP3)).containsExactly(bytes(0x00, 0x00, 0x0C));
        assertThat(write(null, 5, 0, false, Usage.COMP3)).containsExactly(bytes(0x00, 0x00, 0x0F));
    }

    @Test void packedTooManyDigitsFails() {
        assertThatThrownBy(() -> write("1000", 3, 0, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedNegativeInUnsignedWriteFails() {
        assertThatThrownBy(() -> write("-1", 3, 0, false, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedTooManyDecimalsFails() {
        assertThatThrownBy(() -> write("1.234", 5, 2, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedShortResponseFails() {
        assertThatThrownBy(() -> read(bytes(0x12), 3, 0, true, Usage.COMP3))
            .isInstanceOf(MappingException.class).hasMessageContaining("response too short");
    }

    // ---- binary ----

    @Test void binaryTwoBytesSigned() {
        assertThat(write("-2", 4, 0, true, Usage.COMP)).containsExactly(bytes(0xFF, 0xFE));
        assertThat(read(bytes(0xFF, 0xFE), 4, 0, true, Usage.COMP)).isEqualByComparingTo("-2");
    }

    @Test void binaryFourBytes() {
        assertThat(write("123456789", 9, 0, false, Usage.COMP))
            .containsExactly(bytes(0x07, 0x5B, 0xCD, 0x15));
        assertThat(read(bytes(0x07, 0x5B, 0xCD, 0x15), 9, 0, false, Usage.COMP))
            .isEqualByComparingTo("123456789");
    }

    @Test void binaryEightBytes() {
        assertThat(write("1", 18, 0, true, Usage.COMP))
            .containsExactly(bytes(0, 0, 0, 0, 0, 0, 0, 1));
        assertThat(write("-1", 18, 0, true, Usage.COMP))
            .containsExactly(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF));
    }

    @Test void binaryUnsignedReadsHighBitAsMagnitude() {
        assertThat(read(bytes(0xFF, 0xFF), 4, 0, false, Usage.COMP))
            .isEqualByComparingTo("65535");
        assertThat(read(bytes(0xFF, 0xFF), 4, 0, true, Usage.COMP)).isEqualByComparingTo("-1");
    }

    @Test void binaryTooManyDigitsFails() {
        assertThatThrownBy(() -> write("10000", 4, 0, true, Usage.COMP))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void binaryUnsignedNegativeFails() {
        assertThatThrownBy(() -> write("-1", 4, 0, false, Usage.COMP))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void binaryRoundTripWithScale() {
        byte[] data = write("123.45", 7, 2, true, Usage.COMP);
        assertThat(data).hasSize(4);
        assertThat(read(data, 7, 2, true, Usage.COMP).toPlainString()).isEqualTo("123.45");
    }

    @Test void binaryZeroReadsAsZero() {
        assertThat(read(bytes(0x00, 0x00), 4, 0, true, Usage.COMP)).isEqualByComparingTo("0");
    }

    @Test void binaryNullWritesZero() {
        assertThat(write(null, 4, 0, true, Usage.COMP)).containsExactly(bytes(0x00, 0x00));
        assertThat(write(null, 18, 0, false, Usage.COMP)).containsExactly(new byte[8]);
    }

    @Test void binaryReadAcceptsValueAboveDigits() {
        assertThat(read(bytes(0x2E, 0xE0), 4, 0, true, Usage.COMP)).isEqualByComparingTo("12000");
    }

    @Test void binaryShortResponseFails() {
        assertThatThrownBy(() -> read(bytes(0x01), 4, 0, true, Usage.COMP))
            .isInstanceOf(MappingException.class).hasMessageContaining("response too short");
    }
}
