package pt.dazz.bookmap;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.ContinuationLimitException;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

/**
 * Runs a book operation: encode, send, decode, validate, and continue.
 * The client is immutable and thread-safe. The book object is not thread-safe.
 */
public final class BookmapClient {

    private static final int DEFAULT_MAX_CALLS = 1000;

    private final Transport transport;
    private final CodecContext defaultContext;
    private final int maxCalls;
    private final BookCodecRegistry registry;
    private final Map<String, CodecContext> codecContexts = new ConcurrentHashMap<>();

    private BookmapClient(Builder builder) {
        this.transport = builder.transport;
        this.defaultContext = builder.charset == null
            ? CodecContext.defaults() : new CodecContext(builder.charset);
        this.maxCalls = builder.maxCalls;
        this.registry = builder.registry == null
            ? BookCodecRegistry.loadDefault() : builder.registry;
    }

    /**
     * Creates a builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the operation of a book. The client fills the same object with the response.
     * An exception from {@code validate()} or from the transport goes to the caller unchanged.
     *
     * @param request the book
     * @param <T> the book class
     * @return the same book
     * @throws MappingException if there is no codec or the response is empty
     * @throws ContinuationLimitException if the remote program has more data after the last allowed call
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(T request) {
        Objects.requireNonNull(request, "request");
        BookCodec<T> codec = registry.codecFor((Class<T>) request.getClass());
        CodecContext ctx = contextFor(codec);
        String operation = codec.operationName();
        codec.resetOutputCollections(request);
        int calls = 0;
        boolean more;
        do {
            calls++;
            byte[] payload = codec.encodeRequest(request, ctx);
            byte[] response = transport.exchange(
                new BookRequest(operation, payload, codec.maxResponseLength()));
            if (response == null) {
                throw new MappingException(operation + ": empty response from transport");
            }
            codec.decodeResponse(response, request, ctx);
            if (request instanceof Validatable validatable) {
                validatable.validate();
            }
            more = request instanceof Continuable continuable && continuable.hasContinuation();
            if (more && calls >= maxCalls) {
                throw new ContinuationLimitException(operation
                    + ": the remote program has more data after " + maxCalls + " calls");
            }
        } while (more);
        return request;
    }

    private CodecContext contextFor(BookCodec<?> codec) {
        String name = codec.charsetName();
        if (name.isEmpty()) {
            return defaultContext;
        }
        return codecContexts.computeIfAbsent(name, key -> {
            try {
                return new CodecContext(Charset.forName(key));
            } catch (IllegalArgumentException e) {
                throw new MappingException(
                    codec.operationName() + ": invalid charset " + key, e);
            }
        });
    }

    /**
     * Builds a {@link BookmapClient}.
     */
    public static final class Builder {

        private Transport transport;
        private Charset charset;
        private int maxCalls = DEFAULT_MAX_CALLS;
        private BookCodecRegistry registry;

        private Builder() {
        }

        /**
         * Sets the transport. This value is mandatory.
         *
         * @param transport the transport
         * @return this builder
         */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Sets the default charset. The default value is IBM037.
         *
         * @param charset the charset
         * @return this builder
         */
        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        /**
         * Sets the maximum number of calls for one operation. The default value is 1000.
         *
         * @param maxCalls the maximum, at least 1
         * @return this builder
         * @throws IllegalArgumentException if the value is less than 1
         */
        public Builder maxCalls(int maxCalls) {
            if (maxCalls < 1) {
                throw new IllegalArgumentException("maxCalls must be at least 1: " + maxCalls);
            }
            this.maxCalls = maxCalls;
            return this;
        }

        /**
         * Sets the codec registry. The default is {@link BookCodecRegistry#loadDefault()}.
         *
         * @param registry the registry
         * @return this builder
         */
        public Builder codecRegistry(BookCodecRegistry registry) {
            this.registry = registry;
            return this;
        }

        /**
         * Creates the client.
         *
         * @return the client
         * @throws NullPointerException if the transport is missing
         */
        public BookmapClient build() {
            Objects.requireNonNull(transport, "transport");
            return new BookmapClient(this);
        }
    }
}
