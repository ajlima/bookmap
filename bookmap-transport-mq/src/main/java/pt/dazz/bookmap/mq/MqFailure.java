package pt.dazz.bookmap.mq;

/**
 * The type of a failure of an {@link MqConnector} or an {@link MqSession}.
 */
public enum MqFailure {

    /** The user is not authorized, for example to connect to the queue manager. */
    NOT_AUTHORIZED,

    /** The connector cannot open a queue. The queue is unknown or not authorized. */
    INVALID_QUEUE,

    /** The reply is not a bytes message. */
    NOT_BYTES,

    /** Another failure, for example a broken connection. */
    OTHER
}
