The command line `bookmap-copybook` runs the [copybook generator](Copybook-generator) from a terminal or a script.
Use it in scaffold mode, in a CI pipeline, or from a build tool that has no BookMap plugin.

## Requirements

- Java 21 or later.

## Installation

The command line is one executable JAR with all dependencies: `bookmap-copybook-cli-<version>-all.jar`.
Replace `<version>` with the latest version: ![Maven Central](https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-copybook-cli)

Get the JAR from one of these places:

- The assets of the [GitHub release](https://github.com/ajlima/bookmap/releases).
- Maven Central, with the coordinates `pt.dazz.bookmap:bookmap-copybook-cli:<version>` and the classifier `all`.

Download it from Maven Central:

```bash
curl -fLO https://repo1.maven.org/maven2/pt/dazz/bookmap/bookmap-copybook-cli/<version>/bookmap-copybook-cli-<version>-all.jar
```

Make a short command (optional, Linux and macOS):

```bash
alias bookmap-copybook='java -jar /opt/bookmap/bookmap-copybook-cli-<version>-all.jar'
```

The examples on this page use the name `bookmap-copybook`. Without the alias, use `java -jar bookmap-copybook-cli-<version>-all.jar`.

## Commands

```
bookmap-copybook parse <copybook> --output <model.json> [options]
bookmap-copybook generate <copybook | model.json> --package <name> --output-dir <dir> [--lombok] [options]
bookmap-copybook --help
```

| Command | Result |
|---|---|
| `parse <copybook> --output <model.json>` | Writes the model JSON. It writes no Java. |
| `generate <copybook> --package <name> --output-dir <dir>` | Writes the Java classes of a copybook. |
| `generate <model.json> --package <name> --output-dir <dir>` | Writes the Java classes of a model JSON file. |
| `--help`, `-h` | Shows the usage, with the exit code 0. The option works at any position. |

A `generate` input that ends with `.json` is a model. Each other input is a copybook.
`generate` writes the classes in package directories under `--output-dir`. It replaces the existing files with the same name. It does not delete other files.

## Options

| Option | Commands | Meaning |
|---|---|---|
| `--output <file>` | `parse` | The model JSON file to write. Mandatory for `parse`. |
| `--package <name>` | `generate` | The Java package of the classes. Mandatory, unless the options file sets `package`. |
| `--output-dir <dir>` | `generate` | The root directory for the Java files. Mandatory for `generate`. |
| `--lombok` | `generate` | Write Lombok annotations (`@Data`, `@NoArgsConstructor`) instead of getters and setters. |
| `--format fixed\|free` | both | The reference format. Default: `fixed`. See [Source format](Copybook-generator#source-format). |
| `--charset <name>` | both | The charset of the copybook files. Default: UTF-8. |
| `--copy-dir <dir>` | both | A directory with `COPY` members. Repeat the option for more directories. |
| `--record-name <name>` | both | The record name for a copybook without level `01`. Default: the file name. |
| `--strip-prefix <prefix>` | both | A prefix to remove from names, for example `WS-`. Repeat the option for more prefixes. |
| `--input-marker <word>` | both | A name part that marks an input alternative. Write it in upper case. Repeat the option for more words. |
| `--output-marker <word>` | both | A name part that marks an output alternative. Write it in upper case. Repeat the option for more words. |
| `--direction <name>=<direction>` | both | The direction (`INPUT`, `OUTPUT`, or `INPUT_OUTPUT`) of a `REDEFINES` alternative. The direction is not case-sensitive. Repeat the option for more alternatives. |
| `--count-field <table>=<field>` | both | The count field of a fixed table (`OCCURS n`). The generator writes `dependsOn` on the table. Repeat the option for more tables. See [Count field of a fixed table](Copybook-generator#count-field-of-a-fixed-table). |
| `--config <file.json>` | both | An [options file](Copybook-generator#options-file) with all options. |

With a model input, the parse and mapping options (`--format` to `--count-field`) are not permitted. The model already contains their result.
With a model input, the generator uses only `package` and `lombok` from the options file. It ignores the other keys.
If you give a single-value option two times, the last value applies.

### Options file and command line together

The command line and the options file work together in this way:

- A single value on the command line (`--format`, `--charset`, `--record-name`, `--package`) has priority over the value in the file.
- A repeated option (`--copy-dir`, `--strip-prefix`, `--input-marker`, `--output-marker`) adds its values after the values of the file. `--input-marker` and `--output-marker` add words to the default list. To replace the default list, use `inputMarkers` or `outputMarkers` in the options file.
- `--direction` adds a decision. For the same alternative, the command line has priority.
- `--count-field` adds a count field. For the same table, the command line has priority over `countFields` in the file.
- `--lombok` sets `lombok`. Without `--lombok`, the value of the file applies. The command line cannot set `lombok` to `false`.
- Subtype decisions come only from the file.

A relative copy directory in the file starts at the directory of the file. A relative `--copy-dir` starts at the current directory.

## Output

On success, the command writes one line to the standard output:

```
wrote 2 classes to src/main/java
wrote the model of GET-ORDER.cpy to model/GET-ORDER.json
```

The command writes each diagnostic to the standard error, in the format `<file>:<line>:<column>: <severity>: <message>`:

```
GET-INVOICE.cpy:5:12: error: copy member 'ADDRESS' not found: no copy directory is set
```

The generator reports all problems of a copybook before it stops.

| Exit code | Meaning |
|---|---|
| `0` | Success. Warnings and info diagnostics are permitted. |
| `1` | A diagnostic with the severity `error`, an incorrect model JSON, or a file that cannot be read or written. The command wrote no Java file and no model file. |
| `2` | An incorrect command line, or an incorrect options file. |

Examples of exit code 1:

```
missing.cpy:1:1: error: cannot read missing.cpy: java.nio.file.NoSuchFileException: missing.cpy
v2.json: error: unsupported formatVersion 2. This reader supports 1.
error: nodir/GET-ORDER.json
```

For exit code 2, the command writes `error: <message>` and then `Run bookmap-copybook --help for the options.` to the standard error:

| Message | Cause |
|---|---|
| `the command is missing` | No `parse` or `generate`. |
| `unknown command 'frob'. Use parse or generate.` | An unknown command. |
| `the input file is missing` | No copybook or model file. |
| `only one input file is permitted` | More than one input file. |
| `the option --x needs a value` | An option without its value. |
| `unknown option --bogus` | An unknown option. |
| `the option --lombok is only for generate` | `--lombok` with `parse`. |
| `the option --package is missing` | `generate` without `--package`, and no `package` in the options file. |
| `the option --output-dir is missing` | `generate` without `--output-dir`. |
| `the option --output is missing` | `parse` without `--output`. |
| `unknown format 'fre'. Use fixed or free.` | An incorrect `--format`. |
| `unknown charset 'x'` | An incorrect `--charset`. |
| `--direction needs NAME=DIRECTION, not 'X'` | `--direction` without `=`. |
| `unknown direction in 'X=both'. Use INPUT, OUTPUT, or INPUT_OUTPUT.` | An incorrect direction. |
| `--count-field needs TABLE=FIELD, not 'X'` | `--count-field` without `=`. |
| `parse and mapping options are not permitted with a model input` | A parse or mapping option with a model input. |
| `bad.json: unknown key 'foo' in $` | An unknown key in the options file. |

**CAUTION:** If almost each line gives `an entry must start with a level number`, check the format. For example, `--format free` on a fixed-format copybook gives this error.

## Examples

Most examples use the copybooks of [`bookmap-examples-gradle-plugin`](https://github.com/ajlima/bookmap/tree/main/bookmap-examples-gradle-plugin/src/main). The example of a `REDEFINES` direction shows its own copybook.

### Generate the classes of one copybook

```bash
bookmap-copybook generate GET-ORDER.cpy --package com.example.orders --output-dir src/main/java
```

The command writes `src/main/java/com/example/orders/GetOrder.java` and `OrderLine.java`.
See the generated class in [Copybook generator](Copybook-generator#example).

### See the errors

```
$ bookmap-copybook generate GET-INVOICE.cpy --package com.example.billing --output-dir out
GET-INVOICE.cpy:5:12: error: copy member 'ADDRESS' not found: no copy directory is set
$ echo $?
1
```

### Use COPY members and an options file

`GET-INVOICE.cpy` contains `COPY ADDRESS.`. `ADDRESS.cpy` is in the directory `copylib`.
`GET-INVOICE.json` removes the prefix `INV-` from the names. It also links the two fixed tables to their count fields:

```json
{
  "stripPrefixes": ["INV-"],
  "countFields": {
    "INVOICE-LINE": "INVOICE-LINE-COUNT",
    "TAX-SUMMARY-ENTRY": "TAX-SUMMARY-COUNT"
  }
}
```

```bash
bookmap-copybook generate GET-INVOICE.cpy \
  --package com.example.billing \
  --output-dir src/main/java \
  --copy-dir copylib \
  --config GET-INVOICE.json
```

The class `GetInvoice` gets the fields `invoiceId`, `amountDue`, `street`, `city`, `countryCode`, `invoiceLineCount`, `invoiceLine`, `taxSummaryCount`, and `taxSummaryEntry`.
The collections `invoiceLine` and `taxSummaryEntry` get `dependsOn`.

### Link a count field to a fixed table

Without `countFields`, the generator gives a suggestion for each `-COUNT` item just before a fixed table:

```
GET-INVOICE.cpy:9:12: info: INVOICE-LINE: INVOICE-LINE-COUNT can be the count field of this fixed table. To link them, set "countFields": {"INVOICE-LINE": "INVOICE-LINE-COUNT"} in the options file, or --count-field INVOICE-LINE=INVOICE-LINE-COUNT.
```

Give the count fields on the command line:

```bash
bookmap-copybook generate GET-INVOICE.cpy --package com.example.billing --output-dir src/main/java \
  --copy-dir copylib --strip-prefix INV- \
  --count-field INVOICE-LINE=INVOICE-LINE-COUNT --count-field TAX-SUMMARY-ENTRY=TAX-SUMMARY-COUNT
```

See [Count field of a fixed table](Copybook-generator#count-field-of-a-fixed-table).

### Write Lombok classes

```bash
bookmap-copybook generate GET-INVOICE.cpy --package com.example.billing --output-dir src/main/java \
  --copy-dir copylib --lombok
```

The classes get `@Data` and `@NoArgsConstructor`, and no getters and setters.
Add Lombok to your build. See [Lombok with Gradle](Getting-started#lombok-with-gradle) or [Lombok with Maven](Getting-started#lombok-with-maven).

### Set the direction of a REDEFINES alternative

This copybook is not in the examples. The names `INVOICE-QUERY` and `INVOICE-DETAIL` have no input or output marker:

```cobol
       01  GET-INVOICE.
           05  RETURN-CODE          PIC X(4).
           05  INVOICE-QUERY.
               10  INVOICE-ID       PIC X(12).
           05  INVOICE-DETAIL REDEFINES INVOICE-QUERY.
               10  INVOICE-AMOUNT   PIC S9(7)V99 COMP-3.
               10  DUE-DATE         PIC 9(8).
           05  TRAILER              PIC X(10).
```

Without a decision, the command stops with exit code 1 and the error `REDEFINES area INVOICE-QUERY at line 3 needs a decision. ...`. Give the directions:

```bash
bookmap-copybook generate QUERY-INVOICE.cpy --package com.example.billing --output-dir src/main/java \
  --direction INVOICE-QUERY=INPUT --direction INVOICE-DETAIL=OUTPUT
```

See [Directions](Copybook-generator#directions).

### Keep the model and compare it

Create the directory `model`. Then write the model JSON, and keep it in version control:

```bash
bookmap-copybook parse GET-ORDER.cpy --output model/GET-ORDER.json
```

When the copybook changes, write the model again and compare the two files:

```bash
git diff model/GET-ORDER.json
```

Generate the classes from the model:

```bash
bookmap-copybook generate model/GET-ORDER.json --package com.example.orders --output-dir src/main/java
```

### Generate all copybooks of a directory

```bash
for copybook in copybooks/*.cpy; do
  bookmap-copybook generate "$copybook" --package com.example.mainframe \
    --output-dir src/main/java --copy-dir copylib || exit 1
done
```

**CAUTION:** Do not put two copybooks that give the same class name in one package. The second command replaces the class of the first.
The build plugins detect this collision. See [Copybook generator Gradle plugin](Copybook-generator-Gradle-plugin#class-name-collisions).

### Run the generator in a Gradle build without the plugin

The module `bookmap-examples` runs the command line with a `JavaExec` task for each copybook.
Use the [Gradle plugin](Copybook-generator-Gradle-plugin) instead, if possible.

```kotlin
val copybookGenerator: Configuration by configurations.creating

dependencies {
    copybookGenerator("pt.dazz.bookmap:bookmap-copybook-cli:<version>")
}

val generateGetOrder = tasks.register<JavaExec>("generateGetOrder") {
    val outputDir = layout.buildDirectory.dir("generated/sources/copybook/GET-ORDER")
    classpath = copybookGenerator
    mainClass = "pt.dazz.bookmap.copybook.cli.Main"
    inputs.file("src/main/copybook/GET-ORDER.cpy")
    outputs.dir(outputDir)
    doFirst { delete(outputDir) }
    args = listOf("generate", "src/main/copybook/GET-ORDER.cpy",
        "--package", "com.example.orders",
        "--output-dir", outputDir.get().asFile.path)
}

sourceSets.main { java.srcDir(generateGetOrder) }
```

The full build file is [`bookmap-examples/build.gradle.kts`](https://github.com/ajlima/bookmap/blob/main/bookmap-examples/build.gradle.kts).

## Java API

The library `bookmap-copybook` has the same steps as a Java API. The entry point is `pt.dazz.bookmap.copybook.Copybooks`.

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-copybook:<version>")
}
```

| Method | Result |
|---|---|
| `Copybooks.toModel(Path copybook, ParseOptions parse, MappingOptions mapping)` | The model and the diagnostics. No Java. |
| `Copybooks.generate(Path copybook, ParseOptions parse, MappingOptions mapping, GenerateOptions generate)` | The model, the Java files, and the diagnostics. |
| `Copybooks.generate(BookModel model, GenerateOptions generate)` | The Java files of a model. |

Each method returns a `Copybooks.Result` with `model()`, `files()`, and `diagnostics()`. `hasErrors()` tells if an error exists.
`ModelJson.write(BookModel)` and `ModelJson.read(String)` convert a model to JSON and back.
`JavaClassGenerator.writeTo(files, outputDir)` writes the Java files.

```java
Copybooks.Result result = Copybooks.generate(
    Path.of("GET-ORDER.cpy"),
    ParseOptions.defaults(),
    MappingOptions.defaults(),
    new GenerateOptions("com.example.orders", false));   // package, lombok

result.diagnostics().forEach(diagnostic -> System.err.println(diagnostic.format()));
if (!result.hasErrors()) {
    JavaClassGenerator.writeTo(result.files(), Path.of("src/main/java"));
}
```
