package pt.daaz.bookmap.exception;

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
}
