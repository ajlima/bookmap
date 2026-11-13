package pt.dazz.bookmap.spring.mq;

import jakarta.jms.ConnectionFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import pt.dazz.bookmap.mq.MqConnector;
import pt.dazz.bookmap.mq.MqRoute;
import pt.dazz.bookmap.mq.MqTransport;
import pt.dazz.bookmap.mq.jms.JmsConnector;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

/**
 * Creates an {@link MqTransport} bean from the {@code bookmap.transport.mq.*} properties, when
 * {@code bookmap.transport.type=mq}.
 *
 * <p>The connector is an {@link MqConnector} bean. Without one, this class creates a
 * {@link JmsConnector} with the {@code jakarta.jms.ConnectionFactory} bean of the context. The
 * factory is read when the connector is created, so the order of the auto-configuration that
 * creates the factory does not matter.
 */
@AutoConfiguration(before = BookmapAutoConfiguration.class)
@ConditionalOnClass(MqTransport.class)
@ConditionalOnProperty(name = "bookmap.transport.type", havingValue = "mq")
@EnableConfigurationProperties(MqTransportProperties.class)
public class MqTransportAutoConfiguration {

    private static final String PREFIX = "bookmap.transport.mq.";

    /**
     * Creates the auto-configuration. Spring Boot calls this constructor.
     */
    public MqTransportAutoConfiguration() {
        // No state.
    }

    /**
     * Creates the connector for Jakarta Messaging.
     *
     * @param properties the {@code bookmap.transport.mq.*} settings
     * @param connectionFactories the connection factory beans
     * @return the connector
     * @throws IllegalStateException if no single connection factory exists, or if a password has no
     *     user
     */
    @Bean
    @ConditionalOnMissingBean({MqConnector.class, Transport.class})
    JmsConnector jmsConnector(MqTransportProperties properties,
                              ObjectProvider<ConnectionFactory> connectionFactories) {
        ConnectionFactory factory = connectionFactories.getIfUnique();
        if (factory == null) {
            throw new IllegalStateException("No MqConnector bean. Declare one jakarta.jms.ConnectionFactory "
                + "bean, for example with mq-jms-spring-boot-starter, or declare an MqConnector bean.");
        }
        JmsConnector.Builder builder = JmsConnector.builder()
            .connectionFactory(factory)
            .mqNativeMessages(properties.mqNativeMessages());
        if (properties.user() != null) {
            builder.credentials(properties.user(), properties.password());
        } else if (properties.password() != null) {
            throw new IllegalStateException(PREFIX + "password is set, but " + PREFIX + "user is not set.");
        }
        return builder.build();
    }

    /**
     * Builds the transport. Spring Boot calls {@code close()} of the transport when the context
     * closes.
     *
     * @param properties the {@code bookmap.transport.mq.*} settings
     * @param connector the connector bean
     * @param customizers the customizer beans
     * @return the transport
     * @throws IllegalStateException if a setting is not valid
     */
    @Bean
    @ConditionalOnMissingBean(Transport.class)
    MqTransport mqTransport(MqTransportProperties properties, MqConnector connector,
                            ObjectProvider<MqTransportBuilderCustomizer> customizers) {
        MqTransport.Builder builder = MqTransport.builder()
            .connector(connector)
            .defaultReplyQueue(properties.defaultReplyQueue())
            .correlation(properties.correlation())
            .timeout(properties.timeout())
            .persistent(properties.persistent())
            .maxSessions(properties.maxSessions())
            .sessionWaitTimeout(properties.sessionWaitTimeout());
        Map<String, MqRoute> routes = new LinkedHashMap<>();
        properties.routes().forEach((operation, route) -> {
            if (route.requestQueue() == null) {
                throw new IllegalStateException(PREFIX + "routes[" + operation + "].request-queue is not set.");
            }
            routes.put(operation, new MqRoute(route.requestQueue(), route.replyQueue()));
        });
        builder.routes(routes);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }
}
