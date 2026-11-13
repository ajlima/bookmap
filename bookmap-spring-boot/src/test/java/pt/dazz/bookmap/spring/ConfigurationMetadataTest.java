package pt.dazz.bookmap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
        assertThat(properties()).containsOnlyKeys("bookmap.charset", "bookmap.max-calls");
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
}
