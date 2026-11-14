package pt.dazz.bookmap.copybook.ast;

import java.util.Locale;
import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.codec.EditedPicture;

/**
 * Describes a picture string that BookMap supports: {@code X(n)}, {@code S9(n)V9(m)}, or a
 * numeric edited picture such as {@code Z,ZZ9.99CR}.
 *
 * @param text the picture string, in upper case
 * @param numeric {@code true} for a number
 * @param signed {@code true} if the picture starts with {@code S}, or an edited picture has a
 *     sign symbol
 * @param integerDigits the number of characters for text, or the integer digits for a number
 * @param scale the number of decimal digits
 * @param edited the edited picture, with the {@code BLANK WHEN ZERO} clause, or {@code null}
 */
public record Picture(String text, boolean numeric, boolean signed, int integerDigits,
                      int scale, EditedPicture edited) {

    /**
     * Creates a picture without edit symbols.
     *
     * @param text the picture string
     * @param numeric {@code true} for a number
     * @param signed {@code true} for a signed number
     * @param integerDigits the number of characters, or the integer digits
     * @param scale the number of decimal digits
     */
    public Picture(String text, boolean numeric, boolean signed, int integerDigits, int scale) {
        this(text, numeric, signed, integerDigits, scale, null);
    }

    /**
     * Gives the length: the number of characters, or the total number of digits.
     *
     * @return the length
     */
    public int length() {
        return integerDigits + scale;
    }

    /**
     * Gives the number of bytes of an elementary item with this picture, with the BookMap rules.
     *
     * @param usage the effective usage
     * @param sign the effective sign, or {@code null} for the default. A separate sign adds
     *     one byte to a {@code DISPLAY} number.
     * @return the number of bytes
     */
    public int byteLength(Usage usage, Sign sign) {
        if (edited != null) {
            return edited.byteLength();
        }
        int length = length();
        return switch (usage) {
            case DISPLAY -> sign != null && sign.separate() ? length + 1 : length;
            case COMP3 -> length / 2 + 1;
            case COMP, COMP5 -> length <= 4 ? 2 : length <= 9 ? 4 : 8;
        };
    }

    /**
     * Parses a picture string.
     *
     * @param text the picture string
     * @return the picture
     * @throws IllegalArgumentException if BookMap does not support the picture
     */
    public static Picture parse(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        if (EditedPicture.isEdited(upper)) {
            return edited(upper, false);
        }
        // The repetition counts, for example (10), are not symbols.
        String symbols = upper.replaceAll("\\([^)]*\\)", "");
        for (char c : new char[] {'P', 'A', 'N'}) {
            if (symbols.indexOf(c) >= 0) {
                throw new IllegalArgumentException("picture symbol " + c
                    + " is not supported by BookMap" + (c == 'N' ? ". See issue #49." : ""));
            }
        }
        try {
            return expand(upper);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("picture " + upper + " is not valid", e);
        }
    }

    /**
     * Tells if the picture has edit symbols, for example {@code Z} or {@code CR}. A
     * {@code 9(5)} with {@code BLANK WHEN ZERO} is an edited picture without edit symbols.
     *
     * @return {@code true} for an edited picture with edit symbols
     */
    public boolean editSymbols() {
        return edited != null && EditedPicture.isEdited(text);
    }

    /**
     * Gives this picture with the clause {@code BLANK WHEN ZERO}. The result is an edited
     * picture.
     *
     * @return the picture
     * @throws IllegalArgumentException if the clause is not permitted with the picture
     */
    public Picture withBlankWhenZero() {
        if (!numeric) {
            throw new IllegalArgumentException("BLANK WHEN ZERO needs a numeric picture");
        }
        return edited(text, true);
    }

    private static Picture edited(String text, boolean blankWhenZero) {
        EditedPicture edited;
        try {
            edited = EditedPicture.parse(text, blankWhenZero);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("edited picture " + text + ": " + e.getMessage(),
                e);
        }
        return new Picture(text, true, edited.signed(), edited.digits() - edited.scale(),
            edited.scale(), edited);
    }

    /** Counts the symbols. Throws {@link IllegalStateException} for an invalid sequence. */
    private static Picture expand(String text) {
        int x = 0;
        int integer = 0;
        int scale = 0;
        boolean signed = false;
        boolean point = false;
        int i = 0;
        while (i < text.length()) {
            char symbol = text.charAt(i++);
            int count = 1;
            if (i < text.length() && text.charAt(i) == '(') {
                int close = text.indexOf(')', i);
                check(close > i + 1);
                count = parseCount(text.substring(i + 1, close));
                i = close + 1;
            }
            switch (symbol) {
                case 'X' -> {
                    check(integer + scale == 0 && !signed && !point);
                    x += count;
                }
                case 'S' -> {
                    check(i == 1);
                    signed = true;
                }
                case 'V' -> {
                    check(!point && x == 0 && count == 1);
                    point = true;
                }
                case '9' -> {
                    check(x == 0);
                    if (point) {
                        scale += count;
                    } else {
                        integer += count;
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
        check(x + integer + scale > 0);
        return x > 0
            ? new Picture(text, false, false, x, 0)
            : new Picture(text, true, signed, integer, scale);
    }

    private static int parseCount(String digits) {
        try {
            int count = Integer.parseInt(digits);
            check(count > 0);
            return count;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new IllegalStateException();
        }
    }
}
