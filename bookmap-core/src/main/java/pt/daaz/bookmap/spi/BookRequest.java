package pt.daaz.bookmap.spi;

import java.util.Map;
import java.util.Objects;

/**
 * A request that the client gives to a {@link Transport}.
 *
 * @param operation the operation name
 * @param payload the request bytes
 * @param maxResponseLength the maximum length of the response in bytes
 * @param attributes extra transport attributes. A null value becomes an empty map.
 */
public record BookRequest(String operation, byte[] payload, int maxResponseLength,
                          Map<String, String> attributes) {

    /**
     * Creates a request and checks the values.
     */
    public BookRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(payload, "payload");
        attributes = attributes == null ? Map.of() : attributes;
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
