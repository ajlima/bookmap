The module `bookmap-transport-mq` calls a mainframe program through IBM MQ. The transport puts the request bytes on a request queue. Then it waits for the reply on a reply queue, and it returns the reply bytes to `BookmapClient`.

The transport uses Jakarta Messaging (JMS 3.1). You give it the connection factory of IBM MQ. The BookMap module does not contain IBM code, and its POM does not name an IBM artifact.
For the other transports and a comparison, see [Transports and simulator](Transports-and-simulator#choose-a-transport).

## Add the dependencies

Add the transport and the IBM MQ classes for Jakarta Messaging. Gradle:

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-mq:<version>")
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:9.4.3.0")
}
```

Maven:

```xml
<dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-transport-mq</artifactId>
    <version><!-- version --></version>
</dependency>
<dependency>
    <groupId>com.ibm.mq</groupId>
    <artifactId>com.ibm.mq.jakarta.client</artifactId>
    <version>9.4.3.0</version>
</dependency>
```

The IBM library is on Maven Central under an IBM license. Read the license before you use the library.

## Create the transport

```java
MQConnectionFactory factory = new MQConnectionFactory();
factory.setHostName("mq.example.com");
factory.setPort(1414);
factory.setQueueManager("QM1");
factory.setChannel("APP.SVRCONN");
factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);

MqTransport transport = MqTransport.builder()
    .connector(JmsConnector.builder()
        .connectionFactory(factory)
        .credentials("ORDUSER", password)
        .build())
    .defaultReplyQueue("ORDERS.REPLY")
    .route("GetCustomerOrders", "ORDERS.REQUEST")
    .route("GetInvoice", "INVOICE.REQUEST", "INVOICE.REPLY")
    .build();

BookmapClient client = BookmapClient.builder().transport(transport).build();
```

`MqTransport` is thread-safe. Create one transport for each queue manager, and close it when the application stops.

### The builder of MqTransport

| Method | Meaning | Default |
|---|---|---|
| `connector(MqConnector)` | The connector. Mandatory. | none |
| `route(operation, requestQueue)` | Adds a route with the default reply queue. | none |
| `route(operation, requestQueue, replyQueue)` | Adds a route. | none |
| `routes(Map<String, MqRoute>)` | Adds all routes of the map. | none |
| `defaultReplyQueue(String)` | The reply queue for a route without a reply queue. | `null`: a temporary queue. |
| `correlation(MqCorrelation)` | The correlation mode. See below. | `MESSAGE_ID` |
| `timeout(Duration)` | The maximum wait for the reply. It is also the expiry of the request message. | 30 seconds |
| `persistent(boolean)` | Sends persistent request messages. | `false` |
| `maxSessions(int)` | The maximum number of open sessions. | 10 |
| `sessionWaitTimeout(Duration)` | The maximum wait for a free session when all sessions are in use. | 30 seconds |

A route for an operation replaces an earlier route for the same operation. `build()` throws `IllegalStateException` if the connector or the routes are missing.

### The builder of JmsConnector

| Method | Meaning | Default |
|---|---|---|
| `connectionFactory(ConnectionFactory)` | The connection factory of the provider. Mandatory. | none |
| `credentials(user, password)` | The user and the password for the connection. | No credentials. |
| `mqNativeMessages(boolean)` | Sends messages without an RFH2 header. | `true` |

Each session of the pool has its own JMS connection. If a connection breaks, the transport closes that session and opens a new one for the next request.

## The reply queue

The transport selects the reply queue in this order:

1. The reply queue of the route.
2. The default reply queue of the builder.
3. A temporary queue of the session. IBM MQ makes it from the model queue of the factory (property `TEMPMODEL`, default `SYSTEM.DEFAULT.MODEL.QUEUE`).

After a timeout, the transport closes a session with a temporary queue. This deletes the queue and the late reply on it. The next request opens a new session.

A temporary dynamic queue does not accept persistent messages. Many server programs copy the persistence of the request to the reply. Thus, `build()` throws `IllegalStateException` if you set `persistent(true)` and a route uses a temporary queue.

## The contract of the server program

The program on the mainframe must do these steps:

1. Get the request message from the request queue. The message has the format `MQFMT_NONE` and no RFH2 header. The data is the book bytes.
2. Put the reply on the queue in the field `ReplyToQ` of the request.
3. Put the response bytes in the reply, with the format `MQFMT_NONE`.
4. Set the correlation ID of the reply (see below).

Do not use the format `MQSTR` for the reply. A text reply comes as a JMS `TextMessage`, and MQ can convert its characters. A conversion changes the bytes of packed and binary fields. The transport throws `TransportException` for a reply that is not a bytes message.

### Correlation

| Mode | The transport | The server program |
|---|---|---|
| `MESSAGE_ID` (default) | Waits for a reply whose correlation ID is the message ID of the request. | Copies the `MsgId` of the request to the `CorrelId` of the reply. |
| `CORRELATION_ID` | Sets a new random correlation ID on the request, and waits for a reply with the same correlation ID. | Copies the `CorrelId` of the request to the `CorrelId` of the reply. |

### Late replies

A reply that comes after the timeout stays on the reply queue. No later request reads it, because its correlation ID is different. The request message expires after the timeout, so a program that reads it late does not see it.

**CAUTION:** Remove old messages from a shared reply queue, or let the server program set an expiry on the reply. If not, the late replies fill the queue.

## Message format and other providers

With `mqNativeMessages(true)`, the connector gives each queue name to Jakarta Messaging in the form `queue:///NAME?targetClient=1`. IBM MQ then sends the message without an RFH2 header. If a queue name starts with `queue://`, the connector uses the name as it is.

**CAUTION:** Do not set `mqNativeMessages(false)` with IBM MQ, unless the server program reads the RFH2 header. If not, the program gets the RFH2 bytes in front of the book, and the book is incorrect.

Use `mqNativeMessages(false)` with a provider that is not IBM MQ, for example Apache ActiveMQ Artemis. The BookMap tests use Artemis.

## Errors

| Failure | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| The connection fails, or no session is free before `sessionWaitTimeout`. | `ConnectionException`. The request was not sent. |
| The connection is not authorized. | `TransportException` |
| The put of the request fails. | `TransportException`. The request can be on the queue. |
| No reply before the timeout. | `TransportTimeoutException` |
| The reply is not a bytes message. | `TransportException` |
| The get of the reply fails. | `TransportException`. The request can be complete on the server. |
| The connector gives no message ID. | `TransportException` |
| A request after `close()`. | `TransportException` |

The cause of each exception is an `MqException`. Its `failure()` gives the type: `NOT_AUTHORIZED`, `INVALID_QUEUE`, `NOT_BYTES`, or `OTHER`. The cause of the `MqException` is the `JMSException`. Its message contains the MQ reason code, for example `2035` (`MQRC_NOT_AUTHORIZED`).

IBM MQ gives the same exception type for all failures to open a queue. Thus, an unknown queue (2085) and a queue that is not authorized (2035) both give `INVALID_QUEUE`. Read the reason code in the message of the cause.

**WARNING:** Do not send a request again after a `TransportException` or a `TransportTimeoutException`, unless the program is idempotent. The program can have done the first request.

The BookMap tests do not use a real queue manager. The mapping of the IBM MQ exceptions comes from the IBM documentation.

## Not supported

- The CICS-MQ bridge and the MQCIH header. Thus, the transport never throws `RemoteAbendException`.
- Text messages (`MQSTR`) and the character conversion of MQ.
- More than one reply for one request, message groups, and segmentation.
- Transactions (a JMS transacted session or XA).
- A different queue manager for each route. Create one transport for each queue manager.

## Use another client library

`MqTransport` calls the interface `MqConnector`, not Jakarta Messaging. `JmsConnector` is one implementation. To use another library, for example the IBM MQ classes for Java, implement `MqConnector` and `MqSession`. Your session must give an `MqException` with the correct `MqFailure` for each failure.
