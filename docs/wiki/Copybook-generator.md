# Copybook generator

The copybook generator reads a COBOL copybook and writes the annotated Java classes.
The classes compile with the BookMap processor without a change.
Use it in two ways:

- **Scaffold.** Generate the classes one time. Then keep them by hand.
- **Build.** Generate the classes again from the copybook in each build. Use the [Gradle plugin](#gradle-plugin). `bookmap-examples` does this with the command line.

## Pipeline

```
copybook ──parse──▶ AST ──map──▶ model (JSON) ──generate──▶ Java classes
```

1. **Parse.** The parser reads the copybook and expands `COPY ... REPLACING`. It gives a tree of COBOL data items.
2. **Map.** The mapper applies the BookMap rules: names, Java types, `pic`, `order`, collections, directions, and subtypes. It gives the model.
3. **Generate.** The generator writes one Java class for each class of the model, with JavaPoet.

The model has a stable JSON form with a format version. The same copybook and options always give the same JSON and the same Java.
The generator does not keep old models and does not compare them. Other tools can keep the JSON files and compare them to find changes in a copybook.

The library `bookmap-copybook` has the same steps as a Java API. The entry point is `pt.dazz.bookmap.copybook.Copybooks`.

## Command line

Run the executable JAR from Maven Central (`bookmap-copybook-cli-<version>-all.jar`) or from the [GitHub release](https://github.com/ajlima/bookmap/releases):

```bash
java -jar bookmap-copybook-cli-all.jar generate ORDERS.cpy --package com.example.orders --output-dir src/main/java
```

| Command | Result |
|---|---|
| `parse <copybook> --output <model.json>` | Writes the model JSON. |
| `generate <copybook> --package <name> --output-dir <dir>` | Writes the Java classes. Existing files are replaced. |
| `generate <model.json> --package <name> --output-dir <dir>` | Writes the Java classes of a model JSON file. |

| Option | Meaning |
|---|---|
| `--format fixed\|free` | The reference format. Default: `fixed` (columns 7 to 72). |
| `--charset <name>` | The charset of the copybook files. Default: UTF-8. |
| `--copy-dir <dir>` | A directory with `COPY` members. Repeat the option for more directories. |
| `--record-name <name>` | The record name for a copybook without level `01`. Default: the file name. |
| `--strip-prefix <prefix>` | A prefix to remove from names, for example `WS-`. |
| `--input-marker <word>`, `--output-marker <word>` | A name part that marks an input or an output alternative. |
| `--direction <name>=<direction>` | The direction of a `REDEFINES` alternative. |
| `--lombok` | Write Lombok annotations instead of getters and setters. |
| `--config <file.json>` | A file with all options. A command-line option has priority. |

Exit codes: `0` success (warnings are permitted), `1` a diagnostic with an error, `2` an incorrect command line.
Each diagnostic has the format `<file>:<line>:<column>: <severity>: <message>`.
The generator reports all problems of a copybook before it stops.

### Configuration file

A configuration file holds all options, and the subtype decisions:

```json
{
  "format": "fixed",
  "copyDirs": ["copylib"],
  "stripPrefixes": ["WS-"],
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

Relative copy directories start at the directory of the configuration file.
`inputMarkers` and `outputMarkers` in the file replace the default lists.
A name in an option can be qualified, for example `CITY OF BILLING-ADDRESS`. A name that matches more than one item is an error.

## Gradle plugin

The plugin `pt.dazz.bookmap.copybook` generates the classes of a directory tree of copybooks in each build, before `compileJava`.
The directories of the tree give the subpackages. A file `<NAME>.json` next to `<NAME>.cpy` holds the options of that copybook.

The plugin is on Maven Central only. Add `mavenCentral()` to the plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("pt.dazz.bookmap.copybook") version "<version>"
}

dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}

bookmap {
    copybooks {
        packageName = "com.example.mainframe"   // src/main/copybook/orders/ORDER.cpy -> com.example.mainframe.orders
        copyDirs.from("src/main/copylib")       // the COPY members
    }
}
```

The plugin does not add dependencies to your project. Add `bookmap-core` and `bookmap-processor` yourself.
Declare the directories of the `COPY` members in `copyDirs`. The plugin does not search the tree for them.

### The DSL

| Property | Default | Meaning |
|---|---|---|
| `sourceDir` | `src/main/copybook` | The root of the copybook tree. The plugin scans it recursively. |
| `copyDirs` | empty | The directories with the `COPY` members, in search order. |
| `packageName` | none | The base package. It is required if a copybook exists and its options file does not set `package`. |
| `lombok` | `false` | Write Lombok annotations instead of getters and setters. |
| `packagePerCopybook` | `false` | Add one package segment from the copybook name. |
| `outputDir` | `build/generated/sources/copybook/main` | The directory for the generated Java files. |
| `include(...)` | `**/*.cpy` | The patterns of the copybook files in `sourceDir`. Your patterns replace the default. |
| `exclude(...)` | none | The patterns of the files in `sourceDir` that are not copybooks. |

**WARNING:** Do not set `outputDir` to a directory that contains other files, for example `src/main/java`. Each run deletes all files in `outputDir`, and you can lose your source files.

For the package rules, the options files, the class name collisions, the `COPY` members, and the build cache, see the [plugin README](https://github.com/ajlima/bookmap/blob/main/bookmap-copybook-gradle-plugin/README.md).

A Maven plugin is in progress. See the [Roadmap](Roadmap).

## Mapping

| COBOL | Java |
|---|---|
| Level `01` | A class with `@BookOperation`. |
| A group without `OCCURS` | No class. Its items go into the class of the parent. |
| `OCCURS n` | `@BookCollection(fixed = true)` and an item class. |
| `OCCURS m TO n DEPENDING ON X` | `@BookCollection(dependsOn = "x")` and an item class. |
| An elementary item with `OCCURS` | An item class with one field `value`. |
| An elementary item | A `@BookField` with `pic`, for example `pic = "S9(7)V99 COMP-3"`. |
| `FILLER` | A field `filler1`, `filler2`, ... |
| `REDEFINES` of an elementary item | The first definition. The other item is skipped, with a warning. |
| `REDEFINES` of a group | A direction split or subtypes. See the next sections. |
| Level `88`, `VALUE` | Ignored. |

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9(n)` with 1 to 9 digits | `int` |
| `9(n)` with 10 to 18 digits | `long` |
| Decimals, or more than 18 digits | `BigDecimal` |

Names: `GET-CUSTOMER-ORDERS` gives the class `GetCustomerOrders`, and `ORDER-AMOUNT` gives the field `orderAmount`.
A Java keyword gets the suffix `Value`. If two items in one class have the same name, the second name starts with its group name.
The `order` values are 10, 20, 30, ... in each class. Each field has a Javadoc comment with its COBOL declaration.

A fixed `OCCURS` has no `dependsOn`, because the copybook does not tell which field is the count.
Add `dependsOn` by hand if the program has a count field. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#fixed-collections).

## Directions

The copybook does not give the direction of a field. Thus, all fields are `INPUT_OUTPUT`, with one exception: the alternatives of a `REDEFINES` area.

In BookMap, an `INPUT` field does not occupy bytes in the output area.
Outside a `REDEFINES` area, a direction would move the offsets of the fields after it. The offsets would not agree with COBOL.
Inside a `REDEFINES` area, the input alternative and the output alternative share the same bytes. This is a **direction split**:

```cobol
01  GET-INVOICE.
    05  RETURN-CODE          PIC X(4).
    05  INVOICE-REQUEST.
        10  INVOICE-ID       PIC X(12).
    05  INVOICE-RESPONSE REDEFINES INVOICE-REQUEST.
        10  INVOICE-AMOUNT   PIC S9(7)V99 COMP-3.
        10  DUE-DATE         PIC 9(8).
    05  TRAILER              PIC X(10).
```

`invoiceId` is `INPUT`. `invoiceAmount` and `dueDate` are `OUTPUT`. `returnCode` and `trailer` are `INPUT_OUTPUT`.
The request alternative is 1 byte shorter. The generator adds a 1-byte `INPUT` filler, so `trailer` has the COBOL offset in both directions.

The generator finds the direction of an alternative in this order:

1. A direction decision (`--direction` or `directions`).
2. A marker in the group name. The default input markers are `INPUT`, `IN`, `REQUEST`, and `REQ`. The default output markers are `OUTPUT`, `OUT`, `RESPONSE`, and `RESP`.
3. A subtype decision makes subtypes instead (next section).

If none applies, the generator does not guess. It reports an error with an example of each decision.

## Subtypes

A subtype decision maps a `REDEFINES` area to [subtypes](Subtypes-REDEFINES). It gives the discriminator and the value of each alternative.
The class that holds the area gets `@BookSubTypes`. Each alternative becomes a subclass.

## Limitations of the generator

- The generator does not detect dates. A `PIC 9(8)` date stays an `int`.
- The generated operation classes do not implement `Continuable` or `Validatable`. In scaffold mode, add them by hand.
- A fixed `OCCURS` of `COMP` items without `dependsOn` cannot write the value 0: its bytes are LOW-VALUES. The generator gives a note.
- These items are errors: level `66` and `77`, `SIGN LEADING`, `SEPARATE`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL`, edited pictures, `P`, `A`, `N`, `REPLACE`, an `OCCURS` in an `OCCURS`, and a `REDEFINES` area in another one.
- No Maven plugin yet. Use the Gradle plugin, the command line, or the library.
