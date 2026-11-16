The library has a new name, CopyMapper, to prevent confusion with another software product with the name BookMap.
The release `0.28.0` is the first release with the new name. This page tells how to update a project from BookMap `0.27.0` or earlier.

The behavior of the codec, the processor, the generator, and the transports does not change.
Only the names change. The releases of BookMap stay on Maven Central with their old names.

## What changes

- The Maven group `pt.dazz.bookmap` becomes `pt.dazz.copymapper`.
- Each module `bookmap-<suffix>` becomes `copymapper-<suffix>`. For example, `bookmap-core` becomes `copymapper-core`.
- Each package `pt.dazz.bookmap.*` becomes `pt.dazz.copymapper.*`.
- The types with `Bookmap` in the name, the annotations, and the keys change. The tables below give each name.
- The repository is `https://github.com/dazz-pt/copymapper`. The old address redirects to it.

## Dependencies

Gradle:

```kotlin
dependencies {
    implementation("pt.dazz.copymapper:copymapper-core:0.28.0")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor:0.28.0")
    testImplementation("pt.dazz.copymapper:copymapper-transport-simulator:0.28.0")
}
```

Maven:

```xml
<dependency>
  <groupId>pt.dazz.copymapper</groupId>
  <artifactId>copymapper-core</artifactId>
  <version>0.28.0</version>
</dependency>
```

With Maven, also change the `copymapper-processor` entry in the `annotationProcessorPaths` of the `maven-compiler-plugin`.

## Types

| Before | After |
|---|---|
| `BookmapClient` | `CopyMapperClient` |
| `BookmapException` | `CopyMapperException` |
| `BookmapAutoConfiguration` | `CopyMapperAutoConfiguration` |
| `BookmapProperties` | `CopyMapperProperties` |
| `BookmapTransportProperties` | `CopyMapperTransportProperties` |
| `BookmapProcessor` | `CopyMapperProcessor` |
| `BookmapExtension` | `CopyMapperExtension` |
| `BookmapCopybookPlugin` | `CopyMapperCopybookPlugin` |

These types do not change: `BookCodec`, `BookCodecRegistry`, `BookRequest`, `BookReader`, `BookWriter`, `BookModel`, and the suffix `_BookCodec` of the generated codecs.
"Book" is the COBOL term for a record layout.

## Annotations

| Before | After |
|---|---|
| `@BookOperation` | `@Operation` |
| `@BookField` | `@Field` |
| `@BookCollection` | `@Occurs` |
| `@BookGroup` | `@Group` |
| `@BookSubTypes` and `@BookSubTypes.Type` | `@SubTypes` and `@SubTypes.Type` |
| `@BookDefaults` | `@Defaults` |

The attributes do not change. `@CobolValue`, `@CobolUnknown`, `Direction`, and `EmptyItem` do not change.

**CAUTION:** Replace `@BookCollection` with `@Occurs`, not with `@Collection`. `@Collection` does not exist, and the code does not compile.

## Keys and plugins

| Where | Before | After |
|---|---|---|
| Spring Boot properties | `bookmap.charset`, `bookmap.max-calls`, `bookmap.transport.*` | `copymapper.charset`, `copymapper.max-calls`, `copymapper.transport.*` |
| Spring Boot environment variables | `BOOKMAP_*` | `COPYMAPPER_*` |
| Gradle plugin id | `pt.dazz.bookmap.copybook` | `pt.dazz.copymapper.copybook` |
| Gradle extension | `bookmap { copybooks { … } }` | `copymapper { copybooks { … } }` |
| Maven plugin goal | `bookmap-copybook:generate` | `copymapper-copybook:generate` |
| Maven plugin properties | `bookmap.copybook.*` | `copymapper.copybook.*` |
| Command line JAR | `bookmap-copybook-cli-<version>-all.jar` | `copymapper-copybook-cli-<version>-all.jar` |

## Update a project

1. Commit or save your work, so that you can examine the changes.
2. Replace the names in the Java files. This command uses Perl, which is available on Linux and macOS:

   ```bash
   find . -name '*.java' -not -path '*/build/*' -not -path '*/target/*' -exec perl -pi -e '
     s/\bpt\.dazz\.bookmap\b/pt.dazz.copymapper/g;
     s/\bBookCollection\b/Occurs/g;
     s/\bBookOperation\b/Operation/g;
     s/\bBookField\b/Field/g;
     s/\bBookGroup\b/Group/g;
     s/\bBookSubTypes\b/SubTypes/g;
     s/\bBookDefaults\b/Defaults/g;
     s/\bBookmap(Client|Exception|AutoConfiguration|Properties|TransportProperties|Processor|Extension|CopybookPlugin)\b/CopyMapper$1/g;
   ' {} +
   ```

3. Replace the names in the build files:

   ```bash
   find . \( -name '*.gradle' -o -name '*.gradle.kts' -o -name 'pom.xml' \) -not -path '*/build/*' -not -path '*/target/*' -exec perl -pi -e '
     s/\bpt\.dazz\.bookmap\b/pt.dazz.copymapper/g;
     s/\bbookmap-/copymapper-/g;
     s/^(\s*)bookmap \{/$1copymapper {/;
     s/\bbookmap\.copybook\./copymapper.copybook./g;
   ' {} +
   ```

   Then set the version of each CopyMapper dependency and plugin to `0.28.0`.
4. Replace the Spring Boot properties in `application.properties`, `application.yml`, and the deployment files. In YAML, change the top-level key `bookmap:` to `copymapper:`. Change each environment variable `BOOKMAP_*` to `COPYMAPPER_*`.
5. Compile the project again. The codecs that BookMap generated are not found by CopyMapper, because the name of the services file changed.
6. Run the tests.

A file that imports `java.lang.reflect.Field` and an annotation with the name `Field` does not compile after step 2. In that file, use the qualified name of one of the two types.

## Errors that help the migration

**The application does not start.** With `copymapper-spring-boot`, an application that still has a property `bookmap.charset`, `bookmap.max-calls`, or `bookmap.transport.*` does not start.
The message lists the names of these properties, but not their values. Rename each property to `copymapper.*`.
Without this check, Spring Boot ignores the old property, and the application uses the default value, for example the charset `IBM037`.

**The build stops with "Change the import".** The IDE can import an annotation of another library with the same name, for example the `@Field` of Spring Data MongoDB.
The processor stops the build with a message such as this one:

```text
GetOrder.note: org.springframework.data.mongodb.core.mapping.Field is not pt.dazz.copymapper.annotation.Field. Change the import.
```

Change the import to `pt.dazz.copymapper.annotation.Field`. To prevent the problem, see "Imports in the IDE" in [Conventions](Conventions).
