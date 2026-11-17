# Dynamic runtime (design)

Date: 2026-11-17
Status: proposed.
Base package: `pt.dazz.copymapper`
Parent specs: `2026-11-10-copymapper-design.md`, `2026-11-11-copymapper-copybook-design.md`, `2026-11-11-copymapper-fixed-occurs-design.md`, `2026-11-11-copymapper-subtypes-design.md`, `2026-11-13-empty-item-markers-design.md`, `2026-11-13-ctg-transport-design.md`
Milestone: [Dynamic runtime](https://github.com/dazz-pt/copymapper/milestone/7). Issues: #135 to #144, #147, and #148. Related: #149.

Decisions marked **[builder call]** come from the spec author, not from the project owner. You can change them if you have a good reason. All other decisions come from the project owner.

## 1. Intent

CopyMapper converts a book to bytes with a codec that the annotation processor generates at compile time. Each book needs a Java class and a build.

Some applications must add a service at runtime, without a build. The first one is CopyBridge: a self-hosted product with a Studio and a Gateway. An operator uploads a copybook in the Studio. The Gateway then converts REST JSON to a COMMAREA and back, and calls CICS through CTG.

The flow in CopyBridge is:

```text
Studio: upload of the .cpy file
  -> copymapper-copybook: parser -> AST (DataItem)               (exists)
  -> copymapper-copybook: draft of the mapping spec from the AST    (new)
  -> Studio: the operator completes the mapping spec in a mapping screen
       (COBOL on the left, REST on the right)
  -> copymapper-runtime: compile the mapping spec                   (new)
       - complete:   a layout, then an interpreted codec (JSON <-> bytes)
       - incomplete: the list of all open decisions, back to the screen
  -> Gateway: JSON -> codec -> Transport.exchange -> codec -> JSON
```

This design adds the mapping spec: one JSON document that holds all decisions for a record. The copybook generator also accepts it, so the generated classes and the interpreted codec come from the same decisions (#148). The design adds the module `copymapper-runtime` for the compiler, the layout, and the interpreted codec. It also adds two changes to the CTG transport that a runtime caller needs:

- A timeout for one call (#143).
- A failure that tells that the request was not sent (#144). With it, a caller can retry a call without a risk to execute a CICS program two times.

The mapping screen, the storage, and the approvals stay in CopyBridge. The library stays generic: it has no concept of a binding, a node, or an environment.

## 2. What the code does today

These are facts from `main` (v0.28.0):

- `copymapper-copybook` parses a copybook into an AST: `Copybook(sourceName, records)`, with one `DataItem` for each item. `DataItem` has `level`, `name`, `picture`, `usage`, `occurs`, `redefines`, `conditions`, `children`, `source`, `line`, `column`, `declaration`, `value`, and `sign`.
- `Picture` gives `numeric`, `signed`, `integerDigits`, `scale`, `edited`, and `byteLength(Usage, Sign)`. The AST `Usage` has `DISPLAY`, `COMP` (also `COMP-4` and `BINARY`), `COMP3`, and `COMP5`. `Occurs` has `min`, `max`, and `dependingOn`.
- The parser expands `COPY ... REPLACING`, adds the slack bytes of `SYNC`, and rejects `COMP-1`, `COMP-2`, `NATIONAL`, and `POINTER`.
- `copymapper-core` has no dependency. `copymapper-copybook` depends on `copymapper-core` and on Jackson 2.23 (`com.fasterxml.jackson`), in `ModelJson`, `GeneratorConfigJson`, and `JsonObject`.
- The public classes `BookWriter` and `BookReader` in `pt.dazz.copymapper.codec` give all codec primitives: text, zoned, packed, binary, signs, dates, edited numbers, LOW-VALUES, and empty slots. A `CodecContext` gives the charset. The primitive classes in `codec.internal` are not an API.
- An encode or a decode error is a `MappingException`, or its subclass `InvalidValueException`.
- In the annotation model, a field with the direction `INPUT` does not occupy bytes in the response, and an `OUTPUT` field does not occupy bytes in the request. The copybook generator thus keeps all items `INPUT_OUTPUT` outside a `REDEFINES` area.
- `CtgTransport` has one ECI timeout for each instance. It does not read `BookRequest.attributes()`. A failure before the send and a failure after the send can have the same exception type, for example `ConnectionException`.
- Nothing in `copymapper-transport-ctg` uses the codec or the copybook modules.

## 3. Goals, non-goals, and anti-goals

### 3.1 Goals

- A draft of a complete mapping spec from one `01` record of the AST, with default values and with the open decisions marked.
- A compiler that checks a mapping spec and gives an immutable layout, or the list of all open decisions.
- An interpreted codec that converts Jackson 3 JSON to bytes and bytes to JSON with the layout, in both directions.
- The types: `PIC X`, `PIC 9` `DISPLAY` and `COMP-3`, signs, `COMP`, `COMP-4`, `COMP-5`, dates, groups, `FILLER`, fixed `OCCURS`, `OCCURS DEPENDING ON`, and `REDEFINES` with a discriminator or a fixed selection.
- Generated Java classes from a mapping spec, in the copybook generator, the CLI, and the build plugins (#148).
- Byte-exact fixtures that compare the interpreted codec with the codec that the processor generates from the same mapping spec.
- A timeout for one CTG call, and a failure that tells that a request was not sent.
- Jackson 3 in all modules of the library (#147).
- 100% line and branch coverage, and a mutation score of 95% or more in `copymapper-runtime`.

### 3.2 Non-goals

- Views: a `REDEFINES` area with all alternatives in JSON at the same time (#38 supports views only in generated classes).
- Edited pictures, `BLANK WHEN ZERO`, `COMP-1`, `COMP-2`, `NATIONAL`, and DBCS (#46, #49).
- A partial response that ends in a fixed table (#45).
- A policy that fills the unused slots of an `OCCURS DEPENDING ON` table up to the maximum.
- Business validations: patterns, enums, required fields. CopyBridge puts them in its JSON Schema.
- The roles `INTERNAL` and the binding concepts of CopyBridge.
- `requestSent()` in the HTTP and MQ transports. They keep the default.
- A native image test of `copymapper-runtime`.
- The deprecation of the options file and of the model JSON of the copybook generator. Issue #149 does it later, in the milestone "Copybook generator".

### 3.3 Anti-goals

- Silent data loss: the codec truncates text, rounds a decimal, or drops an item with data, without an error.
- The compiler selects an interpretation of an ambiguous item with a heuristic.
- The compiler stops at the first open decision, so that the operator fixes one item at a time.
- Runtime reflection on user classes, or Java code loaded at runtime. The codec uses only the Jackson tree model.
- A change of a generated codec, or of the output of the copybook generator.
- A change of an existing exception type in a transport.

## 4. Decisions of the project owner

### 4.1 The JSON type: Jackson 3

| Option | Trade-off |
|---|---|
| A neutral JDK tree (`Map`, `List`, `BigDecimal`). | No dependency in the API. The caller converts its tree. |
| `JsonNode` of Jackson 2. | The same Jackson as `copymapper-copybook` today. Spring Boot 4 uses Jackson 3, so a caller has two Jackson versions. |
| **`JsonNode` of Jackson 3 (selected).** | The same Jackson as Spring Boot 4.1 and CopyBridge. |

The owner wants Jackson 3 in all projects. Thus, #147 moves `copymapper-copybook` to Jackson 3 before #136. Use the version line of Spring Boot 4.1 (3.1.x) **[builder call]**: a caller then never runs the library on an older Jackson than the build used.

### 4.2 The mapping spec: a complete tree

| Option | Trade-off |
|---|---|
| A sparse overlay: only the decisions that change the defaults. | Small. But the mapping screen must merge the AST and the overlay. |
| A tree of decisions only, linked to the AST by path. | The AST stays the only physical source. The compiler needs the copybook and the parser each time. |
| **A self-contained tree of the full copybook (selected).** | One document holds the COBOL side and the REST side of each item. The screen, the storage, and the Gateway use the same object. |

The draft always comes from the parser. The compiler calculates the offsets again from the COBOL side, and checks the COBOL rules again. It never trusts a stored offset. A complete mapping spec has no implicit value: each decision is explicit.

### 4.3 One JSON: the mapping spec drives the generator too

| Option | Trade-off |
|---|---|
| Two formats: the mapping spec for the runtime, and the options file and the model JSON for the generator. | Smaller. But the same decisions are in two formats. |
| **The mapping spec as the one source, in two steps (selected).** | This milestone: the generator accepts a mapping spec (#148). Later: the options file and the model JSON are deprecated (#149), then removed. No change breaks the CLI or the plugins now. |
| Replace the options file and the model JSON now. | An incompatible change of the CLI, the plugins, #30, and #32. |

The name is `MappingSpec` in all places: the class, the JSON form, and the documentation.

### 4.4 The scope of the mapping

The library applies the decisions that change the bytes or the JSON shape: the JSON name, the direction, the JSON type, the date format, the table mode, the counter, the discriminator, and the fixed selection. Business validations stay in the caller.

### 4.5 The compile result

The compiler gives a result with the layout, or with the list of all open decisions. Each open decision has a kind, the path of the item, the source line, and a message. The mapping screen shows this list.

### 4.6 `OCCURS DEPENDING ON`

The rules of the generated codec (main design, section 5.6), with a check of the minimum. See 5.9.

### 4.7 Fixed `OCCURS`

The rules of the generated codec: count mode, or an empty-slot marker (LOW-VALUES, SPACES, or a key). See 5.8.

### 4.8 `REDEFINES`

A discriminator, or a fixed selection for each direction. An area without one is an open decision. See 5.10.

### 4.9 The CTG timeout for one call

| Option | Trade-off |
|---|---|
| **A `BookRequest` attribute (selected).** | The SPI already has attributes for a transport. The caller keeps the transport as a `Transport`. |
| A method `exchange(BookRequest, Duration)` of `CtgTransport`. | Typed. But the caller must keep the concrete type. |

### 4.10 The failure before the send

| Option | Trade-off |
|---|---|
| **A property `requestSent()` of `TransportException` (selected).** | Additive. The exception types do not change. Other transports can set it later. |
| A new exception `RequestNotSentException`. | Visible in the type. But some failures before the send are `TransportException` today, and they would change type. |

## 5. Design

### 5.1 Module and packages

- The mapping spec, its JSON form, and the draft are in `copymapper-copybook`, package `pt.dazz.copymapper.copybook.spec` **[builder call]**. Reason: the generator must read a mapping spec (#148), and `copymapper-runtime` depends on `copymapper-copybook`. A mapping spec in `copymapper-runtime` would make a cycle.
- Module `copymapper-runtime`: the compiler, the layout, and the codec. Dependencies: `api(project(":copymapper-core"))`, `api(project(":copymapper-copybook"))` for the mapping spec, and `api` Jackson 3 `jackson-databind`. The parser and JavaPoet (an `api` dependency of `copymapper-copybook`) are then on the classpath of the Gateway, but it does not use them.
- Packages of `copymapper-runtime` **[builder call]**:
  - `pt.dazz.copymapper.runtime.layout`: the compiler, the compile result, and the layout.
  - `pt.dazz.copymapper.runtime.codec`: the interpreted codec.
- `ArchitectureTest` of `copymapper-runtime` permits only `java..`, `tools.jackson..`, and `pt.dazz.copymapper..`. No package cycle.
- The module is published to Maven Central.

### 5.2 Public API

The names are proposals **[builder call]**. The behavior is the decision.

```java
// Draft: AST -> mapping spec
MappingSpec spec = MappingSpecs.draft(copybook, "GET-ACCOUNT", DraftOptions.defaults());

// JSON form of the mapping spec
String json = MappingSpecJson.write(spec);       // canonical form
MappingSpec back = MappingSpecJson.read(json);
String hash = MappingSpecJson.sha256(spec);      // hex, from the canonical form

// Compile
Compilation result = LayoutCompiler.compile(spec);
result.openDecisions();                       // List<OpenDecision>, empty when complete
Layout layout = result.layout();              // throws MappingException with all open decisions

// Codec
DynamicCodec codec = DynamicCodec.of(layout);
byte[] request   = codec.encodeRequest(jsonRequest);
JsonNode response = codec.decodeResponse(bytes);
JsonNode request2 = codec.decodeRequest(bytes);    // for a simulator and the tests
byte[] response2  = codec.encodeResponse(jsonResponse);
int max = codec.maxRequestLength();
int maxResponse = codec.maxResponseLength();
```

- `Layout` and `DynamicCodec` are immutable and thread-safe.
- `MappingSpec` and its nodes are immutable records with list copies.
- `DraftOptions` holds the parse options that the draft needs, for example the charset **[builder call]**.

### 5.3 The mapping spec

The mapping spec is a tree with one node for each item of the record, `FILLER` included. Level 88 items are metadata of their node. Level 66 and 77 items are not in the tree.

**Root.**

| Component | Meaning |
|---|---|
| `formatVersion` | `1`. |
| `sourceName` | The name of the copybook. |
| `sourceHash` | The SHA-256 of the copybook text after `COPY` expansion. For traceability only. |
| `charset` | A single-byte charset name. Default `IBM037`. |
| `byteOrder` | `BIG_ENDIAN`. The only value in this version. It is in the spec so that the hash changes if a later version adds a value. |
| `root` | The node of the `01` record. |

**Node: the COBOL side** (copied from the AST by the draft):

| Component | Meaning |
|---|---|
| `cobolName`, `level` | From the AST. `FILLER` keeps the name `FILLER`. |
| (all) | The **resolved** values, not the declared clauses. The draft resolves a `USAGE` that a group gives, and the default `TRAILING` sign of a signed item. The node is then complete without the AST. |
| `blankWhenZero`, `justified`, `edited` | Flags from the AST. The picture text does not show `BLANK WHEN ZERO`, so the flag is necessary. The compiler reports `UNSUPPORTED_TYPE` for `blankWhenZero` and `edited`. `justified` is a warning only, as in the parser. |
| `path` | The qualified path, for example `GET-ACCOUNT.CHECKING-DATA.OVERDRAFT-LIMIT`. Unique in the spec. A `FILLER` gets an index, for example `FILLER#2` **[builder call]**. |
| `picture` | The picture text, or `null` for a group. |
| `usage` | `DISPLAY`, `COMP`, `COMP3`, or `COMP5`. |
| `sign` | `null`, `TRAILING`, `LEADING`, `TRAILING_SEPARATE`, or `LEADING_SEPARATE`. |
| `occurs` | `null`, or `min`, `max`, and the path of the `DEPENDING ON` item. |
| `redefines` | `null`, or the path of the redefined item. |
| `value` | The `VALUE` literal, or `null`. |
| `conditions` | The level 88 items: name and values. |
| `location` | The source name, the line, and the column. |
| `children` | The child nodes, in order. |

**Node: the REST side** (default values from the draft, changed by the operator):

| Component | Meaning | Draft value |
|---|---|---|
| `jsonName` | The JSON property name. Unique among the JSON siblings. | The COBOL name in camel case, with the rule of the copybook generator. `null` for a `FILLER`. |
| `direction` | `INPUT`, `OUTPUT`, `INPUT_OUTPUT`, `IGNORE`, or `DERIVED`. | `INPUT_OUTPUT`. `DERIVED` for a counter. `IGNORE` for a `FILLER`. |
| `jsonType` | `TEXT`, `NUMBER`, `DECIMAL_STRING`, `DATE`, `TIME`, `DATE_TIME`, or `null` for a group. | `TEXT` for `PIC X`, `NUMBER` for a numeric picture. |
| `format` | The date or time pattern, for example `yyyyMMdd`. Mandatory for `DATE`, `TIME`, and `DATE_TIME`. | `null`. |
| `table` | For a fixed `OCCURS`: the mode `COUNT` (with the counter path), `LOW_VALUES`, `SPACES`, or `KEY` (with the key path). | `LOW_VALUES`. |
| `counter` | For an `OCCURS DEPENDING ON`: the path of the counter. | The `DEPENDING ON` item. |
| `variant` | For a `REDEFINES` area: a discriminator (path and a map from value to alternative), or a fixed selection (one alternative for the request, one for the response). | `null`: an open decision. |

### 5.4 The draft

- The draft reads one `01` record of a parsed `Copybook`. It selects the record by name. An unknown name, or an ambiguous one, is an `IllegalArgumentException`.
- It copies the COBOL side of each item, and sets the REST defaults of 5.3.
- It does not guess. A `REDEFINES` area gets no variant, and a numeric item that looks like a date stays `NUMBER`.
- The draft always succeeds for a record that the parser accepts. The compiler reports what is missing.

**The JSON form** **[builder call]**:

- `MappingSpecJson.read` rejects an unknown property, and a `formatVersion` other than `1`. The message gives the JSON path, as `ModelJson` does.
- The canonical form has the properties in the order of the tables in 5.3, no whitespace, no `null` property, and integers without a fraction. A test freezes the canonical form and the hash of one mapping spec. A change of the form needs a new `formatVersion`.

### 5.5 The compiler and the open decisions

`LayoutCompiler.compile(MappingSpec)` checks the full tree, and collects all open decisions. It does not stop at the first one.

| Kind | Example |
|---|---|
| `UNSUPPORTED_TYPE` | An edited picture, `BLANK WHEN ZERO`, or an unknown usage. |
| `INVALID_COBOL` | A picture that does not agree with the usage, a `SIGN` on a `COMP` item, an `OCCURS` max of less than 1, or a `REDEFINES` alternative that is longer than the redefined item when COBOL forbids it **[builder call]**. |
| `UNRESOLVED_REDEFINES` | A `REDEFINES` area without a discriminator and without a fixed selection. |
| `INVALID_VARIANT` | The discriminator is not an elementary item before the area, or a value maps to an item that is not an alternative of the area. |
| `INVALID_TABLE` | A counter that is not an integer item before the table, a counter with too few digits for the maximum, or a key path outside the item. |
| `INVALID_JSON_NAME` | A missing name, or two JSON siblings with the same name. |
| `INVALID_DIRECTION` | `DERIVED` on an item that is not a counter or a discriminator. A direction on a `FILLER` that is not `IGNORE`. |
| `INVALID_TYPE` | A `jsonType` that does not agree with the picture, or a date type without a valid `format`. |

- Each open decision has the kind, the path, the source location, and a message in English. The messages follow the style of the copybook generator: `GET-ACCOUNT.cpy:12:8: SAVINGS-DATA redefines CHECKING-DATA and has no discriminator and no fixed selection`.
- `Compilation.layout()` throws `MappingException` if the list is not empty. The message has all open decisions.

### 5.6 The layout

- A layout node has the offset and the length of the item in bytes, for the request and for the response.
- The direction never changes an offset. The request and the response always have the full physical layout of the record. This is different from the annotation model (section 2), and it agrees with COBOL.
- A group has the sum of its children. A `REDEFINES` area has the length of its longest alternative.
- Items after an `OCCURS DEPENDING ON` table have no fixed offset. The codec calculates them with a cursor, as the generated codec does.
- `maxRequestLength` and `maxResponseLength` use the maximum of each table and the longest alternative.
- The layout also gives, for each node, the data that the mapping screen shows: the offset, the minimum and maximum length, and the effective charset.

### 5.7 The codec

**General rules:**

- A group is a JSON object. A table is a JSON array. A `FILLER` and an `IGNORE` item are not in JSON.
- The request uses the items with `INPUT` and `INPUT_OUTPUT`. The response uses the items with `OUTPUT` and `INPUT_OUTPUT`.
- In a request, an item with `OUTPUT`, `IGNORE`, or `DERIVED`, or an unknown property, is a `MappingException` **[builder call: always strict]**.
- The codec writes each item that is not in the request JSON with its `VALUE`, or with spaces for text and zero for a number. A JSON `null` is the same as a missing property.
- A `FILLER` gets spaces, or its `VALUE`.
- Each error message starts with the JSON path, for example `$.orderLines[2].quantity: ...`.

**Types:**

| COBOL | JSON | Encode | Decode |
|---|---|---|---|
| `PIC X(n)` | string | Spaces on the right. A longer text, or a character that the charset cannot encode, is an error. | `0x00` becomes a space. The spaces on the right are removed. The rules of the generated codec. |
| `PIC 9` `DISPLAY`, `COMP-3`, with `NUMBER` | number | Accepts an integer node, a `DecimalNode`, or a decimal string. Refuses a `DoubleNode` and a `FloatNode`: the value can already have lost digits. More integer digits than the picture is an error. More decimals than the scale is an error, except trailing zeros. | Scale 0: an integer node. Scale > 0: a `DecimalNode` with exactly `scale` decimals. |
| The same, with `DECIMAL_STRING` | string | As `NUMBER`. | A plain decimal string with `scale` decimals, for example `"1500.00"`. |
| Signs | | Overpunch, and `SIGN LEADING` or `TRAILING`, `SEPARATE` or not. A negative value in an unsigned item is an error. | The rules of the generated codec. |
| `COMP`, `COMP-4`, `BINARY` | number | Big-endian, two's complement if signed. The value is limited by the digits of the picture: `PIC 9(4) COMP` accepts 9999 maximum. | Accepts all values that fit in the binary size, as the generated codec does (main design, 5.3: hosts with `TRUNC(BIN)`). Never `null`. |
| `COMP-5` | number | Big-endian. The value is limited by the binary size: `PIC 9(4) COMP-5` accepts 65535 maximum. | The same as `COMP`. |
| `DATE`, `TIME`, `DATE_TIME` | ISO string: `2026-11-17`, `14:30:00`, `2026-11-17T14:30:00` | `PIC X`, `DISPLAY`, or `COMP-3`, with `format`, where the generated codec supports the combination **[builder call]**. | The rules of the generated codec for zero, blank, and LOW-VALUES (packed dates design). |

Use `BookWriter` and `BookReader` for all conversions **[builder call]**. Do not use `codec.internal`. If `COMP-5` needs a primitive that `BookWriter` does not have, add a public method to `BookWriter` and `BookReader` in `copymapper-core`.

`COMP-5` is different from the generator today on write: the generated codec writes only values within the picture digits. Thus, the `COMP-5` encode fixtures use independent expected bytes. The decode fixtures can use the generated codec.

**`null` and blank values on decode.** The codec writes an explicit JSON `null` when the generated codec gives `null`, for example a blank `DISPLAY` number or a zero date. A blank text is `""`. The codec never omits a property of the response direction **[builder call]**.

**Directions of groups and tables** **[builder call]**:

- The draft gives each group and table `INPUT_OUTPUT`. A child can have a narrower direction than its parent: an `INPUT` child in an `INPUT_OUTPUT` group.
- A child with a direction that its parent does not include is an `INVALID_DIRECTION` open decision, for example an `INPUT` child in an `OUTPUT` group.
- `IGNORE` on a group or a table removes all its children from JSON. Their bytes stay.
- A group or a table with no child in one direction is not in the JSON of that direction.

### 5.8 Fixed `OCCURS` (#138)

The rules of the fixed-occurs design and the empty-item markers design:

- The area is always `max × item length` bytes.
- **Count mode** (`table: COUNT`): encode sets the counter to the array size. Decode reads the counter. A blank counter is 0. A negative counter, or a counter above the maximum, is an error.
- **Marker modes** (`LOW_VALUES`, `SPACES`, `KEY`): decode finds the used slots with the marker. A used slot after an empty slot is an error. The empty slots at the end are not in the array.
- Encode writes the marker in each unused slot. An array longer than the maximum is an error. In a marker mode, an item that would read as empty is an error.
- The JSON value is an array with the used items only.

### 5.9 `OCCURS DEPENDING ON` (#141)

- The table occupies `counter × item length` bytes. The items after the table move.
- The counter has the direction `DERIVED` by default: it is not in JSON. Encode writes the array size. Decode reads the counter to find the number of items.
- If the operator makes the counter visible and a request gives a value that is not the array size, the codec throws `MappingException`. The codec never changes the value silently.
- Encode: an array with more items than the maximum, or fewer than the minimum, is an error.
- Decode: a blank counter is 0. A counter below the minimum, above the maximum, or negative is an error. A response that is too short for the items is an error.
- The generated codec does not check the minimum on decode. This difference shows only in an error case.
- Placement: the same rules as the copybook generator. The counter is an elementary integer item before the table, in the same group or in a parent group. An `OCCURS DEPENDING ON` table in another table, or a counter in a table, is an `INVALID_TABLE` open decision **[builder call]**.

### 5.10 `REDEFINES` (#142)

An area is a redefined item and all items that redefine it. The variant of the area is one of:

- **A discriminator.** An elementary item before the area, in the same group or in a parent group. In a table, it is in the same item. A map gives the alternative for each value. The values are compared as the decoded JSON value, as text **[builder call]**.
- **A fixed selection.** One alternative for the request and one for the response. They can be different.

Behavior:

- JSON has only the selected alternative, as an object or a value with the JSON name of that alternative:

  ```json
  {"accountType": "01", "checkingData": {"overdraftLimit": 1500.00, "cardNumberMask": "4111********1111"}}
  ```

- Decode with a discriminator: read the discriminator, then decode only the selected alternative. A value without an alternative is a `MappingException` with the value.
- Encode with a discriminator: the JSON contains exactly one alternative. If the discriminator is missing (or `DERIVED`), the codec writes the value of that alternative. A discriminator value that selects another alternative is an error. Two alternatives are an error. A map with two values for one alternative needs the discriminator in the request **[builder call]**.
- Fixed selection: the codec uses the selected alternative of the direction. Another alternative in the JSON is an error.
- The area always has the length of the longest alternative. A shorter alternative gets spaces up to that length, as a subtype item of the generated codec.
- An alternative can contain a fixed table, and another area. It cannot contain an `OCCURS DEPENDING ON` table (COBOL forbids it).

The generated codec does not pad a subtype at the operation level, and it does not permit a trailer after the area. For these cases, a fixture gives the generated bytes followed by the padding spaces, and it says so.

### 5.11 The test kit (#139)

- A fixture is a folder with: `book.cpy`, `spec.json` (the full mapping spec, in its JSON form), `request.json`, `request.hex`, `response.hex`, and `response.json`.
- One test runs each fixture through the interpreted codec, in both directions, and compares the bytes and the JSON.
- One test also generates the Java classes from the same `spec.json` (5.12) and compiles them with the processor, in memory, as `EndToEndTest` of `copymapper-copybook` does. It compares the bytes of the generated codec with the expected bytes. A case that the generator does not support (for example `COMP-5`) is marked in the fixture, and it uses independent expected bytes.
- Each type of 5.7, each table mode, each variant kind, and each error case of this design has a fixture.
- The copybook generator ignores `VALUE`. A fixture that leaves out an item with a `VALUE` is marked, and it does not use the generated codec for that direction.
- The fixtures are in the `testFixtures` source set of `copymapper-runtime`, and they are published with the module (Gradle test fixtures) **[builder call]**. Thus, CopyBridge can run the same fixtures.

### 5.12 Java classes from a mapping spec (#148)

- The copybook generator accepts a mapping spec as input. The CLI and the build plugins accept a `.mapping.json` file next to, or instead of, the `.cpy` file **[builder call: file name]**.
- The generator translates the mapping spec to its current inputs, then runs the current pipeline: the COBOL side gives the `DataItem` tree, and the REST side gives the `MappingOptions` **[builder call: the method]**.
- The translation:

  | Mapping spec | Generator |
  |---|---|
  | `format` of a date | `dates` |
  | `table: COUNT` | `countFields` |
  | `table: SPACES` or `KEY` | the empty-item markers |
  | A discriminator | `subTypes` |
  | A fixed selection with two alternatives | `directions` (a direction split) |
  | A direction outside a `REDEFINES` area | `INPUT_OUTPUT` in Java. In Java, all items occupy their bytes, so the JSON direction does not apply. |
  | `jsonName` | Not used. The Java names keep the rules of the generator. |

- A node can have an optional `javaName`: the class name of the record, a group, a table, or an alternative, or the field name of an item. Without it, the generator rules apply. `javaName` is not in the canonical form when it is `null`, so the hash of a spec without Java names does not change.
- A mapping spec with an item that the generator cannot express gives an error diagnostic with the path and the source line. Example: an item that the runtime supports and the generator does not.
- #148 covers the types of #136. Each later issue (#138, #140, #141, #142) adds its translation.

### 5.13 The CTG timeout for one call (#143)

- A new constant `CtgTransport.TIMEOUT_ATTRIBUTE` has the value `"ctg.timeoutSeconds"`.
- If `BookRequest.attributes()` has this key, the transport uses its value as the ECI timeout of this call. The value is a decimal integer, from 0 to 32767. `0` means no ECI timeout.
- Without the key, the timeout of the instance applies, as today.
- An incorrect value is an `IllegalArgumentException`, before the transport opens a connection.
- The design of the CTG transport and its wiki page get the attribute.

### 5.14 The failure before the send (#144)

- `TransportException` gets the property `requestSent()`. New constructors take it. The existing constructors keep the value `true`: "the request was possibly sent". This is the safe default.
- The subclasses (`ConnectionException`, `TransportTimeoutException`, `RemoteAbendException`, and `OperationNotFoundException`) get the same constructors.
- `CtgTransport` sets `requestSent()` to `false` only for these failures:
  - no route for the operation;
  - the COMMAREA is longer than 32,500 bytes;
  - no free connection after `connectionWaitTimeout`;
  - `EciConnector.open()` fails;
  - the thread is interrupted while it waits for a connection;
  - the transport is closed.
- All failures during or after `EciConnection.call`, and all ECI return codes, keep `true`. Change a return code to `false` only if the IBM documentation guarantees that the program did not run.
- The HTTP, MQ, and simulator transports do not change.

## 6. Issues and order

| Order | Issue | Scope |
|---|---|---|
| 1 | #135 | This design. |
| 2 | #147 | `copymapper-copybook` on Jackson 3. |
| 3 | #136 | The mapping spec, its JSON form, and the draft (in `copymapper-copybook`). The module `copymapper-runtime`, the compiler, and the layout. The types `PIC X`, `PIC 9` `DISPLAY`, `COMP-3`, groups, and `FILLER`. |
| 4 | #148 | Java classes from a mapping spec, for the types of #136. |
| 5 | #137 | The codec for the types of #136, with the directions and the JSON names. |
| 6 | #139 | The test kit, with fixtures for #136 and #137. |
| 7 | #138 | Fixed `OCCURS`, with count mode and the markers. |
| 8 | #140 | `COMP`, `COMP-4`, `COMP-5`, signs, and dates. |
| 9 | #141 | `OCCURS DEPENDING ON`. |
| 10 | #142 | `REDEFINES` with a discriminator or a fixed selection. |
| any | #143, #144 | The CTG transport. Independent of the runtime. |

Each issue after #139 adds its rules to the mapping spec, the compiler, the codec, and the generator translation, and adds its fixtures to the test kit.

## 7. Changes outside the module

- `settings.gradle.kts`: include `copymapper-runtime`.
- `build.gradle.kts`: add the module to the published modules, and to the mutation modules with a minimum score of 95%.
- `gradle/libs.versions.toml`: Jackson 3. Remove Jackson 2 after #147.
- `gradle/verification-metadata.xml`: run `scripts/update-verification-metadata.sh`.
- `docs/wiki/Dynamic-runtime.md`: a new page. `_Sidebar.md` and `Home.md`: a link to it.
- `docs/wiki/CICS-Transaction-Gateway.md`: the timeout attribute and `requestSent()`.
- `docs/wiki/Contributing.md`, `RELEASING.md`, and `README.md`: the new module.
- `ROADMAP.md`: #147 and #148 in the group "Dynamic runtime", and #149 in the group "Copybook generator".
- `docs/wiki/Copybook-generator.md`: the mapping spec as an input.

## 8. Calls made by the builder of this design

- The Jackson 3 version line of Spring Boot 4.1.
- The mapping spec in `copymapper-copybook`, and one runtime module for the compiler, the layout, and the codec. The package names and the class names.
- The translation of a mapping spec to the current generator inputs, and the file name `.mapping.json`.
- The index in the path of a `FILLER`.
- A strict request: an unknown property or a property that is not in the request is an error.
- The `jsonType` `DECIMAL_STRING`, for callers that cannot keep the precision of a JSON number.
- The discriminator values compared as text.
- Spaces after a shorter `REDEFINES` alternative, also at the record level.
- The test fixtures published with the module.
- `COMP-5` with the full binary range, with independent fixtures.

## 9. Left to the implementation

- The exact text of the messages, in the format of 5.5 and 5.7.
- The internal structure of the layout and of the codec.
- The Javadoc of the public types.
- The order of the tasks in each plan.
