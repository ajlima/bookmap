package pt.daaz.bookmap.exception;

/**
 * Signals a technical failure in the transport.
 */
public class TransportException extends BookmapException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public TransportException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
