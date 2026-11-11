package pt.daaz.bookmap.examples;

import pt.daaz.bookmap.exception.BusinessException;

/**
 * The remote program refused an order query.
 */
public class OrderQueryException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String returnCode;

    /**
     * Creates the exception.
     *
     * @param returnCode the return code from the remote program
     * @param message the return message from the remote program
     */
    public OrderQueryException(String returnCode, String message) {
        super(returnCode + ": " + message);
        this.returnCode = returnCode;
    }

    /**
     * Returns the return code from the remote program.
     *
     * @return the return code
     */
    public String returnCode() {
        return returnCode;
    }
}
