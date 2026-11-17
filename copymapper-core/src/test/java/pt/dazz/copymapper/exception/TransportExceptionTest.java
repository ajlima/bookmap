package pt.dazz.copymapper.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class TransportExceptionTest {

    private final Throwable cause = new IllegalStateException("cause");

    @Test
    void existingConstructorsSayPossiblySent() {
        assertThat(new TransportException("m").requestSent()).isTrue();
        assertThat(new TransportException("m", cause).requestSent()).isTrue();
        assertThat(new ConnectionException("m").requestSent()).isTrue();
        assertThat(new ConnectionException("m", cause).requestSent()).isTrue();
        assertThat(new OperationNotFoundException("m").requestSent()).isTrue();
        assertThat(new OperationNotFoundException("m", cause).requestSent()).isTrue();
        assertThat(new TransportTimeoutException("m").requestSent()).isTrue();
        assertThat(new TransportTimeoutException("m", cause).requestSent()).isTrue();
        assertThat(new RemoteAbendException("ASRA", "m").requestSent()).isTrue();
        assertThat(new RemoteAbendException("ASRA", "m", cause).requestSent()).isTrue();
    }

    @Test
    void newConstructorsKeepTheValue() {
        for (boolean sent : new boolean[] {false, true}) {
            assertThat(new TransportException("m", sent).requestSent()).isEqualTo(sent);
            assertThat(new TransportException("m", cause, sent).requestSent()).isEqualTo(sent);
            assertThat(new ConnectionException("m", sent).requestSent()).isEqualTo(sent);
            assertThat(new ConnectionException("m", cause, sent).requestSent()).isEqualTo(sent);
            assertThat(new OperationNotFoundException("m", sent).requestSent()).isEqualTo(sent);
        }
    }

    @Test
    void newConstructorsKeepMessageAndCause() {
        assertThat(new TransportException("m", cause, false))
            .hasMessage("m").hasCause(cause);
        assertThat(new ConnectionException("m", cause, false))
            .hasMessage("m").hasCause(cause);
        assertThat(new TransportException("m", false)).hasMessage("m").hasNoCause();
        assertThat(new ConnectionException("m", false)).hasMessage("m").hasNoCause();
        assertThat(new OperationNotFoundException("m", false)).hasMessage("m").hasNoCause();
    }

    @Test
    void serializationKeepsTheValue() throws Exception {
        for (boolean sent : new boolean[] {false, true}) {
            TransportException copy = roundTrip(new TransportException("m", sent));
            assertThat(copy.requestSent()).isEqualTo(sent);
            assertThat(copy).hasMessage("m");
        }
    }

    private static TransportException roundTrip(TransportException exception)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(exception);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (TransportException) in.readObject();
        }
    }
}
