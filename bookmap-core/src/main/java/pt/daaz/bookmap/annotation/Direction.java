package pt.daaz.bookmap.annotation;

/**
 * Tells in which message a field or collection appears.
 */
public enum Direction {
    /** The field goes in the request only. */
    INPUT,
    /** The field comes in the response only. */
    OUTPUT,
    /** The field goes in the request and comes in the response. */
    INPUT_OUTPUT;

    /**
     * Tells if the field appears in the request.
     *
     * @return true for {@link #INPUT} and {@link #INPUT_OUTPUT}
     */
    public boolean isInput() {
        return this != OUTPUT;
    }

    /**
     * Tells if the field appears in the response.
     *
     * @return true for {@link #OUTPUT} and {@link #INPUT_OUTPUT}
     */
    public boolean isOutput() {
        return this != INPUT;
    }
}
