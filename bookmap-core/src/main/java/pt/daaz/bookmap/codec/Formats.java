package pt.daaz.bookmap.codec;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Makes the date and time formatters for the fields of a book.
 * A generated codec keeps each formatter in a {@code private static final} field.
 */
public final class Formats {

    private Formats() {
    }

    /**
     * Makes a strict formatter for a pattern. The method changes each {@code y}
     * (year of era) to {@code u} (year) when it is not in a quoted literal.
     * Thus, the strict resolver does not need an era.
     * The formatter uses {@link ResolverStyle#STRICT} and {@link Locale#ROOT}.
     *
     * @param pattern a {@link DateTimeFormatter} pattern
     * @return the formatter
     * @throws IllegalArgumentException if the pattern is not valid
     */
    public static DateTimeFormatter of(String pattern) {
        var converted = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            converted.append(c == 'y' && !quoted ? 'u' : c);
        }
        return DateTimeFormatter.ofPattern(converted.toString(), Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    }
}
