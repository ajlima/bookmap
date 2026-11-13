<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/bookmap-logo-dark.svg">
    <img src="docs/assets/bookmap-logo-light.svg" alt="BookMap" width="380">
  </picture>
</p>

<p align="center">
  <strong>Map COBOL books to Java objects. No reflection. No middleware lock-in.</strong>
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-0f62fe">
  <img alt="Gradle 9" src="https://img.shields.io/badge/Gradle-9.9-02303A">
  <img alt="Coverage 100%" src="https://img.shields.io/badge/coverage-100%25-brightgreen">
  <img alt="Version 0.7.0" src="https://img.shields.io/badge/version-0.7.0-f89820">
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

<p align="center">
  <a href="https://github.com/ajlima/bookmap/wiki">Wiki</a> ·
  <a href="https://github.com/ajlima/bookmap/wiki/Getting-started">Getting started</a> ·
  <a href="https://github.com/ajlima/bookmap/wiki/Annotations-reference">Annotations</a> ·
  <a href="https://github.com/ajlima/bookmap/wiki/Copybook-generator">Copybook generator</a> ·
  <a href="ROADMAP.md">Roadmap</a>
</p>

---

BookMap maps COBOL books (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection.

BookMap needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.

The full documentation is in the **[wiki](https://github.com/ajlima/bookmap/wiki)**.

## Why BookMap

- **No reflection.** The processor generates the codec at compile time. The runtime does not use reflection on your classes.
- **Errors at compile time.** An incorrect layout, `pic`, or type combination stops the build, not production.
- **Few annotations.** Conventions and `pic` strings keep each field to one line, close to the copybook.
- **Automatic pagination.** The client calls the program again until `hasContinuation()` returns `false`, and returns the full list.
- **No middleware lock-in.** A transport is one interface. The simulator lets you test without a mainframe.
- **No silent data loss.** A value that does not fit causes an exception. The codec never truncates or rounds.
- **Classes from copybooks.** The copybook generator writes the annotated classes from a COBOL copybook.

## Installation

The group is `pt.dazz.bookmap` and the version is `0.7.0`. The modules are on Maven Central.

Gradle:

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:0.7.0")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:0.7.0")
    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator:0.7.0")
}
```

Maven:

```xml
<dependency>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-core</artifactId>
  <version>0.7.0</version>
</dependency>
```

With Maven, add `bookmap-processor` to the `annotationProcessorPaths` of the `maven-compiler-plugin`.
See [Getting started](https://github.com/ajlima/bookmap/wiki/Getting-started) for Lombok, charsets, and the client builder.

### Verify an artifact

Each release after `0.6.0` has a build provenance attestation for each JAR, POM, and Gradle module file on Maven Central.
The attestation shows that the release workflow of this repository built the file.
Use the [GitHub CLI](https://cli.github.com/) to verify a file. Sign in with `gh auth login` first.

Download the file. Replace `X.Y.Z` with the version:

```bash
curl -fsSLO https://repo1.maven.org/maven2/pt/dazz/bookmap/bookmap-core/X.Y.Z/bookmap-core-X.Y.Z.jar
```

Verify the file:

```bash
gh attestation verify bookmap-core-X.Y.Z.jar --repo ajlima/bookmap --signer-workflow ajlima/bookmap/.github/workflows/release.yml
```

The command fails if no attestation of this repository and this workflow has the same SHA-256 digest as the file.

## Quick start

`GetCustomerOrders` is a book in convention style. It lists the orders of a customer.

```java
@BookOperation
public class GetCustomerOrders implements Continuable, Validatable {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookField(order = 20, pic = "X(12)")
    private String restartKey;

    @BookField(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @BookField(order = 40, pic = "X(60)", direction = OUTPUT)
    private String returnMessage;

    @BookField(order = 50, pic = "X", direction = OUTPUT)
    private String moreData;

    @BookField(order = 60, pic = "9(3)")
    private int orderCount;

    @BookCollection(order = 70, maxOccurs = 50)
    private List<Order> orders;

    // validate(), hasContinuation(), getters, and setters: see bookmap-examples
}
```

Each order is a class with `@BookField` fields.

```java
public class Order {

    @BookField(order = 10, pic = "X(10)")
    private String orderNumber;

    @BookField(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal totalAmount;

    @BookField(order = 30)
    private LocalDate orderDate;
}
```

Send the request:

```java
BookmapClient client = BookmapClient.builder()
    .transport(transport)
    .charset(Charset.forName("IBM037"))   // default
    .maxCalls(1000)                        // default; safety limit
    .build();

GetCustomerOrders request = new GetCustomerOrders();
request.setCustomerId("CUST000042");
request.setRestartKey("");
GetCustomerOrders result = client.execute(request);   // the response object
```

`BookmapClient` is immutable and thread-safe. The request object is not thread-safe. It belongs to the caller.

## Copybook generator

The copybook generator reads a COBOL copybook and writes the annotated Java classes. Use one of these tools:

- **[Command line](https://github.com/ajlima/bookmap/wiki/Copybook-generator-CLI)**: `java -jar bookmap-copybook-cli-<version>-all.jar generate ORDERS.cpy --package com.example.orders --output-dir src/main/java`
- **[Gradle plugin](https://github.com/ajlima/bookmap/wiki/Copybook-generator-Gradle-plugin)**: `id("pt.dazz.bookmap.copybook") version "0.7.0"`. It generates the classes in each build.
- **[Maven plugin](https://github.com/ajlima/bookmap/wiki/Copybook-generator-Maven-plugin)**: the goal `generate` of `pt.dazz.bookmap:bookmap-copybook-maven-plugin`. It generates the classes in each build.

See [Copybook generator](https://github.com/ajlima/bookmap/wiki/Copybook-generator) for the options file and the mapping rules.

## Documentation

| Topic | Wiki page |
|---|---|
| Installation, the first book, and the client | [Getting started](https://github.com/ajlima/bookmap/wiki/Getting-started) |
| `@BookOperation`, `@BookField`, `@BookCollection`, `@BookDefaults`, Lombok | [Annotations reference](https://github.com/ajlima/bookmap/wiki/Annotations-reference) |
| The six conventions, and the explicit style | [Conventions](https://github.com/ajlima/bookmap/wiki/Conventions) |
| `pic` strings, byte sizes, Java types, and conversion rules | [PIC syntax and type mapping](https://github.com/ajlima/bookmap/wiki/PIC-syntax-and-type-mapping) |
| `OCCURS DEPENDING ON`, pagination, and fixed `OCCURS` | [Collections and fixed OCCURS](https://github.com/ajlima/bookmap/wiki/Collections-and-fixed-OCCURS) |
| `REDEFINES` with a discriminator | [Subtypes (REDEFINES)](https://github.com/ajlima/bookmap/wiki/Subtypes-REDEFINES) |
| The exception hierarchy | [Error handling](https://github.com/ajlima/bookmap/wiki/Error-handling) |
| The transport SPI and the simulator | [Transports and simulator](https://github.com/ajlima/bookmap/wiki/Transports-and-simulator) |
| The Spring Boot auto-configuration | [Spring Boot](https://github.com/ajlima/bookmap/wiki/Spring-Boot) |
| The copybook generator, the CLI, and the build plugins | [Copybook generator](https://github.com/ajlima/bookmap/wiki/Copybook-generator) |
| Frequent problems | [FAQ and troubleshooting](https://github.com/ajlima/bookmap/wiki/FAQ-and-troubleshooting) |
| The build, the commits, and the releases | [Contributing](https://github.com/ajlima/bookmap/wiki/Contributing) |

The source of the wiki is the folder [`docs/wiki/`](docs/wiki). Change the wiki in a pull request, not in the web editor.

## Modules

| Module | Purpose |
|---|---|
| `bookmap-core` | Annotations, `BookmapClient`, the transport SPI, exceptions, and the codec runtime. |
| `bookmap-processor` | The annotation processor. It generates one codec for each `@BookOperation` class. |
| `bookmap-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `bookmap-transport-mq` | A transport for IBM MQ request and reply queues (Jakarta Messaging). See [IBM MQ](https://github.com/ajlima/bookmap/wiki/IBM-MQ). |
| `bookmap-spring-boot` | Spring Boot 4 auto-configuration for `BookmapClient` (built and tested with 4.1.2). |
| `bookmap-copybook` | The copybook generator library: parser, model, JSON form, and Java generator. |
| `bookmap-copybook-cli` | The command line of the copybook generator. |
| `bookmap-copybook-gradle-plugin` | The Gradle plugin of the copybook generator (`pt.dazz.bookmap.copybook`). |
| `bookmap-copybook-maven-plugin` | The Maven plugin of the copybook generator (goal `generate`). |
| `bookmap-examples` | Working examples. The wiki uses them. |
| `bookmap-examples-gradle-plugin` | A standalone build that uses the Gradle plugin. |
| `bookmap-examples-maven-plugin` | A standalone multi-module Maven build that uses the Maven plugin. |
| `bookmap-native-test` | A standalone build that tests BookMap in a GraalVM native image. |

## Project status

BookMap is at version `0.7.0`. GitHub Actions builds each release and publishes it to Maven Central.
The build enforces 100% line and branch coverage for all modules except the examples.
The IBM MQ transport is available. The CICS Transaction Gateway and HTTP transports are planned.
See [RELEASING.md](RELEASING.md) for the release cycle, and [ROADMAP.md](ROADMAP.md) for the planned features.

## License

BookMap is licensed under the [MIT License](LICENSE). Copyright (c) 2026 Alexandre Lima.
Keep the copyright notice and the license text in all copies or substantial portions of the software.
