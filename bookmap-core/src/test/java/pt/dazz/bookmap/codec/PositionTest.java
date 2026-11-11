package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

class PositionTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final String F = "Entry.kind (order 10)";

    private static byte[] latin(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void peekReadsAheadWithoutMovingTheReader() {
        BookReader reader = new BookReader(latin("ABCDEF"), LATIN);
        reader.readText(F, 1);
        assertThat(reader.peek(2).readText(F, 2)).isEqualTo("DE");
        assertThat(reader.position()).isEqualTo(1);
        assertThat(reader.readText(F, 1)).isEqualTo("B");
    }

    @Test
    void peekPastTheEndFailsOnRead() {
        BookReader ahead = new BookReader(latin("AB"), LATIN).peek(5);
        assertThatThrownBy(() -> ahead.readText(F, 1)).isInstanceOf(MappingException.class);
    }

    @Test
    void skipMovesThePosition() {
        BookReader reader = new BookReader(latin("ABCD"), LATIN);
        reader.skip(2);
        assertThat(reader.position()).isEqualTo(2);
        assertThat(reader.readText(F, 2)).isEqualTo("CD");
    }

    @Test
    void writerPositionCountsTheBytes() {
        BookWriter writer = new BookWriter(LATIN);
        assertThat(writer.position()).isZero();
        writer.writeText(F, "AB", 2);
        assertThat(writer.position()).isEqualTo(2);
    }

    @Test
    void writeFillerWritesSpacesOfTheCharset() {
        BookWriter writer = new BookWriter(CodecContext.defaults());
        writer.writeFiller(3);
        assertThat(writer.position()).isEqualTo(3);
        assertThat(writer.toByteArray()).containsExactly(0x40, 0x40, 0x40);
    }
}
