package pt.dazz.bookmap.mq;

import java.util.Objects;

/**
 * Signals a failure of an {@link MqConnector} or an {@link MqSession}. The transport changes it
 * to a BookMap exception.
 */
public final class MqException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The type of the failure. */
    private final MqFailure failure;

    /**
     * Creates the exception.
     *
     * @param failure the type of the failure
     * @param message the message
     * @param cause the cause, or {@code null}
     * @throws NullPointerException if the failure is {@code null}
     */
    public MqException(MqFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Gives the type of the failure.
     *
     * @return the type
     */
    public MqFailure failure() {
        return failure;
    }
}
