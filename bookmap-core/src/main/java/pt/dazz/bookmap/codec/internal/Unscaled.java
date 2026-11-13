package pt.dazz.bookmap.codec.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import pt.dazz.bookmap.exception.MappingException;

/**
 * Checks a number against a field definition and gives its unscaled value.
 */
public final class Unscaled {

    private Unscaled() {
    }

    /**
     * Gives the unscaled value of a number. A null value gives zero.
     *
     * @param field the field descriptor, for error messages
     * @param value the number, or null
     * @param length the total number of digits
     * @param scale the number of implied decimal places
     * @param signed true if the field has a sign
     * @return the value multiplied by ten to the power of {@code scale}
     * @throws MappingException if the value does not fit in the field
     */
    public static BigInteger of(String field, BigDecimal value, int length, int scale, boolean signed) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        BigInteger unscaled;
        try {
            unscaled = number.setScale(scale, RoundingMode.UNNECESSARY).unscaledValue();
        } catch (ArithmeticException e) {
            throw new MappingException(field + ": " + number
                + " has more than " + scale + " decimal places", e);
        }
        if (unscaled.signum() < 0 && !signed) {
            throw new MappingException(field + ": negative value " + number
                + " in an unsigned field");
        }
        if (unscaled.abs().toString().length() > length) {
            throw new MappingException(field + ": " + number + " has more than "
                + length + " digits");
        }
        return unscaled;
    }
}
