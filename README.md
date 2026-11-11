# bookmap

bookmap maps COBOL books (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection.

The framework needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.

## Modules

| Module | Purpose |
|---|---|
| `bookmap-core` | Annotations, `BookmapClient`, the transport SPI, exceptions, and the codec runtime. |
| `bookmap-processor` | The annotation processor. It generates one codec for each `@BookOperation` class. |
| `bookmap-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `bookmap-spring-boot` | Spring Boot 4.0.x auto-configuration for `BookmapClient`. |
| `bookmap-examples` | Working examples. This README uses them. |

## Gradle setup

Add the core library and the processor.

```kotlin
dependencies {
    implementation("pt.daaz.bookmap:bookmap-core:<version>")
    annotationProcessor("pt.daaz.bookmap:bookmap-processor:<version>")
}
```

The project is not published yet. It has no group and no version. The coordinates above are placeholders.
Inside this build, use `project(":bookmap-core")` and `project(":bookmap-processor")`.

If you use Lombok, list Lombok before bookmap in `annotationProcessor`. This is the recommended order.
The other order also works. The processor tests run both orders.

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:<version>")
    annotationProcessor("org.projectlombok:lombok:<version>")
    annotationProcessor("pt.daaz.bookmap:bookmap-processor:<version>")
}
```

## Charsets

Only single-byte charsets are permitted. The default charset is `IBM037` (EBCDIC).
Set the charset on the client: `BookmapClient.builder().charset(...)`.
Set a charset for one book with `@BookDefaults(charset = "...")`.

For a GraalVM native image, `bookmap-core` contains a `native-image.properties` file.
It sets `-H:+AddAllCharsets`. No action is necessary.

## Quick start

`GetCustomerOrders` is a book in convention style. It lists the orders of a customer.

```java
@BookOperation
public class GetCustomerOrders implements Continuable, Validatable {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookField(order = 20, pic = "X(12)")
    private String restartKey;

    @BookField(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @BookField(order = 40, pic = "X(60)", direction = OUTPUT)
    private String returnMessage;

    @BookField(order = 50, pic = "X", direction = OUTPUT)
    private String moreData;

    @BookField(order = 60, pic = "9(3)")
    private int orderCount;

    @BookCollection(order = 70, maxOccurs = 50)
    private List<Order> orders;

    // validate(), hasContinuation(), getters, and setters: see bookmap-examples
}
```

Each order is a class with `@BookField` fields.

```java
public class Order {

    @BookField(order = 10, pic = "X(10)")
    private String orderNumber;

    @BookField(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal totalAmount;

    @BookField(order = 30)
    private LocalDate orderDate;
}
```

Send the request:

```java
BookmapClient client = BookmapClient.builder()
    .transport(transport)
    .charset(Charset.forName("IBM037"))   // default
    .maxCalls(1000)                        // default; safety limit
    .build();

GetCustomerOrders request = new GetCustomerOrders();
request.setCustomerId("CUST000042");
request.setRestartKey("");
GetCustomerOrders result = client.execute(request);   // the same object, filled
```

`BookmapClient` is immutable and thread-safe. The request object is not thread-safe. It belongs to the caller.

## Annotations

### @BookOperation (class)

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The logical operation name. | The simple class name. |

### @BookField (field)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. Gaps are permitted. | Mandatory. |
| `pic` | A COBOL `PIC` string with an optional usage clause. It replaces `length`, `scale`, `signed`, and `usage`. | None. |
| `length` | Number of characters for text. Total number of digits for numbers (`S9(13)V99` is `length = 15`). | Mandatory without `pic`. |
| `direction` | `INPUT`, `OUTPUT`, or `INPUT_OUTPUT`. | `INPUT_OUTPUT`. A count field takes the direction of its collection. |
| `usage` | `DISPLAY`, `COMP3`, or `COMP`. | From `@BookDefaults`, else `DISPLAY`. |
| `scale` | Number of implied decimal places (`V99` is 2). | 0. |
| `signed` | `true` if the field has a sign (`S9`). | From `@BookDefaults`, else `false`. |
| `pattern` | Date or time format. | From `@BookDefaults`, else `yyyyMMdd`. |
| `converter` | A custom `FieldConverter` class. | None. |

A field needs `pic` or `length`. One exception exists: a date or time field with neither gets its length from the pattern.
If you set `pic`, do not set `length`, `scale`, `signed`, or `usage` on the same field. This is a compile error.

### @BookCollection (field of type `List<Item>`)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. | Mandatory. |
| `direction` | As in `@BookField`. | `OUTPUT`. |
| `dependsOn` | The name of the count field. | The member immediately before the collection. |
| `maxOccurs` | The maximum number of items in one call. | Mandatory. |

The item class has `@BookField` fields without `direction`. They use the direction of the collection.
The item class needs a public constructor without arguments.

### @BookDefaults (class or package)

Use `package-info.java` to set defaults for a package. The billing example does this.

```java
@BookDefaults(usage = Usage.COMP3, signed = true)
package pt.daaz.bookmap.examples.billing;
```

| Attribute | Meaning |
|---|---|
| `charset` | The charset name for the codec. It must be single-byte. Default: the client charset. |
| `usage` | The default usage of numeric fields without a usage in the `pic` and without an explicit `usage`. |
| `signed` | The default sign of numeric fields without `pic` and without an explicit `signed`. |
| `pattern` | The default date or time pattern. |

The priority is, from high to low:

1. An explicit attribute on the field.
2. `@BookDefaults` on the class that declares the field.
3. `@BookDefaults` on the package of that class.
4. The built-in default.

The `usage` and `signed` defaults apply only to numeric Java types. Text and date fields ignore them.

## Conventions

The framework uses few attributes. Six conventions reduce the work:

1. `@BookOperation` without a value uses the simple class name.
2. `pic` replaces `length`, `scale`, `signed`, and `usage`.
3. A date or time field without `length` and `pic` gets its length from the pattern.
4. Without `dependsOn`, the count field is the member immediately before the collection.
5. A collection is `OUTPUT` by default. The count field takes the direction of its collection. Other fields are `INPUT_OUTPUT`.
6. `@BookDefaults` sets defaults for a class or a package.

These attributes stay mandatory: `order`, `maxOccurs`, and the size of text and numeric fields.

### Convention style and explicit style

The convention style is the first choice. `GetCustomerOrders` uses it.
The explicit style sets `length`, `scale`, `signed`, `usage`, and `dependsOn` one by one.
Use it when you want no `pic` string. `UpdateShipment` uses it.

```java
@BookOperation("UpdateShipment")
public class UpdateShipment {

    @BookField(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @BookField(order = 20, length = 9, scale = 2, signed = true, usage = Usage.COMP3, direction = INPUT)
    private BigDecimal weightKg;

    @BookField(order = 30, length = 3, direction = INPUT)
    private int lineCount;

    @BookCollection(order = 40, direction = INPUT, dependsOn = "lineCount", maxOccurs = 20)
    private List<ShipmentLine> lines;

    @BookField(order = 50, length = 2, direction = OUTPUT)
    private String status;
}
```

The billing package shows `@BookDefaults` with a Lombok class. `amount` gets its sign from the `pic`.
`tax` gets `COMP3` and the sign from the package defaults.

```java
@Data
public class Invoice {

    @BookField(order = 10, pic = "X(10)")
    private String invoiceNumber;

    @BookField(order = 20, pic = "S9(7)V99")
    private BigDecimal amount;

    @BookField(order = 30, length = 7, scale = 2)
    private BigDecimal tax;
}
```

## PIC syntax

The processor parses `pic` at compile time. An incorrect or unsupported `pic` is a compile error.

- The prefix `PIC` or `PICTURE` and a final `.` are optional. Letters are not case-sensitive.
- Text: `X(n)` or a sequence of `X`.
- Number: optional `S`, integer digits `9(n)` or `999`, and optional `V` followed by decimal digits.
  Examples: `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`.
- Usage clause (optional, after the picture, with an optional `USAGE` or `USAGE IS`):

| Clause | Usage |
|---|---|
| `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | `COMP3` |
| `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY` | `COMP` |
| `DISPLAY` | `DISPLAY` |

Without a clause, the usage comes from `@BookDefaults`, else `DISPLAY`.

The byte size comes from the usage and the length:

- `DISPLAY`: `length` bytes.
- `COMP3`: `length / 2 + 1` bytes.
- `COMP`: 2 bytes for 1 to 4 digits, 4 bytes for 5 to 9 digits, 8 bytes for 10 to 18 digits.

## Type mapping

| Java type | COBOL definition |
|---|---|
| `String` | `PIC X(n)` |
| `int`, `Integer`, `long`, `Long` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP`; scale 0 |
| `BigDecimal` | Any numeric definition, with `scale` |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY`, with a `pattern` |
| Any type | Through a `converter` |

Date patterns use `DateTimeFormatter` (for example `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `HHmmss`).
The `length` must agree with the pattern. The formatters are strict and use `Locale.ROOT`.
An incorrect combination is a compile error (for example `String` with `COMP3`, or `int` with `scale > 0`).

Conversion rules:

- Text, write: the codec adds spaces on the right.
- Text, read: the codec changes low-values to spaces and removes the spaces on the right. An empty field becomes `""`, never `null`.
- Number or date, read: a field with only spaces or low-values becomes `null` for a wrapper type and `0` for a primitive type.
- Signed `DISPLAY` numbers use a trailing overpunch sign.
- Too large a value (text, digits, or a negative value in an unsigned field) throws `MappingException`. The codec never truncates.

Packed dates (for example `S9(7) COMP-3` in the format `0CYYDDD`) have no built-in support. Write a `FieldConverter` for them.
The converter class must be public and have a constructor without arguments. All threads share one instance, so it must be thread-safe.

## Collections and pagination

Fields after a collection are permitted. The codec calculates the offsets at runtime.

- Write: the codec sets the count field to the size of the list. A list larger than `maxOccurs` throws `MappingException`.
- Read: the codec reads the count field. A negative count, or a count larger than `maxOccurs`, throws `MappingException`.
- At the start of each `execute()`, the client clears the `OUTPUT` collections.
- On each page, the codec adds the decoded items to the list. The count field shows the last page.

Two interfaces control the execution cycle.

```java
public interface Validatable {
    void validate();          // throw an exception if the response shows an error
}

public interface Continuable {
    boolean hasContinuation(); // return true to call the operation again
}
```

The cycle for each call:

1. The client encodes the request, sends it with the transport, and decodes the response into the same object.
2. If the object is `Validatable`, the client calls `validate()`. An exception stops the execution.
3. If the object is `Continuable`, the client calls `hasContinuation()`. If the result is `true`, the client goes back to step 1.
4. If `hasContinuation()` is still true after `maxCalls` calls, the client throws `ContinuationLimitException`.

In `GetCustomerOrders`, `hasContinuation()` returns true when `moreData` is `"Y"`. The remote program sets the restart key in the same response.
The client does not reset other fields. Set the restart key to its start value before you reuse an object.

## Transports and the simulator

A transport implements one method:

```java
public interface Transport {
    byte[] exchange(BookRequest request);
}
```

`BookRequest` has the operation name, the payload, a hint for the maximum response length, and optional attributes.
Each transport maps the operation name to its destination. If a mapping does not exist, the transport throws `OperationNotFoundException`.
A transport must translate technical failures to the exceptions in the next section.

`SimulatorTransport` runs handlers in the same JVM. Use it in tests. The typed level uses the generated codec:

```java
SimulatorTransport simulator = SimulatorTransport.builder().build();
BookmapClient client = BookmapClient.builder().transport(simulator).build();

simulator.register(GetCustomerOrders.class, call -> {
    call.setOrders(page);
    call.setReturnCode("0000");
    call.setReturnMessage("OK");
    call.setMoreData("N");
});
```

- `register(String, SimulatedOperation)` works at the byte level.
- A handler can throw `SimulatedAbend("ASRA")`. The simulator changes it to `RemoteAbendException`.
- A handler that is slower than the timeout (`builder().timeout(Duration)`) causes `TransportTimeoutException`.
- A `BookmapException` from a handler goes to the caller unchanged. The simulator wraps other exceptions in `TransportException`.
- `receivedRequests()` returns the recorded calls.

## Spring Boot

`bookmap-spring-boot` supports Spring Boot 4.0.x. The auto-configuration creates a `BookmapClient` bean
when a `Transport` bean exists.

| Property | Default | Meaning |
|---|---|---|
| `bookmap.charset` | `IBM037` | The charset of the client. |
| `bookmap.max-calls` | `1000` | The maximum number of calls for one execution. |

A transport auto-configuration must run before the bookmap auto-configuration.
Declare it in this way:

```java
@AutoConfiguration(before = BookmapAutoConfiguration.class)
```

Then the `Transport` bean exists when the client condition runs. Spring does not enter the core module.

## Error handling

All exceptions are unchecked.

```
BookmapException
├── MappingException              (layout, conversion, missing codec)
├── ContinuationLimitException
├── BusinessException             (base class for your exceptions in validate())
└── TransportException
    ├── RemoteAbendException      (the remote program stopped abnormally)
    ├── TransportTimeoutException
    ├── ConnectionException
    └── OperationNotFoundException
```

Extend `BusinessException` for errors that the remote program reports in the response.
The example `OrderQueryException` does this. `GetCustomerOrders.validate()` throws it when the return code is not `"0000"`.

If the client finds no codec for a class, it throws `MappingException`. Check the annotation processor configuration.
The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec.
If a response is shorter than the layout, the codec throws `MappingException`. The codec ignores extra bytes at the end.

## Known limitations

- No `REDEFINES`.
- No nested collections.
- No `COMP-1` or `COMP-2`.
- Single-byte charsets only.
- Packed dates need a converter.
- Edited pictures (`Z`, `*`, `,`, `+`, `-`, `CR`, `DB`) are rejected at compile time.
