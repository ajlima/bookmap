package pt.dazz.bookmap.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MqRouteTest {

    @Test
    void keepsQueues() {
        MqRoute route = new MqRoute("ORDERS.REQUEST", "ORDERS.REPLY");
        assertThat(route.requestQueue()).isEqualTo("ORDERS.REQUEST");
        assertThat(route.replyQueue()).isEqualTo("ORDERS.REPLY");
    }

    @Test
    void replyQueueCanBeNull() {
        assertThat(new MqRoute("ORDERS.REQUEST", null).replyQueue()).isNull();
    }

    @Test
    void nullRequestQueueThrowsNpe() {
        assertThatThrownBy(() -> new MqRoute(null, "ORDERS.REPLY"))
            .isInstanceOf(NullPointerException.class).hasMessage("requestQueue");
    }

    @Test
    void blankRequestQueueThrows() {
        assertThatThrownBy(() -> new MqRoute(" ", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankReplyQueueThrows() {
        assertThatThrownBy(() -> new MqRoute("ORDERS.REQUEST", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
