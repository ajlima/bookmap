package pt.dazz.copymapper.copybook.spec;

/** Signals that the JSON text of a mapping spec is not valid. */
public class MappingSpecFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message, with the JSON path of the fault
     */
    public MappingSpecFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception with its cause.
     *
     * @param message the message, with the JSON path of the fault
     * @param cause the cause
     */
    public MappingSpecFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
