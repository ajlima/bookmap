A `@BookCollection` maps a COBOL `OCCURS` to a `List<Item>`. A collection is variable or fixed:

- A **variable collection** works like `OCCURS DEPENDING ON`. The book size changes with the number of items.
- A **fixed collection** (`fixed = true`) works like `OCCURS n` without `DEPENDING ON`. The area always holds `n` items.

See [Annotations reference](Annotations-reference#bookcollection-field-of-type-listitem) for the attributes.

## Variable collections

Fields after a collection are permitted. The codec calculates the offsets at runtime.

- Write: the codec sets the count field to the size of the list. A `null` list writes the count 0. A list larger than `maxOccurs` throws `MappingException`.
- Read: the codec reads the count field. A negative count, or a count larger than `maxOccurs`, throws `MappingException`.
- At the start of each `execute()`, the client sets each `OUTPUT` collection to a new, empty `ArrayList`.
- On each page, the codec makes a new `ArrayList` with the existing items and the decoded items. Then it calls the setter. A `null` list starts as an empty `ArrayList`. The count field shows the last page.
- The client does not reset an `INPUT_OUTPUT` collection. On read, the codec replaces it with a new `ArrayList` of the decoded items.
- An `INPUT_OUTPUT` collection in a `Continuable` class is a compile error.

## Pagination

Two interfaces control the execution cycle: `Validatable` and `Continuable`.
See [Annotations reference](Annotations-reference#interfaces).

The cycle for each call:

1. The client encodes the request, sends it with the transport, and decodes the response into the object. Without subtypes, this is the request object. See [Subtypes (REDEFINES)](Subtypes-REDEFINES).
2. If the object is `Validatable`, the client calls `validate()`. An exception stops the execution.
3. If the object is `Continuable`, the client calls `hasContinuation()`. If the result is `true`, the client goes back to step 1.
4. If `hasContinuation()` is still true after `maxCalls` calls, the client throws `ContinuationLimitException`.

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

**CAUTION:** Do not rely on convention 4 for a fixed collection. The copybook does not tell which field is the count. A wrong count field gives wrong lists without an error.

### Rules for both modes

- The response must contain the full area. A response that ends in the area causes a `MappingException`.
- Pagination, the reset of `OUTPUT` collections, and items with subtypes work as for a variable collection.
- The full examples are `GetOrder` and `ListShipments` in `bookmap-examples`, package `pt.dazz.bookmap.examples.orders`.
