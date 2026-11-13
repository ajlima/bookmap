package pt.dazz.bookmap.spring.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.SSLParameters;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.context.annotation.Bean;
import pt.dazz.bookmap.http.HttpRoute;
import pt.dazz.bookmap.http.HttpTransport;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

/**
 * Creates an {@link HttpTransport} bean from the {@code bookmap.transport.http.*} properties, when
 * {@code bookmap.transport.type=http}.
 *
 * <p>With {@code bookmap.transport.http.ssl.bundle}, the transport uses an HTTP client with the
 * {@code SSLContext} of that Spring Boot SSL bundle.
 */
@AutoConfiguration(before = BookmapAutoConfiguration.class)
@ConditionalOnClass(HttpTransport.class)
@ConditionalOnProperty(name = "bookmap.transport.type", havingValue = "http")
@EnableConfigurationProperties(HttpTransportProperties.class)
public class HttpTransportAutoConfiguration {

    private static final String PREFIX = "bookmap.transport.http.";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Creates the auto-configuration. Spring Boot calls this constructor.
     */
    public HttpTransportAutoConfiguration() {
        // No state.
    }

    /**
     * Creates the HTTP client of the SSL bundle. The transport does not close a client that it
     * gets. Thus, this bean calls {@code shutdown()} of the client when the context closes.
     *
     * @param properties the {@code bookmap.transport.http.*} settings
     * @param sslBundles the SSL bundles of Spring Boot
     * @return the holder of the client
     */
    @Bean
    @ConditionalOnProperty(PREFIX + "ssl.bundle")
    @ConditionalOnMissingBean(Transport.class)
    SslBundleHttpClient bookmapSslBundleHttpClient(HttpTransportProperties properties,
                                                   ObjectProvider<SslBundles> sslBundles) {
        SslBundle bundle = sslBundles.getObject().getBundle(properties.ssl().bundle());
        SSLParameters parameters = new SSLParameters();
        SslOptions options = bundle.getOptions();
        if (options.getCiphers() != null) {
            parameters.setCipherSuites(options.getCiphers());
        }
        if (options.getEnabledProtocols() != null) {
            parameters.setProtocols(options.getEnabledProtocols());
        }
        Duration connectTimeout = properties.connectTimeout() == null
            ? DEFAULT_CONNECT_TIMEOUT : properties.connectTimeout();
        return new SslBundleHttpClient(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(connectTimeout)
            .sslContext(bundle.createSslContext())
            .sslParameters(parameters)
            .build());
    }

    /**
     * Builds the transport. Spring Boot calls {@code close()} of the transport when the context
     * closes.
     *
     * @param properties the {@code bookmap.transport.http.*} settings
     * @param sslClient the HTTP client of the SSL bundle, if it exists
     * @param customizers the customizer beans
     * @return the transport
     * @throws IllegalStateException if a setting is not valid
     */
    @Bean
    @ConditionalOnMissingBean(Transport.class)
    HttpTransport httpTransport(HttpTransportProperties properties,
                                ObjectProvider<SslBundleHttpClient> sslClient,
                                ObjectProvider<HttpTransportBuilderCustomizer> customizers) {
        HttpTransport.Builder builder = HttpTransport.builder().timeout(properties.timeout());
        if (properties.baseUri() != null) {
            builder.baseUri(properties.baseUri());
        }
        properties.headers().forEach(builder::header);
        if (properties.username() != null || properties.password() != null) {
            if (properties.username() == null) {
                throw new IllegalStateException(
                    PREFIX + "password is set, but " + PREFIX + "username is not set.");
            }
            if (properties.password() == null) {
                throw new IllegalStateException(
                    PREFIX + "password is not set, but " + PREFIX + "username is set.");
            }
            builder.basicAuth(properties.username(), properties.password());
        }
        if (properties.abendHeader() != null) {
            builder.abendHeader(properties.abendHeader());
        }
        SslBundleHttpClient client = sslClient.getIfAvailable();
        if (client != null) {
            builder.httpClient(client.client());
        } else if (properties.connectTimeout() != null) {
            builder.connectTimeout(properties.connectTimeout());
        }
        Map<String, HttpRoute> routes = new LinkedHashMap<>();
        properties.routes().forEach((operation, route) -> {
            if (route.uri() == null) {
                throw new IllegalStateException(PREFIX + "routes[" + operation + "].uri is not set.");
            }
            routes.put(operation, new HttpRoute(URI.create(route.uri()), route.method(), route.headers()));
        });
        builder.routes(routes);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /**
     * The HTTP client of the SSL bundle. It is not an {@code HttpClient} bean, so that an
     * {@code HttpClient} bean of the application stays the only one of its type.
     *
     * @param client the client
     */
    record SslBundleHttpClient(HttpClient client) implements DisposableBean {

        /**
         * Stops the client. A request that runs continues until it completes or until its timeout.
         * The method does not call {@code close()}, because {@code close()} can wait a long time.
         */
        @Override
        public void destroy() {
            client.shutdown();
        }
    }
}
