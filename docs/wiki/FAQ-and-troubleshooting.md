## The client throws MappingException: no codec for a class

The message is `No codec for <class>. Make sure that bookmap-processor is on the annotationProcessor path.`
The processor did not generate a codec, or the codec is not on the classpath.

1. Add `bookmap-processor` to `annotationProcessor` (Gradle) or to `annotationProcessorPaths` (Maven), not to the normal dependencies.
2. Make sure that the class has `@BookOperation`.
3. Look for the class `<Class>_BookCodec` in the generated sources of the build.
4. Look for the file `META-INF/services/pt.dazz.bookmap.spi.BookCodec` in the output of the build.

The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec, unless it is in the `@BookSubTypes` list of its base class.

## Lombok and BookMap do not work together

List Lombok before BookMap in `annotationProcessor`. This is the recommended order.

If the processor reports `operation class must have a public no-argument constructor`, a Lombok annotation removed the default constructor.
For example, `@AllArgsConstructor`, `@RequiredArgsConstructor`, and `@Builder` do this. Add `@NoArgsConstructor` to the class.

If javac reports an error in the generated codec about a missing constructor, the class has `@Data` and a `final` or `@NonNull` field.
The processor cannot detect this case when it runs before Lombok. Add `@NoArgsConstructor(force = true)` to the class.

If the processor reports `missing getter` or `missing setter`, the accessor is not public. Use `AccessLevel.PUBLIC`, or put the class in the package of the operation class.
See [Annotations reference](Annotations-reference#lombok).

## The codec throws MappingException for a date or time

The message is `<field>: value <value> cannot be represented by the pattern (...)`.
The pattern cannot keep the value. For example, the year 1975 does not fit in `yyMMdd`, and a time with seconds does not fit in `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.
See [PIC syntax and type mapping](PIC-syntax-and-type-mapping#dates-and-times).

## The codec throws MappingException for a value that is too large

The messages are `<field>: text is longer than N characters (L)`, `<field>: <value> has more than N digits`, and `<field>: <value> has more than S decimal places`.
The codec never truncates and never rounds. Check the size of the field in the copybook.
For a `BigDecimal` with too many decimal places, round the value before you set it, for example with `setScale(2, RoundingMode.HALF_EVEN)`.
A negative value in an unsigned field also causes this error. Add `S` to the `pic`, or set `signed = true`.

## The codec throws MappingException for a short response

The message is `<field>: response too short, need N bytes at position P but only R remain`. The field in the message is the first field that does not fit.
The response is shorter than the layout. Check the `order` values, the sizes, and the directions against the copybook.
For a fixed collection, the response must contain the full area.
The codec ignores extra bytes at the end of a response.

## The client throws ContinuationLimitException

The message is `<operation>: the remote program has more data after <n> calls`. `hasContinuation()` is still true after `maxCalls` calls.

1. Check that `hasContinuation()` reads a field that the remote program sets, for example `moreData`.
2. Set the restart key to its start value before you reuse a request object.
3. If the result is really that large, increase `maxCalls`: `BookmapClient.builder().maxCalls(n)`, or `bookmap.max-calls` in Spring Boot. The default is 1000.

## A text field is "" and not null

This is the expected behavior. An empty text field becomes `""`, never `null`.
A number or a date with only spaces or low-values becomes `null` for a wrapper type and `0` for a primitive type.

## A fixed collection gives a wrong number of items

A fixed collection without `dependsOn` uses LOW-VALUES mode. The unused slots must contain only `0x00` bytes.
If the program uses a count field, set `dependsOn` to its name.
Do not rely on convention 4 for a fixed collection. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#fixed-collections).

## The simulator throws OperationNotFoundException

The message is `No simulated operation registered: <operation>`. The test did not register a handler for the operation.
Register the handler with the class of the request. The operation name comes from `@BookOperation`, or from the simple class name.

## The simulator throws TransportTimeoutException

The message is `Simulated operation <operation> did not finish in 5000 ms`. The handler is slower than the timeout.
Set a longer timeout: `SimulatorTransport.builder().timeout(Duration.ofSeconds(10))`.

## The text fields in a simulator test are wrong

The simulator and the client use different charsets. Set the same charset on both builders.

## The client throws IllegalStateException: More than one codec

The message is `More than one codec for <class>`. Two codecs for the same class are on the classpath.
For example, two JARs contain the generated classes of the same book. Keep only one of them.

## Spring Boot has no BookmapClient bean

The auto-configuration creates the client only when a `Transport` bean exists. Declare a `Transport` bean.
See [Spring Boot](Spring-Boot#what-the-auto-configuration-creates).

## The application does not start: Only single-byte charsets are valid

The charset of the client, or `bookmap.charset` in Spring Boot, is a multi-byte charset, for example `UTF-8`.
Use an EBCDIC charset, for example `IBM037` or `IBM500`.

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
