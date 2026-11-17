This page describes the mapping spec and the layout compiler of the module `copymapper-runtime`.
They let a program read a copybook at run time, without generated classes.
The interpreted codec is not available yet.

## The mapping spec

A mapping spec describes one copybook record. It has two sides for each item:

- The COBOL side: the name, the level, the `PIC`, the usage, the sign, `OCCURS`, `REDEFINES`, `VALUE`, and the condition names.
- The REST side: the JSON name, the direction, the JSON type, the format, and the Java name.

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
`read` throws `MappingSpecFormatException` if the JSON is not a valid spec.

## The draft

`MappingSpecs.draft(copybook, recordName, options)` makes a spec from a parsed copybook.
The draft has a default REST side: a JSON name for each item, and the direction and the JSON type that fit the picture.
Edit the JSON to change the REST side. The record name can be in any case.
`DraftOptions` sets the charset. `DraftOptions.defaults()` uses `IBM037`.

## The layout compiler

`LayoutCompiler.compile(spec)` checks a spec and computes the offset and the length of each item.
It returns a `Compilation`:

| Method | Result |
|---|---|
| `complete()` | `true` if the spec has no open decision. |
| `openDecisions()` | The list of the open decisions. |
| `layout()` | The `Layout`. It is `null` if the spec has an open decision. |

An open decision is a problem that the author of the spec must resolve before the runtime can use the spec.
`OpenDecision.format()` writes it as `source:line:column: path: message`.
Each decision has a kind (`OpenDecisionKind`):

| Kind | Meaning |
|---|---|
| `UNSUPPORTED_TYPE` | The runtime does not support a feature of the item. |
| `INVALID_COBOL` | The COBOL side of the item is not valid. |
| `UNRESOLVED_REDEFINES` | A `REDEFINES` cannot be resolved. |
| `INVALID_VARIANT` | A variant is not valid. |
| `INVALID_TABLE` | A table is not valid. |
| `INVALID_JSON_NAME` | The JSON name is missing or not unique. |
| `INVALID_DIRECTION` | The direction is not valid. |
| `INVALID_TYPE` | The JSON type is missing or does not fit the picture. |

The compiler reports all the open decisions, not only the first one.

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
