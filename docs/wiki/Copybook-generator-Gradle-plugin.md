The plugin `pt.dazz.bookmap.copybook` runs the [copybook generator](Copybook-generator) in a Gradle build.
It reads a directory tree of COBOL copybooks and writes the annotated Java classes before `compileJava`.
The directories of the tree give the Java subpackages.

## What the plugin does

- It adds the extension `bookmap` and the task `generateCopybookClasses`.
- The task scans `src/main/copybook` recursively for `.cpy` files.
- It generates the classes of each copybook into `build/generated/sources/copybook/main`.
- With the `java` plugin, it adds that directory to the `main` source set. Gradle then runs the task before `compileJava`.
- The plugin does not add dependencies to your project. Add `bookmap-core` and `bookmap-processor` yourself.

## Requirements

- Run the Gradle daemon on JDK 21 or later. The plugin is compiled for Java 21.
- The tests of the plugin use Gradle 9.9.1.
- The plugin puts these libraries on the build script classpath: `bookmap-copybook`, `bookmap-core`, JavaPoet, and Jackson. They are not isolated from other plugins.

## Setup

The plugin is on Maven Central only. It is not on the Gradle Plugin Portal.
Replace `<version>` with the latest version: ![Maven Central](https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-copybook-gradle-plugin)

1. Add Maven Central to the plugin repositories in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

2. Apply the plugin and add the BookMap libraries in `build.gradle.kts`. Use the same version for all BookMap artifacts.

```kotlin
plugins {
    java
    id("pt.dazz.bookmap.copybook") version "<version>"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}

bookmap {
    copybooks {
        packageName = "com.example.mainframe"
        copyDirs.from("src/main/copylib")
    }
}
```

3. Put the copybooks in `src/main/copybook`, and the `COPY` members in `src/main/copylib`.
4. Run the build:

```bash
./gradlew build
```

If your company uses a Maven mirror (for example, Nexus or Artifactory), put the mirror in `pluginManagement.repositories`.
The plugin marker artifact is `pt.dazz.bookmap.copybook:pt.dazz.bookmap.copybook.gradle.plugin`. The mirror must give it.

## Run the task

The `compileJava` task runs the generation. You can also run it alone:

```bash
./gradlew generateCopybookClasses
```

The generated files are in `build/generated/sources/copybook/main`. The plugin adds this directory to the `main` source set.
Run `generateCopybookClasses` before you open the generated classes in the IDE.

`./gradlew tasks` shows the task in the group "Build tasks", with the description `Generates the BookMap classes from the COBOL copybooks.`

The plugin adds the generated classes only to the `main` source set. It has no task for test copybooks. See [Copybooks for tests](#copybooks-for-tests).
Without the `java` plugin, no task depends on `generateCopybookClasses`. Then run it yourself.

## The DSL

```kotlin
bookmap {
    copybooks {
        sourceDir = layout.projectDirectory.dir("src/main/copybook") // the default
        copyDirs.from("src/main/copylib")                            // COPY members, in search order
        packageName = "com.example.mainframe"                        // required
        lombok = false                                               // the default
        packagePerCopybook = false                                   // the default
        maxRecordLength = 32500                                      // optional
        exclude("**/members/**")                                     // optional
    }
}
```

| Property | Default | Meaning |
|---|---|---|
| `sourceDir` | `src/main/copybook` | The root of the copybook tree. The plugin scans it recursively. |
| `copyDirs` | empty | The directories with the `COPY` members, in search order. See [COPY members](#copy-members). |
| `packageName` | none | The base package. It is required if a copybook exists and its options file does not set `package`. |
| `lombok` | `false` | Write Lombok annotations instead of getters and setters. |
| `packagePerCopybook` | `false` | Add one package segment from the copybook name. See [Class name collisions](#class-name-collisions). |
| `maxRecordLength` | none | The maximum record length in bytes. A longer record gives a warning. See [Record length](Copybook-generator#record-length). |
| `outputDir` | `build/generated/sources/copybook/main` | The directory for the generated Java files. |
| `include(...)` | `**/*.cpy` | The patterns of the copybook files in `sourceDir`. Your patterns replace the default. Each call adds patterns to the list `includes`. |
| `exclude(...)` | none | The patterns of the files in `sourceDir` that are not copybooks. Each call adds patterns to the list `excludes`. |

The patterns use the Ant style, and they are relative to `sourceDir`. You can also set the lists directly, for example `includes = listOf("**/*.cpy", "**/*.cob")`.

If `sourceDir` does not exist, or if it has no copybook, the task generates no class. It deletes the old files in `outputDir`.
If `packageName` is necessary and not set, the build fails with this message: `Set bookmap.copybooks.packageName.`

**WARNING:** Do not set `outputDir` to a directory that contains other files, for example `src/main/java`. Each run deletes all files in `outputDir`, and you can lose your source files.

## Packages from directories

The plugin gives each copybook a package in this order:

1. If the options file of the copybook sets `package`, the plugin uses that package exactly.
2. Otherwise, it uses `packageName` plus one segment for each directory between `sourceDir` and the copybook.
3. If `packagePerCopybook` is `true`, it adds one more segment from the copybook name. Rule 1 has priority over this rule.

The plugin changes each directory name, and the copybook name in rule 3, to a valid package segment:

1. It changes the name to lower case.
2. It replaces each character that is not an ASCII letter, an ASCII digit, or `_` with `_`.
3. It adds `_` before a name that starts with a digit.
4. It adds `_` after a Java keyword, and after `true`, `false`, and `null`.

If the final package is not a valid Java package name, the task logs an error for that copybook. It checks the other copybooks, and then the build fails. For example, `"package": "com.my-co"` in an options file gives this error: `ORDER.cpy: the package 'com.my-co' is not a valid Java package name`.

Examples, with `packageName = "com.example.mainframe"`:

| Copybook in `sourceDir` | Package |
|---|---|
| `ORDER.cpy` | `com.example.mainframe` |
| `orders/ORDER.cpy` | `com.example.mainframe.orders` |
| `Billing/Open-Invoices/INVOICE.cpy` | `com.example.mainframe.billing.open_invoices` |
| `2026/INVOICE.cpy` | `com.example.mainframe._2026` |
| `new/INVOICE.cpy` | `com.example.mainframe.new_` |
| `billing/LIST-OPEN-INVOICES.cpy`, with `packagePerCopybook = true` | `com.example.mainframe.billing.list_open_invoices` |

## Options files

Put the options of one copybook in a file `<NAME>.json` next to `<NAME>.cpy`. The file is optional.
The file has the format of the [options file](Copybook-generator#options-file).

```
src/main/copybook/billing/GET-INVOICE.cpy
src/main/copybook/billing/GET-INVOICE.json   { "stripPrefixes": ["INV-"] }
```

These rules apply:

- `package` in the file has priority over the package from the directories.
- `lombok` in the file has priority over the `lombok` property of the extension.
- `maxRecordLength` in the file has priority over the `maxRecordLength` property of the extension.
- `copyDirs` in the file come first in the search order. The `copyDirs` of the extension come after them.
- A relative directory in `copyDirs` of the file starts at the directory of the file.
- All other options come only from the file. Without a file, the plugin uses the defaults of the generator.

If the file cannot be read, is not valid JSON, has an unknown key, or has a path in `copyDirs` that is not valid, the build fails.
The task logs an error line that starts with the path of the options file, for example `/work/src/main/copybook/ORDER.json: cannot read the file: ...`.

Set the charset of a copybook with `charset` in its options file. The default is UTF-8. The `COPY` members of the copybook use the same charset and the same format.
Write the options file in UTF-8.

## Class name collisions

The generator writes one Java class for each level `01` record, each `OCCURS` group, and each subtype.
The generator makes the class names unique only in one copybook.
All copybooks in one directory go to one package. Thus, two copybooks in one directory can give the same class.

For example, `CUSTOMER-ORDER.cpy` and `CUSTOMER-RETURN.cpy` both contain `COPY ORDER-LINE.`, and the member has an `OCCURS` group.
Each copybook gives a class `OrderLine` in the same package. The build fails with this message:

```
Two copybooks generate the same class:
  class com.example.mainframe.OrderLine comes from CUSTOMER-ORDER.cpy and CUSTOMER-RETURN.cpy
Set bookmap.copybooks.packagePerCopybook = true, set "package" in an options file, or put the copybooks in different directories.
```

The message lists all collisions of the build, not only the first. Use one of these solutions:

1. **Set `packagePerCopybook = true`.** Each copybook gets its own package. This is the easiest solution for a tree with many copybooks in one directory.
2. **Set `package` in the options file** of one of the copybooks.
3. **Put the copybooks in different directories.**

| Copybook | `packagePerCopybook = false` | `packagePerCopybook = true` |
|---|---|---|
| `CUSTOMER-ORDER.cpy` | `com.example.mainframe` | `com.example.mainframe.customer_order` |
| `CUSTOMER-RETURN.cpy` | `com.example.mainframe` | `com.example.mainframe.customer_return` |
| `billing/GET-INVOICE.cpy` | `com.example.mainframe.billing` | `com.example.mainframe.billing.get_invoice` |

**CAUTION:** Do not change `packagePerCopybook` in a project that is in use without a plan. The option changes the package of each generated class, and all imports of these classes must change.

## COPY members

**The plugin does not search the tree for `COPY` members.** You must declare the directories that hold them.

- Declare the directories in `copyDirs`, in the extension or in an options file.
- The search order is: first the `copyDirs` of the options file of the copybook, then the `copyDirs` of the extension, in the order that you give.
- The search rules are the same for all tools. See [COPY members](Copybook-generator#copy-members).
- A change in a member makes the task run again, also in a directory that only an options file declares.

The recommended layout keeps the members out of `sourceDir`:

```
src/main/copybook/orders/GET-ORDER.cpy       a copybook: it gives classes
src/main/copybook/billing/GET-INVOICE.cpy    a copybook: it gives classes
src/main/copylib/ADDRESS.cpy                 a COPY member: it gives no classes
```

If you must keep the members inside `sourceDir`, exclude them:

```kotlin
bookmap {
    copybooks {
        packageName = "com.example.mainframe"
        copyDirs.from("src/main/copybook/members")
        exclude("members/**")
    }
}
```

**WARNING:** Do not put `COPY` members in `sourceDir` without an `exclude` pattern. The plugin tries to generate classes from each member, and the build can fail.

## Up-to-date checks, the build cache, and the configuration cache

- The task is up to date when its inputs do not change. The inputs are:
  - the copybooks and all `.json` files below `sourceDir`, with their paths relative to `sourceDir`. The `exclude` patterns do not apply to the `.json` files;
  - all files in the copy directories, of the extension and of the options files;
  - the order of the copy directories;
  - `include`, `exclude`, `packageName`, `lombok`, `packagePerCopybook`, and `maxRecordLength`.
- The inputs use relative paths. Thus, two checkouts in different directories share the entries of the build cache. One exception exists: a copy directory on another drive than `sourceDir` gives an absolute path.
- The task is cacheable. With `--build-cache`, a clean build can take the generated files from the cache.
- The task supports the configuration cache.
- Each run deletes the old files in `outputDir` first. A removed copybook removes its classes.

## Messages and build failures

The task writes each diagnostic of the generator to the build log, in the format `<file>:<line>:<column>: <severity>: <message>`.
The file is the name of the copybook or of the `COPY` member, without the directory.

- Errors have the level ERROR. Warnings have the level WARN. Infos have the level LIFECYCLE. Thus, an `info:` line also shows in the normal output, but it is not a warning.
- The task checks all copybooks before it fails. Then the build fails with this message: `N copybook(s) have errors. See the messages above.`
- The task checks for class name collisions only when no copybook has an error.
- A missing `packageName` stops the task at once, with `Set bookmap.copybooks.packageName.`
- If the task cannot write a file, the message is `Cannot write the generated classes: <detail>`.

The task deletes the old files in `outputDir` before it reads the copybooks. If a copybook has an error, the task writes no file.
Thus, after a failure, `outputDir` is empty, and `compileJava` cannot use old classes.

## Groovy DSL

The examples on this page use the Kotlin DSL. The tests of the plugin use only the Kotlin DSL. This Groovy DSL `build.gradle` uses the same API, but it is not tested:

```groovy
plugins {
    id 'java'
    id 'pt.dazz.bookmap.copybook' version '<version>'
}

dependencies {
    implementation 'pt.dazz.bookmap:bookmap-core:<version>'
    annotationProcessor 'pt.dazz.bookmap:bookmap-processor:<version>'
}

bookmap {
    copybooks {
        packageName = 'com.example.mainframe'
        copyDirs.from('src/main/copylib')
    }
}
```

## Copybooks for tests

The plugin generates only the classes of the `main` source set. For test copybooks, register a second task of the type `GenerateCopybookClasses`.
A task that you register yourself gets no default values from the extension. Thus, set all properties. This example is not tested:

```kotlin
import pt.dazz.bookmap.copybook.gradle.GenerateCopybookClasses

val generateTestCopybookClasses by tasks.registering(GenerateCopybookClasses::class) {
    sourceDir = layout.projectDirectory.dir("src/test/copybook")
    copyDirs.from("src/test/copylib")
    packageName = "com.example.mainframe.test"
    lombok = false
    packagePerCopybook = false
    outputDir = layout.buildDirectory.dir("generated/sources/copybook/test")
}

sourceSets.test { java.srcDir(generateTestCopybookClasses) }

dependencies {
    testAnnotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

## Example project

The directory [`bookmap-examples-gradle-plugin`](https://github.com/ajlima/bookmap/tree/main/bookmap-examples-gradle-plugin) is a standalone build that uses the plugin:

```
bookmap-examples-gradle-plugin/
  settings.gradle.kts
  build.gradle.kts
  src/main/copybook/orders/GET-ORDER.cpy       -> package ...orders: GetOrder, OrderLine
  src/main/copybook/billing/GET-INVOICE.cpy    -> package ...billing: GetInvoice
  src/main/copybook/billing/GET-INVOICE.json   { "stripPrefixes": ["INV-"] }
  src/main/copylib/ADDRESS.cpy                 the COPY member of GET-INVOICE.cpy
  src/test/java/...                            a test with the simulator transport
```

It takes the plugin and the libraries from the source of the repository. Run it from the repository root:

```bash
./gradlew -p bookmap-examples-gradle-plugin build
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `Plugin [id: 'pt.dazz.bookmap.copybook', ...] was not found` | Add `mavenCentral()` to `pluginManagement.repositories` in `settings.gradle.kts`. |
| `Set bookmap.copybooks.packageName.` | Set `packageName` in the extension, or `package` in each options file. |
| `copy member '...' not found in ...` | Add the directory of the member to `copyDirs`. The search is not recursive. |
| `copy member '...' not found: no copy directory is set` | Set `copyDirs` in the extension or in the options file. |
| `N copybook(s) have errors. See the messages above.` | Read the `error:` lines above the failure. Each line gives the file, the line, and the column. |
| `Cannot write the generated classes: ...` | Check that `outputDir` is a directory that you can write to. |
| `Two copybooks generate the same class` | See [Class name collisions](#class-name-collisions). |
| The build fails on a file that is not a copybook | Add an `exclude(...)` pattern for the file. |
| The generated classes do not compile | Add `bookmap-core` to `implementation`. If you set `lombok = true`, add Lombok to the build. |
