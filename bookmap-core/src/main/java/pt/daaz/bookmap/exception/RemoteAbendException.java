package pt.daaz.bookmap.exception;

/**
 * Signals that the remote program stopped abnormally.
 */
public class RemoteAbendException extends TransportException {

    private static final long serialVersionUID = 1L;

    private final String abendCode;

    /**
     * Creates the exception with an abend code and a message.
     *
     * @param abendCode the abend code, for example {@code ASRA}
     * @param message the message
     */
    public RemoteAbendException(String abendCode, String message) {
        super(message);
        this.abendCode = abendCode;
    }

    /**
     * Creates the exception with an abend code, a message, and a cause.
     *
     * @param abendCode the abend code, for example {@code ASRA}
     * @param message the message
     * @param cause the cause
     */
    public RemoteAbendException(String abendCode, String message, Throwable cause) {
        super(message, cause);
        this.abendCode = abendCode;
    }

    /**
     * Gives the abend code.
     *
     * @return the abend code
     */
    public String abendCode() {
        return abendCode;
    }
}
