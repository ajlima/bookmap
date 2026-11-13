# BookMap roadmap

This roadmap lists the BookMap features that are not developed yet.
The groups are in the order of priority. In each group, the items are also in the order of priority.

Each item has a GitHub issue. Each group has a milestone with the same name.
The roadmap gives no dates and no version numbers. The release version comes from the commit messages.

To propose a new item, open an issue.

## 1. Transports

Real transports for the transport SPI. The SPI accepts them without a change to the core. Milestone: [Transports](https://github.com/ajlima/bookmap/milestone/2).

- [#29](https://github.com/ajlima/bookmap/issues/29) Add Spring Boot auto-configuration for each transport

## 2. Copybook generator

New features of the copybook generator and its build plugins. Milestone: [Copybook generator](https://github.com/ajlima/bookmap/milestone/3).

- [#30](https://github.com/ajlima/bookmap/issues/30) Keep the history of copybook models (store, compare, merge)
- [#31](https://github.com/ajlima/bookmap/issues/31) Add global parse and mapping options to the build plugins
- [#32](https://github.com/ajlima/bookmap/issues/32) Generate classes from a model JSON file in the Gradle plugin
- [#33](https://github.com/ajlima/bookmap/issues/33) Read copybooks from a dependency artifact
- [#34](https://github.com/ajlima/bookmap/issues/34) Support Maven 4 in the Maven plugin
- [#35](https://github.com/ajlima/bookmap/issues/35) Generator: date hints and optional Continuable/Validatable
- [#36](https://github.com/ajlima/bookmap/issues/36) Generator: level 88 constants and VALUE initial values
- [#37](https://github.com/ajlima/bookmap/issues/37) Generator: accept the COBOL items that are errors today

## 3. Mapping

New layout features in the core and the processor. Milestone: [Mapping](https://github.com/ajlima/bookmap/milestone/4).

- [#38](https://github.com/ajlima/bookmap/issues/38) Support REDEFINES without a discriminator
- [#39](https://github.com/ajlima/bookmap/issues/39) Support subtypes in a subtype
- [#40](https://github.com/ajlima/bookmap/issues/40) Support base fields after the redefined area
- [#41](https://github.com/ajlima/bookmap/issues/41) Support a base count field for a collection in a subclass
- [#42](https://github.com/ajlima/bookmap/issues/42) Support more discriminator types
- [#43](https://github.com/ajlima/bookmap/issues/43) Support nested collections
- [#44](https://github.com/ajlima/bookmap/issues/44) Support other empty-item markers in fixed collections
- [#45](https://github.com/ajlima/bookmap/issues/45) Support a response that ends inside a fixed collection
- [#46](https://github.com/ajlima/bookmap/issues/46) Support COMP-1 and COMP-2
- [#47](https://github.com/ajlima/bookmap/issues/47) Support SIGN LEADING and SIGN SEPARATE
- [#48](https://github.com/ajlima/bookmap/issues/48) Support edited pictures
- [#49](https://github.com/ajlima/bookmap/issues/49) Support multi-byte charsets (DBCS and NATIONAL)
- [#50](https://github.com/ajlima/bookmap/issues/50) Support packed dates without a converter

## 4. Integrations

Other DI containers and API styles. Milestone: [Integrations](https://github.com/ajlima/bookmap/milestone/5).

- [#51](https://github.com/ajlima/bookmap/issues/51) Add a Quarkus extension
- [#52](https://github.com/ajlima/bookmap/issues/52) Add a Guice module
- [#53](https://github.com/ajlima/bookmap/issues/53) Add an asynchronous or reactive API

## 5. Maintenance

Deferred review findings. Milestone: [Maintenance](https://github.com/ajlima/bookmap/milestone/6).

- [#54](https://github.com/ajlima/bookmap/issues/54) Triage the deferred minors in the follow-ups file

## Out of scope

These items are design decisions. They are not planned:

- Separate request and response classes. One class holds both directions.
- A change of subclass between two pages of one execution.
- Publication of the Gradle plugin to the Gradle Plugin Portal. BookMap publishes to Maven Central only.
- Automatic discovery of `COPY` members anywhere in the tree. Declare the copy directories.
- A Maven goal prefix that works with no setup, and an m2e (Eclipse) incremental build integration.
