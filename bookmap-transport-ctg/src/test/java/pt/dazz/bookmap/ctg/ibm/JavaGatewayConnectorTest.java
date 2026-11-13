package pt.dazz.bookmap.ctg.ibm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.ctg.EciCall;
import pt.dazz.bookmap.ctg.EciConnection;
import pt.dazz.bookmap.ctg.EciReply;

class JavaGatewayConnectorTest {

    @BeforeEach
    @AfterEach
    void reset() {
        JavaGateway.reset();
    }

    private static JavaGatewayConnector connector() {
        return JavaGatewayConnector.builder().url("tcp://ctg.example.com").build();
    }

    private static EciCall orderCall(byte[] payload) {
        return new EciCall("CICSPROD", "ORDUSER", "secret", "PGMORD01", "TX01", payload, 300, 45);
    }

    @Test
    void opensGatewayWithSettings() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("a", "b");
        JavaGatewayConnector connector = JavaGatewayConnector.builder()
            .url("tcp://ctg.example.com")
            .port(2007)
            .socketConnectTimeout(Duration.ofSeconds(5))
            .protocolProperties(properties)
            .build();
        properties.setProperty("c", "d");
        connector.open();
        JavaGateway gateway = JavaGateway.last;
        assertThat(gateway.url()).isEqualTo("tcp://ctg.example.com");
        assertThat(gateway.port()).isEqualTo(2007);
        assertThat(gateway.socketConnectTimeout()).isEqualTo(5000);
        assertThat(gateway.protocolProperties()).containsOnlyKeys("a");
    }

    @Test
    void defaults() throws IOException {
        connector().open();
        JavaGateway gateway = JavaGateway.last;
        assertThat(gateway.port()).isEqualTo(2006);
        assertThat(gateway.socketConnectTimeout()).isEqualTo(10_000);
        assertThat(gateway.protocolProperties()).isEmpty();
    }

    @Test
    void buildWithoutUrlFails() {
        assertThatIllegalStateException().isThrownBy(() -> JavaGatewayConnector.builder().build())
            .withMessageContaining("url");
    }

    @Test
    void rejectsNullValues() {
        JavaGatewayConnector.Builder builder = JavaGatewayConnector.builder();
        assertThatNullPointerException().isThrownBy(() -> builder.url(null));
        assertThatNullPointerException().isThrownBy(() -> builder.socketConnectTimeout(null));
        assertThatNullPointerException().isThrownBy(() -> builder.protocolProperties(null));
    }

    @Test
    void openFailurePropagates() {
        IOException failure = new IOException("refused");
        JavaGateway.openFailure = failure;
        assertThatThrownBy(() -> connector().open()).isSameAs(failure);
    }

    @Test
    void callBuildsSyncRequest() throws IOException {
        byte[] payload = new byte[20];
        payload[0] = 1;
        payload[19] = 2;
        connector().open().call(orderCall(payload));
        ECIRequest request = JavaGateway.last.lastRequest();
        assertThat(request.Call_Type).isEqualTo(ECIRequest.ECI_SYNC).isEqualTo(1);
        assertThat(request.Server).isEqualTo("CICSPROD");
        assertThat(request.Userid).isEqualTo("ORDUSER");
        assertThat(request.Password).isEqualTo("secret");
        assertThat(request.Program).isEqualTo("PGMORD01");
        assertThat(request.Transid).isEqualTo("TX01");
        assertThat(request.Commarea_Length).isEqualTo(300);
        assertThat(request.Extend_Mode).isEqualTo(ECIRequest.ECI_NO_EXTEND).isZero();
        assertThat(request.Luw_Token).isZero();
        assertThat(request.eciTimeout()).isEqualTo((short) 45);
        assertThat(request.outboundLength()).isEqualTo(20);
        assertThat(request.inboundLength()).isEqualTo(300);
        assertThat(request.Commarea).hasSize(300);
        assertThat(request.Commarea[0]).isEqualTo((byte) 1);
        assertThat(request.Commarea[19]).isEqualTo((byte) 2);
        assertThat(request.Commarea[20]).isZero();
    }

    @Test
    void callReturnsReply() throws IOException {
        JavaGateway.onFlow = request -> {
            request.reply(-7, "ASRA");
            request.Commarea[0] = 9;
        };
        EciReply reply = connector().open().call(orderCall(new byte[20]));
        assertThat(reply.returnCode()).isEqualTo(-7);
        assertThat(reply.abendCode()).isEqualTo("ASRA");
        assertThat(reply.commarea()).hasSize(300).startsWith(9);
    }

    @Test
    void flowFailurePropagates() throws IOException {
        EciConnection connection = connector().open();
        IOException failure = new IOException("reset");
        JavaGateway.flowFailure = failure;
        assertThatThrownBy(() -> connection.call(orderCall(new byte[1]))).isSameAs(failure);
    }

    @Test
    void closeClosesGateway() throws IOException {
        connector().open().close();
        assertThat(JavaGateway.last.closed()).isTrue();
    }

    @Test
    void closeFailurePropagates() throws IOException {
        EciConnection connection = connector().open();
        IOException failure = new IOException("broken");
        JavaGateway.closeFailure = failure;
        assertThatThrownBy(connection::close).isSameAs(failure);
    }

    @Test
    void keepsPropertiesFromDefaults() throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("keyring", "/etc/ctg/client.p12");
        Properties properties = new Properties(defaults);
        properties.setProperty("a", "b");
        JavaGatewayConnector.builder().url("ssl://ctg.example.com").protocolProperties(properties)
            .build().open();
        assertThat(JavaGateway.last.protocolProperties().getProperty("keyring"))
            .isEqualTo("/etc/ctg/client.p12");
        assertThat(JavaGateway.last.protocolProperties().getProperty("a")).isEqualTo("b");
    }
}
