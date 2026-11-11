package pt.dazz.bookmap.codec.internal;

import java.nio.charset.Charset;

/**
 * Detects a blank field. A blank field has only spaces or low-values.
 */
public final class Blank {

    private Blank() {
    }

    /**
     * Checks if all bytes of a range are the space of the charset or {@code 0x00}.
     *
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param len the number of bytes to check
     * @param cs the charset that defines the space
     * @return true if the range is blank
     */
    public static boolean isBlank(byte[] bytes, int from, int len, Charset cs) {
        byte space = space(cs);
        for (int i = from; i < from + len; i++) {
            if (bytes[i] != space && bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the byte that is the space in the charset.
     *
     * @param cs the charset
     * @return the space byte
     */
    static byte space(Charset cs) {
        return " ".getBytes(cs)[0];
    }
}
