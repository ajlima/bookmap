package pt.dazz.bookmap.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.Continuable;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.ContinuationLimitException;
import pt.dazz.bookmap.spi.BookCodec;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

class BookmapAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BookmapAutoConfiguration.class));

    /** A book that always asks for one more call. */
    static class Statement implements Continuable {
        @Override
        public boolean hasContinuation() {
            return true;
        }
    }

    /** Hand-written codec. It records the charset of each call. */
    static class StatementCodec implements BookCodec<Statement> {
        final List<Charset> charsets = new ArrayList<>();

        @Override public Class<Statement> type() { return Statement.class; }
        @Override public String operationName() { return "GETSTMT"; }
        @Override public String charsetName() { return ""; }
        @Override public int maxResponseLength() { return 16; }
        @Override public Statement newInstance() { throw new UnsupportedOperationException(); }

        @Override
        public byte[] encodeRequest(Statement value, CodecContext ctx) {
            charsets.add(ctx.charset());
            return new byte[] {1};
        }

        @Override public void decodeResponse(byte[] data, Statement target, CodecContext ctx) { }
        @Override public Statement decodeRequest(byte[] data, CodecContext ctx) { throw new UnsupportedOperationException(); }
        @Override public byte[] encodeResponse(Statement value, CodecContext ctx) { throw new UnsupportedOperationException(); }
        @Override public void resetOutputCollections(Statement value) { }
    }

    /** Hand-written transport. It counts the calls. */
    static class CountingTransport implements Transport {
        int calls;

        @Override
        public byte[] exchange(BookRequest request) {
            calls++;
            return new byte[] {0};
        }
    }

    private ApplicationContextRunner withBeans(StatementCodec codec, CountingTransport transport) {
        return runner
            .withBean(Transport.class, () -> transport)
            .withBean(BookCodecRegistry.class, () -> BookCodecRegistry.of(codec));
    }

    @Test
    void createsClientWhenTransportExists() {
        runner.withBean(Transport.class, CountingTransport::new)
            .run(context -> assertThat(context).hasSingleBean(BookmapClient.class)
                .hasSingleBean(BookCodecRegistry.class));
    }

    @Test
    void noClientWithoutTransport() {
        runner.run(context -> assertThat(context).doesNotHaveBean(BookmapClient.class)
            .hasSingleBean(BookCodecRegistry.class));
    }

    @Test
    void userClientWins() {
        BookmapClient own = BookmapClient.builder().transport(new CountingTransport()).build();
        runner.withBean(Transport.class, CountingTransport::new)
            .withBean(BookmapClient.class, () -> own)
            .run(context -> assertThat(context.getBean(BookmapClient.class)).isSameAs(own));
    }

    @Test
    void propertiesAreApplied() {
        StatementCodec codec = new StatementCodec();
        CountingTransport transport = new CountingTransport();
        withBeans(codec, transport).withPropertyValues("bookmap.max-calls=2")
            .run(context -> {
                BookmapClient client = context.getBean(BookmapClient.class);
                Statement statement = new Statement();
                assertThatThrownBy(() -> client.execute(statement))
                    .isInstanceOf(ContinuationLimitException.class);
                assertThat(transport.calls).isEqualTo(2);
            });
    }

    @Test
    void defaultsAreApplied() {
        StatementCodec codec = new StatementCodec();
        withBeans(codec, new CountingTransport()).withPropertyValues("bookmap.max-calls=1")
            .run(context -> {
                BookmapClient client = context.getBean(BookmapClient.class);
                assertThatThrownBy(() -> client.execute(new Statement()))
                    .isInstanceOf(ContinuationLimitException.class);
                assertThat(codec.charsets).containsExactly(Charset.forName("IBM037"));
            });
    }

    @Test
    void charsetPropertyIsApplied() {
        StatementCodec codec = new StatementCodec();
        withBeans(codec, new CountingTransport())
            .withPropertyValues("bookmap.charset=IBM500", "bookmap.max-calls=1")
            .run(context -> {
                BookmapClient client = context.getBean(BookmapClient.class);
                assertThatThrownBy(() -> client.execute(new Statement()))
                    .isInstanceOf(ContinuationLimitException.class);
                assertThat(codec.charsets).containsExactly(Charset.forName("IBM500"));
            });
    }

    @Test
    void invalidCharsetPropertyFails() {
        runner.withBean(Transport.class, CountingTransport::new)
            .withPropertyValues("bookmap.charset=UTF-8")
            .run(context -> assertThat(context).hasFailed().getFailure()
                .rootCause().isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single-byte"));
    }

    @Test
    void unknownCharsetPropertyFails() {
        runner.withBean(Transport.class, CountingTransport::new)
            .withPropertyValues("bookmap.charset=NO-SUCH-CHARSET")
            .run(context -> assertThat(context).hasFailed().getFailure()
                .rootCause().isInstanceOf(UnsupportedCharsetException.class));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ctg|bookmap-transport-ctg and the IBM library ctgclient.jar",
        "http|bookmap-transport-http",
        "mq|bookmap-transport-mq",
        })
    void missingTransportStopsTheApplication(String type, String requirement) {
        runner.withPropertyValues("bookmap.transport.type=" + type)
            .run(context -> assertThat(context).hasFailed().getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bookmap.transport.type=" + type + ", but no Transport bean exists. Add "
                    + requirement + " to the class path."));
    }

    @Test
    void missingTransportFailsAlsoWithLazyInitialization() {
        runner.withInitializer(context ->
                context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
            .withPropertyValues("bookmap.transport.type=ctg")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void typeWithApplicationTransportStarts() {
        runner.withBean(Transport.class, CountingTransport::new)
            .withPropertyValues("bookmap.transport.type=http")
            .run(context -> assertThat(context).hasSingleBean(BookmapClient.class));
    }

    @Test
    void invalidTypeFails() {
        runner.withBean(Transport.class, CountingTransport::new)
            .withPropertyValues("bookmap.transport.type=ctgg")
            .run(context -> assertThat(context).hasFailed());
    }
}
