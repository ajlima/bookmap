A `@BookCollection` maps a COBOL `OCCURS` to a `List<Item>`. A collection is variable or fixed:

- A **variable collection** works like `OCCURS DEPENDING ON`. The book size changes with the number of items.
- A **fixed collection** (`fixed = true`) works like `OCCURS n` without `DEPENDING ON`. The area always holds `n` items.

See [Annotations reference](Annotations-reference#bookcollection-field-of-type-listitem) for the attributes.

## Rules for collections

Each rule gives a compile error if the class does not obey it:

- Declare the field as `List<Item>`. Set `maxOccurs` to 1 or more.
- The item class is public, has a public constructor without arguments, and has no type parameters.
- The item class has at least one `@BookField`. An abstract item class is permitted only with `@BookSubTypes`.
- Do not set `direction` on an item field. The item fields use the direction of the collection.
- Do not put a `@BookCollection` in an item class. Nested collections are not supported.

### The count field

A variable collection always has a count field. A fixed collection has a count field only with `dependsOn`.

- Without `dependsOn`, the count field of a variable collection is the member immediately before the collection.
- The count field is `int`, `Integer`, `long`, or `Long`.
- The count field comes before the collection.
- The digits of the count field must hold `maxOccurs`. For example, `9(1)` cannot hold `maxOccurs = 50`.
- A count field without `direction` gets the direction of the collection. An explicit direction must include the direction of the collection.
- One count field serves only one collection.

The compile errors start with `dependsOn field '<name>'`, for example `dependsOn field 'orderCount' must come before the collection`.
A variable collection at the first position gives `dependsOn is missing and no member comes before the collection`.

## Variable collections

Fields after a collection are permitted. The codec calculates the offsets at runtime.

- Write: the codec sets the count field to the size of the list. It also calls the setter of the count field on your object. A `null` list writes the count 0.
- Write: the codec writes only the items of the list. Thus, the book size is the fixed part plus the item length multiplied by the number of items.
- Write: a list larger than `maxOccurs`, or a list with a `null` item, throws `MappingException`.
- Read: the codec reads the count field. A blank count is 0. A negative count, or a count larger than `maxOccurs`, throws `MappingException`.
- At the start of each `execute()`, the client sets each `OUTPUT` collection to a new, empty `ArrayList`.
- On each page, the codec makes a new `ArrayList` with the existing items and the decoded items. Then it calls the setter. A `null` list starts as an empty `ArrayList`. The count field shows the last page.
- The client does not reset an `INPUT_OUTPUT` collection. On read, the codec replaces it with a new `ArrayList` of the decoded items.
- An `INPUT_OUTPUT` collection in a `Continuable` class is a compile error.
- The codec always makes a new list, then it calls the setter. Thus, your getter can give a copy or an unmodifiable list.

## Pagination

Two interfaces control the execution cycle: `Validatable` and `Continuable`.
See [Annotations reference](Annotations-reference#interfaces).

The cycle for each call:

1. The client encodes the request, sends it with the transport, and decodes the response into the object. Without subtypes, this is the request object. See [Subtypes (REDEFINES)](Subtypes-REDEFINES).
2. If the object is `Validatable`, the client calls `validate()`. An exception stops the execution.
3. If the object is `Continuable`, the client calls `hasContinuation()`. If the result is `true`, the client goes back to step 1.
4. If `hasContinuation()` is still true after `maxCalls` calls, the client throws `ContinuationLimitException`. The message is `<operation>: the remote program has more data after <n> calls`.

The client encodes the current object on each call. Thus, the fields that a response sets go into the next request, for example the restart key.
The default `maxCalls` is 1000. Change it with `BookmapClient.builder().maxCalls(n)`. If `hasContinuation()` gives `false` on the last permitted call, no exception occurs.

In `GetCustomerOrders`, `hasContinuation()` returns true when `moreData` is `"Y"`. The remote program sets the restart key in the same response.
The client does not reset other fields. Set the restart key to its start value before you reuse an object.

## Fixed collections

Many books use a fixed table (`OCCURS n` without `DEPENDING ON`). The area always holds `n` items, and the fields after it have a fixed offset:

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

### LOW-VALUES mode

LOW-VALUES mode applies when `dependsOn` is not set. The unused slots contain LOW-VALUES (bytes `0x00`).

- Write: the codec writes the items, then LOW-VALUES in the unused slots.
- Read: a slot with only `0x00` bytes is empty. The list ends at the last slot with data. The check does not use the charset.
- An empty slot before a slot with data causes a `MappingException`.
- An item that encodes to only `0x00` bytes causes a `MappingException` on write. A read cannot find such an item.

### Count mode

Count mode applies when `dependsOn` is set. A count field before the table holds the number of used items:

```java
@BookCollection(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
private List<ShipmentSummary> shipments;
```

- Write: the codec sets the count field to the list size. Then it writes the items and LOW-VALUES in the unused slots.
- Read: the codec reads the number of items that the count field gives. It ignores the other slots.

**CAUTION:** Set `dependsOn` if the program gives a count field. Without `dependsOn`, the codec uses LOW-VALUES mode and does not read the count field. Only `0x00` bytes make an empty slot. If the program fills the unused slots with spaces, the codec reads those slots as items.

### Example: count mode

The example `ListShipments` in `bookmap-examples` uses a count field:

```cobol
01  LIST-SHIPMENTS.
    05  CUSTOMER-ID       PIC X(10).
    05  SHIPMENT-COUNT    PIC 9(2).
    05  SHIPMENT OCCURS 5.
        10  SHIPMENT-ID   PIC X(12).
        10  STATUS        PIC X(2).
    05  RETURN-CODE       PIC X(4).
```

```java
@BookOperation
public class ListShipments {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookField(order = 20, pic = "9(2)")          // gets the direction OUTPUT from the collection
    private int shipmentCount;

    @BookCollection(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
    private List<ShipmentSummary> shipments;

    @BookField(order = 40, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    // getters and setters
}

public class ShipmentSummary {

    @BookField(order = 10, pic = "X(12)")
    private String shipmentId;

    @BookField(order = 20, pic = "X(2)")
    private String status;

    // getters and setters
}
```

The fixed area always has 5 slots of 14 bytes. `returnCode` is always at the same offset.

### Rules for both modes

- The fixed area is `maxOccurs` multiplied by the item length. With subtype items, the item length is the length of the longest layout.
- A field after a fixed collection has a fixed offset, if no variable collection comes before it.
- The response must contain the full area, in both modes. A response that ends in the area causes a `MappingException`.
- Pagination, the reset of `OUTPUT` collections, and items with subtypes work as for a variable collection.
- The full examples are `GetOrder` and `ListShipments` in `bookmap-examples`, package `pt.dazz.bookmap.examples.orders`.

## Messages

The codec throws `MappingException` with one of these messages. `<field>` has the format `Owner.field (order n)`.

| Message | Cause |
|---|---|
| `<field>: the list has N items, more than maxOccurs M` | The list is too large for the book. |
| `<field>: the list contains a null item` | An item of the list is `null`. |
| `<field>: count V is not between 0 and maxOccurs M` | The response has a count that is not valid. |
| `<field>: item I is all LOW-VALUES, so a read cannot find it` | LOW-VALUES mode: an item encodes to `0x00` bytes only. |
| `<field>: slot A is empty, but slot B after it has data` | LOW-VALUES mode: an empty slot comes before a slot with data. |
| `<field>: the response is too short for the fixed area` | The response ends inside a fixed area. |
