An `@Occurs` maps a COBOL `OCCURS` to a `List<Item>`. A collection is variable or fixed:

- A **variable collection** works like `OCCURS DEPENDING ON`. The book size changes with the number of items.
- A **fixed collection** (`fixed = true`) works like `OCCURS n` without `DEPENDING ON`. The area always holds `n` items.

See [Annotations reference](Annotations-reference#occurs-field-of-type-listitem) for the attributes.

## Rules for collections

Each rule gives a compile error if the class does not obey it:

- Declare the field as `List<Item>`. Set `maxOccurs` to 1 or more.
- The item class is public, has a public constructor without arguments, and has no type parameters.
- The item class has at least one `@Field`. An abstract item class is permitted only with `@SubTypes`.
- Do not set `direction` on an item field. The item fields use the direction of the collection.
- An `@Occurs` in an item class must be fixed. See [Nested collections](#nested-collections).

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
The default `maxCalls` is 1000. Change it with `CopyMapperClient.builder().maxCalls(n)`. If `hasContinuation()` gives `false` on the last permitted call, no exception occurs.

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
@Occurs(order = 40, maxOccurs = 3, fixed = true)
private List<OrderLine> lines;

@Field(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
private BigDecimal orderTotal;
```

The program tells the caller the number of used items in one of these ways. CopyMapper supports all of them.

| COBOL program | Mode | Attributes |
|---|---|---|
| `MOVE LOW-VALUES TO` the table. | LOW-VALUES mode | None. |
| `MOVE SPACES TO` the table. | SPACES mode | `emptyItem = EmptyItem.SPACES` |
| `INITIALIZE` the table, or `MOVE ZERO TO` the key of an unused entry. | Key mode | `emptyKey = "<key field>"` |
| A count field before the table. | Count mode | `dependsOn = "<count field>"` |

### LOW-VALUES mode

LOW-VALUES mode applies when `dependsOn`, `emptyItem`, and `emptyKey` are not set. The unused slots contain LOW-VALUES (bytes `0x00`).

- Write: the codec writes the items, then LOW-VALUES in the unused slots.
- Read: a slot with only `0x00` bytes is empty. The list ends at the last slot with data. The check does not use the charset.
- An empty slot before a slot with data causes a `MappingException`.
- An item that encodes to only `0x00` bytes causes a `MappingException` on write. A read cannot find such an item.

### SPACES mode

SPACES mode applies when `emptyItem = EmptyItem.SPACES` is set, without `emptyKey` and without `dependsOn`:

```java
@Occurs(order = 30, maxOccurs = 20, fixed = true, emptyItem = EmptyItem.SPACES)
private List<OrderLine> lines;
```

- Write: the codec writes the items, then spaces of the charset in the unused slots (`0x40` in EBCDIC, `0x20` in ASCII).
- Read: a slot is empty if each byte is a space or `0x00`. Thus, the codec also accepts a table that the program cleared with LOW-VALUES.
- An empty slot before a slot with data causes a `MappingException`.
- An item that encodes to only spaces causes a `MappingException` on write. For example, an item with only text fields and empty values.

### Key mode

Key mode applies when `emptyKey` is set. The key is a field of the item class. A slot is empty if its key is blank or zero:

```java
@Occurs(order = 20, maxOccurs = 4, fixed = true, emptyKey = "orderNumber",
    emptyItem = EmptyItem.SPACES)
private List<Backorder> backorders;
```

- A `String` key is empty if it has only spaces or `0x00` bytes. The text `"000"` is not empty: map the key as a number to use zeros.
- A number key is empty if its value is zero, or if a `DISPLAY` or `COMP-3` key has only spaces or `0x00` bytes. A `COMP` key is empty only if its value is zero.
- The other bytes of the slot have no effect. A program can keep old data in a removed entry.
- Write: the codec writes the items. In each unused slot, it writes the bytes of `emptyItem` (`0x00` by default). A number key gets the value zero, encoded with its `PIC`. Thus, a COBOL check such as `IF ORDER-NUMBER = ZERO` is true.
- An item with an empty key causes a `MappingException` on write. A read cannot find such an item.
- An empty slot before a slot with data causes a `MappingException`.

Rules for the key. Each rule gives a compile error if the class does not obey it:

- The key is a field of the item class. With subtypes, it is a field of the base class, not of a subtype.
- The key is `String`, `int`, `Integer`, `long`, `Long`, or `BigDecimal`, without a converter.
- `emptyKey` is not permitted with `dependsOn`.
- `emptyItem` and `emptyKey` need `fixed = true`.

### Count mode

Count mode applies when `dependsOn` is set. A count field before the table holds the number of used items:

```java
@Occurs(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
private List<ShipmentSummary> shipments;
```

- Write: the codec sets the count field to the list size. Then it writes the items and LOW-VALUES in the unused slots.
- Read: the codec reads the number of items that the count field gives. It ignores the other slots.
- With `emptyItem = EmptyItem.SPACES`, the codec writes spaces in the unused slots. A read does not use `emptyItem`.

**CAUTION:** Select the mode that agrees with the program. In LOW-VALUES mode, only `0x00` bytes make an empty slot. If the program fills the unused slots with spaces, the codec reads those slots as items.

The copybook generator does not guess the mode of a fixed table. Give the count field with the option `countFields`, and the markers with the options `emptyItems` and `emptyKeys`. See [Count field of a fixed table](Copybook-generator#count-field-of-a-fixed-table) and [Empty entries of a fixed table](Copybook-generator#empty-entries-of-a-fixed-table).

### Example: count mode

The example `ListShipments` in `copymapper-examples` uses a count field:

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
@Operation
public class ListShipments {

    @Field(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @Field(order = 20, pic = "9(2)")          // gets the direction OUTPUT from the collection
    private int shipmentCount;

    @Occurs(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
    private List<ShipmentSummary> shipments;

    @Field(order = 40, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    // getters and setters
}

public class ShipmentSummary {

    @Field(order = 10, pic = "X(12)")
    private String shipmentId;

    @Field(order = 20, pic = "X(2)")
    private String status;

    // getters and setters
}
```

The fixed area always has 5 slots of 14 bytes. `returnCode` is always at the same offset.

### Rules for all modes

- The fixed area is `maxOccurs` multiplied by the item length. With subtype items, the item length is the length of the longest layout.
- A field after a fixed collection has a fixed offset, if no variable collection comes before it.
- The response must contain the full area, in all modes. A response that ends in the area causes a `MappingException`. To accept a response with only the used entries, set `partial = true`. See [Partial responses](#partial-responses).
- Pagination, the reset of `OUTPUT` collections, and items with subtypes work as for a variable collection.
- The full examples are `GetOrder`, `ListShipments`, and `ListBackorders` in `copymapper-examples`, package `pt.dazz.copymapper.examples.orders`.

## Partial responses

Some programs send only the used entries of a fixed table. For example, an MQ message or an HTTP body ends after the last used entry. Set `partial = true` for this table:

```cobol
01  LIST-OPEN-ORDERS.
    05  CUSTOMER-ID       PIC X(10).
    05  RETURN-CODE       PIC X(4).
    05  ORDER-COUNT       PIC 9(2).
    05  OPEN-ORDER OCCURS 10.
        10  ORDER-ID      PIC X(12).
        10  ORDER-STATUS  PIC X(2).
```

```java
@Field(order = 30, pic = "9(2)")
private int orderCount;

@Occurs(order = 40, maxOccurs = 10, fixed = true, dependsOn = "orderCount",
    partial = true)
private List<OpenOrder> orders;
```

The full response has `4 + 2 + 10 × 14 = 146` bytes. With 3 open orders, the program can send `4 + 2 + 3 × 14 = 48` bytes.

- The response can end on a slot boundary inside the area. The missing slots are unused slots.
- The response can also end at the start of the area. This gives 0 slots.
- A response that ends inside a slot causes a `MappingException`, in all modes.
- Count mode: a count larger than the number of slots in the response causes a `MappingException`. The codec ignores the slots after the count.
- LOW-VALUES, SPACES, and key mode: the codec checks only the slots in the response. The rules of the mode apply to them.
- A response with the full area works as without `partial`.
- A write and a request always use the full area. `maxResponseLength()` does not change.

Rules. Each rule gives a compile error if the class does not obey it:

| Rule | Message |
|---|---|
| Set `fixed = true`. | `partial needs fixed = true` |
| Use the direction `OUTPUT` or `INPUT_OUTPUT`. | `partial needs the direction OUTPUT or INPUT_OUTPUT` |
| Use `partial` only on a top-level collection, not in an item class. | `partial is permitted only on a top-level collection` |
| Put no `OUTPUT` or `INPUT_OUTPUT` member after the collection, also in a subtype. An `INPUT` member is permitted. | `partial needs the collection at the end of the response: <member> comes after it` |

A slot is the unit. A slot with an inner collection or with a subtype item is complete in the response, or the response ends before it.

**CAUTION:** Use `partial` only for a program that sends the used entries. In LOW-VALUES, SPACES, and key mode, a response that a gateway cuts on a slot boundary gives a shorter list without an error.

The CICS Transaction Gateway transport always gives the full COMMAREA. The HTTP and MQ transports give the response as the program sent it.
The full example is `ListOpenOrders` in `copymapper-examples`, package `pt.dazz.copymapper.examples.orders`.

## Nested collections

An item class can have a fixed collection. This is a table in a table entry:

```cobol
01  GET-ORDER-TAXES.
    05  ORDER-ID              PIC X(10).
    05  LINE-COUNT            PIC 9(2).
    05  ORDER-LINE OCCURS 0 TO 20 DEPENDING ON LINE-COUNT.
        10  PRODUCT-CODE      PIC X(8).
        10  QUANTITY          PIC 9(5).
        10  TAX-COUNT         PIC 9.
        10  LINE-TAX OCCURS 3.
            15  TAX-CODE      PIC X(4).
            15  TAX-AMOUNT    PIC S9(7)V99 COMP-3.
    05  ORDER-TOTAL           PIC S9(9)V99 COMP-3.
```

```java
public class TaxedOrderLine {

    @Field(order = 10, pic = "X(8)")
    private String productCode;

    @Field(order = 20, pic = "9(5)")
    private int quantity;

    @Field(order = 30, pic = "9")
    private int taxCount;

    @Occurs(order = 40, maxOccurs = 3, fixed = true, dependsOn = "taxCount")
    private List<LineTax> taxes;

    // getters and setters
}
```

The outer collection can be fixed or variable. The full example is `GetOrderTaxes` in `copymapper-examples`.

### Rules for a nested collection

Each rule gives a compile error if the class does not obey it:

- Set `fixed = true` on the inner collection. An `OCCURS DEPENDING ON` in a table entry is not supported.
- Do not set `direction` on the inner collection. It uses the direction of the outer collection.
- Put the count field in the item class, before the inner collection. A field of the outer class gives `dependsOn field 'x' does not exist in item class Y`.
- Do not use an item class in its own path, for example `OrderLine` > `SubLine` > `OrderLine`.
- Use a maximum of 7 levels of collections.

All modes of a fixed collection are available for the inner collection: count, LOW-VALUES, SPACES, and key mode.
A subtype of an item class can also have an inner collection. The item class of an inner collection can have subtypes.

### Layout and behavior

- Each entry has a fixed length. For example, an entry of `ORDER-LINE` is `8 + 5 + 1 + 3 × 9 = 41` bytes.
- Write: before the codec writes an item, it sets the inner count field to the size of the inner list. A `null` inner list is an empty list.
- Read: the codec reads the inner collection of each used outer slot. It makes a new list for each item.
- Only a top-level `OUTPUT` collection adds the items of each page, and only a top-level collection is reset at the start of `execute()`.
- The outer marker checks the full outer slot, with the inner area.

**CAUTION:** Set `emptyItem` on each level. The inner collection does not get the `emptyItem` of the outer collection. If the program clears the table with spaces, an inner collection in LOW-VALUES mode reads a slot of spaces as an item.

### Messages with an index

A message about an item of a nested collection names the collection and the index at each level:

```
GetOrderTaxes.lines (order 30) item 1: TaxedOrderLine.taxes (order 40): the list has 4 items, more than maxOccurs 3
GetOrderTaxes.lines (order 30) item 2: TaxedOrderLine.taxes (order 40) item 0: LineTax.taxAmount (order 20): ...
```

The index starts at 0. Each level adds its prefix, outer level first. A collection without an inner collection gets the prefix too. See [Messages](#messages).

## Messages

The codec throws `MappingException` with one of these messages. `<field>` has the format `Owner.field (order n)`.

A message about an item starts with the collection and the index of the item, for example:

```
CreateShipment.lines (order 30) item 2: ShipmentLine.sku (order 1): text is longer than 10 characters (12)
```

The index starts at 0. The codec adds the prefix to the same exception object. Thus, the class and the cause of the exception do not change, also for a subclass of `MappingException` from a converter.
A `CopyMapperException` from a converter that is not a `MappingException` goes to the caller unchanged, without the prefix.
The messages in the table below are about the collection. They do not get the prefix of their own collection.

| Message | Cause |
|---|---|
| `<field>: the list has N items, more than maxOccurs M` | The list is too large for the book. |
| `<field>: item I is null` | Item I of the list is `null`. |
| `<field>: count V is not between 0 and maxOccurs M` | The response has a count that is not valid. |
| `<field>: item I is all LOW-VALUES, so a read cannot find it` | LOW-VALUES mode: an item encodes to `0x00` bytes only. |
| `<field>: item I is all spaces, so a read cannot find it` | SPACES mode: an item encodes to spaces only. |
| `<field>: item I has an empty key K, so a read cannot find it` | Key mode: the key of an item is blank or zero. |
| `<field>: slot A is empty, but slot B after it has data` | LOW-VALUES, SPACES, or key mode: an empty slot comes before a slot with data. |
| `<field> slot S: byte 0x.. at offset N of the field is not valid in a zoned decimal` | Key mode: the number key of slot S has bytes that are not valid. |
| `<field>: the response is too short for the fixed area` | The response ends inside a fixed area, without `partial = true`. |
| `<field>: the response ends inside slot S, after B of L bytes` | `partial = true`: the response ends inside slot `S`. The index starts at 0. |
| `<field>: count C is more than the number of slots in the response (P)` | `partial = true`, count mode: the response has fewer slots than the count. |
