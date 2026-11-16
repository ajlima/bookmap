package pt.dazz.copymapper.exception;

/**
 * Base class of all CopyMapper exceptions. It is unchecked.
 */
public class CopyMapperException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public CopyMapperException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public CopyMapperException(String message, Throwable cause) {
        super(message, cause);
    }
}
