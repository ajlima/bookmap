package pt.daaz.bookmap.spi;

import pt.daaz.bookmap.convert.CodecContext;

/**
 * Converts a book class to bytes and back.
 * The annotation processor generates one implementation for each {@code @BookOperation} class.
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
     * Reads the output fields from the response bytes into the book.
     *
     * @param data the response bytes
     * @param target the book that receives the values
     * @param ctx the codec context
     */
    void decodeResponse(byte[] data, T target, CodecContext ctx);

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
     * Clears the output collections of the book.
     *
     * @param value the book
     */
    void resetOutputCollections(T value);
}
