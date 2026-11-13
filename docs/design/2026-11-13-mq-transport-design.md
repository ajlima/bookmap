# BookMap IBM MQ transport: design

Date: 2026-11-13
Status: implemented in v0.10.0.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (the main design, sections 2.2, 5.9, and 5.10), `docs/design/2026-11-13-ctg-transport-design.md` (the CTG transport, #26), `CLAUDE.md` (project rules), GitHub issue #27.

## 1. Intent

BookMap has a transport SPI and an in-memory simulator. This design adds a transport that calls a mainframe program through IBM MQ, with a request queue and a reply queue.

A user adds the module `bookmap-transport-mq` and a Jakarta Messaging provider, for example the IBM MQ client `com.ibm.mq:com.ibm.mq.jakarta.client`. The user maps each operation name to a request queue and, as an option, a reply queue. Then the user gives the transport to a `BookmapClient`. The application code does not change: it uses the same book classes as with the simulator or the CTG transport.

The server side is a program that gets the request message, for example a CICS transaction that an MQ trigger starts. The program reads the book bytes, writes the response bytes to the reply queue, and copies the message ID of the request to the correlation ID of the reply.

The CTG transport (#26) and this transport use the same structure: a small BookMap connector interface, one adapter class, a builder with routes, and the same failure translation approach. The Spring Boot auto-configuration of the transports is a separate issue (#29).

## 2. Goals, non-goals, and anti-goals

Goals:

- A `Transport` that puts the request bytes on a request queue, waits for the correlated reply, and returns the reply bytes.
- A mapping from operation name to request queue and reply queue, set in a builder.
- A temporary reply queue when the route and the builder have no reply queue.
- Two correlation modes: the message ID of the request becomes the correlation ID of the reply (the default), or the server copies the correlation ID of the request to the reply.
- A translation of each failure to one of the BookMap exceptions of section 5.10 of the main design.
- A thread-safe transport, with a bounded pool of sessions.
- 100% line and branch coverage without a real queue manager.
- Publication to Maven Central, with no IBM artifact in the POM.

Non-goals (possible later):

- The CICS-MQ bridge and the MQCIH header. Thus, the transport cannot report a CICS abend: it never throws `RemoteAbendException`.
- Text messages (format `MQSTR`) and the character conversion of MQ.
- The IBM MQ classes for Java (`com.ibm.mq.MQQueueManager`) and the exact MQ reason codes (see 4.1).
- A different queue manager for each route. To use two queue managers, create two transports.
- Message groups, segmentation, and one request with more than one reply.
- Transactions (a JMS transacted session or XA).
- `BookRequest.attributes()` is not read.
- Spring Boot properties and auto-configuration (#29).
- A native image test of this transport.

Anti-goals (failure, even if the code works):

- `bookmap-core` or `bookmap-processor` depends on Jakarta Messaging or on an MQ library, or contains an MQ concept.
- A published BookMap artifact contains IBM code, or the POM of a published artifact names an IBM artifact.
- A `JMSException` or a provider exception goes to the caller without a translation to a BookMap exception.
- Silent data damage: a character conversion changes the bytes of a packed or binary field in the request or in the reply.
- A reply of another request goes to the caller.

## 3. Constraints

- The IBM MQ client libraries are on Maven Central under an IBM license: `com.ibm.mq:com.ibm.mq.jakarta.client` and `com.ibm.mq:com.ibm.mq.allclient` (version 9.4.3.0, checked on 2026-11-13). This design does not use them in the build.
- The Jakarta Messaging API `jakarta.jms:jakarta.jms-api` 3.1.0 is on Maven Central, under the EPL 2.0 or the GPL 2.0 with the Classpath Exception. It is not IBM code.
- The rules of `CLAUDE.md`: Java 21, no reflection at runtime, 100% line and branch coverage, English and ASD-STE100 documentation.
- Dependency verification is on. This design adds one main dependency and test dependencies (see 7).
- No queue manager is available for the tests.

## 4. Approaches

The author decided four points. For each point, this section gives the options and the decision.

### 4.1 The API and the dependency model

| Option | Trade-off |
|---|---|
| **A. Jakarta Messaging, with a connection factory from the user (selected).** | The module depends only on `jakarta.jms-api`. The user creates the IBM `MQConnectionFactory` and gives it to the builder. Another JMS provider also works. The POM names no IBM artifact, and the build needs no stub. The cost: the transport classifies a failure by the JMS exception type and by the step (connect, send, or receive). It does not read the MQ reason code, because that code is on an IBM class, and the project does not use reflection. |
| B. The IBM MQ classes for Java, as a `compileOnly` dependency. | Exact reason codes and full control of the message descriptor (MQMD). IBM only. The adapter cannot be tested without a queue manager, so it needs a stub, as in the CTG design. |
| C. Jakarta Messaging, with the IBM client as a normal dependency. | Simple setup for the user. But the POM names an IBM-licensed artifact. The CTG design gives this as an anti-goal. |

The author selected A.

### 4.2 The mapping from operation name to queues

| Option | Trade-off |
|---|---|
| **A. Routes in the builder (selected).** | `route("GetInvoice", "INVOICE.REQUEST")`, and `routes(Map<String, MqRoute>)`, as in the CTG design. Each route has a request queue and an optional reply queue. |
| B. One request queue and one reply queue for all operations. | Simple configuration. But the operation name must go in the message, and the server program must select the operation. |
| C. Routes, and a default request queue for an operation without a route. | An incorrect operation name goes to a queue and gives a timeout, not `OperationNotFoundException`. |

The author selected A.

### 4.3 The message format

| Option | Trade-off |
|---|---|
| **A. Bytes only, without the CICS-MQ bridge (selected).** | The request is a bytes message with the format `MQFMT_NONE` and no RFH2 header. The program gets the book bytes as they are. A reply that is not a bytes message causes a `TransportException`, because a character conversion damages packed and binary fields. |
| B. Bytes, and an optional MQCIH header for the CICS-MQ bridge. | The bridge can start a CICS program without an MQ-aware program, and it can report an abend. But it needs more code and IBM-specific message properties. |

The author selected A. The CICS-MQ bridge can be a later issue.

### 4.4 The correlation of the reply

| Option | Trade-off |
|---|---|
| **A. Message ID to correlation ID by default, and an option for the correlation ID (selected).** | The default is the usual MQ pattern: the server copies the message ID of the request to the correlation ID of the reply. The option `CORRELATION_ID` is for a server that copies the correlation ID of the request. |
| B. Message ID to correlation ID only. | Less code. A server that copies the correlation ID cannot be used. |

The author selected A.

## 5. Design

### 5.1 Module and packages

- Module: `bookmap-transport-mq`. Dependencies: `api(project(":bookmap-core"))` and `api("jakarta.jms:jakarta.jms-api:3.1.0")`. No other runtime dependency.
- Package `pt.dazz.bookmap.mq`: `MqTransport`, `MqRoute`, `MqCorrelation`, `MqConnector`, `MqSession`, `MqRequest`, `MqException`, `MqFailure`.
- Package `pt.dazz.bookmap.mq.jms`: `JmsConnector`, the adapter to Jakarta Messaging. It is the only class that names a `jakarta.jms` class.

`bookmap-core` and `bookmap-processor` do not change.

### 5.2 The connector interface

The transport does not call Jakarta Messaging. It calls this interface:

```java
public interface MqConnector {
    MqSession open() throws MqException;
}

public interface MqSession extends AutoCloseable {
    String send(MqRequest request) throws MqException;
    byte[] receive(String replyQueue, String correlationId, Duration wait) throws MqException;
    @Override void close() throws MqException;
}

public record MqRequest(String requestQueue, String replyQueue, byte[] payload,
                        String correlationId, Duration timeToLive, boolean persistent) { }

public final class MqException extends Exception {
    public MqException(MqFailure failure, String message, Throwable cause) { ... }
    public MqFailure failure() { ... }
}

public enum MqFailure { NOT_AUTHORIZED, INVALID_QUEUE, NOT_BYTES, OTHER }
```

- `open()` connects to the queue manager and opens one session. A session is used by one thread at a time.
- `send` puts the request message and returns its message ID. `replyQueue` is the reply-to queue of the message. `null` means the temporary queue of the session. `correlationId` is `null` in the mode `MESSAGE_ID`. `timeToLive` is the expiry of the message: `Duration.ZERO` means no expiry.
- `receive` waits for a maximum of `wait` for the message with the correlation ID `correlationId` on `replyQueue` (`null`: the temporary queue). It returns the bytes of the message, or `null` after the wait.
- `MqFailure` tells the type of a failure. The transport selects the BookMap exception from the failure and the step (see 5.5). `NOT_BYTES` means that the reply is not a bytes message.

A user can implement `MqConnector` for another client, for example a test fake or the IBM MQ classes for Java. `JmsConnector` is the implementation for Jakarta Messaging.

### 5.3 MqRoute, MqCorrelation, and the builder

```java
public record MqRoute(String requestQueue, String replyQueue) { }

public enum MqCorrelation { MESSAGE_ID, CORRELATION_ID }
```

- `requestQueue` is mandatory and not blank.
- `replyQueue` is optional: `null`, or not blank. If it is `null`, the transport uses the default reply queue of the builder. If the builder has no default reply queue, the transport uses a temporary queue.
- The constructor throws `NullPointerException` for a `null` request queue and `IllegalArgumentException` for a blank name.
- The transport does not check the length of a queue name. The provider checks it.

```java
MQConnectionFactory factory = new MQConnectionFactory();   // IBM MQ classes for Jakarta Messaging
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
```

| Method | Meaning | Default |
|---|---|---|
| `connector(MqConnector)` | The connector. Mandatory. | none |
| `route(String operation, String requestQueue)` | Adds a route with the default reply queue. | none |
| `route(String operation, String requestQueue, String replyQueue)` | Adds a route. | none |
| `routes(Map<String, MqRoute>)` | Adds all routes of the map. | none |
| `defaultReplyQueue(String)` | The reply queue for a route without a reply queue. | `null`: a temporary queue. |
| `correlation(MqCorrelation)` | The correlation mode. | `MESSAGE_ID` |
| `timeout(Duration)` | The maximum wait for the reply. It is also the expiry of the request message. | 30 seconds |
| `persistent(boolean)` | Sends persistent request messages. | `false` |
| `maxSessions(int)` | The maximum number of open sessions. | 10 |
| `sessionWaitTimeout(Duration)` | The maximum wait for a free session when all sessions are in use. | 30 seconds |

Rules:

- A temporary dynamic queue of IBM MQ does not accept persistent messages (2048, `MQRC_PERSISTENT_NOT_ALLOWED`). Many server programs copy the persistence of the request to the reply. Thus, `build()` throws `IllegalStateException` if `persistent(true)` is set and a route uses a temporary reply queue.
- A route for an operation replaces an earlier route for the same operation.
- `build()` throws `IllegalStateException` if the connector is not set or if no route is set.
- `timeout` throws `IllegalArgumentException` if the value is zero or negative. A reply wait must have a limit.
- `maxSessions` throws `IllegalArgumentException` if the value is less than 1. `sessionWaitTimeout` throws `IllegalArgumentException` if the value is negative.
- Each method throws `NullPointerException` for a `null` value, except `defaultReplyQueue`, where `null` resets the default.

### 5.4 One exchange

1. Find the route of `request.operation()`. If no route exists, throw `OperationNotFoundException`. The transport does not open a session.
2. Get a session from the pool (5.6).
3. In the mode `CORRELATION_ID`, make a new correlation ID: `ID:` and 48 hexadecimal characters of 24 random bytes. This is the format of a binary MQ correlation ID in Jakarta Messaging.
4. Call `MqSession.send` with the request queue, the reply queue, the payload, the correlation ID, the timeout as the time to live, and the persistence.
5. Select the correlation ID of the reply: the message ID that `send` returns (mode `MESSAGE_ID`), or the correlation ID of step 3 (mode `CORRELATION_ID`).
6. Call `MqSession.receive` with the reply queue, this correlation ID, and the timeout.
7. Give the session back to the pool, or close it (5.6).
8. If the reply is `null`, throw `TransportTimeoutException`. Otherwise, return the reply bytes.

`BookRequest.maxResponseLength()` is not used. The reply contains the bytes that the server program puts. A reply that is shorter than the response layout causes a `MappingException` in the codec (main design, 5.8).

**Late replies.** A reply that comes after the timeout stays on the reply queue. No later exchange reads it, because its correlation ID is different. The request expires after the timeout, so a server that reads the request late does not see it. A server that sets the expiry of the reply from the request removes most late replies. For a shared reply queue, the operator must remove old messages, or the server must set an expiry on the reply.

### 5.5 Failure translation

Each failure has one source and one exception:

| Source | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| No free session after `sessionWaitTimeout`. | `ConnectionException` |
| The thread is interrupted while it waits for a session. | `TransportException`. The interrupt flag stays set. |
| `open()` fails with `NOT_AUTHORIZED`. | `TransportException` |
| `open()` fails with another failure. | `ConnectionException` |
| `send` fails. The request can be on the queue: a persistent put can complete before the connection breaks. | `TransportException` |
| `send` returns a `null` or blank message ID, in the mode `MESSAGE_ID`. The transport does not call `receive`, because a reply without a correlation ID can belong to another request. | `TransportException` |
| `receive` returns `null`. | `TransportTimeoutException` |
| `receive` fails with `NOT_BYTES`. | `TransportException` |
| `receive` fails with another failure. The request can be complete on the server. | `TransportException` |

Each message gives the operation, the queue, and the failure, for example `GetInvoice (INVOICE.REQUEST): no reply after PT30S` or `GetInvoice (INVOICE.REQUEST): send failed (INVALID_QUEUE)`. The cause of the exception is the `MqException`. Its cause is the `JMSException`, with the MQ reason code in its message.

A security failure has no BookMap exception of its own, as in the CTG design. This design adds no exception to `bookmap-core`.

**The JMS exception types.** `JmsConnector` sets the `MqFailure` from the type of the `JMSException`:

| `JMSException` type | `MqFailure` |
|---|---|
| `JMSSecurityException` | `NOT_AUTHORIZED` |
| `InvalidDestinationException` | `INVALID_QUEUE` |
| Another type | `OTHER` |

The IBM MQ classes for Jakarta Messaging use subclasses of these standard types. For example, a connection that is not authorized gives a `JMSSecurityException` (`JMSWMQ2013`). Each failure to open a queue gives an `InvalidDestinationException` (`JMSWMQ2008`), for all reason codes: an unknown queue (2085) and a queue that the user cannot open (2035) give the same type. Thus, the transport does not tell an unknown queue from a queue that is not authorized. The message of the cause gives the reason code. A broken connection (2009, `MQRC_CONNECTION_BROKEN`) gives another type. The tests do not use the IBM library, so the tests cannot prove these IBM mappings. The documentation tells this.

### 5.6 Sessions, threads, and close

`MqTransport` implements `Transport` and `AutoCloseable`. It is thread-safe. The pool is the same as the connection pool of the CTG design.

- The transport keeps a pool of open sessions. A `Semaphore` with `maxSessions` permits limits the number of sessions in use. A queue holds the free sessions.
- To get a session, the transport waits for a permit, for a maximum of `sessionWaitTimeout`. Then it takes a free session from the queue, or it opens a new session.
- After an exchange that gives a reply, the session goes back to the queue. After a timeout, the session goes back to the queue only if the reply queue is a named queue. A session with a temporary queue is closed: this deletes the queue and the late reply on it.
- After an `MqException` from `send` or `receive`, the transport closes the session and ignores an `MqException` or a `RuntimeException` from `close`. The next exchange opens a new session. Thus, the transport connects again after a broken connection.
- The permit is always released.
- `close()` closes all free sessions and marks the transport closed. An `exchange` after `close()` throws `TransportException`. A session that is in use at `close()` is closed when its exchange ends. `close()` ignores an `MqException` or a `RuntimeException` of a session. A second `close()` does nothing.

The transport opens no session in `build()`. The first exchange opens the first session.

### 5.7 The adapter

`JmsConnector` has a builder:

| Method | Meaning | Default |
|---|---|---|
| `connectionFactory(ConnectionFactory)` | The connection factory of the provider. Mandatory. | none |
| `credentials(String user, String password)` | The user and the password for `createConnection`. | `null`: `createConnection()` without credentials. |
| `mqNativeMessages(boolean)` | Sends messages without an RFH2 header (see below). | `true` |

- `open()` creates a `Connection` with the credentials. It creates a `Session` without a transaction, with `AUTO_ACKNOWLEDGE`, and an anonymous `MessageProducer`. Then it starts the connection. Each `MqSession` has its own `Connection`, so a broken connection affects only one session. If a step fails, `open()` closes the connection.
- `send` creates a `BytesMessage` with the payload. It sets `JMSReplyTo` to the reply queue, and `JMSCorrelationID` if the correlation ID is not `null`. It sends the message with the delivery mode, the priority 4, and the time to live in milliseconds. It returns `getJMSMessageID()`.
- `receive` creates a `MessageConsumer` on the reply queue with the selector `JMSCorrelationID = '<id>'`, calls `receive(waitMillis)`, and closes the consumer. A `BytesMessage` gives its bytes. Another message type gives an `MqException` with `NOT_BYTES`.
- The session makes the temporary queue the first time that it needs it, and keeps it until `close`. The provider makes the temporary queue from its model queue. For IBM MQ, this is the property `TEMPMODEL` of the factory.
- `close` closes the connection. This also closes the session, the consumers, and the temporary queue.
- Each `JMSException` becomes an `MqException`, with the `MqFailure` of 5.5.

**Message format.** With `mqNativeMessages(true)`, the adapter gives each queue name to `Session.createQueue` in the form `queue:///NAME?targetClient=1`. With this property, the IBM MQ classes for Jakarta Messaging send a message without an RFH2 header, with the format `MQFMT_NONE`. The program gets only the book bytes. If a queue name starts with `queue://`, the adapter uses the name as it is. With `mqNativeMessages(false)`, the adapter uses each name as it is. Use `false` for a provider that is not IBM MQ.

**CAUTION:** Do not use `mqNativeMessages(false)` with IBM MQ, unless the server program reads the RFH2 header. If not, the program gets the RFH2 bytes in front of the book bytes, and the book is incorrect.

A reply with the format `MQFMT_NONE` comes as a `BytesMessage`, without a character conversion. A reply with the format `MQSTR` comes as a `TextMessage`. The adapter does not accept it (`NOT_BYTES`).

### 5.8 User setup

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-mq:<version>")
    runtimeOnly("com.ibm.mq:com.ibm.mq.jakarta.client:9.4.3.0")   // IBM license
}
```

The user creates the `MQConnectionFactory` in the application code. Thus, the application also needs the IBM library at compile time: `implementation` instead of `runtimeOnly`. The documentation tells the license of the IBM library and the factory properties: host, port, queue manager, channel, and transport type.

## 6. Tests

- `MqRouteTest`: the rules of 5.3.
- `MqTransportTest`, with a fake `MqConnector`: each row of the table of 5.5, the values in `MqRequest`, the two correlation modes, the reply queue selection (route, default, temporary), the builder rules (also the rule for persistent messages and a temporary queue), the pool (reuse, the limit, the wait timeout, the discard after a failure, the interrupt), and `close`.
- `MqTransportClientTest`: a `BookmapClient` with a generated codec, a fake connector, and a full call.
- `JmsConnectorTest`, with an embedded Apache ActiveMQ Artemis broker (in-VM, no network, no persistence): a request and a reply from a small server thread in the test, both correlation modes, a named reply queue and a temporary queue, the selector (a reply for another correlation ID stays on the queue), the timeout, a `TextMessage` reply (`NOT_BYTES`), the time to live, the credentials, and `close`.
- `JmsConnectorTest`, for the failure types: a `JMSSecurityException`, an `InvalidDestinationException`, and another `JMSException` give the correct `MqFailure`. The test makes these exceptions directly, and with the broker where possible (security on, and a queue that does not exist with the automatic creation of queues off).
- `JmsConnectorTest`, for the queue names: `mqNativeMessages(true)` gives `queue:///NAME?targetClient=1`, a name that starts with `queue://` does not change, and `false` gives the name as it is. Artemis does not accept the IBM form, so this test calls the name function directly.

No test uses a real queue manager or the IBM library.

## 7. Changes outside the module

- `settings.gradle.kts`: include `bookmap-transport-mq`.
- `build.gradle.kts`: add the module to `publishedModules`, with the description "BookMap transport for IBM MQ (Jakarta Messaging request and reply)."
- `gradle/libs.versions.toml`: `jakarta-jms-api` 3.1.0, and the Artemis test libraries (`artemis-jakarta-server`, `artemis-jakarta-client`).
- `gradle/verification-metadata.xml`: run `scripts/update-verification-metadata.sh`.
- `docs/wiki/Contributing.md`, `RELEASING.md`, and the release workflow: add the module to the list of published modules, if the workflow has a list.
- `docs/wiki/IBM-MQ.md`: a new page. `_Sidebar.md` and `Home.md`: a link to the page.
- `docs/wiki/Transports-and-simulator.md` and `docs/wiki/Getting-started.md`: name the new transport.
- `README.md`: the module list and the "Project status" section.
- `ROADMAP.md`: remove #27.
- `bookmap-core` and `bookmap-processor`: no change.

## 8. Calls made by the builder of this design

These points were not decided by the author. They are proposals of this design:

- Each pooled session has its own JMS connection. This keeps a broken connection to one session, and the pool connects again.
- Only a missing route gives `OperationNotFoundException` (main design, 5.9). A queue that the transport cannot open gives `TransportException`, because IBM MQ gives the same exception type for an unknown queue and for a queue that is not authorized.
- A security failure is a `TransportException`, as in the CTG design.
- Only a failure of `open()` or of the session pool is a `ConnectionException`: then the request was not sent, and a retry is safe. A failure at `send` or `receive` is a `TransportException`, because the request can be on the queue or complete on the server.
- The timeout is also the expiry of the request message.
- The default timeout is 30 seconds, and the default pool size is 10, as in the CTG design.
- Non-persistent request messages by default. Request and reply traffic is usually non-persistent, and a temporary reply queue does not accept persistent messages.
- `mqNativeMessages(true)` by default, because the module is for IBM MQ.
- One queue manager and one set of credentials for each transport. The credentials are in the connector, not in the transport.
- The pool code is a copy of the pool of the CTG transport, not a shared module. `bookmap-core` does not change, and the two transports are on separate branches. A shared internal module can come later.
- The tests of the adapter use an embedded Artemis broker. It is a test dependency only, and it does not go into the POM.

## 9. Left to the implementation

- The exact text of the messages, in the format of 5.5.
- The test fakes and the structure of the broker test.
- The Javadoc of the public types.
