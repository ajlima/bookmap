`bookmap-spring-boot` gives a Spring Boot auto-configuration for `BookmapClient`. The build compiles and tests it with Spring Boot 4.1.2.
The module includes `bookmap-core` and `spring-boot-autoconfigure`. The core module has no Spring dependency.

## Setup

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-spring-boot:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator:<version>")
}
```

```xml
<dependency>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-spring-boot</artifactId>
  <version>${bookmap.version}</version>
</dependency>
```

With Maven, add `bookmap-processor` to `annotationProcessorPaths`. See [Getting started](Getting-started#installation-with-maven).

## What the auto-configuration creates

| Bean | Condition | Value |
|---|---|---|
| `BookCodecRegistry` | No `BookCodecRegistry` bean exists. | `BookCodecRegistry.loadDefault()`: all generated codecs on the class path. |
| `BookmapClient` | A `Transport` bean exists, and no `BookmapClient` bean exists. | A client with the transport, the registry, and the `bookmap.*` properties. |

Thus, you must declare a `Transport` bean. Without it, the application has no `BookmapClient` bean.
BookMap has no real transport yet. Write your own transport (see [Transports and simulator](Transports-and-simulator#the-transport-spi)), or use the simulator in tests.

```java
@Configuration
class MainframeConfiguration {

    @Bean
    Transport mainframeTransport() {
        return new MyTransport();
    }
}
```

Then inject the client:

```java
@Service
class OrderService {

    private final BookmapClient client;

    OrderService(BookmapClient client) {
        this.client = client;
    }

    List<Order> ordersOf(String customerId) {
        GetCustomerOrders request = new GetCustomerOrders();
        request.setCustomerId(customerId);
        request.setRestartKey("");
        return client.execute(request).getOrders();
    }
}
```

## Properties

| Property | Default | Meaning |
|---|---|---|
| `bookmap.charset` | `IBM037` | The default charset of the client. It must be single-byte. A book with `@BookDefaults(charset = ...)` uses its own charset. |
| `bookmap.max-calls` | `1000` | The maximum number of calls for one execution. The value must be 1 or more. |

```properties
bookmap.charset=IBM500
bookmap.max-calls=50
```

The properties bind to the record `pt.dazz.bookmap.spring.BookmapProperties`, with the prefix `bookmap`.
The module has no configuration metadata file yet. Thus, the IDE does not complete the `bookmap.*` properties.

The application does not start in these cases:

- `bookmap.charset` is not a single-byte charset, for example `UTF-8`. The message is `Only single-byte charsets are valid: UTF-8`.
- `bookmap.max-calls` is less than 1. The message is `maxCalls must be at least 1: <n>`.

## Replace the beans

Declare your own `BookmapClient` bean to replace the client of the auto-configuration:

```java
@Bean
BookmapClient bookmapClient(Transport transport, BookCodecRegistry registry) {
    return BookmapClient.builder()
        .transport(transport)
        .codecRegistry(registry)
        .maxCalls(50)
        .build();
}
```

Declare your own `BookCodecRegistry` bean to use only some codecs. The generated codec classes are public, with a public constructor without arguments:

```java
@Bean
BookCodecRegistry bookCodecRegistry() {
    return BookCodecRegistry.of(new GetCustomerOrders_BookCodec());
}
```

`BookCodecRegistry.loadDefault(ClassLoader)` loads the codecs of a specific class loader.

## Tests with the simulator

Declare the simulator as the `Transport` bean of the test:

```java
@TestConfiguration
class SimulatorConfiguration {

    @Bean
    SimulatorTransport simulatorTransport() {
        return SimulatorTransport.builder().build();
    }
}
```

Inject `SimulatorTransport` in the test, and register the handlers. See [Transports and simulator](Transports-and-simulator#the-simulator).

## Transport auto-configuration

If you write an auto-configuration for a transport, it must run before the BookMap auto-configuration:

```java
@AutoConfiguration(before = BookmapAutoConfiguration.class)
```

Then the `Transport` bean exists when the condition of the client runs.
A `Transport` bean in your own `@Configuration` class needs no special order.
