package pt.dazz.bookmap;

import java.util.ArrayList;
import java.util.List;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Hand-written codec for tests. It records every call. */
class FakeCodec<T> implements BookCodec<T> {

    final List<String> events = new ArrayList<>();
    final List<CodecContext> contexts = new ArrayList<>();
    private final Class<T> type;
    private final String charsetName;

    FakeCodec(Class<T> type, String charsetName) {
        this.type = type;
        this.charsetName = charsetName;
    }

    @Override public Class<T> type() { return type; }
    @Override public String operationName() { return "GETORDER"; }
    @Override public String charsetName() { return charsetName; }
    @Override public int maxResponseLength() { return 128; }
    @Override public T newInstance() { throw new UnsupportedOperationException(); }

    @Override
    public byte[] encodeRequest(T value, CodecContext ctx) {
        events.add("encode");
        contexts.add(ctx);
        return new byte[] {1, 2};
    }

    @Override
    public void decodeResponse(byte[] data, T target, CodecContext ctx) {
        events.add("decode");
        if (target instanceof Order order) {
            order.decoded++;
        }
    }

    @Override public T decodeRequest(byte[] data, CodecContext ctx) { throw new UnsupportedOperationException(); }
    @Override public byte[] encodeResponse(T value, CodecContext ctx) { throw new UnsupportedOperationException(); }

    @Override
    public void resetOutputCollections(T value) {
        events.add("reset");
    }

    /** A plain book. */
    static class Order {
        int decoded;
    }

    /** A book with pages and a check. */
    static class PagedOrder extends Order implements Continuable, Validatable {
        int pagesWithMore;
        int failOnDecode = -1;

        @Override
        public boolean hasContinuation() {
            return decoded <= pagesWithMore;
        }

        @Override
        public void validate() {
            if (decoded == failOnDecode) {
                throw new pt.dazz.bookmap.exception.BusinessException("Order " + decoded + " is not valid");
            }
        }
    }

    /** A subclass without a codec. */
    static class SpecialOrder extends Order {
    }
}
