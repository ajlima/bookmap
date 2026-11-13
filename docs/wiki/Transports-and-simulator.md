The client sends the bytes of each request through a **transport**. The transport is the only part of BookMap that knows the middleware.
BookMap has a simulator transport for tests, a transport for the [CICS Transaction Gateway](CICS-Transaction-Gateway), and an [HTTP transport](HTTP-transport). A transport for IBM MQ is on the [Roadmap](Roadmap).

## The transport SPI

A transport implements one method of `pt.dazz.bookmap.spi.Transport`:

```java
public interface Transport {
    byte[] exchange(BookRequest request);
}
```

`BookRequest` is a record with these components:

| Component | Meaning |
|---|---|
| `operation` | The operation name: the value of `@BookOperation`, or the simple class name. |
| `payload` | The request bytes. |
| `maxResponseLength` | The maximum length of the response in bytes. For a book with subtypes, it is the length of the longest layout. |
| `attributes` | Extra transport attributes, as a `Map<String, String>`. A `null` value becomes an empty map. |

`BookmapClient` does not set attributes. The map is always empty in the current version.

### Rules for a transport

- Map the operation name to the destination of the middleware, for example a CICS program name or a queue name.
- Return the response bytes of the remote program. Do not return `null`: the client then throws `MappingException` with the message `<operation>: empty response from transport`.
- Translate each technical failure to `TransportException` or to one of its subclasses. Do not let a middleware exception go to the caller.
- If the remote system does not know the operation, throw `OperationNotFoundException`.
- Make the transport thread-safe. All threads share one `BookmapClient`, and thus one transport.

| Failure | Exception |
|---|---|
| The remote program stopped abnormally (abend). | `RemoteAbendException(abendCode, message)` |
| The remote program did not answer in time. | `TransportTimeoutException` |
| The transport cannot connect to the remote system. | `ConnectionException` |
| The remote system does not know the operation. | `OperationNotFoundException` |
| Any other technical failure. | `TransportException` |

### Example: a transport with fixed responses

```java
import java.util.Map;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

public final class FixedResponseTransport implements Transport {

    private final Map<String, byte[]> responses;

    public FixedResponseTransport(Map<String, byte[]> responses) {
        this.responses = Map.copyOf(responses);
    }

    @Override
    public byte[] exchange(BookRequest request) {
        byte[] response = responses.get(request.operation());
        if (response == null) {
            throw new OperationNotFoundException("Unknown operation: " + request.operation());
        }
        return response;
    }
}
```

See [Getting started](Getting-started#connect-to-the-mainframe) for a transport skeleton with the exception mapping.

## The simulator

`SimulatorTransport` is in the module `bookmap-transport-simulator`, package `pt.dazz.bookmap.simulator`. It runs handlers in the same JVM. Use it in tests.

```kotlin
dependencies {
    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator:<version>")
}
```

```xml
<dependency>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-transport-simulator</artifactId>
  <version>${bookmap.version}</version>
  <scope>test</scope>
</dependency>
```

### The builder

| Method | Meaning | Default |
|---|---|---|
| `codecRegistry(BookCodecRegistry)` | The registry for the typed registrations. | `BookCodecRegistry.loadDefault()` |
| `charset(Charset)` | The charset for a codec that names no charset. It must be single-byte. | `IBM037` |
| `timeout(Duration)` | The time limit for one request. | 5 seconds |

Each builder method throws `NullPointerException` for a `null` value.

**CAUTION:** Set the same charset on the simulator and on the client. If the charsets are different, the text fields decode incorrectly.

### Register a handler

Use one of three registrations:

| Method | The handler gets | The handler gives |
|---|---|---|
| `register(Class<T>, SimulatedHandler<T>)` | The decoded request object. | Nothing. It changes the object. The simulator encodes the response from the same object. |
| `registerResponder(Class<T>, SimulatedResponder<T>)` | The decoded request object. | A new object, for example a subclass. The simulator encodes the layout of that object. |
| `register(String, SimulatedOperation)` | The request bytes. | The response bytes. |

The signatures are:

- `SimulatedHandler<T>`: `void handle(T call)`
- `SimulatedResponder<T>`: `T respond(T call)`. The result must not be `null`.
- `SimulatedOperation`: `byte[] handle(byte[] request)`. It gets the payload only, not the `BookRequest`.

```java
SimulatorTransport simulator = SimulatorTransport.builder()
    .timeout(Duration.ofSeconds(1))
    .build();
BookmapClient client = BookmapClient.builder().transport(simulator).build();

simulator.register(GetCustomerOrders.class, call -> {
    call.setOrders(List.of());
    call.setReturnCode("0000");
    call.setReturnMessage("OK");
    call.setMoreData("N");
});
```

Rules for the registrations:

- The simulator gets the operation name from the codec of the class.
- The call object contains only the fields of the request: the `INPUT` and `INPUT_OUTPUT` fields.
- A registration replaces an earlier registration with the same operation name.
- If no codec exists for the class, the registration throws `MappingException`.
- For a class with `@BookSubTypes`, register the base class. The base class can be abstract. A registration with a subclass throws `IllegalArgumentException` with the message `<Sub> is a subtype of <Base>. Register <Base>.`
- If a responder returns `null`, the simulator throws `MappingException` with the message `<operation>: the responder returned null`.

### Byte-level registration

Use the byte level to test the exact bytes. The generated codec gives the encode and decode methods:

```java
BookCodec<GetCustomerOrders> codec =
    BookCodecRegistry.loadDefault().codecFor(GetCustomerOrders.class);
CodecContext context = CodecContext.defaults();   // IBM037

simulator.register(codec.operationName(), payload -> {
    GetCustomerOrders call = codec.decodeRequest(payload, context);
    call.setOrders(List.of());
    call.setReturnCode("0000");
    call.setReturnMessage("OK");
    call.setMoreData("N");
    return codec.encodeResponse(call, context);
});
```

`BookCodec` is in `pt.dazz.bookmap.spi`. `CodecContext` is in `pt.dazz.bookmap.convert`.

### Simulate failures

| In the handler | The client throws |
|---|---|
| `throw new SimulatedAbend("ASRA")` | `RemoteAbendException`. `abendCode()` gives `ASRA`. The message is `Simulated abend ASRA`. |
| `throw new SimulatedAbend("ASRA", "message")` | `RemoteAbendException` with the code and the message. |
| A handler that is slower than the timeout | `TransportTimeoutException` with the message `Simulated operation <operation> did not finish in <n> ms`. The simulator interrupts the handler. |
| `throw` a `BookmapException`, for example `ConnectionException` | The same exception, unchanged. |
| `throw` any other exception or error | `TransportException` with the message `Simulated operation <operation> failed`. The cause is the original exception. |
| No registration for the operation | `OperationNotFoundException` with the message `No simulated operation registered: <operation>`. |

```java
simulator.register(GetCustomerOrders.class, call -> {
    throw new SimulatedAbend("ASRA");
});

assertThatThrownBy(() -> client.execute(request))
    .isInstanceOfSatisfying(RemoteAbendException.class,
        e -> assertThat(e.abendCode()).isEqualTo("ASRA"));
```

### Check the requests

- `receivedRequests()` returns an unmodifiable copy of the received `BookRequest` objects, in the order of arrival.
- The simulator records each request before it looks for the handler. Thus, a request to an unknown operation is also recorded.
- `clearReceivedRequests()` removes the recorded requests.

```java
client.execute(request);

assertThat(simulator.receivedRequests()).hasSize(1);
assertThat(simulator.receivedRequests().get(0).operation()).isEqualTo("GetCustomerOrders");
assertThat(simulator.receivedRequests().get(0).payload()).hasSize(22);
simulator.clearReceivedRequests();
```

### Threads

Each request runs on a virtual thread. The registrations and the list of requests are thread-safe.
If the caller thread is interrupted, the simulator throws `TransportException` and keeps the interrupt flag.
