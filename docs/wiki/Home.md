<p align="center">
  <img src="https://raw.githubusercontent.com/dazz-pt/copymapper/main/docs/assets/copymapper-logo-light.svg" alt="CopyMapper" width="470">
</p>

<p align="center">
  <strong>Map COBOL books to Java objects. No reflection. No middleware lock-in.</strong>
</p>

<p align="center">
  <a href="https://central.sonatype.com/namespace/pt.dazz.copymapper"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/pt.dazz.copymapper/copymapper-core"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-0f62fe">
  <a href="https://github.com/dazz-pt/copymapper/blob/main/LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

# CopyMapper

CopyMapper maps COBOL copybooks (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection on your classes.

CopyMapper needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.
The base package is `pt.dazz.copymapper`. The license is MIT.

Before `0.28.0`, the library had the name BookMap. To update a project, see [Migrate to CopyMapper](Migrate-to-CopyMapper).

## Why CopyMapper

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
| [Migrate to CopyMapper](Migrate-to-CopyMapper) | The changes from BookMap `0.27.0` to CopyMapper `0.28.0`. |
| [Annotations reference](Annotations-reference) | `@Operation`, `@Field`, `@Occurs`, `@Group`, `@Defaults`, `@SubTypes`, and Lombok. |
| [Conventions](Conventions) | The six conventions, and the convention style and the explicit style. |
| [PIC syntax and type mapping](PIC-syntax-and-type-mapping) | The `pic` string, the byte sizes, the Java types, and the conversion rules. |
| [Collections and fixed OCCURS](Collections-and-fixed-OCCURS) | Variable collections, pagination, and fixed tables. |
| [Subtypes (REDEFINES)](Subtypes-REDEFINES) | One area with more than one layout, and a field that selects the layout. |
| [REDEFINES views](REDEFINES-views) | One area with more than one layout, and no field that selects the layout. |
| [Error handling](Error-handling) | The exception hierarchy and the business errors. |
| [Transports and simulator](Transports-and-simulator) | The comparison of the transports, the transport SPI, and the simulator for tests. |
| &nbsp;&nbsp;↳ [CICS Transaction Gateway](CICS-Transaction-Gateway) | The transport for CICS programs through CTG (ECI). |
| &nbsp;&nbsp;↳ [HTTP transport](HTTP-transport) | The transport for an HTTP endpoint with a binary body. |
| &nbsp;&nbsp;↳ [IBM MQ](IBM-MQ) | The IBM MQ transport: request and reply queues. |
| [Spring Boot](Spring-Boot) | The auto-configuration of `CopyMapperClient`. |
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
| `copymapper-core` | Annotations, `CopyMapperClient`, the transport SPI, exceptions, and the codec runtime. |
| `copymapper-processor` | The annotation processor. It generates one codec for each `@Operation` class. |
| `copymapper-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `copymapper-transport-ctg` | A transport for CICS programs through the CICS Transaction Gateway (ECI). |
| `copymapper-transport-http` | A transport for an HTTP endpoint that accepts the bytes of the book. |
| `copymapper-transport-mq` | A transport for IBM MQ request and reply queues (Jakarta Messaging). |
| `copymapper-spring-boot` | Spring Boot 4 auto-configuration for `CopyMapperClient` (built and tested with 4.1.2). |
| `copymapper-copybook` | The copybook generator library: parser, model, JSON form, and Java generator. |
| `copymapper-copybook-cli` | The command line of the copybook generator. |
| `copymapper-copybook-gradle-plugin` | The Gradle plugin of the copybook generator (`pt.dazz.copymapper.copybook`). |
| `copymapper-copybook-maven-plugin` | The Maven plugin of the copybook generator (goal `generate`). |
| `copymapper-examples` | Working examples. This wiki uses them. |
| `copymapper-examples-gradle-plugin` | A standalone build that uses the Gradle plugin. |
| `copymapper-examples-maven-plugin` | A standalone multi-module Maven build that uses the Maven plugin. |

## Known limitations

- No subtypes in a subtype, and no base fields after the redefined area.
- No `OCCURS DEPENDING ON` in a table entry. A nested collection must be fixed.
- No `COMP-1` or `COMP-2`.
- Single-byte charsets only.
- No alphanumeric edited pictures, for example `XXBXX`. No currency sign other than `$`, and no `DECIMAL-POINT IS COMMA`.
- No `P` scaling, `A`, or `N` in a picture, and no `COMP-5`.
- An operation class must not be a record. `@lombok.Value` is not supported, because it gives no setters.
- The CTG transport sends a COMMAREA. Channels and containers are not supported.
- A record, an item, a collection, or a field must not be longer than 2147483647 bytes. A longer layout is a compile error.
- A `pic` string must have only ASCII characters.

The [Roadmap](Roadmap) lists the plan for each limitation.

## Project status

GitHub Actions builds each release and publishes it to Maven Central.
The build enforces 100% line and branch coverage for all modules except the examples.
CopyMapper has transports for the [CICS Transaction Gateway](CICS-Transaction-Gateway), for [HTTP](HTTP-transport), and for [IBM MQ](IBM-MQ). The transport SPI accepts more transports without a change to the core.

CopyMapper is licensed under the [MIT License](https://github.com/dazz-pt/copymapper/blob/main/LICENSE). Copyright (c) 2026 Alexandre Lima.
