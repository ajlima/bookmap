// Test stub of the IBM CICS Transaction Gateway API. The signatures and the constant values come
// from the public CICS TG V9.3 Javadoc. This file is not published and contains no IBM code.
// Do not change a signature or a constant without a check against the IBM Javadoc.
package com.ibm.ctg.client;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Consumer;

/** Stub of a connection to a Gateway daemon. The static fields and extra methods are for tests only. */
public class JavaGateway {

    public static volatile IOException openFailure;
    public static volatile IOException flowFailure;
    public static volatile IOException closeFailure;
    public static volatile Consumer<ECIRequest> onFlow;
    public static volatile JavaGateway last;

    private final String url;
    private final int port;
    private final int socketConnectTimeout;
    private final Properties protocolProperties;
    private ECIRequest lastRequest;
    private boolean closed;

    public JavaGateway(String strSetURL, int iSetPort, int setSocketConnectTimeout,
                       Properties protocolProps) throws IOException {
        if (openFailure != null) {
            throw openFailure;
        }
        url = strSetURL;
        port = iSetPort;
        socketConnectTimeout = setSocketConnectTimeout;
        protocolProperties = protocolProps;
        last = this;
    }

    public int flow(GatewayRequest gatRequest) throws IOException {
        ECIRequest request = (ECIRequest) gatRequest;
        lastRequest = request;
        if (flowFailure != null) {
            throw flowFailure;
        }
        if (onFlow != null) {
            onFlow.accept(request);
        }
        return request.getRc();
    }

    public void close() throws IOException {
        closed = true;
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    /** Test only: resets the static test controls. */
    public static void reset() {
        openFailure = null;
        flowFailure = null;
        closeFailure = null;
        onFlow = null;
        last = null;
    }

    public String url() { return url; }

    public int port() { return port; }

    public int socketConnectTimeout() { return socketConnectTimeout; }

    public Properties protocolProperties() { return protocolProperties; }

    public ECIRequest lastRequest() { return lastRequest; }

    public boolean closed() { return closed; }
}
