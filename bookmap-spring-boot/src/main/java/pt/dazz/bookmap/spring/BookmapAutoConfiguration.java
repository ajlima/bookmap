package pt.dazz.bookmap.spring;

import java.nio.charset.Charset;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.spi.Transport;

/**
 * Creates a {@link BookmapClient} bean from a {@link Transport} bean and the {@code bookmap.*} settings.
 *
 * <p>The {@code BookmapClient} bean exists only when a {@code Transport} bean exists.
 * A transport auto-configuration must declare
 * {@code @AutoConfiguration(before = BookmapAutoConfiguration.class)}.
 * Then the {@code Transport} bean is known when this class runs.
 *
 * <p>If {@code bookmap.transport.type} is set and no {@code Transport} bean exists, the
 * application does not start.
 */
@AutoConfiguration
@EnableConfigurationProperties({BookmapProperties.class, BookmapTransportProperties.class})
public class BookmapAutoConfiguration {

    /**
     * Creates the auto-configuration. Spring Boot calls this constructor.
     */
    public BookmapAutoConfiguration() {
        // No state.
    }

    /**
     * Loads the generated codecs with the service loader.
     *
     * @return the codec registry
     */
    @Bean
    @ConditionalOnMissingBean
    BookCodecRegistry bookCodecRegistry() {
        return BookCodecRegistry.loadDefault();
    }

    /**
     * Builds the client. The context fails to start if the charset is not valid.
     *
     * @param transport the transport bean
     * @param registry the codec registry bean
     * @param properties the {@code bookmap.*} settings
     * @return the client
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Transport.class)
    BookmapClient bookmapClient(Transport transport, BookCodecRegistry registry,
                                BookmapProperties properties) {
        return BookmapClient.builder()
            .transport(transport)
            .charset(Charset.forName(properties.charset()))
            .maxCalls(properties.maxCalls())
            .codecRegistry(registry)
            .build();
    }

    /**
     * Stops the application when {@code bookmap.transport.type} is set and no transport
     * auto-configuration created a {@code Transport} bean. The bean is not lazy. Thus, the check
     * also runs with lazy initialization.
     *
     * @param properties the {@code bookmap.transport.*} settings
     * @return never
     * @throws IllegalStateException always
     */
    @Bean
    @Lazy(false)
    @ConditionalOnProperty("bookmap.transport.type")
    @ConditionalOnMissingBean(Transport.class)
    MissingTransport bookmapMissingTransport(BookmapTransportProperties properties) {
        throw new IllegalStateException(properties.type().missingTransportMessage());
    }

    /** The type of the bean of the missing-transport check. No instance exists. */
    interface MissingTransport {
    }
}
