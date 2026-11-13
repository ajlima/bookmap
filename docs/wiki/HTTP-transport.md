`bookmap-transport-http` is a transport for an HTTP endpoint that accepts the COBOL bytes of a book.
The request bytes go in the body of an HTTP request. The transport returns the body of the response.

The body is binary: `application/octet-stream`. Examples of endpoints are a CICS web service with a binary body, a custom HTTP program in CICS, or an API gateway that accepts a binary body.

The module uses only the JDK HTTP client (`java.net.http`). It has no other dependency.

**NOTE:** A standard z/OS Connect API expects JSON. This transport does not convert a book to JSON. Use it only with an endpoint that accepts the bytes of the book.

## Setup

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-transport-http:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

```xml
<dependency>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-transport-http</artifactId>
  <version>${bookmap.version}</version>
</dependency>
```

## Create the transport

```java
HttpTransport transport = HttpTransport.builder()
    .baseUri("https://gateway.example.com/orders/")
    .basicAuth("ORDUSER", password)
    .route("GetCustomerOrders", "customer-orders")
    .route("GetInvoice", HttpRoute.of("https://billing.example.com/invoices", "PUT"))
    .abendHeader("X-Abend-Code")
    .timeout(Duration.ofSeconds(20))
    .build();

BookmapClient client = BookmapClient.builder()
    .transport(transport)
    .build();
```

`HttpTransport` is thread-safe. Create one transport for each endpoint configuration, and share it.
Close the transport when the application stops.

### Routes

A route maps an operation name to a URI, an HTTP method, and headers.

| Method | Meaning |
|---|---|
| `route(String operation, String uri)` | Adds a route with the method `POST` and no route headers. |
| `route(String operation, HttpRoute route)` | Adds a route. |
| `routes(Map<String, HttpRoute> routes)` | Adds all routes of the map. |

- The operation name is the value of `@BookOperation`, or the simple class name.
- A route for an operation replaces an earlier route for the same operation.
- A route URI is an absolute `http` or `https` URI, or a relative reference. `build()` resolves a relative reference against the base URI.
- End the base URI with `/`. Without it, a relative route replaces the last segment: `https://h/orders` and `invoices` give `https://h/invoices`.
- `HttpRoute.of(uri, method)` sets the method. `new HttpRoute(uri, method, headers)` also sets route headers.

If no route exists for an operation, `exchange` throws `OperationNotFoundException`. The transport sends no request.

**CAUTION:** Use `POST` or `PUT` for a program that is not idempotent. The JDK client can send a `GET` or `HEAD` request two times after the server closes a pooled connection.

### The builder

| Method | Meaning | Default |
|---|---|---|
| `baseUri(String)`, `baseUri(URI)` | The URI that resolves a relative route URI. | none |
| `header(String name, String value)` | Adds a header to each request. | none |
| `basicAuth(String user, String password)` | Sets `Authorization: Basic ...`, in UTF-8. The user must not contain `:`. | none |
| `headerSupplier(Supplier<Map<String, String>>)` | Gives headers for each request. | none |
| `abendHeader(String name)` | The response header that contains an abend code. | none: no abend check. |
| `timeout(Duration)` | The time limit of one exchange, until the last byte of the response body. | 30 seconds |
| `connectTimeout(Duration)` | The time limit to connect. Only for the client that the transport creates. | 10 seconds |
| `httpClient(HttpClient)` | Your own HTTP client. | The transport creates a client. |

`build()` throws `IllegalStateException` if no route is set, if a route URI is relative and no base URI is set, or if `httpClient` and `connectTimeout` are both set.

## Headers and authentication

The transport merges the headers in this order. A later header replaces an earlier header with the same name. Header names are not case-sensitive.

1. The defaults: `Content-Type: application/octet-stream` and `Accept: application/octet-stream`.
2. The headers of the transport: `header` and `basicAuth`.
3. The headers of the route.
4. The headers of the header supplier.

The transport rejects a restricted header of the JDK client, for example `Host`, `Connection`, or `Content-Length`.

**CAUTION:** Use `basicAuth` and a bearer token only with an `https` route. On `http`, the password and the token go over the network as clear text.

A bearer token that changes:

```java
HttpTransport transport = HttpTransport.builder()
    .headerSupplier(() -> Map.of("Authorization", "Bearer " + tokens.current()))
    .route("GetInvoice", "https://billing.example.com/invoices")
    .build();
```

The transport calls the supplier for each request. Make the supplier thread-safe.
If the supplier throws an exception, returns `null`, or gives an incorrect header, `exchange` throws `TransportException`. The transport sends no request.

### Mutual TLS, a proxy, or HTTP/2

Give your own `HttpClient`:

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

The transport does not change your client, and does not shut it down.
Set the connect timeout on your client.
Do not let your client follow redirects: a redirect can send the request two times.

The client that the transport creates uses HTTP/1.1, the default `SSLContext` of the JVM, and no proxy. It does not follow redirects.

## Timeouts

`timeout` is the time limit of the full exchange: from the send until the last byte of the response body.
When the time ends, the transport cancels the request and throws `TransportTimeoutException`.
The program on the server can be complete at that time.

`connectTimeout` is the time limit to open the connection. A connection that does not open in time gives `ConnectionException`.

## Errors

The transport checks each response in this order. The first match gives the exception.

| Failure | Exception |
|---|---|
| No route for the operation. | `OperationNotFoundException` |
| The transport is closed. | `TransportException` |
| The header supplier fails. | `TransportException` |
| The connection does not open in time, is refused, or the host is unknown. | `ConnectionException` |
| The TLS handshake fails. | `ConnectionException` |
| The full response does not arrive in `timeout`. | `TransportTimeoutException` |
| Another I/O failure. The program can be complete on the server. | `TransportException` |
| The thread is interrupted. | `TransportException`. The interrupt flag stays set. |
| The response contains the abend header, with a value that is not blank. Any status. | `RemoteAbendException` |
| Status `2xx`. | No exception. The transport returns the body. |
| Status `404`. | `OperationNotFoundException` |
| Status `408` or `504`. | `TransportTimeoutException` |
| Status `502` or `503`. | `ConnectionException` |
| Any other status, also `3xx`. | `TransportException` |

The transport never sends a request two times. Retry in your application only if the program is idempotent.

The abend code is the trimmed value of the abend header, with a maximum of 32 characters.

Each message gives the operation, the method, and the URI, for example:

```
GetInvoice (PUT https://billing.example.com/invoices): HTTP status 500: {"error":"..."}
```

- For a text, JSON, or XML response, the message gives the first 200 characters of the body.
- For another response, the message gives the first 32 bytes of the body in hexadecimal, for example `hex C1C2C3`.
- The message never contains a request header, the user information of the URI, or the query of the URI.

**CAUTION:** Do not put a password or a token in the query of a route URI. The cause of an exception and the log of the JDK client can show the full URI.

## Close

`close()` stops the transport. An `exchange` after `close()` throws `TransportException`.
If the transport created its client, `close()` calls `HttpClient.shutdown()`. The client accepts no new request.
A request that runs continues until it completes or until its timeout. `close()` does not wait for it.

## Test without a mainframe

Use the [simulator](Transports-and-simulator#the-simulator) for unit tests.
To test the transport itself, run a local HTTP server, for example `com.sun.net.httpserver.HttpServer` of the JDK, and point a route to it.
