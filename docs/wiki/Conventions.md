The framework uses few attributes. Six conventions reduce the work:

1. `@Operation` without a value uses the simple class name.
2. `pic` replaces `length`, `scale`, `signed`, and `usage`.
3. A date or time field without `length` and `pic` gets its length from the pattern. An explicit `length` must agree with the pattern.
4. Without `dependsOn`, the count field of a variable collection is the member immediately before it. A fixed collection without `dependsOn` has no count field.
5. A collection is `OUTPUT` by default. The count field takes the direction of its collection, unless the count field sets a direction. Other fields are `INPUT_OUTPUT`.
6. `@Defaults` sets defaults for a class or a package.

These attributes stay mandatory: `order`, `maxOccurs`, and the size of text and numeric fields.

## Convention style and explicit style

The convention style is the first choice. `GetCustomerOrders` on the [Getting started](Getting-started) page uses it.
The explicit style sets `length`, `scale`, `signed`, `usage`, and `dependsOn` one by one.
Use it when you want no `pic` string. `UpdateShipment` uses it.

```java
@Operation("UpdateShipment")
public class UpdateShipment {

    @Getter
    @Setter
    @Field(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @Field(order = 20, length = 9, scale = 2, signed = true, usage = Usage.COMP3, direction = INPUT)
    private BigDecimal weightKg;

    @Getter
    @Setter
    @Field(order = 30, length = 3, direction = INPUT)
    private int lineCount;

    @Occurs(order = 40, direction = INPUT, dependsOn = "lineCount", maxOccurs = 20)
    private List<ShipmentLine> lines;   // lineCount is INPUT: it agrees with the INPUT collection

    @Field(order = 50, length = 2, direction = OUTPUT)
    private String status;

    // The other getters and setters are handwritten: see copymapper-examples
}
```

## Defaults for a package

The billing package shows `@Defaults` with a Lombok class. The package sets `COMP3` and a sign:

```java
@Defaults(usage = Usage.COMP3, signed = true)
package pt.dazz.copymapper.examples.billing;
```

Both numeric fields get `COMP3` from the package defaults, because their `pic` has no usage clause. `amount` gets its sign from the `pic`. `tax` gets its sign from the package defaults.
`invoiceNumber` is a text field. It ignores the `usage` and `signed` defaults.

```java
@Data
public class Invoice {

    @Field(order = 10, pic = "X(10)")
    private String invoiceNumber;

    @Field(order = 20, pic = "S9(7)V99")
    private BigDecimal amount;

    @Field(order = 30, length = 7, scale = 2)
    private BigDecimal tax;
}
```

See [Annotations reference](Annotations-reference#defaults-class-or-package) for the priority of the defaults.

## Imports in the IDE

The annotations have short names, for example `@Field` and `@Operation`. Other libraries use the same names, for example `java.lang.reflect.Field`, the `@Field` of Spring Data MongoDB, and the `@Operation` of OpenAPI.
The auto-import of the IDE can select the wrong type. Most wrong imports do not compile.

The processor also stops the build if a class that it maps has an annotation of another library with the name `Field`, `Occurs`, `Group`, `SubTypes`, or `Defaults`:

```text
GetOrder.note: org.springframework.data.mongodb.core.mapping.Field is not pt.dazz.copymapper.annotation.Field. Change the import.
```

To prevent the wrong import, exclude the other types from the auto-import of the IDE:

- IntelliJ IDEA: open **Settings** > **Editor** > **General** > **Auto Import**, and add the names to **Exclude from auto-import**.
- Eclipse: open **Preferences** > **Java** > **Appearance** > **Type Filters**, and add the names.
