package pt.dazz.bookmap.spi;

import java.util.List;
import pt.dazz.bookmap.convert.CodecContext;

/**
 * Converts a book class to bytes and back.
 * The annotation processor generates one implementation for each {@code @BookOperation} class.
 * For a class with {@code @BookSubTypes}, one codec handles the base class and all subtypes.
 *
 * @param <T> the book class
 */
public interface BookCodec<T> {

    /**
     * Gives the book class.
     *
     * @return the class
     */
    Class<T> type();

    /**
     * Gives the classes that this codec handles. A codec with subtypes gives the base class (if
     * it is not abstract) and each subtype. The registry finds the codec by each of these
     * classes.
     *
     * @return the classes, by default only {@link #type()}
     */
    default List<Class<? extends T>> types() {
        return List.of(type());
    }

    /**
     * Gives the operation name.
     *
     * @return the name
     */
    String operationName();

    /**
     * Gives the name of the charset that the book defines. It can be empty.
     *
     * @return the charset name
     */
    String charsetName();

    /**
     * Gives the maximum length of the response in bytes.
     *
     * @return the length
     */
    int maxResponseLength();

    /**
     * Creates an empty book instance.
     *
     * @return the new instance
     */
    T newInstance();

    /**
     * Writes the input fields of the book to bytes.
     *
     * @param value the book
     * @param ctx the codec context
     * @return the request bytes
     */
    byte[] encodeRequest(T value, CodecContext ctx);

    /**
     * Reads the output fields from the response bytes into the book. For an {@code OUTPUT}
     * collection, the codec sets a new list with the existing items and the decoded items.
     * For an {@code INPUT_OUTPUT} collection, the codec sets a new list with the decoded items.
     * With subtypes, the response must select the class of the target. If not, the codec throws
     * a {@code MappingException}.
     *
     * @param data the response bytes
     * @param target the book that receives the values
     * @param ctx the codec context
     */
    void decodeResponse(byte[] data, T target, CodecContext ctx);

    /**
     * Reads a response and gives the book that contains it. Without subtypes, this is the
     * target. With subtypes, the response can select a subtype of the target class. Then the
     * codec creates an instance of the subtype, copies the base members of the target to it,
     * and reads the response into it. The client calls this method for the first response of
     * an execution.
     *
     * <p>The default implementation calls {@link #decodeResponse} and gives the target.
     *
     * @param data the response bytes
     * @param target the book of the request
     * @param ctx the codec context
     * @return the book that contains the response
     */
    default T readResponse(byte[] data, T target, CodecContext ctx) {
        decodeResponse(data, target, ctx);
        return target;
    }

    /**
     * Reads the input fields from request bytes into a new book.
     *
     * @param data the request bytes
     * @param ctx the codec context
     * @return the new book
     */
    T decodeRequest(byte[] data, CodecContext ctx);

    /**
     * Writes the output fields of the book to bytes.
     *
     * @param value the book
     * @param ctx the codec context
     * @return the response bytes
     */
    byte[] encodeResponse(T value, CodecContext ctx);

    /**
     * Sets each {@code OUTPUT} collection of the book to a new, empty list.
     *
     * @param value the book
     */
    void resetOutputCollections(T value);
}
