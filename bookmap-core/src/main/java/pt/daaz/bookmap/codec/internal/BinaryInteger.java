package pt.daaz.bookmap.codec.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import pt.daaz.bookmap.exception.MappingException;

/**
 * Converts binary (COMP) numbers. The bytes are big-endian, two's complement if signed.
 */
public final class BinaryInteger {

    private static final int BITS_PER_BYTE = 8;

    private BinaryInteger() {
    }

    /**
     * Encodes a number. A null value gives zero.
     * The value must have at most {@code length} digits.
     *
     * @param field the field descriptor, for error messages
     * @param value the number, or null
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param size the number of bytes
     * @return exactly {@code size} bytes
     * @throws MappingException if the value does not fit in the field
     */
    public static byte[] encode(String field, BigDecimal value, int length, int scale,
                                boolean signed, int size) {
        BigInteger unscaled = Unscaled.of(field, value, length, scale, signed);
        byte[] out = new byte[size];
        for (int i = 0; i < size; i++) {
            out[size - 1 - i] = unscaled.shiftRight(BITS_PER_BYTE * i).byteValue();
        }
        return out;
    }

    /**
     * Decodes a number. The result is never null. The method accepts every value that fits
     * in the bytes, also a value with more than the declared number of digits.
     *
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param size the number of bytes
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @return the number
     */
    public static BigDecimal decode(byte[] bytes, int from, int size, int scale, boolean signed) {
        byte[] slice = Arrays.copyOfRange(bytes, from, from + size);
        BigInteger unscaled = signed ? new BigInteger(slice) : new BigInteger(1, slice);
        return new BigDecimal(unscaled, scale);
    }
}
