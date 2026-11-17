package pt.dazz.copymapper.copybook.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MappingSpecTest {

    private static SpecNode root() {
        return new SpecNode("ROOT", 1, "ROOT", null, null, null, null, null, null, List.of(), null,
                false, false, null, null, null, null, null, null, null, null, List.of());
    }

    private static MappingSpec spec(int version, String charset, String order) {
        return new MappingSpec(version, "A.cpy", null, charset, order, root());
    }

    @Test
    void rejectsAnotherFormatVersion() {
        var e = assertThrows(IllegalArgumentException.class, () -> spec(2, "IBM037", "BIG_ENDIAN"));
        assertTrue(e.getMessage().contains("formatVersion"));
    }

    @Test
    void rejectsLittleEndian() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> spec(1, "IBM037", "LITTLE_ENDIAN"));
        assertTrue(e.getMessage().contains("LITTLE_ENDIAN"));
    }

    @Test
    void rejectsMultiByteCharset() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> spec(1, "UTF-8", "BIG_ENDIAN"));
        assertTrue(e.getMessage().contains("UTF-8"));
    }

    @Test
    void rejectsUnknownCharset() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> spec(1, "NOPE", "BIG_ENDIAN"));
        assertTrue(e.getMessage().contains("NOPE"));
    }

    @Test
    void acceptsIbm037() {
        var s = spec(1, "IBM037", "BIG_ENDIAN");
        assertEquals(MappingSpec.FORMAT_VERSION, s.formatVersion());
        assertEquals("IBM037", s.charset());
    }

    @Test
    void rejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> new MappingSpec(1, null, null, "IBM037", "BIG_ENDIAN", root()));
        assertThrows(NullPointerException.class, () -> spec(1, null, "BIG_ENDIAN"));
        assertThrows(NullPointerException.class, () -> spec(1, "IBM037", null));
        assertThrows(NullPointerException.class,
                () -> new MappingSpec(1, "A", null, "IBM037", "BIG_ENDIAN", null));
    }

    @Test
    void storesTheCanonicalCharsetName() {
        assertEquals("IBM037", spec(1, "cp037", "BIG_ENDIAN").charset());
        assertEquals("IBM037", spec(1, "ibm037", "BIG_ENDIAN").charset());
    }

    @Test
    void rejectsADecodeOnlyCharset() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> spec(1, "ISO-2022-CN", "BIG_ENDIAN"));
        assertTrue(e.getMessage().contains("ISO-2022-CN"));
    }
}
