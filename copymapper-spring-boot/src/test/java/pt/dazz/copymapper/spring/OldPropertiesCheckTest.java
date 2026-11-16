package pt.dazz.copymapper.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class OldPropertiesCheckTest {

    private final StandardEnvironment environment = new StandardEnvironment();

    private OldPropertiesCheckTest properties(String... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            map.put(parts[0], parts[1]);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("test", map));
        return this;
    }

    private OldPropertiesCheckTest variables(Map<String, Object> variables) {
        environment.getPropertySources().replace("systemEnvironment",
            new SystemEnvironmentPropertySource("systemEnvironment", variables));
        return this;
    }

    private void check() {
        new OldPropertiesCheck().postProcessEnvironment(environment, new SpringApplication());
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertFailure() {
        return assertThatThrownBy(this::check).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passesWithoutOldKeys() {
        properties("copymapper.charset=IBM500");
        assertThatCode(this::check).doesNotThrowAnyException();
    }

    @Test
    void failsForOldCharset() {
        properties("bookmap.charset=IBM500");
        assertFailure()
            .hasMessageContaining("bookmap.charset")
            .hasMessageContaining("Rename the properties bookmap.* to copymapper.*")
            .hasMessageContaining("https://github.com/dazz-pt/copymapper/wiki/Migrate-to-CopyMapper")
            .message().doesNotContain("IBM500");
    }

    @Test
    void failsForOldMaxCallsInCamelCase() {
        properties("bookmap.maxCalls=5");
        assertFailure().hasMessageContaining("bookmap.maxcalls");
    }

    @Test
    void failsForOldMaxCallsInKebabCase() {
        properties("bookmap.max-calls=5");
        assertFailure().hasMessageContaining("bookmap.max-calls");
    }

    @Test
    void failsForOldTransportEnvironmentVariableWithoutTheValue() {
        variables(Map.of("BOOKMAP_TRANSPORT_CTG_PASSWORD", "secret"));
        assertFailure()
            .hasMessageContaining("bookmap.transport.ctg.password")
            .hasMessageContaining("An environment variable has the same name in upper case")
            .message().doesNotContain("secret");
    }

    @Test
    void listsAllOldKeysInOrder() {
        properties("bookmap.transport.type=ctg", "bookmap.charset=IBM037");
        assertFailure().hasMessageContaining("bookmap.charset, bookmap.transport.type");
    }

    @Test
    void ignoresOtherBookmapKeys() {
        properties("bookmap.api.key=x", "bookmap.transportation=y");
        variables(Map.of("BOOKMAP_HOME", "/opt/other"));
        assertThatCode(this::check).doesNotThrowAnyException();
    }

    @Test
    void skipsSourcesThatCannotListTheirNames() {
        environment.getPropertySources().addFirst(new PropertySource<Object>("not enumerable") {
            @Override
            public Object getProperty(String name) {
                return "bookmap.charset".equals(name) ? "IBM500" : null;
            }
        });
        assertThatCode(this::check).doesNotThrowAnyException();
        assertThat(OldPropertiesCheck.oldKeys(environment)).isEmpty();
    }
}
