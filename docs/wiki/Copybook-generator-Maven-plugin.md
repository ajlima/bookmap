The plugin `pt.dazz.bookmap:bookmap-copybook-maven-plugin` runs the [copybook generator](Copybook-generator) in a Maven build.
It reads a directory tree of COBOL copybooks and writes the annotated Java classes in the phase `generate-sources`.
The directories of the tree give the Java subpackages.

The plugin has the same rules as the [Gradle plugin](Copybook-generator-Gradle-plugin). Both plugins use the same code in `bookmap-copybook`.

## What the plugin does

- The goal `generate` scans `src/main/copybook` recursively for `.cpy` files.
- It generates the classes of each copybook into `target/generated-sources/copybook`.
- It adds that directory to the compile source roots. The compiler then compiles the classes with the other sources of the module.
- It skips the generation when no input changed. See [Up-to-date check and skip](#up-to-date-check-and-skip).
- The plugin does not add dependencies to your project. Add `bookmap-core` and `bookmap-processor` yourself.

## Requirements

- Maven 3.6.3 or later. The tests use Maven 3.6.3 and 3.9.16. Maven 4 is not tested.
- Run Maven on JDK 21 or later. The plugin and BookMap are compiled for Java 21.

## Setup

The plugin is on Maven Central. A Maven mirror (for example, Nexus or Artifactory) that gives Maven Central also gives the plugin.
Replace `<version>` with the latest version: ![Maven Central](https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-copybook-maven-plugin)

1. Add `bookmap-core`, the processor, and the plugin to `pom.xml`:

```xml
<properties>
  <bookmap.version><version></bookmap.version>
</properties>

<dependencies>
  <dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-core</artifactId>
    <version>${bookmap.version}</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.13.0</version>
      <configuration>
        <release>21</release>
        <annotationProcessorPaths>
          <path>
            <groupId>pt.dazz.bookmap</groupId>
            <artifactId>bookmap-processor</artifactId>
            <version>${bookmap.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
    <plugin>
      <groupId>pt.dazz.bookmap</groupId>
      <artifactId>bookmap-copybook-maven-plugin</artifactId>
      <version>${bookmap.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <packageName>com.example.mainframe</packageName>
        <copyDirs>
          <copyDir>src/main/copylib</copyDir>
        </copyDirs>
      </configuration>
    </plugin>
  </plugins>
</build>
```

2. Put the copybooks in `src/main/copybook`, and the `COPY` members in `src/main/copylib`.
3. Run the build:

```bash
mvn verify
```

If you use Lombok, add Lombok to `annotationProcessorPaths` before `bookmap-processor`.

## Run the goal

The phase `generate-sources` runs the goal. Thus, `mvn compile` and each later phase run it. Run only the generation:

```bash
mvn generate-sources
```

You can also run the goal from the command line with the full coordinates:

```bash
mvn pt.dazz.bookmap:bookmap-copybook-maven-plugin:<version>:generate
```

The short form `mvn bookmap-copybook:generate` works only if `pt.dazz.bookmap` is in the `<pluginGroups>` of your `settings.xml`:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>pt.dazz.bookmap</pluginGroup>
  </pluginGroups>
</settings>
```

The generated files are in `target/generated-sources/copybook`. Your IDE adds this directory as a source directory when it imports the Maven project.

## The parameters

| Parameter | User property | Default | Meaning |
|---|---|---|---|
| `sourceDir` | `bookmap.copybook.sourceDir` | `${project.basedir}/src/main/copybook` | The root of the copybook tree. The goal scans it recursively. |
| `copyDirs` | none | empty | The directories with the `COPY` members, in search order. See [COPY members](#copy-members). |
| `packageName` | `bookmap.copybook.packageName` | none | The base package. It is required if a copybook exists and its options file does not set `package`. |
| `lombok` | `bookmap.copybook.lombok` | `false` | Write Lombok annotations instead of getters and setters. |
| `packagePerCopybook` | `bookmap.copybook.packagePerCopybook` | `false` | Add one package segment from the copybook name. See [Class name collisions](#class-name-collisions). |
| `outputDir` | `bookmap.copybook.outputDir` | `${project.build.directory}/generated-sources/copybook` | The directory for the generated Java files. |
| `includes` | none | `**/*.cpy` | The patterns of the copybook files in `sourceDir`. Your patterns replace the default. |
| `excludes` | none | none | The patterns of the files in `sourceDir` that are not copybooks. |
| `skip` | `bookmap.copybook.skip` | `false` | Do not generate the classes. |

Relative paths in `sourceDir`, `copyDirs`, and `outputDir` start at the directory of the module (`${project.basedir}`).
The patterns use the Ant style, relative to `sourceDir`. For example:

```xml
<excludes>
  <exclude>members/**</exclude>
</excludes>
```

Set a parameter with its user property on the command line:

```bash
mvn generate-sources -Dbookmap.copybook.packagePerCopybook=true
```

If `packageName` is necessary and not set, the build fails with this message: `Set the packageName parameter of bookmap-copybook-maven-plugin.`

**WARNING:** Do not set `outputDir` to a directory that contains other files, for example `src/main/java`. Each run deletes all files in `outputDir`, and you can lose your source files.

## Packages from directories

The goal gives each copybook a package in this order:

1. If the options file of the copybook sets `package`, the goal uses that package exactly.
2. Otherwise, it uses `packageName` plus one segment for each directory between `sourceDir` and the copybook.
3. If `packagePerCopybook` is `true`, it adds one more segment from the copybook name. Rule 1 has priority over this rule.

The goal changes each name to a valid package segment: lower case, `_` for each character that is not an ASCII letter or digit, `_` before a leading digit, and `_` after a Java keyword.
The rules are the same as in the Gradle plugin. See [Packages from directories](Copybook-generator-Gradle-plugin#packages-from-directories) for all examples.

| Copybook in `sourceDir` | Package, with `packageName` = `com.example.mainframe` |
|---|---|
| `ORDER.cpy` | `com.example.mainframe` |
| `orders/ORDER.cpy` | `com.example.mainframe.orders` |
| `Billing/Open-Invoices/INVOICE.cpy` | `com.example.mainframe.billing.open_invoices` |

If the final package is not a valid Java package name, the build fails for that copybook.

## Options files

Put the options of one copybook in a file `<NAME>.json` next to `<NAME>.cpy`. The file is optional.
The file has the format of the [options file](Copybook-generator#options-file).

- `package` in the file has priority over the package from the directories.
- `lombok` in the file has priority over the `lombok` parameter.
- `copyDirs` in the file come first in the search order. The `copyDirs` parameter comes after them.
- A relative directory in `copyDirs` of the file starts at the directory of the file.
- All other options come only from the file.

If the file cannot be read, is not valid JSON, has an unknown key, or has a path in `copyDirs` that is not valid, the build fails. The message starts with the path of the file.

## Class name collisions

All copybooks in one directory go to one package. Thus, two copybooks in one directory can give the same class.
For example, `CUSTOMER-ORDER.cpy` and `CUSTOMER-RETURN.cpy` both contain `COPY ORDER-LINE.`, and the member has an `OCCURS` group.
Each copybook gives a class `OrderLine` in the same package. The build fails with this message:

```
Two copybooks generate the same class:
  class com.example.mainframe.OrderLine comes from CUSTOMER-ORDER.cpy and CUSTOMER-RETURN.cpy
Set <packagePerCopybook>true</packagePerCopybook>, set "package" in an options file, or put the copybooks in different directories.
```

The message lists all collisions of the build, not only the first. Use one of these solutions:

1. **Set `<packagePerCopybook>true</packagePerCopybook>`.** Each copybook gets its own package: `CUSTOMER-ORDER.cpy` goes to `com.example.mainframe.customer_order`.
2. **Set `package` in the options file** of one of the copybooks.
3. **Put the copybooks in different directories.**

**CAUTION:** Do not change `packagePerCopybook` in a project that is in use without a plan. The option changes the package of each generated class, and all imports of these classes must change.

## COPY members

**The goal does not search the tree for `COPY` members.** You must declare the directories that hold them.

- Declare the directories in the `copyDirs` parameter, or in an options file.
- The search order is: first the `copyDirs` of the options file of the copybook, then the `copyDirs` parameter, in the order that you give.
- The search rules are the same for all tools. See [COPY members](Copybook-generator#copy-members).

The recommended layout keeps the members out of `sourceDir`:

```
src/main/copybook/orders/GET-ORDER.cpy       a copybook: it gives classes
src/main/copybook/billing/GET-INVOICE.cpy    a copybook: it gives classes
src/main/copylib/ADDRESS.cpy                 a COPY member: it gives no classes
```

If you must keep the members inside `sourceDir`, exclude them:

```xml
<configuration>
  <packageName>com.example.mainframe</packageName>
  <copyDirs>
    <copyDir>src/main/copybook/members</copyDir>
  </copyDirs>
  <excludes>
    <exclude>members/**</exclude>
  </excludes>
</configuration>
```

**WARNING:** Do not put `COPY` members in `sourceDir` without an exclude pattern. The goal tries to generate classes from each member, and the build can fail.

## Up-to-date check and skip

Maven has no input and output tracking. The goal does its own check:

- After a successful run, the goal stores a SHA-256 fingerprint of all inputs in `target/bookmap-copybook/`. Each `outputDir` has its own fingerprint file.
- The inputs are the copybooks, the options files, the files in the copy directories (of the parameter and of the options files), the order of the copy directories, the parameters, and the plugin version.
- In the next build, if the fingerprint is the same and `outputDir` exists, the goal logs `The generated classes are up to date.` and does not generate again.
- A changed, added, or removed file, or a changed parameter, makes the goal generate again. Each run deletes the old files in `outputDir` first, so a removed copybook removes its classes.
- If the goal fails, it stores no fingerprint. The next build runs the goal again.
- `mvn clean` removes the fingerprint and the generated files.

**WARNING:** Give each execution of the goal in one module its own `outputDir`. Each run deletes all files in its `outputDir`, so two executions with the same `outputDir` remove the classes of each other.

With `skip` (`-Dbookmap.copybook.skip=true`), the goal does not generate. It still adds `outputDir` to the source roots, so the classes of an earlier run stay available to the compiler.

## Multi-module projects

Put the copybooks in one module. That module runs the plugin and the processor, and its JAR contains the generated classes and their codecs.
The other modules depend on that JAR and do not run the plugin.

```
parent/
  pom.xml                  pluginManagement for bookmap-copybook-maven-plugin
  mainframe-model/         the copybooks; runs the plugin and bookmap-processor
    src/main/copybook/...
    src/main/copylib/...
  orders-service/          depends on mainframe-model
  billing-service/         depends on mainframe-model
```

In a corporate parent POM, declare the plugin one time in `<pluginManagement>`:

```xml
<pluginManagement>
  <plugins>
    <plugin>
      <groupId>pt.dazz.bookmap</groupId>
      <artifactId>bookmap-copybook-maven-plugin</artifactId>
      <version>${bookmap.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</pluginManagement>
```

Then the module with the copybooks declares only the plugin and its configuration:

```xml
<plugin>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-copybook-maven-plugin</artifactId>
  <configuration>
    <packageName>com.example.mainframe</packageName>
    <copyDirs>
      <copyDir>src/main/copylib</copyDir>
    </copyDirs>
  </configuration>
</plugin>
```

A module that uses the classes declares a dependency on the model module, and the simulator for its tests:

```xml
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>mainframe-model</artifactId>
    <version>${project.version}</version>
  </dependency>
  <dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-transport-simulator</artifactId>
    <version>${bookmap.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Example project

The directory [`bookmap-examples-maven-plugin`](https://github.com/ajlima/bookmap/tree/main/bookmap-examples-maven-plugin) is a standalone multi-module Maven build that uses the plugin:

```
bookmap-examples-maven-plugin/
  pom.xml                                            the parent: pluginManagement
  mainframe-model/
    src/main/copybook/orders/GET-ORDER.cpy           -> package ...orders
    src/main/copybook/billing/GET-INVOICE.cpy        -> package ...billing
    src/main/copybook/billing/GET-INVOICE.json       { "stripPrefixes": ["INV-"] }
    src/main/copylib/ADDRESS.cpy                     the COPY member
  orders-service/
    src/test/java/.../MavenPluginEndToEndTest.java   a test with the simulator transport
```

It uses the snapshot version of the repository and the Maven Wrapper. Run it from the repository root:

```bash
./gradlew publishToMavenLocal
```

```bash
bookmap-examples-maven-plugin/mvnw -B -f bookmap-examples-maven-plugin/pom.xml verify
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `Set the packageName parameter of bookmap-copybook-maven-plugin.` | Set `packageName` in the configuration, or `package` in each options file. |
| `copy member '...' not found in ...` | Add the directory of the member to `copyDirs`. The search is not recursive. |
| `Two copybooks generate the same class` | See [Class name collisions](#class-name-collisions). |
| `No plugin found for prefix 'bookmap-copybook'` | Add `pt.dazz.bookmap` to `<pluginGroups>`, or use the full coordinates. |
| The generated classes have no codec at runtime | Add `bookmap-processor` to `annotationProcessorPaths` in the module that runs the plugin. |
| A change in a copybook has no effect | Check that the file is in `sourceDir` and matches `includes`. Run `mvn clean generate-sources` to force a new generation. |
