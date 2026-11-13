package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FailureTranslatorTest {

    private static HttpHeaders contentType(String value) {
        return HttpHeaders.of(Map.of("Content-Type", List.of(value)), (a, b) -> true);
    }

    private static final HttpHeaders NONE = HttpHeaders.of(Map.of(), (a, b) -> true);

    @Test
    void targetWithoutAPort() {
        assertThat(FailureTranslator.target("GetInvoice", "PUT",
                URI.create("https://billing.example.com/invoices?x=1")))
            .isEqualTo("GetInvoice (PUT https://billing.example.com/invoices)");
    }

    @Test
    void targetWithAPortAndNoPath() {
        assertThat(FailureTranslator.target("GetInvoice", "POST",
                URI.create("http://user:secret@billing.example.com:8080")))
            .isEqualTo("GetInvoice (POST http://billing.example.com:8080)");
    }

    @Test
    void textExcerptIsLimitedTo200Characters() {
        byte[] body = "a".repeat(250).getBytes(StandardCharsets.UTF_8);

        assertThat(FailureTranslator.excerpt(contentType("text/plain"), body))
            .isEqualTo("a".repeat(200));
    }

    @Test
    void controlCharactersBecomeSpaces() {
        byte[] body = "a\u0001b\nc".getBytes(StandardCharsets.UTF_8);

        assertThat(FailureTranslator.excerpt(contentType("application/problem+xml"), body))
            .isEqualTo("a b c");
    }

    @Test
    void jsonContentTypeGivesText() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(FailureTranslator.excerpt(contentType("application/json; charset=UTF-8"), body))
            .isEqualTo("{}");
    }

    @Test
    void noContentTypeGivesHex() {
        assertThat(FailureTranslator.excerpt(NONE, new byte[] {0x0A, (byte) 0xFF}))
            .isEqualTo("hex 0AFF");
    }

    @Test
    void hexExcerptIsLimitedTo32Bytes() {
        byte[] body = new byte[40];

        assertThat(FailureTranslator.excerpt(contentType("application/octet-stream"), body))
            .isEqualTo("hex " + "00".repeat(32));
    }

    @Test
    void emptyBodyGivesNoExcerpt() {
        assertThat(FailureTranslator.excerpt(contentType("text/plain"), new byte[0])).isEmpty();
    }

    @Test
    void cleanReplacesUnicodeControlCharacters() {
        assertThat(FailureTranslator.clean("a\u0085b\u009Bc\u0007d")).isEqualTo("a b c d");
    }

    @Test
    void textExcerptReplacesUnicodeControlCharacters() {
        byte[] body = "a\u0085b".getBytes(StandardCharsets.UTF_8);

        assertThat(FailureTranslator.excerpt(contentType("text/plain"), body)).isEqualTo("a b");
    }
}
