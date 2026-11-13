package pt.dazz.bookmap.ctg;

import java.io.IOException;

/**
 * Opens connections to a CICS Transaction Gateway. {@link CtgTransport} uses this interface and
 * does not call the IBM library. {@code pt.dazz.bookmap.ctg.ibm.JavaGatewayConnector} is the
 * implementation for the IBM CTG client library. A test can use a fake implementation.
 */
@FunctionalInterface
public interface EciConnector {

    /**
     * Opens a new connection.
     *
     * @return an open connection
     * @throws IOException if the connection cannot be opened
     */
    EciConnection open() throws IOException;
}
