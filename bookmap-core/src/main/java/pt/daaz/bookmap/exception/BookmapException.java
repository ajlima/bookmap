package pt.daaz.bookmap.exception;

/**
 * Base class of all bookmap exceptions. It is unchecked.
 */
public class BookmapException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public BookmapException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public BookmapException(String message, Throwable cause) {
        super(message, cause);
    }
}
