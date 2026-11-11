package pt.dazz.bookmap.spi;

/**
 * Sends one request to the remote system and returns the response bytes.
 * An implementation translates technical failures into bookmap exceptions.
 * It must not throw middleware exceptions.
 */
public interface Transport {

    /**
     * Sends a request and waits for the response.
     *
     * @param request the request
     * @return the response bytes
     */
    byte[] exchange(BookRequest request);
}
