package pt.dazz.copymapper.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.dazz.copymapper.CopyMapperClient;
import pt.dazz.copymapper.spi.Transport;

/** Starts a real application, so that the check runs as Spring Boot registers it. */
class OldPropertiesStartupTest {

    static final AtomicBoolean CLIENT_USED = new AtomicBoolean();

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(CopyMapperAutoConfiguration.class)
    static class Application {

        @Bean
        Transport transport() {
            return request -> new byte[] {0};
        }

        @Bean
        Object warmUp(CopyMapperClient client) {
            CLIENT_USED.set(true);
            return client;
        }
    }

    private static Throwable start(String... properties) {
        SpringApplication application = new SpringApplication(Application.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(java.util.Arrays.stream(properties)
            .map(p -> p.split("=", 2))
            .collect(java.util.stream.Collectors.toMap(p -> p[0], p -> (Object) p[1])));
        Throwable failure = null;
        try {
            application.run().close();
        } catch (RuntimeException e) {
            failure = e;
        }
        return failure;
    }

    @Test
    void startsWithTheNewProperties() {
        assertThat(start("copymapper.charset=IBM500")).isNull();
    }

    @Test
    void failsWithLazyInitialization() {
        assertThat(start("bookmap.charset=IBM500", "spring.main.lazy-initialization=true"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Rename the properties bookmap.* to copymapper.*");
    }

    @Test
    void failsBeforeABeanUsesTheClient() {
        CLIENT_USED.set(false);
        assertThat(start("bookmap.charset=IBM500"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(CLIENT_USED).isFalse();
    }
}
