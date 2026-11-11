package pt.dazz.bookmap.copybook.json;

/**
 * Tells that a JSON text is not a valid model or configuration.
 */
public class ModelJsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message
     */
    public ModelJsonException(String message) {
        super(message);
    }
}
