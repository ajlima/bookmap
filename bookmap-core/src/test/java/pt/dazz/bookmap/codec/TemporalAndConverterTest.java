package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.convert.FieldContext;
import pt.dazz.bookmap.convert.FieldConverter;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.MappingException;

class TemporalAndConverterTest {

    static final CodecContext EBCDIC = CodecContext.defaults();
    static final String F = "Shipment.shippedOn (order 10)";
    static final DateTimeFormatter DATE = Formats.of("yyyyMMdd");
    static final DateTimeFormatter TIME = Formats.of("HHmmss");
    static final DateTimeFormatter DATE_TIME = Formats.of("yyyyMMddHHmmss");

    private static byte[] ebcdic(String digits) {
        return digits.getBytes(EBCDIC.charset());
    }

    private static byte[] filled(int length, int value) {
        byte[] out = new byte[length];
        Arrays.fill(out, (byte) value);
        return out;
    }

    private static byte[] write(java.time.temporal.TemporalAccessor value, int length,
                                DateTimeFormatter formatter) {
        var w = new BookWriter(EBCDIC);
        w.writeTemporal(F, value, length, formatter);
        return w.toByteArray();
    }

    private static LocalDate readDate(byte[] data, int length, DateTimeFormatter formatter) {
        return new BookReader(data, EBCDIC).readDate(F, length, formatter);
    }

    @Test void dateDefaultPattern() {
        byte[] bytes = write(LocalDate.of(2026, 3, 15), 8, DATE);
        assertThat(bytes).containsExactly(0xF2, 0xF0, 0xF2, 0xF6, 0xF0, 0xF3, 0xF1, 0xF5);
        assertThat(readDate(bytes, 8, DATE)).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test void julianDate() {
        var julian = Formats.of("yyyyDDD");
        byte[] bytes = write(LocalDate.of(2026, 2, 1), 7, julian);
        assertThat(bytes).isEqualTo(ebcdic("2026032"));
        assertThat(readDate(bytes, 7, julian)).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test void isoDateInText() {
        var iso = Formats.of("yyyy-MM-dd");
        byte[] bytes = write(LocalDate.of(2026, 3, 15), 10, iso);
        assertThat(bytes).containsExactly(
            0xF2, 0xF0, 0xF2, 0xF6, 0x60, 0xF0, 0xF3, 0x60, 0xF1, 0xF5);
        assertThat(readDate(bytes, 10, iso)).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test void timeAndDateTime() {
        var time = LocalTime.of(13, 45, 10);
        byte[] timeBytes = write(time, 6, TIME);
        assertThat(timeBytes).containsExactly(0xF1, 0xF3, 0xF4, 0xF5, 0xF1, 0xF0);
        assertThat(new BookReader(timeBytes, EBCDIC).readTime(F, 6, TIME)).isEqualTo(time);

        var stamp = LocalDateTime.of(2026, 3, 15, 13, 45, 10);
        byte[] stampBytes = write(stamp, 14, DATE_TIME);
        assertThat(stampBytes).isEqualTo(ebcdic("20260315134510"));
        assertThat(new BookReader(stampBytes, EBCDIC).readDateTime(F, 14, DATE_TIME))
            .isEqualTo(stamp);
    }

    @Test void asciiCharsetWritesAsciiDigits() {
        var ascii = new CodecContext(StandardCharsets.US_ASCII);
        var w = new BookWriter(ascii);
        w.writeTemporal(F, LocalDate.of(2026, 3, 15), 8, DATE);
        assertThat(w.toByteArray()).isEqualTo("20260315".getBytes(StandardCharsets.US_ASCII));
        assertThat(new BookReader(w.toByteArray(), ascii).readDate(F, 8, DATE))
            .isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test void nullDateWritesZeros() {
        assertThat(write(null, 8, DATE))
            .containsExactly(0xF0, 0xF0, 0xF0, 0xF0, 0xF0, 0xF0, 0xF0, 0xF0);
    }

    @Test void zeroBlankLowValueDatesReadAsNull() {
        assertThat(readDate(ebcdic("00000000"), 8, DATE)).isNull();
        assertThat(readDate(filled(8, 0x40), 8, DATE)).isNull();
        assertThat(readDate(filled(8, 0x00), 8, DATE)).isNull();
        var reader = new BookReader(ebcdic("00000000000000"), EBCDIC);
        assertThat(reader.readDateTime(F, 14, DATE_TIME)).isNull();
        assertThat(new BookReader(filled(6, 0x40), EBCDIC).readTime(F, 6, TIME)).isNull();
        assertThat(new BookReader(filled(6, 0x00), EBCDIC).readTime(F, 6, TIME)).isNull();
        assertThat(new BookReader(filled(14, 0x40), EBCDIC).readDateTime(F, 14, DATE_TIME)).isNull();
        assertThat(new BookReader(filled(14, 0x00), EBCDIC).readDateTime(F, 14, DATE_TIME)).isNull();
    }

    @Test void zerosWithSeparatorsAreNotOnlyZeros() {
        var iso = Formats.of("yyyy-MM-dd");
        assertThat(write(null, 10, iso)).isEqualTo(ebcdic("0000000000"));
        assertThat(readDate(ebcdic("0000000000"), 10, iso)).isNull();
        assertThatThrownBy(() -> readDate(ebcdic("0000-00-00"), 10, iso))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ");
    }

    @Test void invalidDateFails() {
        assertThatThrownBy(() -> readDate(ebcdic("20261340"), 8, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": ")
            .hasCauseInstanceOf(java.time.DateTimeException.class);
    }

    @Test void patternLengthMismatchFails() {
        assertThatThrownBy(() -> write(LocalDate.of(2026, 3, 15), 6, DATE))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ");
    }

    @Test void valueThatDoesNotFitThePatternFails() {
        assertThatThrownBy(() -> write(LocalDate.of(2026, 3, 15), 6, TIME))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ")
            .hasCauseInstanceOf(java.time.DateTimeException.class);
    }

    @Test void readWithWrongTypeOfPatternFails() {
        assertThatThrownBy(() -> new BookReader(ebcdic("20260315"), EBCDIC)
            .readDateTime(F, 8, DATE))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ");
    }

    @Test void shortResponseFails() {
        assertThatThrownBy(() -> readDate(ebcdic("2026"), 8, DATE))
            .isInstanceOf(MappingException.class).hasMessageContaining("too short");
    }

    @Test void midnightRoundTrip() {
        byte[] bytes = write(LocalTime.MIDNIGHT, 6, TIME);
        assertThat(bytes).isEqualTo(ebcdic("000000"));
        assertThat(new BookReader(bytes, EBCDIC).readTime(F, 6, TIME))
            .isEqualTo(LocalTime.MIDNIGHT);
    }

    // ---- R28: strict round trip on write ----

    @Test void twoDigitYearOutsideTheWindowFails() {
        var yearMonthDay = Formats.of("yyMMdd");
        assertThatThrownBy(() -> write(LocalDate.of(1975, 3, 1), 6, yearMonthDay))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": value 1975-03-01 cannot be represented by the pattern")
            .hasMessageContaining("'750301'");
    }

    @Test void twoDigitYearInsideTheWindowRoundTrips() {
        var yearMonthDay = Formats.of("yyMMdd");
        byte[] bytes = write(LocalDate.of(2026, 3, 1), 6, yearMonthDay);
        assertThat(bytes).isEqualTo(ebcdic("260301"));
        assertThat(readDate(bytes, 6, yearMonthDay)).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test void timeWithSecondsInAPatternWithoutSecondsFails() {
        assertThatThrownBy(() -> write(LocalTime.of(10, 15, 30), 4, Formats.of("HHmm")))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": value 10:15:30 cannot be represented by the pattern");
    }

    @Test void timeWithoutSecondsInAPatternWithoutSecondsRoundTrips() {
        var hoursMinutes = Formats.of("HHmm");
        byte[] bytes = write(LocalTime.of(10, 15), 4, hoursMinutes);
        assertThat(bytes).isEqualTo(ebcdic("1015"));
        assertThat(new BookReader(bytes, EBCDIC).readTime(F, 4, hoursMinutes))
            .isEqualTo(LocalTime.of(10, 15));
    }

    @Test void timeWithNanosecondsFails() {
        assertThatThrownBy(() -> write(LocalTime.of(10, 15, 30, 500_000_000), 6, TIME))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": value 10:15:30.500 cannot be represented by the pattern");
    }

    @Test void dateTimeWithSecondsInAPatternWithoutSecondsFails() {
        assertThatThrownBy(() -> write(LocalDateTime.of(2026, 3, 15, 13, 45, 10), 12,
                Formats.of("yyyyMMddHHmm")))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": value 2026-03-15T13:45:10 cannot be represented");
    }

    @Test void textThatDoesNotParseBackFails() {
        // The year has a variable width, so it takes all digits on read.
        assertThatThrownBy(() -> write(LocalDate.of(2026, 12, 1), 7, Formats.of("yyyyMd")))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith(F + ": value 2026-12-01 cannot be represented by the pattern")
            .hasCauseInstanceOf(java.time.DateTimeException.class);
    }

    // ---- I3: bytes that the charset cannot decode ----

    @Test void undecodableByteInADateFails() {
        var ascii = new CodecContext(StandardCharsets.US_ASCII);
        byte[] data = "2026031".getBytes(StandardCharsets.US_ASCII);
        byte[] withHighByte = Arrays.copyOf(data, 8);
        withHighByte[7] = (byte) 0xB5;
        assertThatThrownBy(() -> new BookReader(withHighByte, ascii).readDate(F, 8, DATE))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": the field has a byte that charset US-ASCII cannot decode")
            .hasCauseInstanceOf(java.nio.charset.CharacterCodingException.class);
    }

    @Test void quotedLiteralIsNotChanged() {
        assertThat(Formats.of("yyyy'y'MMdd").format(LocalDate.of(2026, 3, 15)))
            .isEqualTo("2026y0315");
    }

    @Test void escapedQuoteKeepsTheStateOutsideTheLiteral() {
        assertThat(Formats.of("yyyy''MM").format(LocalDate.of(2026, 3, 15)))
            .isEqualTo("2026'03");
    }

    // A converter that maps Boolean to "Y" or "N".
    static final FieldConverter<Boolean> YES_NO = new FieldConverter<>() {
        @Override public Boolean decode(byte[] bytes, FieldContext context) {
            return bytes[0] == "Y".getBytes(context.charset())[0];
        }

        @Override public byte[] encode(Boolean value, FieldContext context) {
            return (value ? "Y" : "N").getBytes(context.charset());
        }
    };

    static final FieldContext ONE_BYTE = new FieldContext("flag", 1, 0, false, Usage.DISPLAY,
        "", 1, EBCDIC.charset());

    @Test void customConverterRoundTrip() {
        var w = new BookWriter(EBCDIC);
        w.writeConverted(F, true, YES_NO, ONE_BYTE);
        w.writeConverted(F, false, YES_NO, ONE_BYTE);
        assertThat(w.toByteArray()).containsExactly(0xE8, 0xD5);
        var r = new BookReader(w.toByteArray(), EBCDIC);
        assertThat(r.readConverted(F, YES_NO, ONE_BYTE)).isTrue();
        assertThat(r.readConverted(F, YES_NO, ONE_BYTE)).isFalse();
        assertThat(r.position()).isEqualTo(2);
    }

    @Test void converterWithWrongByteCountFails() {
        FieldConverter<Boolean> twoBytes = new FieldConverter<>() {
            @Override public Boolean decode(byte[] bytes, FieldContext context) {
                return null;
            }

            @Override public byte[] encode(Boolean value, FieldContext context) {
                return new byte[2];
            }
        };
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeConverted(F, true, twoBytes, ONE_BYTE))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ")
            .hasMessageContaining("returned 2 bytes");
    }

    @Test void converterThatReturnsNullBytesFails() {
        FieldConverter<Boolean> nothing = new FieldConverter<>() {
            @Override public Boolean decode(byte[] bytes, FieldContext context) {
                return null;
            }

            @Override public byte[] encode(Boolean value, FieldContext context) {
                return null;
            }
        };
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeConverted(F, true, nothing, ONE_BYTE))
            .isInstanceOf(MappingException.class).hasMessageContaining("returned null");
    }

    @Test void converterRuntimeExceptionIsWrapped() {
        var failure = new IllegalStateException("broken");
        FieldConverter<Boolean> failing = new FieldConverter<>() {
            @Override public Boolean decode(byte[] bytes, FieldContext context) {
                throw failure;
            }

            @Override public byte[] encode(Boolean value, FieldContext context) {
                throw failure;
            }
        };
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeConverted(F, true, failing, ONE_BYTE))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ")
            .hasCause(failure);
        var r = new BookReader(new byte[1], EBCDIC);
        assertThatThrownBy(() -> r.readConverted(F, failing, ONE_BYTE))
            .isInstanceOf(MappingException.class).hasMessageStartingWith(F + ": ")
            .hasCause(failure);
    }

    @Test void converterExceptionWithoutMessageNamesTheClass() {
        FieldConverter<Boolean> failing = new FieldConverter<>() {
            @Override public Boolean decode(byte[] bytes, FieldContext context) {
                throw new IllegalStateException();
            }

            @Override public byte[] encode(Boolean value, FieldContext context) {
                throw new IllegalStateException();
            }
        };
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeConverted(F, true, failing, ONE_BYTE))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": converter failed (java.lang.IllegalStateException)");
        var r = new BookReader(new byte[1], EBCDIC);
        assertThatThrownBy(() -> r.readConverted(F, failing, ONE_BYTE))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": converter failed (java.lang.IllegalStateException)");
    }

    @Test void bookmapExceptionFromConverterPassesThrough() {
        var original = new BookmapException("own error") { };
        FieldConverter<Boolean> failing = new FieldConverter<>() {
            @Override public Boolean decode(byte[] bytes, FieldContext context) {
                throw original;
            }

            @Override public byte[] encode(Boolean value, FieldContext context) {
                throw original;
            }
        };
        var w = new BookWriter(EBCDIC);
        assertThatThrownBy(() -> w.writeConverted(F, true, failing, ONE_BYTE)).isSameAs(original);
        var r = new BookReader(new byte[1], EBCDIC);
        assertThatThrownBy(() -> r.readConverted(F, failing, ONE_BYTE)).isSameAs(original);
    }

    @Test void converterReadWithShortResponseFails() {
        var r = new BookReader(new byte[0], EBCDIC);
        assertThatThrownBy(() -> r.readConverted(F, YES_NO, ONE_BYTE))
            .isInstanceOf(MappingException.class).hasMessageContaining("too short");
    }
}
