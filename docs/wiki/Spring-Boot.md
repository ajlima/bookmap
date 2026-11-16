`copymapper-spring-boot` gives a Spring Boot auto-configuration for `CopyMapperClient`. The build compiles and tests it with Spring Boot 4.1.2.
The module includes `copymapper-core` and `spring-boot-autoconfigure`. The core module has no Spring dependency.

## Setup

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-spring-boot:<version>")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:<version>")
    testImplementation("pt.dazz.copymapper:copymapper-transport-simulator:<version>")
}
```

```xml
<dependency>
  <groupId>pt.dazz.copymapper</groupId>
  <artifactId>copymapper-spring-boot</artifactId>
  <version>${copymapper.version}</version>
</dependency>
```

With Maven, add `copymapper-processor` to `annotationProcessorPaths`. See [Getting started](Getting-started#installation-with-maven).

## What the auto-configuration creates

| Bean | Condition | Value |
|---|---|---|
| `BookCodecRegistry` | No `BookCodecRegistry` bean exists. | `BookCodecRegistry.loadDefault()`: all generated codecs on the class path. |
| `CopyMapperClient` | A `Transport` bean exists, and no `CopyMapperClient` bean exists. | A client with the transport, the registry, and the `copymapper.*` properties. |

A `Transport` bean comes from one of these sources:

- A transport auto-configuration. Set `copymapper.transport.type` and the properties of the transport. See [Select a transport](#select-a-transport).
- Your own `@Bean`, for example a transport that you write (see [Transports and simulator](Transports-and-simulator#the-transport-spi)), or the simulator in tests.

Without a `Transport` bean, the application has no `CopyMapperClient` bean.

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

    private final CopyMapperClient client;

    OrderService(CopyMapperClient client) {
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
| `copymapper.charset` | `IBM037` | The default charset of the client. It must be single-byte. A book with `@Defaults(charset = ...)` uses its own charset. |
| `copymapper.max-calls` | `1000` | The maximum number of calls for one execution. The value must be 1 or more. |
| `copymapper.transport.type` | none | The transport that the auto-configuration creates: `ctg`, `mq`, or `http`. See [Select a transport](#select-a-transport). |

```properties
copymapper.charset=IBM500
copymapper.max-calls=50
```

The properties bind to the record `pt.dazz.copymapper.spring.CopyMapperProperties`, with the prefix `copymapper`.

The JAR of `copymapper-spring-boot` contains the file `META-INF/spring-configuration-metadata.json`.
The IDE reads this file. It completes the `copymapper.*` properties in `application.properties` and `application.yml`, and it shows the description and the default value of each property.
For `copymapper.charset`, the IDE also proposes the values `IBM037`, `IBM500`, `IBM1047`, `IBM1140`, and `IBM1148`.
You can set a different single-byte charset.
The metadata does not add a dependency to your application.

The application does not start in these cases:

- `copymapper.charset` is not a single-byte charset, for example `UTF-8`. The message is `Only single-byte charsets are valid: UTF-8`.
- `copymapper.max-calls` is less than 1. The message is `maxCalls must be at least 1: <n>`.
- The environment has a property name of the releases until `0.27.0`: `bookmap.charset`, `bookmap.max-calls`, or a key under `bookmap.transport`. The environment variables, for example `BOOKMAP_TRANSPORT_CTG_PASSWORD`, are included. The message lists the names, but not the values. Rename each property to `copymapper.*`. See [Migrate to CopyMapper](Migrate-to-CopyMapper).

## Select a transport

Set `copymapper.transport.type` to select the transport auto-configuration:

| Value | Transport | Add to the class path |
|---|---|---|
| `ctg` | [CICS Transaction Gateway](#cics-transaction-gateway) | `copymapper-transport-ctg` and the IBM library `ctgclient.jar`. |
| `mq` | [IBM MQ](#ibm-mq) | `copymapper-transport-mq`. The context also needs a `jakarta.jms.ConnectionFactory` bean. |
| `http` | [HTTP](#http) | `copymapper-transport-http`. |

Without `copymapper.transport.type`, no transport auto-configuration runs. Then declare a `Transport` bean.
A `Transport` bean of your application always wins. The transport auto-configuration then does nothing.

The application does not start if `copymapper.transport.type` is set and no `Transport` bean exists. The message tells what to add, for example:
`copymapper.transport.type=ctg, but no Transport bean exists. Add copymapper-transport-ctg and the IBM library ctgclient.jar to the class path.`

## CICS Transaction Gateway

With `copymapper.transport.type=ctg`, the auto-configuration creates a `CtgTransport` bean from the `copymapper.transport.ctg.*` properties.
For the transport itself, see [CICS Transaction Gateway](CICS-Transaction-Gateway).

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-spring-boot:<version>")
    implementation("pt.dazz.copymapper:copymapper-transport-ctg:<version>")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:<version>")
    runtimeOnly(files("libs/ctgclient.jar"))   // or the artifact in your company repository
}
```

```properties
copymapper.transport.type=ctg
copymapper.transport.ctg.url=tcp://ctg.example.com
copymapper.transport.ctg.server=CICSPROD
copymapper.transport.ctg.userid=ORDUSER
copymapper.transport.ctg.password=${CTG_PASSWORD}
copymapper.transport.ctg.routes[GetCustomerOrders].program=PGMORD01
copymapper.transport.ctg.routes[GetCustomerOrders].transaction=TX01
copymapper.transport.ctg.routes[GetInvoice].program=PGMINV01
```

| Property | Default | Meaning |
|---|---|---|
| `copymapper.transport.ctg.url` | none | The URL of the Gateway daemon, for example `tcp://ctg.example.com` or `ssl://ctg.example.com`. `local:` selects local mode. Mandatory for the connector of the IBM library. |
| `copymapper.transport.ctg.port` | `2006` | The port of the Gateway daemon. |
| `copymapper.transport.ctg.socket-connect-timeout` | `10s` | The time limit to connect the socket. |
| `copymapper.transport.ctg.protocol-properties[<key>]` | none | A protocol property, for example the key ring for TLS. |
| `copymapper.transport.ctg.server` | none: the default server of CTG. | The name of the CICS server in the CTG configuration. |
| `copymapper.transport.ctg.default-transaction` | none: the mirror transaction. | The transaction for a route without a transaction. |
| `copymapper.transport.ctg.userid` | none: no credentials. | The user ID for CICS. |
| `copymapper.transport.ctg.password` | none | The password for CICS. It needs a user ID. |
| `copymapper.transport.ctg.timeout` | `30s` | The ECI timeout, in whole seconds. `0s` means no timeout. |
| `copymapper.transport.ctg.max-connections` | `10` | The maximum number of open gateway connections. |
| `copymapper.transport.ctg.connection-wait-timeout` | `30s` | The maximum wait for a free connection. |
| `copymapper.transport.ctg.routes[<operation>].program` | none | The CICS program of the operation. Mandatory. |
| `copymapper.transport.ctg.routes[<operation>].transaction` | none: the default transaction. | The transaction of the operation. |

Rules:

- Set at least one route. The key is the operation name, for example `GetInvoice`. The operation name is case-sensitive.
- Write the key in brackets: `routes[GetInvoice].program`. In YAML, write `"[GetInvoice]":`. The brackets also keep a key with special characters.
- An environment variable cannot give a route, because Spring Boot changes its key to lowercase. Use a properties file, a YAML file, or `SPRING_APPLICATION_JSON`.
- The application does not start if a value is not valid, for example a timeout with a fraction of a second, a route without a program, or a password without a user ID.

### Secrets

Give the password with an environment variable (`COPYMAPPER_TRANSPORT_CTG_PASSWORD`), a configuration tree (`spring.config.import=configtree:/run/secrets/`), or a secret store.
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

## HTTP

With `copymapper.transport.type=http`, the auto-configuration creates an `HttpTransport` bean from the `copymapper.transport.http.*` properties.
For the transport itself, see [HTTP transport](HTTP-transport).

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-spring-boot:<version>")
    implementation("pt.dazz.copymapper:copymapper-transport-http:<version>")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:<version>")
}
```

```properties
copymapper.transport.type=http
copymapper.transport.http.base-uri=https://gateway.example.com/orders/
copymapper.transport.http.username=ORDUSER
copymapper.transport.http.password=${GATEWAY_PASSWORD}
copymapper.transport.http.headers[X-Client-Id]=order-service
copymapper.transport.http.abend-header=X-Abend-Code
copymapper.transport.http.routes[GetCustomerOrders].uri=customer-orders
copymapper.transport.http.routes[GetInvoice].uri=https://billing.example.com/invoices
copymapper.transport.http.routes[GetInvoice].method=PUT
```

| Property | Default | Meaning |
|---|---|---|
| `copymapper.transport.http.base-uri` | none | The URI that resolves a relative route URI. It must be an absolute `http` or `https` URI. |
| `copymapper.transport.http.headers[<name>]` | none | A header of each request. |
| `copymapper.transport.http.username` | none | The user for basic authentication. It needs a password. |
| `copymapper.transport.http.password` | none | The password for basic authentication. It needs a user. |
| `copymapper.transport.http.abend-header` | none: no abend check. | The name of a response header that contains an abend code. |
| `copymapper.transport.http.timeout` | `30s` | The time limit of one exchange, until the last byte of the response. |
| `copymapper.transport.http.connect-timeout` | none: 10 seconds. | The time limit to connect. |
| `copymapper.transport.http.ssl.bundle` | none | The name of a Spring Boot SSL bundle for TLS. See [TLS with an SSL bundle](#tls-with-an-ssl-bundle). |
| `copymapper.transport.http.routes[<operation>].uri` | none | The URI of the operation, absolute or relative to `base-uri`. Mandatory. |
| `copymapper.transport.http.routes[<operation>].method` | `POST` | The HTTP method of the operation. |
| `copymapper.transport.http.routes[<operation>].headers[<name>]` | none | A header of this route. It replaces a header of the transport with the same name. |

The rules of the routes and of the secrets are the same as for the [CICS Transaction Gateway](#cics-transaction-gateway). The `toString()` of `HttpTransportProperties` shows only the names of the headers, not their values.

The application does not start in these cases: no route, a route without `uri`, a relative URI without `base-uri`, a user without a password or a password without a user, or an incorrect header.

### TLS with an SSL bundle

Set `copymapper.transport.http.ssl.bundle` to the name of a [Spring Boot SSL bundle](https://docs.spring.io/spring-boot/reference/features/ssl.html).
The HTTP client then uses the trust store, the key store, the protocols, and the ciphers of the bundle. A key store in the bundle gives mutual TLS.

```properties
spring.ssl.bundle.pem.mainframe.truststore.certificate=classpath:certs/gateway-ca.crt
spring.ssl.bundle.pem.mainframe.keystore.certificate=classpath:certs/client.crt
spring.ssl.bundle.pem.mainframe.keystore.private-key=file:/run/secrets/client.key
copymapper.transport.http.ssl.bundle=mainframe
```

The client uses HTTP/1.1, does not follow redirects, and uses `connect-timeout` (10 seconds by default). Spring Boot stops the client when the context closes.
An unknown bundle name stops the application.

### Customize the builder of the HTTP transport

An `HttpTransportBuilderCustomizer` bean changes the builder after the properties, and before `build()`. The order is the `@Order` of the beans.
For example, send a bearer token that can change:

```java
@Bean
HttpTransportBuilderCustomizer bearerToken(TokenService tokens) {
    return builder -> builder.headerSupplier(
        () -> Map.of("Authorization", "Bearer " + tokens.current()));
}
```

A customizer can also set your own `HttpClient`, for example with a proxy. Then do not set `connect-timeout`: the application does not start. Set the connect timeout on your client.

## IBM MQ

With `copymapper.transport.type=mq`, the auto-configuration creates an `MqTransport` bean from the `copymapper.transport.mq.*` properties.
For the transport itself, see [IBM MQ](IBM-MQ).

The transport needs a `jakarta.jms.ConnectionFactory` bean. CopyMapper does not create it. Use the IBM starter `com.ibm.mq:mq-jms-spring-boot-starter`: it creates the factory from the `ibm.mq.*` properties.
Another source is also correct, for example the Artemis auto-configuration of Spring Boot, or your own `@Bean`.

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-spring-boot:<version>")
    implementation("pt.dazz.copymapper:copymapper-transport-mq:<version>")
    implementation("com.ibm.mq:mq-jms-spring-boot-starter:<version>")   // IBM license
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:<version>")
}
```

```properties
ibm.mq.queue-manager=QM1
ibm.mq.channel=APP.SVRCONN
ibm.mq.conn-name=mq.example.com(1414)
ibm.mq.user=ORDUSER
ibm.mq.password=${MQ_PASSWORD}

copymapper.transport.type=mq
copymapper.transport.mq.default-reply-queue=ORDERS.REPLY
copymapper.transport.mq.routes[GetCustomerOrders].request-queue=ORDERS.REQUEST
copymapper.transport.mq.routes[GetInvoice].request-queue=INVOICE.REQUEST
copymapper.transport.mq.routes[GetInvoice].reply-queue=INVOICE.REPLY
```

| Property | Default | Meaning |
|---|---|---|
| `copymapper.transport.mq.user` | none: the credentials of the factory. | The user for the connection. |
| `copymapper.transport.mq.password` | none | The password for the connection. It needs a user. |
| `copymapper.transport.mq.mq-native-messages` | `true` | Sends the messages without an RFH2 header. Set `false` only for a provider that is not IBM MQ. |
| `copymapper.transport.mq.default-reply-queue` | none: a temporary queue. | The reply queue for a route without a reply queue. |
| `copymapper.transport.mq.correlation` | `message-id` | `message-id` or `correlation-id`. See [Correlation](IBM-MQ#correlation). |
| `copymapper.transport.mq.timeout` | `30s` | The maximum wait for the reply. It is also the expiry of the request message. |
| `copymapper.transport.mq.persistent` | `false` | Sends persistent request messages. A temporary reply queue does not accept them. |
| `copymapper.transport.mq.max-sessions` | `10` | The maximum number of open sessions. |
| `copymapper.transport.mq.session-wait-timeout` | `30s` | The maximum wait for a free session. |
| `copymapper.transport.mq.routes[<operation>].request-queue` | none | The request queue of the operation. Mandatory. |
| `copymapper.transport.mq.routes[<operation>].reply-queue` | none: the default reply queue. | The reply queue of the operation. |

Usually, the credentials are in the factory (`ibm.mq.user`, `ibm.mq.password`). Set `copymapper.transport.mq.user` only to use other credentials.

**CAUTION:** Do not set `copymapper.transport.mq.user` with a `CachingConnectionFactory` or a `SingleConnectionFactory`. These factories do not accept a user and a password for each connection, and each exchange then fails. Put the credentials in the factory, or set `spring.jms.cache.enabled=false`.
The rules of the routes and of the secrets are the same as for the [CICS Transaction Gateway](#cics-transaction-gateway).

The application does not start in these cases: no route, a route without `request-queue`, a password without a user, persistent messages with a temporary reply queue, or no single `ConnectionFactory` bean.
With two or more factories, mark one with `@Primary`, or declare an `MqConnector` bean.

**CAUTION:** Spring Boot and the IBM starter can wrap the factory in a `CachingConnectionFactory`. Then all the sessions of the transport use one connection, and a broken connection affects all the sessions. The temporary reply queue of a session that the transport closes also stays open until the shared connection closes. To stop this, set `spring.jms.cache.enabled=false`, or use the pool of the IBM starter.

### Customize the builder of the MQ transport

An `MqTransportBuilderCustomizer` bean changes the builder after the properties, and before `build()`. The order is the `@Order` of the beans.
Declare your own `MqConnector` bean to use another client, for example a fake in a test.

## Replace the beans

Declare your own `CopyMapperClient` bean to replace the client of the auto-configuration:

```java
@Bean
CopyMapperClient copyMapperClient(Transport transport, BookCodecRegistry registry) {
    return CopyMapperClient.builder()
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

If you write an auto-configuration for a transport, it must run before the CopyMapper auto-configuration:

```java
@AutoConfiguration(before = CopyMapperAutoConfiguration.class)
```

Then the `Transport` bean exists when the condition of the client runs.
A `Transport` bean in your own `@Configuration` class needs no special order.
