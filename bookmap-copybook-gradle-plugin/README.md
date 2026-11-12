# BookMap copybook Gradle plugin

The plugin `pt.dazz.bookmap.copybook` runs the BookMap copybook generator in a Gradle build.
It reads a directory tree of COBOL copybooks and writes the annotated Java classes before `compileJava`.
The directories of the tree give the Java subpackages.

The plugin is a thin adapter. The `bookmap-copybook` library does the parse, the mapping, and the generation.
For the mapping rules, the options, and the limitations, see the section "Copybook generator" in the [main README](../README.md#copybook-generator).

## 1. What the plugin does

- It adds the extension `bookmap` and the task `generateCopybookClasses`.
- The task scans `src/main/copybook` recursively for `.cpy` files.
- It generates the classes of each copybook into `build/generated/sources/copybook/main`.
- With the `java` plugin, it adds that directory to the `main` source set. Gradle then runs the task before `compileJava`.
- The plugin does not add dependencies to your project. Add `bookmap-core` and `bookmap-processor` yourself.

## 2. Setup

The plugin is on Maven Central only. It is not on the Gradle Plugin Portal.
Add Maven Central to the plugin repositories in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Apply the plugin and add the BookMap libraries in `build.gradle.kts`. Use the same version for all BookMap artifacts.

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

If your company uses a Maven mirror (for example, Nexus or Artifactory), put the mirror in `pluginManagement.repositories`.
The plugin marker artifact is `pt.dazz.bookmap.copybook:pt.dazz.bookmap.copybook.gradle.plugin`. The mirror must give it.

## 3. The DSL

```kotlin
bookmap {
    copybooks {
        sourceDir = layout.projectDirectory.dir("src/main/copybook") // the default
        copyDirs.from("src/main/copylib")                            // COPY members, in search order
        packageName = "com.example.mainframe"                        // required
        lombok = false                                               // the default
        packagePerCopybook = false                                   // the default
        exclude("**/members/**")                                     // optional
    }
}
```

| Property | Default | Meaning |
|---|---|---|
| `sourceDir` | `src/main/copybook` | The root of the copybook tree. The plugin scans it recursively. |
| `copyDirs` | empty | The directories with the `COPY` members, in search order. See [section 7](#7-copy-members-where-to-put-them). |
| `packageName` | none | The base package. It is required if a copybook exists and its options file does not set `package`. |
| `lombok` | `false` | Write Lombok annotations instead of getters and setters. |
| `packagePerCopybook` | `false` | Add one package segment from the copybook name. See [section 6](#6-class-name-collisions-and-packagepercopybook). |
| `outputDir` | `build/generated/sources/copybook/main` | The directory for the generated Java files. |
| `include(...)` | `**/*.cpy` | The patterns of the copybook files in `sourceDir`. Your patterns replace the default. |
| `exclude(...)` | none | The patterns of the files in `sourceDir` that are not copybooks. |

If `sourceDir` does not exist, or if it has no copybook, the task does nothing. It also deletes the old generated files.
If `packageName` is necessary and not set, the build fails with this message: `Set bookmap.copybooks.packageName.`

## 4. Packages from directories

The plugin gives each copybook a package in this order:

1. If the options file of the copybook sets `package`, the plugin uses that package exactly.
2. Otherwise, it uses `packageName` plus one segment for each directory between `sourceDir` and the copybook.
3. If `packagePerCopybook` is `true`, it adds one more segment from the copybook name. Rule 1 has priority over this rule.

The plugin changes each directory name, and the copybook name in rule 3, to a valid package segment:

1. It changes the name to lower case.
2. It replaces each character that is not an ASCII letter, an ASCII digit, or `_` with `_`.
3. It adds `_` before a name that starts with a digit.
4. It adds `_` after a Java keyword, and after `true`, `false`, and `null`.

Examples, with `packageName = "com.example.mainframe"`:

| Copybook in `sourceDir` | Package |
|---|---|
| `ORDER.cpy` | `com.example.mainframe` |
| `orders/ORDER.cpy` | `com.example.mainframe.orders` |
| `Billing/Open-Invoices/INVOICE.cpy` | `com.example.mainframe.billing.open_invoices` |
| `2026/INVOICE.cpy` | `com.example.mainframe._2026` |
| `new/INVOICE.cpy` | `com.example.mainframe.new_` |
| `billing/LIST-OPEN-INVOICES.cpy`, with `packagePerCopybook = true` | `com.example.mainframe.billing.list_open_invoices` |

## 5. Options files

Put the options of one copybook in a file `<NAME>.json` next to `<NAME>.cpy`. The file is optional.
The file has the format of the `--config` file of the command line. See "Command line" in the [main README](../README.md#command-line).

```
src/main/copybook/billing/GET-INVOICE.cpy
src/main/copybook/billing/GET-INVOICE.json   { "stripPrefixes": ["INV-"] }
```

These rules apply:

- `package` in the file has priority over the package from the directories.
- `lombok` in the file has priority over the `lombok` property of the extension.
- `copyDirs` in the file come first in the search order. The `copyDirs` of the extension come after them.
- A relative directory in `copyDirs` of the file starts at the directory of the file.
- All other options come only from the file. Without a file, the plugin uses the defaults of the generator.

If the file is not valid JSON, or if it has an unknown key, the build fails. The message starts with the path of the file.

## 6. Class name collisions and `packagePerCopybook`

The generator writes one Java class for each level `01` record, each `OCCURS` group, and each subtype.
A group without `OCCURS` gives no class: its fields go into the class of the parent.
The generator makes the class names unique only in one copybook.

All copybooks in one directory go to one package. Thus, two copybooks in one directory can give the same class.
For example, `CUSTOMER-ORDER.cpy` and `CUSTOMER-RETURN.cpy` both contain `COPY ORDER-LINE.`, and the member has an `OCCURS` group:

```cobol
      * ORDER-LINE: A COPY MEMBER.
           05  ORDER-LINE OCCURS 5 TIMES.
               10  PRODUCT-CODE         PIC X(12).
               10  QUANTITY             PIC 9(5).
```

Each copybook gives a class `OrderLine` in the same package. The build fails with this message:

```
Two copybooks generate the same class:
  class com.example.mainframe.OrderLine comes from CUSTOMER-ORDER.cpy and CUSTOMER-RETURN.cpy
Set bookmap.copybooks.packagePerCopybook = true, set "package" in an options file, or put the copybooks in different directories.
```

The message lists all collisions of the build, not only the first.
Use one of these solutions:

1. **Set `packagePerCopybook = true`.** Each copybook gets its own package. This is the easiest solution for a tree with many copybooks in one directory.
2. **Set `package` in the options file** of one of the copybooks.
3. **Put the copybooks in different directories.**

The packages before and after `packagePerCopybook = true`:

| Copybook | `packagePerCopybook = false` | `packagePerCopybook = true` |
|---|---|---|
| `CUSTOMER-ORDER.cpy` | `com.example.mainframe` | `com.example.mainframe.customer_order` |
| `CUSTOMER-RETURN.cpy` | `com.example.mainframe` | `com.example.mainframe.customer_return` |
| `billing/GET-INVOICE.cpy` | `com.example.mainframe.billing` | `com.example.mainframe.billing.get_invoice` |

**CAUTION:** Do not change `packagePerCopybook` in a project that is in use without a plan. The option changes the package of each generated class, and all imports of these classes must change.

## 7. `COPY` members: where to put them

**The plugin does not search the tree for `COPY` members.** You must declare the directories that hold them.

- Declare the directories in `copyDirs`, in the extension or in an options file.
- The search order is: first the `copyDirs` of the options file of the copybook, then the `copyDirs` of the extension, in the order that you give.
- The search in each directory is not recursive. A member in a subdirectory of a copy directory is not found.
- For `COPY ADDRESS.`, the generator looks for the file `ADDRESS`, then `ADDRESS.cpy`, then `ADDRESS.cbl`, then `ADDRESS.cob`, in each directory.
- The first match wins. Thus, the order of the directories is important if two directories have a member with the same name.
- If no directory has the member, the build fails. The message gives the member and the searched directories, for example: `copy member 'ADDRESS' not found in /work/orders/src/main/copylib`.
- A change in a member makes the task run again, also in a directory that only an options file declares.

The recommended layout keeps the members out of `sourceDir`:

```
src/main/copybook/orders/GET-ORDER.cpy       a copybook: it gives classes
src/main/copybook/billing/GET-INVOICE.cpy    a copybook: it gives classes
src/main/copylib/ADDRESS.cpy                 a COPY member: it gives no classes
```

```kotlin
bookmap {
    copybooks {
        packageName = "com.example.mainframe"
        copyDirs.from("src/main/copylib")
    }
}
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

## 8. Up-to-date checks, the build cache, and the configuration cache

- The task is up to date when its inputs do not change. The inputs are the copybooks, the options files, the copy directories (of the extension and of the options files), and the properties of the extension.
- The inputs use relative paths. Thus, two checkouts in different directories share the entries of the build cache.
- The task is cacheable. With `--build-cache`, a clean build can take the generated files from the cache.
- The task supports the configuration cache.
- Each run deletes the old files in `outputDir` first. A removed copybook removes its classes.

## 9. Requirements

- Run the Gradle daemon on JDK 21 or later. The plugin is compiled for Java 21.
- The tests of the plugin use Gradle 9.9.1.
- The plugin puts these libraries on the build script classpath: `bookmap-copybook`, `bookmap-core`, JavaPoet, and Jackson. They are not isolated from other plugins.

## 10. Example

The directory [`bookmap-examples-gradle-plugin`](../bookmap-examples-gradle-plugin) is a standalone build that uses the plugin.
It has copybooks in two subpackages, an options file, a `COPY` member in `src/main/copylib`, and a test with the simulator transport.
It takes the plugin and the libraries from the source of this repository. Run it from the repository root:

```bash
./gradlew -p bookmap-examples-gradle-plugin build
```
