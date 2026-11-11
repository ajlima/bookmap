package pt.dazz.bookmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.FakeCodec.Order;
import pt.dazz.bookmap.FakeCodec.PagedOrder;
import pt.dazz.bookmap.FakeCodec.SpecialOrder;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.codec.BookCodecRegistryTest;
import pt.dazz.bookmap.exception.BusinessException;
import pt.dazz.bookmap.exception.ContinuationLimitException;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

class BookmapClientTest {

    /** Counts the calls and records the requests. */
    static class CountingTransport implements Transport {
        final List<BookRequest> requests = new ArrayList<>();
        final List<String> events;
        byte[] response = new byte[] {9};

        CountingTransport(List<String> events) {
            this.events = events;
        }

        @Override
        public byte[] exchange(BookRequest request) {
            requests.add(request);
            events.add("exchange");
            return response;
        }

        int calls() {
            return requests.size();
        }
    }

    private final FakeCodec<Order> orderCodec = new FakeCodec<>(Order.class, "");
    private final FakeCodec<PagedOrder> pagedCodec = new FakeCodec<>(PagedOrder.class, "");
    private final CountingTransport transport = new CountingTransport(new ArrayList<>());

    private BookmapClient.Builder builder() {
        return BookmapClient.builder().transport(transport)
            .codecRegistry(BookCodecRegistry.of(orderCodec, pagedCodec));
    }

    @Test
    void singleCallForPlainPojo() {
        Order order = new Order();
        Order result = builder().build().execute(order);
        assertThat(result).isSameAs(order);
        assertThat(transport.calls()).isEqualTo(1);
        assertThat(order.decoded).isEqualTo(1);
    }

    @Test
    void validateIsCalledAfterEachResponse() {
        PagedOrder order = new PagedOrder();
        order.pagesWithMore = 5;
        order.failOnDecode = 2;
        assertThatThrownBy(() -> builder().build().execute(order))
            .isInstanceOf(BusinessException.class);
        assertThat(transport.calls()).isEqualTo(2);
    }

    @Test
    void continuationRepeatsUntilFalse() {
        PagedOrder order = new PagedOrder();
        order.pagesWithMore = 2;
        builder().build().execute(order);
        assertThat(transport.calls()).isEqualTo(3);
    }

    @Test
    void continuationStopsWhenLastAllowedCallHasNoMoreData() {
        PagedOrder order = new PagedOrder();
        order.pagesWithMore = 2;
        builder().maxCalls(3).build().execute(order);
        assertThat(transport.calls()).isEqualTo(3);
    }

    @Test
    void continuationLimit() {
        PagedOrder order = new PagedOrder();
        order.pagesWithMore = Integer.MAX_VALUE;
        BookmapClient client = builder().maxCalls(3).build();
        assertThatThrownBy(() -> client.execute(order))
            .isInstanceOf(ContinuationLimitException.class)
            .hasMessageContaining("GETORDER");
        assertThat(transport.calls()).isEqualTo(3);
    }

    @Test
    void outputCollectionsAreResetBeforeExecution() {
        PagedOrder order = new PagedOrder();
        order.pagesWithMore = 1;
        BookmapClient client = BookmapClient.builder().transport(transport)
            .codecRegistry(BookCodecRegistry.of(pagedCodec)).build();
        client.execute(order);
        assertThat(pagedCodec.events).containsExactly("reset", "encode", "decode", "encode", "decode");
        assertThat(transport.events).containsExactly("exchange", "exchange");
    }

    @Test
    void defaultContextUsesIbm037() {
        builder().build().execute(new Order());
        assertThat(orderCodec.contexts.get(0).charset().name()).isEqualTo("IBM037");
    }

    @Test
    void clientCharsetAppliesWhenCodecHasNone() {
        builder().charset(Charset.forName("IBM500")).build().execute(new Order());
        assertThat(orderCodec.contexts.get(0).charset().name()).isEqualTo("IBM500");
    }

    @Test
    void codecCharsetOverridesClientCharset() {
        FakeCodec<Order> codec = new FakeCodec<>(Order.class, "IBM1047");
        BookmapClient client = BookmapClient.builder().transport(transport)
            .charset(Charset.forName("IBM500"))
            .codecRegistry(BookCodecRegistry.of(codec)).build();
        client.execute(new Order());
        client.execute(new Order());
        assertThat(codec.contexts).hasSize(2);
        assertThat(codec.contexts.get(0).charset().name()).isEqualTo("IBM1047");
        assertThat(codec.contexts.get(1)).isSameAs(codec.contexts.get(0));
    }

    @Test
    void unknownCodecCharsetFails() {
        FakeCodec<Order> codec = new FakeCodec<>(Order.class, "NO-SUCH-CHARSET");
        BookmapClient client = BookmapClient.builder().transport(transport)
            .codecRegistry(BookCodecRegistry.of(codec)).build();
        assertThatThrownBy(() -> client.execute(new Order()))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("GETORDER")
            .hasMessageContaining("NO-SUCH-CHARSET");
        assertThat(transport.calls()).isZero();
    }

    @Test
    void decodeOnlyCodecCharsetFails() {
        // The JDK can decode ISO-2022-CN, but it cannot encode it.
        FakeCodec<Order> codec = new FakeCodec<>(Order.class, "ISO-2022-CN");
        BookmapClient client = BookmapClient.builder().transport(transport)
            .codecRegistry(BookCodecRegistry.of(codec)).build();
        assertThatThrownBy(() -> client.execute(new Order()))
            .isInstanceOf(MappingException.class)
            .hasMessage("GETORDER: invalid charset ISO-2022-CN")
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(transport.calls()).isZero();
    }

    @Test
    void requestCarriesOperationAndMaxResponseLength() {
        builder().build().execute(new Order());
        BookRequest request = transport.requests.get(0);
        assertThat(request.operation()).isEqualTo("GETORDER");
        assertThat(request.maxResponseLength()).isEqualTo(128);
        assertThat(request.payload()).containsExactly(1, 2);
    }

    @Test
    void nullResponseFails() {
        transport.response = null;
        assertThatThrownBy(() -> builder().build().execute(new Order()))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("empty response");
    }

    @Test
    void transportExceptionPropagates() {
        RemoteAbendException abend = new RemoteAbendException("ASRA", "abend");
        BookmapClient client = builder().transport(request -> {
            throw abend;
        }).build();
        assertThatThrownBy(() -> client.execute(new Order())).isSameAs(abend);
    }

    @Test
    void missingCodecFails() {
        BookmapClient client = builder().build();
        assertThatThrownBy(() -> client.execute(new SpecialOrder()))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("annotationProcessor");
    }

    @Test
    void nullRequestFails() {
        BookmapClient client = builder().build();
        assertThatThrownBy(() -> client.execute(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultRegistryLoadsServiceEntries() {
        BookmapClient client = BookmapClient.builder().transport(transport).build();
        client.execute(new BookCodecRegistryTest.Invoice());
        assertThat(transport.calls()).isEqualTo(1);
        BookRequest request = transport.requests.get(0);
        assertThat(request.operation()).isEqualTo("INVOICE");
        assertThat(request.maxResponseLength()).isEqualTo(10);
    }

    @Test
    void builderRejectsMissingTransportAndBadMaxCalls() {
        assertThatThrownBy(() -> BookmapClient.builder().build())
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BookmapClient.builder().maxCalls(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
