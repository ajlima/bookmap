package pt.dazz.bookmap.copybook.tree;

/**
 * Tells that the generation of the copybook classes failed. The message is for the user.
 */
public final class CopybookTreeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message the message for the user
     */
    public CopybookTreeException(String message) {
        super(message);
    }
}
