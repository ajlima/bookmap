package pt.dazz.bookmap.spring.mq;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pt.dazz.bookmap.mq.MqCorrelation;

/**
 * Settings of the MQ transport, with the prefix {@code bookmap.transport.mq}.
 *
 * <p>The connection to the queue manager comes from a {@code jakarta.jms.ConnectionFactory} bean,
 * for example from mq-jms-spring-boot-starter and its {@code ibm.mq.*} properties.
 *
 * <p>The Spring Boot configuration processor copies the text of each {@code @param} tag into the
 * configuration metadata. Do not use inline Javadoc tags in the {@code @param} tags.
 *
 * @param user The user for the connection. Without a value, the connection uses the credentials of
 *     the connection factory. Do not set it with a CachingConnectionFactory: that factory does not
 *     accept a user for each connection.
 * @param password The password for the connection. Set it with an environment variable or a secret
 *     store, not in a file in the source repository. A password needs a user.
 * @param mqNativeMessages Sends the messages without an RFH2 header, in the form
 *     queue:///NAME?targetClient=1. Keep true for IBM MQ. Set false for another provider.
 * @param defaultReplyQueue The reply queue for a route without a reply queue. Without a value, the
 *     transport uses a temporary queue.
 * @param correlation How the reply is correlated: message-id (the server copies the message ID of
 *     the request) or correlation-id (the server copies the correlation ID of the request).
 * @param timeout The maximum wait for the reply. It is also the expiry of the request message.
 * @param persistent Sends persistent request messages. A temporary reply queue does not accept
 *     them.
 * @param maxSessions The maximum number of open sessions. The value must be 1 or more.
 * @param sessionWaitTimeout The maximum wait for a free session when all sessions are in use.
 * @param routes The route of each operation. The key is the operation name, for example
 *     routes[GetInvoice].request-queue. An environment variable cannot give a route. At least one
 *     route.
 */
@ConfigurationProperties("bookmap.transport.mq")
public record MqTransportProperties(
        String user,
        String password,
        @DefaultValue("true") boolean mqNativeMessages,
        String defaultReplyQueue,
        @DefaultValue("message-id") MqCorrelation correlation,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("false") boolean persistent,
        @DefaultValue("10") int maxSessions,
        @DefaultValue("30s") Duration sessionWaitTimeout,
        Map<String, Route> routes) {

    /**
     * Replaces a missing map with an empty map.
     */
    public MqTransportProperties {
        routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    /**
     * The queues of one operation.
     *
     * @param requestQueue The queue of the request. Mandatory.
     * @param replyQueue The queue of the reply. Without a value, the transport uses the default reply
     *     queue.
     */
    public record Route(String requestQueue, String replyQueue) {
    }

    /**
     * Shows the settings. The password is hidden.
     *
     * @return the settings, with {@code password=******} when a password is set
     */
    @Override
    public String toString() {
        return "MqTransportProperties[user=" + user + ", password=" + (password == null ? "null" : "******")
            + ", mqNativeMessages=" + mqNativeMessages + ", defaultReplyQueue=" + defaultReplyQueue
            + ", correlation=" + correlation + ", timeout=" + timeout + ", persistent=" + persistent
            + ", maxSessions=" + maxSessions + ", sessionWaitTimeout=" + sessionWaitTimeout
            + ", routes=" + new TreeMap<>(routes) + "]";
    }
}
