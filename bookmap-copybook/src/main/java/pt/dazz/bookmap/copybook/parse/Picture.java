package pt.dazz.bookmap.copybook.parse;

import java.util.Locale;

/**
 * Describes a picture string that BookMap supports: {@code X(n)} or {@code S9(n)V9(m)}.
 *
 * @param text the picture string, in upper case
 * @param numeric {@code true} for a number
 * @param signed {@code true} if the picture starts with {@code S}
 * @param integerDigits the number of characters for text, or the integer digits for a number
 * @param scale the number of decimal digits
 */
public record Picture(String text, boolean numeric, boolean signed, int integerDigits,
                      int scale) {

    private static final String EDIT_SYMBOLS = "Z*,.+-B0/";

    /**
     * Gives the length: the number of characters, or the total number of digits.
     *
     * @return the length
     */
    public int length() {
        return integerDigits + scale;
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
        // The repetition counts, for example (10), are not symbols.
        String symbols = upper.replaceAll("\\([^)]*\\)", "");
        for (char c : symbols.toCharArray()) {
            if (EDIT_SYMBOLS.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                    "edited picture " + upper + " is not supported by BookMap");
            }
        }
        // DB is caught above through B.
        if (symbols.contains("CR")) {
            throw new IllegalArgumentException(
                "edited picture " + upper + " is not supported by BookMap");
        }
        for (char c : new char[] {'P', 'A', 'N'}) {
            if (symbols.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                    "picture symbol " + c + " is not supported by BookMap");
            }
        }
        try {
            return expand(upper);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("picture " + upper + " is not valid", e);
        }
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
