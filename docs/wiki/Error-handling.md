All exceptions are unchecked. They are in the package `pt.dazz.bookmap.exception`.

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

| Exception | Cause | What to do |
|---|---|---|
| `MappingException` | A layout or conversion problem, or a missing codec. | Correct the class, the data, or the build. It is a bug, not a runtime condition. |
| `ContinuationLimitException` | `hasContinuation()` is still `true` after `maxCalls` calls. | Check `hasContinuation()`, or increase `maxCalls`. |
| `BusinessException` | The base class for the errors that your `validate()` throws. | Show the error to the user, or handle it in the business logic. |
| `TransportException` | A technical failure of the transport. | Retry the call, or report the failure. |
| `RemoteAbendException` | The remote program stopped abnormally. `abendCode()` gives the abend code, for example `ASRA`. | Report the failure to the mainframe team. |
| `TransportTimeoutException` | The remote program did not answer in time. | Retry the call, or increase the timeout of the transport. |
| `ConnectionException` | The transport cannot connect to the remote system. A custom transport throws it. | Check the network and the configuration. |
| `OperationNotFoundException` | The transport has no destination for the operation name. | Check the operation name and the configuration of the transport. |

Each exception has a constructor `(String message)` and a constructor `(String message, Throwable cause)`.
`RemoteAbendException` has the constructors `(String abendCode, String message)` and `(String abendCode, String message, Throwable cause)`.

## Business errors

Extend `BusinessException` for the errors that the remote program reports in the response.
The example `OrderQueryException` in `bookmap-examples` does this:

```java
import pt.dazz.bookmap.exception.BusinessException;

public class OrderQueryException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String returnCode;

    public OrderQueryException(String returnCode, String message) {
        super(returnCode + ": " + message);
        this.returnCode = returnCode;
    }

    public String returnCode() {
        return returnCode;
    }
}
```

`GetCustomerOrders.validate()` throws it when the return code is not `"0000"`:

```java
@Override
public void validate() {
    if (!"0000".equals(returnCode)) {
        throw new OrderQueryException(returnCode, returnMessage);
    }
}
```

The client calls `validate()` after each response. An exception on page 2 stops the pagination: the client sends no more requests.
The client does not wrap the exceptions from `validate()` or from the transport. They go to the caller unchanged.

## Catch the exceptions

Put each subclass before its superclass. Otherwise, the code does not compile.

```java
try {
    GetCustomerOrders result = client.execute(request);
    // use the result
} catch (OrderQueryException e) {          // your BusinessException
    String code = e.returnCode();
} catch (BusinessException e) {
    // other business errors from validate()
} catch (RemoteAbendException e) {
    String abend = e.abendCode();          // for example "ASRA"
} catch (TransportTimeoutException e) {
    // the remote program did not answer in time
} catch (TransportException e) {
    // other transport failures
} catch (ContinuationLimitException e) {
    // more data after maxCalls calls
} catch (MappingException e) {
    // a layout, conversion, or codec problem: a bug, not a runtime condition
}
```

## Messages

Most messages start with the field. The field has the format `Owner.field (order n)`, for example `GetCustomerOrders.orderDate (order 30)`.

### Client and codec registry

| Exception | Message | Cause |
|---|---|---|
| `MappingException` | `No codec for <class>. Make sure that bookmap-processor is on the annotationProcessor path.` | The processor did not run, or the class has no `@BookOperation`. |
| `MappingException` | `<operation>: empty response from transport` | The transport returned `null`. |
| `MappingException` | `<operation>: invalid charset <name>` | The charset of `@BookDefaults(charset = ...)` is not a valid single-byte charset. |
| `ContinuationLimitException` | `<operation>: the remote program has more data after <n> calls` | `hasContinuation()` is still `true` after `maxCalls` calls. |
| `IllegalArgumentException` | `Only single-byte charsets are valid: UTF-8` | The charset of the builder is not single-byte. |
| `IllegalArgumentException` | `maxCalls must be at least 1: <n>` | The value of `maxCalls` is less than 1. |
| `NullPointerException` | `transport` | `build()` without a transport. |
| `NullPointerException` | `request` | `execute(null)`. |

### Fields

| Message | Cause |
|---|---|
| `<field>: response too short, need N bytes at position P but only R remain` | The response is shorter than the layout. The codec ignores extra bytes at the end. |
| `<field>: text is longer than N characters (L)` | A text value is too long for the field. The codec never truncates. |
| `<field>: <value> has more than N digits` | A number is too large for the field. |
| `<field>: <value> has more than S decimal places` | A `BigDecimal` has more decimal places than the `scale`. The codec never rounds. |
| `<field>: negative value <value> in an unsigned field` | A negative value in a field without a sign. |
| `<field>: value <value> does not fit int` | A number in the response is too large for the Java type. |
| `<field>: byte 0xNN at offset K of the field is not valid in a packed decimal` | The response has bytes that are not a `COMP-3` number. The same message exists for a zoned decimal (`DISPLAY`). |
| `<field>: value <value> cannot be represented by the pattern (...)` | The date or time pattern cannot keep the value. See [Dates and times](PIC-syntax-and-type-mapping#dates-and-times). |
| `<field>: converter failed (<message>)` | Your `FieldConverter` threw a `RuntimeException`. The cause is the original exception. A `BookmapException` from a converter goes to the caller unchanged. |
| `<field>: converter must return N bytes but returned M bytes` | Your `FieldConverter` returned the wrong number of bytes. |

For the messages of collections and subtypes, see [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#messages) and [Subtypes (REDEFINES)](Subtypes-REDEFINES#behavior).
For the messages of the simulator, see [Transports and simulator](Transports-and-simulator#simulate-failures).

## Mapping errors

- If the client finds no codec for a class, it throws `MappingException`. Check the annotation processor configuration.
- The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec, unless it is in the `@BookSubTypes` list of its base class.
- If a response is shorter than the layout, the codec throws `MappingException`. An empty response (zero bytes) also gives this message. The codec ignores extra bytes at the end.

See [FAQ and troubleshooting](FAQ-and-troubleshooting) for the frequent causes.
