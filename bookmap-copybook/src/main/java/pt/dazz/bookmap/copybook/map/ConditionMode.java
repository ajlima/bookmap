package pt.dazz.bookmap.copybook.map;

import java.util.Locale;

/**
 * Tells what the generator writes for the level 88 items of one elementary item.
 */
public enum ConditionMode {
    /** A Java enum, and an enum field. */
    ENUM,
    /** A constant and a boolean method for each level 88 item. */
    CONSTANTS,
    /** Nothing. The level 88 items are ignored. */
    NONE;

    /**
     * Gives the mode of a word. The word is not case-sensitive.
     *
     * @param word {@code enum}, {@code constants}, or {@code none}
     * @return the mode
     * @throws IllegalArgumentException if the word is not a mode
     */
    public static ConditionMode of(String word) {
        for (ConditionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(word)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown condition mode '" + word
            + "'. Use enum, constants, or none.");
    }

    /**
     * Gives the word of the mode, in lower case.
     *
     * @return the word
     */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
