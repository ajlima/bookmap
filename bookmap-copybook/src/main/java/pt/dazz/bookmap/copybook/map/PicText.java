package pt.dazz.bookmap.copybook.map;

import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.copybook.ast.Picture;
import pt.dazz.bookmap.copybook.ast.Usage;

/**
 * Gives the {@code pic} text of a generated field.
 */
public final class PicText {

    private PicText() {
    }

    /**
     * Gives the picture with the effective usage and sign. A {@code DISPLAY} usage and the
     * default sign are not written.
     *
     * @param picture the picture
     * @param usage the effective usage
     * @param sign the effective sign, or {@code null} for the default
     * @return the text, for example {@code S9(7)V99 COMP-3},
     *     {@code S9(5)V99 SIGN LEADING SEPARATE}, or {@code ZZ9.99 BLANK WHEN ZERO}
     */
    public static String of(Picture picture, Usage usage, Sign sign) {
        return switch (usage) {
            case DISPLAY -> picture.edited() != null ? picture.edited().toString()
                : sign == null ? picture.text()
                : picture.text() + " SIGN " + (sign.leading() ? "LEADING" : "TRAILING")
                    + (sign.separate() ? " SEPARATE" : "");
            case COMP, COMP5 -> picture.text() + " COMP";
            case COMP3 -> picture.text() + " COMP-3";
        };
    }

    /**
     * Gives the number of bytes of an elementary item, with the BookMap rules.
     *
     * @param picture the picture
     * @param usage the effective usage
     * @param sign the effective sign, or {@code null} for the default
     * @return the number of bytes
     */
    public static int byteLength(Picture picture, Usage usage, Sign sign) {
        return picture.byteLength(usage, sign);
    }
}
