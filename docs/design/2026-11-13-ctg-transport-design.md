# BookMap CICS Transaction Gateway transport: design

Date: 2026-11-13
Status: implemented in v0.8.0.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (the main design, sections 2.2, 5.9, and 5.10), `CLAUDE.md` (project rules), GitHub issue #26.

## 1. Intent

BookMap has a transport SPI and an in-memory simulator. It has no transport to a real CICS system.
This design adds the first real transport: a call to a CICS program through the CICS Transaction Gateway (CTG), with the External Call Interface (ECI) and a COMMAREA.

A user adds the module `bookmap-transport-ctg` and the IBM client library `ctgclient.jar`. The user maps each operation name to a CICS program and a transaction. Then the user gives the transport to a `BookmapClient`. The application code does not change: it uses the same book classes as with the simulator.

The MQ transport (#27) and the HTTP transport (#28) come later. The Spring Boot auto-configuration of the transports is a separate issue (#29).

## 2. Goals, non-goals, and anti-goals

Goals:

- A `Transport` that sends the request bytes as a COMMAREA to a CICS program through a CTG Gateway daemon (remote mode) or a local CTG client (local mode), and returns the COMMAREA that the program gives back.
- A mapping from operation name to program and transaction, set in a builder.
- A translation of each CTG failure to one of the BookMap exceptions of section 5.10 of the main design.
- A thread-safe transport, with a bounded pool of gateway connections.
- 100% line and branch coverage without a real CICS system and without the IBM library.
- Publication to Maven Central, with no IBM dependency in the POM.

Non-goals (possible later):

- Channels and containers. The COMMAREA limit of 32,500 bytes applies.
- Extended logical units of work (`ECI_EXTENDED`), asynchronous ECI calls, and XA transactions.
- The JCA resource adapter (`cicseci.rar`) and the Jakarta EE connection factory.
- A different program or CICS server for each request attribute. `BookRequest.attributes()` is not read.
- Spring Boot properties and auto-configuration (#29).
- A native image test of this transport. The IBM library is not available in the build.

Anti-goals (failure, even if the code works):

- `bookmap-core` or `bookmap-processor` depends on the CTG library, or contains a CICS concept.
- A published BookMap artifact contains IBM code, or the POM of a published artifact names an IBM artifact.
- A CTG exception or an ECI return code goes to the caller without a translation to a BookMap exception.
- Silent data loss: the transport returns a COMMAREA without the trailing `X'00'` bytes of a binary or packed field.

## 3. Constraints

- The IBM client library `ctgclient.jar` is IBM-licensed. It is not on Maven Central: no artifact has the group `com.ibm.ctg`, and the group `com.ibm.cics` has no CTG artifact (checked on 2026-11-13). The build cannot download it.
- The API in this design comes from the CICS TG V9.3 Javadoc of `com.ibm.ctg.client` (`JavaGateway`, `ECIRequest`, `ECIReturnCodes`). The same classes exist in V9.1 and V9.2.
- The rules of `CLAUDE.md`: Java 21, no reflection at runtime, 100% line and branch coverage, English and ASD-STE100 documentation.
- Dependency verification is on. This design adds no external dependency.

## 4. Approaches

The author decided four points. For each point, this section gives the options and the decision.

### 4.1 Compile and test without the IBM library

| Option | Trade-off |
|---|---|
| **A. A BookMap interface, an adapter, and a stub (selected).** | The transport uses a small BookMap interface (`EciConnector`). All the logic is tested with fakes of that interface. One adapter class calls `JavaGateway` and `ECIRequest`. It compiles against a stub of these IBM classes. The stub is in the repository and is never published. The user adds the real `ctgclient.jar` at runtime. |
| B. The interface only. | No IBM class name in BookMap. But each user must write and test the adapter. |
| C. Compile against the real jar from a local path. | CI and other contributors cannot build the module. |

The author selected A.

### 4.2 Publication

The author decided to publish `bookmap-transport-ctg` to Maven Central with the other modules. The POM has no IBM dependency. The jar contains no IBM code. Rejected: no publication until a test on a real CICS system.

### 4.3 The mapping from operation name to program and transaction

| Option | Trade-off |
|---|---|
| **A. Routes in the builder (selected).** | `route("GetCustomerOrders", "PGMORD01", "TX01")`, and `routes(Map<String, CtgRoute>)`. Plain Java. A later Spring Boot binding (#29) can give the map. |
| B. An annotation on the book class. | It puts a CICS concept on the POJO. The main design rejects this (section 2.3). |
| C. A properties file. | The module must load and parse a file. |

The author selected A.

### 4.4 The data area

The author selected the COMMAREA only. Rejected for this version: the COMMAREA and a channel with one container. A channel has no 32 KB limit, but it needs a container name for each route and more stub API.

## 5. Design

### 5.1 Module and packages

- Module: `bookmap-transport-ctg`. Dependencies: `api(project(":bookmap-core"))`. No other runtime dependency.
- Package `pt.dazz.bookmap.ctg`: `CtgTransport`, `CtgRoute`, `EciConnector`, `EciConnection`, `EciCall`, `EciReply`.
- Package `pt.dazz.bookmap.ctg.ibm`: `JavaGatewayConnector`, the adapter to the IBM library. It is the only class that names an IBM class.
- Source set `ctgStub` of the same module: the stub of `com.ibm.ctg.client`. See 5.7.

`bookmap-core` and `bookmap-processor` do not change.

### 5.2 The connector interface

The transport does not call the IBM library. It calls this interface:

```java
public interface EciConnector {
    EciConnection open() throws IOException;
}

public interface EciConnection extends AutoCloseable {
    EciReply call(EciCall call) throws IOException;
    @Override void close() throws IOException;
}

public record EciCall(String server, String userid, String password, String program,
                      String transaction, byte[] commarea, int commareaLength,
                      int timeoutSeconds) { }

public record EciReply(int returnCode, String abendCode, byte[] commarea) { }
```

- `server`, `userid`, `password`, and `transaction` can be `null`. CTG then uses its defaults: the default CICS server, no credentials, and the default mirror transaction.
- `commarea` contains the request bytes. `commareaLength` is the size of the COMMAREA that CICS gets.
- `timeoutSeconds` is the ECI timeout, from 0 to 32767. `0` means no ECI timeout.
- The constructor throws `IllegalArgumentException` if the request bytes are longer than `commareaLength`, or if the timeout is out of range.
- `toString()` does not show the password or the request bytes.
- `returnCode` is the value of `ECIRequest.getRc()`. `abendCode` is the field `Abend_Code`. `commarea` is the COMMAREA after the call.

A user can implement `EciConnector` for another client, for example a test fake. `JavaGatewayConnector` is the implementation for the IBM library.

### 5.3 CtgRoute and the builder

```java
public record CtgRoute(String program, String transaction) { }
```

- `program` is mandatory: 1 to 8 characters, not blank.
- `transaction` is optional: `null`, or 1 to 4 characters, not blank. If it is `null`, the transport uses the default transaction of the builder.
- The constructor throws `NullPointerException` for a `null` program and `IllegalArgumentException` for an incorrect length.

```java
CtgTransport transport = CtgTransport.builder()
    .connector(JavaGatewayConnector.builder()
        .url("tcp://ctg.example.com")
        .port(2006)
        .build())
    .server("CICSPROD")
    .credentials("ORDUSER", password)
    .route("GetCustomerOrders", "PGMORD01", "TX01")
    .route("GetInvoice", "PGMINV01")
    .build();
```

| Method | Meaning | Default |
|---|---|---|
| `connector(EciConnector)` | The connector. Mandatory. | none |
| `route(String operation, String program)` | Adds a route with the default transaction. | none |
| `route(String operation, String program, String transaction)` | Adds a route. | none |
| `routes(Map<String, CtgRoute>)` | Adds all routes of the map. | none |
| `server(String)` | The name of the CICS server in the CTG configuration. | `null`: the default server of CTG. |
| `defaultTransaction(String)` | The transaction for a route without a transaction. 1 to 4 characters. | `null`: the mirror transaction of CICS. |
| `credentials(String userid, String password)` | The user ID and the password for CICS. | `null`: no credentials. |
| `timeout(Duration)` | The ECI timeout, in whole seconds. `Duration.ZERO` means no timeout. | 30 seconds |
| `maxConnections(int)` | The maximum number of open gateway connections. | 10 |
| `connectionWaitTimeout(Duration)` | The maximum wait for a free connection when all connections are in use. | 30 seconds |

Rules:

- A route for an operation replaces an earlier route for the same operation.
- `build()` throws `IllegalStateException` if the connector is not set or if no route is set.
- `timeout` throws `IllegalArgumentException` if the value is negative, has a fraction of a second, or is more than `Short.MAX_VALUE` seconds. The ECI timeout of CTG is a `short`.
- `maxConnections` throws `IllegalArgumentException` if the value is less than 1.
- Each method throws `NullPointerException` for a `null` value, except `server`, `defaultTransaction`, and `credentials`, where `null` resets the default.

### 5.4 One exchange

1. Find the route of `request.operation()`. If no route exists, throw `OperationNotFoundException`. The transport does not open a connection.
2. Calculate the COMMAREA length: the larger of `payload.length` and `maxResponseLength`. If the length is more than 32,500 bytes, throw `TransportException`. The message gives the operation, the length, and the limit. The transport does not open a connection.
3. Get a connection from the pool (5.6).
4. Call `EciConnection.call` with the route, the builder values, the payload, and the COMMAREA length.
5. Give the connection back to the pool, or close it (5.6).
6. If the return code is `0`, return the COMMAREA of the reply. Otherwise, translate the return code (5.5).

The response is the full COMMAREA, with the length of step 2. The codec ignores the bytes after the response layout (main design, 5.8).

**COMMAREA lengths in the adapter.** The adapter sets `Commarea_Length` to the COMMAREA length. It sets the outbound length to `payload.length`: CTG sends only the request bytes, and CICS gets a COMMAREA of the full length. The adapter sets the inbound length to the COMMAREA length. Thus, CTG does not remove the trailing `X'00'` bytes of the response ("null stripping"). Without this, a response that ends with a binary or packed zero would be too short.

### 5.5 Failure translation

Each failure has one source and one exception:

| Source | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| The COMMAREA length is more than 32,500 bytes. | `TransportException` |
| No free connection after `connectionWaitTimeout`. | `ConnectionException` |
| `EciConnector.open()` throws `IOException`. | `ConnectionException` |
| `EciConnection.call` throws `IOException` or an unchecked exception. The request can be complete in CICS. | `TransportException` |
| The thread is interrupted while it waits for a connection. | `TransportException`. The interrupt flag stays set. |
| `ECI_ERR_TRANSACTION_ABEND` (-7) with the abend code `AEI0` (`PGMIDERR`: CICS does not know the program). | `OperationNotFoundException` |
| `ECI_ERR_TRANSACTION_ABEND` (-7) with another abend code. | `RemoteAbendException(abendCode)` |
| `ECI_ERR_REQUEST_TIMEOUT` and `ECI_ERR_NO_REPLY` (-5), `ECI_ERR_RESPONSE_TIMEOUT` (-6). | `TransportTimeoutException` |
| `ECI_ERR_NO_CICS` (-3), `ECI_ERR_CICS_DIED` (-4), `ECI_ERR_RESOURCE_SHORTAGE` (-16), `ECI_ERR_NO_SESSIONS` (-17), `ECI_ERR_UNKNOWN_SERVER` (-22), `ECI_ERR_NO_SYSTEMS` (-26), `ECI_ERR_MAX_SYSTEMS` (-28), `ECI_ERR_MAX_SESSIONS` (-29). | `ConnectionException` |
| `ECI_ERR_SECURITY_ERROR` (-27). | `TransportException` |
| Any other non-zero return code. | `TransportException` |

Each message gives the operation, the program, the return code, and the name of the return code, for example `GetInvoice (PGMINV01): ECI return code -22 (ECI_ERR_UNKNOWN_SERVER)`. The transport has a table of the names of the codes in this design. For another code, the name is `UNKNOWN`. For an abend, the message ends with the abend code, for example `, abend ASRA`.

The return code values come from `ECIReturnCodes` of CICS TG V9.3. The transport uses its own constants, because it does not compile against the IBM library. A test checks that the stub constants have the same values as the transport constants.

A security failure has no BookMap exception of its own. This design adds no exception to `bookmap-core`.

### 5.6 Connections, threads, and close

`CtgTransport` implements `Transport` and `AutoCloseable`. It is thread-safe.

- The transport keeps a pool of open connections. A `Semaphore` with `maxConnections` permits limits the number of connections in use. A queue holds the free connections.
- To get a connection, the transport waits for a permit, for a maximum of `connectionWaitTimeout`. Then it takes a free connection from the queue, or it opens a new connection.
- After a call that gives a reply, the connection goes back to the queue. This is also true when the return code is not `0`: the connection to the gateway is still correct.
- After an exception from `call`, the transport closes the connection and ignores a second `IOException` from `close`. The next exchange opens a new connection.
- The permit is always released, also after an unchecked exception from `open()` or `call`, for example a `NoClassDefFoundError` when `ctgclient.jar` is missing.
- `close()` closes all free connections and marks the transport closed. An `exchange` after `close()` throws `TransportException`. A connection that is in use at `close()` is closed when its exchange ends. `close()` ignores an `IOException` of a connection. A second `close()` does nothing.

The transport opens no connection in `build()`. The first exchange opens the first connection.

### 5.7 The adapter and the stub

`JavaGatewayConnector` has a builder:

| Method | Meaning | Default |
|---|---|---|
| `url(String)` | The URL of the Gateway daemon, for example `tcp://ctg.example.com` or `ssl://ctg.example.com`. `local:` selects local mode. Mandatory. | none |
| `port(int)` | The port of the Gateway daemon. | 2006 |
| `socketConnectTimeout(Duration)` | The time limit to connect the socket. | 10 seconds |
| `protocolProperties(Properties)` | The protocol properties, for example the key ring for TLS. A copy is kept. | empty |

- `open()` calls `new JavaGateway(url, port, socketConnectTimeoutMillis, protocolProperties)`. This constructor opens the connection.
- `call` creates `new ECIRequest(ECI_SYNC, server, userid, password, program, transaction, commarea, commareaLength, ECI_NO_EXTEND, 0)`. It sets the ECI timeout and the two COMMAREA lengths (5.4). Then it calls `JavaGateway.flow` and returns `EciReply(request.getRc(), request.Abend_Code, request.Commarea)`.
- `close` calls `JavaGateway.close()`.
- The adapter has no condition. All the decisions are in `CtgTransport`.

**The stub.** The source set `ctgStub` contains the classes `com.ibm.ctg.client.JavaGateway`, `GatewayRequest`, `ECIRequest`, and `ECIReturnCodes`, with only the members that the adapter uses. The signatures and the constant values come from the V9.3 Javadoc. The stub contains no IBM code.

- The main source set has `compileOnly(sourceSets["ctgStub"].output)`. The test source set has the stub on its runtime classpath.
- The stub is a programmable fake. A test sets the reply or the `IOException` of the next `flow`, and reads the last `ECIRequest`.
- The published jar contains only the main classes. The POM has no IBM artifact, because a `compileOnly` dependency does not go into the POM. A test of the build checks the content of the jar (see 6).
- The JaCoCo report and the coverage rule use the main source set only. The stub is not measured.

**WARNING:** Do not change a stub signature or a stub constant without a check against the IBM Javadoc. The compiler copies the constant values into the adapter. An incorrect value or signature causes a wrong translation or a `NoSuchMethodError` with the real library.

### 5.8 User setup

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-ctg:<version>")
    runtimeOnly(files("libs/ctgclient.jar"))   // or the artifact in your internal repository
}
```

The user gets `ctgclient.jar` from the CICS TG installation or the CICS TG SupportPac. If the library is missing at runtime, the first `open()` throws `NoClassDefFoundError`. The documentation tells this.

## 6. Tests

- `CtgRouteTest`: the rules of 5.3.
- `CtgTransportTest`, with a fake `EciConnector`: each row of the table of 5.5, the COMMAREA length and the limit, the values in `EciCall`, the builder rules, the pool (reuse, the limit, the wait timeout, the discard after `IOException`, the interrupt), and `close`.
- `CtgTransportClientTest`: a `BookmapClient` with a generated codec, a fake connector, and a full call with a response that ends with `X'00'` bytes.
- `JavaGatewayConnectorTest`, with the stub: the constructor arguments, the `ECIRequest` fields, the timeout, the two COMMAREA lengths, the reply, and `close`.
- `EciReturnCodesTest`: the transport constants are equal to the stub constants.
- A check in the build: the jar of the module contains no `com/ibm/` entry.

No test uses a real CICS system.

## 7. Changes outside the module

- `settings.gradle.kts`: include `bookmap-transport-ctg`.
- `build.gradle.kts`: add the module to `publishedModules`, with the description "BookMap transport for the CICS Transaction Gateway (ECI)."
- `docs/wiki/Contributing.md` and `RELEASING.md`: add the module to the list of published modules.
- `docs/wiki/CICS-Transaction-Gateway.md`: a new page. `_Sidebar.md` and `Home.md`: a link to the page.
- `docs/wiki/Transports-and-simulator.md` and `docs/wiki/Getting-started.md`: name the new transport.
- `README.md`: the module list and the "Project status" section.
- `ROADMAP.md`: remove #26.
- `bookmap-core` and `bookmap-processor`: no change.
- `gradle/verification-metadata.xml`: no change is expected. Run `scripts/update-verification-metadata.sh` to make sure.

## 8. Calls made by the builder of this design

These points were not decided by the author. They are proposals of this design:

- The `AEI0` abend becomes `OperationNotFoundException`, not `RemoteAbendException`.
- A security error (-27) is a `TransportException`, not a new exception class.
- An `IOException` during `flow` is a `TransportException`, not a `ConnectionException`, because the request can be complete in CICS.
- The default ECI timeout is 30 seconds, and the default pool size is 10.
- The limit of 32,500 bytes is fixed. It is the size that IBM guarantees for all protocols.
- One CICS server and one set of credentials for each transport. To use two servers, create two transports.

## 9. Left to the implementation

- The exact text of the messages, in the format of 5.5.
- The test fakes and the structure of the stub.
- The Javadoc of the public types.
