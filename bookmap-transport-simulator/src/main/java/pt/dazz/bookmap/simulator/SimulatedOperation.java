package pt.dazz.bookmap.simulator;

/** Byte-level simulated operation. It receives the request payload and returns the response. */
@FunctionalInterface
public interface SimulatedOperation {

    /**
     * Handles one request.
     *
     * @param request the request payload
     * @return the response payload
     */
    byte[] handle(byte[] request);
}
