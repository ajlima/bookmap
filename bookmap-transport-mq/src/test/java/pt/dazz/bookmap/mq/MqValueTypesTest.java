package pt.dazz.bookmap.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MqValueTypesTest {

    @Test
    void exceptionKeepsFailureAndCause() {
        IllegalStateException cause = new IllegalStateException("broken");
        MqException e = new MqException(MqFailure.NOT_AUTHORIZED, "send failed", cause);
        assertThat(e.failure()).isEqualTo(MqFailure.NOT_AUTHORIZED);
        assertThat(e).hasMessage("send failed").hasCause(cause);
    }

    @Test
    void exceptionRejectsNullFailure() {
        assertThatThrownBy(() -> new MqException(null, "send failed", null))
            .isInstanceOf(NullPointerException.class).hasMessage("failure");
    }

    @Test
    void requestRejectsNullValues() {
        byte[] payload = new byte[] {1};
        assertThatThrownBy(() -> new MqRequest(null, null, payload, null, Duration.ZERO, false))
            .isInstanceOf(NullPointerException.class).hasMessage("requestQueue");
        assertThatThrownBy(() -> new MqRequest("Q", null, null, null, Duration.ZERO, false))
            .isInstanceOf(NullPointerException.class).hasMessage("payload");
        assertThatThrownBy(() -> new MqRequest("Q", null, payload, null, null, false))
            .isInstanceOf(NullPointerException.class).hasMessage("timeToLive");
    }

    @Test
    void requestAllowsNullReplyQueueAndCorrelationId() {
        MqRequest request = new MqRequest("INVOICE.REQUEST", null, new byte[0], null,
            Duration.ofSeconds(30), true);
        assertThat(request.replyQueue()).isNull();
        assertThat(request.correlationId()).isNull();
        assertThat(request.persistent()).isTrue();
    }

    @Test
    void correlationModesAndFailures() {
        assertThat(MqCorrelation.values())
            .containsExactly(MqCorrelation.MESSAGE_ID, MqCorrelation.CORRELATION_ID);
        assertThat(MqFailure.values()).containsExactly(MqFailure.NOT_AUTHORIZED,
            MqFailure.INVALID_QUEUE, MqFailure.NOT_BYTES, MqFailure.OTHER);
    }
}
