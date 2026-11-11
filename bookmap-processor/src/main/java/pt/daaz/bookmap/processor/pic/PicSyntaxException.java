package pt.daaz.bookmap.processor.pic;

/**
 * Tells that a {@code PIC} string is not correct or is not supported.
 * The message names the problem and the original string.
 */
public class PicSyntaxException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for a {@code PIC} string and a problem.
     *
     * @param pic the original {@code PIC} string
     * @param problem the description of the problem
     */
    public PicSyntaxException(String pic, String problem) {
        super("Invalid PIC string \"" + pic + "\": " + problem);
    }
}
