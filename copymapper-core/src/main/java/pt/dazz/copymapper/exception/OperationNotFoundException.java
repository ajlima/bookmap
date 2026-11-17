package pt.dazz.copymapper.exception;

/**
 * Signals that the remote system does not know the operation.
 */
public class OperationNotFoundException extends TransportException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public OperationNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public OperationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception with a message and the send state of the request.
     *
     * @param message the message
     * @param requestSent {@code false} if the transport certainly did not send the request,
     *     {@code true} if it possibly sent the request
     */
    public OperationNotFoundException(String message, boolean requestSent) {
        super(message, requestSent);
    }
}
