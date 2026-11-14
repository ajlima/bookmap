# BookMap copybook Maven plugin

The plugin `pt.dazz.bookmap:bookmap-copybook-maven-plugin` runs the BookMap copybook generator in a Maven build.
It reads a directory tree of COBOL copybooks and writes the annotated Java classes in the phase `generate-sources`.
The directories of the tree give the Java subpackages.

The plugin has the same rules as the [Gradle plugin](../bookmap-copybook-gradle-plugin/README.md). Both plugins use the same code in `bookmap-copybook`.
For the mapping rules, the options, and the limitations of the generator, see the page "Copybook generator" in the [wiki](https://github.com/ajlima/bookmap/wiki/Copybook-generator).

## 1. What the plugin does

- The goal `generate` scans `src/main/copybook` recursively for `.cpy` files.
- It generates the classes of each copybook into `target/generated-sources/copybook`.
- It adds that directory to the main compile source roots. The compiler then compiles the classes with the other sources of the module. It has no option for test sources.
- It skips the generation when no input changed (see [section 8](#8-up-to-date-check-and-skip)).
- The plugin does not add dependencies to your project. Add `bookmap-core` and `bookmap-processor` yourself.

## 2. Setup

The plugin is on Maven Central. A Maven mirror (for example, Nexus or Artifactory) that gives Maven Central also gives the plugin.

Requirements:

- Maven 3.6.3 or later. The tests use Maven 3.6.3 and 3.9.16. Maven 4 is not tested.
- Run Maven on JDK 21 or later. The plugin and BookMap are compiled for Java 21.

```xml
<dependencies>
  <dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-core</artifactId>
    <version>0.24.0</version>
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
            <version>0.24.0</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
    <plugin>
      <groupId>pt.dazz.bookmap</groupId>
      <artifactId>bookmap-copybook-maven-plugin</artifactId>
      <version>0.24.0</version>
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

You can also run the goal from the command line with the full coordinates:

```bash
mvn pt.dazz.bookmap:bookmap-copybook-maven-plugin:0.24.0:generate
```

The short form `mvn bookmap-copybook:generate` works only if `pt.dazz.bookmap` is in the `<pluginGroups>` of your `settings.xml`.

## 3. The parameters

| Parameter | User property | Default | Meaning |
|---|---|---|---|
| `sourceDir` | `bookmap.copybook.sourceDir` | `${project.basedir}/src/main/copybook` | The root of the copybook tree. The goal scans it recursively. |
| `copyDirs` | none | empty | The directories with the `COPY` members, in search order. See [section 7](#7-copy-members-where-to-put-them). |
| `packageName` | `bookmap.copybook.packageName` | none | The base package. It is required if a copybook exists and its options file does not set `package`. |
| `lombok` | `bookmap.copybook.lombok` | `false` | Write Lombok annotations instead of getters and setters. |
| `packagePerCopybook` | `bookmap.copybook.packagePerCopybook` | `false` | Add one package segment from the copybook name. See [section 6](#6-class-name-collisions-and-packagepercopybook). |
| `outputDir` | `bookmap.copybook.outputDir` | `${project.build.directory}/generated-sources/copybook` | The directory for the generated Java files. |
| `includes` | none | `**/*.cpy` | The patterns of the copybook files in `sourceDir`. Your patterns replace the default. An empty list uses the default. |
| `excludes` | none | none | The patterns of the files in `sourceDir` that are not copybooks. |
| `skip` | `bookmap.copybook.skip` | `false` | Do not generate the classes. |

Relative paths in `sourceDir`, `copyDirs`, and `outputDir` start at the directory of the module (`${project.basedir}`).
The patterns use the Ant style, relative to `sourceDir`. For example:

```xml
<excludes>
  <exclude>members/**</exclude>
</excludes>
```

If `packageName` is necessary and not set, the build fails with this message: `Set the packageName parameter of bookmap-copybook-maven-plugin.`
A user property has an effect only when the POM does not set the parameter.

The goal writes each diagnostic of the generator to the Maven log as `[ERROR]` or `[WARNING]`. An `info:` diagnostic also shows as `[WARNING]`.
The goal checks all copybooks before it fails. Then the build fails with `N copybook(s) have errors. See the messages above.`
If a copybook has an error, the goal writes no file. Thus, after a failure, `outputDir` is empty.

**WARNING:** Do not set `outputDir` to a directory that contains other files, for example `src/main/java`. Each run deletes all files in `outputDir`, and you can lose your source files.

## 4. Packages from directories

The goal gives each copybook a package in this order:

1. If the options file of the copybook sets `package`, the goal uses that package exactly.
2. Otherwise, it uses `packageName` plus one segment for each directory between `sourceDir` and the copybook.
3. If `packagePerCopybook` is `true`, it adds one more segment from the copybook name. Rule 1 has priority over this rule.

The goal changes each name to a valid package segment: lower case, `_` for each character that is not an ASCII letter, an ASCII digit, or `_`, `_` before a leading digit, and `_` after a Java keyword, `true`, `false`, or `null`.
For the full rules and the examples, see "Packages from directories" in the [Gradle plugin README](../bookmap-copybook-gradle-plugin/README.md#4-packages-from-directories). The rules are the same.

| Copybook in `sourceDir` | Package, with `packageName` = `com.example.mainframe` |
|---|---|
| `ORDER.cpy` | `com.example.mainframe` |
| `orders/ORDER.cpy` | `com.example.mainframe.orders` |
| `Billing/Open-Invoices/INVOICE.cpy` | `com.example.mainframe.billing.open_invoices` |

If the final package is not a valid Java package name, the goal logs an error for that copybook. It checks the other copybooks, and then the build fails.

## 5. Options files

Put the options of one copybook in a file `<NAME>.json` next to `<NAME>.cpy`. The file is optional.
The file has the format of the `--config` file of the command line. See "Options file" in the [wiki](https://github.com/ajlima/bookmap/wiki/Copybook-generator#options-file).

- `package` in the file has priority over the package from the directories.
- `lombok` in the file has priority over the `lombok` parameter.
- `copyDirs` in the file come first in the search order. The `copyDirs` parameter comes after them.
- A relative directory in `copyDirs` of the file starts at the directory of the file.
- All other options come only from the file.

Mapping options such as `stripPrefixes`, `directions`, `subTypes`, and `countFields` name items of one copybook. Thus, the goal has no parameter for them. Put them in the options file.
For example, `countFields` links a count field to a fixed table. See "Count field of a fixed table" in the [wiki](https://github.com/ajlima/bookmap/wiki/Copybook-generator#count-field-of-a-fixed-table).

If the file cannot be read, is not valid JSON, has an unknown key, or has a path in `copyDirs` that is not valid, the build fails. The goal logs an error line that starts with the path of the file.

The copybook and its `COPY` members use the `charset` and the `format` of the options file. The default charset is UTF-8. The goal does not use `project.build.sourceEncoding`. Write the options file in UTF-8.

## 6. Class name collisions and `packagePerCopybook`

The generator writes one Java class for each level `01` record, each `OCCURS` group, and each subtype.
A group without `OCCURS` gives no class: its fields go into the class of the parent.
The generator makes the class names unique only in one copybook.

All copybooks in one directory go to one package. Thus, two copybooks in one directory can give the same class.
For example, `CUSTOMER-ORDER.cpy` and `CUSTOMER-RETURN.cpy` both contain `COPY ORDER-LINE.`, and the member has an `OCCURS` group. Each copybook gives a class `OrderLine` in the same package. The build fails with this message:

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

## 7. `COPY` members: where to put them

**The goal does not search the tree for `COPY` members.** You must declare the directories that hold them.

- Declare the directories in the `copyDirs` parameter, or in an options file.
- The search order is: first the `copyDirs` of the options file of the copybook, then the `copyDirs` parameter, in the order that you give.
- The search in each directory is not recursive. A member in a subdirectory of a copy directory is not found.
- For `COPY ADDRESS.`, the generator looks in each directory for a file `ADDRESS`, `ADDRESS.cbl`, `ADDRESS.cob`, or `ADDRESS.cpy`. The case of the name is not important.
- If one directory has more than one of these files, the first name in alphabetical order wins: `ADDRESS`, then `ADDRESS.cbl`, then `ADDRESS.cob`, then `ADDRESS.cpy`. Keep only one file for each member.
- The first directory that has the member wins. Thus, the order of the directories is important if two directories have a member with the same name.
- If no directory has the member, the build fails. The message gives the member and the searched directories, for example: `copy member 'ADDRESS' not found in /work/mainframe-model/src/main/copylib`.

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

## 8. Up-to-date check and `skip`

Maven has no input and output tracking. The goal does its own check:

- After a successful run, the goal stores a SHA-256 fingerprint of all inputs in `${project.build.directory}/bookmap-copybook/`. Each `outputDir` has its own fingerprint file.
- The inputs are the copybooks and all `.json` files below `sourceDir` (the `excludes` do not apply to the `.json` files), the files directly in each copy directory (not in its subdirectories), the order of the copy directories, the parameters `packageName`, `lombok`, `packagePerCopybook`, `outputDir`, `includes`, and `excludes`, and the plugin version.
- In the next build, if the fingerprint is the same and `outputDir` exists, the goal logs `The generated classes are up to date.` and does not generate again.
- A changed, added, or removed file, or a changed parameter, makes the goal generate again. Each run deletes the old files in `outputDir` first, so a removed copybook removes its classes.
- If the goal fails, it stores no fingerprint. The next build runs the goal again.
- `mvn clean` removes the fingerprint. It removes the generated files only if `outputDir` is below `target`, as the default is.
- The goal does not check the files in `outputDir`, only that the directory exists. If you delete a generated file, delete `target/bookmap-copybook/` or run `mvn clean`.

**WARNING:** Give each execution of the goal in one module its own `outputDir`. Each run deletes all files in its `outputDir`, so two executions with the same `outputDir` remove the classes of each other.

With `skip` (`-Dbookmap.copybook.skip=true`), the goal does not generate, and it logs `Skipped.` It still adds `outputDir` to the source roots, so the classes of an earlier run stay available to the compiler.

## 9. Multi-module projects

Put the copybooks in one module. That module runs the plugin and the processor, and its JAR contains the generated classes and their codecs. The other modules depend on that JAR and do not run the plugin.

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
      <version>0.24.0</version>
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

## 10. Example

The directory [`bookmap-examples-maven-plugin`](../bookmap-examples-maven-plugin) is a standalone multi-module Maven build that uses the plugin.
It has the module `mainframe-model` (copybooks in two subpackages, an options file, and a `COPY` member) and the module `orders-service` (a test with the simulator transport).
It uses the snapshot version of this repository and the Maven Wrapper. Run it from the repository root:

```bash
./gradlew publishToMavenLocal
```

```bash
bookmap-examples-maven-plugin/mvnw -B -f bookmap-examples-maven-plugin/pom.xml verify
```
