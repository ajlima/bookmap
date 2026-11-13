# BookMap Spring Boot auto-configuration of the transports: design

Date: 2026-11-13
Status: approved, not released.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (the main design, section 5.12), `docs/design/2026-11-13-ctg-transport-design.md` (#26), `docs/design/2026-11-13-mq-transport-design.md` (#27), `docs/design/2026-11-13-http-transport-design.md` (#28), `CLAUDE.md` (project rules), GitHub issue #29.

## 1. Intent

`bookmap-spring-boot` creates a `BookmapClient` bean from a `Transport` bean. Today, the application must declare the `Transport` bean in its own code.

This design adds an auto-configuration for each real transport: CTG, IBM MQ, and HTTP. Each one creates the `Transport` bean from `bookmap.transport.*` properties. Then a Spring Boot application adds a transport module and some properties, and it gets a `BookmapClient` bean with no Java configuration.

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

## 2. Goals, non-goals, and anti-goals

Goals:

- One auto-configuration for each transport. It creates the `Transport` bean from properties, and declares `@AutoConfiguration(before = BookmapAutoConfiguration.class)`.
- A clear selection of the transport, also when more than one transport module is on the class path.
- A binding for each builder setting that has a text value: routes, server, queues, URIs, headers, timeouts, pool sizes, and credentials.
- A customizer bean for each transport, for the settings that are not text: a header supplier, an `HttpClient`, and others.
- Mutual TLS for HTTP with a Spring Boot SSL bundle.
- The configuration metadata for each new property, with a description and a default value. The IDE completes the properties.
- 100% line and branch coverage, with `ApplicationContextRunner` tests. No test uses a mainframe, a queue manager, or an IBM library.

Non-goals (possible later):

- More than one transport in one application, for example CTG for some operations and HTTP for others. The application can declare its own `Transport` bean for this.
- A starter module for each transport.
- Spring Boot health indicators and metrics for the transports.
- The creation of an IBM `MQConnectionFactory` from BookMap properties. The connection factory comes from the context (see 5.4).

Anti-goals (failure, even if the code works):

- `bookmap-spring-boot` adds a transport module, `jakarta.jms-api`, or an IBM artifact to its POM.
- The application starts without a `BookmapClient` bean, and with no message, when `bookmap.transport.type` is set.
- A password goes into a log, an exception message, or the `toString()` of a properties object.
- An auto-configuration loads an absent class and stops the application, when the user does not use that transport.
- A change in the main code of a transport module (`bookmap-transport-ctg`, `-mq`, `-http`). The only change is the build file of the CTG module, which shares its test stub (5.10).

## 3. Constraints

- Spring Boot 4.1.2, from the version catalog. Java 21. No reflection at runtime in BookMap code. Spring Boot itself uses reflection; this is accepted in `bookmap-spring-boot` (main design, 5.12).
- The transport modules are separate published modules. `bookmap-transport-ctg` is on `main` (PR #71). `bookmap-transport-mq` and `bookmap-transport-http` have an approved design. Their code is in progress on other branches.
- The transport builders have no getters. A test sees a setting only through the behavior of the transport.
- The IBM CTG library is not in the build. The CTG transport module compiles against a stub that is not published.
- Dependency verification is on. Each new test dependency needs `scripts/update-verification-metadata.sh`.

## 4. Approaches

The author decided three points.

### 4.1 The selection of the transport

| Option | Trade-off |
|---|---|
| **A. An explicit type (selected).** | The property `bookmap.transport.type` has the value `ctg`, `mq`, or `http`. Without this property, no transport auto-configuration runs. It is never ambiguous, and the IDE proposes the values. One more line of configuration. |
| B. By the configured section. | A transport runs when its section is set. Two sections cause a failure at startup. Less to write, but more condition code, and a less visible rule. |
| C. The first match wins. | A fixed order. A second transport is ignored with no message. |

The author selected A.

### 4.2 The module layout

| Option | Trade-off |
|---|---|
| **A. In `bookmap-spring-boot`, with `compileOnly` transports (selected).** | The usual Spring Boot pattern. `@ConditionalOnClass` guards each auto-configuration. The user adds the transport module and the vendor library. No new published module. |
| B. A module for each transport (`bookmap-spring-boot-ctg`, and so on). | One dependency is sufficient for the user. Three more published modules, with their release, documentation, and coverage setup. |

The author selected A.

### 4.3 TLS and other settings of the HTTP client

| Option | Trade-off |
|---|---|
| **A. An SSL bundle and a customizer (selected).** | `bookmap.transport.http.ssl.bundle` names a `spring.ssl.bundle.*` bundle. The auto-configuration makes an `HttpClient` with the `SSLContext` of the bundle. A customizer bean sets the other settings. |
| B. A customizer only. | Less BookMap code. Each application writes an `HttpClient` with its own `SSLContext`. |

The author selected A.

## 5. Design

### 5.1 Module and packages

`bookmap-spring-boot/build.gradle.kts`:

```kotlin
compileOnly(project(":bookmap-transport-ctg"))
compileOnly(project(":bookmap-transport-mq"))
compileOnly(project(":bookmap-transport-http"))
testImplementation(project(":bookmap-transport-ctg"))
testImplementation(project(":bookmap-transport-mq"))
testImplementation(project(":bookmap-transport-http"))
```

Each increment adds only the lines of its transport. A `compileOnly` dependency does not go into the POM or into the Gradle module metadata.

| Package | Content |
|---|---|
| `pt.dazz.bookmap.spring` | `BookmapAutoConfiguration`, `BookmapProperties`, `BookmapTransportProperties`, `TransportType`. |
| `pt.dazz.bookmap.spring.ctg` | `CtgTransportAutoConfiguration`, `CtgTransportProperties`, `CtgTransportBuilderCustomizer`. |
| `pt.dazz.bookmap.spring.mq` | `MqTransportAutoConfiguration`, `MqTransportProperties`, `MqTransportBuilderCustomizer`. |
| `pt.dazz.bookmap.spring.http` | `HttpTransportAutoConfiguration`, `HttpTransportProperties`, `HttpTransportBuilderCustomizer`. |

`AutoConfiguration.imports` lists the three new classes.

### 5.2 The selection of the transport

A new record `BookmapTransportProperties`, with the prefix `bookmap.transport`, has one field: `type`, of the enum `TransportType { CTG, MQ, HTTP }`. Thus, the property is `bookmap.transport.type`. `BookmapAutoConfiguration` enables it. `BookmapProperties` does not change, so its public constructor stays the same. Spring Boot accepts `ctg`, `CTG`, and the other cases. An incorrect value, for example `ctgg`, stops the application at startup, with a binding error.

Each transport auto-configuration has these conditions, in this order:

```java
@AutoConfiguration(before = BookmapAutoConfiguration.class)
@ConditionalOnClass(CtgTransport.class)
@ConditionalOnProperty(name = "bookmap.transport.type", havingValue = "ctg")
@EnableConfigurationProperties(CtgTransportProperties.class)
public class CtgTransportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Transport.class)
    CtgTransport ctgTransport(...) { ... }
}
```

- `@ConditionalOnClass` is on the class. Thus, Spring Boot does not load the class when the transport module is absent.
- `havingValue` is not case-sensitive in Spring Boot.
- `@ConditionalOnMissingBean(Transport.class)`: a `Transport` bean of the application wins, for example a `SimulatorTransport` in a test.
- The bean type is the transport class, for example `CtgTransport`. Thus, the application can inject it. Spring Boot calls `close()` of the transport when the context closes (inferred destroy method).

**The missing transport.** `BookmapAutoConfiguration` gets one more bean method. It runs only when `bookmap.transport.type` is set and no `Transport` bean exists. It throws `IllegalStateException`, and the application does not start. The bean has `@Lazy(false)`. Thus, the check also runs with `spring.main.lazy-initialization=true`. The message names the module and the vendor library, for example:

`bookmap.transport.type=ctg, but no Transport bean exists. Add bookmap-transport-ctg and the IBM library ctgclient.jar to the class path.`

`BookmapAutoConfiguration` runs after the transport auto-configurations. Thus, the condition sees their beans.

### 5.3 CTG

Conditions: `CtgTransport` on the class path, and `bookmap.transport.type=ctg`.

Beans:

| Bean | Condition | Value |
|---|---|---|
| `EciConnector` | No `EciConnector` bean, and `com.ibm.ctg.client.JavaGateway` on the class path. | `JavaGatewayConnector` from `url`, `port`, `socket-connect-timeout`, and `protocol-properties`. |
| `CtgTransport` | No `Transport` bean. | `CtgTransport.builder()` with the `EciConnector` bean, the properties, then each customizer. |

The `JavaGatewayConnector` bean is in a nested configuration class with `@ConditionalOnClass(name = "com.ibm.ctg.client.JavaGateway")`. If the IBM library is absent and no `EciConnector` bean exists, the `CtgTransport` bean method throws `IllegalStateException`: `No EciConnector bean. Add the IBM library ctgclient.jar, or declare an EciConnector bean.`

Properties, prefix `bookmap.transport.ctg`:

| Property | Type | Default | Builder method |
|---|---|---|---|
| `url` | `String` | none. Mandatory for `JavaGatewayConnector`. | `JavaGatewayConnector.Builder.url` |
| `port` | `int` | `2006` | `port` |
| `socket-connect-timeout` | `Duration` | `10s` | `socketConnectTimeout` |
| `protocol-properties` | `Map<String, String>` | empty | `protocolProperties` |
| `server` | `String` | none: the default server of CTG. | `CtgTransport.Builder.server` |
| `default-transaction` | `String` | none: the mirror transaction. | `defaultTransaction` |
| `userid` | `String` | none: no credentials. | `credentials` |
| `password` | `String` | none | `credentials` |
| `timeout` | `Duration` | `30s` | `timeout` |
| `max-connections` | `int` | `10` | `maxConnections` |
| `connection-wait-timeout` | `Duration` | `30s` | `connectionWaitTimeout` |
| `routes` | `Map<String, Route>` | none. At least one route. | `routes` |
| `routes[<operation>].program` | `String` | none. Mandatory. | `CtgRoute.program` |
| `routes[<operation>].transaction` | `String` | none: the default transaction. | `CtgRoute.transaction` |

Rules:

- `userid` and `password` go to `credentials(userid, password)` when `userid` is set. A `password` without a `userid` stops the application: `bookmap.transport.ctg.password is set, but bookmap.transport.ctg.userid is not set.`
- The properties record has its own nested record `Route(String program, String transaction)`. The auto-configuration converts it to `CtgRoute`. Thus, the configuration metadata has a description for each field, and the binding does not depend on the constructor of `CtgRoute`.
- The builders check the values: for example, a `timeout` with a fraction of a second, or a program name with 9 characters. A check failure stops the application. The cause is the `IllegalArgumentException` or the `IllegalStateException` of the builder.

### 5.4 IBM MQ

Conditions: `MqTransport` on the class path, and `bookmap.transport.type=mq`.

The MQ transport needs a `jakarta.jms.ConnectionFactory`. This design takes it from the context. Two usual sources:

- The IBM starter `com.ibm.mq:mq-jms-spring-boot-starter`. It creates an `MQConnectionFactory` from the `ibm.mq.*` properties: the host, the port, the queue manager, the channel, and the credentials.
- The Spring Boot Artemis or JNDI auto-configuration, or a `ConnectionFactory` bean of the application.

Thus, BookMap needs no IBM class, and no MQ connection property of its own.

Beans:

| Bean | Condition | Value |
|---|---|---|
| `MqConnector` | No `MqConnector` bean, and one `ConnectionFactory` candidate (`@ConditionalOnSingleCandidate`). | `JmsConnector` with the factory, `user`, `password`, and `mq-native-messages`. |
| `MqTransport` | No `Transport` bean. | `MqTransport.builder()` with the `MqConnector` bean, the properties, then each customizer. |

Order: `@AutoConfiguration(before = BookmapAutoConfiguration.class, afterName = {...})`. The `afterName` list contains the IBM starter auto-configuration and the Spring Boot JMS, Artemis, and JNDI auto-configurations. Thus, the `ConnectionFactory` bean exists when the condition runs. The builder verifies the class names of Spring Boot 4.1 and of the IBM starter.

If no `MqConnector` bean can be created, the `MqTransport` bean method throws `IllegalStateException`: `No MqConnector bean. Declare one jakarta.jms.ConnectionFactory bean, for example with mq-jms-spring-boot-starter, or declare an MqConnector bean.`

Properties, prefix `bookmap.transport.mq`:

| Property | Type | Default | Builder method |
|---|---|---|---|
| `user` | `String` | none: `createConnection()` without credentials. | `JmsConnector.Builder.credentials` |
| `password` | `String` | none | `credentials` |
| `mq-native-messages` | `boolean` | `true` | `mqNativeMessages` |
| `default-reply-queue` | `String` | none: a temporary queue. | `MqTransport.Builder.defaultReplyQueue` |
| `correlation` | `MqCorrelation` | `message-id` | `correlation` |
| `timeout` | `Duration` | `30s` | `timeout` |
| `persistent` | `boolean` | `false` | `persistent` |
| `max-sessions` | `int` | `10` | `maxSessions` |
| `session-wait-timeout` | `Duration` | `30s` | `sessionWaitTimeout` |
| `routes[<operation>].request-queue` | `String` | none. Mandatory. | `MqRoute.requestQueue` |
| `routes[<operation>].reply-queue` | `String` | none: the default reply queue. | `MqRoute.replyQueue` |

Rules:

- `user` and `password` follow the rule of CTG: a `password` without a `user` stops the application. Usually, the credentials are in the connection factory (`ibm.mq.user`, `ibm.mq.password`). Set `bookmap.transport.mq.user` only to use other credentials.
- The table follows the approved MQ design. If the MQ code changes a builder method, this table changes with it.

**A shared connection.** Spring Boot and the IBM starter can wrap the factory in a `CachingConnectionFactory`. Then all the sessions of the transport use one connection. The transport still works. But a broken connection affects all the sessions, not only one. The documentation tells this, and tells how to stop it: `spring.jms.cache.enabled=false`, or the pool of the IBM starter.

### 5.5 HTTP

Conditions: `HttpTransport` on the class path, and `bookmap.transport.type=http`. The HTTP module has no vendor library. Thus, the `type` property is the only guard.

Beans:

| Bean | Condition | Value |
|---|---|---|
| `HttpTransport` | No `Transport` bean. | `HttpTransport.builder()` with the properties, the client of the SSL bundle, then each customizer. |

Properties, prefix `bookmap.transport.http`:

| Property | Type | Default | Builder method |
|---|---|---|---|
| `base-uri` | `URI` | none | `baseUri` |
| `headers` | `Map<String, String>` | empty | `header` |
| `username` | `String` | none | `basicAuth` |
| `password` | `String` | none | `basicAuth` |
| `abend-header` | `String` | none: no abend check. | `abendHeader` |
| `timeout` | `Duration` | `30s` | `timeout` |
| `connect-timeout` | `Duration` | none: the default of the transport (10 seconds). | `connectTimeout`, or the client of the SSL bundle |
| `ssl.bundle` | `String` | none | see below |
| `routes[<operation>].uri` | `String` | none. Mandatory. | `HttpRoute.uri` |
| `routes[<operation>].method` | `String` | none: `POST`. | `HttpRoute.method` |
| `routes[<operation>].headers` | `Map<String, String>` | empty | `HttpRoute.headers` |

Rules:

- `username` and `password` go to `basicAuth(username, password)`. Each one without the other stops the application.
- `connect-timeout` has no default in the properties. The auto-configuration calls `connectTimeout` only when the property is set, and only when no SSL bundle is set. Thus, a customizer can set an `HttpClient` without a conflict in `build()`.
- If a customizer sets an `HttpClient` and `connect-timeout` is also set without an SSL bundle, `build()` throws `IllegalStateException` (HTTP design, 5.3). The application does not start. This is correct: the two settings are in conflict.

**The SSL bundle.** If `ssl.bundle` is set, the auto-configuration gets the bundle from `SslBundles`. It makes an `HttpClient` like the client of the transport: HTTP/1.1, `Redirect.NEVER`, the connect timeout (the property, else 10 seconds), and the `SSLContext` of the bundle. The protocols and the ciphers of the bundle options go into the `SSLParameters` of the client. It gives the client to `httpClient`. An unknown bundle name stops the application, with the `NoSuchSslBundleException` of Spring Boot.

The transport does not close a client of the caller. Thus, the auto-configuration declares the client as a bean with `@Bean(destroyMethod = "shutdown")`. Spring Boot calls `shutdown()` when the context closes. Do not use the inferred destroy method: it selects `close()`, and `close()` can wait for a long time (HTTP design, 8).

### 5.6 The customizers

Each transport has a functional interface:

```java
@FunctionalInterface
public interface HttpTransportBuilderCustomizer {
    void customize(HttpTransport.Builder builder);
}
```

The auto-configuration calls each customizer bean after the properties, and before `build()`. The order is the `@Order` or `Ordered` order of the beans. A customizer can replace a value of a property, for example the client of the SSL bundle. The rules of `build()` still apply (5.5).

Examples: a `headerSupplier` with a bearer token, an `HttpClient` with a proxy, and a route with a computed value.

```java
@Bean
HttpTransportBuilderCustomizer bearerToken(TokenService tokens) {
    return builder -> builder.headerSupplier(
        () -> Map.of("Authorization", "Bearer " + tokens.current()));
}
```

### 5.7 Secrets

- A password is a `String` property. Spring Boot binds it from any property source: an environment variable (`BOOKMAP_TRANSPORT_CTG_PASSWORD`), a configuration tree (`spring.config.import=configtree:/run/secrets/`), or a vault.
- `toString()` of each properties record shows `password=******` when the password is set. It never shows the value.
- The metadata description of each password tells the reader to use an environment variable or a secret store, not a plain value in `application.properties`.
- The Actuator endpoint `configprops` hides the values by default in Spring Boot 4.1. BookMap does not change this.

**CAUTION:** Do not write a password as a plain value in `application.properties` in the source repository. Each person who can read the repository can then read the password.

### 5.8 The route map

- The key of the map is the operation name, for example `GetCustomerOrders`. An operation name is case-sensitive.
- Write the key in brackets: `routes[GetCustomerOrders].program`. In YAML: `"[GetCustomerOrders]":`. The brackets keep the key as it is.
- An environment variable cannot give a route: Spring Boot changes its key to lowercase. Use a properties file, a YAML file, or `SPRING_APPLICATION_JSON`.
- A route without its mandatory field (`program`, `request-queue`, or `uri`) stops the application. The message gives the operation.

The builder of this design verifies the case rules with a test, also without brackets, and writes the result in the documentation.

### 5.9 The configuration metadata

The configuration processor makes the metadata of each properties record. The text of each `@param` tag is the description (see the comment in `BookmapProperties`). `additional-spring-configuration-metadata.json` adds:

- A hint for `bookmap.transport.type` with a description of each value.
- A hint for `bookmap.transport.http.ssl.bundle` with the provider `handle-as` of Spring Boot, if the IDE supports it. Else, no hint.

`ConfigurationMetadataTest` checks for each increment: the list of the keys, the type, the default value, and the start of the description of each new property. `listsOnlyTheBookmapProperties` gets the new keys.

### 5.10 The CTG stub for the tests of other modules

`JavaGatewayConnector.open()` makes the JVM load `JavaGateway`, `GatewayRequest`, `ECIRequest`, and `ECIReturnCodes`. Thus, the spring-boot tests need the full stub of the CTG module.

The author decided to share the stub of the CTG module, not to copy it. The Gradle plugin `java-test-fixtures` cannot do this: the main code of the CTG module compiles against the stub, and the test fixtures depend on the main code. Gradle reports a cycle. Thus:

- `bookmap-transport-ctg` keeps the source set `ctgStub`. A task `ctgStubJar` puts the stub classes in a jar.
- A consumable configuration `ctgStubElements` gives this jar to other modules. It is not a variant of the `java` component. Thus, it does not go into the POM or into the Gradle module metadata.
- `bookmap-spring-boot` uses `testImplementation(project(path = ":bookmap-transport-ctg", configuration = "ctgStubElements"))`.
- The check `verifyNoIbmClasses` and the JaCoCo rule of the CTG module do not change.

This change is in PR 1, in its own commit. It does not change the main code or the published files of the CTG module.

## 6. Increments

| PR | Content | Start | Last line of the PR description |
|---|---|---|---|
| 1 | This design. `TransportType`, the missing-transport check, the CTG auto-configuration, its metadata, and the documentation. | After PR #71 is on `main`. | `Part of #29 (CTG).` |
| 2 | The MQ auto-configuration, its metadata, and the documentation. | After the MQ transport is on `main`. | `Part of #29 (MQ).` |
| 3 | The HTTP auto-configuration, the SSL bundle, its metadata, and the documentation. | After the HTTP transport is on `main`. | `Closes #29.` |

PR 2 and PR 3 can occur in the other order. The last PR says `Closes #29`.

## 7. Tests

All tests use `ApplicationContextRunner`, as `BookmapAutoConfigurationTest` does.

For each transport:

- No `bookmap.transport.type`: no transport bean, no client bean.
- Another type: no bean of this transport.
- The transport module is absent (`FilteredClassLoader`): no bean, and the missing-transport check fails with the message of 5.2.
- A `Transport` bean of the application: it wins.
- A full exchange through a `BookmapClient`: the properties reach the transport. For CTG, a fake `EciConnector` bean records the `EciCall`. For MQ, a fake `MqConnector` bean records the `MqRequest`. For HTTP, a JDK `HttpServer` on a local port records the request.
- Each default and each property of the table.
- Each rule that stops the application.
- The customizer order.
- `close()` of the transport when the context closes.
- `toString()` of the properties does not contain the password.

Special tests:

- CTG: the `JavaGatewayConnector` bean. The tests use the stub of the CTG module (5.10). `open()` of the connector creates the stub `JavaGateway`, and the test reads its constructor arguments. Thus, the test checks `url`, `port`, `socket-connect-timeout`, and `protocol-properties` with no IBM library. A second test removes the class with `FilteredClassLoader`, and checks the message of 5.3.
- MQ: a fake `jakarta.jms.ConnectionFactory` bean gives a `JmsConnector`. Two factory beans and no primary give no connector, and the message of 5.4.
- HTTP: an SSL bundle from test PEM files in `src/test/resources` and a JDK `HttpsServer`. A test checks that the context close calls `shutdown()` of the client.

## 8. Documentation

- `docs/wiki/Spring-Boot.md`: a section for each transport, with the properties and an example. Remove the sentence "BookMap has no real transport yet".
- `docs/wiki/CICS-Transaction-Gateway.md`, `IBM-MQ.md`, and `HTTP-transport.md` (from the transport PRs): a short "Spring Boot" section with a link.
- `docs/design/2026-11-10-bookmap-design.md`, section 5.12: one sentence about the transport auto-configurations and `bookmap.transport.type`.
- `ROADMAP.md`: remove #29 in the last PR.
- `README.md`: the "Project status" section, if it names the Spring Boot features.

## 9. Calls made by the builder of this design

These points were not decided by the author. They are proposals of this design:

- The property name `bookmap.transport.type`, the enum `TransportType`, and the record `BookmapTransportProperties`. A new component in `BookmapProperties` would change its public constructor.
- The missing-transport check in `BookmapAutoConfiguration` (5.2).
- The connector bean (`EciConnector`, `MqConnector`) is separate from the transport bean, so the application can replace only the connector.
- The MQ connection factory comes from the context, not from BookMap properties.
- The properties records have their own route records. They do not bind the route records of the transport modules.
- The defaults in the properties are the same values as the defaults of the builders, so the IDE shows them. The exception is `connect-timeout` of HTTP (5.5).
- A password without a user ID stops the application.
- The auto-configuration shuts down the `HttpClient` of the SSL bundle with `shutdown()`, not `close()`.
- A customizer `HttpClient` together with `connect-timeout` stops the application, as in the HTTP builder.

## 10. Left to the implementation

- The exact text of the messages, in the format of this design.
- The class names in `afterName` for MQ, verified against Spring Boot 4.1 and the IBM starter.
- The Javadoc of the public types.
