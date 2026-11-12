## The client throws MappingException: no codec for a class

The processor did not generate a codec, or the codec is not on the classpath.

1. Add `bookmap-processor` to `annotationProcessor`, not to `implementation`.
2. Make sure that the class has `@BookOperation`.
3. Look for the class `<Class>_BookCodec` in the generated sources of the build.
4. Look for the file `META-INF/services/pt.dazz.bookmap.spi.BookCodec` in the output of the build.

The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec, unless it is in the `@BookSubTypes` list of its base class.

## Lombok and BookMap do not work together

List Lombok before BookMap in `annotationProcessor`. This is the recommended order.

If javac reports an error in the generated codec about a missing constructor, the class has no constructor without arguments.
Add `@NoArgsConstructor` to the class. Use `@NoArgsConstructor(force = true)` for a class with a `final` field.
See [Annotations reference](Annotations-reference#lombok).

## The codec throws MappingException for a date or time

The pattern cannot keep the value. For example, the year 1975 does not fit in `yyMMdd`, and a time with seconds does not fit in `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.
See [PIC syntax and type mapping](PIC-syntax-and-type-mapping#dates-and-times).

## The codec throws MappingException for a value that is too large

The codec never truncates and never rounds. Check the size of the field in the copybook.
A negative value in an unsigned field also causes this error. Add `S` to the `pic`, or set `signed = true`.

## The codec throws MappingException for a short response

The response is shorter than the layout. Check the `order` values and the sizes against the copybook.
For a fixed collection, the response must contain the full area.
The codec ignores extra bytes at the end of a response.

## The client throws ContinuationLimitException

`hasContinuation()` is still true after `maxCalls` calls.

1. Check that `hasContinuation()` reads a field that the remote program sets, for example `moreData`.
2. Set the restart key to its start value before you reuse a request object.
3. If the result is really that large, increase `maxCalls` on the builder.

## A text field is "" and not null

This is the expected behavior. An empty text field becomes `""`, never `null`.
A number or a date with only spaces or low-values becomes `null` for a wrapper type and `0` for a primitive type.

## A fixed collection gives a wrong number of items

A fixed collection without `dependsOn` uses LOW-VALUES mode. The unused slots must contain only `0x00` bytes.
If the program uses a count field, set `dependsOn` to its name.
Do not rely on convention 4 for a fixed collection. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#fixed-collections).

## Can I use a multi-byte charset?

No. Only single-byte charsets are permitted. Multi-byte charsets are on the [Roadmap](Roadmap).

## Can I use separate classes for the request and the response?

No. One class holds both directions. Set the `direction` of each field.
This is a design decision. See the "Out of scope" section of the [Roadmap](Roadmap).

## The generator cannot find a COPY member

The tools do not search the tree for `COPY` members. Declare their directories in `copyDirs` (plugins and options file) or with `--copy-dir` (command line).
The search in each directory is not recursive.

## The Gradle plugin is not found

The plugin is on Maven Central only. It is not on the Gradle Plugin Portal.
Add `mavenCentral()` to `pluginManagement.repositories` in `settings.gradle.kts`.
See [Copybook generator Gradle plugin](Copybook-generator-Gradle-plugin#setup).

## Which copybook tool do I use?

Use the Gradle plugin or the Maven plugin to generate the classes in each build. Use the command line to generate the classes one time.
See [Copybook generator](Copybook-generator#scaffold-mode-and-build-mode).

## Where do I report a problem?

Open an issue in the [GitHub repository](https://github.com/ajlima/bookmap/issues).
