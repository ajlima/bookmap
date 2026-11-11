package pt.daaz.bookmap;

/**
 * Marks a book that the remote program can answer in many pages.
 * The client calls the operation again while {@link #hasContinuation()} returns true.
 */
public interface Continuable {

    /**
     * Tells if the remote program has more data.
     *
     * @return true if the client must call the operation again
     */
    boolean hasContinuation();
}
