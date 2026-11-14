# BookMap roadmap

This roadmap lists the BookMap features that are not developed yet.
The groups are in the order of priority. In each group, the items are also in the order of priority.

Each item has a GitHub issue. Each group has a milestone with the same name.
The roadmap gives no dates and no version numbers. The release version comes from the commit messages.

To propose a new item, open an issue.

## 1. Transports

Real transports for the transport SPI. The SPI accepts them without a change to the core. Milestone: [Transports](https://github.com/ajlima/bookmap/milestone/2).

No open issue.

## 2. Copybook generator

New features of the copybook generator and its build plugins. Milestone: [Copybook generator](https://github.com/ajlima/bookmap/milestone/3).

- [#30](https://github.com/ajlima/bookmap/issues/30) Keep the history of copybook models (store, compare, merge)
- [#31](https://github.com/ajlima/bookmap/issues/31) Add global parse and mapping options to the build plugins
- [#32](https://github.com/ajlima/bookmap/issues/32) Generate classes from a model JSON file in the Gradle plugin
- [#33](https://github.com/ajlima/bookmap/issues/33) Read copybooks from a dependency artifact
- [#34](https://github.com/ajlima/bookmap/issues/34) Support Maven 4 in the Maven plugin

## 3. Mapping

New layout features in the core and the processor. Milestone: [Mapping](https://github.com/ajlima/bookmap/milestone/4).

- [#38](https://github.com/ajlima/bookmap/issues/38) Support REDEFINES without a discriminator
- [#39](https://github.com/ajlima/bookmap/issues/39) Support subtypes in a subtype
- [#40](https://github.com/ajlima/bookmap/issues/40) Support base fields after the redefined area
- [#41](https://github.com/ajlima/bookmap/issues/41) Support a base count field for a collection in a subclass
- [#42](https://github.com/ajlima/bookmap/issues/42) Support more discriminator types
- [#45](https://github.com/ajlima/bookmap/issues/45) Support a response that ends inside a fixed collection
- [#46](https://github.com/ajlima/bookmap/issues/46) Support COMP-1 and COMP-2
- [#49](https://github.com/ajlima/bookmap/issues/49) Support multi-byte charsets (DBCS and NATIONAL)

## 4. Integrations

Other DI containers and API styles. Milestone: [Integrations](https://github.com/ajlima/bookmap/milestone/5).

- [#51](https://github.com/ajlima/bookmap/issues/51) Add a Quarkus extension
- [#52](https://github.com/ajlima/bookmap/issues/52) Add a Micronaut integration
- [#53](https://github.com/ajlima/bookmap/issues/53) Add an asynchronous or reactive API

## 5. Maintenance

Deferred review findings and internal changes. Milestone: [Maintenance](https://github.com/ajlima/bookmap/milestone/6).

- [#92](https://github.com/ajlima/bookmap/issues/92) Check the record length for an int overflow
- [#93](https://github.com/ajlima/bookmap/issues/93) Name the collection and the index in an item field error
- [#94](https://github.com/ajlima/bookmap/issues/94) Reject non-ASCII letters in a PIC
- [#95](https://github.com/ajlima/bookmap/issues/95) Report each processor error only one time
- [#96](https://github.com/ajlima/bookmap/issues/96) Defer a class whose interface is not generated yet
- [#97](https://github.com/ajlima/bookmap/issues/97) Report the accessor errors also when the class has other errors
- [#98](https://github.com/ajlima/bookmap/issues/98) Do not add a bookmap error next to a javac error for an unresolved value
- [#99](https://github.com/ajlima/bookmap/issues/99) Compare the type arguments of a getter and a setter
- [#100](https://github.com/ajlima/bookmap/issues/100) Compare the charsets by charset, and decide the empty charset
- [#101](https://github.com/ajlima/bookmap/issues/101) Keep the caller stack trace in a simulator exception
- [#102](https://github.com/ajlima/bookmap/issues/102) Show a binary COMP field and a FieldConverter in bookmap-examples
- [#103](https://github.com/ajlima/bookmap/issues/103) Internal clean-up from the v1 reviews
- [#110](https://github.com/ajlima/bookmap/issues/110) Remove the package cycle between annotation and convert in bookmap-core
- [#111](https://github.com/ajlima/bookmap/issues/111) Remove the package cycle between ast and parse in bookmap-copybook
- [#117](https://github.com/ajlima/bookmap/issues/117) Divide CodecGenerator into smaller classes

## Out of scope

These items are design decisions. They are not planned:

- Separate request and response classes. One class holds both directions.
- A change of subclass between two pages of one execution.
- Publication of the Gradle plugin to the Gradle Plugin Portal. BookMap publishes to Maven Central only.
- Automatic discovery of `COPY` members anywhere in the tree. Declare the copy directories.
- A Maven goal prefix that works with no setup, and an m2e (Eclipse) incremental build integration.
