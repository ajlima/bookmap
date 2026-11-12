package pt.dazz.bookmap.copybook.gradle;

/**
 * Tells that the generation of the copybook classes failed. The message is for the user.
 */
final class CopybookGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message the message for the user
     */
    CopybookGenerationException(String message) {
        super(message);
    }
}
