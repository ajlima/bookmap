# Copybook generator: count field of a fixed table

Date: 2026-11-13
Status: implemented in v0.16.0.

Related documents: `docs/design/2026-11-11-bookmap-copybook-design.md` (the copybook generator), `docs/design/2026-11-11-bookmap-fixed-occurs-design.md` (fixed collections and count mode), GitHub issue #81.

## 1. Intent

Many copybooks keep the number of used entries of a fixed table in a separate field:

```cobol
       01  GET-INVOICE.
           05  INVOICE-LINE-COUNT       PIC 9(3).
           05  INVOICE-LINE OCCURS 100 TIMES.
               10  PRODUCT-CODE         PIC X(12).
```

The core supports this case with `@BookCollection(fixed = true, dependsOn = "invoiceLineCount")`: count mode.
Before this change, the generator wrote a fixed collection without `dependsOn`. The codec then used LOW-VALUES mode, and the developer had to add `dependsOn` by hand.
This spec adds an option that links a count field to a fixed table. The generator does not guess the link.

## 2. The option

The option `countFields` is a map. The key is the name of a fixed table. The value is the name of its count field.

| Tool | Syntax |
|---|---|
| Options file | `"countFields": { "INVOICE-LINE": "INVOICE-LINE-COUNT" }` |
| Command line | `--count-field INVOICE-LINE=INVOICE-LINE-COUNT`. Repeat the option for more tables. |
| Gradle plugin, Maven plugin | The options file `<NAME>.json` next to `<NAME>.cpy`. |

- A name can be qualified with `OF` or `IN`, as in the other options.
- On the command line, `--count-field` adds an entry to `countFields` of the `--config` file. For the same table, the command line has priority.
- `--count-field` is a mapping option. Thus, it is not permitted with a model input.
- `MappingOptions` gets the component `countFields`. A constructor without it stays, so that existing callers compile.

## 3. Mapping

With the option, the mapper writes `dependsOn` on the fixed collection. `fixed` stays `true`.

The mapper checks the option before the walk (names, kinds) and in the walk (position, type, size).

| Check | Message |
|---|---|
| The table exists and is not ambiguous. | `count field option: item X does not exist`, `count field option: item X is ambiguous. Qualify it: ...` |
| The count field exists and is not ambiguous. | Same messages. |
| The table has `OCCURS`. | `count field option: X has no OCCURS` |
| The table has no `DEPENDING ON`. | `count field option: X has OCCURS DEPENDING ON. Remove the option: the DEPENDING ON item is the count field.` |
| The count field is an elementary numeric item without decimals. | `count field option: X must be an elementary numeric item without decimals` |
| One count field serves only one table. | `count field option: N is already the count field of T` |
| The count field is in the same class, before the table. | `count field N must be in the same class, before the table` |
| The count field is an integer (at most 18 digits). | `count field N must be an integer` |
| The digits of the count field hold the maximum of the table. | `count field N has 1 digits and cannot hold 20` |
| The table is in a generated class. | `count field option: T is not in a generated class, so the count field has no effect` |

The checks of the walk are the checks of `DEPENDING ON`. The mapper uses one method for both, with another label in the message.

## 4. Suggestion

The mapper gives an `info` diagnostic if all these conditions are true:

- A fixed table has no count field from the option.
- The item just before the table, in the same group, is an elementary numeric item without decimals.
- The name of that item ends with `-COUNT`.
- The table is not a `FILLER`.

```
GET-INVOICE.cpy:4:12: info: INVOICE-LINE: INVOICE-LINE-COUNT can be the count field of this fixed table. To link them, set "countFields": {"INVOICE-LINE": "INVOICE-LINE-COUNT"} in the options file, or --count-field INVOICE-LINE=INVOICE-LINE-COUNT.
```

The suggestion does not change the model.

## 5. Model JSON

No change. A collection member already has `fixed` and an optional `dependsOn`. A fixed collection with a count field has both. `formatVersion` stays `1`.
A model that a former version wrote is still valid. A model with `"fixed": true` and `dependsOn` was already valid for the reader and the Java generator.

## 6. Decisions for review

1. **Plugins: options file only, no plugin property.**
   Options: (a) a `countFields` map property in the Gradle extension and a `countFields` parameter of the Maven goal; (b) only the options file `<NAME>.json`.
   Choice: (b). The plugins have no property for any mapping option today (`stripPrefixes`, `directions`, `subTypes`). A count field names items of one copybook, so a project-wide property would apply to all copybooks. The options file is already an input of the up-to-date checks of both plugins. The plugin examples now use `countFields`, and their tests check count mode end to end.

2. **Names: COBOL names, bare or qualified.**
   Options: (a) COBOL names; (b) Java field names.
   Choice: (a). All other options use COBOL names, and the user reads them in the copybook. A qualified name (`N OF ORDER-A`) solves a name in more than one record.

3. **Error position.**
   Options: (a) all errors at `<file>:1:1`, as for direction and subtype decisions; (b) the table position when the table is known.
   Choice: (b), with (a) for a name that does not resolve. The table position helps more in a large copybook, and an unknown name has no position.

4. **The count field must come before the table, in the same class.**
   Options: (a) accept a count field after the table; (b) accept only before.
   Choice: (b). The issue asks for it, and the processor gives the compile error `dependsOn field '...' must come before the collection`. The generator uses the same rule as for `DEPENDING ON`.

5. **Digits check.**
   Options: (a) only check "numeric without decimals", as the issue says; (b) also check that the digits hold `maxOccurs`.
   Choice: (b). The processor gives a compile error for a count field that cannot hold `maxOccurs`. An early generator error is clearer. `DEPENDING ON` has the same check.

6. **One count field for one table.**
   Choice: an error for two `countFields` entries with the same count field. The processor has the same rule. The generator does not check a count field that is both a `DEPENDING ON` item and a `countFields` value: the processor reports it.

7. **The option on a table with `DEPENDING ON`.**
   Options: (a) ignore the option; (b) error.
   Choice: (b). The option has no effect there, and a silent option hides a mistake.

8. **A linked table that the mapper skips.**
   Example: a table that redefines an elementary item. Options: (a) ignore; (b) error.
   Choice: (b), but only if no other error exists. Another error, for example a nested `OCCURS`, already explains the missing table.

9. **Suggestion scope.**
   Options: (a) any `-COUNT` item before the table in the same class; (b) only the item just before the table, in the same group.
   Choice: (b), as the issue says. The suggestion also requires an integer picture, because the option rejects other count fields. The name check is exact (`-COUNT` at the end, in upper case, as the parser keeps it). Names such as `LINE-CNT` or `NUM-LINES` get no suggestion.

10. **The existing `COMP` diagnostic.**
    A fixed table of `COMP` items gave the `info` text `... Add dependsOn if the program has a count field.`. It now says `... Link the count field with the option countFields if the program has one.`, because a hand edit is lost at the next generation. A linked table gets no such diagnostic: count mode can write the value 0.

11. **`MappingOptions` compatibility.**
    Options: (a) change the canonical constructor only; (b) also keep the former 5-argument constructor.
    Choice: (b). `bookmap-copybook` is a published module. The extra constructor keeps callers compiling, and it keeps the diff small next to other changes that add options.

12. **Command line merge.**
    Choice: `--count-field` adds to `countFields` of the options file, and the command line has priority for the same table. This is the rule of `--direction`.
