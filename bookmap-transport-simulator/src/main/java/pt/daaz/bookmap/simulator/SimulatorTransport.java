package pt.daaz.bookmap.simulator;

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
import pt.daaz.bookmap.codec.BookCodecRegistry;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.exception.BookmapException;
import pt.daaz.bookmap.exception.OperationNotFoundException;
import pt.daaz.bookmap.exception.RemoteAbendException;
import pt.daaz.bookmap.exception.TransportException;
import pt.daaz.bookmap.exception.TransportTimeoutException;
import pt.daaz.bookmap.spi.BookCodec;
import pt.daaz.bookmap.spi.BookRequest;
import pt.daaz.bookmap.spi.Transport;

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
    private final Charset charset;
    private final Duration timeout;
    private final Map<String, SimulatedOperation> operations = new ConcurrentHashMap<>();
    private final Map<String, CodecContext> contexts = new ConcurrentHashMap<>();
    private final List<BookRequest> received = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private SimulatorTransport(Builder builder) {
        this.registry = builder.registry == null ? BookCodecRegistry.loadDefault() : builder.registry;
        this.charset = builder.charset == null ? CodecContext.defaults().charset() : builder.charset;
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
     * is not empty. Otherwise the charset of the simulator is used.
     *
     * @param type the book type
     * @param handler the handler
     * @param <T> the book type
     * @return this transport
     */
    public <T> SimulatorTransport register(Class<T> type, SimulatedHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        BookCodec<T> codec = registry.codecFor(type);
        CodecContext ctx = contexts.computeIfAbsent(codec.charsetName(), this::contextFor);
        return register(codec.operationName(), request -> {
            T call = codec.decodeRequest(request, ctx);
            handler.handle(call);
            return codec.encodeResponse(call, ctx);
        });
    }

    private CodecContext contextFor(String charsetName) {
        return new CodecContext(charsetName.isEmpty() ? charset : Charset.forName(charsetName));
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
