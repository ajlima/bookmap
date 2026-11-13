package pt.dazz.bookmap.mq;

import java.time.Duration;

/**
 * One session to the queue manager. One thread at a time uses a session.
 */
public interface MqSession extends AutoCloseable {

    /**
     * Puts a request message.
     *
     * @param request the request
     * @return the message ID of the request
     * @throws MqException if the put fails
     */
    String send(MqRequest request) throws MqException;

    /**
     * Waits for the reply with a correlation ID.
     *
     * @param replyQueue the reply queue, or {@code null} for the temporary queue of the session
     * @param correlationId the correlation ID of the reply
     * @param wait the maximum wait
     * @return the bytes of the reply, or {@code null} if no reply comes during the wait
     * @throws MqException if the get fails, or if the reply is not a bytes message
     *     ({@link MqFailure#NOT_BYTES})
     */
    byte[] receive(String replyQueue, String correlationId, Duration wait) throws MqException;

    /**
     * Closes the session and its connection.
     *
     * @throws MqException if the close fails
     */
    @Override
    void close() throws MqException;
}
