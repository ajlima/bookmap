package pt.dazz.bookmap.spring.ctg;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.dazz.bookmap.ctg.CtgRoute;
import pt.dazz.bookmap.ctg.CtgTransport;
import pt.dazz.bookmap.ctg.EciConnector;
import pt.dazz.bookmap.ctg.ibm.JavaGatewayConnector;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

/**
 * Creates a {@link CtgTransport} bean from the {@code bookmap.transport.ctg.*} properties, when
 * {@code bookmap.transport.type=ctg}.
 *
 * <p>The connector is an {@link EciConnector} bean. Without one, and with the IBM library
 * {@code ctgclient.jar} on the class path, this class creates a {@link JavaGatewayConnector}.
 */
@AutoConfiguration(before = BookmapAutoConfiguration.class)
@ConditionalOnClass(CtgTransport.class)
@ConditionalOnProperty(name = "bookmap.transport.type", havingValue = "ctg")
@EnableConfigurationProperties(CtgTransportProperties.class)
public class CtgTransportAutoConfiguration {

    private static final String PREFIX = "bookmap.transport.ctg.";

    /**
     * Creates the auto-configuration. Spring Boot calls this constructor.
     */
    public CtgTransportAutoConfiguration() {
        // No state.
    }

    /**
     * Builds the transport. Spring Boot calls {@code close()} of the transport when the context
     * closes.
     *
     * @param properties the {@code bookmap.transport.ctg.*} settings
     * @param connector the connector bean
     * @param customizers the customizer beans
     * @return the transport
     * @throws IllegalStateException if no connector exists, or if a setting is not valid
     */
    @Bean
    @ConditionalOnMissingBean(Transport.class)
    CtgTransport ctgTransport(CtgTransportProperties properties, ObjectProvider<EciConnector> connector,
                              ObjectProvider<CtgTransportBuilderCustomizer> customizers) {
        EciConnector eciConnector = connector.getIfAvailable();
        if (eciConnector == null) {
            throw new IllegalStateException(
                "No EciConnector bean. Add the IBM library ctgclient.jar, or declare an EciConnector bean.");
        }
        CtgTransport.Builder builder = CtgTransport.builder()
            .connector(eciConnector)
            .server(properties.server())
            .defaultTransaction(properties.defaultTransaction())
            .timeout(properties.timeout())
            .maxConnections(properties.maxConnections())
            .connectionWaitTimeout(properties.connectionWaitTimeout());
        if (properties.userid() != null) {
            builder.credentials(properties.userid(), properties.password());
        } else if (properties.password() != null) {
            throw new IllegalStateException(
                PREFIX + "password is set, but " + PREFIX + "userid is not set.");
        }
        Map<String, CtgRoute> routes = new LinkedHashMap<>();
        properties.routes().forEach((operation, route) -> {
            if (route.program() == null) {
                throw new IllegalStateException(PREFIX + "routes[" + operation + "].program is not set.");
            }
            routes.put(operation, new CtgRoute(route.program(), route.transaction()));
        });
        builder.routes(routes);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /**
     * Creates the connector of the IBM library, when the library is on the class path.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.ibm.ctg.client.JavaGateway")
    static class JavaGatewayConnectorConfiguration {

        /**
         * Builds the connector.
         *
         * @param properties the {@code bookmap.transport.ctg.*} settings
         * @return the connector
         * @throws IllegalStateException if the URL is not set
         */
        @Bean
        @ConditionalOnMissingBean(EciConnector.class)
        JavaGatewayConnector javaGatewayConnector(CtgTransportProperties properties) {
            Properties protocolProperties = new Properties();
            protocolProperties.putAll(properties.protocolProperties());
            JavaGatewayConnector.Builder builder = JavaGatewayConnector.builder()
                .port(properties.port())
                .socketConnectTimeout(properties.socketConnectTimeout())
                .protocolProperties(protocolProperties);
            if (properties.url() != null) {
                builder.url(properties.url());
            }
            return builder.build();
        }
    }
}
