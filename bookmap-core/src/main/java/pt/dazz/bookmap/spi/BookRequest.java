package pt.dazz.bookmap.spi;

import java.util.Map;
import java.util.Objects;

/**
 * A request that the client gives to a {@link Transport}.
 *
 * <p>The record does not copy the payload, to prevent a copy for each exchange. Do not change
 * the payload bytes after you create the request.
 *
 * @param operation the operation name
 * @param payload the request bytes
 * @param maxResponseLength the maximum length of the response in bytes
 * @param attributes extra transport attributes. The record keeps a copy of the map. A null map
 *     becomes an empty map. A null key or a null value is not permitted.
 */
public record BookRequest(String operation, byte[] payload, int maxResponseLength,
                          Map<String, String> attributes) {

    /**
     * Creates a request and checks the values.
     *
     * @throws NullPointerException if the operation or the payload is null, or if the
     *     attributes contain a null key or a null value
     */
    public BookRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(payload, "payload");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Creates a request without attributes.
     *
     * @param operation the operation name
     * @param payload the request bytes
     * @param maxResponseLength the maximum length of the response in bytes
     */
    public BookRequest(String operation, byte[] payload, int maxResponseLength) {
        this(operation, payload, maxResponseLength, null);
    }
}
