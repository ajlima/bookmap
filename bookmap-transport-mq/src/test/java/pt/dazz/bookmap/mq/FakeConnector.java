package pt.dazz.bookmap.mq;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A programmable {@link MqConnector} for the tests. It records the calls. */
final class FakeConnector implements MqConnector {

    /** Gives the reply of a receive call: bytes, {@code null} for no reply, or an exception. */
    @FunctionalInterface
    interface Responder {
        byte[] reply(String replyQueue, String correlationId) throws MqException;
    }

    /** One receive call. */
    record Received(String replyQueue, String correlationId, Duration maxWait) { }

    final List<MqRequest> sent = new CopyOnWriteArrayList<>();
    final List<Received> received = new CopyOnWriteArrayList<>();
    final AtomicInteger opened = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    final AtomicInteger inUse = new AtomicInteger();
    final AtomicInteger maxInUse = new AtomicInteger();
    private final AtomicInteger messageIds = new AtomicInteger();

    /** The failure of the next open call only. */
    final AtomicReference<MqException> openFailure = new AtomicReference<>();
    volatile MqException sendFailure;
    volatile MqException closeFailure;
    volatile RuntimeException closeRuntimeFailure;
    /** The message ID of each send call, or {@code null} for the default {@code ID:<n>}. */
    volatile String fixedMessageId;
    volatile boolean useFixedMessageId;
    volatile Responder responder = (queue, correlationId) -> new byte[] {(byte) 0xF1};

    @Override
    public MqSession open() throws MqException {
        MqException failure = openFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }
        opened.incrementAndGet();
        return new FakeSession();
    }

    static MqException failure(MqFailure failure) {
        return new MqException(failure, "fake " + failure, null);
    }

    private final class FakeSession implements MqSession {

        @Override
        public String send(MqRequest request) throws MqException {
            maxInUse.accumulateAndGet(inUse.incrementAndGet(), Math::max);
            sent.add(request);
            if (sendFailure != null) {
                inUse.decrementAndGet();
                throw sendFailure;
            }
            if (useFixedMessageId) {
                inUse.decrementAndGet();
                return fixedMessageId;
            }
            return "ID:" + messageIds.incrementAndGet();
        }

        @Override
        public byte[] receive(String replyQueue, String correlationId, Duration wait)
                throws MqException {
            received.add(new Received(replyQueue, correlationId, wait));
            try {
                return responder.reply(replyQueue, correlationId);
            } finally {
                inUse.decrementAndGet();
            }
        }

        @Override
        public void close() throws MqException {
            closed.incrementAndGet();
            if (closeRuntimeFailure != null) {
                throw closeRuntimeFailure;
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
