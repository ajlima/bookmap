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

| Exception | Cause |
|---|---|
| `MappingException` | A layout or conversion problem, or a missing codec. |
| `ContinuationLimitException` | `hasContinuation()` is still true after `maxCalls` calls. |
| `BusinessException` | The base class for the errors that `validate()` throws. |
| `TransportException` | The base class for the technical failures of a transport. |
| `RemoteAbendException` | The remote program stopped abnormally. `abendCode()` gives the abend code. |
| `TransportTimeoutException` | The remote program did not answer in time. |
| `ConnectionException` | The transport cannot connect to the remote system. |
| `OperationNotFoundException` | The transport has no destination for the operation name. |

## Business errors

Extend `BusinessException` for errors that the remote program reports in the response.
The example `OrderQueryException` does this. `GetCustomerOrders.validate()` throws it when the return code is not `"0000"`.

```java
@Override
public void validate() {
    if (!"0000".equals(returnCode)) {
        throw new OrderQueryException(returnCode, returnMessage);
    }
}
```

## Mapping errors

- If the client finds no codec for a class, it throws `MappingException`. Check the annotation processor configuration.
- The client uses the exact class of the object. A subclass without its own `@BookOperation` has no codec, unless it is in the `@BookSubTypes` list of its base class.
- If a response is shorter than the layout, the codec throws `MappingException`. The codec ignores extra bytes at the end.

See [FAQ and troubleshooting](FAQ-and-troubleshooting) for the frequent causes.
