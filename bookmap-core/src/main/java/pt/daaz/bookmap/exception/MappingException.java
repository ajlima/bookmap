package pt.daaz.bookmap.exception;

/**
 * Signals an error in the layout, in a conversion, or a missing codec.
 */
public class MappingException extends BookmapException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public MappingException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
