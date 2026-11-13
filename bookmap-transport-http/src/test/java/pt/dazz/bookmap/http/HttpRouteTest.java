package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpRouteTest {

    @Test
    void ofUriUsesPostAndNoHeaders() {
        HttpRoute route = HttpRoute.of("https://gateway.example.com/orders");

        assertThat(route.uri()).isEqualTo(URI.create("https://gateway.example.com/orders"));
        assertThat(route.method()).isEqualTo("POST");
        assertThat(route.headers()).isEmpty();
    }

    @Test
    void ofUriAndMethodKeepsARelativeUri() {
        HttpRoute route = HttpRoute.of("invoices", "PUT");

        assertThat(route.uri()).isEqualTo(URI.create("invoices"));
        assertThat(route.uri().isAbsolute()).isFalse();
        assertThat(route.method()).isEqualTo("PUT");
    }

    @Test
    void nullUriIsRejected() {
        assertThatThrownBy(() -> new HttpRoute(null, "POST", Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void methodThatIsNotATokenIsRejected() {
        assertThatThrownBy(() -> HttpRoute.of("x", "BAD METHOD"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incorrectUriIsRejected() {
        assertThatThrownBy(() -> HttpRoute.of("not a uri"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headersAreAnUnmodifiableCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Client-Id", "order-service");
        HttpRoute route = new HttpRoute(URI.create("x"), null, source);
        source.put("X-Other", "value");

        assertThat(route.headers()).containsExactly(Map.entry("X-Client-Id", "order-service"));
        assertThatThrownBy(() -> route.headers().put("A", "b"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restrictedRouteHeaderIsRejected() {
        assertThatThrownBy(() -> new HttpRoute(URI.create("x"), null, Map.of("Host", "h")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
