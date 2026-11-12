# bookmap — Copybook generator (design)

Date: 2026-11-11
Status: implemented in v0.4.0.
Base package: `pt.dazz.bookmap.copybook`
Parent spec: `2026-11-10-bookmap-design.md`
Depends on: `2026-11-11-bookmap-fixed-occurs-design.md`. Implement and merge the fixed `OCCURS` feature first, on its own branch and PR. Start the copybook branch from `main` after that merge (or from the fixed `OCCURS` branch, and rebase it after the merge).

Decisions marked **[builder call]** come from the spec author, not from the project owner. You can change them if you have a good reason. All other decisions come from the project owner.

## 1. Intent

Today a developer reads a COBOL copybook line by line and writes the BookMap class by hand. For each field, the developer writes the Java name, the `order`, the `pic`, and the Java type. For each `OCCURS`, the developer writes an item class. The owner says that this work is the main cost: field by field, with the correct order, names, and mapping.

The copybook generator does this work. It reads the text of a copybook and writes Java classes with BookMap annotations. The classes compile with the BookMap processor without a change.

Two uses are in scope:

- **Scaffold:** generate the classes one time. The developer then keeps them by hand.
- **Build:** generate the classes again from the copybook in each build.

## 2. Architecture

The generator is a pipeline with three steps:

```
copybook text ──parse──▶ AST ──map──▶ model (JSON) ──generate──▶ Java classes
```

1. **Parse.** Read the copybook (fixed or free format, `COPY ... REPLACING`). Produce an **AST**: the tree of COBOL data items, as COBOL defines them. The AST knows nothing about Java.
2. **Map.** Apply the BookMap decisions to the AST: class names, field names, Java types, `pic`, `order`, directions, collections, and subtypes. Produce the **model**.
3. **Generate.** Write the Java classes from the model, with JavaPoet.

The model has a JSON form. The owner wants this form for future modules: they will keep old versions of the model, compare them, and show the changes to the developer. This module is **stateless**. It does not keep, compare, or merge models.

### 2.1 Modules

| Module | Content | Dependencies |
|---|---|---|
| `bookmap-copybook` | Parser, AST, mapper, model, JSON reader and writer, Java generator. A public library API. | JavaPoet, Jackson (`jackson-databind`) **[builder call]**. No Gradle, Maven, Spring, or middleware library. |
| `bookmap-copybook-cli` | A command-line program. It reads the options and calls the API. | `bookmap-copybook`, picocli **[builder call]** |

Later work adds a Gradle plugin and a Maven plugin. They also call the API. They are not in this design.

The library does not depend on `bookmap-processor`. It can depend on `bookmap-core` for the `Direction` enum. **[builder call]**

### 2.2 Library API

The API has one entry point for each step, and one for the full pipeline. The builder chooses the exact names. A sketch:

```java
ParseResult parsed = CopybookParser.parse(source, parseOptions);      // AST + diagnostics
MapResult mapped   = ModelMapper.map(parsed.ast(), mappingOptions);    // model + diagnostics
String json        = ModelJson.write(mapped.model());
BookModel model    = ModelJson.read(json);
List<JavaFile> out = JavaClassGenerator.generate(model, generateOptions);
```

The CLI and the future plugins must not need more than this API.

## 3. Goals, non-goals, and anti-goals

### 3.1 Goals

- Generate correct BookMap classes from realistic copybooks (orders, invoices, accounts).
- Generated classes compile with the BookMap processor. They round-trip through the simulator transport.
- A deterministic model JSON: the same input and options always give the same bytes.
- A deterministic Java output: the same model and options always give the same bytes.
- Clear diagnostics for all problems, with the line and column.
- 100% line and branch coverage in both new modules.

### 3.2 Non-goals

- A Gradle plugin or a Maven plugin (later work).
- Persistence of models, a comparison of two models, or a merge of developer edits. Future modules do this.
- Detection of dates. The copybook does not tell if `PIC 9(8)` is a date. The field stays a number.
- Constants from level 88, and initial values from `VALUE`.
- A parser for the PROCEDURE DIVISION or for full COBOL programs.

### 3.3 Anti-goals

- A guess when the copybook is ambiguous. If the generator is not sure, it reports an error that tells the developer which option to set.
- Generated code that compiles but has incorrect offsets.
- Silent data loss in the generated mapping. A skipped item always gives a diagnostic.
- Timestamps, absolute paths, or other variable data in the model or in the Java output.

## 4. Parse

### 4.1 Source format

- **Fixed format** is the default. Columns 1–6: sequence area, ignored. Column 7: indicator. Columns 8–72: code. Columns 73–80: ignored.
- Indicator `*` or `/`: comment line. Indicator `-`: continuation line. Indicator `D` or `d`: debug line, treated as a comment **[builder call]**. Any other indicator except space: error.
- **Free format** is an option. A `*>` starts a comment to the end of the line. No column rules.
- The source charset is an option. The default is UTF-8.
- COBOL words are not case-sensitive. The AST keeps the names in upper case.
- A tab character in fixed format: error, because the columns become unclear. **[builder call]**

### 4.2 `COPY ... REPLACING`

- A `COPY` statement includes another member. The parser finds it in the copy directories (an option, in order).
- The member name matches a file `NAME`, `NAME.cpy`, `NAME.cbl`, or `NAME.cob`. The match is not case-sensitive. **[builder call]**
- `OF library` and `IN library` are accepted and ignored. **[builder call]**
- `REPLACING` supports pseudo-text (`==old== BY ==new==`) and words (`OLD-NAME BY NEW-NAME`).
- Support the common tag convention, for example `REPLACING ==:PREFIX:== BY ==ORD==` on `05 :PREFIX:-ID`. The builder chooses the exact matching rule. Tests must cover this case.
- A nested `COPY` is supported. A cycle is an error.
- A missing member is an error.
- The `REPLACE` statement is an error.

### 4.3 Data items

The parser reads data description entries with these clauses:

| Clause | Parse result |
|---|---|
| Level `01`–`49` | Item in the tree. |
| Level `66` (`RENAMES`) | Error (not supported by BookMap). |
| Level `77` | Error. **[builder call]** |
| Level `88` | Condition. Kept in the AST, ignored by the mapper (info diagnostic). |
| Name or `FILLER` (or no name, which COBOL treats as `FILLER`) | Name. |
| `PIC` / `PICTURE` | Picture string. |
| `USAGE` (with or without the word `USAGE` and `IS`) | Usage. A group usage applies to all its elementary items. |
| `OCCURS n [TIMES]` | Fixed table. |
| `OCCURS m TO n [TIMES] DEPENDING ON name` and `OCCURS n DEPENDING ON name` | Variable table. |
| `INDEXED BY`, `ASCENDING KEY`, `DESCENDING KEY` | Accepted and ignored (info diagnostic). |
| `REDEFINES name` | Redefinition. |
| `VALUE` / `VALUES` | Accepted and ignored. |
| `SIGN TRAILING` without `SEPARATE` | Accepted (the BookMap default). |
| `SIGN LEADING`, `SIGN ... SEPARATE`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO` | Error. |
| `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL` | Error. |
| Edited pictures (`Z`, `*`, `,`, `.`, `+`, `-`, `CR`, `DB`, `B`, `0`, `/`), `P`, `A`, `N` | Error. |

The parser keeps the line and column of each item for diagnostics. The parser also keeps the normalized declaration text, for example `05 ORDER-AMOUNT PIC S9(7)V99 COMP-3.`.

### 4.4 Records

- Each level `01` item is a record. A copybook can have more than one record.
- A copybook without a level `01` item (for example, it starts at level `05`) gets one synthetic record. Its name comes from the record-name option, else from the file name without the extension.
- A level `01` item with `REDEFINES` of another `01`: two separate records, with a warning. **[builder call]**

## 5. Map

### 5.1 Classes

| COBOL | Java |
|---|---|
| Record (level `01`) | An operation class with `@BookOperation`. |
| Group without `OCCURS` | No class. Its items go into the class of the parent (the group is **flattened**). |
| Group with `OCCURS` | An item class, and a `@BookCollection` field in the parent class. |
| Elementary item with `OCCURS` | An item class with one field named `value`, and a `@BookCollection` field in the parent class. |
| `OCCURS` in an item of another `OCCURS` | Error (BookMap has no nested collections). |
| Elementary item | A `@BookField` field. |
| `FILLER` | A `@BookField` field `filler1`, `filler2`, ... in each class. |

`@BookOperation` has no value: the operation name is the simple class name. **[builder call]**

### 5.2 Names

- Split a COBOL name at each hyphen (`-`) and underscore (`_`).
- Class name: PascalCase (`GET-CUSTOMER-ORDERS` → `GetCustomerOrders`).
- Field name: camelCase (`ORDER-AMOUNT` → `orderAmount`).
- Prefix removal: an option gives a list of prefixes (for example `WS-`, `LK-`). The default list is empty. The match is not case-sensitive. If the removal leaves an empty name, keep the full name.
- A Java keyword or a literal (`class`, `true`, `null`) gets the suffix `Value` (`CLASS` → `classValue`).
- A name that starts with a digit gets the prefix `field` for a field and `Record` for a class (`1ST-ADDRESS` → `field1stAddress`). **[builder call]** for the class prefix.
- A class name equal to a simple name in `java.lang` (for example `STRING`, `OBJECT`) gets the suffix `Record`. **[builder call]**
- A field name collision in one class (two flattened groups with the same item name): add the name of the nearest group that makes the names different (`BILLING-ADDRESS` + `CITY` → `billingAddressCity`). If the names are still the same, add a number. **[builder call]** for the number.
- A class name collision in one package: add the name of the parent class. If the names are still the same, add a number. **[builder call]**

### 5.3 Fields and types

- `pic`: the convention style. The value is the COBOL picture and the effective usage, for example `pic = "S9(7)V99 COMP-3"`. A `DISPLAY` usage is not written.
- `order`: 10, 20, 30, ... in source order, in each class.
- Java type:

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9`/`S9` without decimals, 1 to 9 digits | `int` |
| `9`/`S9` without decimals, 10 to 18 digits | `long` |
| `9`/`S9` with decimals, or more than 18 digits | `BigDecimal` |

- Each field gets a Javadoc comment with the normalized COBOL declaration, for example `{@code 05 ORDER-AMOUNT PIC S9(7)V99 COMP-3.}`.

### 5.4 Collections

- `OCCURS n` → `@BookCollection(order = …, maxOccurs = n, fixed = true)`, without `dependsOn`. The copybook does not tell which field is a count. The developer adds `dependsOn` by hand if the program uses a count field.
- `OCCURS m TO n DEPENDING ON X` → `@BookCollection(order = …, maxOccurs = n, dependsOn = "<field name of X>")`. The minimum `m` is ignored.
- The `DEPENDING ON` field must be in the same generated class, before the table. If not, error.
- A fixed table of `COMP` items cannot write the value 0 without a count field: its bytes are all LOW-VALUES, and the fixed `OCCURS` codec rejects them. The mapper gives an `INFO` diagnostic for such a table.
- A generated operation class does not implement `Continuable`. Thus, the BookMap rule against an `INPUT_OUTPUT` collection in a `Continuable` class does not apply. If a developer adds `Continuable` by hand, the developer must also change the direction of such a collection.
- The item class gets the name of the `OCCURS` item (`ORDER-LINE` → `OrderLine`). The collection field gets the field name of the same item (`orderLine`).

### 5.5 Directions

The copybook does not give the direction of a field.

**Scope (owner decision).** Directions apply only to the alternatives of a group `REDEFINES` area, and to the items in those alternatives. All other items are `INPUT_OUTPUT`. Reason: in BookMap, an `INPUT` field does not occupy bytes in the output area. Outside a `REDEFINES` area, a direction would move the offsets of the items after it, and the offsets would not agree with COBOL. Inside a `REDEFINES` area, the input and output alternatives share the same memory, so the separation by direction agrees with COBOL.

- A direction decision for an item outside a group `REDEFINES` area is an error. The message gives this reason.
- A name marker on a group outside a `REDEFINES` area has no effect.

Inside a group `REDEFINES` area, the generator resolves the direction of each alternative and item in this order:

1. **Explicit decision** of the developer: an option maps the COBOL name of an alternative group to `INPUT`, `OUTPUT`, or `INPUT_OUTPUT`. This decision has priority. A decision for an item inside an alternative is an error: a different direction inside an alternative would make the offsets disagree with COBOL. **[builder call]**
2. **Name markers**: split the name of the alternative group at each hyphen. If one part is an input marker, the group is `INPUT`. If one part is an output marker, the group is `OUTPUT`. If both, the group has no marker direction, with a warning. Markers apply to alternative groups only.
   - Default input markers: `INPUT`, `IN`, `REQUEST`, `REQ`.
   - Default output markers: `OUTPUT`, `OUT`, `RESPONSE`, `RESP`.
   - The developer can replace or extend both lists (for example, with `ENTRADA` and `SAIDA`).
3. **Inheritance**: each item in an alternative gets the direction of its alternative group.
4. **Default**: an alternative without 1 or 2 has no direction. Section 5.6 then reports the area as a doubt, unless a subtype decision exists.

In the generated code:

- A field with `INPUT_OUTPUT` has no `direction` attribute.
- A collection always gets an explicit `direction`, because the BookMap default for a collection is `OUTPUT`. **[builder call]**
- An item class field has no `direction` (a BookMap rule). Thus, a direction decision, a marker group, or a direction split inside an `OCCURS` item is an error. The message tells the developer to use subtypes or to edit the class by hand. **[builder call]**
- A count field without an explicit `direction` gets the direction of its collection (a BookMap rule). If the resolved direction of the count field is different from the direction of its collection, the generator writes the count field direction explicitly. If the count direction does not include the collection direction (for example, an `INPUT` count for an `OUTPUT` collection), this is an error. **[builder call]**

### 5.5.1 Names in options

Options name COBOL items (directions, subtype areas, discriminators). A COBOL name is often not unique, for example `CITY` in two address groups.

- An option accepts a bare name (`CITY`) or a qualified name (`CITY OF BILLING-ADDRESS`, with more `OF` parts if necessary). `IN` is a synonym of `OF`.
- A bare or qualified name that matches more than one item is an error. The message lists the matches as qualified names.
- A name that matches no item is an error.
- The parser also accepts a qualified name in `DEPENDING ON` (`DEPENDING ON LINE-COUNT OF ORDER-HEADER`).
- A copybook with more than one record: a name can start with the record as the last `OF` part.

**[builder call]** for all of 5.5.1.

### 5.6 `REDEFINES`

**Elementary item with `REDEFINES`.** Keep the redefined item. Skip the item with `REDEFINES`, with a warning that names both items.

**Group with `REDEFINES`.** The redefined group and the groups that redefine it are the **alternatives** of one area. The generator resolves the area in this order:

1. **Subtypes**, if the developer gives a subtype decision for the area (see 5.7).
2. **Direction split**, if there are exactly two alternatives, and one resolves to `INPUT` and the other to `OUTPUT` (through an explicit decision or a marker, see 5.5).
3. **Otherwise**: error. The message gives the area, the line, and the two options that resolve it, with an example of each option.

**Direction split.** BookMap calculates the offsets separately for each direction. Thus, the input fields and the output fields of the same area need no subtypes. Both alternatives stay in the same class, with their directions. An example:

```cobol
01  GET-INVOICE.
    05  RETURN-CODE          PIC X(4).
    05  REQUEST-DATA.
        10  INVOICE-ID       PIC X(12).
    05  RESPONSE-DATA REDEFINES REQUEST-DATA.
        10  INVOICE-AMOUNT   PIC S9(7)V99 COMP-3.
        10  DUE-DATE         PIC 9(8).
    05  TRAILER              PIC X(10).
```

gives `returnCode` (`INPUT_OUTPUT`), `invoiceId` (`INPUT`), `invoiceAmount` and `dueDate` (`OUTPUT`), and `trailer` (`INPUT_OUTPUT`).

If the two alternatives have different lengths and an item comes after the area, the offsets of that item differ between the directions. In COBOL, the item comes after the longest alternative. Thus, the generator adds a filler field at the end of the shorter alternative, with its direction, to make the lengths equal. **[builder call]** In the example, `REQUEST-DATA` is 12 bytes and `RESPONSE-DATA` is 13 bytes. The generator adds a 1-byte `INPUT` filler after `invoiceId`.

### 5.7 Subtypes

A subtype decision gives, for one area:

- the discriminator (a COBOL name of an elementary item before the area, in the same class),
- the discriminator value of each alternative,
- optionally, a class name for each alternative.

The generator then writes:

- `@BookSubTypes(discriminator = …, value = { … })` on the class that contains the area (an operation class or an item class). This class is the base class. It is not abstract. **[builder call]**
- One subclass for each alternative. Its default name is the PascalCase name of the alternative group. The subclass has the fields of the alternative.
- The `order` values of a subclass start after the highest `order` of the base class.

Errors:

- An item after the area, at the level of the area or higher, in the same class. BookMap requires the redefined area at the end of the layout.
- A discriminator that does not exist, is not before the area, or has a type that BookMap does not permit.
- An alternative without a value, or a value for a name that is not an alternative.
- A subtype area inside another subtype area.
- A discriminator that resolves to `INPUT` in an operation class (a BookMap rule).

If an area has a subtype decision, the subtype decision wins over a direction split (5.6, step 1). **[builder call]**

The processor checks the remaining BookMap rules on the generated code. The generator checks the rules above, because it can give the copybook line in the message.

## 6. Model JSON

- The JSON has a format version field, for example `"formatVersion": 1`. **[builder call]** for the name.
- The model contains: the source name (the file name only, without a directory), the classes in source order, and for each class its kind (operation, item, subtype), its name, its base class and subtype data, and its members.
- Each member contains: kind (field or collection), Java name, Java type, `order`, `pic`, direction (resolved), the COBOL name, and the normalized declaration text. A collection also contains `maxOccurs`, `fixed`, `dependsOn`, and the item class.
- The model does not contain line numbers. A new comment line in the copybook must not change the model. **[builder call]**
- The model does not contain generation options (package, Lombok). Those come from the generate step.
- The writer uses a fixed key order, two-space indentation, `\n` line ends, and a final new line. **[builder call]** for the format details.
- The reader rejects an unknown format version, an unknown key, and a missing mandatory key, with a clear message.

## 7. Generate

- Options: the Java package (mandatory), the output directory, and Lombok (on or off).
- One `.java` file for each class. The generator overwrites existing files. For a scaffold, use a different directory. **[builder call]**
- Each class has a Javadoc header: "Generated by the BookMap copybook generator from `<source name>`." No date and no version. **[builder call]**
- Without Lombok: private fields, a public getter and a public setter for each field. Each class has an implicit public constructor without arguments.
- With Lombok: `@Data` and `@NoArgsConstructor`. A subclass also gets `@EqualsAndHashCode(callSuper = true)` and `@ToString(callSuper = true)`. **[builder call]**
- Records are not supported. BookMap needs setters.
- The generated code must compile without warnings under the BookMap build settings.

## 8. Diagnostics

- Each diagnostic has a severity (`ERROR`, `WARNING`, `INFO`), a source name, a line, a column, and a message.
- The parser and the mapper collect all diagnostics. They do not stop at the first error.
- If a step has an `ERROR`, the next step does not run.
- Messages follow ASD-STE100: short, active, with the action to take.
- Format in the CLI: `<source>:<line>:<column>: <severity>: <message>`. **[builder call]**

## 9. CLI

Two commands. **[builder call]** for the exact flag names.

```
bookmap-copybook parse    <copybook>              [options] --output model.json
bookmap-copybook generate <copybook | model.json> --package com.example.orders --output-dir src/main/java [options]
```

- `parse` writes the model JSON.
- `generate` accepts a copybook or a model JSON. The `.json` extension selects the model input. With a model input, the parse and map options are not permitted.
- Parse and map options: `--format fixed|free`, `--charset`, `--copy-dir` (more than one), `--record-name`, `--strip-prefix` (more than one), `--input-marker` and `--output-marker` (add to the defaults), `--direction NAME=DIRECTION` (more than one, alternative groups only).
- Generate options: `--package`, `--output-dir`, `--lombok`.
- `--config <file.json>`: a JSON file with all options, including subtype decisions and replacement marker lists. A command-line option has priority over the same option in the file.
- Exit codes: `0` success (warnings are permitted), `1` diagnostics with an `ERROR`, `2` incorrect command line or options.
- Diagnostics go to the standard error stream.
- Users run the CLI with one `java -jar` command. The builder chooses how (for example, an executable jar with all dependencies, published with a classifier).

A config file sketch:

```json
{
  "format": "fixed",
  "copyDirs": ["copylib"],
  "stripPrefixes": ["WS-"],
  "inputMarkers": ["INPUT", "IN", "REQUEST", "REQ", "ENTRADA"],
  "outputMarkers": ["OUTPUT", "OUT", "RESPONSE", "RESP", "SAIDA"],
  "directions": { "INVOICE-QUERY": "INPUT", "INVOICE-DETAIL": "OUTPUT" },
  "subTypes": {
    "CHECKING-DATA": {
      "discriminator": "ACCOUNT-TYPE",
      "values": { "CHECKING-DATA": "01", "SAVINGS-DATA": "02" },
      "classNames": { "SAVINGS-DATA": "SavingsAccount" }
    }
  },
  "package": "com.example.accounts",
  "lombok": false
}
```

## 10. Tests

- Unit tests for the parser, the mapper, the JSON reader and writer, the names, and the generator.
- Realistic copybooks in the test resources: an order with a fixed table and a variable table, an invoice with `REQUEST`/`RESPONSE` areas (direction split), a bank account with subtypes, and a copybook with `COPY ... REPLACING`. Use international business data.
- End-to-end tests: generate the Java, compile it with `javac` and the BookMap processor, load the classes, and round-trip an object through the simulator transport.
- Golden-file tests for the model JSON and the Java output. They also prove determinism.
- CLI tests: each command, each exit code, and the config file.
- 100% line and branch coverage in `bookmap-copybook` and `bookmap-copybook-cli`. JaCoCo verification includes both modules.

## 11. Examples and documentation

- `bookmap-examples`: add copybooks and a Gradle task that runs the generator in the build. The generated classes go to a generated source directory and compile with the processor. Tests round-trip them through the simulator. This shows the build use without a plugin.
- README: a new section "Copybook generator", with the pipeline, the CLI, the mapping table, the direction rules, and the limitations. Update the module list.

## 12. Release

Both modules are published to Maven Central with the other modules. A `feat:` commit merged to `main` publishes a new minor version. The owner must approve the merge.
