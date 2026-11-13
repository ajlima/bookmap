<p align="center">
  <img src="https://raw.githubusercontent.com/ajlima/bookmap/main/docs/assets/bookmap-logo-light.svg" alt="BookMap" width="380">
</p>

<p align="center">
  <strong>Map COBOL books to Java objects. No reflection. No middleware lock-in.</strong>
</p>

<p align="center">
  <a href="https://central.sonatype.com/namespace/pt.dazz.bookmap"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-core"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-0f62fe">
  <a href="https://github.com/ajlima/bookmap/blob/main/LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

# BookMap

BookMap maps COBOL books (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection on your classes.

BookMap needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.
The base package is `pt.dazz.bookmap`. The license is MIT.

## Why BookMap

- **No reflection.** The processor generates the codec at compile time. The runtime does not use reflection on your classes.
- **Errors at compile time.** An incorrect layout, `pic`, or type combination stops the build, not production.
- **Few annotations.** Conventions and `pic` strings keep each field to one line, close to the copybook.
- **Automatic pagination.** The client calls the program again until `hasContinuation()` returns `false`, and returns the full list.
- **No middleware lock-in.** A transport is one interface. The simulator lets you test without a mainframe.
- **No silent data loss.** A value that does not fit causes an exception. The codec never truncates or rounds.
- **Classes from copybooks.** The copybook generator writes the annotated classes from a COBOL copybook.

## Pages

| Page | Content |
|---|---|
| [Getting started](Getting-started) | Installation, the first book, and the first call. |
| [Annotations reference](Annotations-reference) | `@BookOperation`, `@BookField`, `@BookCollection`, `@BookDefaults`, `@BookSubTypes`, and Lombok. |
| [Conventions](Conventions) | The six conventions, and the convention style and the explicit style. |
| [PIC syntax and type mapping](PIC-syntax-and-type-mapping) | The `pic` string, the byte sizes, the Java types, and the conversion rules. |
| [Collections and fixed OCCURS](Collections-and-fixed-OCCURS) | Variable collections, pagination, and fixed tables. |
| [Subtypes (REDEFINES)](Subtypes-REDEFINES) | One area with more than one layout. |
| [Error handling](Error-handling) | The exception hierarchy and the business errors. |
| [Transports and simulator](Transports-and-simulator) | The comparison of the transports, the transport SPI, and the simulator for tests. |
| &nbsp;&nbsp;↳ [CICS Transaction Gateway](CICS-Transaction-Gateway) | The transport for CICS programs through CTG (ECI). |
| &nbsp;&nbsp;↳ [HTTP transport](HTTP-transport) | The transport for an HTTP endpoint with a binary body. |
| &nbsp;&nbsp;↳ [IBM MQ](IBM-MQ) | The IBM MQ transport: request and reply queues. |
| [Spring Boot](Spring-Boot) | The auto-configuration of `BookmapClient`. |
| [Copybook generator](Copybook-generator) | The concepts, the options file, and the mapping rules of the generator. |
| [Copybook generator CLI](Copybook-generator-CLI) | The command line, with examples, and the Java API. |
| [Copybook generator Gradle plugin](Copybook-generator-Gradle-plugin) | The Gradle plugin: setup, DSL, packages, and troubleshooting. |
| [Copybook generator Maven plugin](Copybook-generator-Maven-plugin) | The Maven plugin: setup, parameters, multi-module projects, and troubleshooting. |
| [FAQ and troubleshooting](FAQ-and-troubleshooting) | Frequent problems and their solutions. |
| [Contributing](Contributing) | The build, the branches, the commits, and the releases. |
| [Roadmap](Roadmap) | The planned features, with their issues and milestones. |

## Modules

| Module | Purpose |
|---|---|
| `bookmap-core` | Annotations, `BookmapClient`, the transport SPI, exceptions, and the codec runtime. |
| `bookmap-processor` | The annotation processor. It generates one codec for each `@BookOperation` class. |
| `bookmap-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `bookmap-transport-ctg` | A transport for CICS programs through the CICS Transaction Gateway (ECI). |
| `bookmap-transport-http` | A transport for an HTTP endpoint that accepts the bytes of the book. |
| `bookmap-transport-mq` | A transport for IBM MQ request and reply queues (Jakarta Messaging). |
| `bookmap-spring-boot` | Spring Boot 4 auto-configuration for `BookmapClient` (built and tested with 4.1.2). |
| `bookmap-copybook` | The copybook generator library: parser, model, JSON form, and Java generator. |
| `bookmap-copybook-cli` | The command line of the copybook generator. |
| `bookmap-copybook-gradle-plugin` | The Gradle plugin of the copybook generator (`pt.dazz.bookmap.copybook`). |
| `bookmap-copybook-maven-plugin` | The Maven plugin of the copybook generator (goal `generate`). |
| `bookmap-examples` | Working examples. This wiki uses them. |
| `bookmap-examples-gradle-plugin` | A standalone build that uses the Gradle plugin. |
| `bookmap-examples-maven-plugin` | A standalone multi-module Maven build that uses the Maven plugin. |

## Known limitations

- No `REDEFINES` without a discriminator. See [Subtypes (REDEFINES)](Subtypes-REDEFINES).
- No subtypes in a subtype, and no base fields after the redefined area.
- No nested collections.
- No `COMP-1` or `COMP-2`.
- Single-byte charsets only.
- Edited pictures (`Z`, `*`, `,`, `+`, `-`, `CR`, `DB`) are rejected at compile time.
- No `P` scaling, `A`, or `N` in a picture, and no `COMP-5`.
- An operation class must not be a record. `@lombok.Value` is not supported, because it gives no setters.
- The CTG transport sends a COMMAREA. Channels and containers are not supported.

The [Roadmap](Roadmap) lists the plan for each limitation.

## Project status

GitHub Actions builds each release and publishes it to Maven Central.
The build enforces 100% line and branch coverage for all modules except the examples.
BookMap has transports for the [CICS Transaction Gateway](CICS-Transaction-Gateway), for [HTTP](HTTP-transport), and for [IBM MQ](IBM-MQ). The transport SPI accepts more transports without a change to the core.

BookMap is licensed under the [MIT License](https://github.com/ajlima/bookmap/blob/main/LICENSE). Copyright (c) 2026 Alexandre Lima.
