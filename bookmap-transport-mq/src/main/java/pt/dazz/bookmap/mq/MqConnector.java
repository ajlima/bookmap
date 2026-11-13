package pt.dazz.bookmap.mq;

/**
 * Opens sessions to the queue manager. {@code JmsConnector} is the implementation for Jakarta
 * Messaging. A test can use a fake.
 */
public interface MqConnector {

    /**
     * Connects to the queue manager and opens one session.
     *
     * @return the session
     * @throws MqException if the connection or the session fails
     */
    MqSession open() throws MqException;
}
