package pt.dazz.bookmap.annotation;

/**
 * Gives the position and the format of the sign of a signed zoned decimal ({@code DISPLAY})
 * number. It agrees with the COBOL {@code SIGN} clause.
 */
public enum Sign {
    /** The sign is an overpunch in the last digit. This is the COBOL default. */
    TRAILING(false, false),
    /** The sign is an overpunch in the first digit ({@code SIGN IS LEADING}). */
    LEADING(true, false),
    /**
     * The sign is a {@code +} or a {@code -} after the digits
     * ({@code SIGN IS TRAILING SEPARATE CHARACTER}). The field has one more byte.
     */
    TRAILING_SEPARATE(false, true),
    /**
     * The sign is a {@code +} or a {@code -} before the digits
     * ({@code SIGN IS LEADING SEPARATE CHARACTER}). The field has one more byte.
     */
    LEADING_SEPARATE(true, true);

    private final boolean leading;
    private final boolean separate;

    Sign(boolean leading, boolean separate) {
        this.leading = leading;
        this.separate = separate;
    }

    /**
     * Tells if the sign comes before the digits.
     *
     * @return true for {@link #LEADING} and {@link #LEADING_SEPARATE}
     */
    public boolean leading() {
        return leading;
    }

    /**
     * Tells if the sign is a separate character.
     *
     * @return true for {@link #TRAILING_SEPARATE} and {@link #LEADING_SEPARATE}
     */
    public boolean separate() {
        return separate;
    }
}
