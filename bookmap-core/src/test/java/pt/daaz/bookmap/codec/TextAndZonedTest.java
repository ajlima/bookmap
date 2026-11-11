package pt.daaz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.internal.Blank;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.exception.MappingException;

class TextAndZonedTest {

    static final CodecContext EBCDIC = CodecContext.defaults();
    static final String F = "Order.code (order 10)";

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] write(BigDecimal value, int length, int scale, boolean signed) {
        var w = new BookWriter(EBCDIC);
        w.writeNumber(F, value, length, scale, signed, Usage.DISPLAY);
        return w.toByteArray();
    }

    private static BigDecimal read(CodecContext ctx, byte[] data, int length, int scale,
                                   boolean signed) {
        return new BookReader(data, ctx).readNumber(F, length, scale, signed, Usage.DISPLAY);
    }

    private static BigDecimal read(byte[] data, int length, int scale, boolean signed) {
        return read(EBCDIC, data, length, scale, signed);
    }

    @Test void textIsPaddedWithSpaces() {
        var w = new BookWriter(EBCDIC);
        w.writeText(F, "AB", 4);
        assertThat(w.toByteArray()).containsExactly(bytes(0xC1, 0xC2, 0x40, 0x40));
    }

    @Test void nullTextWritesSpaces() {
        var w = new BookWriter(EBCDIC);
        w.writeText(F, null, 3);
        assertThat(w.toByteArray()).containsExactly(bytes(0x40, 0x40, 0x40));
    }

    @Test void textTooLongFails() {
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeText(F, "ABCDE", 4)).isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F);
    }

    @Test void unmappableCharacterFails() {
        assertThatThrownBy(() -> new BookWriter(EBCDIC).writeText(F, "東京", 4))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void textReadTrimsRightAndLowValues() {
        var r = new BookReader(bytes(0xC1, 0x00, 0x40, 0x40), EBCDIC);
        assertThat(r.readText(F, 4)).isEqualTo("A");
    }

    @Test void blankTextReadsAsEmptyString() {
        assertThat(new BookReader(bytes(0x40, 0x40, 0x40, 0x40), EBCDIC).readText(F, 4)).isEmpty();
        assertThat(new BookReader(bytes(0, 0, 0, 0), EBCDIC).readText(F, 4)).isEmpty();
    }

    @Test void signedZonedPositive() {
        assertThat(write(BigDecimal.valueOf(123), 3, 0, true))
            .containsExactly(bytes(0xF1, 0xF2, 0xC3));
    }

    @Test void signedZonedNegative() {
        assertThat(write(BigDecimal.valueOf(-123), 3, 0, true))
            .containsExactly(bytes(0xF1, 0xF2, 0xD3));
    }

    @Test void unsignedZoned() {
        assertThat(write(BigDecimal.valueOf(42), 4, 0, false))
            .containsExactly(bytes(0xF0, 0xF0, 0xF4, 0xF2));
    }

    @Test void zonedWithScale() {
        byte[] out = write(new BigDecimal("12.34"), 5, 2, false);
        assertThat(out).containsExactly(bytes(0xF0, 0xF1, 0xF2, 0xF3, 0xF4));
        assertThat(read(out, 5, 2, false)).isEqualByComparingTo("12.34");
        assertThat(read(out, 5, 2, false).scale()).isEqualTo(2);
    }

    @Test void extraScaleWithSameValueIsAccepted() {
        byte[] out = write(new BigDecimal("10.500"), 5, 2, false);
        assertThat(read(out, 5, 2, false)).isEqualTo(new BigDecimal("10.50"));
    }

    @Test void extraScaleWithDifferentValueFails() {
        assertThatThrownBy(() -> write(new BigDecimal("10.505"), 5, 2, false))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void tooManyDigitsFails() {
        assertThatThrownBy(() -> write(BigDecimal.valueOf(12345), 4, 0, false))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void negativeInUnsignedFails() {
        assertThatThrownBy(() -> write(BigDecimal.valueOf(-1), 2, 0, false))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void nullNumberWritesZeros() {
        assertThat(write(null, 3, 0, false)).containsExactly(bytes(0xF0, 0xF0, 0xF0));
    }

    @Test void nullSignedNumberWritesPositiveZero() {
        assertThat(write(null, 3, 0, true)).containsExactly(bytes(0xF0, 0xF0, 0xC0));
    }

    @Test void blankZonedReadsAsNull() {
        assertThat(read(bytes(0x40, 0x40, 0x40), 3, 0, false)).isNull();
        assertThat(read(bytes(0, 0, 0), 3, 0, true)).isNull();
    }

    @Test void zonedReadRoundTripNegative() {
        assertThat(read(bytes(0xF1, 0xF2, 0xD3), 3, 0, true)).isEqualByComparingTo("-123");
    }

    @Test void zonedReadAcceptsAlternatePositiveAndNegativeZones() {
        assertThat(read(bytes(0xF1, 0xF2, 0xA3), 3, 0, true)).isEqualByComparingTo("123");
        assertThat(read(bytes(0xF1, 0xF2, 0xE3), 3, 0, true)).isEqualByComparingTo("123");
        assertThat(read(bytes(0xF1, 0xF2, 0xF3), 3, 0, true)).isEqualByComparingTo("123");
        assertThat(read(bytes(0xF1, 0xF2, 0xB3), 3, 0, true)).isEqualByComparingTo("-123");
    }

    @Test void malformedZonedFails() {
        var r = new BookReader(bytes(0xF1, 0xC1, 0xF3), EBCDIC);
        assertThatThrownBy(() -> r.readNumber(F, 3, 0, false, Usage.DISPLAY))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void invalidDigitNibbleFails() {
        assertThatThrownBy(() -> read(bytes(0xF1, 0xFA, 0xF3), 3, 0, false))
            .isInstanceOf(MappingException.class);
    }

    @Test void invalidLastZoneFails() {
        assertThatThrownBy(() -> read(bytes(0xF1, 0xF2, 0x13), 3, 0, true))
            .isInstanceOf(MappingException.class);
    }

    @Test void shortResponseFails() {
        assertThatThrownBy(() -> new BookReader(new byte[2], EBCDIC).readText(F, 4))
            .isInstanceOf(MappingException.class).hasMessageContaining("response too short");
    }

    @Test void readerAdvancesPosition() {
        var r = new BookReader(bytes(0xC1, 0xC2, 0xF1, 0xF2), EBCDIC);
        r.readText(F, 2);
        assertThat(r.position()).isEqualTo(2);
        r.readNumber(F, 2, 0, false, Usage.DISPLAY);
        assertThat(r.position()).isEqualTo(4);
    }

    @Test void overpunchOnGermanCodePage() {
        var ctx = new CodecContext(Charset.forName("IBM273"));
        var w = new BookWriter(ctx);
        w.writeNumber(F, BigDecimal.valueOf(120), 3, 0, true, Usage.DISPLAY);
        w.writeNumber(F, BigDecimal.valueOf(-120), 3, 0, true, Usage.DISPLAY);
        byte[] out = w.toByteArray();
        assertThat(out).containsExactly(bytes(0xF1, 0xF2, 0xC0, 0xF1, 0xF2, 0xD0));
        var r = new BookReader(out, ctx);
        assertThat(r.readNumber(F, 3, 0, true, Usage.DISPLAY)).isEqualByComparingTo("120");
        assertThat(r.readNumber(F, 3, 0, true, Usage.DISPLAY)).isEqualByComparingTo("-120");
    }

    @Test void overpunchOnAsciiCharset() {
        var ctx = new CodecContext(StandardCharsets.US_ASCII);
        var w = new BookWriter(ctx);
        w.writeNumber(F, BigDecimal.valueOf(123), 3, 0, true, Usage.DISPLAY);
        w.writeNumber(F, BigDecimal.valueOf(-120), 3, 0, true, Usage.DISPLAY);
        w.writeNumber(F, BigDecimal.valueOf(45), 3, 0, false, Usage.DISPLAY);
        byte[] out = w.toByteArray();
        assertThat(new String(out, StandardCharsets.US_ASCII)).isEqualTo("12C12}045");
        var r = new BookReader(out, ctx);
        assertThat(r.readNumber(F, 3, 0, true, Usage.DISPLAY)).isEqualByComparingTo("123");
        assertThat(r.readNumber(F, 3, 0, true, Usage.DISPLAY)).isEqualByComparingTo("-120");
        assertThat(r.readNumber(F, 3, 0, false, Usage.DISPLAY)).isEqualByComparingTo("45");
    }

    @Test void asciiMalformedFails() {
        var ctx = new CodecContext(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> read(ctx, "1A3".getBytes(StandardCharsets.US_ASCII), 3, 0, true))
            .isInstanceOf(MappingException.class);
        assertThatThrownBy(() -> read(ctx, "12!".getBytes(StandardCharsets.US_ASCII), 3, 0, true))
            .isInstanceOf(MappingException.class);
        assertThatThrownBy(() -> read(ctx, "12}".getBytes(StandardCharsets.US_ASCII), 3, 0, false))
            .isInstanceOf(MappingException.class);
    }

    @Test void unsignedAcceptsZoneCRejectsZoneD() {
        assertThat(read(bytes(0xF1, 0xF2, 0xC3), 3, 0, false)).isEqualByComparingTo("123");
        assertThatThrownBy(() -> read(bytes(0xF1, 0xF2, 0xD3), 3, 0, false))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F);
    }

    @Test void packedAndBinaryAreNotSupportedYet() {
        var w = new BookWriter(EBCDIC);
        var r = new BookReader(new byte[8], EBCDIC);
        assertThatThrownBy(() -> w.writeNumber(F, null, 3, 0, true, Usage.COMP3))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> r.readNumber(F, 3, 0, true, Usage.COMP))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void byteLengthFollowsUsage() {
        assertThat(BookWriter.byteLength(Usage.DISPLAY, 7)).isEqualTo(7);
        assertThat(BookWriter.byteLength(Usage.COMP3, 7)).isEqualTo(4);
        assertThat(BookWriter.byteLength(Usage.COMP3, 8)).isEqualTo(5);
        assertThat(BookWriter.byteLength(Usage.COMP, 4)).isEqualTo(2);
        assertThat(BookWriter.byteLength(Usage.COMP, 5)).isEqualTo(4);
        assertThat(BookWriter.byteLength(Usage.COMP, 9)).isEqualTo(4);
        assertThat(BookWriter.byteLength(Usage.COMP, 10)).isEqualTo(8);
    }

    @Test void blankDetectionUsesCharsetSpace() {
        byte[] data = bytes(0xC1, 0x40, 0x00, 0x40);
        assertThat(Blank.isBlank(data, 1, 3, EBCDIC.charset())).isTrue();
        assertThat(Blank.isBlank(data, 0, 4, EBCDIC.charset())).isFalse();
        assertThat(Blank.isBlank(new byte[] {' ', 0}, 0, 2, StandardCharsets.US_ASCII)).isTrue();
    }
}
