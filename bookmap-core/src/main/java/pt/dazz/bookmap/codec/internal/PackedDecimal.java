package pt.dazz.bookmap.codec.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import pt.dazz.bookmap.exception.MappingException;

/**
 * Converts packed decimal (COMP-3) numbers. Each byte holds two digits.
 * The last nibble is the sign: {@code C} positive, {@code D} negative, {@code F} unsigned.
 */
public final class PackedDecimal {

    private static final int NEGATIVE_FLAG = 10;
    private static final int INVALID = -1;
    /** Bit n is set if sign nibble n is positive: A, C, E, F. */
    private static final int POSITIVE_SIGNS = 1 << 0xA | 1 << 0xC | 1 << 0xE | 1 << 0xF;
    /** Bit n is set if sign nibble n is negative: B, D. */
    private static final int NEGATIVE_SIGNS = 1 << 0xB | 1 << 0xD;

    private PackedDecimal() {
    }

    /**
     * Encodes a number. A null value gives zero.
     * An even {@code length} gives a leading zero nibble.
     *
     * @param field the field descriptor, for error messages
     * @param value the number, or null
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @return exactly {@code length / 2 + 1} bytes
     * @throws MappingException if the value does not fit in the field
     */
    public static byte[] encode(String field, BigDecimal value, int length, int scale,
                                boolean signed) {
        BigInteger unscaled = Unscaled.of(field, value, length, scale, signed);
        String digits = unscaled.abs().toString();
        int size = length / 2 + 1;
        int nibbles = size * 2 - 1;
        int offset = nibbles - digits.length();
        byte[] out = new byte[size];
        for (int i = 0; i < nibbles; i++) {
            int digit = i < offset ? 0 : digits.charAt(i - offset) - '0';
            out[i / 2] |= (byte) (i % 2 == 0 ? digit << 4 : digit);
        }
        int sign = !signed ? 0xF : unscaled.signum() < 0 ? 0xD : 0xC;
        out[size - 1] |= (byte) sign;
        return out;
    }

    /**
     * Decodes a number. A field with only {@code 0x00} or only spaces gives null.
     *
     * @param field the field descriptor, for error messages
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param cs the charset that defines the space
     * @return the number, or null if the field is blank
     * @throws MappingException if a nibble is not valid
     */
    public static BigDecimal decode(String field, byte[] bytes, int from, int length, int scale,
                                    boolean signed, Charset cs) {
        int size = length / 2 + 1;
        if (Blank.isBlank(bytes, from, size, cs)) {
            return null;
        }
        int nibbles = size * 2 - 1;
        int pad = nibbles - length;
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < nibbles; i++) {
            int b = bytes[from + i / 2] & 0xFF;
            int nibble = i % 2 == 0 ? b >> 4 : b & 0x0F;
            if (nibble > 9 || i < pad && nibble != 0) {
                throw invalid(field, b, i / 2);
            }
            digits.append((char) ('0' + nibble));
        }
        int lastByte = bytes[from + size - 1] & 0xFF;
        int sign = signNibble(lastByte & 0x0F);
        if (sign == INVALID || !signed && sign == NEGATIVE_FLAG) {
            throw invalid(field, lastByte, size - 1);
        }
        BigDecimal result = new BigDecimal(new BigInteger(digits.toString()), scale);
        return sign == NEGATIVE_FLAG ? result.negate() : result;
    }

    private static MappingException invalid(String field, int b, int offset) {
        return new MappingException(field + ": byte 0x" + String.format("%02X", b)
            + " at offset " + offset + " of the field is not valid in a packed decimal");
    }

    /** Gives 0 for a positive sign, NEGATIVE_FLAG for a negative sign, or INVALID. */
    private static int signNibble(int nibble) {
        if ((POSITIVE_SIGNS >> nibble & 1) != 0) {
            return 0;
        }
        return (NEGATIVE_SIGNS >> nibble & 1) != 0 ? NEGATIVE_FLAG : INVALID;
    }
}
