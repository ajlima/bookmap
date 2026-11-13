# BookMap HTTP transport: design

Date: 2026-11-13
Status: proposed.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (the main design, sections 2.2, 5.9, and 5.10), `docs/design/2026-11-13-ctg-transport-design.md` (the CTG transport, #26), `CLAUDE.md` (project rules), GitHub issue #28.

## 1. Intent

BookMap has a transport SPI and an in-memory simulator. This design adds a transport that sends the request bytes of a book to an HTTP endpoint and returns the bytes of the HTTP response.

The endpoint receives and sends the raw COBOL bytes of the book. Examples are a CICS web service with a binary body, a custom HTTP program in CICS, or an API gateway in front of the mainframe that accepts a binary body.

A user adds the module `bookmap-transport-http`. The user maps each operation name to a URL, and optionally to an HTTP method and headers. Then the user gives the transport to a `BookmapClient`. The application code does not change: it uses the same book classes as with the simulator.

The module uses only the JDK HTTP client (`java.net.http`). It has no external runtime dependency.

## 2. Goals, non-goals, and anti-goals

Goals:

- A `Transport` that sends the request bytes as the body of an HTTP request and returns the body of the response.
- A mapping from operation name to a URL, a method, and headers, set in a builder.
- Authentication with headers: fixed headers, a header supplier that the transport calls for each request, and a basic authentication shortcut.
- Mutual TLS, a proxy, and other client settings through an `HttpClient` that the caller supplies.
- A translation of each HTTP failure to one of the BookMap exceptions of section 5.10 of the main design.
- A thread-safe transport.
- 100% line and branch coverage, with an in-process HTTP server of the JDK. No test dependency is added.
- Publication to Maven Central.

Non-goals (possible later):

- A JSON body. Standard z/OS Connect APIs expect JSON and map it to the COMMAREA on the server. This transport does not convert a book to JSON.
- A pluggable body codec, for example base64 in a JSON field.
- A retry by the transport.
- OAuth flows, token caches, and other identity provider logic. The header supplier can give a token that the application gets.
- A different URL or header for each request attribute. `BookRequest.attributes()` is not read.
- An asynchronous call (`sendAsync`).
- Spring Boot properties and auto-configuration (#29).
- A native image test of this transport.

Anti-goals (failure, even if the code works):

- `bookmap-core` or `bookmap-processor` changes, or depends on an HTTP library.
- The module adds a runtime dependency that is not in the JDK.
- An `IOException` or another JDK exception goes to the caller without a translation to a BookMap exception.
- A message of an exception contains a password, a token, the `Authorization` header, or the user information or the query of the URL.
- The transport sends a request two times. A CICS program is not always idempotent.

## 3. Constraints

- Java 21. The module uses `java.net.http.HttpClient`. In Java 21, `HttpClient` implements `AutoCloseable`.
- The project has no `module-info.java`. The module needs no `requires` clause.
- The rules of `CLAUDE.md`: no reflection at runtime, 100% line and branch coverage, English and ASD-STE100 documentation.
- Dependency verification is on. This design adds no external dependency. `com.sun.net.httpserver` is in the JDK (module `jdk.httpserver`).
- Behavior of the JDK 21 client, checked in the source of `jdk.internal.net.http.MultiExchange`:
  - A refused connection and an unknown host both give `java.net.ConnectException`.
  - After a `ConnectException`, the client tries the connection one more time. The request was not sent. The system property `jdk.httpclient.disableRetryConnect` stops this.
  - After a failure on a pooled connection that the server closed, the client sends the request again only for `GET` and `HEAD`. The system property `jdk.httpclient.enableAllMethodRetry` extends this to all methods. The default method of this transport is `POST`.
  - `HttpRequest.Builder.header` throws `IllegalArgumentException` for a restricted header, for example `Host`, `Connection`, or `Content-Length`.

## 4. Approaches

The author decided three points. For each point, this section gives the options and the decision.

### 4.1 The body of the request and the response

| Option | Trade-off |
|---|---|
| **A. A binary body (selected).** | The transport sends the request bytes unchanged, with `Content-Type: application/octet-stream`. It returns the response body unchanged. It works with an endpoint that accepts the COBOL bytes. It does not work with a standard z/OS Connect JSON API. |
| B. A binary body and a pluggable body codec. | A small interface converts the bytes to a body and back, for example base64 in a JSON field. More API and tests. |
| C. A JSON mapping of the book fields. | A standard z/OS Connect API works. It needs a JSON writer and field names in JSON, and changes near the codec layer. |

The author selected A.

### 4.2 Authentication

| Option | Trade-off |
|---|---|
| **A. Headers and a caller-supplied client (selected).** | Fixed headers for the transport and for each route, a header supplier for each request, a `basicAuth` shortcut, and an optional `HttpClient` from the caller for mutual TLS, a proxy, and the HTTP version. |
| B. Basic authentication and a bearer token only. | Less API. No mutual TLS and no custom trust store. |
| C. A header supplier and an `SSLContext`. | The transport always creates its own client. No proxy or HTTP version setting. |

The author selected A.

### 4.3 Failure translation and retry

| Option | Trade-off |
|---|---|
| **A. A fixed map, without retry (selected).** | One table maps each status and each JDK exception to a BookMap exception. An optional response header gives an abend code. The transport never sends a request two times. |
| B. A fixed map, with a retry. | A retry of a connection failure and of `502` or `503`. More configuration. A retry after `502` can run the program two times. |
| C. A pluggable mapper. | The user maps a status, headers, and a body to an exception. More API. |

The author selected A.

### 4.4 The mapping from operation name to URL

This point follows the CTG design (4.3 there): routes in the builder, with `route(...)` and `routes(Map)`. A later Spring Boot binding (#29) can give the map. An annotation on the book class is rejected: it puts a transport concept on the POJO (main design, 2.3).

### 4.5 The scope of the timeout

The request timeout of the JDK client stops when the response headers arrive. A server that sends the headers and then stops before the end of the body blocks the caller without a limit.

| Option | Trade-off |
|---|---|
| **A. One deadline for the whole exchange (selected).** | The transport sends with `sendAsync` and waits for the full response for a maximum of `timeout`. Then it cancels the request. A little more code and one more test. |
| B. The headers only. | The behavior of the JDK. The documentation tells that a stopped body has no limit. |

The author selected A.

## 5. Design

### 5.1 Module and package

- Module: `bookmap-transport-http`. Dependencies: `api(project(":bookmap-core"))`. No other runtime dependency.
- Package `pt.dazz.bookmap.http`: `HttpTransport` and `HttpRoute`, both public. `HttpSender`, package-private (see 5.7).

`bookmap-core` and `bookmap-processor` do not change.

### 5.2 HttpRoute

```java
public record HttpRoute(URI uri, String method, Map<String, String> headers) {
    public static HttpRoute of(String uri);
    public static HttpRoute of(String uri, String method);
}
```

- `uri` is mandatory. It is an absolute `http` or `https` URI, or a relative reference. `build()` resolves a relative reference against the base URI of the builder.
- `method` is optional. `null` means `POST`. A value must be an HTTP token, for example `POST` or `PUT`. The transport does not change the case.
- `headers` is optional. `null` means no headers. The record keeps an unmodifiable copy.
- The constructor throws `NullPointerException` for a `null` URI, and `IllegalArgumentException` for an incorrect method or a header that 5.4 rejects.
- `of(String)` and `of(String, String)` call `URI.create`. An incorrect URI gives `IllegalArgumentException`.

### 5.3 The builder

```java
HttpTransport transport = HttpTransport.builder()
    .baseUri("https://gateway.example.com/orders/")
    .basicAuth("ORDUSER", password)
    .header("X-Client-Id", "order-service")
    .route("GetCustomerOrders", "customer-orders")
    .route("GetInvoice", HttpRoute.of("https://billing.example.com/invoices", "PUT"))
    .abendHeader("X-Abend-Code")
    .timeout(Duration.ofSeconds(20))
    .build();
```

| Method | Meaning | Default |
|---|---|---|
| `baseUri(String)`, `baseUri(URI)` | The URI that resolves a relative route URI. It must be an absolute `http` or `https` URI. | none |
| `route(String operation, String uri)` | Adds a route with the method `POST` and no route headers. | none |
| `route(String operation, HttpRoute route)` | Adds a route. | none |
| `routes(Map<String, HttpRoute>)` | Adds all routes of the map. | none |
| `header(String name, String value)` | Adds a header to each request. | none |
| `basicAuth(String user, String password)` | Sets the header `Authorization: Basic <base64 of user:password>`, in UTF-8 (RFC 7617). | none |
| `headerSupplier(Supplier<Map<String, String>>)` | Gives headers for each request, for example a bearer token that can change. | none |
| `abendHeader(String name)` | The name of a response header that contains an abend code (5.6). | none: no abend check. |
| `timeout(Duration)` | The time limit of one exchange, from the send until the last byte of the response body arrives. | 30 seconds |
| `connectTimeout(Duration)` | The time limit to connect. Only for the client that the transport creates. | 10 seconds |
| `httpClient(HttpClient)` | A client of the caller, for example with an `SSLContext` for mutual TLS, a proxy, or HTTP/2. | none: the transport creates a client. |

Rules:

- A route for an operation replaces an earlier route for the same operation.
- A header replaces an earlier header with the same name. Header names are not case-sensitive.
- `basicAuth` replaces an earlier `Authorization` header of the transport.
- `build()` throws `IllegalStateException` if no route is set, if a route URI is relative and no base URI is set, if a resolved URI is not an absolute `http` or `https` URI, or if `connectTimeout` and `httpClient` are both set.
- `timeout` and `connectTimeout` throw `IllegalArgumentException` if the value is zero or negative.
- `header` throws `IllegalArgumentException` for a header that 5.4 rejects.
- Each method throws `NullPointerException` for a `null` value.

**The client that the transport creates.** It uses HTTP/1.1, the connect timeout, and `Redirect.NEVER`. HTTP/1.1 prevents an `h2c` upgrade request on a cleartext connection. All other settings are the JDK defaults: the default `SSLContext`, no proxy, and no authenticator.

**The client of the caller.** The transport does not change it, and does not close it. The caller sets the redirect policy. A client with a redirect policy other than `NEVER` can send a request two times.

### 5.4 Headers

For each request, the transport merges the headers in this order. A later header replaces an earlier header with the same name:

1. The defaults: `Content-Type: application/octet-stream` and `Accept: application/octet-stream`.
2. The headers of the transport (`header`, `basicAuth`).
3. The headers of the route.
4. The headers of the header supplier.

The transport rejects these headers:

- A restricted header of the JDK client, for example `Host`, `Connection`, `Content-Length`, `Expect`, or `Upgrade`.
- A name that is not an HTTP token, or a value that contains a line break.
- A `null` name or value.

The builder and `HttpRoute` reject an incorrect header with `IllegalArgumentException`. For the header supplier, the check occurs in `exchange` (5.6).

### 5.5 One exchange

1. Find the route of `request.operation()`. If no route exists, throw `OperationNotFoundException`. The transport does not send a request.
2. Call the header supplier, if it is set.
3. Create the `HttpRequest`: the URI and the method of the route, the merged headers, and a body of `payload` bytes.
4. Send the request asynchronously with `BodyHandlers.ofByteArray()`. Wait for the full response, for a maximum of `timeout`. If the time ends, cancel the request and throw `TransportTimeoutException`.
5. Translate the response (5.6). For a success, return the body.

`maxResponseLength` and `attributes()` of `BookRequest` are not read. The body can be longer than the layout: the codec ignores extra bytes (main design, 5.8). A shorter body causes `MappingException` in the codec.

### 5.6 Failure translation

The transport checks the response in this order. The first match gives the exception:

| Source | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| The transport is closed. | `TransportException` |
| The header supplier throws a `RuntimeException`, returns `null`, or returns a header that 5.4 rejects. | `TransportException`, with the exception as the cause. |
| `HttpConnectTimeoutException`: the connection did not open in time. | `ConnectionException` |
| `ConnectException`: the connection is refused, or the host is unknown. | `ConnectionException` |
| `SSLHandshakeException`: the TLS handshake failed. The request was not sent. | `ConnectionException` |
| The full response did not arrive in `timeout` (5.5). The transport cancels the request. | `TransportTimeoutException` |
| `HttpTimeoutException` from the client, for example from a request timeout of a client of the caller. | `TransportTimeoutException` |
| Another `IOException`. The program can be complete on the server. | `TransportException` |
| The thread is interrupted while it waits. The transport cancels the request. | `TransportException`. The interrupt flag stays set. |
| The abend header is set, and the response contains it with a value that is not blank. Any status. | `RemoteAbendException(value)` |
| Status `2xx`. | No exception. The transport returns the body. An empty body gives an empty array. |
| Status `404`. | `OperationNotFoundException` |
| Status `408` or `504`. | `TransportTimeoutException` |
| Status `502` or `503`. | `ConnectionException` |
| Any other status, also `1xx` and `3xx`. | `TransportException` |

The abend check comes before the status check. Thus, a `500` response with the header `X-Abend-Code: ASRA` gives `RemoteAbendException("ASRA")`. The transport trims the value of the abend header.

A `3xx` status is a failure, because the client that the transport creates does not follow redirects.

**Messages.** Each message gives the operation, the method, and the URI without the user information and without the query. Examples:

- `GetInvoice (PUT https://billing.example.com/invoices): HTTP status 500: <excerpt>`
- `GetInvoice (PUT https://billing.example.com/invoices): connection failed`
- `GetInvoice (PUT https://billing.example.com/invoices): no response in 20000 ms`
- `GetInvoice (PUT https://billing.example.com/invoices): abend ASRA`

The excerpt of the body follows these rules:

- If the `Content-Type` of the response contains `text`, `json`, or `xml`, the excerpt is the start of the body as UTF-8 text, with a maximum of 200 characters. Control characters become spaces.
- Otherwise, the excerpt is the first 32 bytes in hexadecimal, for example `hex C1C2C3...`.
- An empty body gives no excerpt.

A message never contains a header value of the request.

The exception keeps the JDK exception as its cause. The cause can contain the full URI. **CAUTION:** Do not put a password or a token in the query of a route URI. The cause of an exception and a log of the JDK client can show it.

### 5.7 The sender seam

The transport sends each request through a package-private interface:

```java
interface HttpSender {
    CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request);
}
```

The production sender calls `HttpClient.sendAsync(request, BodyHandlers.ofByteArray())`. The transport waits with `future.get(timeout)`. It translates the cause of an `ExecutionException` with the table of 5.6. A package-private builder method sets a fake sender in the tests. With the fake, the tests cause a connect timeout, a TLS handshake failure, an `IOException`, and an interrupt. These failures are not deterministic with a real socket.

The sender is not public API. A user who needs a different client gives an `HttpClient`.

### 5.8 Threads and close

`HttpTransport` implements `Transport` and `AutoCloseable`. It is thread-safe. The configuration is immutable after `build()`. The header supplier must be thread-safe, because many threads can call it at the same time.

- The transport does no pooling of its own. The JDK client keeps its connections.
- `close()` marks the transport closed. If the transport created the client, `close()` calls `HttpClient.shutdown()`. The client accepts no new request. A request that runs continues until it completes or until its timeout. `close()` does not wait for it. The transport does not shut down a client of the caller. A second `close()` does nothing.
- An `exchange` after `close()` throws `TransportException`.

The transport opens no connection in `build()`. The first exchange opens the first connection.

### 5.9 User setup

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-http:<version>")
}
```

Mutual TLS:

```java
HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)            // with the key of the client
    .connectTimeout(Duration.ofSeconds(5))
    .build();

HttpTransport transport = HttpTransport.builder()
    .httpClient(client)
    .route("GetInvoice", "https://billing.example.com/invoices")
    .build();
```

A bearer token that changes:

```java
HttpTransport transport = HttpTransport.builder()
    .headerSupplier(() -> Map.of("Authorization", "Bearer " + tokens.current()))
    .route("GetInvoice", "https://billing.example.com/invoices")
    .build();
```

## 6. Tests

- `HttpRouteTest`: the rules of 5.2 and the header rules of 5.4.
- `HttpTransportBuilderTest`: the rules of 5.3, the URI resolution, and the conflict of `httpClient` and `connectTimeout`.
- `HttpTransportTest`, with a JDK `HttpServer` on a free local port: the method, the URI, the body, the merged headers, the basic authentication value, the header supplier, each status row of 5.6, the abend header, the body excerpt (text and hexadecimal), a slow handler with a short timeout, a handler that sends the headers and then stops before the end of the body, a refused connection, a client of the caller, and `close`.
- `HttpTransportFailureTest`, with a fake `HttpSender`: the connect timeout, the TLS handshake failure, another `IOException`, and the interrupt.
- `HttpTransportClientTest`: a `BookmapClient` with a generated codec and a full call through the `HttpServer`.

No test uses a mainframe or the network outside the local machine.

## 7. Changes outside the module

- `settings.gradle.kts`: include `bookmap-transport-http`.
- `build.gradle.kts`: add the module to `publishedModules`, with the description "BookMap HTTP transport (binary body), for example for CICS web services."
- `docs/wiki/Contributing.md`, and `RELEASING.md`: add the module to the list of published modules.
- `docs/wiki/HTTP-transport.md`: a new page. `_Sidebar.md` and `Home.md`: a link to the page.
- `docs/wiki/Transports-and-simulator.md` and `docs/wiki/Getting-started.md`: name the new transport.
- `README.md`: the module list and the "Project status" section.
- `ROADMAP.md`: remove #28.
- `bookmap-core` and `bookmap-processor`: no change.
- `gradle/verification-metadata.xml`: no change is expected. Run `scripts/update-verification-metadata.sh` to make sure.

The CTG (#26) and MQ (#27) sessions change the same shared files. Keep the changes there small.

## 8. Calls made by the builder of this design

These points were not decided by the author. They are proposals of this design:

- A connect timeout and a TLS handshake failure give `ConnectionException`, not `TransportTimeoutException`, because the request was not sent.
- Another `IOException` gives `TransportException`, because the program can be complete on the server.
- The abend header is off by default. When it is set, it applies to all statuses and comes before the status check.
- `3xx` is a failure. The client that the transport creates does not follow redirects.
- The transport creates an HTTP/1.1 client. A user who wants HTTP/2 gives a client.
- `httpClient` and `connectTimeout` together are an error in `build()`, not a silent choice.
- `close()` calls `shutdown()`, not `close()` or `shutdownNow()` of the client. `close()` of the client can wait for a long time. `shutdownNow()` stops a request that the program can already have run.
- The default timeout is 30 seconds, and the default connect timeout is 10 seconds, as in the CTG transport.
- The body excerpt: 200 characters of text for a text, JSON, or XML response, else 32 bytes in hexadecimal.
- The method is configurable for each route. The default is `POST`. The JDK can send a `GET` or `HEAD` request two times after a closed pooled connection. The Javadoc of `HttpRoute` tells this.
- `BookRequest.maxResponseLength()` is not used to limit the body size.

## 9. Left to the implementation

- The exact text of the messages, in the format of 5.6.
- The structure of the test server and the fakes.
- The Javadoc of the public types.
