# BookMap copybook generator, maximum record length: design

Date: 2026-11-13
Status: proposed.

Related documents: `docs/design/2026-11-11-bookmap-copybook-design.md` (the generator design), `docs/design/2026-11-13-ctg-transport-design.md` (the COMMAREA limit), GitHub issue #80.

## 1. Intent

A transport can have a size limit. For example, a CICS COMMAREA holds 32,500 bytes at most, and `CtgTransport` rejects a longer record at runtime.
Today the generator does not tell the developer about this. The error comes only at the first call.

This change adds the generator option `maxRecordLength`. A record that is longer than the value gives a `warning` diagnostic.
The map step also gives an `info` diagnostic with the length of each record. Thus, the developer can compare the length with the limit of the transport.

## 2. Goals and non-goals

Goals:

- Add the option `maxRecordLength` to the options file, the CLI (`--max-record-length <bytes>`), the Gradle plugin, and the Maven plugin.
- Give a `warning` with the record name, its length, the limit, and the largest tables.
- Give an `info` with the length of each record, always.

Non-goals:

- No default limit. The generator does not know the transport.
- No CICS concept in `bookmap-core` or `bookmap-processor`.
- No error. The warning does not stop the generation.

## 3. Design

### 3.1 The length

The class `RecordLength` (package `pt.dazz.bookmap.copybook.map`) computes the length of a level `01` item with the existing rule of `RedefinesResolver.length`:

- An elementary item has the byte length of its `PIC` and its `USAGE`.
- A group has the sum of its children. A `REDEFINES` area counts with its longest alternative.
- An `OCCURS` item has the length of one entry, multiplied by the maximum number of occurrences.

This is the length of the encoded record. With `OCCURS DEPENDING ON`, it is the maximum length, and the message says `at most`.

### 3.2 The diagnostics

`ModelMapper.map` calls `RecordLength.check` for each record, before it maps the record. The diagnostics have the position of the level `01` item.

```
GET-INVOICE.cpy:1:8: info: record GET-INVOICE has 35556 bytes
GET-INVOICE.cpy:1:8: warning: record GET-INVOICE has 35556 bytes, more than the maximum record length of 32500 bytes. The largest tables: INVOICE-LINE (100 x 298 = 29800 bytes).
```

The warning names the three largest tables, in decreasing length. A table is an `OCCURS` item. The check does not look into a table, because a table in a table is an error of the map step.
A record of exactly `maxRecordLength` bytes gives no warning.

### 3.3 The option

| Place | Name | Priority |
|---|---|---|
| `MappingOptions` | `maxRecordLength` (`Integer`, `null` = no check) | — |
| Options file | `"maxRecordLength": 32500` | — |
| CLI | `--max-record-length <bytes>` | The command line has priority over the options file. |
| Gradle | `bookmap { copybooks { maxRecordLength = 32500 } }` | The options file has priority over the extension. |
| Maven | `<maxRecordLength>32500</maxRecordLength>`, user property `bookmap.copybook.maxRecordLength` | The options file has priority over the parameter. |

A value less than 1 is an error: a `ModelJsonException` for the options file, exit code 2 for the CLI, and a `CopybookTreeException` for the plugins.
With a model JSON input, `--max-record-length` is not permitted, like the other mapping options.

### 3.4 Info diagnostics in the build plugins

Before this change, `CopybookTree` sent `info` diagnostics to the `warn` method of its `Log`. The plugins logged them as warnings.
With one record-length info for each copybook, each build would show one false warning for each copybook.
Thus, `CopybookTree.Log` gets the method `info`. The Gradle task logs it at the level LIFECYCLE, and the Maven goal logs it as `[INFO]`.
Both levels show in the normal output. Only the level changes: an `info:` line is no longer a warning.

## 4. Tests

- `RecordLengthTest`: the length of fixed and variable tables, the usage of a group, the order and the limit of the table list, a record of exactly the limit, a record without a table, and more than one record.
- `GeneratorConfigJsonTest`: the key and its errors.
- `CopybookTreeTest`: the routing of `info`, the value of the settings, the priority of the options file, and an incorrect value.
- `MainTest` (CLI): the info, the warning, the priority of the command line, and the usage errors.
- The Gradle and Maven plugin tests: the mapping of the property or the parameter, the warning in the build log, and the fingerprint of the Maven goal.

## 5. Decisions for review

1. **Where the check runs.**
   Options: (a) in the map step, on the copybook AST; (b) in the generate step, on the model; (c) in the plugins only.
   Choice: (a). The map step already computes item lengths with `RedefinesResolver.length` for `REDEFINES` areas. The model does not keep lengths, so (b) needs a second length rule. The CLI also needs the check, so (c) is not enough.
   Result: `parse` also shows the info and the warning. A model JSON input gives no length diagnostic, and it does not accept `--max-record-length`.

2. **Where the option lives.**
   Options: (a) a new component of `MappingOptions`; (b) a new options record; (c) a new parameter of `Copybooks.toModel`.
   Choice: (a). The option changes the map step only, like the other mapping options. A second constructor without the new component keeps the existing calls valid, to keep the change small.

3. **Priority between the plugin setting and the options file.**
   Options: the options file first, or the plugin setting first.
   Choice: the options file first, as for `lombok` and `package`. A team sets one limit for the tree, and one copybook can have another limit (for example, an operation on another transport).

4. **The length of a record with `OCCURS DEPENDING ON`.**
   Options: the maximum length, or no check.
   Choice: the maximum length, with `at most` in the message. The transport must accept the longest record.

5. **The tables in the warning.**
   Options: all tables, the largest table only, or the three largest tables.
   Choice: the three largest tables, each with `occurrences x entry length = total`. The list stays short, and the developer sees which `OCCURS` to change.

6. **The level of `info` diagnostics in the build plugins.**
   Options: (a) keep `info` as a warning; (b) log `info` at the info level of the build tool (Gradle `--info` only); (c) log `info` at a level that shows by default but is not a warning (Gradle LIFECYCLE, Maven `[INFO]`).
   Choice: (c). The issue asks to always show the length. Option (a) gives one false warning for each copybook. Option (b) hides the length in a normal Gradle build.
   This also changes the level of the existing `info` diagnostics, for example `condition X is ignored`. The wiki pages of the plugins tell the new levels.

7. **Number format in the messages.**
   Options: digits only (`35556`), or a group separator (`35,556`).
   Choice: digits only. The output does not depend on the locale, and the number is the same as in the option value.
