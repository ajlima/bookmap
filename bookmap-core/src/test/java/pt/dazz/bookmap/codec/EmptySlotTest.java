package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

class EmptySlotTest {

    private static final Charset LATIN = StandardCharsets.ISO_8859_1;
    private static final Charset EBCDIC = Charset.forName("IBM1047");

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static boolean empty(EmptySlot marker, String slot, Charset cs) {
        byte[] data = hex("ff" + slot);
        return marker.isEmpty("lines slot 0", data, 1, data.length - 1, cs);
    }

    @Test
    void lowValuesNeedsOnlyZeroBytes() {
        assertThat(empty(EmptySlot.lowValues(), "0000", LATIN)).isTrue();
        assertThat(empty(EmptySlot.lowValues(), "0020", LATIN)).isFalse();
        assertThat(EmptySlot.lowValues().fill(3, LATIN)).containsExactly(0, 0, 0);
    }

    @Test
    void spacesAcceptsSpacesAndLowValuesOfTheCharset() {
        assertThat(empty(EmptySlot.spaces(), "2000", LATIN)).isTrue();
        assertThat(empty(EmptySlot.spaces(), "4040", EBCDIC)).isTrue();
        assertThat(empty(EmptySlot.spaces(), "4040", LATIN)).isFalse();
        assertThat(EmptySlot.spaces().fill(2, EBCDIC)).containsExactly(0x40, 0x40);
    }

    @Test
    void textKeyIsEmptyWhenBlank() {
        EmptySlot marker = EmptySlot.lowValues().withTextKey(1, 2);
        assertThat(empty(marker, "41200041", LATIN)).isTrue();
        assertThat(empty(marker, "00413000", LATIN)).isFalse();
        assertThat(marker.fill(4, LATIN)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void displayKeyIsEmptyWhenBlankOrZero() {
        EmptySlot marker = EmptySlot.spaces().withNumberKey(1, 2, 0, false, Usage.DISPLAY);
        assertThat(empty(marker, "41202041", LATIN)).isTrue();
        assertThat(empty(marker, "41303041", LATIN)).isTrue();
        assertThat(empty(marker, "41303141", LATIN)).isFalse();
        EmptySlot signed = EmptySlot.lowValues().withNumberKey(0, 2, 0, true, Usage.DISPLAY);
        assertThat(signed.fill(2, LATIN)).containsExactly(0x30, 0x7b);
        assertThat(empty(signed, "307b", LATIN)).isTrue();
        assertThat(marker.fill(4, LATIN)).containsExactly(0x20, 0x30, 0x30, 0x20);
    }

    @Test
    void displayKeyWithInvalidBytesThrows() {
        EmptySlot marker = EmptySlot.lowValues().withNumberKey(0, 2, 0, false, Usage.DISPLAY);
        assertThatThrownBy(() -> empty(marker, "5858", LATIN))
            .isInstanceOf(MappingException.class).hasMessageStartingWith("lines slot 0: ");
    }

    @Test
    void packedKeyIsEmptyWhenBlankOrZero() {
        EmptySlot marker = EmptySlot.spaces().withNumberKey(0, 3, 0, true, Usage.COMP3);
        assertThat(empty(marker, "4040", EBCDIC)).isTrue();
        assertThat(empty(marker, "000c", EBCDIC)).isTrue();
        assertThat(empty(marker, "000d", EBCDIC)).isTrue();
        assertThat(empty(marker, "001c", EBCDIC)).isFalse();
        assertThat(marker.fill(2, EBCDIC)).containsExactly(0x00, 0x0c);
    }

    @Test
    void binaryKeyIsEmptyOnlyWhenZero() {
        EmptySlot marker = EmptySlot.spaces().withNumberKey(0, 4, 0, false, Usage.COMP);
        assertThat(empty(marker, "0000", EBCDIC)).isTrue();
        assertThat(empty(marker, "4040", EBCDIC)).isFalse();
        assertThat(marker.fill(3, EBCDIC)).containsExactly(0x00, 0x00, 0x40);
    }

    @Test
    void countUsedSlotsUsesTheMarker() {
        BookReader reader = new BookReader(hex("41422020"), new CodecContext(LATIN));
        assertThat(reader.countUsedSlots("lines", 2, 2, EmptySlot.spaces())).isEqualTo(1);
        assertThatThrownBy(() -> new BookReader(hex("20204142"), new CodecContext(LATIN))
            .countUsedSlots("lines", 2, 2, EmptySlot.spaces()))
            .isInstanceOf(MappingException.class)
            .hasMessage("lines: slot 0 is empty, but slot 1 after it has data");
    }

    @Test
    void countUsedSlotsGivesTheSlotIndexOfAnInvalidKey() {
        EmptySlot marker = EmptySlot.lowValues().withNumberKey(0, 1, 0, false, Usage.DISPLAY);
        assertThatThrownBy(() -> new BookReader(hex("3158"), new CodecContext(LATIN))
            .countUsedSlots("lines", 1, 2, marker))
            .isInstanceOf(MappingException.class).hasMessageStartingWith("lines slot 1: ");
    }

    @Test
    void writerWritesEmptySlotsAndChecksAnItem() {
        BookWriter writer = new BookWriter(new CodecContext(LATIN));
        EmptySlot marker = EmptySlot.spaces().withNumberKey(1, 1, 0, false, Usage.DISPLAY);
        writer.writeText("code", "A5", 2);
        writer.writeEmptySlots(marker, 2, 2);
        assertThat(writer.toByteArray()).containsExactly(0x41, 0x35, 0x20, 0x30, 0x20, 0x30);
        assertThat(writer.isEmptySlotFrom("lines slot 0", marker, 0)).isFalse();
        assertThat(writer.isEmptySlotFrom("lines slot 1", marker, 2)).isTrue();
        assertThat(writer.isEmptySlotFrom("lines slot 2", marker, 6)).isFalse();
    }
}
