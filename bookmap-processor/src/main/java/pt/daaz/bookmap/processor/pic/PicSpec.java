package pt.daaz.bookmap.processor.pic;

import java.util.Optional;
import pt.daaz.bookmap.annotation.Usage;

/**
 * Gives the result of a parsed {@code PIC} string.
 *
 * @param category the category of the field: text or number
 * @param length the number of characters for text, or the total number of digits for a number
 * @param scale the number of implied decimal places. It is 0 for text.
 * @param signed {@code true} if the picture starts with {@code S}
 * @param usage the usage from the usage clause. It is empty when the string has no usage clause.
 */
public record PicSpec(Category category, int length, int scale, boolean signed,
    Optional<Usage> usage) {

    /**
     * Gives the category of a {@code PIC} string.
     */
    public enum Category {
        /** Alphanumeric text ({@code X}). */
        TEXT,
        /** Number ({@code 9}, with optional {@code S} and {@code V}). */
        NUMBER
    }
}
