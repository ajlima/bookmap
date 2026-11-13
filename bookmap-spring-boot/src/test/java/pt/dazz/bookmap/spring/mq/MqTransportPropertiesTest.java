package pt.dazz.bookmap.spring.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.mq.MqCorrelation;

class MqTransportPropertiesTest {

    private static MqTransportProperties withPassword(String password) {
        return new MqTransportProperties("orders", password, true, null, MqCorrelation.MESSAGE_ID,
            Duration.ofSeconds(30), false, 10, Duration.ofSeconds(30), null);
    }

    @Test
    void toStringHidesThePassword() {
        assertThat(withPassword("s3cr3t").toString())
            .contains("password=******", "user=orders")
            .doesNotContain("s3cr3t");
    }

    @Test
    void toStringShowsAMissingPassword() {
        assertThat(withPassword(null).toString()).contains("password=null");
    }

    @Test
    void routesAreEmptyWhenNotSet() {
        assertThat(withPassword(null).routes()).isEmpty();
    }
}
