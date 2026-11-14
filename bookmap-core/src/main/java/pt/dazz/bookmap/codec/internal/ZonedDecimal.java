package pt.dazz.bookmap.codec.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.exception.MappingException;

/**
 * Converts zoned decimal (DISPLAY) numbers. The sign is an overpunch in the first or the last
 * digit, or a separate {@code +} or {@code -} before or after the digits.
 */
public final class ZonedDecimal {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";
    /** Bit n is set if EBCDIC zone n is positive: C, F, A, E. */
    private static final int POSITIVE_ZONES = 1 << 0xC | 1 << 0xF | 1 << 0xA | 1 << 0xE;
    /** Bit n is set if EBCDIC zone n is negative: D, B. */
    private static final int NEGATIVE_ZONES = 1 << 0xD | 1 << 0xB;
    private static final int NEGATIVE_FLAG = 10;
    private static final int INVALID = -1;

    private ZonedDecimal() {
    }

    /**
     * Encodes a number. A null value gives zeros. Zero gets the positive sign.
     *
     * @param field the field descriptor, for error messages
     * @param value the number, or null
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param sign the position and the format of the sign
     * @param cs the single-byte charset
     * @return {@code length} bytes, plus 1 for a separate sign
     * @throws MappingException if the value does not fit in the field
     */
    public static byte[] encode(String field, BigDecimal value, int length, int scale,
                                boolean signed, Sign sign, Charset cs) {
        BigInteger unscaled = Unscaled.of(field, value, length, scale, signed);
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        boolean ebcdic = isEbcdic(cs);
        boolean separate = sign.separate();
        int first = separate && sign.leading() ? 1 : 0;
        int overpunch = signed && !separate ? overpunchIndex(sign, length) : -1;
        byte[] out = new byte[length + (separate ? 1 : 0)];
        int offset = length - digits.length();
        for (int i = 0; i < length; i++) {
            int digit = i < offset ? 0 : digits.charAt(i - offset) - '0';
            boolean withSign = i == overpunch;
            out[first + i] = ebcdic ? ebcdicByte(digit, withSign, negative)
                : asciiByte(digit, withSign, negative, cs);
        }
        if (separate) {
            out[sign.leading() ? 0 : length] = (negative ? "-" : "+").getBytes(cs)[0];
        }
        return out;
    }

    /**
     * Decodes a number. A blank field gives null. The blank check includes a separate sign.
     *
     * @param field the field descriptor, for error messages
     * @param bytes the buffer
     * @param from the index of the first byte
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @param sign the position and the format of the sign
     * @param cs the single-byte charset
     * @return the number, or null if the field is blank
     * @throws MappingException if a byte is not valid, or the charset cannot decode a byte
     */
    public static BigDecimal decode(String field, byte[] bytes, int from, int length, int scale,
                                    boolean signed, Sign sign, Charset cs) {
        boolean separate = sign.separate();
        int size = length + (separate ? 1 : 0);
        if (Blank.isBlank(bytes, from, size, cs)) {
            return null;
        }
        boolean ebcdic = isEbcdic(cs);
        String text = ebcdic ? null : TextCodec.decodeStrict(field, bytes, from, size, cs);
        int first = separate && sign.leading() ? 1 : 0;
        int overpunch = separate ? -1 : overpunchIndex(sign, length);
        StringBuilder digits = new StringBuilder(length);
        boolean negative = false;
        for (int i = 0; i < length; i++) {
            int at = first + i;
            boolean withSign = i == overpunch;
            int b = bytes[from + at] & 0xFF;
            int code = ebcdic ? ebcdicDigit(b, withSign) : asciiDigit(text.charAt(at), withSign);
            if (code == INVALID || !signed && code >= NEGATIVE_FLAG) {
                throw invalid(field, b, at, "is not valid in a zoned decimal");
            }
            negative |= code >= NEGATIVE_FLAG;
            digits.append((char) ('0' + code % NEGATIVE_FLAG));
        }
        if (separate) {
            int at = sign.leading() ? 0 : length;
            int b = bytes[from + at];
            boolean minus = b == "-".getBytes(cs)[0];
            if (!minus && b != "+".getBytes(cs)[0] || minus && !signed) {
                throw invalid(field, b & 0xFF, at,
                    "is not a valid sign. The sign must be '+' or '-'");
            }
            negative = minus;
        }
        BigDecimal result = new BigDecimal(new BigInteger(digits.toString()), scale);
        return negative ? result.negate() : result;
    }

    private static MappingException invalid(String field, int b, int offset, String problem) {
        return new MappingException(field + ": byte 0x" + String.format("%02X", b)
            + " at offset " + offset + " of the field " + problem);
    }

    /** Gives the index of the digit with the overpunch sign. */
    private static int overpunchIndex(Sign sign, int length) {
        return sign.leading() ? 0 : length - 1;
    }

    private static boolean isEbcdic(Charset cs) {
        return "0".getBytes(cs)[0] == (byte) 0xF0;
    }

    private static byte ebcdicByte(int digit, boolean withSign, boolean negative) {
        int zone = withSign ? negative ? 0xD0 : 0xC0 : 0xF0;
        return (byte) (zone | digit);
    }

    private static byte asciiByte(int digit, boolean withSign, boolean negative, Charset cs) {
        char c = withSign
            ? (negative ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH).charAt(digit)
            : (char) ('0' + digit);
        return String.valueOf(c).getBytes(cs)[0];
    }

    /** Gives the digit, plus 10 for a negative sign, or INVALID. */
    private static int ebcdicDigit(int b, boolean withSign) {
        int digit = b & 0x0F;
        int zone = b >> 4;
        if (digit > 9) {
            return INVALID;
        }
        if (!withSign) {
            return zone == 0xF ? digit : INVALID;
        }
        if ((POSITIVE_ZONES >> zone & 1) != 0) {
            return digit;
        }
        return (NEGATIVE_ZONES >> zone & 1) != 0 ? digit + NEGATIVE_FLAG : INVALID;
    }

    /** Gives the digit, plus 10 for a negative sign, or INVALID. */
    private static int asciiDigit(char c, boolean withSign) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (!withSign) {
            return INVALID;
        }
        int positive = POSITIVE_OVERPUNCH.indexOf(c);
        if (positive >= 0) {
            return positive;
        }
        int negative = NEGATIVE_OVERPUNCH.indexOf(c);
        return negative >= 0 ? negative + NEGATIVE_FLAG : INVALID;
    }
}
