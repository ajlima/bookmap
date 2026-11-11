package pt.dazz.bookmap.copybook.map;

import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.parse.Picture;

/**
 * Gives the {@code pic} text of a generated field.
 */
public final class PicText {

    private PicText() {
    }

    /**
     * Gives the picture with the effective usage. A {@code DISPLAY} usage is not written.
     *
     * @param picture the picture
     * @param usage the effective usage
     * @return the text, for example {@code S9(7)V99 COMP-3}
     */
    public static String of(Picture picture, Usage usage) {
        return switch (usage) {
            case DISPLAY -> picture.text();
            case COMP -> picture.text() + " COMP";
            case COMP3 -> picture.text() + " COMP-3";
        };
    }

    /**
     * Gives the number of bytes of an elementary item, with the BookMap rules.
     *
     * @param picture the picture
     * @param usage the effective usage
     * @return the number of bytes
     */
    public static int byteLength(Picture picture, Usage usage) {
        int length = picture.length();
        return switch (usage) {
            case DISPLAY -> length;
            case COMP3 -> length / 2 + 1;
            case COMP -> length <= 4 ? 2 : length <= 9 ? 4 : 8;
        };
    }
}
