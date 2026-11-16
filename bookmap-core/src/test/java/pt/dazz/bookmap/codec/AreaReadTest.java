package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.MappingException;

/** Tests the reader methods of a {@code REDEFINES} area. */
class AreaReadTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final String F = "GetShipment.resultDetail (order 50)";

    private static byte[] latin(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void areaGivesAReaderAtTheStartAndMovesThisReaderAfterTheArea() {
        BookReader reader = new BookReader(latin("AB1234CD"), LATIN);
        reader.readText(F, 2);
        BookReader area = reader.area(F, 4);
        assertThat(reader.position()).isEqualTo(6);
        assertThat(area.position()).isEqualTo(2);
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
            .hasMessage(F + ": response too short, need 4 bytes at position 2 but only 3 remain");
        assertThat(reader.position()).isEqualTo(2);
    }

    @Test
    void orNullGivesTheValueOfAValidRead() {
        BookReader area = new BookReader(latin("0042"), LATIN);
        BigDecimal value = BookReader.orNull(
            () -> area.peek(0).readNumber(F, 4, 0, false, Usage.DISPLAY));
        assertThat(value).isEqualByComparingTo("42");
    }

    @Test
    void orNullGivesNullForBytesThatAreNotValid() {
        BookReader area = new BookReader(latin("AB42"), LATIN);
        BigDecimal value = BookReader.orNull(
            () -> area.peek(0).readNumber(F, 4, 0, false, Usage.DISPLAY));
        assertThat(value).isNull();
    }

    @Test
    void orNullDoesNotCatchOtherExceptions() {
        assertThatThrownBy(() -> BookReader.orNull(() -> {
            throw new IllegalStateException("bug");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BookReader.orNull(() -> {
            throw new BookmapException("transport") {
            };
        })).isInstanceOf(BookmapException.class);
    }
}
