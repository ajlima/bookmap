package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpTransportBuilderTest {

    @Test
    void buildWithoutARouteFails() {
        assertThatThrownBy(() -> HttpTransport.builder().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("route");
    }

    @Test
    void relativeRouteWithoutABaseUriFails() {
        assertThatThrownBy(() -> HttpTransport.builder().route("GetInvoice", "invoices").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GetInvoice");
    }

    @Test
    void relativeRouteIsResolvedAgainstTheBaseUri() {
        try (HttpTransport transport = HttpTransport.builder()
                .baseUri("https://gateway.example.com/orders/")
                .route("GetInvoice", "invoices")
                .build()) {
            assertThat(transport.routes().get("GetInvoice").uri())
                .isEqualTo(URI.create("https://gateway.example.com/orders/invoices"));
        }
    }

    @Test
    void baseUriWithoutATrailingSlashReplacesTheLastSegment() {
        try (HttpTransport transport = HttpTransport.builder()
                .baseUri(URI.create("https://gateway.example.com/orders"))
                .route("GetInvoice", "invoices")
                .build()) {
            assertThat(transport.routes().get("GetInvoice").uri())
                .isEqualTo(URI.create("https://gateway.example.com/invoices"));
        }
    }

    @Test
    void absoluteRouteIgnoresTheBaseUriAndKeepsMethodAndHeaders() {
        HttpRoute route = new HttpRoute(URI.create("http://billing.example.com/x"), "PUT",
            Map.of("X-Route", "1"));
        try (HttpTransport transport = HttpTransport.builder()
                .baseUri("https://gateway.example.com/")
                .route("GetInvoice", route)
                .build()) {
            HttpRoute resolved = transport.routes().get("GetInvoice");
            assertThat(resolved.uri()).isEqualTo(URI.create("http://billing.example.com/x"));
            assertThat(resolved.method()).isEqualTo("PUT");
            assertThat(resolved.headers()).containsEntry("X-Route", "1");
        }
    }

    @Test
    void baseUriMustBeAbsoluteHttpOrHttps() {
        assertThatThrownBy(() -> HttpTransport.builder().baseUri("ftp://h/"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpTransport.builder().baseUri("relative/"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpTransport.builder().baseUri("http:opaque"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvedRouteMustBeHttpOrHttps() {
        assertThatThrownBy(() -> HttpTransport.builder().route("GetInvoice", "ftp://h/x").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GetInvoice");
    }

    @Test
    void httpClientAndConnectTimeoutTogetherFail() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThatThrownBy(() -> HttpTransport.builder()
                    .httpClient(client)
                    .connectTimeout(Duration.ofSeconds(1))
                    .route("GetInvoice", "http://h/x")
                    .build())
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void timeoutsMustBePositive() {
        HttpTransport.Builder builder = HttpTransport.builder();
        assertThatThrownBy(() -> builder.timeout(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.timeout(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.connectTimeout(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restrictedHeaderIsRejected() {
        assertThatThrownBy(() -> HttpTransport.builder().header("Host", "h"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        HttpTransport.Builder b = HttpTransport.builder();
        assertThatThrownBy(() -> b.baseUri((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.baseUri((URI) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route("A", (String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route("A", (HttpRoute) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.routes(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.header(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.basicAuth(null, "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.basicAuth("u", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.headerSupplier(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.abendHeader(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.timeout(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.connectTimeout(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.httpClient(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.sender(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void laterRouteReplacesEarlierRouteAndRoutesAddsAll() {
        try (HttpTransport transport = HttpTransport.builder()
                .route("GetInvoice", "http://h/first")
                .route("GetInvoice", "http://h/second")
                .routes(Map.of("GetOrder", HttpRoute.of("http://h/order"),
                    "GetShipment", HttpRoute.of("http://h/shipment")))
                .build()) {
            assertThat(transport.routes()).containsOnlyKeys("GetInvoice", "GetOrder", "GetShipment");
            assertThat(transport.routes().get("GetInvoice").uri())
                .isEqualTo(URI.create("http://h/second"));
        }
    }

    @Test
    void transportOwnsOnlyTheClientThatItCreates() {
        try (HttpTransport own = HttpTransport.builder().route("A", "http://h/a").build()) {
            assertThat(own.ownsClient()).isTrue();
        }
        try (HttpClient client = HttpClient.newHttpClient();
             HttpTransport caller = HttpTransport.builder()
                 .httpClient(client).route("A", "http://h/a").build()) {
            assertThat(caller.ownsClient()).isFalse();
        }
    }

    @Test
    void builderMessagesHaveNoUserInformationAndNoQuery() {
        assertThatThrownBy(() -> HttpTransport.builder().baseUri("ftp://u:secret@h/x?token=abc"))
            .hasMessageNotContaining("secret")
            .hasMessageNotContaining("token");
        assertThatThrownBy(() -> HttpTransport.builder()
                .route("GetInvoice", "ftp://u:secret@h/x?token=abc").build())
            .hasMessageNotContaining("secret")
            .hasMessageNotContaining("token");
        assertThatThrownBy(() -> HttpTransport.builder()
                .route("GetInvoice", "x?token=abc").build())
            .hasMessageNotContaining("token");
    }

    @Test
    void basicAuthUserWithAColonIsRejected() {
        assertThatThrownBy(() -> HttpTransport.builder().basicAuth("ORD:USER", "secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("ORD:USER")
            .hasMessageNotContaining("secret");
    }

    @Test
    void timeoutThatIsTooLargeIsRejected() {
        assertThatThrownBy(() -> HttpTransport.builder()
                .timeout(java.time.temporal.ChronoUnit.FOREVER.getDuration()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
