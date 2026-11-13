package pt.dazz.bookmap.spring;

import java.util.Locale;

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

    /** The modules and the libraries of the transport. */
    private final String requirement;

    TransportType(String requirement) {
        this.requirement = requirement;
    }

    /**
     * Gives the message for an application that selects this transport and has no
     * {@code Transport} bean.
     *
     * @return the message, with what to add to the class path
     */
    String missingTransportMessage() {
        String name = name().toLowerCase(Locale.ROOT);
        String start = "bookmap.transport.type=" + name + ", but no Transport bean exists. ";
        return start + "Add " + requirement + " to the class path.";
    }
}
