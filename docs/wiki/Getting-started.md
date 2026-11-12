This page shows how to add BookMap to a build, write a first book, and send the first request.

## Requirements

- Java 21 or later.
- Gradle or Maven with annotation processing.

## Installation

Add the core library and the processor.
Replace `<version>` with the latest version on [Maven Central](https://central.sonatype.com/namespace/pt.dazz.bookmap):
![Maven Central](https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-core)

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

The group is `pt.dazz.bookmap`. Use the same version for all BookMap artifacts.

### Lombok

If you use Lombok, list Lombok before BookMap in `annotationProcessor`. This is the recommended order.
The other order also works. The processor tests run both orders.

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:<lombok-version>")
    annotationProcessor("org.projectlombok:lombok:<lombok-version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

See [Annotations reference](Annotations-reference#lombok) for the Lombok rules.

### Charsets and native image

Only single-byte charsets are permitted. The default charset is `IBM037` (EBCDIC).
Set the charset on the client: `BookmapClient.builder().charset(...)`.
Set a charset for one book with `@BookDefaults(charset = "...")`.

For a GraalVM native image, `bookmap-core` contains a `native-image.properties` file.
It sets `-H:+AddAllCharsets`. This configuration is included, but it is not tested yet.

## Write a book

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

At compile time, the processor generates the codec `GetCustomerOrders_BookCodec` in the same package.
It also registers the codec in `META-INF/services/pt.dazz.bookmap.spi.BookCodec`.

## Send the request

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

## The builder

| Method | Meaning | Default |
|---|---|---|
| `transport(Transport)` | The transport. See [Transports and simulator](Transports-and-simulator). | Mandatory. |
| `charset(Charset)` | The default charset. It must be single-byte. | `IBM037` |
| `maxCalls(int)` | The maximum number of calls for one execution. The value must be 1 or more. | `1000` |
| `codecRegistry(BookCodecRegistry)` | The codec registry. | `BookCodecRegistry.loadDefault()` (`ServiceLoader`) |

## Next steps

- Read the [Annotations reference](Annotations-reference).
- Test without a mainframe with the [simulator](Transports-and-simulator).
- Generate the classes from a copybook with the [Copybook generator](Copybook-generator).
