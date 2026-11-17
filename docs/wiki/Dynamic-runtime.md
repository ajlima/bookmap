This page describes the mapping spec and the layout compiler of the module `copymapper-runtime`.
They let a program read a copybook at run time, without generated classes.
The interpreted codec is not available yet.

## The mapping spec

A mapping spec describes one copybook record. It has two sides for each item:

- The COBOL side: the name, the level, the `PIC`, the usage, the sign, `OCCURS`, `REDEFINES`, `VALUE`, and the condition names.
- The REST side: the JSON name, the direction, the JSON type, the format, the table, the counter, the variant, and the Java name.

The spec is the record `MappingSpec` in the package `pt.dazz.copymapper.copybook.spec`. Its items are `SpecNode` records.
The spec also holds the format version (always `1`), the source name, the source hash, the charset, and the byte order (always `BIG_ENDIAN`).
The default charset is `IBM037`. The charset must be a single-byte charset.

## The JSON form

`MappingSpecJson.write(spec)` writes a spec as canonical JSON. `MappingSpecJson.read(json)` reads it again.
The canonical form is always the same for the same spec:

- The keys have a fixed order. The COBOL side comes first, then the REST side, then the Java name. The children come last.
- The text is compact, with no final new line.
- The text has no `null` value. A boolean appears only when it is `true`. An empty list does not appear.

`MappingSpecJson.sha256(spec)` gives the hash of the spec. It is the SHA-256 of the canonical text in UTF-8, as 64 lower-case hexadecimal digits.
The order of the values of a discriminator is part of the canonical text, so a change of that order changes the hash.
`read` throws `MappingSpecFormatException` if the JSON is not a valid spec. A duplicate key, an unknown key, and a `null` value are not valid.

The root object has these keys, in this order:

| Key | Value |
|---|---|
| `formatVersion` | `1`. |
| `sourceName` | The name of the copybook. |
| `sourceHash` | Optional. The SHA-256 of the declarations of the record, as 64 lower-case hexadecimal digits. |
| `charset` | The canonical name of a single-byte charset, for example `IBM037`. |
| `byteOrder` | `BIG_ENDIAN`. |
| `root` | The node of the record. |

A node has these keys, in this order. Each key is optional, except `cobolName`, `level`, and `path`:

| Key | Value |
|---|---|
| `cobolName` | The COBOL name, or `FILLER`. |
| `level` | The level number: `1` for the record, `2` to `49` for an item. |
| `path` | The path from the record, for example `GET-ACCOUNT.BALANCE`. A second item with the same name in a group gets `#2`, a third gets `#3`. |
| `picture` | The picture text, for example `9(7)V99`. A group has no picture. |
| `usage` | `DISPLAY`, `COMP`, `COMP3`, or `COMP5`. |
| `sign` | `TRAILING`, `LEADING`, `TRAILING_SEPARATE`, or `LEADING_SEPARATE`. |
| `occurs` | An object with `min`, `max`, and the optional `dependingOn` path. |
| `redefines` | The path of the redefined item. |
| `value` | An object with `kind` (`TEXT`, `NUMBER`, or `FIGURATIVE`) and `text`. |
| `conditions` | A list of objects with `name` and `values`. Each value is an object with `from` and the optional `thru`, as `value`. |
| `location` | An object with `source`, `line`, and `column`. |
| `blankWhenZero` | `true` for `BLANK WHEN ZERO`. |
| `edited` | `true` for a picture with edit symbols, for example `ZZ9.99`. |
| `jsonName` | The JSON name. |
| `direction` | `INPUT`, `OUTPUT`, `INPUT_OUTPUT`, `IGNORE`, or `DERIVED`. |
| `jsonType` | `TEXT`, `NUMBER`, `DECIMAL_STRING`, `DATE`, `TIME`, or `DATE_TIME`. |
| `format` | The date or time pattern, for example `yyyyMMdd`. |
| `table` | An object with `mode` (`COUNT`, `LOW_VALUES`, `SPACES`, or `KEY`), and `path` for `COUNT` and `KEY`. |
| `counter` | The path of the counter of an `OCCURS DEPENDING ON` table. |
| `variant` | An object with `discriminator` (a path) and `values` (a map from a value to the path of an alternative). Or an object with `request`, `response`, or both (the path of an alternative). |
| `javaName` | The Java name. |
| `children` | The list of the child nodes, in order. |

The `sourceHash` is the SHA-256 of the declarations of the items of the record, after `COPY` expansion.
A change of a level, a name, a picture, or another clause of an item changes the hash.
A change of a `VALUE` clause, a level 88 item, the spaces between the words, or another record does not change the hash.

## The draft

`MappingSpecs.draft(copybook, recordName, options)` makes a spec from a parsed copybook.
The draft has a default REST side:

- Each item gets a JSON name, the direction `INPUT_OUTPUT`, and the JSON type `TEXT` for `PIC X` or `NUMBER` for a numeric picture.
- A `FILLER`, and each item in a `FILLER` group, gets no JSON name and the direction `IGNORE`.
- The counter of an `OCCURS DEPENDING ON` table gets the direction `DERIVED`. If more than one item has the name of the counter, the draft keeps the name as text.
- A fixed `OCCURS` gets the table mode `LOW_VALUES`. An `OCCURS DEPENDING ON` gets the path of its counter.
- A `REDEFINES` area gets no variant.

Edit the JSON to change the REST side. The record name can be in any case.
`DraftOptions` sets the charset. `DraftOptions.defaults()` uses `IBM037`.

## The layout compiler

`LayoutCompiler.compile(spec)` checks a spec and computes the offset and the length of each item.
It returns a `Compilation`:

| Method | Result |
|---|---|
| `complete()` | `true` if the spec has no open decision. |
| `openDecisions()` | The list of the open decisions. |
| `layout()` | The `Layout`. If the spec has an open decision, it throws `MappingException`. The message lists all the open decisions. |

An open decision is a problem that the author of the spec must resolve before the runtime can use the spec.
`OpenDecision.format()` writes it as `source:line:column: path: message`.
Each decision has a kind (`OpenDecisionKind`):

| Kind | Meaning |
|---|---|
| `UNSUPPORTED_TYPE` | The runtime does not support a feature of the item. |
| `INVALID_COBOL` | The COBOL side of the item is not valid: for example the picture, the usage, the sign, the path, or the level. Or the item is longer than 2147483647 bytes. |
| `UNRESOLVED_REDEFINES` | A `REDEFINES` cannot be resolved. |
| `INVALID_VARIANT` | A variant is not valid, or the item is not part of a `REDEFINES` area. |
| `INVALID_TABLE` | A table is not valid. A `table` needs a fixed `OCCURS`. A `counter` needs an `OCCURS DEPENDING ON`. |
| `INVALID_JSON_NAME` | The JSON name is missing or not unique. |
| `INVALID_DIRECTION` | The direction is missing, a `FILLER` is not `IGNORE`, the direction is `DERIVED`, or the direction is outside the direction of the group. |
| `INVALID_TYPE` | The JSON type is missing or does not fit the picture, or a `format` is on a type that is not a date or a time. |

The compiler reports all the open decisions, not only the first one.
An item with the direction `IGNORE`, and each item in an `IGNORE` group, needs no JSON name and no JSON type.
This version does not support `DERIVED`.

A `Layout` gives the spec, the root `LayoutNode`, the charset, and the maximum request and response lengths.
`layout.node(path)` finds a node by its path. A `LayoutNode` has the `offset`, the `length`, and the children of an item.

## Supported types

This version supports these items:

- `PIC X`.
- Unsigned `PIC 9`, also with a decimal point (`V`), in `DISPLAY` and in `COMP-3`.
- Groups.
- `FILLER`.

The compiler reports each other type as an open decision.

## Example

The example parses a copybook, makes a draft, writes the JSON, and compiles the spec.

```java
ParseResult parsed = CopybookParser.parse(
    "GET-ORDER.cpy", Files.readString(Path.of("GET-ORDER.cpy")), ParseOptions.defaults());
if (parsed.hasErrors()) {
    parsed.diagnostics().forEach(diagnostic -> System.err.println(diagnostic.format()));
    return;
}

MappingSpec spec = MappingSpecs.draft(parsed.copybook(), "GET-ORDER", DraftOptions.defaults());
Files.writeString(Path.of("get-order.spec.json"), MappingSpecJson.write(spec));
System.out.println("hash: " + MappingSpecJson.sha256(spec));

Compilation compilation = LayoutCompiler.compile(spec);
if (compilation.complete()) {
    Layout layout = compilation.layout();
    System.out.println("request length: " + layout.maxRequestLength());
} else {
    compilation.openDecisions().forEach(decision -> System.err.println(decision.format()));
}
```

The module `copymapper-runtime` depends on `copymapper-core` and `copymapper-copybook`:

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-runtime:<version>")
}
```
