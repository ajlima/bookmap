package pt.dazz.bookmap.simulator;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookCodec;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

/**
 * In-memory transport for tests. It replaces a real CICS or MQ system. Register byte-level or
 * typed operations, then use the transport in a {@code BookmapClient}.
 *
 * <p>Each request runs on a virtual thread. A request that takes more than the timeout is
 * interrupted and fails with {@link TransportTimeoutException}. The transport records every
 * request. Registrations and the request list are thread-safe.
 */
public final class SimulatorTransport implements Transport {

    private final BookCodecRegistry registry;
    private final CodecContext defaultContext;
    private final Duration timeout;
    private final Map<String, SimulatedOperation> operations = new ConcurrentHashMap<>();
    private final Map<String, CodecContext> contexts = new ConcurrentHashMap<>();
    private final List<BookRequest> received = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private SimulatorTransport(Builder builder) {
        this.registry = builder.registry == null ? BookCodecRegistry.loadDefault() : builder.registry;
        this.defaultContext = builder.charset == null
            ? CodecContext.defaults() : new CodecContext(builder.charset);
        this.timeout = builder.timeout;
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Registers a byte-level operation. An earlier operation with the same name is replaced.
     *
     * @param operation the operation name
     * @param op the operation
     * @return this transport
     */
    public SimulatorTransport register(String operation, SimulatedOperation op) {
        operations.put(Objects.requireNonNull(operation, "operation"),
            Objects.requireNonNull(op, "op"));
        return this;
    }

    /**
     * Registers a typed operation. The transport finds the codec for the type in the codec
     * registry and uses the operation name of the codec. The charset of the codec is used when it
     * is not empty. Otherwise the charset of the simulator is used. For a class with subtypes,
     * register the base class. The decoded call is the subtype that the request selects.
     *
     * @param type the book type
     * @param handler the handler
     * @param <T> the book type
     * @return this transport
     * @throws IllegalArgumentException if the type is a subtype of another book class
     */
    public <T> SimulatorTransport register(Class<T> type, SimulatedHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return registerResponder(type, call -> {
            handler.handle(call);
            return call;
        });
    }

    /**
     * Registers a typed operation that gives its own response object. The rules of
     * {@link #register(Class, SimulatedHandler)} apply. The transport encodes the object that
     * the responder gives, with the layout of its class.
     *
     * @param type the book type
     * @param responder the responder
     * @param <T> the book type
     * @return this transport
     * @throws IllegalArgumentException if the type is a subtype of another book class
     */
    public <T> SimulatorTransport registerResponder(Class<T> type,
                                                    SimulatedResponder<T> responder) {
        Objects.requireNonNull(responder, "responder");
        BookCodec<T> codec = registry.codecFor(type);
        if (codec.type() != type) {
            throw new IllegalArgumentException(type.getName() + " is a subtype of "
                + codec.type().getName() + ". Register " + codec.type().getName() + ".");
        }
        CodecContext ctx = contextFor(codec);
        String operation = codec.operationName();
        return register(operation, request -> {
            T response = responder.respond(codec.decodeRequest(request, ctx));
            if (response == null) {
                throw new MappingException(operation + ": the responder returned null");
            }
            return codec.encodeResponse(response, ctx);
        });
    }

    private CodecContext contextFor(BookCodec<?> codec) {
        String name = codec.charsetName();
        if (name.isEmpty()) {
            return defaultContext;
        }
        return contexts.computeIfAbsent(name, key -> {
            try {
                return new CodecContext(Charset.forName(key));
            } catch (IllegalArgumentException e) {
                throw new MappingException(codec.operationName() + ": invalid charset " + key, e);
            }
        });
    }

    /**
     * Returns the requests received so far, in order.
     *
     * @return an unmodifiable snapshot of the requests
     */
    public List<BookRequest> receivedRequests() {
        return List.copyOf(received);
    }

    /** Removes all recorded requests. */
    public void clearReceivedRequests() {
        received.clear();
    }

    @Override
    public byte[] exchange(BookRequest request) {
        received.add(request);
        SimulatedOperation operation = operations.get(request.operation());
        if (operation == null) {
            throw new OperationNotFoundException(
                "No simulated operation registered: " + request.operation());
        }
        Future<byte[]> future = executor.submit(() -> operation.handle(request.payload()));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TransportTimeoutException("Simulated operation " + request.operation()
                + " did not finish in " + timeout.toMillis() + " ms", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted while waiting for " + request.operation(), e);
        } catch (ExecutionException e) {
            throw translate(e.getCause(), request.operation());
        }
    }

    private static BookmapException translate(Throwable failure, String operation) {
        if (failure instanceof SimulatedAbend abend) {
            return new RemoteAbendException(abend.abendCode(), abend.getMessage(), abend);
        }
        if (failure instanceof BookmapException bookmap) {
            return bookmap;
        }
        return new TransportException("Simulated operation " + operation + " failed", failure);
    }

    /** Builds a {@link SimulatorTransport}. */
    public static final class Builder {

        private BookCodecRegistry registry;
        private Charset charset;
        private Duration timeout = Duration.ofSeconds(5);

        private Builder() {
        }

        /**
         * Sets the codec registry. The default is {@link BookCodecRegistry#loadDefault()}.
         *
         * @param registry the codec registry
         * @return this builder
         */
        public Builder codecRegistry(BookCodecRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        /**
         * Sets the charset for codecs that name no charset. The default is IBM037.
         *
         * @param charset a single-byte charset
         * @return this builder
         */
        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset");
            return this;
        }

        /**
         * Sets the time limit for one operation. The default is 5 seconds.
         *
         * @param timeout the time limit
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * Builds the transport.
         *
         * @return a new transport
         */
        public SimulatorTransport build() {
            return new SimulatorTransport(this);
        }
    }
}
