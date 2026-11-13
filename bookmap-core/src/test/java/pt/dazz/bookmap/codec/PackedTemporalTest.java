package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

class PackedTemporalTest {

    static final CodecContext EBCDIC = CodecContext.defaults();
    static final String F = "Order.orderDate (order 10)";
    static final DateTimeFormatter DATE = Formats.of("yyyyMMdd");
    static final DateTimeFormatter TIME = Formats.of("HHmmss");
    static final DateTimeFormatter DATE_TIME = Formats.of("yyyyMMddHHmmss");

    private static byte[] hex(String text) {
        return HexFormat.of().parseHex(text);
    }

    private static String write(TemporalAccessor value, int length, boolean signed,
                                DateTimeFormatter formatter) {
        var w = new BookWriter(EBCDIC);
        w.writePackedTemporal(F, value, length, signed, formatter);
        return HexFormat.of().withUpperCase().formatHex(w.toByteArray());
    }

    private static LocalDate date(String bytes, int length, boolean signed,
                                  DateTimeFormatter formatter) {
        return new BookReader(hex(bytes), EBCDIC).readPackedDate(F, length, signed, formatter);
    }

    private static LocalTime time(String bytes, int length, boolean signed) {
        return new BookReader(hex(bytes), EBCDIC).readPackedTime(F, length, signed, TIME);
    }

    private static LocalDateTime dateTime(String bytes, int length, boolean signed) {
        return new BookReader(hex(bytes), EBCDIC).readPackedDateTime(F, length, signed,
            DATE_TIME);
    }

    @Test void writesSignedAndUnsignedDates() {
        LocalDate date = LocalDate.of(2026, 11, 13);
        assertThat(write(date, 8, true, DATE)).isEqualTo("020261113C");
        assertThat(write(date, 8, false, DATE)).isEqualTo("020261113F");
        assertThat(write(date, 7, true, Formats.of("yyyyDDD"))).isEqualTo("2026317C");
        assertThat(write(date, 7, false, Formats.of("'0'yyMMdd"))).isEqualTo("0261113F");
    }

    @Test void readsDates() {
        assertThat(date("020261113C", 8, true, DATE)).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(date("020261113F", 8, false, DATE)).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(date("020261113C", 8, false, DATE)).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(date("2026317F", 7, true, Formats.of("yyyyDDD")))
            .isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(date("0126317C", 7, true, Formats.of("'01'yyDDD")))
            .isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test void timeKeepsLeadingZeros() {
        assertThat(write(LocalTime.of(0, 15), 6, true, TIME)).isEqualTo("0001500C");
        assertThat(time("0001500C", 6, true)).isEqualTo(LocalTime.of(0, 15));
        assertThat(new BookReader(hex("0130509F"), EBCDIC)
            .readPackedTime(F, 7, false, Formats.of("'0'HHmmss")))
            .isEqualTo(LocalTime.of(13, 5, 9));
    }

    @Test void dateTimeRoundTrip() {
        LocalDateTime stamp = LocalDateTime.of(2026, 11, 13, 23, 59, 58);
        String bytes = write(stamp, 14, true, DATE_TIME);
        assertThat(bytes).isEqualTo("020261113235958C");
        assertThat(dateTime(bytes, 14, true)).isEqualTo(stamp);
    }

    @Test void nullWritesPackedZero() {
        assertThat(write(null, 8, true, DATE)).isEqualTo("000000000C");
        assertThat(write(null, 6, false, TIME)).isEqualTo("0000000F");
    }

    @Test void zeroBlankAndLowValues() {
        assertThat(date("000000000C", 8, true, DATE)).isNull();
        assertThat(date("000000000F", 8, false, DATE)).isNull();
        assertThat(date("0000000000", 8, true, DATE)).isNull();
        assertThat(date("4040404040", 8, true, DATE)).isNull();
        assertThat(dateTime("000000000000000C", 14, true)).isNull();
        assertThat(time("0000000C", 6, true)).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(time("00000000", 6, true)).isNull();
        assertThat(time("40404040", 6, true)).isNull();
    }

    @Test void negativeSignFails() {
        assertThatThrownBy(() -> date("020261113D", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": a date or time must not be negative (sign nibble D)");
        assertThatThrownBy(() -> date("000000000B", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": a date or time must not be negative (sign nibble B)");
        assertThatThrownBy(() -> time("0000000D", 6, false))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": a date or time must not be negative (sign nibble D)");
    }

    @Test void invalidNibblesFail() {
        assertThatThrownBy(() -> date("02026111AC", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("not valid in a packed decimal");
        assertThatThrownBy(() -> date("120261113C", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("not valid in a packed decimal");
        assertThatThrownBy(() -> date("0202611134", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("not valid in a packed decimal");
    }

    @Test void invalidDateFails() {
        assertThatThrownBy(() -> date("020261332C", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": '20261332' is not a valid value for the pattern");
    }

    @Test void valueThatThePatternCannotKeepFails() {
        assertThatThrownBy(() -> write(LocalTime.of(10, 15, 30, 5), 6, true, TIME))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("cannot be represented by the pattern");
        assertThatThrownBy(() -> write(LocalDate.of(2026, 1, 1), 6, true, TIME))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("cannot be formatted with the pattern");
        assertThatThrownBy(() -> write(LocalDate.of(12026, 1, 1), 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("characters but the field length is 8");
    }

    @Test void shortResponseFails() {
        assertThatThrownBy(() -> date("02026111", 8, true, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("response too short, need 5 bytes");
    }

    @Test void readerMovesOverThePackedBytes() {
        var reader = new BookReader(hex("2026317C0001500C"), EBCDIC);
        assertThat(reader.readPackedDate(F, 7, true, Formats.of("yyyyDDD")))
            .isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(reader.readPackedTime(F, 6, true, TIME)).isEqualTo(LocalTime.of(0, 15));
        assertThat(reader.position()).isEqualTo(8);
    }
}
