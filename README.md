<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/bookmap-logo-dark.svg">
    <img src="docs/assets/bookmap-logo-light.svg" alt="BookMap" width="380">
  </picture>
</p>

<p align="center">
  <strong>Map COBOL books to Java objects. No reflection. No middleware lock-in.</strong>
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-0f62fe">
  <img alt="Gradle 9" src="https://img.shields.io/badge/Gradle-9.9-02303A">
  <img alt="Coverage 100%" src="https://img.shields.io/badge/coverage-100%25-brightgreen">
  <img alt="Version 0.1.0-SNAPSHOT" src="https://img.shields.io/badge/version-0.1.0--SNAPSHOT-f89820">
  <a href="LICENSE"><img alt="License Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-blue"></a>
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> ·
  <a href="#installation">Installation</a> ·
  <a href="#reference">Reference</a> ·
  <a href="#copybook-generator">Copybook generator</a> ·
  <a href="#transports-and-the-simulator">Transports</a> ·
  <a href="#spring-boot">Spring Boot</a>
</p>

---

BookMap maps COBOL books (COMMAREA layouts) to Java classes. You add annotations to a class.
A compile-time annotation processor generates the codec. The runtime does not use reflection.

BookMap needs Java 21. The core and processor modules have no dependency on Spring or on any middleware library.

## Why BookMap

- **No reflection.** The processor generates the codec at compile time. The runtime does not use reflection on your classes.
- **Errors at compile time.** An incorrect layout, `pic`, or type combination stops the build, not production.
- **Few annotations.** Conventions and `pic` strings keep each field to one line, close to the copybook.
- **Automatic pagination.** The client calls the program again until `hasContinuation()` returns `false`, and returns the full list.
- **No middleware lock-in.** A transport is one interface. The simulator lets you test without a mainframe.
- **No silent data loss.** A value that does not fit causes an exception. The codec never truncates or rounds.
- **Classes from copybooks.** The copybook generator writes the annotated classes from a COBOL copybook.

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
GetCustomerOrders result = client.execute(request);   // the response object
```

`BookmapClient` is immutable and thread-safe. The request object is not thread-safe. It belongs to the caller.

## Installation

Add the core library and the processor.

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:0.1.0-SNAPSHOT")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:0.1.0-SNAPSHOT")
}
```

The group is `pt.dazz.bookmap` and the version is `0.1.0-SNAPSHOT`. The project is not published yet.
Inside this build, use `project(":bookmap-core")` and `project(":bookmap-processor")`.

If you use Lombok, list Lombok before BookMap in `annotationProcessor`. This is the recommended order.
The other order also works. The processor tests run both orders.
The `bookmap-examples` build lists BookMap first on purpose, because that order is more difficult for the processor.

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:<version>")
    annotationProcessor("org.projectlombok:lombok:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:0.1.0-SNAPSHOT")
}
```

### Charsets and native image

Only single-byte charsets are permitted. The default charset is `IBM037` (EBCDIC).
Set the charset on the client: `BookmapClient.builder().charset(...)`.
Set a charset for one book with `@BookDefaults(charset = "...")`.

For a GraalVM native image, `bookmap-core` contains a `native-image.properties` file.
It sets `-H:+AddAllCharsets`. This configuration is included, but it is not tested yet.

## Modules

| Module | Purpose |
|---|---|
| `bookmap-core` | Annotations, `BookmapClient`, the transport SPI, exceptions, and the codec runtime. |
| `bookmap-processor` | The annotation processor. It generates one codec for each `@BookOperation` class. |
| `bookmap-transport-simulator` | A transport for tests. It runs handlers in the same JVM. |
| `bookmap-spring-boot` | Spring Boot 4.0.x auto-configuration for `BookmapClient`. |
| `bookmap-copybook` | The copybook generator library: parser, model, JSON form, and Java generator. |
| `bookmap-copybook-cli` | The command line of the copybook generator. |
| `bookmap-copybook-gradle-plugin` | The Gradle plugin of the copybook generator (`pt.dazz.bookmap.copybook`). |
| `bookmap-examples` | Working examples. This README uses them. |
| `bookmap-examples-gradle-plugin` | A standalone build that uses the Gradle plugin. |

## Reference

### Annotations

#### @BookOperation (class)

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The logical operation name. | The simple class name. |

#### @BookField (field)

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
Do not set `signed` or `scale` on a `String` or a date and time field. This is a compile error.
An operation class and an item class need at least one annotated field.

#### @BookCollection (field of type `List<Item>`)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. | Mandatory. |
| `direction` | As in `@BookField`. | `OUTPUT`. |
| `dependsOn` | The name of the count field. | Variable collection: the member immediately before the collection. Fixed collection: no count field. |
| `fixed` | `true` for a fixed table (`OCCURS n` without `DEPENDING ON`). See [Fixed collections](#fixed-collections). | `false` |
| `maxOccurs` | The maximum number of items in one call. | Mandatory. |

The item class has `@BookField` fields without `direction`. They use the direction of the collection.
The item class needs a public constructor without arguments.
The digits of the count field must hold `maxOccurs`. For example, `9(1)` with `maxOccurs = 50` is a compile error.

#### Lombok

The processor finds the Lombok annotations by name. It does not depend on Lombok.

- `@Data`, `@Getter`, and `@Setter` give the accessors. `AccessLevel.PRIVATE` and `AccessLevel.NONE` give no accessor.
- `AccessLevel.PACKAGE`, `AccessLevel.MODULE`, and `AccessLevel.PROTECTED` give an accessor only in the package of the operation class. The codec is in that package. For a superclass or an item class in another package, use public accessors.
- `@AllArgsConstructor`, `@RequiredArgsConstructor`, and `@Builder` remove the default constructor. Add `@NoArgsConstructor` or a public constructor without arguments to the class.
- `@Data` on a class with a `final` or `@NonNull` field without a value also removes the default constructor. Add `@NoArgsConstructor` to such a class (`@NoArgsConstructor(force = true)` for a `final` field). If BookMap runs before Lombok, the processor cannot detect this case, and javac reports the error in the generated codec.
- `@NoArgsConstructor` must be public and must not have a `staticName`.

#### @BookDefaults (class or package)

Use `package-info.java` to set defaults for a package. The billing example does this.

```java
@BookDefaults(usage = Usage.COMP3, signed = true)
package pt.dazz.bookmap.examples.billing;
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

### Conventions

The framework uses few attributes. Six conventions reduce the work:

1. `@BookOperation` without a value uses the simple class name.
2. `pic` replaces `length`, `scale`, `signed`, and `usage`.
3. A date or time field without `length` and `pic` gets its length from the pattern.
4. Without `dependsOn`, the count field of a variable collection is the member immediately before it. A fixed collection without `dependsOn` has no count field.
5. A collection is `OUTPUT` by default. The count field takes the direction of its collection. Other fields are `INPUT_OUTPUT`.
6. `@BookDefaults` sets defaults for a class or a package.

These attributes stay mandatory: `order`, `maxOccurs`, and the size of text and numeric fields.

#### Convention style and explicit style

The convention style is the first choice. `GetCustomerOrders` uses it.
The explicit style sets `length`, `scale`, `signed`, `usage`, and `dependsOn` one by one.
Use it when you want no `pic` string. `UpdateShipment` uses it.

```java
@BookOperation("UpdateShipment")
public class UpdateShipment {

    @Getter
    @Setter
    @BookField(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @BookField(order = 20, length = 9, scale = 2, signed = true, usage = Usage.COMP3, direction = INPUT)
    private BigDecimal weightKg;

    @Getter
    @Setter
    @BookField(order = 30, length = 3, direction = INPUT)
    private int lineCount;

    @BookCollection(order = 40, direction = INPUT, dependsOn = "lineCount", maxOccurs = 20)
    private List<ShipmentLine> lines;

    @BookField(order = 50, length = 2, direction = OUTPUT)
    private String status;

    // The other getters and setters are handwritten: see bookmap-examples
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

### PIC syntax

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

### Type mapping

| Java type | COBOL definition |
|---|---|
| `String` | `PIC X(n)` |
| `int`, `Integer`, `long`, `Long` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP`; scale 0 |
| `BigDecimal` | Any numeric definition, with `scale` |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY`, with a `pattern` |
| Any type | Through a `converter` |

Date patterns use `DateTimeFormatter` (for example `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `HHmmss`).
The `length` must agree with the pattern. The formatters are strict and use `Locale.ROOT`.
A two-digit year (`yy`) reads as a year from 2000 to 2099.
On write, the codec reads the text again and compares it with the value.
If the pattern cannot keep the value, the codec throws `MappingException`.
Examples: the year 1975 with `yyMMdd`, or a time with seconds or nanoseconds with `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.
An incorrect combination is a compile error (for example `String` with `COMP3`, or `int` with `scale > 0`).

Conversion rules:

- Text, write: the codec adds spaces on the right.
- Text, read: the codec changes low-values to spaces and removes the spaces on the right. An empty field becomes `""`, never `null`.
- Read: a byte that the charset cannot decode throws `MappingException`. The codec never puts a replacement character in a value.
- Number or date, read: a field with only spaces or low-values becomes `null` for a wrapper type and `0` for a primitive type.
- Signed `DISPLAY` numbers use a trailing overpunch sign.
- Too large a value (text, digits, or a negative value in an unsigned field) throws `MappingException`. The codec never truncates.

Packed dates (for example `S9(7) COMP-3` in the format `0CYYDDD`) have no built-in support. Write a `FieldConverter` for them.
The converter class must be public and have a constructor without arguments. All threads share one instance, so it must be thread-safe.

### Collections and pagination

Fields after a collection are permitted. The codec calculates the offsets at runtime.

- Write: the codec sets the count field to the size of the list. A `null` list writes the count 0. A list larger than `maxOccurs` throws `MappingException`.
- Read: the codec reads the count field. A negative count, or a count larger than `maxOccurs`, throws `MappingException`.
- At the start of each `execute()`, the client sets each `OUTPUT` collection to a new, empty `ArrayList`.
- On each page, the codec makes a new `ArrayList` with the existing items and the decoded items. Then it calls the setter. A `null` list starts as an empty `ArrayList`. The count field shows the last page.
- The client does not reset an `INPUT_OUTPUT` collection. On read, the codec replaces it with a new `ArrayList` of the decoded items.
- An `INPUT_OUTPUT` collection in a `Continuable` class is a compile error.

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

1. The client encodes the request, sends it with the transport, and decodes the response into the object. Without subtypes, this is the request object. See [Subtypes](#subtypes-redefines).
2. If the object is `Validatable`, the client calls `validate()`. An exception stops the execution.
3. If the object is `Continuable`, the client calls `hasContinuation()`. If the result is `true`, the client goes back to step 1.
4. If `hasContinuation()` is still true after `maxCalls` calls, the client throws `ContinuationLimitException`.

In `GetCustomerOrders`, `hasContinuation()` returns true when `moreData` is `"Y"`. The remote program sets the restart key in the same response.
The client does not reset other fields. Set the restart key to its start value before you reuse an object.

### Fixed collections

A `@BookCollection` without `fixed` works like `OCCURS DEPENDING ON`: the book size changes with the number of items.
Many books use a fixed table (`OCCURS n` without `DEPENDING ON`) instead. The area always holds `n` items, and the fields after it have a fixed offset:

```cobol
01  GET-ORDER.
    05  ORDER-ID          PIC X(10).
    05  RESTART-KEY       PIC X(4).
    05  MORE-DATA         PIC X.
    05  ORDER-LINE OCCURS 3.
        10  PRODUCT-CODE  PIC X(8).
        10  QUANTITY      PIC 9(5).
        10  UNIT-PRICE    PIC S9(7)V99 COMP-3.
    05  ORDER-TOTAL       PIC S9(9)V99 COMP-3.
```

Set `fixed = true` for this table:

```java
@BookCollection(order = 40, maxOccurs = 3, fixed = true)
private List<OrderLine> lines;

@BookField(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
private BigDecimal orderTotal;
```

The program tells the caller the number of used items in one of two ways. BookMap supports both.

**LOW-VALUES mode** (no `dependsOn`). The unused slots contain LOW-VALUES (bytes `0x00`).

- Write: the codec writes the items, then LOW-VALUES in the unused slots.
- Read: a slot with only `0x00` bytes is empty. The list ends at the last slot with data. The check does not use the charset.
- An empty slot before a slot with data causes a `MappingException`.
- An item that encodes to only `0x00` bytes causes a `MappingException` on write. A read cannot find such an item.

**Count mode** (`dependsOn` set). A count field before the table holds the number of used items:

```java
@BookCollection(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
private List<ShipmentSummary> shipments;
```

- Write: the codec sets the count field to the list size. Then it writes the items and LOW-VALUES in the unused slots.
- Read: the codec reads the number of items that the count field gives. It ignores the other slots.

Do not rely on convention 4 for a fixed collection. The copybook does not tell which field is the count. A wrong count field gives wrong lists without an error.

Rules for both modes:

- The response must contain the full area. A response that ends in the area causes a `MappingException`.
- Pagination, the reset of `OUTPUT` collections, and items with subtypes work as for a variable collection.
- The full examples are `GetOrder` and `ListShipments` in `bookmap-examples`, package `pt.dazz.bookmap.examples.orders`.

### Subtypes (REDEFINES)

A COBOL book can use `REDEFINES` to give one area more than one layout. A field in the book (the discriminator) tells which layout is valid:

```cobol
01  GET-ACCOUNT.
    05  ACCOUNT-ID        PIC X(12).
    05  RESTART-KEY       PIC X(10).
    05  MORE-DATA         PIC X.
    05  ACCOUNT-TYPE      PIC 9(2).
    05  CHECKING-DATA.
        10  OVERDRAFT-LIMIT   PIC S9(9)V99 COMP-3.
        10  ENTRY-COUNT       PIC 9(2).
        10  ENTRIES OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.
            ...
    05  SAVINGS-DATA REDEFINES CHECKING-DATA.
        10  INTEREST-RATE     PIC 9V9(4).
        10  MATURITY-DATE     PIC 9(8).
```

Map each layout to a subclass. Put `@BookSubTypes` on the base class. The base class declares the common fields and the discriminator:

```java
@BookOperation
@BookSubTypes(discriminator = "accountType", value = {
    @BookSubTypes.Type(value = "01", type = CheckingAccount.class),
    @BookSubTypes.Type(value = "02", type = SavingsAccount.class)
})
public class GetAccount implements Continuable {
    @BookField(order = 10, pic = "X(12)", direction = INPUT) private String accountId;
    @BookField(order = 20, pic = "X(10)")                    private String restartKey;
    @BookField(order = 30, pic = "X", direction = OUTPUT)    private String moreData;
    @BookField(order = 40, pic = "9(2)")                     private int accountType;
}

public class CheckingAccount extends GetAccount {
    @BookField(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT) private BigDecimal overdraftLimit;
    @BookField(order = 60, pic = "9(2)")                                 private int entryCount;
    @BookCollection(order = 70, maxOccurs = 10)                          private List<StatementEntry> entries;
}

public class SavingsAccount extends GetAccount {
    @BookField(order = 50, pic = "9V9(4)", direction = OUTPUT) private BigDecimal interestRate;
    @BookField(order = 60, direction = OUTPUT)                 private LocalDate maturityDate;
}
```

Send the base class. The client gives the subclass that the response selects:

```java
GetAccount request = new GetAccount();
request.setAccountId("ACC-0000001");
request.setRestartKey("");
GetAccount result = client.execute(request);   // a CheckingAccount or a SavingsAccount
```

The same annotation goes on the item class of a collection. Then each item can have a different subclass:

```java
@BookSubTypes(discriminator = "entryType", value = {
    @BookSubTypes.Type(value = "C", type = CardPayment.class),
    @BookSubTypes.Type(value = "T", type = Transfer.class)
})
public abstract class StatementEntry {
    @BookField(order = 10, pic = "X")                private String entryType;
    @BookField(order = 20, pic = "S9(13)V99 COMP-3") private BigDecimal amount;
}
```

The full example is in `bookmap-examples`, package `pt.dazz.bookmap.examples.accounts`.

In a test, use `registerResponder` to answer with a subclass:

```java
simulator.registerResponder(GetAccount.class, call -> {
    SavingsAccount account = new SavingsAccount();
    account.setInterestRate(new BigDecimal("0.0325"));
    account.setMoreData("N");
    return account;
});
```

| Attribute | Meaning |
|---|---|
| `discriminator` | The name of the discriminator field. The base class or one of its superclasses declares it. |
| `value` | The list of `@BookSubTypes.Type`. |
| `Type.value` | The discriminator value of the subclass, as text. |
| `Type.type` | The subclass. |

Rules:

- The discriminator is `String`, `int`, `Integer`, `long`, or `Long`. It has no converter.
- For a numeric discriminator, `"001"` and `"1"` are the same value. For a text discriminator, the spaces on the right have no effect.
- In an operation class, the discriminator is `OUTPUT` or `INPUT_OUTPUT`. An abstract base class needs an `INPUT_OUTPUT` discriminator.
- Each member of a subclass must have an `order` greater than all members of the base class. Thus, the redefined area is the end of the layout. Two subclasses can use the same `order` values.
- A collection in a subclass needs a count field in the same subclass.
- The subclasses do not have `@BookOperation`. They use the operation name of the base class.
- The base class can be abstract. Then the caller always sends a subclass.
- The base class can be in the list with its own value.

Behavior:

- Write: for a subclass in the list, the codec sets the discriminator to the value of the subclass. Then it writes the layout of the subclass.
- Read: the codec reads the discriminator and selects the subclass. An unknown value throws `MappingException`. The message contains the value and the known values.
- Class change: if the first response selects a subclass of the request class, the client creates the subclass. The client copies the annotated members of the base class from the request to the new object. Then it decodes the response into the new object. Fields without an annotation are not copied.
- The next pages use the object of the first response. A later page that selects a different class throws `MappingException`.
- If the request is a subclass and the response selects a different subclass, the client throws `MappingException`.
- Items: each item occupies the length of the longest subclass. The codec writes spaces at the end of a shorter item.
- `maxResponseLength()` is the length of the longest layout.

Compile errors:

- The discriminator does not exist in the base class, has a type or converter that the rules do not permit, has direction `INPUT`, or comes after a variable collection. A discriminator after a fixed collection is permitted.
- An empty list, a type that is not an instantiable subclass of the base class, or a type in the list two times.
- A blank value, a value that is used two times, a value that is not a number for a numeric field, or a value that does not fit in the field.
- `@BookSubTypes` on a subclass (subtypes in a subtype), `@BookOperation` on a subclass, or a subclass that extends another subclass in the list.
- A subclass member with an `order` that is not greater than all base members, or a collection in a subclass with a count field in the base class.

### Error handling

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
The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec, unless it is in the `@BookSubTypes` list of its base class.
If a response is shorter than the layout, the codec throws `MappingException`. The codec ignores extra bytes at the end.

## Copybook generator

The copybook generator reads a COBOL copybook and writes the annotated Java classes.
The classes compile with the BookMap processor without a change.
Use it in two ways:

- **Scaffold.** Generate the classes one time. Then keep them by hand.
- **Build.** Generate the classes again from the copybook in each build. Use the [Gradle plugin](#gradle-plugin). `bookmap-examples` does this with the command line.

### Pipeline

```
copybook ──parse──▶ AST ──map──▶ model (JSON) ──generate──▶ Java classes
```

1. **Parse.** The parser reads the copybook and expands `COPY ... REPLACING`. It gives a tree of COBOL data items.
2. **Map.** The mapper applies the BookMap rules: names, Java types, `pic`, `order`, collections, directions, and subtypes. It gives the model.
3. **Generate.** The generator writes one Java class for each class of the model, with JavaPoet.

The model has a stable JSON form with a format version. The same copybook and options always give the same JSON and the same Java.
The generator does not keep old models and does not compare them. Other tools can keep the JSON files and compare them to find changes in a copybook.

### Command line

Run the executable JAR from Maven Central (`bookmap-copybook-cli-<version>-all.jar`) or from the GitHub release:

```bash
java -jar bookmap-copybook-cli-all.jar generate ORDERS.cpy --package com.example.orders --output-dir src/main/java
```

| Command | Result |
|---|---|
| `parse <copybook> --output <model.json>` | Writes the model JSON. |
| `generate <copybook> --package <name> --output-dir <dir>` | Writes the Java classes. Existing files are replaced. |
| `generate <model.json> --package <name> --output-dir <dir>` | Writes the Java classes of a model JSON file. |

| Option | Meaning |
|---|---|
| `--format fixed\|free` | The reference format. Default: `fixed` (columns 7 to 72). |
| `--charset <name>` | The charset of the copybook files. Default: UTF-8. |
| `--copy-dir <dir>` | A directory with `COPY` members. Repeat the option for more directories. |
| `--record-name <name>` | The record name for a copybook without level `01`. Default: the file name. |
| `--strip-prefix <prefix>` | A prefix to remove from names, for example `WS-`. |
| `--input-marker <word>`, `--output-marker <word>` | A name part that marks an input or an output alternative. |
| `--direction <name>=<direction>` | The direction of a `REDEFINES` alternative. |
| `--lombok` | Write Lombok annotations instead of getters and setters. |
| `--config <file.json>` | A file with all options. A command-line option has priority. |

Exit codes: `0` success (warnings are permitted), `1` a diagnostic with an error, `2` an incorrect command line.
Each diagnostic has the format `<file>:<line>:<column>: <severity>: <message>`.
The generator reports all problems of a copybook before it stops.

A configuration file holds all options, and the subtype decisions:

```json
{
  "format": "fixed",
  "copyDirs": ["copylib"],
  "stripPrefixes": ["WS-"],
  "directions": { "INVOICE-QUERY": "INPUT", "INVOICE-DETAIL": "OUTPUT" },
  "subTypes": {
    "CHECKING-DATA": {
      "discriminator": "ACCOUNT-TYPE",
      "values": { "CHECKING-DATA": "01", "SAVINGS-DATA": "02" },
      "classNames": { "SAVINGS-DATA": "SavingsAccount" }
    }
  },
  "package": "com.example.accounts",
  "lombok": false
}
```

Relative copy directories start at the directory of the configuration file.
`inputMarkers` and `outputMarkers` in the file replace the default lists.
A name in an option can be qualified, for example `CITY OF BILLING-ADDRESS`. A name that matches more than one item is an error.

The library has the same steps as a Java API. The entry point is `pt.dazz.bookmap.copybook.Copybooks`.

### Gradle plugin

The plugin `pt.dazz.bookmap.copybook` generates the classes of a directory tree of copybooks in each build, before `compileJava`.
The directories of the tree give the subpackages. A file `<NAME>.json` next to `<NAME>.cpy` holds the options of that copybook.
The plugin is on Maven Central only. Add `mavenCentral()` to the plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("pt.dazz.bookmap.copybook") version "<version>"
}

bookmap {
    copybooks {
        packageName = "com.example.mainframe"   // src/main/copybook/orders/ORDER.cpy -> com.example.mainframe.orders
        copyDirs.from("src/main/copylib")       // the COPY members
    }
}
```

Declare the directories of the `COPY` members in `copyDirs`. The plugin does not search the tree for them.
For all options, the package rules, the class name collisions, and the location of the `COPY` members, see the [plugin README](bookmap-copybook-gradle-plugin/README.md).

### Mapping

| COBOL | Java |
|---|---|
| Level `01` | A class with `@BookOperation`. |
| A group without `OCCURS` | No class. Its items go into the class of the parent. |
| `OCCURS n` | `@BookCollection(fixed = true)` and an item class. |
| `OCCURS m TO n DEPENDING ON X` | `@BookCollection(dependsOn = "x")` and an item class. |
| An elementary item with `OCCURS` | An item class with one field `value`. |
| An elementary item | A `@BookField` with `pic`, for example `pic = "S9(7)V99 COMP-3"`. |
| `FILLER` | A field `filler1`, `filler2`, ... |
| `REDEFINES` of an elementary item | The first definition. The other item is skipped, with a warning. |
| `REDEFINES` of a group | A direction split or subtypes. See the next section. |
| Level `88`, `VALUE` | Ignored. |

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9(n)` with 1 to 9 digits | `int` |
| `9(n)` with 10 to 18 digits | `long` |
| Decimals, or more than 18 digits | `BigDecimal` |

Names: `GET-CUSTOMER-ORDERS` gives the class `GetCustomerOrders`, and `ORDER-AMOUNT` gives the field `orderAmount`.
A Java keyword gets the suffix `Value`. If two items in one class have the same name, the second name starts with its group name.
The `order` values are 10, 20, 30, ... in each class. Each field has a Javadoc comment with its COBOL declaration.

A fixed `OCCURS` has no `dependsOn`, because the copybook does not tell which field is the count.
Add `dependsOn` by hand if the program has a count field. See [Fixed collections](#fixed-collections).

### Directions

The copybook does not give the direction of a field. Thus, all fields are `INPUT_OUTPUT`, with one exception: the alternatives of a `REDEFINES` area.

In BookMap, an `INPUT` field does not occupy bytes in the output area.
Outside a `REDEFINES` area, a direction would move the offsets of the fields after it. The offsets would not agree with COBOL.
Inside a `REDEFINES` area, the input alternative and the output alternative share the same bytes. This is a **direction split**:

```cobol
01  GET-INVOICE.
    05  RETURN-CODE          PIC X(4).
    05  INVOICE-REQUEST.
        10  INVOICE-ID       PIC X(12).
    05  INVOICE-RESPONSE REDEFINES INVOICE-REQUEST.
        10  INVOICE-AMOUNT   PIC S9(7)V99 COMP-3.
        10  DUE-DATE         PIC 9(8).
    05  TRAILER              PIC X(10).
```

`invoiceId` is `INPUT`. `invoiceAmount` and `dueDate` are `OUTPUT`. `returnCode` and `trailer` are `INPUT_OUTPUT`.
The request alternative is 1 byte shorter. The generator adds a 1-byte `INPUT` filler, so `trailer` has the COBOL offset in both directions.

The generator finds the direction of an alternative in this order:

1. A direction decision (`--direction` or `directions`).
2. A marker in the group name. The default input markers are `INPUT`, `IN`, `REQUEST`, and `REQ`. The default output markers are `OUTPUT`, `OUT`, `RESPONSE`, and `RESP`.
3. A subtype decision makes subtypes instead (next section).

If none applies, the generator does not guess. It reports an error with an example of each decision.

### Subtypes

A subtype decision maps a `REDEFINES` area to [subtypes](#subtypes-redefines). It gives the discriminator and the value of each alternative.
The class that holds the area gets `@BookSubTypes`. Each alternative becomes a subclass.

### Limitations of the generator

- The generator does not detect dates. A `PIC 9(8)` date stays an `int`.
- The generated operation classes do not implement `Continuable` or `Validatable`. In scaffold mode, add them by hand.
- A fixed `OCCURS` of `COMP` items without `dependsOn` cannot write the value 0: its bytes are LOW-VALUES. The generator gives a note.
- These items are errors: level `66` and `77`, `SIGN LEADING`, `SEPARATE`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL`, edited pictures, `P`, `A`, `N`, `REPLACE`, an `OCCURS` in an `OCCURS`, and a `REDEFINES` area in another one.
- No Maven plugin yet. Use the Gradle plugin, the command line, or the library.

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
- `registerResponder(Class, SimulatedResponder)` lets a handler return a different object, for example a subclass of a class with `@BookSubTypes`. The simulator encodes the layout of the returned object.
- For a class with subtypes, register the base class. Registration with a subclass throws `IllegalArgumentException`.
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

A transport auto-configuration must run before the BookMap auto-configuration.
Declare it in this way:

```java
@AutoConfiguration(before = BookmapAutoConfiguration.class)
```

Then the `Transport` bean exists when the client condition runs. The core module has no Spring dependency.

## Known limitations

- No `REDEFINES` without a discriminator. See [Subtypes](#subtypes-redefines).
- No subtypes in a subtype, and no base fields after the redefined area.
- No nested collections.
- A fixed collection detects empty slots only through LOW-VALUES or a count field.
- No `COMP-1` or `COMP-2`.
- Single-byte charsets only.
- Packed dates need a converter.
- Edited pictures (`Z`, `*`, `,`, `+`, `-`, `CR`, `DB`) are rejected at compile time.

## Project status

BookMap is at version `0.1.0-SNAPSHOT`. GitHub Actions builds each release and publishes it to Maven Central.
See [RELEASING.md](RELEASING.md) for the release cycle and the one-time setup.
The build enforces 100% line and branch coverage for all modules except the examples.
Real transports (CICS Transaction Gateway, IBM MQ, HTTP) are planned. The transport SPI accepts them without a change to the core.

## License

BookMap is licensed under the [Apache License, Version 2.0](LICENSE).
See the [NOTICE](NOTICE) file for the copyright notice.
