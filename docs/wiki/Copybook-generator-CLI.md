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
| `--help`, `-h` | Shows the usage. |

A `generate` input that ends with `.json` is a model. Each other input is a copybook.
`generate` writes the classes in package directories under `--output-dir`. It replaces the existing files with the same name. It does not delete other files.

## Options

| Option | Commands | Meaning |
|---|---|---|
| `--output <file>` | `parse` | The model JSON file to write. Mandatory for `parse`. |
| `--package <name>` | `generate` | The Java package of the classes. Mandatory, unless the options file sets `package`. |
| `--output-dir <dir>` | `generate` | The root directory for the Java files. Mandatory for `generate`. |
| `--lombok` | `generate` | Write Lombok annotations (`@Data`, `@NoArgsConstructor`) instead of getters and setters. |
| `--format fixed\|free` | both | The reference format. Default: `fixed` (columns 7 to 72). |
| `--charset <name>` | both | The charset of the copybook files. Default: UTF-8. |
| `--copy-dir <dir>` | both | A directory with `COPY` members. Repeat the option for more directories. |
| `--record-name <name>` | both | The record name for a copybook without level `01`. Default: the file name. |
| `--strip-prefix <prefix>` | both | A prefix to remove from names, for example `WS-`. Repeat the option for more prefixes. |
| `--input-marker <word>` | both | A name part that marks an input alternative. Repeat the option for more words. |
| `--output-marker <word>` | both | A name part that marks an output alternative. Repeat the option for more words. |
| `--direction <name>=<direction>` | both | The direction (`INPUT` or `OUTPUT`) of a `REDEFINES` alternative. Repeat the option for more alternatives. |
| `--config <file.json>` | both | An [options file](Copybook-generator#options-file) with all options. |

With a model input, the parse and mapping options (`--format` to `--direction`) are not permitted. The model already contains their result.

### Options file and command line together

The command line and the options file work together in this way:

- A single value on the command line (`--format`, `--charset`, `--record-name`, `--package`) has priority over the value in the file.
- A repeated option (`--copy-dir`, `--strip-prefix`, `--input-marker`, `--output-marker`) adds its values after the values of the file.
- `--direction` adds a decision. For the same alternative, the command line has priority.
- `--lombok` sets `lombok`. Without `--lombok`, the value of the file applies.
- Subtype decisions come only from the file.

A relative copy directory in the file starts at the directory of the file. A relative `--copy-dir` starts at the current directory.

## Output

On success, the command writes one line to the standard output:

```
wrote 2 classes to src/main/java
```

The command writes each diagnostic to the standard error, in the format `<file>:<line>:<column>: <severity>: <message>`:

```
GET-INVOICE.cpy:5:12: error: copy member 'ADDRESS' not found: no copy directory is set
```

The generator reports all problems of a copybook before it stops.

| Exit code | Meaning |
|---|---|
| `0` | Success. Warnings and info diagnostics are permitted. |
| `1` | A diagnostic with the severity `error`. The command wrote no Java file. |
| `2` | An incorrect command line, for example a missing option or an unknown option. |

## Examples

The examples use the copybooks of [`bookmap-examples-gradle-plugin`](https://github.com/ajlima/bookmap/tree/main/bookmap-examples-gradle-plugin/src/main).

### Generate the classes of one copybook

```bash
bookmap-copybook generate GET-ORDER.cpy --package com.example.orders --output-dir src/main/java
```

The command writes `src/main/java/com/example/orders/GetOrder.java` and `OrderLine.java`.
See the generated class in [Copybook generator](Copybook-generator#example).

### Use COPY members and an options file

`GET-INVOICE.cpy` contains `COPY ADDRESS.`. `ADDRESS.cpy` is in the directory `copylib`.
`GET-INVOICE.json` removes the prefix `INV-` from the names:

```json
{
  "stripPrefixes": ["INV-"]
}
```

```bash
bookmap-copybook generate GET-INVOICE.cpy \
  --package com.example.billing \
  --output-dir src/main/java \
  --copy-dir copylib \
  --config GET-INVOICE.json
```

The class `GetInvoice` gets the fields `invoiceId`, `amountDue`, `street`, `city`, and `countryCode`.

### Write Lombok classes

```bash
bookmap-copybook generate GET-INVOICE.cpy --package com.example.billing --output-dir src/main/java \
  --copy-dir copylib --lombok
```

The classes get `@Data` and `@NoArgsConstructor`, and no getters and setters.
Add Lombok to your build. See [Getting started](Getting-started#lombok).

### Set the direction of a REDEFINES alternative

`QUERY-INVOICE.cpy` has a group `INVOICE-QUERY` and a group `INVOICE-DETAIL REDEFINES INVOICE-QUERY`.
The names have no input or output marker. Thus, give the directions:

```bash
bookmap-copybook generate QUERY-INVOICE.cpy --package com.example.billing --output-dir src/main/java \
  --direction INVOICE-QUERY=INPUT --direction INVOICE-DETAIL=OUTPUT
```

See [Directions](Copybook-generator#directions).

### Keep the model and compare it

Write the model JSON. Keep it in version control:

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
