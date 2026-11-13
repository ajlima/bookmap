# bookmap — Design

Date: 2026-11-10
Status: implemented in v0.1.0.
Base package: `pt.dazz.bookmap`

Decisions marked **[builder call]** come from the spec author, not from the project owner. You can change them if you have a good reason.

## 1. Intent

The project owner had a framework (`cicsannotations`). It mapped COBOL books of CICS programs to Java POJOs with annotations (`@Cics.Field`, `@Cics.Transaction`, `@Cics.Collection`). It called the programs through the IBM J2C connector in WebSphere.

**bookmap** replaces that framework. It has three basic changes:

1. **No middleware dependency.** The book ↔ POJO mapping does not know about CICS. CICS is only one possible transport. Other transports are CTG/ECI, HTTP (z/OS Connect), MQ, and a simulator.
2. **No reflection at runtime.** An annotation processor generates all serialization code at compile time. Thus, native images (GraalVM, Spring AOT, Quarkus) need no reflection configuration. The EBCDIC charsets are in the JDK module `jdk.charsets`. `bookmap-core` ships a `native-image.properties` file that adds them (`-H:+AddAllCharsets`). The standalone build `bookmap-native-test` tests this configuration in a native image.
3. **No DI container dependency.** The core is plain Java. Optional modules connect it to Spring, Quarkus, or Micronaut.

The owner has no access to a CICS system now. Thus, the first transport is a **simulator**. The simulator must let you test the full cycle, including pagination and errors.

You can use the code of the old framework as an inspiration. It is not a limit.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals (v1)

- Annotations that describe a book layout in a POJO: fields, direction, types, conversions, and variable-length collections.
- An annotation processor that generates one codec for each POJO. The processor also validates the layout at compile time.
- A client that executes a call. The client serializes the POJO, sends it through a transport, deserializes the response, and validates it. It also does the restart automatically (pagination) and returns the full collection.
- Interfaces `Continuable` (restart) and `Validatable` (business return validation).
- An exception hierarchy that is independent of the middleware.
- An in-memory simulator transport.
- One DI integration: **Spring Boot** **[builder call]**.

### 2.2 Non-goals (v1, possible later)

- Real transports (CTG/ECI, MQ, HTTP/z/OS Connect). The transport SPI must let you add them without a change to the core.
- Quarkus and Micronaut integrations.
- Generation of POJOs from copybook source (a COBOL parser).
- `REDEFINES` without a discriminator, nested collections (a collection in a collection item), `COMP-1`/`COMP-2` (floating point), `SIGN LEADING`/`SIGN SEPARATE`. A `REDEFINES` with a discriminator is supported through subtypes: see `2026-11-11-bookmap-subtypes-design.md`.
- Separate request and response classes. One class holds both directions (see 4.1).
- An asynchronous or reactive API.

### 2.3 Anti-goals (failure, even if the code works)

- A class in the core or processor module depends on Spring, Quarkus, Micronaut, Guice, or a middleware library.
- The core API contains concepts of one middleware (for example, CICS transaction, program, or COMMAREA).
- The framework uses reflection at runtime to read fields, call methods, or create instances of user classes.
- Silent data corruption: the framework truncates text, or loses digits or decimal places, without an error.
- A layout error that the compiler can find occurs first in production.

## 3. Constraints

- Gradle. Java 21 or later (toolchain 21).
- The core and processor modules have no runtime dependencies other than the JDK. The processor can use JavaPoet (shaded or as a processor-path dependency).
- The processor generates code with **JavaPoet**.
- No reflection at runtime on user classes by bookmap code. The runtime finds codecs through generated code and `ServiceLoader` (the JDK creates the codec instances). GraalVM supports `ServiceLoader` natively.
- Only single-byte charsets are supported. Thus, text `length` in characters is equal to the length in bytes. The client, the simulator, and the processor reject other charsets.
- The tests must reach 100% line and branch coverage of production code (see `CLAUDE.md`).
- All code, documentation, examples, and test data are in English. Documentation follows ASD-STE100 for approximately 80%. Examples use an international business domain. See `CLAUDE.md`.

## 4. Decisions of the project owner

### 4.1 One POJO for request and response, with a direction on each field

Each field has a `Direction`: `INPUT`, `OUTPUT`, or `INPUT_OUTPUT`.

- The **request layout** contains only `INPUT` and `INPUT_OUTPUT` fields, in order.
- The **response layout** contains only `OUTPUT` and `INPUT_OUTPUT` fields, in order.
- A field that is not in a direction does not occupy bytes in the area of that direction. The processor calculates the offsets separately for each direction.

One class is correct for v1, because:
- It is the real model: a COMMAREA or a message is one area with input, output, and input-output fields.
- A restart key is `INPUT_OUTPUT`. Thus, it goes back automatically in the next call, and `hasContinuation()` can be a simple boolean method.

### 4.2 Explicit order

Each field has an `order`. The `order` sets the position in the book. The declaration order in Java has no effect. Gaps are permitted (for example, 10, 20, 30). Only the relative order is important.

The `order` is mandatory. Without it, an IDE "sort members" action or a refactoring can change the book layout without an error.

### 4.3 Logical operation name

The class has an annotation with a **logical operation name** (for example, `"GetCustomerOrders"`). It replaces `@Cics.Transaction`. Each transport maps the logical name to its own destination through configuration. For example, the CICS transport maps the name to a transaction and a program. An MQ transport maps it to queues. The simulator uses the name directly.

### 4.4 Variable-length collections

A collection works like `OCCURS DEPENDING ON`. The book size really changes with the number of items. The collection annotation points to the count field. A fixed table (`OCCURS n` without `DEPENDING ON`) is supported through `fixed = true`: see `2026-11-11-bookmap-fixed-occurs-design.md`.

### 4.5 Restart (pagination) with the `Continuable` interface

A POJO with pagination implements `Continuable`. The interface has one method: `boolean hasContinuation()`. After each call, the framework calls this method. If the method returns `true`, the framework calls the operation again. The method can read all the fields of the object. If necessary, it can also change fields for the next call. The framework repeats the cycle until the method returns `false`. Then it returns the POJO with the full collection.

### 4.6 Return validation with the `Validatable` interface

A COBOL program can return a business error in the output area (a return code and a message). For this case, the POJO implements `Validatable`. The method itself decides on the error and throws the exception.

### 4.7 Technical errors

The transport translates technical failures (abend, timeout, connection failure) into framework exceptions. These exceptions are independent of the middleware.

### 4.8 Code generation instead of reflection

An annotation processor generates one codec for each annotated POJO, with JavaPoet.

### 4.9 Supported types

Text `PIC X`, zoned numeric with and without a sign (`PIC 9`, `PIC S9`), implied decimal (`V99`), `COMP-3`, `COMP` (binary), dates in `PIC 9(8)` and other formats, EBCDIC↔ASCII encoding.

### 4.10 Lombok support

The POJOs can use Lombok to generate getters and setters. See 5.5.

### 4.11 Names

Framework name: `bookmap`. Package: `pt.dazz.bookmap`. The annotations have neutral names (no "Cics").

### 4.12 Convention over configuration

Use as few annotation attributes as possible. The owner approved these six conventions:

1. **Operation name.** `@BookOperation` without a value uses the simple class name.
2. **`pic` attribute.** A COBOL `PIC` string replaces `length`, `scale`, `signed`, and `usage` (for example, `pic = "S9(13)V99 COMP-3"`). The separate attributes stay available.
3. **Date length from the pattern.** A date or time field without `length` and without `pic` gets its length from the pattern.
4. **`dependsOn` by convention.** Without `dependsOn`, the count field is the member immediately before the collection.
5. **Default directions.** A collection is `OUTPUT` by default. The count field gets the direction of its collection. Other fields are `INPUT_OUTPUT` by default.
6. **`@BookDefaults`.** An optional annotation on a class or a package sets default values (charset, `usage`, `signed`, date pattern) for a group of books.

These attributes stay mandatory: `order` (see 4.2), `maxOccurs`, and the size of text and numeric fields (through `pic` or `length`).

## 5. Design

### 5.1 Gradle modules

| Module | Content | Depends on |
|---|---|---|
| `bookmap-core` | annotations, client API, `Continuable`, `Validatable`, transport SPI, codec SPI, type converters, exceptions | JDK only |
| `bookmap-processor` | annotation processor: codec generation and layout validation | `bookmap-core` (annotations and types), JavaPoet |
| `bookmap-transport-simulator` | in-memory transport | `bookmap-core` |
| `bookmap-spring-boot` | Spring Boot auto-configuration | `bookmap-core`, Spring Boot |
| `bookmap-examples` | example POJOs and end-to-end tests with the simulator | all modules above |

**[builder call]** The annotations are in `bookmap-core`. A separate annotations module is optional.

### 5.2 Annotations **[builder call: names and attributes]**

```java
@BookOperation                       // operation name = "GetCustomerOrders"
public class GetCustomerOrders implements Continuable, Validatable { ... }
```

**`@BookOperation`** (class): `value` — the logical operation name. Default: the simple class name.

**`@BookField`** (field):

| Attribute | Meaning |
|---|---|
| `order` | position in the book (int, mandatory, gaps permitted) |
| `pic` | COBOL `PIC` string, with an optional usage clause (see 5.2.1). Replaces `length`, `scale`, `signed`, and `usage`. |
| `length` | the `PIC` length: number of characters for text, **total number of digits** for numbers (for example, `S9(13)V99` → `length = 15`) |
| `direction` | `INPUT`, `OUTPUT`, `INPUT_OUTPUT`. Default `INPUT_OUTPUT`. A count field of a collection without an explicit direction gets the direction of the collection. |
| `usage` | `DISPLAY`, `COMP3`, `COMP`. Default: from `@BookDefaults`, else `DISPLAY`. |
| `scale` | number of implied decimal places (`V99` → 2), default 0 |
| `signed` | `true` if the field has a sign (`S9`). Default: from `@BookDefaults`, else `false`. |
| `pattern` | date or time format. Default: from `@BookDefaults`, else `yyyyMMdd`. |
| `converter` | custom converter class (optional) |

Rules:
- A field must have `pic` or `length`, with one exception: a date or time field without both gets its length from the pattern (for example, `yyyyMMdd` → 8).
- If you set `pic`, you must not also set `length`, `scale`, `signed`, or `usage` on the same field. This is a compile error. The processor finds explicitly set attributes through `AnnotationMirror.getElementValues()`.
- An explicitly set attribute has priority over `@BookDefaults`.
- The `usage` and `signed` defaults apply only to numeric Java types (`int`, `Integer`, `long`, `Long`, `BigDecimal`). Text, date/time, and converter fields ignore them.
- A converter field must have `pic` or `length`. They set its byte length.
- An annotated `static` field, or a field with both `@BookField` and `@BookCollection`, is a compile error.

**`@BookCollection`** (field of type `List<Item>`):

| Attribute | Meaning |
|---|---|
| `order` | position in the book (mandatory) |
| `direction` | as in `@BookField`. Default `OUTPUT`. |
| `dependsOn` | name of the count field. Default: the member immediately before the collection, in `order`. |
| `maxOccurs` | maximum number of items in one call (mandatory). Used for validation and as a buffer-size hint for transports. |

The item class has `@BookField` fields without `direction`. They use the direction of the collection. An explicit `direction` on an item field is a compile error. The item class must have a public no-argument constructor. A count field can serve only one collection.

**`@BookDefaults`** (class or package, through `package-info.java`):

| Attribute | Meaning |
|---|---|
| `charset` | charset name for the codec. Default `""`: the client charset. The charset applies to the full codec. Thus, only `@BookDefaults` on the operation class or its package sets it. A different `charset` on a superclass or an item class is a compile error. The charset must be single-byte. |
| `usage` | default `usage` for numeric fields without `pic` usage clause and without explicit `usage` |
| `signed` | default `signed` for numeric fields without `pic` and without explicit `signed` |
| `pattern` | default date or time pattern |

Priority, from high to low: explicit attribute on the field → `@BookDefaults` on the class that declares the field → `@BookDefaults` on the package of that class → built-in default. A field in a superclass or in a collection item uses the defaults of its own declaring class and package.

#### 5.2.1 PIC string syntax

The processor parses `pic` at compile time. An unsupported or incorrect `pic` is a compile error.

- An optional `PIC` or `PICTURE` prefix and an optional `.` at the end are permitted. Letters are not case-sensitive. Spaces between parts are permitted.
- Text: `X(n)` or a sequence of `X` (for example, `XXX`) → text, `length = n`.
- Number: optional `S`, then the integer digits `9(n)` or a sequence of `9`, then optionally `V` and the decimal digits `9(m)` or a sequence of `9`. Examples: `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`. `length` = integer digits + decimal digits. `scale` = decimal digits. `signed` = `S` is present.
- Optional usage clause after the picture, with an optional `USAGE` or `USAGE IS` prefix: `COMP-3`, `COMPUTATIONAL-3`, or `PACKED-DECIMAL` → `COMP3`; `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, or `BINARY` → `COMP`; `DISPLAY` → `DISPLAY`. Without a clause: `@BookDefaults` usage, else `DISPLAY`.
- Not supported in v1 (compile error): edited pictures (`Z`, `*`, `,`, `.` inside the picture, `+`, `-`, `CR`, `DB`, `B`, `0`, `/`), `P` scaling, `A`, `N`, `COMP-1`, `COMP-2`, `COMP-5`. A `V` without decimal digits after it (`9(3)V`) is valid COBOL, but bookmap rejects it in v1 **[builder call]**.

The byte size comes from `usage` and `length`:
- `DISPLAY` text or number: `length` bytes.
- `COMP3`: `length / 2 + 1` bytes.
- `COMP`: 1–4 digits = 2 bytes, 5–9 digits = 4 bytes, 10–18 digits = 8 bytes.

### 5.3 Java ↔ COBOL mapping **[builder call]**

| Java type | Permitted COBOL definition |
|---|---|
| `String` | `PIC X(n)` |
| `int`, `Integer`, `long`, `Long` | `PIC 9` or `PIC S9`, `DISPLAY`, `COMP3`, or `COMP`, `scale = 0` |
| `BigDecimal` | any numeric definition, with `scale` |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY` with a `pattern`. All `DateTimeFormatter` patterns with a fixed text length are permitted (for example, `yyyyMMdd`, `yyMMdd`, Julian `yyyyDDD`, `HHmmss`, `yyyy-MM-dd`). Formatters use `ResolverStyle.STRICT` and `Locale.ROOT`. The processor makes the formatter and formats a sample of the field type (2026-12-31 23:59:58.999999999). The field length is the length of this formatted text (for example, `yyyy-MM-dd'T'HH:mm:ss` gives 19). An explicit `length` must agree with it. The formatted sample must parse back to the field type. This also applies to an `INPUT` field. A second sample with one-digit values (2026-01-01 01:01:01.000000001) must give text of the same length. Thus, `d`, `M`, `H`, and `n` are compile errors. Under `Locale.ROOT`, `MMMM` and `EEEE` give fixed three-letter names (for example, `Dec`), so they are permitted. An invalid pattern, or a pattern that does not fit the type, is a compile error. A `pic` with `S` or `V`, or an explicit `signed` or `scale`, is a compile error. A two-digit year (`yy`) reads as a year from 2000 to 2099. On write, the codec parses the text again and compares the result with the value. If they are different, the codec throws `MappingException` (see "Date or time, write" below). |
| any type | through a `converter` |

The framework has no built-in support for packed dates (for example, `S9(7) COMP-3` in the format `0CYYDDD`). Use a `converter` for them **[builder call]**.

An incorrect combination is a **compile error**. Examples: `String` with `COMP3`, `int` with `scale > 0`, `long` with more than 18 digits.

Conversion rules:
- **Text, write:** the framework adds spaces on the right.
- **Text, read:** the framework changes low-values (`0x00`) to spaces and removes the spaces on the right. A field with only spaces or low-values becomes `""` (empty string), never `null`. Thus, a comparison such as `"0000".equals(returnCode)` is predictable **[builder call]**.
- **Signed `DISPLAY` number:** trailing overpunch sign (the COBOL default). For an EBCDIC charset, the codec works on bytes, not on characters: each digit is `0xF0 | d`, and the last byte is `0xC0 | d` (positive) or `0xD0 | d` (negative). This is correct for all EBCDIC code pages. On read, zones `C`, `F`, `A`, `E` are positive and `D`, `B` are negative. For an ASCII charset, the codec uses the characters `{ABCDEFGHI` (positive) and `}JKLMNOPQR` (negative). An unsigned number contains only digits. On read, an unsigned field accepts zone `C` in the last byte and rejects zone `D`.
- **`COMP3`:** sign nibble `C` (positive), `D` (negative), `F` (unsigned). On read, a signed field accepts `C`, `F`, `A`, `E` (positive) and `D`, `B` (negative). An unsigned field accepts `C`, `F`, `A`, `E` and rejects `D` and `B`. For an even number of digits, the first nibble is a pad nibble. On read, the pad nibble must be `0`. A different value causes `MappingException`.
- **`COMP`:** big-endian. Two's complement when `signed = true`. A `COMP` read never gives `null`: all `0x00` is the value 0. The digit limit of the `PIC` applies on write only. On read, all values that fit in the binary size are accepted, because hosts compiled with `TRUNC(BIN)` or `TRUNC(OPT)` can send larger values **[builder call]**.
- **Value too large:** if text is longer than `length`, a number has too many digits or decimal places, or a negative number goes into an unsigned field, the framework throws `MappingException`. It never truncates or rounds silently.
- **Null, write:** text → spaces, number → zeros, date → zeros.
- **Date or time, write:** the codec formats the value, parses the text again, and compares the result with the value. If the pattern cannot keep the value, the codec throws `MappingException`. Examples: a time with seconds and the pattern `HHmm`, a time with nanoseconds and the pattern `HHmmss`, or the year 1975 and the pattern `yyMMdd`. The codec never truncates a date or time silently. The caller removes the extra precision before it sets the value (for example, with `truncatedTo(ChronoUnit.SECONDS)`).
- **Decode errors:** if the charset cannot decode a byte of a text, date, or `DISPLAY` number field, the codec throws `MappingException`. It never puts a replacement character (U+FFFD) in a value.
- **Number or date, read:** a `DISPLAY` or `COMP3` field with only spaces or low-values becomes `null` for wrapper types and `0` for primitive types. A date/time field with only spaces or low-values becomes `null`. A `LocalDate` or `LocalDateTime` field with only zeros also becomes `null`. A `LocalTime` with only zeros is midnight, not `null`.
- **Charset:** the client sets the charset. The default is `IBM037` (EBCDIC). The numeric `DISPLAY` conversions use the same charset. Only single-byte charsets that can encode and decode are permitted. `CodecContext` rejects other charsets with `IllegalArgumentException`.
- **Lombok accessors:** a Lombok accessor with `AccessLevel.PACKAGE`, `AccessLevel.MODULE`, or `AccessLevel.PROTECTED` is callable only if the class that declares the field is in the package of the codec. Otherwise, the accessor is missing, and this is a compile error. See 5.5.

### 5.4 Custom converter **[builder call]**

```java
public interface FieldConverter<J> {
    J decode(byte[] bytes, FieldContext context);
    byte[] encode(J value, FieldContext context);   // must return exactly context.byteLength() bytes
}
```

`FieldContext` contains `length`, `scale`, `signed`, `usage`, `pattern`, `byteLength`, and the charset. The generated code creates the converter with `new`, without reflection. The converter class must be public and have a no-argument constructor. Each codec creates one converter instance. All threads share this instance. Thus, a converter must be thread-safe. The type `J` must agree with the boxed field type. If not, this is a compile error. If a converter returns `null` for a primitive field, the codec throws `MappingException`.

### 5.5 Generated codec

For each class with `@BookOperation`, the processor generates `<ClassName>_BookCodec implements BookCodec<ClassName>` in the same package. For a nested class `Outer.Inner`, the codec is the top-level class `Outer_Inner_BookCodec`.

The operation class must be public, not abstract, not generic, not a record, and top-level or `static` nested. It must have a public no-argument constructor. A base class with `@BookSubTypes` can be abstract; then it needs no constructor. If not, this is a compile error. The codec has these methods:
- `encodeRequest(T, context) → byte[]` and `decodeResponse(byte[], T, context)`: the client uses them.
- `decodeRequest(byte[], context) → T` and `encodeResponse(T, context) → byte[]`: the typed simulator uses them.
- `resetOutputCollections(T)`: see 5.6.
- `operationName()`, `maxResponseLength()` (calculated with `maxOccurs`), `newInstance()`.
- `charsetName()`: the charset from `@BookDefaults`, or `""`. If it is not empty, the client and the simulator use this charset for this codec instead of their own charset.

**Field access.** The generated code uses JavaBean getters and setters. It does not access private fields. A missing getter or setter is a compile error.

**Lombok.** Lombok generates getters and setters in the same compilation. Thus, the processor must not require that these methods are already visible:
- If the field, or the class that declares the field, has `@lombok.Getter`, `@lombok.Setter`, or `@lombok.Data`, the processor accepts the matching accessors as present. `@Getter` gives the getter, `@Setter` gives the setter, `@Data` gives both (no setter for a `final` field). An annotation with `AccessLevel.NONE` or `AccessLevel.PRIVATE` gives no accessor. `AccessLevel.PACKAGE`, `AccessLevel.MODULE`, and `AccessLevel.PROTECTED` give an accessor only if the class that declares the field is in the package of the codec (the package of the operation class). For a superclass or an item class in another package, they give no accessor. With `@lombok.experimental.Accessors`, the processor does not assume names: the methods must be visible. `@lombok.Value` is not supported, because it makes a class without setters. It generates calls to the conventional names (`getX`, `setX`). The processor finds these annotations **by name**. It does not depend on Lombok **[builder call]**.
- `@lombok.NoArgsConstructor` (public, without `staticName`) gives a public no-argument constructor. `@lombok.NoArgsConstructor`, `@lombok.AllArgsConstructor`, `@lombok.RequiredArgsConstructor`, and `@lombok.Builder` remove the default constructor of javac. Thus, the processor does not count that default constructor. Without an explicit public no-argument constructor or a public `@NoArgsConstructor`, this is the compile error "must have a public no-argument constructor".
- If an accessor is missing and Lombok is not present, the processor defers the class to the next processing round. It reports the error only in the last round. This also covers other code generators.
- The documentation shows the Gradle configuration with Lombok before bookmap in `annotationProcessor`.
- The tests cover: a POJO with `@Data`, a POJO with `@Getter`/`@Setter` on some fields only, and a collection item with Lombok. The processor tests run real Lombok together with bookmap in both processor orders.
- The builder selects the integration method (for example, an SPI similar to `lombok-mapstruct-binding`), if the tests above pass.

**Inheritance.** Annotated fields in superclasses are supported. The `order` must be unique in the full class hierarchy **[builder call]**.

**Records** are not supported. The POJO is mutable: the response and the restart change it.

**Registration.** The processor generates the entries in `META-INF/services/pt.dazz.bookmap.spi.BookCodec`. As AutoService does, it reads the existing file, merges the names, and removes names of types that no longer exist. Thus, an incremental compile does not lose codecs. The `BookCodecRegistry` in the core loads them with `ServiceLoader` and keeps an index by class.

**Compile errors.** The processor reports these errors:
- duplicate `order` in the class hierarchy;
- `dependsOn` (explicit or by convention) refers to a field that does not exist, that is not an integer, that is after the collection, or whose direction does not include the collection direction;
- incorrect `pic`, or `pic` together with `length`, `scale`, `signed`, or `usage`;
- a text or numeric field without `pic` and without `length`;
- incorrect Java type × `usage` combination;
- missing getter or setter (see Lombok above);
- collection item without a public no-argument constructor;
- nested collection;
- `maxOccurs < 1`, or a `dependsOn` field whose digits cannot hold `maxOccurs`;
- an operation class or an item class without `@BookField` or `@BookCollection` members;
- an explicit `signed` or `scale` on a `String` or a date and time field;
- `@BookOperation` with a blank name, or two operation classes with the same name in one compilation;
- `INPUT_OUTPUT` collection in a `Continuable` class (see 5.6);
- an operation class that does not agree with the rules above;
- the other errors in 5.2, 5.3, and 5.4.

Each error message identifies the class, the field, and the `order`.

### 5.6 Collections **[builder call]**

- **Read cursor.** The generated code calculates the offsets after a variable collection at runtime. Fields after a collection are permitted.
- **Write.** In each encode method, for each collection in that direction, the codec sets the `dependsOn` field to the list size. If the list size is more than `maxOccurs`, the codec throws `MappingException`. Thus, a simulated handler does not set the count.
- **Count digits.** The digits of the `dependsOn` field must hold `maxOccurs`. If not, this is a compile error (for example, `9(1)` with `maxOccurs = 50`).
- **Read.** The codec reads the count from the `dependsOn` field. A blank count is 0. If the count is negative or more than `maxOccurs`, the codec throws `MappingException`.
- **Accumulation (`OUTPUT` collections).** On each page, the codec makes a new `ArrayList` with the items of the current list and **adds** the decoded items. Then it calls the setter. Thus, a getter that returns a copy or an unmodifiable list is permitted. The `dependsOn` field shows the count of the **last page**. Thus, `hasContinuation()` can see the full list and the last page (for example, the last item can contain the restart key).
- If the list is `null`, the codec creates an `ArrayList`.
- **Reset.** At the start of each `execute()`, the client clears the `OUTPUT` collections of the POJO (`codec.resetOutputCollections(T)`). Thus, if you use a POJO again, the items do not duplicate. Accumulation occurs only between the pages of one execution. The client does not reset other fields. For example, if you use a POJO again, set the restart key to its start value yourself.
- **`INPUT_OUTPUT` collections.** The reset does not clear them, because they contain request data. `decodeResponse` **replaces** the list with the decoded items.
- An `INPUT_OUTPUT` collection in a `Continuable` class is a compile error in v1. To send the accumulated list again on each page has no clear meaning.

### 5.7 Lifecycle interfaces

```java
public interface Continuable {
    /**
     * The framework calls this method after each response, after validation.
     * Return true to call the operation again. Return false to stop.
     * You can change fields here for the next call, if necessary.
     */
    boolean hasContinuation();
}

public interface Validatable {
    /**
     * The framework calls this method after each response.
     * Throw an exception (for example, a subclass of BusinessException) if the response shows an error.
     */
    void validate();
}
```

### 5.8 Client and execution cycle

```java
BookmapClient client = BookmapClient.builder()
    .transport(transport)
    .charset(Charset.forName("IBM037"))   // default
    .maxCalls(1000)                        // default; safety limit
    .build();

GetCustomerOrders result = client.execute(request);   // returns the response object
```

Cycle:
1. The client finds the codec of the class. If there is no codec, it throws `MappingException`. The message tells you to examine the annotation processor configuration.
2. The client clears the `OUTPUT` collections.
3. `encodeRequest` → `transport.exchange(BookRequest)` → `readResponse` on the first call, `decodeResponse` on the next calls. Without subtypes, both fill the same object. With subtypes, the first response can give a new object of a subclass (see the subtypes spec, 4.8). The next calls use that object.
4. If the POJO is `Validatable`, the client calls `validate()`. An exception stops the execution and goes to the caller. Pagination does not continue.
5. If the POJO is `Continuable`, the client calls `hasContinuation()`. If the result is `true`, the client goes back to step 3.
6. If `hasContinuation()` is still true after `maxCalls` calls, the client throws `ContinuationLimitException`.

The client finds the codec by the exact class of the POJO. A subclass instance without its own `@BookOperation` causes "No codec", unless the subclass is in the `@BookSubTypes` list of its base class.

`BookmapClient` is immutable and thread-safe. The POJO is not thread-safe. It belongs to the caller.

**Short response.** If the response is shorter than the layout requires, the codec throws `MappingException`.
**Extra bytes.** The codec ignores extra bytes at the end of the response. A COMMAREA often has padding **[builder call]**.

### 5.9 Transport SPI

```java
public interface Transport {
    byte[] exchange(BookRequest request);
}

public record BookRequest(
    String operation,                 // logical operation name from @BookOperation
    byte[] payload,                   // encoded request
    int maxResponseLength,            // hint; a transport can ignore it
    Map<String, String> attributes    // optional, transport-specific; empty by default
) {}
```

- Each transport maps the operation name to its destination through its own configuration. Example for a future CICS transport: `GetCustomerOrders` → transaction `TX01`, program `PGMORD01`.
- If a transport has no mapping for an operation name, it throws `OperationNotFoundException`.
- The transport translates technical failures into the exceptions in 5.10. It must not throw middleware exceptions.

### 5.10 Exceptions (all unchecked)

```
BookmapException
├── MappingException              (layout, conversion, missing codec)
├── ContinuationLimitException
├── BusinessException             (base class for user exceptions in validate())
└── TransportException
    ├── RemoteAbendException      (abendCode: the remote program stopped abnormally)
    ├── TransportTimeoutException
    ├── ConnectionException
    └── OperationNotFoundException
```

### 5.11 Simulator

`SimulatorTransport implements Transport`:
- `register(String operation, SimulatedOperation handler)`. `SimulatedOperation` has one method: `byte[] handle(byte[] request)`. This is the byte level.
- `register(Class<T> type, SimulatedHandler<T> handler)`. This is the typed level. The simulator uses the codec: `decodeRequest`, then the handler (it sets the output fields), then `encodeResponse`. The key is the `@BookOperation` name.
- A simulated operation can throw `SimulatedAbend("ASRA")`. The simulator changes it to `RemoteAbendException`.
- A simulated operation can be slow. If it takes more time than the simulator timeout, the simulator interrupts it and throws `TransportTimeoutException`. The timeout is configurable.
- A `BookmapException` from a simulated operation (for example, a `ConnectionException` that a test throws on purpose) goes to the caller unchanged. The simulator wraps all other exceptions in `TransportException`.
- An operation that is not registered causes `OperationNotFoundException`.
- The simulator records the received calls. Tests can examine the number of calls and the bytes of each call.

Target test case: a simulated operation returns 120 orders in pages of 50, with a restart key.

### 5.12 Spring Boot integration

`bookmap-spring-boot` contains an auto-configuration. It creates a `BookmapClient` from a `Transport` bean in the context and from properties (`bookmap.charset`, `bookmap.max-calls`). It uses standard Spring Boot mechanisms (`@AutoConfiguration`, `@Bean`, `@ConfigurationProperties`, conditions). Thus, it is compatible with Spring AOT and native images. No Spring class goes into the core. A future transport auto-configuration must declare `@AutoConfiguration(before = BookmapAutoConfiguration.class)`, so that its `Transport` bean exists before the client condition runs. The property `bookmap.transport.type` selects the transport auto-configuration (see `2026-11-13-transport-autoconfig-design.md`).

## 6. Full example

```java
@BookOperation                                                       // name = "GetCustomerOrders"
public class GetCustomerOrders implements Continuable, Validatable {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)  private String customerId;
    @BookField(order = 20, pic = "X(12)")                     private String restartKey;   // INPUT_OUTPUT
    @BookField(order = 30, pic = "X(4)",  direction = OUTPUT) private String returnCode;
    @BookField(order = 40, pic = "X(60)", direction = OUTPUT) private String returnMessage;
    @BookField(order = 50, pic = "X",     direction = OUTPUT) private String moreData;     // 'Y' or 'N'
    @BookField(order = 60, pic = "9(3)")                      private int orderCount;      // OUTPUT: from the collection
    @BookCollection(order = 70, maxOccurs = 50)                                             // OUTPUT; dependsOn = orderCount
    private List<Order> orders;

    @Override
    public void validate() {
        if (!"0000".equals(returnCode)) {
            throw new OrderQueryException(returnCode, returnMessage);
        }
    }

    @Override
    public boolean hasContinuation() {
        return "Y".equals(moreData);   // restartKey is INPUT_OUTPUT: it goes back as received
    }

    // getters and setters (or Lombok)
}

public class Order {
    @BookField(order = 10, pic = "X(10)")            private String orderNumber;
    @BookField(order = 20, pic = "S9(13)V99 COMP-3") private BigDecimal totalAmount;
    @BookField(order = 30)                           private LocalDate orderDate;   // length 8 from yyyyMMdd
    // getters and setters (or Lombok)
}
```

The same `Order` without conventions: `@BookField(order = 20, length = 15, scale = 2, signed = true, usage = COMP3)` and `@BookField(order = 30, length = 8)`. Both forms give the same codec.

Request: `customerId` (10) + `restartKey` (12) = 22 bytes.
Response: `restartKey` (12) + `returnCode` (4) + `returnMessage` (60) + `moreData` (1) + `orderCount` (3) + `orderCount` × 26 bytes (10 + 8 + 8).

## 7. Decisions for the builder

- Gradle Kotlin DSL or Groovy DSL, version catalog, build conventions. Suggestion: Kotlin DSL and a version catalog.
- How the processor gets JavaPoet (shaded or processor path).
- Processor test strategy (for example, compile-testing).
- Test libraries (for example, JUnit 5 and AssertJ).
- Internal names of support classes and subpackages.
- The exact text of error messages. Each message must identify the class, the field, and the `order`.
- An optional simulator helper that loads fixed responses from files.
