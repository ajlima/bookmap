package pt.dazz.bookmap.exception;

/**
 * Signals that the client reached the maximum number of calls for one continued operation.
 */
public class ContinuationLimitException extends BookmapException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public ContinuationLimitException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ContinuationLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
