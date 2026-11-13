package pt.dazz.bookmap.mq;

/**
 * Tells how the server program correlates the reply with the request.
 */
public enum MqCorrelation {

    /** The server copies the message ID of the request to the correlation ID of the reply. */
    MESSAGE_ID,

    /**
     * The transport sets a new correlation ID on the request. The server copies the correlation
     * ID of the request to the reply.
     */
    CORRELATION_ID
}
