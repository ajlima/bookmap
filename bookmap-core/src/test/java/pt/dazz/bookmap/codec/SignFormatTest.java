package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

class SignFormatTest {

    static final CodecContext EBCDIC = CodecContext.defaults();
    static final CodecContext ASCII = new CodecContext(StandardCharsets.US_ASCII);
    static final String F = "Invoice.tax (order 30)";

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] write(CodecContext ctx, String value, Sign sign) {
        var w = new BookWriter(ctx);
        w.writeNumber(F, value == null ? null : new BigDecimal(value), 5, 2, true,
            Usage.DISPLAY, sign);
        return w.toByteArray();
    }

    private static BigDecimal read(CodecContext ctx, byte[] data, Sign sign) {
        var r = new BookReader(data, ctx);
        BigDecimal value = r.readNumber(F, 5, 2, true, Usage.DISPLAY, sign);
        assertThat(r.position()).isEqualTo(data.length);
        return value;
    }

    private static String ascii(byte[] data) {
        return new String(data, StandardCharsets.US_ASCII);
    }

    @Test void signFlags() {
        assertThat(Sign.TRAILING.leading()).isFalse();
        assertThat(Sign.TRAILING.separate()).isFalse();
        assertThat(Sign.LEADING.leading()).isTrue();
        assertThat(Sign.LEADING.separate()).isFalse();
        assertThat(Sign.TRAILING_SEPARATE.leading()).isFalse();
        assertThat(Sign.TRAILING_SEPARATE.separate()).isTrue();
        assertThat(Sign.LEADING_SEPARATE.leading()).isTrue();
        assertThat(Sign.LEADING_SEPARATE.separate()).isTrue();
    }

    @Test void ebcdicLayoutOfEachFormat() {
        assertThat(write(EBCDIC, "-12.34", Sign.TRAILING))
            .containsExactly(bytes(0xF0, 0xF1, 0xF2, 0xF3, 0xD4));
        assertThat(write(EBCDIC, "12.34", Sign.TRAILING))
            .containsExactly(bytes(0xF0, 0xF1, 0xF2, 0xF3, 0xC4));
        assertThat(write(EBCDIC, "-12.34", Sign.LEADING))
            .containsExactly(bytes(0xD0, 0xF1, 0xF2, 0xF3, 0xF4));
        assertThat(write(EBCDIC, "12.34", Sign.LEADING))
            .containsExactly(bytes(0xC0, 0xF1, 0xF2, 0xF3, 0xF4));
        assertThat(write(EBCDIC, "-12.34", Sign.TRAILING_SEPARATE))
            .containsExactly(bytes(0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0x60));
        assertThat(write(EBCDIC, "12.34", Sign.TRAILING_SEPARATE))
            .containsExactly(bytes(0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0x4E));
        assertThat(write(EBCDIC, "-12.34", Sign.LEADING_SEPARATE))
            .containsExactly(bytes(0x60, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4));
        assertThat(write(EBCDIC, "12.34", Sign.LEADING_SEPARATE))
            .containsExactly(bytes(0x4E, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4));
    }

    @Test void asciiLayoutOfEachFormat() {
        assertThat(ascii(write(ASCII, "-12.34", Sign.TRAILING))).isEqualTo("0123M");
        assertThat(ascii(write(ASCII, "12.34", Sign.TRAILING))).isEqualTo("0123D");
        assertThat(ascii(write(ASCII, "-12.34", Sign.LEADING))).isEqualTo("}1234");
        assertThat(ascii(write(ASCII, "12.34", Sign.LEADING))).isEqualTo("{1234");
        assertThat(ascii(write(ASCII, "-12.34", Sign.TRAILING_SEPARATE))).isEqualTo("01234-");
        assertThat(ascii(write(ASCII, "12.34", Sign.TRAILING_SEPARATE))).isEqualTo("01234+");
        assertThat(ascii(write(ASCII, "-12.34", Sign.LEADING_SEPARATE))).isEqualTo("-01234");
        assertThat(ascii(write(ASCII, "12.34", Sign.LEADING_SEPARATE))).isEqualTo("+01234");
    }

    @ParameterizedTest @EnumSource(Sign.class)
    void roundTripGivesTheValueAndTheBytes(Sign sign) {
        for (CodecContext ctx : new CodecContext[] {EBCDIC, ASCII}) {
            for (String value : new String[] {"-123.45", "123.45", "0.00", "-0.01", "999.99"}) {
                byte[] data = write(ctx, value, sign);
                BigDecimal back = read(ctx, data, sign);
                assertThat(back).isEqualTo(new BigDecimal(value));
                assertThat(write(ctx, back.toPlainString(), sign)).containsExactly(data);
            }
        }
    }

    @Test void zeroAndNullGetThePositiveSign() {
        assertThat(ascii(write(ASCII, "0", Sign.LEADING_SEPARATE))).isEqualTo("+00000");
        assertThat(ascii(write(ASCII, null, Sign.LEADING_SEPARATE))).isEqualTo("+00000");
        assertThat(ascii(write(ASCII, null, Sign.TRAILING_SEPARATE))).isEqualTo("00000+");
        assertThat(write(EBCDIC, null, Sign.LEADING))
            .containsExactly(bytes(0xC0, 0xF0, 0xF0, 0xF0, 0xF0));
    }

    @Test void negativeZeroReadsAsZero() {
        assertThat(read(ASCII, "-00000".getBytes(StandardCharsets.US_ASCII),
            Sign.LEADING_SEPARATE)).isEqualTo(new BigDecimal("0.00"));
        assertThat(read(EBCDIC, bytes(0xD0, 0xF0, 0xF0, 0xF0, 0xF0), Sign.LEADING))
            .isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest @EnumSource(Sign.class)
    void blankFieldReadsAsNull(Sign sign) {
        int size = BookWriter.byteLength(Usage.DISPLAY, 5, sign);
        assertThat(read(EBCDIC, new byte[size], sign)).isNull();
        byte[] spaces = " ".repeat(size).getBytes(StandardCharsets.US_ASCII);
        assertThat(read(ASCII, spaces, sign)).isNull();
    }

    @Test void leadingOverpunchAcceptsTheAlternateZones() {
        assertThat(read(EBCDIC, bytes(0xA1, 0xF2, 0xF3, 0xF4, 0xF5), Sign.LEADING))
            .isEqualByComparingTo("123.45");
        assertThat(read(EBCDIC, bytes(0xE1, 0xF2, 0xF3, 0xF4, 0xF5), Sign.LEADING))
            .isEqualByComparingTo("123.45");
        assertThat(read(EBCDIC, bytes(0xF1, 0xF2, 0xF3, 0xF4, 0xF5), Sign.LEADING))
            .isEqualByComparingTo("123.45");
        assertThat(read(EBCDIC, bytes(0xB1, 0xF2, 0xF3, 0xF4, 0xF5), Sign.LEADING))
            .isEqualByComparingTo("-123.45");
        assertThat(read(ASCII, "12345".getBytes(StandardCharsets.US_ASCII), Sign.LEADING))
            .isEqualByComparingTo("123.45");
    }

    @Test void leadingOverpunchRejectsASignInTheLastByte() {
        assertThatThrownBy(() -> read(EBCDIC, bytes(0xF1, 0xF2, 0xF3, 0xF4, 0xD5), Sign.LEADING))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0xD5 at offset 4 of the field is not valid in a zoned"
                + " decimal");
        assertThatThrownBy(() -> read(ASCII, "1234N".getBytes(StandardCharsets.US_ASCII),
            Sign.LEADING)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0x4E at offset 4 of the field is not valid in a zoned"
                + " decimal");
    }

    @Test void separateSignMustBePlusOrMinus() {
        assertThatThrownBy(() -> read(EBCDIC, bytes(0x40, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5),
            Sign.LEADING_SEPARATE)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0x40 at offset 0 of the field is not a valid sign. The sign"
                + " must be '+' or '-'");
        assertThatThrownBy(() -> read(ASCII, "123450".getBytes(StandardCharsets.US_ASCII),
            Sign.TRAILING_SEPARATE)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0x30 at offset 5 of the field is not a valid sign. The sign"
                + " must be '+' or '-'");
    }

    @Test void unsignedFieldRejectsAMinusSign() {
        var r = new BookReader("-123".getBytes(StandardCharsets.US_ASCII), ASCII);
        assertThatThrownBy(() -> r.readNumber(F, 3, 0, false, Usage.DISPLAY,
            Sign.LEADING_SEPARATE)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0x2D at offset 0 of the field is not a valid sign. The sign"
                + " must be '+' or '-'");
        var plus = new BookReader("+123".getBytes(StandardCharsets.US_ASCII), ASCII);
        assertThat(plus.readNumber(F, 3, 0, false, Usage.DISPLAY, Sign.LEADING_SEPARATE))
            .isEqualByComparingTo("123");
    }

    @Test void separateSignNeedsPlainDigits() {
        assertThatThrownBy(() -> read(EBCDIC, bytes(0x4E, 0xF1, 0xF2, 0xF3, 0xF4, 0xC5),
            Sign.LEADING_SEPARATE)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0xC5 at offset 5 of the field is not valid in a zoned"
                + " decimal");
        assertThatThrownBy(() -> read(ASCII, "1234E+".getBytes(StandardCharsets.US_ASCII),
            Sign.TRAILING_SEPARATE)).isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte 0x45 at offset 4 of the field is not valid in a zoned"
                + " decimal");
    }

    @Test void separateSignOnGermanCodePage() {
        var ctx = new CodecContext(Charset.forName("IBM273"));
        byte[] data = write(ctx, "-1.20", Sign.TRAILING_SEPARATE);
        assertThat(data).containsExactly(bytes(0xF0, 0xF0, 0xF1, 0xF2, 0xF0, 0x60));
        assertThat(read(ctx, data, Sign.TRAILING_SEPARATE)).isEqualByComparingTo("-1.20");
    }

    @Test void byteLengthCountsTheSeparateSign() {
        assertThat(BookWriter.byteLength(Usage.DISPLAY, 7, Sign.TRAILING)).isEqualTo(7);
        assertThat(BookWriter.byteLength(Usage.DISPLAY, 7, Sign.LEADING)).isEqualTo(7);
        assertThat(BookWriter.byteLength(Usage.DISPLAY, 7, Sign.TRAILING_SEPARATE)).isEqualTo(8);
        assertThat(BookWriter.byteLength(Usage.DISPLAY, 7, Sign.LEADING_SEPARATE)).isEqualTo(8);
        assertThat(BookWriter.byteLength(Usage.COMP3, 7, Sign.TRAILING)).isEqualTo(4);
    }

    @Test void emptySlotWithASeparateSignKey() {
        EmptySlot marker = EmptySlot.lowValues()
            .withNumberKey(1, 3, 0, true, Usage.DISPLAY, Sign.LEADING_SEPARATE);
        byte[] slot = marker.fill(6, StandardCharsets.US_ASCII);
        assertThat(slot).containsExactly(bytes(0, '+', '0', '0', '0', 0));
        assertThat(marker.isEmpty(F, slot, 0, 6, StandardCharsets.US_ASCII)).isTrue();
        byte[] used = bytes(0, '-', '0', '0', '7', 0);
        assertThat(marker.isEmpty(F, used, 0, 6, StandardCharsets.US_ASCII)).isFalse();
    }
}
