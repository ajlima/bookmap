<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/copymapper-logo-dark.svg">
    <img src="docs/assets/copymapper-logo-light.svg" alt="CopyMapper" width="470">
  </picture>
</p>

<p align="center">
  <strong>Map COBOL books to Java objects. No reflection. No middleware lock-in.</strong>
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-0f62fe">
  <img alt="Gradle 9" src="https://img.shields.io/badge/Gradle-9.9-02303A">
  <img alt="Coverage 100%" src="https://img.shields.io/badge/coverage-100%25-brightgreen">
  <img alt="Version 0.28.0" src="https://img.shields.io/badge/version-0.28.0-f89820">
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

<p align="center">
  <a href="https://github.com/dazz-pt/copymapper/wiki">Wiki</a> ·
  <a href="https://github.com/dazz-pt/copymapper/wiki/Getting-started">Getting started</a> ·
  <a href="https://github.com/dazz-pt/copymapper/wiki/Annotations-reference">Annotations</a> ·
  <a href="https://github.com/dazz-pt/copymapper/wiki/Transports-and-simulator">Transports</a> ·
  <a href="https://github.com/dazz-pt/copymapper/wiki/Copybook-generator">Copybook generator</a> ·
  <a href="ROADMAP.md">Roadmap</a>
</p>

---

CopyMapper maps COBOL copybooks (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection.

CopyMapper needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.

The full documentation is in the **[wiki](https://github.com/dazz-pt/copymapper/wiki)**.

Before `0.28.0`, the library had the name BookMap. To update a project, see [Migrate to CopyMapper](https://github.com/dazz-pt/copymapper/wiki/Migrate-to-CopyMapper).

## Why CopyMapper

- **No reflection.** The processor generates the codec at compile time. The runtime does not use reflection on your classes.
- **Errors at compile time.** An incorrect layout, `pic`, or type combination stops the build, not production.
- **Few annotations.** Conventions and `pic` strings keep each field to one line, close to the copybook.
- **Automatic pagination.** The client calls the program again until `hasContinuation()` returns `false`, and returns the full list.
- **No middleware lock-in.** A transport is one interface. The simulator lets you test without a mainframe.
- **No silent data loss.** A value that does not fit causes an exception. The codec never truncates or rounds.
- **Classes from copybooks.** The copybook generator writes the annotated classes from a COBOL copybook.

## Installation

The group is `pt.dazz.copymapper` and the version is `0.28.0`. The modules are on Maven Central.

Gradle:

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-core:0.28.0")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:0.28.0")
    testImplementation("pt.dazz.copymapper:copymapper-transport-simulator:0.28.0")
}
```

Maven:

```xml
<dependency>
  <groupId>pt.dazz.copymapper</groupId>
  <artifactId>copymapper-core</artifactId>
  <version>0.28.0</version>
</dependency>
```

With Maven, add `copymapper-processor` to the `annotationProcessorPaths` of the `maven-compiler-plugin`.
See [Getting started](https://github.com/dazz-pt/copymapper/wiki/Getting-started) for Lombok, charsets, and the client builder.

### Verify an artifact

Each release after `0.6.0` has a build provenance attestation for each JAR, POM, and Gradle module file on Maven Central.
The attestation shows that the release workflow of this repository built the file.
Use the [GitHub CLI](https://cli.github.com/) to verify a file. Sign in with `gh auth login` first.

The download path and the command depend on the release.
The repository moved from `ajlima/bookmap` to `dazz-pt/copymapper` after the release `0.26.0`.
The library changed its name from BookMap to CopyMapper in the release `0.28.0`.

| Releases | Group and file | Identity of the workflow |
|---|---|---|
| `0.26.0` and earlier | `pt.dazz.bookmap`, `bookmap-core-X.Y.Z.jar` | `--owner ajlima`, workflow of `ajlima/bookmap` |
| `0.27.0` | `pt.dazz.bookmap`, `bookmap-core-X.Y.Z.jar` | `--repo dazz-pt/copymapper` |
| `0.28.0` and later | `pt.dazz.copymapper`, `copymapper-core-X.Y.Z.jar` | `--repo dazz-pt/copymapper` |

Replace `X.Y.Z` with the version.

For the release `0.28.0` and each later release:

```bash
curl -fsSLO https://repo1.maven.org/maven2/pt/dazz/copymapper/copymapper-core/X.Y.Z/copymapper-core-X.Y.Z.jar
```

```bash
gh attestation verify copymapper-core-X.Y.Z.jar --repo dazz-pt/copymapper --signer-workflow dazz-pt/copymapper/.github/workflows/release.yml
```

For the release `0.27.0`:

```bash
curl -fsSLO https://repo1.maven.org/maven2/pt/dazz/bookmap/bookmap-core/0.27.0/bookmap-core-0.27.0.jar
```

```bash
gh attestation verify bookmap-core-0.27.0.jar --repo dazz-pt/copymapper --signer-workflow dazz-pt/copymapper/.github/workflows/release.yml
```

For the release `0.26.0` and each earlier release:

```bash
curl -fsSLO https://repo1.maven.org/maven2/pt/dazz/bookmap/bookmap-core/X.Y.Z/bookmap-core-X.Y.Z.jar
```

```bash
gh attestation verify bookmap-core-X.Y.Z.jar --owner ajlima --signer-workflow ajlima/bookmap/.github/workflows/release.yml
```

The command fails if no attestation of this workflow has the same SHA-256 digest as the file.

## Quick start: from Java to CICS

This example calls the CICS program `PGMORD01` through the CICS Transaction Gateway (CTG). The program lists the orders of a customer.
The steps are the same for the other transports. Only the transport object changes.

### 1. The copybook

`PGMORD01` uses this COMMAREA:

```cobol
       01  GET-CUSTOMER-ORDERS.
           05  CUSTOMER-ID             PIC X(10).
           05  RESTART-KEY             PIC X(12).
           05  RETURN-CODE             PIC X(4).
           05  RETURN-MESSAGE          PIC X(60).
           05  MORE-DATA               PIC X.
           05  ORDER-COUNT             PIC 9(3).
           05  ORDERS OCCURS 0 TO 50 TIMES
                   DEPENDING ON ORDER-COUNT.
               10  ORDER-NUMBER        PIC X(10).
               10  TOTAL-AMOUNT        PIC S9(13)V99 COMP-3.
               10  ORDER-DATE          PIC 9(8).
```

### 2. The book class

`GetCustomerOrders` maps the copybook in convention style. Each field is one line, with the `pic` of the copybook.

```java
@Operation
public class GetCustomerOrders implements Continuable, Validatable {

    @Field(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @Field(order = 20, pic = "X(12)")
    private String restartKey;

    @Field(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @Field(order = 40, pic = "X(60)", direction = OUTPUT)
    private String returnMessage;

    @Field(order = 50, pic = "X", direction = OUTPUT)
    private String moreData;

    @Field(order = 60, pic = "9(3)")
    private int orderCount;                       // the count of ORDERS (DEPENDING ON)

    @Occurs(order = 70, maxOccurs = 50)
    private List<Order> orders;

    @Override
    public void validate() {                      // the client calls it after each response
        if (!"0000".equals(returnCode)) {
            throw new OrderQueryException(returnCode, returnMessage);
        }
    }

    @Override
    public boolean hasContinuation() {            // "Y": the client calls the program again
        return "Y".equals(moreData);
    }

    // getters and setters: see copymapper-examples
}
```

```java
public class Order {

    @Field(order = 10, pic = "X(10)")
    private String orderNumber;

    @Field(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal totalAmount;

    @Field(order = 30)
    private LocalDate orderDate;                  // PIC 9(8), pattern yyyyMMdd by default
}
```

`OrderQueryException` extends `BusinessException`. The annotation processor generates the codec of `GetCustomerOrders` at compile time.
You can also generate the classes from the copybook. See [Copybook generator](#copybook-generator).

### 3. The dependencies

Add the core, the processor, and the CTG transport. The IBM library `ctgclient.jar` has an IBM license, and it is not on Maven Central.
Get it from your CICS TG installation, and add it yourself.

Gradle:

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-transport-ctg:0.28.0")       // includes copymapper-core
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:0.28.0")
    runtimeOnly(files("libs/ctgclient.jar"))                             // or the artifact in your company repository
    testImplementation("pt.dazz.copymapper:copymapper-transport-simulator:0.28.0")
}
```

Maven:

```xml
<dependency>
  <groupId>pt.dazz.copymapper</groupId>
  <artifactId>copymapper-transport-ctg</artifactId>
  <version>0.28.0</version>
</dependency>
<!-- The IBM library, from the repository of your company. Use your own coordinates. -->
<dependency>
  <groupId>com.example.ibm</groupId>
  <artifactId>ctgclient</artifactId>
  <version>9.3</version>
  <scope>runtime</scope>
</dependency>
```

With Maven, add `copymapper-processor` to the `annotationProcessorPaths` of the `maven-compiler-plugin`.

### 4. The transport and the client

```java
CtgTransport transport = CtgTransport.builder()
    .connector(JavaGatewayConnector.builder()
        .url("tcp://ctg.example.com")                          // the Gateway daemon
        .port(2006)
        .build())
    .server("CICSPROD")                                        // the CICS server in the CTG configuration
    .credentials("ORDUSER", System.getenv("CTG_PASSWORD"))     // the CICS user ID and password
    .route("GetCustomerOrders", "PGMORD01", "TX01")            // operation -> program and transaction
    .build();

CopyMapperClient client = CopyMapperClient.builder()
    .transport(transport)
    .build();
```

A route maps the operation name to a CICS program and a transaction. The operation name is the simple class name, or the value of `@Operation`.
`CopyMapperClient` and `CtgTransport` are thread-safe. Make one of each for the application. Close the transport when the application stops.

### 5. The call and the response

```java
GetCustomerOrders request = new GetCustomerOrders();
request.setCustomerId("CUST000042");
request.setRestartKey("");

try {
    GetCustomerOrders result = client.execute(request);   // all pages, while hasContinuation() is true
    for (Order order : result.getOrders()) {
        System.out.println(order.getOrderNumber() + " " + order.getOrderDate() + " " + order.getTotalAmount());
    }
} catch (OrderQueryException e) {          // validate(): the program returned an error code
    System.out.println("Refused: " + e.returnCode());
} catch (RemoteAbendException e) {         // the CICS program stopped abnormally
    System.out.println("Abend " + e.abendCode());
} catch (TransportException e) {           // a timeout, no connection, no route, or another failure
    System.out.println("Technical failure: " + e.getMessage());
}
```

The client encodes the request in EBCDIC (`IBM037` by default), and CTG sends it to `PGMORD01` in a COMMAREA.
The client decodes the response into the result object. It calls `validate()`, and it calls the program again while `hasContinuation()` returns `true`.
The request object is not thread-safe. It belongs to the caller.

**WARNING:** Do not retry a call after a timeout or a failure during the call without a check. CICS can have run the program and changed data.

### 6. The same flow in Spring Boot

Add `copymapper-spring-boot` and the CTG transport:

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-spring-boot:0.28.0")
    implementation("pt.dazz.copymapper:copymapper-transport-ctg:0.28.0")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:0.28.0")
    runtimeOnly(files("libs/ctgclient.jar"))
}
```

Set the transport in `application.properties`. The auto-configuration creates the `CtgTransport` and the `CopyMapperClient` beans:

```properties
copymapper.transport.type=ctg
copymapper.transport.ctg.url=tcp://ctg.example.com
copymapper.transport.ctg.server=CICSPROD
copymapper.transport.ctg.userid=ORDUSER
copymapper.transport.ctg.password=${CTG_PASSWORD}
copymapper.transport.ctg.routes[GetCustomerOrders].program=PGMORD01
copymapper.transport.ctg.routes[GetCustomerOrders].transaction=TX01
```

Inject the client:

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

**CAUTION:** Do not write the password as a plain value in `application.properties`. Each person who can read the repository can then read the password.

See [Spring Boot](https://github.com/dazz-pt/copymapper/wiki/Spring-Boot#cics-transaction-gateway) for all properties, the secrets, and the customizer.
For an HTTP endpoint, set `copymapper.transport.type=http` and the `copymapper.transport.http.*` properties. See [Spring Boot](https://github.com/dazz-pt/copymapper/wiki/Spring-Boot#http).
For IBM MQ, set `copymapper.transport.type=mq` and the `copymapper.transport.mq.*` properties. See [Spring Boot](https://github.com/dazz-pt/copymapper/wiki/Spring-Boot#ibm-mq).

### 7. Test without CICS

The simulator replaces the transport in tests. It uses the same book classes, and it needs no mainframe:

```java
SimulatorTransport simulator = SimulatorTransport.builder().build();
CopyMapperClient client = CopyMapperClient.builder().transport(simulator).build();

simulator.register(GetCustomerOrders.class, call -> {
    call.setOrders(List.of());
    call.setReturnCode("0000");
    call.setReturnMessage("OK");
    call.setMoreData("N");
});
```

See [Transports and simulator](https://github.com/dazz-pt/copymapper/wiki/Transports-and-simulator) for the failures that the simulator can give.

### Other transports

- [HTTP transport](https://github.com/dazz-pt/copymapper/wiki/HTTP-transport): `HttpTransport` sends the book bytes to an HTTP endpoint with a binary body.
- [IBM MQ](https://github.com/dazz-pt/copymapper/wiki/IBM-MQ): `MqTransport` puts the book bytes on a request queue, and gets the reply from a reply queue.

[Transports and simulator](https://github.com/dazz-pt/copymapper/wiki/Transports-and-simulator#choose-a-transport) compares the transports, and tells how to write your own transport.

## Copybook generator

The copybook generator reads a COBOL copybook and writes the annotated Java classes. Use one of these tools:

- **[Command line](https://github.com/dazz-pt/copymapper/wiki/Copybook-generator-CLI)**: `java -jar copymapper-copybook-cli-<version>-all.jar generate ORDERS.cpy --package com.example.orders --output-dir src/main/java`
- **[Gradle plugin](https://github.com/dazz-pt/copymapper/wiki/Copybook-generator-Gradle-plugin)**: `id("pt.dazz.copymapper.copybook") version "0.28.0"`. It generates the classes in each build.
- **[Maven plugin](https://github.com/dazz-pt/copymapper/wiki/Copybook-generator-Maven-plugin)**: the goal `generate` of `pt.dazz.copymapper:copymapper-copybook-maven-plugin`. It generates the classes in each build.

See [Copybook generator](https://github.com/dazz-pt/copymapper/wiki/Copybook-generator) for the options file and the mapping rules.

## Documentation

| Topic | Wiki page |
|---|---|
| Installation, the first book, and the client | [Getting started](https://github.com/dazz-pt/copymapper/wiki/Getting-started) |
| `@Operation`, `@Field`, `@Occurs`, `@Defaults`, Lombok | [Annotations reference](https://github.com/dazz-pt/copymapper/wiki/Annotations-reference) |
| The six conventions, and the explicit style | [Conventions](https://github.com/dazz-pt/copymapper/wiki/Conventions) |
| `pic` strings, byte sizes, Java types, and conversion rules | [PIC syntax and type mapping](https://github.com/dazz-pt/copymapper/wiki/PIC-syntax-and-type-mapping) |
| `OCCURS DEPENDING ON`, pagination, and fixed `OCCURS` | [Collections and fixed OCCURS](https://github.com/dazz-pt/copymapper/wiki/Collections-and-fixed-OCCURS) |
| `REDEFINES` with a discriminator | [Subtypes (REDEFINES)](https://github.com/dazz-pt/copymapper/wiki/Subtypes-REDEFINES) |
| `REDEFINES` without a discriminator | [REDEFINES views](https://github.com/dazz-pt/copymapper/wiki/REDEFINES-views) |
| The exception hierarchy | [Error handling](https://github.com/dazz-pt/copymapper/wiki/Error-handling) |
| The comparison of the transports, the transport SPI, and the simulator | [Transports and simulator](https://github.com/dazz-pt/copymapper/wiki/Transports-and-simulator) |
| &nbsp;&nbsp;↳ The CICS Transaction Gateway transport | [CICS Transaction Gateway](https://github.com/dazz-pt/copymapper/wiki/CICS-Transaction-Gateway) |
| &nbsp;&nbsp;↳ The HTTP transport | [HTTP transport](https://github.com/dazz-pt/copymapper/wiki/HTTP-transport) |
| &nbsp;&nbsp;↳ The IBM MQ transport | [IBM MQ](https://github.com/dazz-pt/copymapper/wiki/IBM-MQ) |
| The Spring Boot auto-configuration | [Spring Boot](https://github.com/dazz-pt/copymapper/wiki/Spring-Boot) |
| The copybook generator, the CLI, and the build plugins | [Copybook generator](https://github.com/dazz-pt/copymapper/wiki/Copybook-generator) |
| Frequent problems | [FAQ and troubleshooting](https://github.com/dazz-pt/copymapper/wiki/FAQ-and-troubleshooting) |
| The build, the commits, and the releases | [Contributing](https://github.com/dazz-pt/copymapper/wiki/Contributing) |

The source of the wiki is the folder [`docs/wiki/`](docs/wiki). Change the wiki in a pull request, not in the web editor.

## Modules

| Module | Purpose |
|---|---|
| `copymapper-core` | Annotations, `CopyMapperClient`, the transport SPI, exceptions, and the codec runtime. |
| `copymapper-processor` | The annotation processor. It generates one codec for each `@Operation` class. |
| `copymapper-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `copymapper-transport-ctg` | A transport for CICS programs through the CICS Transaction Gateway (ECI). Add the IBM `ctgclient.jar` yourself. |
| `copymapper-transport-http` | A transport for an HTTP endpoint that accepts the bytes of the book. It uses only the JDK HTTP client. |
| `copymapper-transport-mq` | A transport for IBM MQ request and reply queues (Jakarta Messaging). See [IBM MQ](https://github.com/dazz-pt/copymapper/wiki/IBM-MQ). |
| `copymapper-spring-boot` | Spring Boot 4 auto-configuration for `CopyMapperClient` (built and tested with 4.1.2). |
| `copymapper-copybook` | The copybook generator library: parser, model, JSON form, and Java generator. |
| `copymapper-copybook-cli` | The command line of the copybook generator. |
| `copymapper-copybook-gradle-plugin` | The Gradle plugin of the copybook generator (`pt.dazz.copymapper.copybook`). |
| `copymapper-copybook-maven-plugin` | The Maven plugin of the copybook generator (goal `generate`). |
| `copymapper-examples` | Working examples. The wiki uses them. |
| `copymapper-examples-gradle-plugin` | A standalone build that uses the Gradle plugin. |
| `copymapper-examples-maven-plugin` | A standalone multi-module Maven build that uses the Maven plugin. |
| `copymapper-native-test` | A standalone build that tests CopyMapper in a GraalVM native image. |

## Project status

CopyMapper is at version `0.28.0`. GitHub Actions builds each release and publishes it to Maven Central.
The build enforces 100% line and branch coverage for all modules except the examples.
It also checks the dependency structure (ArchUnit) and the cyclomatic complexity (PMD), and it reports the size of each module. A workflow runs the mutation tests (PIT) each night.
CopyMapper has transports for the CICS Transaction Gateway (ECI), for HTTP (binary body), and for IBM MQ. The Spring Boot module creates each transport from properties.
See [RELEASING.md](RELEASING.md) for the release cycle, and [ROADMAP.md](ROADMAP.md) for the planned features.

## License

CopyMapper is licensed under the [MIT License](LICENSE). Copyright (c) 2026 Alexandre Lima.
Keep the copyright notice and the license text in all copies or substantial portions of the software.
