The framework uses few attributes. Six conventions reduce the work:

1. `@BookOperation` without a value uses the simple class name.
2. `pic` replaces `length`, `scale`, `signed`, and `usage`.
3. A date or time field without `length` and `pic` gets its length from the pattern.
4. Without `dependsOn`, the count field of a variable collection is the member immediately before it. A fixed collection without `dependsOn` has no count field.
5. A collection is `OUTPUT` by default. The count field takes the direction of its collection. Other fields are `INPUT_OUTPUT`.
6. `@BookDefaults` sets defaults for a class or a package.

These attributes stay mandatory: `order`, `maxOccurs`, and the size of text and numeric fields.

## Convention style and explicit style

The convention style is the first choice. `GetCustomerOrders` on the [Getting started](Getting-started) page uses it.
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

## Defaults for a package

The billing package shows `@BookDefaults` with a Lombok class. The package sets `COMP3` and a sign:

```java
@BookDefaults(usage = Usage.COMP3, signed = true)
package pt.dazz.bookmap.examples.billing;
```

`amount` gets its sign from the `pic`. `tax` gets `COMP3` and the sign from the package defaults.

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

See [Annotations reference](Annotations-reference#bookdefaults-class-or-package) for the priority of the defaults.
