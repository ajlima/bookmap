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
| `operation` | The operation name from `@BookOperation`. |
| `payload` | The request bytes. |
| `maxResponseLength` | A hint for the maximum length of the response in bytes. |
| `attributes` | Optional transport attributes, as a `Map<String, String>`. A `null` value becomes an empty map. |

Each transport maps the operation name to its destination. If a mapping does not exist, the transport throws `OperationNotFoundException`.
A transport must translate technical failures to the subclasses of `TransportException`. See [Error handling](Error-handling).

Real transports (CICS Transaction Gateway, IBM MQ, HTTP) are planned. See the [Roadmap](Roadmap).

## The simulator

`SimulatorTransport` is in the module `bookmap-transport-simulator`. It runs handlers in the same JVM. Use it in tests.

```kotlin
dependencies {
    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator:<version>")
}
```

The typed level uses the generated codec:

```java
SimulatorTransport simulator = SimulatorTransport.builder().build();
BookmapClient client = BookmapClient.builder().transport(simulator).build();

simulator.register(GetCustomerOrders.class, call -> {
    call.setOrders(page);
    call.setReturnCode("0000");
    call.setReturnMessage("OK");
    call.setMoreData("N");
});
```

- `register(String, SimulatedOperation)` works at the byte level.
- `registerResponder(Class, SimulatedResponder)` lets a handler return a different object, for example a subclass of a class with `@BookSubTypes`. The simulator encodes the layout of the returned object.
- For a class with subtypes, register the base class. Registration with a subclass throws `IllegalArgumentException`.
- A handler can throw `SimulatedAbend("ASRA")`. The simulator changes it to `RemoteAbendException`.
- A handler that is slower than the timeout (`builder().timeout(Duration)`) causes `TransportTimeoutException`.
- A `BookmapException` from a handler goes to the caller unchanged. The simulator wraps other exceptions in `TransportException`.
- `receivedRequests()` returns the recorded calls.
