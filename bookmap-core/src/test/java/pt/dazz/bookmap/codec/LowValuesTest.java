package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

class LowValuesTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    @Test
    void writeLowValuesWritesZeroBytes() {
        BookWriter writer = new BookWriter(LATIN);
        writer.writeLowValues(3);
        assertThat(writer.toByteArray()).containsExactly(0, 0, 0);
    }

    @Test
    void isLowValuesFromIsTrueOnlyForZeroBytes() {
        BookWriter writer = new BookWriter(LATIN);
        writer.writeText("id", "A", 1);
        writer.writeLowValues(2);
        assertThat(writer.isLowValuesFrom(1)).isTrue();
        assertThat(writer.isLowValuesFrom(0)).isFalse();
        assertThat(writer.isLowValuesFrom(3)).isFalse();
    }

    @Test
    void countUsedSlotsGivesLastUsedSlotPlusOne() {
        assertThat(new BookReader(hex("41420000" + "0000"), LATIN)
            .countUsedSlots("lines", 2, 3)).isEqualTo(1);
        assertThat(new BookReader(hex("414243444546"), LATIN)
            .countUsedSlots("lines", 2, 3)).isEqualTo(3);
        assertThat(new BookReader(hex("000000000000"), LATIN)
            .countUsedSlots("lines", 2, 3)).isZero();
    }

    @Test
    void countUsedSlotsAcceptsASlotWithSomeZeroBytes() {
        assertThat(new BookReader(hex("0041" + "0000"), LATIN)
            .countUsedSlots("lines", 2, 2)).isEqualTo(1);
    }

    @Test
    void countUsedSlotsRejectsAGap() {
        BookReader reader = new BookReader(hex("0000" + "4344"), LATIN);
        assertThatThrownBy(() -> reader.countUsedSlots("lines", 2, 2))
            .isInstanceOf(MappingException.class)
            .hasMessage("lines: slot 0 is empty, but slot 1 after it has data");
    }

    @Test
    void countUsedSlotsRejectsShortData() {
        BookReader reader = new BookReader(hex("414243"), LATIN);
        assertThatThrownBy(() -> reader.countUsedSlots("lines", 2, 2))
            .isInstanceOf(MappingException.class)
            .hasMessage("lines: the response is too short for the fixed area");
    }

    @Test
    void countUsedSlotsDoesNotMove() {
        BookReader reader = new BookReader(hex("4141" + "4343" + "0000"), LATIN);
        reader.skip(2);
        assertThat(reader.countUsedSlots("lines", 2, 2)).isEqualTo(1);
        assertThat(reader.position()).isEqualTo(2);
    }

    @Test
    void ebcdicSpacesAreNotEmpty() {
        CodecContext ebcdic = new CodecContext(Charset.forName("Cp037"));
        assertThat(new BookReader(hex("4040"), ebcdic).countUsedSlots("lines", 2, 1))
            .isEqualTo(1);
    }

    @Test
    void skipAreaMovesThePosition() {
        BookReader reader = new BookReader(hex("41424344"), LATIN);
        reader.skipArea("lines", 4);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void skipAreaRejectsShortData() {
        BookReader reader = new BookReader(hex("4142"), LATIN);
        assertThatThrownBy(() -> reader.skipArea("lines", 3))
            .isInstanceOf(MappingException.class)
            .hasMessage("lines: the response is too short for the fixed area");
    }
}
