package pt.dazz.bookmap.spring;

/**
 * The transport that the Spring Boot auto-configuration creates. The property
 * {@code bookmap.transport.type} selects it.
 */
public enum TransportType {

    /** The CICS Transaction Gateway (ECI). */
    CTG("bookmap-transport-ctg and the IBM library ctgclient.jar"),

    /** IBM MQ, with Jakarta Messaging. */
    MQ("bookmap-transport-mq"),

    /** HTTP, with a binary body. */
    HTTP("bookmap-transport-http");

    private final String requirement;

    TransportType(String requirement) {
        this.requirement = requirement;
    }

    /**
     * Tells what the class path must contain for this transport.
     *
     * @return the modules and the libraries, for an error message
     */
    String requirement() {
        return requirement;
    }
}
