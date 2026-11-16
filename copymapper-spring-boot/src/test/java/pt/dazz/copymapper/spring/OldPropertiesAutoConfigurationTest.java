package pt.dazz.copymapper.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import pt.dazz.copymapper.spi.Transport;

class OldPropertiesAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OldPropertiesAutoConfiguration.class,
            CopyMapperAutoConfiguration.class));

    private static ApplicationContextRunner withEnvironmentVariables(
            ApplicationContextRunner runner, Map<String, Object> variables) {
        return runner.withInitializer(context -> context.getEnvironment().getPropertySources()
            .addFirst(new SystemEnvironmentPropertySource("systemEnvironment", variables)));
    }

    private static String failureMessage(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        Throwable cause = context.getStartupFailure();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertThat(cause).isInstanceOf(IllegalStateException.class);
        return cause.getMessage();
    }

    @Test
    void startsWithoutOldKeys() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsForOldCharset() {
        runner.withPropertyValues("bookmap.charset=IBM500").run(context -> {
            String message = failureMessage(context);
            assertThat(message)
                .contains("bookmap.charset")
                .contains("Rename the properties bookmap.* to copymapper.*")
                .contains("https://github.com/dazz-pt/copymapper/wiki/Migrate-to-CopyMapper")
                .doesNotContain("IBM500");
        });
    }

    @Test
    void failsForOldMaxCallsInCamelCase() {
        runner.withPropertyValues("bookmap.maxCalls=5").run(context ->
            assertThat(failureMessage(context)).contains("bookmap.maxcalls"));
    }

    @Test
    void failsForOldMaxCallsInKebabCase() {
        runner.withPropertyValues("bookmap.max-calls=5").run(context ->
            assertThat(failureMessage(context)).contains("bookmap.max-calls"));
    }

    @Test
    void failsForOldTransportEnvironmentVariableWithoutTheValue() {
        withEnvironmentVariables(runner, Map.of("BOOKMAP_TRANSPORT_CTG_PASSWORD", "secret"))
            .run(context -> assertThat(failureMessage(context))
                .contains("bookmap.transport.ctg.password")
                .doesNotContain("secret"));
    }

    @Test
    void listsAllOldKeysInOrder() {
        runner.withPropertyValues("bookmap.transport.type=ctg", "bookmap.charset=IBM037")
            .run(context -> assertThat(failureMessage(context))
                .contains("bookmap.charset, bookmap.transport.type"));
    }

    @Test
    void ignoresOtherBookmapKeys() {
        withEnvironmentVariables(runner.withPropertyValues("bookmap.api.key=x",
            "bookmap.transportation=y"), Map.of("BOOKMAP_HOME", "/opt/other"))
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void skipsSourcesThatCannotListTheirNames() {
        PropertySource<Object> source = new PropertySource<>("not enumerable") {
            @Override
            public Object getProperty(String name) {
                return "bookmap.charset".equals(name) ? "IBM500" : null;
            }
        };
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
            .addFirst(source)).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsAlsoWithATransportBean() {
        runner.withBean(Transport.class, () -> request -> new byte[] {0})
            .withPropertyValues("bookmap.charset=IBM037")
            .run(context -> assertThat(failureMessage(context)).contains("bookmap.charset"));
    }
}
