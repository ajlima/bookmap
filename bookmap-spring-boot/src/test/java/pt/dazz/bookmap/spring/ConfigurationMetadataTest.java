package pt.dazz.bookmap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.boot.configurationmetadata.ValueHint;
import org.springframework.boot.configurationmetadata.ValueProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.spi.Transport;

/**
 * Checks the configuration metadata that the Spring Boot configuration processor generates.
 * The test reads the file from the classpath, with the same library that an IDE uses.
 */
class ConfigurationMetadataTest {

    /**
     * Reads the metadata file of this module. Other JARs on the classpath, for example
     * spring-boot-autoconfigure, contain a file with the same name. Thus, the test finds the file
     * next to the compiled {@code BookmapProperties} class.
     */
    private static Map<String, ConfigurationMetadataProperty> properties() throws IOException {
        URL classes = BookmapProperties.class.getProtectionDomain().getCodeSource().getLocation();
        URL metadata = URI.create(classes + "META-INF/spring-configuration-metadata.json").toURL();
        try (InputStream in = metadata.openStream()) {
            return ConfigurationMetadataRepositoryJsonBuilder.create(in).build().getAllProperties();
        }
    }

    @Test
    void listsOnlyTheBookmapProperties() throws IOException {
        assertThat(properties()).containsOnlyKeys("bookmap.charset", "bookmap.max-calls",
            "bookmap.transport.type",
            "bookmap.transport.ctg.url", "bookmap.transport.ctg.port",
            "bookmap.transport.ctg.socket-connect-timeout", "bookmap.transport.ctg.protocol-properties",
            "bookmap.transport.ctg.server", "bookmap.transport.ctg.default-transaction",
            "bookmap.transport.ctg.userid", "bookmap.transport.ctg.password",
            "bookmap.transport.ctg.timeout", "bookmap.transport.ctg.max-connections",
            "bookmap.transport.ctg.connection-wait-timeout", "bookmap.transport.ctg.routes",
            "bookmap.transport.http.base-uri", "bookmap.transport.http.headers",
            "bookmap.transport.http.username", "bookmap.transport.http.password",
            "bookmap.transport.http.abend-header", "bookmap.transport.http.timeout",
            "bookmap.transport.http.connect-timeout", "bookmap.transport.http.ssl.bundle",
            "bookmap.transport.http.routes",
            "bookmap.transport.mq.user", "bookmap.transport.mq.password",
            "bookmap.transport.mq.mq-native-messages", "bookmap.transport.mq.default-reply-queue",
            "bookmap.transport.mq.correlation", "bookmap.transport.mq.timeout",
            "bookmap.transport.mq.persistent", "bookmap.transport.mq.max-sessions",
            "bookmap.transport.mq.session-wait-timeout", "bookmap.transport.mq.routes");
    }

    @Test
    void charsetHasTypeDescriptionAndDefault() throws IOException {
        ConfigurationMetadataProperty charset = properties().get("bookmap.charset");
        assertThat(charset.getType()).isEqualTo("java.lang.String");
        assertThat(charset.getDefaultValue()).isEqualTo("IBM037");
        assertThat(charset.getDescription()).startsWith("The default charset of the client.");
    }

    @Test
    void maxCallsHasTypeDescriptionAndDefault() throws IOException {
        ConfigurationMetadataProperty maxCalls = properties().get("bookmap.max-calls");
        assertThat(maxCalls.getType()).isEqualTo("java.lang.Integer");
        assertThat(maxCalls.getDefaultValue()).isEqualTo(1000);
        assertThat(maxCalls.getDescription()).startsWith("The maximum number of calls");
    }

    @Test
    void eachCharsetHintIsAValidClientCharset() throws IOException {
        List<String> values = properties().get("bookmap.charset").getHints().getValueHints().stream()
            .map(hint -> (String) hint.getValue())
            .toList();
        assertThat(values).startsWith("IBM037").contains("IBM500");

        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BookmapAutoConfiguration.class))
            .withBean(Transport.class, () -> request -> new byte[0]);
        for (String value : values) {
            runner.withPropertyValues("bookmap.charset=" + value)
                .run(context -> assertThat(context).as(value).hasNotFailed()
                    .hasSingleBean(BookmapClient.class));
        }
    }

    @Test
    void charsetHintAcceptsAnyOtherValue() throws IOException {
        assertThat(properties().get("bookmap.charset").getHints().getValueProviders())
            .extracting(ValueProvider::getName)
            .containsExactly("any");
    }

    @Test
    void eachCharsetHintHasADescription() throws IOException {
        assertThat(properties().get("bookmap.charset").getHints().getValueHints())
            .extracting(ValueHint::getDescription)
            .allSatisfy(description -> assertThat(description).isNotBlank());
    }

    @Test
    void transportTypeHasTypeDescriptionAndHints() throws IOException {
        ConfigurationMetadataProperty type = properties().get("bookmap.transport.type");
        assertThat(type.getType()).isEqualTo("pt.dazz.bookmap.spring.TransportType");
        assertThat(type.getDescription()).startsWith("The transport that");
        assertThat(type.getHints().getValueHints())
            .extracting(ValueHint::getValue)
            .containsExactly("ctg", "mq", "http");
        assertThat(type.getHints().getValueHints())
            .extracting(ValueHint::getDescription)
            .allSatisfy(description -> assertThat(description).isNotBlank());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "port|java.lang.Integer|2006",
        "socket-connect-timeout|java.time.Duration|10s",
        "timeout|java.time.Duration|30s",
        "max-connections|java.lang.Integer|10",
        "connection-wait-timeout|java.time.Duration|30s"})
    void ctgPropertyHasTypeAndDefault(String name, String type, String defaultValue) throws IOException {
        ConfigurationMetadataProperty property = properties().get("bookmap.transport.ctg." + name);
        assertThat(property.getType()).isEqualTo(type);
        assertThat(String.valueOf(property.getDefaultValue())).isEqualTo(defaultValue);
        assertThat(property.getDescription()).isNotBlank();
    }

    @Test
    void ctgRoutesAndPasswordHaveDescriptions() throws IOException {
        Map<String, ConfigurationMetadataProperty> properties = properties();
        assertThat(properties.get("bookmap.transport.ctg.routes").getType())
            .isEqualTo("java.util.Map<java.lang.String,pt.dazz.bookmap.spring.ctg.CtgTransportProperties$Route>");
        assertThat(properties.get("bookmap.transport.ctg.routes").getDescription())
            .startsWith("The route of each operation.");
        assertThat(properties.get("bookmap.transport.ctg.password").getDescription())
            .contains("environment variable");
    }

    @Test
    void httpPropertiesHaveTypesDefaultsAndDescriptions() throws IOException {
        Map<String, ConfigurationMetadataProperty> properties = properties();
        ConfigurationMetadataProperty timeout = properties.get("bookmap.transport.http.timeout");
        assertThat(timeout.getType()).isEqualTo("java.time.Duration");
        assertThat(timeout.getDefaultValue()).isEqualTo("30s");
        assertThat(properties.get("bookmap.transport.http.connect-timeout").getDefaultValue()).isNull();
        assertThat(properties.get("bookmap.transport.http.base-uri").getType()).isEqualTo("java.net.URI");
        assertThat(properties.get("bookmap.transport.http.ssl.bundle").getDescription())
            .startsWith("The name of a Spring Boot SSL bundle");
        assertThat(properties.get("bookmap.transport.http.password").getDescription())
            .contains("environment variable");
        assertThat(properties.get("bookmap.transport.http.routes").getDescription())
            .startsWith("The route of each operation.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "mq-native-messages|java.lang.Boolean|true",
        "correlation|pt.dazz.bookmap.mq.MqCorrelation|message-id",
        "timeout|java.time.Duration|30s",
        "persistent|java.lang.Boolean|false",
        "max-sessions|java.lang.Integer|10",
        "session-wait-timeout|java.time.Duration|30s"})
    void mqPropertyHasTypeAndDefault(String name, String type, String defaultValue) throws IOException {
        ConfigurationMetadataProperty property = properties().get("bookmap.transport.mq." + name);
        assertThat(property.getType()).isEqualTo(type);
        assertThat(String.valueOf(property.getDefaultValue())).isEqualTo(defaultValue);
        assertThat(property.getDescription()).isNotBlank();
    }

    @Test
    void mqPasswordHasADescription() throws IOException {
        assertThat(properties().get("bookmap.transport.mq.password").getDescription())
            .contains("environment variable");
    }
}
