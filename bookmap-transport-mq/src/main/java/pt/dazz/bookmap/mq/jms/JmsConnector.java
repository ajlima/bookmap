package pt.dazz.bookmap.mq.jms;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.InvalidDestinationException;
import jakarta.jms.JMSException;
import jakarta.jms.JMSSecurityException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import pt.dazz.bookmap.mq.MqConnector;
import pt.dazz.bookmap.mq.MqException;
import pt.dazz.bookmap.mq.MqFailure;
import pt.dazz.bookmap.mq.MqRequest;
import pt.dazz.bookmap.mq.MqSession;

/**
 * An {@link MqConnector} for Jakarta Messaging. For IBM MQ, give it an {@code MQConnectionFactory}
 * of the IBM MQ classes for Jakarta Messaging. Another provider also works.
 *
 * <p>Each session has its own {@link Connection}. Thus, a broken connection affects only one
 * session.
 */
public final class JmsConnector implements MqConnector {

    private static final int PRIORITY = 4;
    private static final String MQ_QUEUE_PREFIX = "queue://";

    private final ConnectionFactory connectionFactory;
    private final String user;
    private final String password;
    private final boolean mqNativeMessages;

    private JmsConnector(Builder builder) {
        this.connectionFactory = builder.connectionFactory;
        this.user = builder.user;
        this.password = builder.password;
        this.mqNativeMessages = builder.mqNativeMessages;
    }

    /**
     * Creates a builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a connection, a session without a transaction, and a producer. Then it starts the
     * connection.
     *
     * @return the session
     * @throws MqException if a step fails. The connector then closes the connection.
     */
    @Override
    public MqSession open() throws MqException {
        Connection connection;
        try {
            connection = user == null
                ? connectionFactory.createConnection()
                : connectionFactory.createConnection(user, password);
        } catch (JMSException e) {
            throw failure("connect failed", e);
        }
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(null);
            connection.start();
            return new JmsSession(connection, session, producer);
        } catch (JMSException e) {
            try {
                connection.close();
            } catch (JMSException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw failure("open session failed", e);
        }
    }

    /**
     * Gives the name for {@code Session.createQueue}. With MQ native messages, a plain queue name
     * becomes {@code queue:///NAME?targetClient=1}: IBM MQ then sends the message without an RFH2
     * header. A name that starts with {@code queue://} does not change.
     *
     * @param name the queue name
     * @return the name for the provider
     */
    String queueName(String name) {
        if (!mqNativeMessages || name.startsWith(MQ_QUEUE_PREFIX)) {
            return name;
        }
        return "queue:///" + name + "?targetClient=1";
    }

    /**
     * Gives the failure type of a Jakarta Messaging exception.
     *
     * @param e the exception
     * @return the failure type
     */
    static MqFailure failureOf(JMSException e) {
        return switch (e) {
            case JMSSecurityException security -> MqFailure.NOT_AUTHORIZED;
            case InvalidDestinationException destination -> MqFailure.INVALID_QUEUE;
            default -> MqFailure.OTHER;
        };
    }

    /** Gives the body of a reply, or {@code null} for no reply. */
    private static byte[] bytesOf(Message message) throws JMSException, MqException {
        if (message == null) {
            return null;
        }
        if (!(message instanceof BytesMessage bytes)) {
            throw new MqException(MqFailure.NOT_BYTES,
                "the reply is not a bytes message: " + message.getClass().getName(), null);
        }
        byte[] data = new byte[(int) bytes.getBodyLength()];
        bytes.readBytes(data);
        return data;
    }

    private static MqException failure(String text, JMSException e) {
        return new MqException(failureOf(e), text + ": " + e.getMessage(), e);
    }

    private final class JmsSession implements MqSession {

        private final Connection connection;
        private final Session session;
        private final MessageProducer producer;
        private final Map<String, Queue> queues = new HashMap<>();
        private Queue temporaryQueue;

        JmsSession(Connection connection, Session session, MessageProducer producer) {
            this.connection = connection;
            this.session = session;
            this.producer = producer;
        }

        @Override
        public String send(MqRequest request) throws MqException {
            try {
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(request.payload());
                message.setJMSReplyTo(queue(request.replyQueue()));
                if (request.correlationId() != null) {
                    message.setJMSCorrelationID(request.correlationId());
                }
                int deliveryMode = request.persistent()
                    ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
                producer.send(queue(request.requestQueue()), message, deliveryMode, PRIORITY,
                    request.timeToLive().toMillis());
                return message.getJMSMessageID();
            } catch (JMSException e) {
                throw failure("send failed", e);
            }
        }

        @Override
        public byte[] receive(String replyQueue, String correlationId, Duration wait)
                throws MqException {
            String selector = "JMSCorrelationID = '" + correlationId.replace("'", "''") + "'";
            try {
                MessageConsumer consumer = session.createConsumer(queue(replyQueue), selector);
                try {
                    // A wait of 0 milliseconds means "no limit" in Jakarta Messaging.
                    return bytesOf(consumer.receive(Math.max(1, wait.toMillis())));
                } finally {
                    consumer.close();
                }
            } catch (JMSException e) {
                throw failure("receive failed", e);
            }
        }

        @Override
        public void close() throws MqException {
            try {
                connection.close();
            } catch (JMSException e) {
                throw failure("close failed", e);
            }
        }

        /** Gives the queue of a name, or the temporary queue of the session for {@code null}. */
        private Queue queue(String name) throws JMSException {
            if (name == null) {
                if (temporaryQueue == null) {
                    temporaryQueue = session.createTemporaryQueue();
                }
                return temporaryQueue;
            }
            Queue queue = queues.get(name);
            if (queue == null) {
                queue = session.createQueue(queueName(name));
                queues.put(name, queue);
            }
            return queue;
        }
    }

    /**
     * Builds a {@link JmsConnector}.
     */
    public static final class Builder {

        private ConnectionFactory connectionFactory;
        private String user;
        private String password;
        private boolean mqNativeMessages = true;

        private Builder() {
        }

        /**
         * Sets the connection factory of the provider. This value is mandatory.
         *
         * @param connectionFactory the connection factory
         * @return this builder
         */
        public Builder connectionFactory(ConnectionFactory connectionFactory) {
            this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
            return this;
        }

        /**
         * Sets the user and the password for the connection. A {@code null} user removes the
         * credentials: the connector then calls {@code createConnection()}.
         *
         * @param user the user, or {@code null}
         * @param password the password
         * @return this builder
         */
        public Builder credentials(String user, String password) {
            this.user = user;
            this.password = password;
            return this;
        }

        /**
         * Sends messages without an RFH2 header, with the format {@code MQFMT_NONE}. The default
         * value is {@code true}. Use {@code false} for a provider that is not IBM MQ.
         *
         * @param mqNativeMessages {@code true} for IBM MQ native messages
         * @return this builder
         */
        public Builder mqNativeMessages(boolean mqNativeMessages) {
            this.mqNativeMessages = mqNativeMessages;
            return this;
        }

        /**
         * Creates the connector.
         *
         * @return the connector
         * @throws IllegalStateException if the connection factory is not set
         */
        public JmsConnector build() {
            if (connectionFactory == null) {
                throw new IllegalStateException("The connection factory is not set");
            }
            return new JmsConnector(this);
        }
    }
}
