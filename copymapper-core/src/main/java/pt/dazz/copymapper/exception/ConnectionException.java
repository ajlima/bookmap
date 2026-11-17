package pt.dazz.copymapper.exception;

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

    /**
     * Creates the exception with a message and the send state of the request.
     *
     * @param message the message
     * @param requestSent {@code false} if the transport certainly did not send the request,
     *     {@code true} if it possibly sent the request
     */
    public ConnectionException(String message, boolean requestSent) {
        super(message, requestSent);
    }

    /**
     * Creates the exception with a message, a cause, and the send state of the request.
     *
     * @param message the message
     * @param cause the cause
     * @param requestSent {@code false} if the transport certainly did not send the request,
     *     {@code true} if it possibly sent the request
     */
    public ConnectionException(String message, Throwable cause, boolean requestSent) {
        super(message, cause, requestSent);
    }
}
