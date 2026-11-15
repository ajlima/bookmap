package pt.dazz.bookmap.exception;

/**
 * Signals an error in the layout, in a conversion, or a missing codec.
 */
public class MappingException extends BookmapException {

    private static final long serialVersionUID = 1L;

    /** The collections and the indexes of the items, outer level first. */
    private String itemPrefix = "";

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

    /**
     * Adds the collection and the index of an item to the start of the message.
     * The generated codecs call this method. Do not call it from application code.
     *
     * <p>The method changes this exception and returns it. The class, the cause, and the stack
     * trace do not change. Each call adds its text before the text of the earlier calls. Thus,
     * the outer collection comes first.
     *
     * @param collection the descriptor of the collection
     * @param index the index of the item, from 0
     * @return this exception
     */
    public MappingException atItem(String collection, int index) {
        itemPrefix = collection + " item " + index + ": " + itemPrefix;
        return this;
    }

    /**
     * Gives the message, with the collection and the index of each item from
     * {@link #atItem(String, int)} at the start.
     *
     * @return the message
     */
    @Override
    public String getMessage() {
        String message = super.getMessage();
        return itemPrefix.isEmpty() ? message : itemPrefix + message;
    }
}
