package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.convert.FieldContext;
import pt.dazz.bookmap.convert.FieldConverter;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.InvalidValueException;
import pt.dazz.bookmap.exception.MappingException;

/** Tests the reader methods of a {@code REDEFINES} area. */
class AreaReadTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final String F = "GetShipment.resultDetail (order 50)";
    private static final DateTimeFormatter DATE = Formats.of("yyyyMMdd");

    private static byte[] latin(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void areaGivesAReaderOnTheAreaAndMovesThisReaderAfterTheArea() {
        BookReader reader = new BookReader(latin("AB1234CD"), LATIN);
        reader.readText(F, 2);
        BookReader area = reader.area(F, 4);
        assertThat(reader.position()).isEqualTo(6);
        assertThat(area.position()).isZero();
        assertThat(area.peek(0).readText(F, 4)).isEqualTo("1234");
        assertThat(area.peek(0).readText(F, 2)).isEqualTo("12");
        assertThat(reader.readText(F, 2)).isEqualTo("CD");
    }

    @Test
    void areaAcceptsDataThatEndsAtTheEndOfTheArea() {
        BookReader reader = new BookReader(latin("1234"), LATIN);
        reader.area(F, 4);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void areaChecksThatTheDataHoldsTheFullArea() {
        BookReader reader = new BookReader(latin("AB123"), LATIN);
        reader.readText(F, 2);
        assertThatThrownBy(() -> reader.area(F, 4))
            .isInstanceOf(MappingException.class)
            .isNotInstanceOf(InvalidValueException.class)
            .hasMessage(F + ": response too short, need 4 bytes at position 2 but only 3 remain");
        assertThat(reader.position()).isEqualTo(2);
    }

    @Test
    void aMemberCannotReadPastTheEndOfTheArea() {
        BookReader area = new BookReader(latin("1234CD"), LATIN).area(F, 4);
        assertThatThrownBy(() -> BookReader.orNull(() -> area.peek(0).readText(F, 6)))
            .isInstanceOf(MappingException.class)
            .isNotInstanceOf(InvalidValueException.class)
            .hasMessage(F + ": response too short, need 6 bytes at position 0 but only 4 remain");
    }

    @Test
    void orNullGivesTheValueOfAValidRead() {
        BookReader area = new BookReader(latin("0042"), LATIN);
        BigDecimal value = BookReader.orNull(
            () -> area.peek(0).readNumber(F, 4, 0, false, Usage.DISPLAY));
        assertThat(value).isEqualByComparingTo("42");
    }

    @Test
    void orNullGivesNullForZonedBytesThatAreNotValid() {
        BookReader area = new BookReader(latin("AB42"), LATIN);
        assertThat(BookReader.orNull(
            () -> area.peek(0).readNumber(F, 4, 0, false, Usage.DISPLAY))).isNull();
    }

    @Test
    void orNullGivesNullForPackedBytesThatAreNotValid() {
        BookReader area = new BookReader(latin("ABC"), LATIN);
        assertThat(BookReader.orNull(
            () -> area.peek(0).readNumber(F, 5, 0, true, Usage.COMP3))).isNull();
    }

    @Test
    void orNullGivesNullForADateThatIsNotValid() {
        BookReader area = new BookReader(latin("20261340"), LATIN);
        assertThat(BookReader.orNull(() -> area.peek(0).readDate(F, 8, DATE))).isNull();
        assertThat(BookReader.orNull(
            () -> area.peek(0).readPackedDate(F, 8, true, DATE))).isNull();
    }

    @Test
    void orNullGivesNullForANegativePackedDate() {
        BookReader area = new BookReader(new byte[] {0x20, 0x26, 0x11, 0x16, 0x0D}, LATIN);
        assertThat(BookReader.orNull(
            () -> area.peek(0).readPackedDate(F, 9, true, DATE))).isNull();
    }

    @Test
    void orNullGivesNullForAnEditedNumberThatIsNotValid() {
        BookReader area = new BookReader(latin("1A.00"), LATIN);
        EditedPicture picture = EditedPicture.parse("99.99", false);
        assertThat(BookReader.orNull(() -> area.peek(0).readEdited(F, picture))).isNull();
    }

    @Test
    void orNullGivesNullForTextThatTheCharsetCannotDecode() {
        BookReader area = new BookReader(new byte[] {(byte) 0x80},
            new CodecContext(StandardCharsets.US_ASCII));
        assertThat(BookReader.orNull(() -> area.peek(0).readText(F, 1))).isNull();
    }

    @Test
    void orNullDoesNotCatchAFailureOfAConverter() {
        BookReader area = new BookReader(latin("X"), LATIN);
        FieldConverter<String> failing = new FieldConverter<>() {
            @Override
            public String decode(byte[] bytes, FieldContext context) {
                throw new IllegalStateException("bad flag");
            }

            @Override
            public byte[] encode(String value, FieldContext context) {
                return new byte[0];
            }
        };
        FieldContext context = new FieldContext("flag", 1, 0, false, Usage.DISPLAY, "", 1,
            Charset.forName("ISO-8859-1"));
        assertThatThrownBy(() -> BookReader.orNull(
            () -> area.peek(0).readConverted(F, failing, context)))
            .isInstanceOf(MappingException.class)
            .isNotInstanceOf(InvalidValueException.class)
            .hasMessageContaining("converter failed");
    }

    @Test
    void orNullDoesNotCatchOtherExceptions() {
        assertThatThrownBy(() -> BookReader.orNull(() -> {
            throw new IllegalStateException("bug");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BookReader.orNull(() -> {
            throw new MappingException("other");
        })).isInstanceOf(MappingException.class);
        assertThatThrownBy(() -> BookReader.orNull(() -> {
            throw new BookmapException("transport") {
            };
        })).isInstanceOf(BookmapException.class);
    }

    @Test
    void invalidValueExceptionKeepsTheCause() {
        IllegalStateException cause = new IllegalStateException("x");
        assertThat(new InvalidValueException("m", cause)).hasCause(cause).hasMessage("m");
    }
}
