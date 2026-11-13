package pt.dazz.bookmap.mq;

import java.util.Objects;

/**
 * The queues of one operation.
 *
 * @param requestQueue the queue that gets the request. It is mandatory and not blank.
 * @param replyQueue the queue that gets the reply, or {@code null}. If it is {@code null}, the
 *     transport uses its default reply queue, or a temporary queue.
 */
public record MqRoute(String requestQueue, String replyQueue) {

    /**
     * Creates a route and checks the values.
     *
     * @throws NullPointerException if the request queue is {@code null}
     * @throws IllegalArgumentException if a queue name is blank
     */
    public MqRoute {
        Objects.requireNonNull(requestQueue, "requestQueue");
        if (requestQueue.isBlank()) {
            throw new IllegalArgumentException("The request queue is blank");
        }
        if (replyQueue != null && replyQueue.isBlank()) {
            throw new IllegalArgumentException("The reply queue is blank");
        }
    }
}
