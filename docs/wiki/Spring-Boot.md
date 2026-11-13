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

A `Transport` bean comes from one of these sources:

- A transport auto-configuration. Set `bookmap.transport.type` and the properties of the transport. See [Select a transport](#select-a-transport).
- Your own `@Bean`, for example a transport that you write (see [Transports and simulator](Transports-and-simulator#the-transport-spi)), or the simulator in tests.

Without a `Transport` bean, the application has no `BookmapClient` bean.

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
| `bookmap.transport.type` | none | The transport that the auto-configuration creates: `ctg`, `mq`, or `http`. See [Select a transport](#select-a-transport). |

```properties
bookmap.charset=IBM500
bookmap.max-calls=50
```

The properties bind to the record `pt.dazz.bookmap.spring.BookmapProperties`, with the prefix `bookmap`.

The JAR of `bookmap-spring-boot` contains the file `META-INF/spring-configuration-metadata.json`.
The IDE reads this file. It completes the `bookmap.*` properties in `application.properties` and `application.yml`, and it shows the description and the default value of each property.
For `bookmap.charset`, the IDE also proposes the values `IBM037`, `IBM500`, `IBM1047`, `IBM1140`, and `IBM1148`.
You can set a different single-byte charset.
The metadata does not add a dependency to your application.

The application does not start in these cases:

- `bookmap.charset` is not a single-byte charset, for example `UTF-8`. The message is `Only single-byte charsets are valid: UTF-8`.
- `bookmap.max-calls` is less than 1. The message is `maxCalls must be at least 1: <n>`.

## Select a transport

Set `bookmap.transport.type` to select the transport auto-configuration:

| Value | Transport | Add to the class path |
|---|---|---|
| `ctg` | [CICS Transaction Gateway](#cics-transaction-gateway) | `bookmap-transport-ctg` and the IBM library `ctgclient.jar`. |
| `mq` | IBM MQ. Not available yet. | `bookmap-transport-mq`. |
| `http` | HTTP. Not available yet. | `bookmap-transport-http`. |

Without `bookmap.transport.type`, no transport auto-configuration runs. Then declare a `Transport` bean.
A `Transport` bean of your application always wins. The transport auto-configuration then does nothing.

The application does not start if `bookmap.transport.type` is set and no `Transport` bean exists. The message tells what to add, for example:
`bookmap.transport.type=ctg, but no Transport bean exists. Add bookmap-transport-ctg and the IBM library ctgclient.jar to the class path.`

## CICS Transaction Gateway

With `bookmap.transport.type=ctg`, the auto-configuration creates a `CtgTransport` bean from the `bookmap.transport.ctg.*` properties.
For the transport itself, see [CICS Transaction Gateway](CICS-Transaction-Gateway).

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-spring-boot:<version>")
    implementation("pt.dazz.bookmap:bookmap-transport-ctg:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
    runtimeOnly(files("libs/ctgclient.jar"))   // or the artifact in your company repository
}
```

```properties
bookmap.transport.type=ctg
bookmap.transport.ctg.url=tcp://ctg.example.com
bookmap.transport.ctg.server=CICSPROD
bookmap.transport.ctg.userid=ORDUSER
bookmap.transport.ctg.password=${CTG_PASSWORD}
bookmap.transport.ctg.routes[GetCustomerOrders].program=PGMORD01
bookmap.transport.ctg.routes[GetCustomerOrders].transaction=TX01
bookmap.transport.ctg.routes[GetInvoice].program=PGMINV01
```

| Property | Default | Meaning |
|---|---|---|
| `bookmap.transport.ctg.url` | none | The URL of the Gateway daemon, for example `tcp://ctg.example.com` or `ssl://ctg.example.com`. `local:` selects local mode. Mandatory for the connector of the IBM library. |
| `bookmap.transport.ctg.port` | `2006` | The port of the Gateway daemon. |
| `bookmap.transport.ctg.socket-connect-timeout` | `10s` | The time limit to connect the socket. |
| `bookmap.transport.ctg.protocol-properties[<key>]` | none | A protocol property, for example the key ring for TLS. |
| `bookmap.transport.ctg.server` | none: the default server of CTG. | The name of the CICS server in the CTG configuration. |
| `bookmap.transport.ctg.default-transaction` | none: the mirror transaction. | The transaction for a route without a transaction. |
| `bookmap.transport.ctg.userid` | none: no credentials. | The user ID for CICS. |
| `bookmap.transport.ctg.password` | none | The password for CICS. It needs a user ID. |
| `bookmap.transport.ctg.timeout` | `30s` | The ECI timeout, in whole seconds. `0s` means no timeout. |
| `bookmap.transport.ctg.max-connections` | `10` | The maximum number of open gateway connections. |
| `bookmap.transport.ctg.connection-wait-timeout` | `30s` | The maximum wait for a free connection. |
| `bookmap.transport.ctg.routes[<operation>].program` | none | The CICS program of the operation. Mandatory. |
| `bookmap.transport.ctg.routes[<operation>].transaction` | none: the default transaction. | The transaction of the operation. |

Rules:

- Set at least one route. The key is the operation name, for example `GetInvoice`. The operation name is case-sensitive.
- Write the key in brackets: `routes[GetInvoice].program`. In YAML, write `"[GetInvoice]":`. The brackets also keep a key with special characters.
- An environment variable cannot give a route, because Spring Boot changes its key to lowercase. Use a properties file, a YAML file, or `SPRING_APPLICATION_JSON`.
- The application does not start if a value is not valid, for example a timeout with a fraction of a second, a route without a program, or a password without a user ID.

### Secrets

Give the password with an environment variable (`BOOKMAP_TRANSPORT_CTG_PASSWORD`), a configuration tree (`spring.config.import=configtree:/run/secrets/`), or a secret store.
The `toString()` of `CtgTransportProperties` does not show the password.

**CAUTION:** Do not write a password as a plain value in `application.properties` in the source repository. Each person who can read the repository can then read the password.

### The connector

The transport uses an `EciConnector` bean. Without one, the auto-configuration creates a `JavaGatewayConnector` from the properties, if `ctgclient.jar` is on the class path.
Declare your own `EciConnector` bean to use another client, for example a fake in a test. If no connector exists, the application does not start: `No EciConnector bean. Add the IBM library ctgclient.jar, or declare an EciConnector bean.`

### Customize the builder

A `CtgTransportBuilderCustomizer` bean changes the builder after the properties, and before `build()`. The order is the `@Order` of the beans.

```java
@Bean
CtgTransportBuilderCustomizer extraRoute() {
    return builder -> builder.route("GetShipment", "PGMSHP01", "TX03");
}
```

Spring Boot closes the transport when the context closes.

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
