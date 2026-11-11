package pt.daaz.bookmap.exception;

/**
 * Signals that the remote program did not answer in time.
 */
public class TransportTimeoutException extends TransportException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public TransportTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public TransportTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
