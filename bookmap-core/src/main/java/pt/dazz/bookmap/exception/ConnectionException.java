package pt.dazz.bookmap.exception;

/**
 * Signals that the transport cannot connect to the remote system.
 */
public class ConnectionException extends TransportException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public ConnectionException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
