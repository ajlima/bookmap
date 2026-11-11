package pt.daaz.bookmap.simulator;

/**
 * Typed simulated operation. The call object has the input fields filled in. The handler sets the
 * output fields on the same object.
 *
 * @param <T> the book type
 */
@FunctionalInterface
public interface SimulatedHandler<T> {

    /**
     * Handles one call.
     *
     * @param call the decoded request, also used for the response
     */
    void handle(T call);
}
