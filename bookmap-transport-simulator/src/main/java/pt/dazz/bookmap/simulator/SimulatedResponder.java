package pt.dazz.bookmap.simulator;

/**
 * Typed simulated operation that gives the response object. Use it when the response is not the
 * call object, for example when the response is a subtype that the request did not select.
 *
 * @param <T> the book type
 */
@FunctionalInterface
public interface SimulatedResponder<T> {

    /**
     * Handles one call and gives the response.
     *
     * @param call the decoded request
     * @return the response object, not null
     */
    T respond(T call);
}
