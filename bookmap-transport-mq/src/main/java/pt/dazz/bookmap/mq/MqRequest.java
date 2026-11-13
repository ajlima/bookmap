package pt.dazz.bookmap.mq;

import java.time.Duration;
import java.util.Objects;

/**
 * A request message that the transport gives to {@link MqSession#send(MqRequest)}.
 *
 * @param requestQueue the queue that gets the request
 * @param replyQueue the reply-to queue, or {@code null} for the temporary queue of the session
 * @param payload the request bytes
 * @param correlationId the correlation ID of the request, or {@code null} for no correlation ID
 * @param timeToLive the expiry of the message. {@link Duration#ZERO} means no expiry.
 * @param persistent {@code true} for a persistent message
 */
public record MqRequest(String requestQueue, String replyQueue, byte[] payload,
                        String correlationId, Duration timeToLive, boolean persistent) {

    /**
     * Creates a request and checks the values.
     *
     * @throws NullPointerException if the request queue, the payload, or the time to live is
     *     {@code null}
     */
    public MqRequest {
        Objects.requireNonNull(requestQueue, "requestQueue");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(timeToLive, "timeToLive");
    }
}
