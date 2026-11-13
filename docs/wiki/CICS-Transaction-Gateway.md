`bookmap-transport-ctg` is a transport for the CICS Transaction Gateway (CTG). It calls a CICS program with the External Call Interface (ECI).
The request bytes go to the program in a COMMAREA. The transport returns the COMMAREA that the program gives back.

The module contains no IBM code. Add the IBM client library `ctgclient.jar` to your application.
For the other transports and a comparison, see [Transports and simulator](Transports-and-simulator#choose-a-transport).

## Setup

Get `ctgclient.jar` from your CICS TG installation or from the CICS TG client package. BookMap cannot put it on Maven Central, because it has an IBM license.
Put the jar in a repository of your company, or in a folder of your project.

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-ctg:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
    runtimeOnly(files("libs/ctgclient.jar"))   // or the artifact in your company repository
}
```

```xml
<dependency>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-transport-ctg</artifactId>
  <version>${bookmap.version}</version>
</dependency>
<!-- The IBM library, from the repository of your company. Use your own coordinates. -->
<dependency>
  <groupId>com.example.ibm</groupId>
  <artifactId>ctgclient</artifactId>
  <version>9.3</version>
  <scope>runtime</scope>
</dependency>
```

**CAUTION:** Add `ctgclient.jar` to the runtime class path. If it is missing, the first call throws `NoClassDefFoundError`.

## Create the transport

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

BookmapClient client = BookmapClient.builder()
    .transport(transport)
    .build();
```

Use one transport for all threads. Close the transport when the application stops. `close()` closes the gateway connections.

### Routes

A route maps an operation name to a CICS program and a transaction. The operation name is the value of `@BookOperation`, or the simple class name.

| Method | Meaning |
|---|---|
| `route(operation, program)` | Adds a route with the default transaction. |
| `route(operation, program, transaction)` | Adds a route with a transaction. |
| `routes(Map<String, CtgRoute>)` | Adds all routes of the map. |

- The program name has 1 to 8 characters. The transaction name has 1 to 4 characters.
- A route for an operation replaces an earlier route for the same operation.
- If an operation has no route, `exchange` throws `OperationNotFoundException`. The transport does not call CTG.

### The builder of the transport

| Method | Meaning | Default |
|---|---|---|
| `connector(EciConnector)` | The connector. Mandatory. | none |
| `server(String)` | The name of the CICS server in the CTG configuration. | `null`: the default server of CTG. |
| `defaultTransaction(String)` | The transaction for a route without a transaction. | `null`: the mirror transaction of CICS. |
| `credentials(String userid, String password)` | The CICS user ID and password. | `null`: no credentials. |
| `timeout(Duration)` | The ECI timeout, in whole seconds from 0 to 32767. `Duration.ZERO` means no timeout. | 30 seconds |
| `maxConnections(int)` | The maximum number of open gateway connections. | 10 |
| `connectionWaitTimeout(Duration)` | The maximum wait for a free connection when all connections are in use. Zero or more. | 30 seconds |

`build()` throws `IllegalStateException` if the connector is not set or if no route is set. It opens no connection.

### The builder of the connector

`JavaGatewayConnector` opens a connection with `com.ibm.ctg.client.JavaGateway`.

| Method | Meaning | Default |
|---|---|---|
| `url(String)` | The URL of the gateway, for example `tcp://ctg.example.com` or `ssl://ctg.example.com`. `local:` selects local mode. Mandatory. | none |
| `port(int)` | The port of the Gateway daemon. | 2006 |
| `socketConnectTimeout(Duration)` | The time limit to connect the socket, from 0 to `Integer.MAX_VALUE` milliseconds. | 10 seconds |
| `protocolProperties(Properties)` | The protocol properties, for example the key ring for TLS. The builder keeps a copy. | empty |

## The COMMAREA

- The COMMAREA length is the larger of the request length and the maximum response length of the book.
- The maximum COMMAREA length is 32,500 bytes. CTG can send this length with all protocols. If the length is more, `exchange` throws `TransportException` before the call.
- CTG sends only the request bytes. CICS gets a COMMAREA of the full length.
- The transport gets the full COMMAREA back. CTG does not remove the trailing `X'00'` bytes. Thus, a response that ends with a binary or packed zero has the correct length.

Channels and containers are not supported.

## Errors

The transport translates each failure to a BookMap exception:

| Failure | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| The COMMAREA is longer than 32,500 bytes. | `TransportException` |
| No free connection after `connectionWaitTimeout`. | `ConnectionException` |
| The connection to the gateway cannot be opened. | `ConnectionException` |
| A failure during the call. CICS can have run the program. | `TransportException` |
| `ECI_ERR_TRANSACTION_ABEND` (-7) with the abend `AEI0`: CICS does not know the program. | `OperationNotFoundException` |
| `ECI_ERR_TRANSACTION_ABEND` (-7) with another abend. | `RemoteAbendException` with the abend code |
| `ECI_ERR_REQUEST_TIMEOUT` or `ECI_ERR_NO_REPLY` (-5), `ECI_ERR_RESPONSE_TIMEOUT` (-6). | `TransportTimeoutException` |
| `ECI_ERR_NO_CICS` (-3), `ECI_ERR_CICS_DIED` (-4), `ECI_ERR_RESOURCE_SHORTAGE` (-16), `ECI_ERR_NO_SESSIONS` (-17), `ECI_ERR_UNKNOWN_SERVER` (-22), `ECI_ERR_NO_SYSTEMS` (-26), `ECI_ERR_MAX_SYSTEMS` (-28), `ECI_ERR_MAX_SESSIONS` (-29). | `ConnectionException` |
| `ECI_ERR_SECURITY_ERROR` (-27) and all other return codes. | `TransportException` |

The message gives the operation, the program, the return code, and its name. For example: `GetInvoice (PGMINV01): ECI return code -22 (ECI_ERR_UNKNOWN_SERVER)`.

**WARNING:** Do not retry a call after a `TransportException` from a failure during the call, or after a timeout, without a check. CICS can have run the program and changed data.

## Spring Boot

`bookmap-spring-boot` creates the transport from properties. Set `bookmap.transport.type=ctg` and the `bookmap.transport.ctg.*` properties.
See [Spring Boot](Spring-Boot#cics-transaction-gateway).

## Test without CICS

Use the [simulator](Transports-and-simulator#the-simulator) in tests. It uses the same book classes.
To test your CTG configuration code, give the builder a fake `EciConnector`. An `EciConnector` opens an `EciConnection`, and `EciConnection.call(EciCall)` gives an `EciReply`.
