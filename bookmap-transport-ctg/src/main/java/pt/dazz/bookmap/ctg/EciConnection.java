package pt.dazz.bookmap.ctg;

import java.io.IOException;

/**
 * One open connection to a CICS Transaction Gateway. {@link CtgTransport} uses a connection in
 * one thread at a time.
 */
public interface EciConnection extends AutoCloseable {

    /**
     * Calls a CICS program synchronously and waits for the reply.
     *
     * @param call the call
     * @return the reply. A reply with a return code that is not zero is not an exception.
     * @throws IOException if a network failure occurs. The program can be complete in CICS.
     */
    EciReply call(EciCall call) throws IOException;

    /**
     * Closes the connection.
     *
     * @throws IOException if a network failure occurs
     */
    @Override
    void close() throws IOException;
}
