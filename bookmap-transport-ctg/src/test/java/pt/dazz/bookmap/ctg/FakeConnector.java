package pt.dazz.bookmap.ctg;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** A fake connector for tests. It gives scripted replies and records each call. */
final class FakeConnector implements EciConnector {

    private final AtomicInteger opened = new AtomicInteger();
    private final List<EciCall> calls = new CopyOnWriteArrayList<>();
    private final List<FakeConnection> connections = new CopyOnWriteArrayList<>();
    private final Deque<Object> script = new ArrayDeque<>();
    private volatile Function<EciCall, EciReply> responder =
        call -> new EciReply(0, null, new byte[call.commareaLength()]);
    private volatile Exception openFailure;
    private volatile Exception closeFailure;

    /** Answers each call with this function, after the scripted replies. */
    FakeConnector respond(Function<EciCall, EciReply> function) {
        this.responder = function;
        return this;
    }

    /** Adds a scripted reply for the next call. */
    synchronized FakeConnector thenReply(EciReply reply) {
        script.add(reply);
        return this;
    }

    /** Adds a scripted failure for the next call. */
    synchronized FakeConnector thenFail(Exception failure) {
        script.add(failure);
        return this;
    }

    FakeConnector failOpen(Exception failure) {
        this.openFailure = failure;
        return this;
    }

    FakeConnector failClose(Exception failure) {
        this.closeFailure = failure;
        return this;
    }

    int opened() {
        return opened.get();
    }

    List<EciCall> calls() {
        return List.copyOf(calls);
    }

    List<FakeConnection> connections() {
        return List.copyOf(connections);
    }

    @Override
    public EciConnection open() throws IOException {
        Exception failure = openFailure;
        if (failure instanceof IOException io) {
            throw io;
        }
        if (failure != null) {
            throw (RuntimeException) failure;
        }
        opened.incrementAndGet();
        FakeConnection connection = new FakeConnection();
        connections.add(connection);
        return connection;
    }

    private synchronized Object next() {
        return script.poll();
    }

    /** A fake connection. */
    final class FakeConnection implements EciConnection {

        private volatile boolean closed;

        boolean closed() {
            return closed;
        }

        @Override
        public EciReply call(EciCall call) throws IOException {
            calls.add(call);
            Object next = next();
            if (next instanceof IOException failure) {
                throw failure;
            }
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            if (next instanceof EciReply reply) {
                return reply;
            }
            return responder.apply(call);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            Exception failure = closeFailure;
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure != null) {
                throw (RuntimeException) failure;
            }
        }
    }
}
