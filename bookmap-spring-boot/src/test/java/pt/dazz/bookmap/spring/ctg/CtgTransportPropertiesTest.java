package pt.dazz.bookmap.spring.ctg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CtgTransportPropertiesTest {

    private static CtgTransportProperties withPassword(String password) {
        return new CtgTransportProperties(null, 2006, Duration.ofSeconds(10), null, null, null,
            "ORDUSER", password, Duration.ofSeconds(30), 10, Duration.ofSeconds(30), null);
    }

    @Test
    void toStringHidesThePassword() {
        assertThat(withPassword("s3cr3t").toString())
            .contains("password=******")
            .contains("userid=ORDUSER")
            .doesNotContain("s3cr3t");
    }

    @Test
    void toStringShowsAMissingPassword() {
        assertThat(withPassword(null).toString()).contains("password=null");
    }

    @Test
    void mapsAreEmptyWhenNotSet() {
        CtgTransportProperties properties = withPassword(null);
        assertThat(properties.routes()).isEmpty();
        assertThat(properties.protocolProperties()).isEmpty();
    }

    @Test
    void toStringHidesTheProtocolPropertyValues() {
        CtgTransportProperties properties = new CtgTransportProperties(null, 2006, Duration.ofSeconds(10),
            Map.of("ssl.keyringpw", "ringsecret"), null, null, null, null, Duration.ofSeconds(30), 10,
            Duration.ofSeconds(30), null);
        assertThat(properties.toString())
            .contains("protocolProperties=[ssl.keyringpw]")
            .doesNotContain("ringsecret");
    }
}
