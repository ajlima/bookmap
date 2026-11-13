package pt.dazz.bookmap.mq.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionConsumer;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.ServerSessionPool;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * A connection factory for failure tests. Without a delegate, its connections fail at
 * {@code createSession}. With a delegate, {@code createSession} and {@code start} use the
 * delegate. With {@code failClose}, {@code close} throws. The test reads {@link #closed} to check
 * that the connector closes the connection.
 */
final class StubConnectionFactory implements ConnectionFactory {

    volatile boolean closed;
    private final Connection delegate;
    private final boolean failClose;

    StubConnectionFactory(Connection delegate, boolean failClose) {
        this.delegate = delegate;
        this.failClose = failClose;
    }

    @Override
    public Connection createConnection() {
        return new FailingConnection();
    }

    @Override
    public Connection createConnection(String userName, String password) {
        return new FailingConnection();
    }

    @Override
    public JMSContext createContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JMSContext createContext(String userName, String password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        throw new UnsupportedOperationException();
    }

    private final class FailingConnection implements Connection {

        @Override
        public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
            if (delegate == null) {
                throw new JMSException("no session");
            }
            return delegate.createSession(transacted, acknowledgeMode);
        }

        @Override
        public Session createSession(int sessionMode) throws JMSException {
            throw new JMSException("no session");
        }

        @Override
        public Session createSession() throws JMSException {
            throw new JMSException("no session");
        }

        @Override
        public void close() throws JMSException {
            closed = true;
            if (failClose) {
                throw new JMSException("close failed too");
            }
        }

        @Override
        public String getClientID() {
            return null;
        }

        @Override
        public void setClientID(String clientID) {
        }

        @Override
        public ConnectionMetaData getMetaData() {
            return null;
        }

        @Override
        public ExceptionListener getExceptionListener() {
            return null;
        }

        @Override
        public void setExceptionListener(ExceptionListener listener) {
        }

        @Override
        public void start() throws JMSException {
            if (delegate != null) {
                delegate.start();
            }
        }

        @Override
        public void stop() {
        }

        @Override
        public ConnectionConsumer createConnectionConsumer(Destination destination,
                String messageSelector, ServerSessionPool sessionPool, int maxMessages) {
            return null;
        }

        @Override
        public ConnectionConsumer createSharedConnectionConsumer(Topic topic,
                String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
                int maxMessages) {
            return null;
        }

        @Override
        public ConnectionConsumer createDurableConnectionConsumer(Topic topic,
                String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
                int maxMessages) {
            return null;
        }

        @Override
        public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
                String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
                int maxMessages) {
            return null;
        }
    }
}
