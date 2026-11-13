package pt.dazz.bookmap.spring.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpTransportPropertiesTest {

    private static HttpTransportProperties of(String password, Map<String, String> headers,
                                              Map<String, HttpTransportProperties.Route> routes) {
        return new HttpTransportProperties(null, headers, "ORDUSER", password, null,
            Duration.ofSeconds(30), null, null, routes);
    }

    @Test
    void toStringHidesThePasswordAndTheHeaderValues() {
        HttpTransportProperties properties = of("s3cr3t", Map.of("Authorization", "Bearer tok3n"),
            Map.of("GetInvoice", new HttpTransportProperties.Route("invoices", "PUT",
                Map.of("X-Api-Key", "k3y"))));
        assertThat(properties.toString())
            .contains("password=******", "headers=[Authorization]", "X-Api-Key", "invoices")
            .doesNotContain("s3cr3t", "tok3n", "k3y");
    }

    @Test
    void toStringShowsAMissingPassword() {
        assertThat(of(null, null, null).toString()).contains("password=null");
    }

    @Test
    void missingValuesAreEmpty() {
        HttpTransportProperties properties = of(null, null, null);
        assertThat(properties.headers()).isEmpty();
        assertThat(properties.routes()).isEmpty();
        assertThat(properties.ssl().bundle()).isNull();
        assertThat(new HttpTransportProperties.Route("x", null, null).headers()).isEmpty();
    }
}
