package pt.dazz.bookmap.exception;

/**
 * Signals that the bytes of a field are not a valid value of its type, for example a byte that
 * is not valid in a packed decimal, a text that is not a date, or an unknown enum value.
 *
 * <p>In a {@code REDEFINES} area, the codec reads a member with this problem as {@code null}.
 * Other mapping problems, for example a short response or a failure of a converter, are not
 * of this type.
 */
public class InvalidValueException extends MappingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public InvalidValueException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public InvalidValueException(String message, Throwable cause) {
        super(message, cause);
    }
}
