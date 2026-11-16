package pt.dazz.copymapper.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.convert.CodecContext;
import pt.dazz.copymapper.exception.MappingException;

class PresentSlotsTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private static BookReader reader(int length, int position) {
        BookReader reader = new BookReader(new byte[length], LATIN);
        reader.skip(position);
        return reader;
    }

    @Test
    void fullAreaGivesAllSlots() {
        assertThat(reader(10, 4).presentSlots("orders", 2, 3)).isEqualTo(3);
    }

    @Test
    void longerDataGivesAllSlots() {
        assertThat(reader(13, 4).presentSlots("orders", 2, 3)).isEqualTo(3);
    }

    @Test
    void eachSlotBoundaryGivesTheCompleteSlots() {
        assertThat(reader(4, 4).presentSlots("orders", 2, 3)).isZero();
        assertThat(reader(6, 4).presentSlots("orders", 2, 3)).isEqualTo(1);
        assertThat(reader(8, 4).presentSlots("orders", 2, 3)).isEqualTo(2);
    }

    @Test
    void theReaderDoesNotMove() {
        BookReader reader = reader(8, 4);
        reader.presentSlots("orders", 2, 3);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void aCutInsideTheFirstSlotIsAnError() {
        assertThatThrownBy(() -> reader(5, 0).presentSlots("orders", 14, 10))
            .isInstanceOf(MappingException.class)
            .hasMessage("orders: the response ends inside slot 0, after 5 of 14 bytes");
    }

    @Test
    void aCutInsideTheLastSlotIsAnError() {
        assertThatThrownBy(() -> reader(45, 6).presentSlots("orders", 14, 3))
            .isInstanceOf(MappingException.class)
            .hasMessage("orders: the response ends inside slot 2, after 11 of 14 bytes");
    }

    @Test
    void aPositionAfterTheEndOfTheDataIsAnError() {
        assertThatThrownBy(() -> reader(4, 5).presentSlots("orders", 2, 3))
            .isInstanceOf(MappingException.class)
            .hasMessage("orders: the response is too short for the fixed area");
    }

    @Test
    void aLargeAreaIsCalculatedInLong() {
        // 2 x 1_500_000_000 slots overflow an int.
        assertThat(reader(4, 0).presentSlots("orders", 2, 1_500_000_000)).isEqualTo(2);
        assertThat(reader(0, 0).presentSlots("orders", 1_500_000_000, 2)).isZero();
    }
}
