# bookmap — Fixed `OCCURS` (design)

Date: 2026-11-11
Status: implemented in v0.3.0.
Base package: `pt.dazz.bookmap`
Parent spec: `2026-11-10-bookmap-design.md`
Related spec: `2026-11-11-bookmap-copybook-design.md` (the copybook generator needs this feature)

Decisions marked **[builder call]** come from the spec author, not from the project owner. You can change them if you have a good reason. All other decisions come from the project owner.

## 1. Intent

BookMap maps a COBOL table only as `OCCURS DEPENDING ON`. A `@BookCollection` always has a count field. The codec writes and reads only the items that the count gives. The offsets of the members after the collection change with the number of items.

Many COBOL books use a **fixed** table:

```cobol
01  GET-ORDER.
    05  ORDER-ID          PIC X(10).
    05  LINE-COUNT        PIC 9(2).
    05  ORDER-LINE OCCURS 20.
        10  PRODUCT-CODE  PIC X(8).
        10  QUANTITY      PIC 9(5).
    05  ORDER-TOTAL       PIC S9(9)V99 COMP-3.
```

The area of `ORDER-LINE` is always 20 items long, with data or not. `ORDER-TOTAL` is always at the same offset. BookMap cannot express this book today. A mapping as a normal collection gives incorrect offsets for `ORDER-TOTAL`.

The owner says that a fixed `OCCURS` is common. The program tells the caller the number of used items in one of two ways:

- A count field before the table, without `DEPENDING ON` (a convention of the program only).
- The unused items contain `LOW-VALUES` (bytes `0x00`).

This design adds a fixed collection that supports both ways.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals

- A fixed collection in an operation class and in a subtype (nested collections stay a non-goal).
- An optional count field.
- Without a count field, empty items are detected through `LOW-VALUES`.
- Compile-time offsets for the members after a fixed collection.
- Support in the processor, `bookmap-core`, the simulator transport, and `bookmap-examples`. 100% line and branch coverage in all modules.

### 2.2 Non-goals

- Nested collections (unchanged: still not supported).
- A different "empty" marker (for example, spaces, or a key field with zeros). Only `LOW-VALUES` marks an empty item.
- Partial responses that end inside the fixed area.

### 2.3 Anti-goals

- Silent data loss. A read must never drop an item with data. A write must never produce bytes that read back as a different list.
- A guess for the count field. The convention "the member immediately before the collection" does **not** apply to a fixed collection. A wrong guess would give wrong counts without an error.
- A change that breaks a codec that the 0.2.0 processor generated, or a book without fixed collections.

## 3. Annotation

Add one attribute to `@BookCollection`:

| Attribute | Meaning | Default |
|---|---|---|
| `fixed` | `true` for a fixed table (`OCCURS n` without `DEPENDING ON`). The area always holds `maxOccurs` items. | `false` |

```java
@BookCollection(order = 30, maxOccurs = 20, fixed = true)
private List<OrderLine> lines;                       // no count field: LOW-VALUES mode

@BookCollection(order = 30, maxOccurs = 20, fixed = true, dependsOn = "lineCount")
private List<OrderLine> lines;                       // count field mode
```

For a fixed collection, `dependsOn` is optional:

- `dependsOn` empty: no count field (**LOW-VALUES mode**).
- `dependsOn` set: the named field is the count field (**count mode**). The rules for the count field are the same as for `OCCURS DEPENDING ON`: type, digits that hold `maxOccurs`, position before the collection, and the direction.

The Javadoc of `dependsOn` and `fixed` must describe this difference.

## 4. Layout

- The area of a fixed collection is `maxOccurs × item length` bytes in its direction. The item length is the length of the longest layout of the item class (unchanged rule for subtypes).
- A member after a fixed collection has an offset that the processor calculates at compile time, if no variable collection comes before it.
- A fixed collection after a variable collection uses the runtime cursor, as today.
- `maxResponseLength()` does not change: it already uses `maxOccurs`.

## 5. Behavior

### 5.1 Write

1. If the list is larger than `maxOccurs`, throw `MappingException` (as today).
2. A `null` list is an empty list (as today).
3. Count mode: set the count field to the list size (as today).
4. Write each item. A shorter subtype item gets spaces at the end, up to the item length (as today).
5. Write `LOW-VALUES` in each slot after the last item, up to `maxOccurs`.
6. LOW-VALUES mode: if the encoded bytes of an item are all `0x00`, throw `MappingException`. The message contains the field descriptor and the item index. Reason: a read gives this item as empty, so the data would be lost. **[builder call]**

### 5.2 Read, count mode

1. Read the count field. A blank count is 0. A negative count, or a count larger than `maxOccurs`, throws `MappingException` (as today).
2. Decode `count` items.
3. Ignore the other slots. Move the cursor to the end of the full area.

### 5.3 Read, LOW-VALUES mode

1. A slot is **empty** if all its bytes are `0x00`. The check does not use the charset.
2. Find the last slot that is not empty. Decode all slots up to and including it.
3. An empty slot before a slot that is not empty throws `MappingException`. The message contains the field descriptor and the index of the empty slot. Reason: to skip or to stop at this slot would lose data.
4. The empty slots at the end are not in the list.
5. Move the cursor to the end of the full area.

### 5.4 Pagination, reset, and subtypes

- Pagination (`Continuable`), the reset of `OUTPUT` collections, and the rule against an `INPUT_OUTPUT` collection in a `Continuable` class are the same as for a variable collection.
- Items with subtypes: the empty check (5.3) uses the full slot. A slot that is not empty is decoded through the discriminator, as today.
- A fixed collection in a subclass without a count field does not need a count field in the same subclass. With a count field, the existing rule applies.
- A subtype discriminator can come after one or more fixed collections, if no variable collection comes before it. The discriminator offset is then a compile-time constant. Today the processor rejects a discriminator after any collection. Relax this rule, and keep the error for a variable collection. **[builder call]**

### 5.5 Implementation notes

- Add new public methods to `BookReader` and `BookWriter` (for example, a check for an all-`0x00` slot, and a write of `LOW-VALUES`). Do not change existing signatures: codecs from the 0.2.0 processor must keep working. **[builder call]**
- A response that ends inside the fixed area is a non-goal. Document in the README that the full area must be in the response. **[builder call]**

## 6. Compile errors

- `fixed = true` with a `dependsOn` that names a missing field, or a field that breaks the count field rules.
- A subtype discriminator after a variable collection (unchanged). A discriminator after only fixed collections is permitted (see 5.4).
- All existing collection errors (for example, `maxOccurs < 1`).

No new error for `fixed = false`: the behavior of existing books does not change.

## 7. Simulator and examples

- The simulator uses the generated codec. It needs no change other than tests for both modes.
- Add an example to `bookmap-examples` with international business data: an order with a fixed table of order lines and a field after the table. Show both modes. **[builder call]** for the class names.
- Add a round-trip test through the simulator for each mode, and for a fixed collection of subtype items.

## 8. Documentation

- README: update the `@BookCollection` table, the "Collections and pagination" section, the conventions list (convention 4 does not apply to a fixed collection), and "Known limitations".
- Javadoc of `@BookCollection`.

## 9. Release

A `feat:` commit merged to `main` publishes version 0.3.0 to Maven Central. The owner must approve the merge.
