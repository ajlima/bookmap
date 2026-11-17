package pt.dazz.copymapper.exception;

/**
 * Signals a technical failure in the transport.
 */
public class TransportException extends CopyMapperException {

    private static final long serialVersionUID = 1L;

    /**
     * {@code true} if the transport certainly did not send the request. The field stores the
     * inverse of {@link #requestSent()}, so an exception that an older version serialized
     * (without this field) reads as "possibly sent".
     */
    private final boolean notSent;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public TransportException(String message) {
        this(message, true);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public TransportException(String message, Throwable cause) {
        this(message, cause, true);
    }

    /**
     * Creates the exception with a message and the send state of the request.
     *
     * @param message the message
     * @param requestSent {@code false} if the transport certainly did not send the request,
     *     {@code true} if it possibly sent the request
     */
    public TransportException(String message, boolean requestSent) {
        super(message);
        this.notSent = !requestSent;
    }

    /**
     * Creates the exception with a message, a cause, and the send state of the request.
     *
     * @param message the message
     * @param cause the cause
     * @param requestSent {@code false} if the transport certainly did not send the request,
     *     {@code true} if it possibly sent the request
     */
    public TransportException(String message, Throwable cause, boolean requestSent) {
        super(message, cause);
        this.notSent = !requestSent;
    }

    /**
     * Tells if the transport possibly sent the request to the remote system.
     *
     * @return {@code false} if the transport certainly did not send the request, so a new try
     *     is safe. {@code true} if the transport possibly sent the request, so the remote
     *     system can have run it.
     */
    public boolean requestSent() {
        return !notSent;
    }
}
