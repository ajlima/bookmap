This page shows the full path from an empty project to the first call to a COBOL program:

1. Add BookMap to the build (Gradle or Maven).
2. Map a COBOL book to a Java class.
3. Build the project. The processor generates the codec.
4. Test the class with the simulator.
5. Connect the client to the mainframe with a transport.

## Requirements

- Java 21 or later.
- Gradle, or Maven 3.6.3 or later.
- Annotation processing on in the compiler. Gradle and Maven turn it on when you declare the processor.

## Choose the modules

All modules have the group `pt.dazz.bookmap`. Use the same version for all BookMap modules.
Replace `<version>` with the latest version: ![Maven Central](https://img.shields.io/maven-central/v/pt.dazz.bookmap/bookmap-core)

| Module | Scope | Use it for |
|---|---|---|
| `bookmap-core` | Compile and runtime | The annotations, `BookmapClient`, the transport SPI, and the exceptions. Always necessary. |
| `bookmap-processor` | Annotation processor only | The generation of the codecs at compile time. Always necessary. Do not put it on the runtime classpath. |
| `bookmap-transport-simulator` | Test | The tests without a mainframe. |
| `bookmap-transport-mq` | Compile and runtime | The calls through IBM MQ request and reply queues. See [IBM MQ](IBM-MQ). |
| `bookmap-spring-boot` | Compile and runtime | The auto-configuration of `BookmapClient` in Spring Boot 4 (built and tested with 4.1.2). It includes `bookmap-core`. See [Spring Boot](Spring-Boot). |

To generate the classes from copybooks, see [Copybook generator](Copybook-generator).

## Installation with Gradle

Add the modules to `build.gradle.kts`:

```kotlin
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")

    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator:<version>")
}
```

The generated codecs are in `build/generated/sources/annotationProcessor/java/main`.

### Lombok with Gradle

List Lombok before BookMap in `annotationProcessor`. This is the recommended order.
The other order also works. The processor tests run both orders.

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:<lombok-version>")
    annotationProcessor("org.projectlombok:lombok:<lombok-version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

## Installation with Maven

Add the modules and the processor to `pom.xml`. Replace `x.y.z` with the version.
The processor goes in `annotationProcessorPaths` of the `maven-compiler-plugin`, not in `<dependencies>`.

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <bookmap.version>x.y.z</bookmap.version>   <!-- the latest BookMap version -->
</properties>

<dependencies>
  <dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-core</artifactId>
    <version>${bookmap.version}</version>
  </dependency>
  <dependency>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-transport-simulator</artifactId>
    <version>${bookmap.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.13.0</version>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>pt.dazz.bookmap</groupId>
            <artifactId>bookmap-processor</artifactId>
            <version>${bookmap.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

The generated codecs are in `target/generated-sources/annotations`.

**CAUTION:** With `annotationProcessorPaths`, the compiler runs only the processors in that list. Add each processor of the module to the list, for example Lombok or MapStruct. Otherwise, the other processors do not run.

### Lombok with Maven

Put Lombok before BookMap in `annotationProcessorPaths`. Add Lombok also as a `provided` dependency.

```xml
<dependencies>
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>x.y.z</version>   <!-- the Lombok version -->
    <scope>provided</scope>
  </dependency>
</dependencies>
```

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>x.y.z</version>   <!-- the Lombok version -->
  </path>
  <path>
    <groupId>pt.dazz.bookmap</groupId>
    <artifactId>bookmap-processor</artifactId>
    <version>${bookmap.version}</version>
  </path>
</annotationProcessorPaths>
```

See [Annotations reference](Annotations-reference#lombok) for the Lombok rules.

## Map a book

A book is the layout of the data that a COBOL program reads and writes (for example, a COMMAREA).
In this example, the program `GetCustomerOrders` gets the orders of a customer. It sends at most 50 orders on each call.

One Java class holds the request and the response. The `direction` of each field tells where the field is:

- An `INPUT` field is only in the request.
- An `OUTPUT` field is only in the response.
- An `INPUT_OUTPUT` field (the default) is in the request and in the response.

| Order | Field | COBOL definition | Direction | Request bytes | Response bytes |
|---|---|---|---|---|---|
| 10 | `customerId` | `PIC X(10)` | `INPUT` | 10 | 0 |
| 20 | `restartKey` | `PIC X(12)` | `INPUT_OUTPUT` | 12 | 12 |
| 30 | `returnCode` | `PIC X(4)` | `OUTPUT` | 0 | 4 |
| 40 | `returnMessage` | `PIC X(60)` | `OUTPUT` | 0 | 60 |
| 50 | `moreData` | `PIC X` | `OUTPUT` | 0 | 1 |
| 60 | `orderCount` | `PIC 9(3)` | `OUTPUT` (from its collection) | 0 | 3 |
| 70 | `orders` | `OCCURS 0 TO 50 DEPENDING ON ORDER-COUNT` | `OUTPUT` | 0 | 26 for each order |

Thus, the request has 22 bytes. The response has 80 bytes plus 26 bytes for each order.

The class in convention style:

```java
import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

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

    @Override
    public void validate() {
        if (!"0000".equals(returnCode)) {
            throw new OrderQueryException(returnCode, returnMessage);
        }
    }

    @Override
    public boolean hasContinuation() {
        return "Y".equals(moreData);
    }

    // a public getter and a public setter for each annotated field
}
```

Each order is an item class:

```java
public class Order {

    @BookField(order = 10, pic = "X(10)")
    private String orderNumber;

    @BookField(order = 20, pic = "S9(13)V99 COMP-3")   // 8 bytes, packed decimal
    private BigDecimal totalAmount;

    @BookField(order = 30)                             // PIC 9(8), pattern yyyyMMdd
    private LocalDate orderDate;

    // a public getter and a public setter for each annotated field
}
```

`OrderQueryException` is your own exception. It extends `BusinessException`. See [Error handling](Error-handling#business-errors).

### Rules for the classes

- Each annotated field needs a public getter and a public setter: `getName()` (or `isName()` for a `boolean`) and `setName(...)`. Lombok `@Data`, `@Getter`, and `@Setter` also work. The codec calls them, because BookMap does not use reflection.
- An operation class and an item class need a public constructor without arguments.
- The `order` values give the sequence of the fields. Gaps are permitted. Use 10, 20, 30, ... to keep space for new fields.
- A text or numeric field needs a size: a `pic`, or a `length`.
- A collection needs `maxOccurs`. Its count field is the field immediately before it, unless you set `dependsOn`.

See [Conventions](Conventions) for the defaults, and [Annotations reference](Annotations-reference) for all attributes.

### Validatable and Continuable

The two interfaces are optional:

- `Validatable.validate()` runs after each response. Throw an exception if the response shows an error. The exception stops the execution.
- `Continuable.hasContinuation()` runs after each response. Return `true` to call the program again. The client sends the same object, with the restart key that the program set in the response.

The client adds the orders of each response to `orders`. It stops when `hasContinuation()` returns `false`, or after `maxCalls` calls.
See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#pagination).

## Build the project

Run the build:

```bash
./gradlew build
```

```bash
mvn verify
```

The processor generates the codec `GetCustomerOrders_BookCodec` in the package of `GetCustomerOrders`.
It also registers the codec in `META-INF/services/pt.dazz.bookmap.spi.BookCodec`. The client finds it at runtime with the `ServiceLoader`.

An incorrect book stops the build with a compile error on the field. For example:

- A field without a getter or a setter.
- A `pic` that BookMap does not support, for example an edited picture.
- A Java type that does not agree with the `pic`, for example `int` with `V99`.
- A count field that cannot hold `maxOccurs`.

## Test with the simulator

The simulator runs a handler in the same JVM. The handler plays the role of the COBOL program.
The simulator encodes and decodes the bytes with the generated codec, so the test also checks the layout.

```java
class GetCustomerOrdersTest {

    private final SimulatorTransport simulator = SimulatorTransport.builder().build();
    private final BookmapClient client = BookmapClient.builder().transport(simulator).build();

    @Test
    void getsAllPagesOfOrders() {
        simulator.register(GetCustomerOrders.class, call -> {
            boolean firstPage = call.getRestartKey().isBlank();
            call.setOrders(List.of(order(firstPage ? "ORD0000001" : "ORD0000002")));
            call.setReturnCode("0000");
            call.setReturnMessage("OK");
            call.setMoreData(firstPage ? "Y" : "N");
            call.setRestartKey(firstPage ? "PAGE2" : "");
        });

        GetCustomerOrders request = new GetCustomerOrders();
        request.setCustomerId("CUST000042");
        request.setRestartKey("");

        GetCustomerOrders result = client.execute(request);

        assertThat(result.getOrders()).hasSize(2);
        assertThat(simulator.receivedRequests()).hasSize(2);
    }

    private static Order order(String number) {
        Order order = new Order();
        order.setOrderNumber(number);
        order.setTotalAmount(new BigDecimal("125.50"));
        order.setOrderDate(LocalDate.of(2026, 11, 12));
        return order;
    }
}
```

The simulator can also simulate an abend, a timeout, and a business error. See [Transports and simulator](Transports-and-simulator#the-simulator).
The full test is `EndToEndTest` in [`bookmap-examples`](https://github.com/ajlima/bookmap/tree/main/bookmap-examples).

## Connect to the mainframe

The client sends the bytes through a `Transport`. A transport implements one method:

```java
public class MyTransport implements Transport {

    @Override
    public byte[] exchange(BookRequest request) {
        String program = destinationOf(request.operation());   // for example, "GetCustomerOrders" -> "ORDRS01"
        try {
            return callTheMainframe(program, request.payload(), request.maxResponseLength());
        } catch (java.net.SocketTimeoutException e) {
            throw new TransportTimeoutException("timeout on " + program, e);
        } catch (java.io.IOException e) {
            throw new ConnectionException("cannot reach the mainframe", e);
        }
    }
}
```

- `request.operation()` is the operation name: the value of `@BookOperation`, or the simple class name.
- `request.payload()` is the request in bytes. The text fields use the charset of the client.
- Return the response bytes of the program. A response shorter than the layout causes a `MappingException`.
- Translate each technical failure to a subclass of `TransportException`. See [Error handling](Error-handling).

For CICS through the CICS Transaction Gateway, use `CtgTransport`. See [CICS Transaction Gateway](CICS-Transaction-Gateway).
For an HTTP endpoint that accepts the bytes of the book, use `HttpTransport`. See [HTTP transport](HTTP-transport).
For IBM MQ, use `MqTransport`. See [IBM MQ](IBM-MQ).
For other middleware, write a transport with the client library of your middleware.

Then create the client with the transport:

```java
BookmapClient client = BookmapClient.builder()
    .transport(new MyTransport())
    .charset(Charset.forName("IBM037"))   // default
    .maxCalls(1000)                        // default; safety limit
    .build();

GetCustomerOrders request = new GetCustomerOrders();
request.setCustomerId("CUST000042");
request.setRestartKey("");
GetCustomerOrders result = client.execute(request);   // the response object
```

With Spring Boot, declare the transport as a bean. The auto-configuration creates the client. See [Spring Boot](Spring-Boot).

### The builder

| Method | Meaning | Default |
|---|---|---|
| `transport(Transport)` | The transport. | Mandatory. |
| `charset(Charset)` | The default charset. It must be single-byte. | `IBM037` |
| `maxCalls(int)` | The maximum number of calls for one execution. The value must be 1 or more. | `1000` |
| `codecRegistry(BookCodecRegistry)` | The codec registry. | `BookCodecRegistry.loadDefault()` (`ServiceLoader`) |

### Threads

`BookmapClient` is immutable and thread-safe. Create one client and share it.
The request object is not thread-safe. It belongs to the caller. Use a new request object for each execution, or set the restart key to its start value before you use an object again.

### Charsets and native image

Only single-byte charsets are permitted. The default charset is `IBM037` (EBCDIC, United States).
Set the charset on the client with `BookmapClient.builder().charset(...)`. Set a charset for one book with `@BookDefaults(charset = "...")`.

For a GraalVM native image, `bookmap-core` contains a `native-image.properties` file.
It sets `-H:+AddAllCharsets`. Thus, the native image contains the EBCDIC charsets, and you add no option.
The generated codecs need no reflection configuration. The runtime finds them through `ServiceLoader`, and GraalVM supports it.

A smoke test checks this configuration with GraalVM Community for JDK 21. It runs one operation through the simulator transport, with the charset `IBM1047`.
See the standalone build `bookmap-native-test`.

## Next steps

- Read the [Annotations reference](Annotations-reference) and the [Conventions](Conventions).
- Map fixed tables and `REDEFINES` areas: [Collections and fixed OCCURS](Collections-and-fixed-OCCURS) and [Subtypes (REDEFINES)](Subtypes-REDEFINES).
- Generate the classes from a copybook with the [Copybook generator](Copybook-generator).
- If a step fails, see [FAQ and troubleshooting](FAQ-and-troubleshooting).
