package pt.daaz.bookmap.codec.internal;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import pt.daaz.bookmap.exception.MappingException;

/**
 * Converts text fields between Java strings and bytes.
 */
public final class TextCodec {

    private TextCodec() {
    }

    /**
     * Encodes a string. The method adds spaces on the right.
     * A null value gives only spaces.
     *
     * @param field the field descriptor, for error messages
     * @param value the string, or null
     * @param length the field length in bytes
     * @param cs the single-byte charset
     * @return exactly {@code length} bytes
     * @throws MappingException if the string is too long or has an unmappable character
     */
    public static byte[] encode(String field, String value, int length, Charset cs) {
        byte[] out = new byte[length];
        Arrays.fill(out, Blank.space(cs));
        if (value == null) {
            return out;
        }
        if (value.length() > length) {
            throw new MappingException(field + ": text is longer than " + length
                + " characters (" + value.length() + ")");
        }
        try {
            var buffer = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            buffer.get(out, 0, buffer.remaining());
        } catch (CharacterCodingException e) {
            throw new MappingException(field + ": text has a character that charset "
                + cs.name() + " cannot encode", e);
        }
        return out;
    }

    /**
     * Decodes a field. The method changes {@code 0x00} to space
     * and removes the spaces on the right. It never returns null.
     *
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param len the field length in bytes
     * @param cs the single-byte charset
     * @return the text
     */
    public static String decode(byte[] bytes, int from, int len, Charset cs) {
        byte[] copy = Arrays.copyOfRange(bytes, from, from + len);
        byte space = Blank.space(cs);
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] == 0) {
                copy[i] = space;
            }
        }
        String text = new String(copy, cs);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }
}
