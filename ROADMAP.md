# CopyMapper roadmap

This roadmap lists the CopyMapper features that are not developed yet.
The groups are in the order of priority. In each group, the items are also in the order of priority.

Each item has a GitHub issue. Each group has a milestone with the same name.
The roadmap gives no dates and no version numbers. The release version comes from the commit messages.

To propose a new item, open an issue.

## 1. Dynamic runtime

A runtime codec that reads a copybook and converts JSON to bytes and bytes to JSON, with no generated class. The group also adds the CTG transport changes that a runtime caller needs. Milestone: [Dynamic runtime](https://github.com/dazz-pt/copymapper/milestone/7).

- [#135](https://github.com/dazz-pt/copymapper/issues/135) Write the design of the dynamic runtime
- [#136](https://github.com/dazz-pt/copymapper/issues/136) Compile an immutable physical layout from the copybook AST
- [#137](https://github.com/dazz-pt/copymapper/issues/137) Encode and decode JSON with an interpreted codec on the layout
- [#138](https://github.com/dazz-pt/copymapper/issues/138) Support a fixed OCCURS in the layout and the codec
- [#139](https://github.com/dazz-pt/copymapper/issues/139) Add a test kit of byte-exact fixtures
- [#140](https://github.com/dazz-pt/copymapper/issues/140) Support binary integers, signs, and dates in the layout and the codec
- [#141](https://github.com/dazz-pt/copymapper/issues/141) Support OCCURS DEPENDING ON in the layout and the codec
- [#142](https://github.com/dazz-pt/copymapper/issues/142) Support REDEFINES with a discriminator in the layout and the codec
- [#143](https://github.com/dazz-pt/copymapper/issues/143) Set a timeout for each call in the CTG transport
- [#144](https://github.com/dazz-pt/copymapper/issues/144) Separate a CTG failure before the send from a failure after the send
- [#147](https://github.com/dazz-pt/copymapper/issues/147) Move copymapper-copybook to Jackson 3
- [#148](https://github.com/dazz-pt/copymapper/issues/148) Generate Java classes from a mapping spec

## 2. Transports

Real transports for the transport SPI. The SPI accepts them without a change to the core. Milestone: [Transports](https://github.com/dazz-pt/copymapper/milestone/2).

No open issue.

## 3. Copybook generator

New features of the copybook generator and its build plugins. Milestone: [Copybook generator](https://github.com/dazz-pt/copymapper/milestone/3).

- [#30](https://github.com/dazz-pt/copymapper/issues/30) Keep the history of copybook models (store, compare, merge)
- [#31](https://github.com/dazz-pt/copymapper/issues/31) Add global parse and mapping options to the build plugins
- [#32](https://github.com/dazz-pt/copymapper/issues/32) Generate classes from a model JSON file in the Gradle plugin
- [#33](https://github.com/dazz-pt/copymapper/issues/33) Read copybooks from a dependency artifact
- [#34](https://github.com/dazz-pt/copymapper/issues/34) Support Maven 4 in the Maven plugin
- [#149](https://github.com/dazz-pt/copymapper/issues/149) Deprecate the options file and the model JSON of the copybook generator

## 4. Mapping

New layout features in the core and the processor. Milestone: [Mapping](https://github.com/dazz-pt/copymapper/milestone/4).

- [#39](https://github.com/dazz-pt/copymapper/issues/39) Support subtypes in a subtype
- [#40](https://github.com/dazz-pt/copymapper/issues/40) Support base fields after the redefined area
- [#41](https://github.com/dazz-pt/copymapper/issues/41) Support a base count field for a collection in a subclass
- [#42](https://github.com/dazz-pt/copymapper/issues/42) Support more discriminator types
- [#46](https://github.com/dazz-pt/copymapper/issues/46) Support COMP-1 and COMP-2
- [#49](https://github.com/dazz-pt/copymapper/issues/49) Support multi-byte charsets (DBCS and NATIONAL)

## 5. Integrations

Other DI containers and API styles. Milestone: [Integrations](https://github.com/dazz-pt/copymapper/milestone/5).

- [#51](https://github.com/dazz-pt/copymapper/issues/51) Add a Quarkus extension
- [#52](https://github.com/dazz-pt/copymapper/issues/52) Add a Micronaut integration
- [#53](https://github.com/dazz-pt/copymapper/issues/53) Add an asynchronous or reactive API

## 6. Maintenance

Deferred review findings and internal changes. Milestone: [Maintenance](https://github.com/dazz-pt/copymapper/milestone/6).

- [#92](https://github.com/dazz-pt/copymapper/issues/92) Check the record length for an int overflow
- [#93](https://github.com/dazz-pt/copymapper/issues/93) Name the collection and the index in an item field error
- [#94](https://github.com/dazz-pt/copymapper/issues/94) Reject non-ASCII letters in a PIC
- [#95](https://github.com/dazz-pt/copymapper/issues/95) Report each processor error only one time
- [#96](https://github.com/dazz-pt/copymapper/issues/96) Defer a class whose interface is not generated yet
- [#97](https://github.com/dazz-pt/copymapper/issues/97) Report the accessor errors also when the class has other errors
- [#98](https://github.com/dazz-pt/copymapper/issues/98) Do not add a copymapper error next to a javac error for an unresolved value
- [#99](https://github.com/dazz-pt/copymapper/issues/99) Compare the type arguments of a getter and a setter
- [#100](https://github.com/dazz-pt/copymapper/issues/100) Compare the charsets by charset, and decide the empty charset
- [#101](https://github.com/dazz-pt/copymapper/issues/101) Keep the caller stack trace in a simulator exception
- [#102](https://github.com/dazz-pt/copymapper/issues/102) Show a binary COMP field and a FieldConverter in copymapper-examples
- [#103](https://github.com/dazz-pt/copymapper/issues/103) Internal clean-up from the v1 reviews
- [#110](https://github.com/dazz-pt/copymapper/issues/110) Remove the package cycle between annotation and convert in copymapper-core
- [#111](https://github.com/dazz-pt/copymapper/issues/111) Remove the package cycle between ast and parse in copymapper-copybook
- [#117](https://github.com/dazz-pt/copymapper/issues/117) Divide CodecGenerator into smaller classes

## Out of scope

These items are design decisions. They are not planned:

- Separate request and response classes. One class holds both directions.
- A change of subclass between two pages of one execution.
- Publication of the Gradle plugin to the Gradle Plugin Portal. CopyMapper publishes to Maven Central only.
- Automatic discovery of `COPY` members anywhere in the tree. Declare the copy directories.
- A Maven goal prefix that works with no setup, and an m2e (Eclipse) incremental build integration.
