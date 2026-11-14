# Nested collections (design)

Date: 2026-11-14
Status: proposed.
Issue: #43
Parent specs: `2026-11-10-bookmap-design.md` (section 2.2), `2026-11-11-bookmap-fixed-occurs-design.md`, `2026-11-13-empty-item-markers-design.md`

Decisions marked **[builder call]** come from the spec author, not from the project owner. Section 12 lists them for review.

## 1. Intent

BookMap rejects a `@BookCollection` in an item class. The copybook generator rejects an `OCCURS` in an `OCCURS`. Many books have a table in a table entry:

```cobol
01  GET-ORDER.
    05  ORDER-ID              PIC X(10).
    05  LINE-COUNT            PIC 9(2).
    05  ORDER-LINE OCCURS 10.
        10  PRODUCT-CODE      PIC X(8).
        10  QUANTITY          PIC 9(5).
        10  TAX-COUNT         PIC 9.
        10  LINE-TAX OCCURS 3.
            15  TAX-CODE      PIC X(4).
            15  TAX-AMOUNT    PIC S9(7)V99 COMP-3.
    05  ORDER-TOTAL           PIC S9(9)V99 COMP-3.
```

This spec adds a collection in an item class (a **nested collection**), with the existing rules of each level.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals

- A `@BookCollection` in an item class, also in a subtype of an item class.
- A fixed inner collection in all four modes: count, LOW-VALUES, SPACES, and key.
- A variable inner collection (count field in the item) in a variable outer collection.
- Nested collections at more than two levels, up to the depth limit (section 9).
- Messages that name the index at each level.
- Support in `bookmap-processor`, `bookmap-core` (only if necessary, add-only), the copybook generator, the documentation, and `bookmap-examples`. 100% line and branch coverage.

### 2.2 Non-goals

- A count field outside the item class for an inner collection. This is the COBOL "complex `OCCURS DEPENDING ON`" form (section 3). Section 3.3 tells why.
- A variable inner collection in a fixed outer collection (section 3.2).
- Pagination of an inner collection. Only a top-level `OUTPUT` collection accumulates items across pages.
- A new generator option. The existing options `countFields`, `emptyItems`, and `emptyKeys` already find an inner table by its COBOL name (section 10.3).

### 2.3 Anti-goals

- Silent data loss at any level. A read must never drop an item with data. A write must never produce bytes that read back as a different object.
- A change of the bytes that a codec writes or reads for a book without a nested collection.
- A change of the signatures of `BookReader` and `BookWriter`. Codecs from older processors must keep working.

## 3. Supported combinations

### 3.1 The four combinations

| Outer | Inner | COBOL | In scope |
|---|---|---|---|
| Fixed (`OCCURS n`) | Fixed (`OCCURS m`) | Valid. Each entry has the same length. | Yes. All four modes of the inner collection. |
| Variable (`OCCURS DEPENDING ON`) | Fixed | Valid. Each entry has the same length. | Yes. All four modes of the inner collection. |
| Fixed | Variable | Valid only as IBM "complex ODO", with the count outside the outer table. | No. Compile error. |
| Variable | Variable | Valid only as IBM "complex ODO", with the count outside the outer table. | Yes, but only with the count field in the item. The entries then have different lengths. |

### 3.2 Why a variable collection in a fixed collection is an error

A fixed area has `maxOccurs` slots of the same length. The empty-slot check, the skip of the unused slots, the key offset, and the subtype filler all need a constant slot length. A variable inner collection with its count in the item gives each slot a different length. Thus, the processor rejects it. For a table that the program fills to the maximum, map the inner collection as fixed, with `dependsOn`.

### 3.3 Where the count field of an inner collection can be

In COBOL, `DEPENDING ON` names a data-name without a subscript. Thus, the count of a COBOL inner `OCCURS DEPENDING ON` is outside the outer table, and all entries of the outer table have the same number of inner entries. IBM Enterprise COBOL calls this a "table that has variable-length elements" (complex ODO).

BookMap supports a count field only **in the item class**, before the inner collection:

- A fixed inner collection with `dependsOn`: the count is a program convention, not a COBOL `DEPENDING ON`. This is valid COBOL in all outer collections.
- A variable inner collection: each entry has its own count. COBOL cannot declare this layout, but a program can build it, and a non-COBOL partner (for example, a Java or a .NET program on MQ) often sends it. BookMap already reads a variable collection with a runtime cursor, so the cost is low.

A count field outside the item class is a compile error. The complex ODO form needs one count for all entries, equal list sizes on write, and a slot length that changes at runtime. This is a separate feature. **[builder call]**

## 4. Annotation API

No new annotation and no new attribute. `@BookCollection` is now permitted in an item class, with these rules:

- `dependsOn` names a field of the same item class (with subtypes: of the same layout, as for an operation class). Without `dependsOn`, the count field of a variable inner collection is the member immediately before it in the item class (the existing convention).
- Do not set `direction` on an inner collection. It gets the direction of the outer collection, as an item field does.
- `fixed`, `emptyItem`, and `emptyKey` have the same meaning as at the top level. The key is a field of the inner item class.
- The inner `emptyItem` does not inherit the outer `emptyItem`. The default stays `LOW_VALUES`. **[builder call]**

```java
public class OrderLine {

    @BookField(order = 10, pic = "X(8)")
    private String productCode;

    @BookField(order = 20, pic = "9(5)")
    private int quantity;

    @BookField(order = 30, pic = "9")
    private int taxCount;

    @BookCollection(order = 40, maxOccurs = 3, fixed = true, dependsOn = "taxCount")
    private List<LineTax> taxes;

    // getters and setters
}
```

## 5. Compile rules

Each rule gives a compile error at the member. The existing collection errors apply at each level.

| Rule | Message |
|---|---|
| A variable collection in the item of a fixed collection. | `a variable collection is not permitted in the item of a fixed collection (<outer descriptor>)` |
| The count field is not in the item class. | `dependsOn field '<name>' does not exist in item class <ItemClass>. The count field of a nested collection must be in the item class` |
| `direction` on an inner collection. | `direction must not be set on a collection in an item class` |
| An item class that contains itself, directly or through other item classes. | `item class <X> contains itself: <A> > <B> > <X>` |
| More levels than the depth limit. | `a collection must not have more than 7 levels of nested collections` |
| An item class with subtypes and a variable inner collection, in the base class or in a subtype. | `an item class with subtypes must have a fixed length: <member> is a variable collection` |
| An item discriminator after a variable inner collection. | `discriminator must not come after a variable collection` (existing rule, now also for an item) |
| An `emptyKey` after an inner collection. | Not possible: the outer collection is fixed, so its inner collections are fixed and the key offset is constant. No new rule. |

A count field can serve only one collection in its class (existing rule, per class).

## 6. Byte layout and offsets

- The length of an entry with a fixed inner collection is the sum of the member lengths. An inner fixed collection has the length `maxOccurs × inner entry length`.
- An entry with a variable inner collection has a variable length. Its maximum is the sum of the maximum member lengths.
- `maxResponseLength()` uses the maximum length at each level, recursively.
- With subtypes in an item class, each entry has the length of the longest layout (existing rule). All layouts have a fixed length (section 5).
- The offset of an item discriminator and of an `emptyKey` is a compile-time constant. It includes the full areas of the inner fixed collections before it.

Example of `ORDER-LINE` in section 1: an entry is `8 + 5 + 1 + 3 × (4 + 5) = 41` bytes. The fixed area is `10 × 41 = 410` bytes.

## 7. Behavior

### 7.1 Write

1. The codec writes the members of an item in order, as it does for an operation class.
2. Before it writes an item, the codec sets each inner count field to the size of the inner list, and calls the setter on the item. This is the existing rule of the top level, applied to each item. A `null` inner list is an empty list.
3. An inner list larger than `maxOccurs`, or with a `null` item, throws `MappingException`.
4. The empty-slot rules apply at each level: an inner fixed collection writes the bytes of its `emptyItem` in its unused slots. An inner item that a read cannot find throws `MappingException`.
5. The check of an outer item that a read cannot find uses the full outer slot, with the inner area. For example, in LOW-VALUES mode, an outer item with empty fields and an empty inner list with the default `emptyItem` is all `0x00`. The codec throws `MappingException`.

### 7.2 Read

1. The codec reads an item from its members in order. For an inner collection, it uses the same rules as at the top level: count mode, marker modes, and the skip of the unused inner slots.
2. The codec reads an inner collection only in a used outer slot. It does not read the unused outer slots.
3. The codec makes a new `ArrayList` for each inner collection and calls the setter on the item.

### 7.3 Empty-item markers at each level

The markers of #44 apply to each fixed collection on its own:

- The outer marker checks the full outer slot, with the inner area. The outer key is a field of the outer item class.
- The inner marker checks each inner slot of a used outer slot.
- The inner `emptyItem` does not inherit the outer one (decision 3). Example: the program clears the outer table with spaces. The outer collection has `emptyItem = SPACES`. An inner collection without `dependsOn` also needs `emptyItem = SPACES`, else a slot with spaces reads as an inner item. The documentation gives this caution.

### 7.4 Pagination, reset, and `Continuable`

- Only a top-level `OUTPUT` collection accumulates items across pages, and only the top-level `OUTPUT` collections are reset at the start of `execute()`.
- An inner collection is part of its item. Each decoded item has new inner lists.
- The rule against an `INPUT_OUTPUT` collection in a `Continuable` class applies only to a top-level collection.

### 7.5 Subtypes

- A base item class and a subtype of an item class can declare an inner collection. The count field of a collection in a subtype is in the same layout (existing rule of the operation class).
- The item class of an inner collection can have subtypes. The existing item rules apply.

## 8. Messages with an index

Today a message names the field descriptor, but not the item. With nested collections, the same item class can be at several positions. **[builder call]**

The codec catches a `MappingException` of an item, and throws a new `MappingException` with the prefix `<collection descriptor> item <i>: `. The cause is the original exception. Each level adds its prefix:

```
GetOrder.lines (order 40) item 2: OrderLine.taxes (order 40) item 0: LineTax.taxAmount (order 20): value 123456789.00 has more than 7 integer digits
GetOrder.lines (order 40) item 2: OrderLine.taxes (order 40): count 7 is not between 0 and maxOccurs 3
```

The prefix also applies to a collection without a nested collection. Thus, a message of an item field changes for all books: it now starts with the collection descriptor and the index. The messages of the collection itself (for example, `the list has N items, more than maxOccurs M`) do not change at the top level. **[builder call]**

## 9. Depth limit

A collection path has at most 7 levels: the top-level collection and 6 levels of nested collections. The COBOL 85 standard permits 7 levels of `OCCURS`. **[builder call]**

The processor also detects a cycle: an item class that contains itself. Without this check, the model builder would not stop.

## 10. Copybook generator

### 10.1 Mapping

- An `OCCURS` without `DEPENDING ON` in an `OCCURS` entry maps to a fixed inner collection in the item class.
- An `OCCURS DEPENDING ON` in an `OCCURS` entry is an error. Its count is outside the entry (section 3.3). The message: `OCCURS <inner> DEPENDING ON <object> is inside OCCURS <outer>. BookMap does not support a DEPENDING ON table in a table entry (complex OCCURS DEPENDING ON).` **[builder call]**
- An inner table gets the direction of the outer table. The `Continuable` check of an `INPUT_OUTPUT` table applies only to a top-level table.
- The info diagnostic about a `COMP` item in LOW-VALUES mode applies to each fixed table.
- The record length and the "largest tables" warning count the inner tables in the outer entry length. The warning names only top-level tables.

### 10.2 Count field and markers of an inner table

- `countFields`: the count item of an inner table must be in the same entry, before the table. The existing message `count field <name> must be in the same class, before the table` applies.
- `emptyItems` and `emptyKeys`: the same rules as for a top-level table. The key item is in the inner entry.
- The suggestion of a `-COUNT` item just before a fixed table applies also to an inner table.

### 10.3 Options

The options find a table through its COBOL name, bare or qualified (`LINE-TAX` or `LINE-TAX OF ORDER-LINE`). This already works for an inner table. Thus, `MappingOptions`, the options file, the CLI, and the Gradle and Maven plugins do not change. A test proves each option on an inner table.

### 10.4 Generated classes and model JSON

- An item class can have a `@BookCollection` member.
- The model JSON of a collection item can contain collection members. The snapshot of the processor (`ModelSnapshot`) gets the members of each item, not only the fields.

## 11. Round-trip guarantee

For each supported combination, `decode(encode(x))` gives an object equal to `x`, if each list at each level has a size that the mode can express. A test covers each combination and each inner mode through the simulator transport:

- Fixed in fixed: count, LOW-VALUES, SPACES, and key modes of the inner collection.
- Fixed in variable.
- Variable in variable, with entries of different lengths.
- An inner collection in a subtype of an item class, and inner items with subtypes.
- Three levels.

## 12. Documentation and examples

- `docs/wiki/Collections-and-fixed-OCCURS.md`: a new section "Nested collections", with the combinations, the count field rule, the markers at each level, and the messages with an index. Remove the rule "Do not put a `@BookCollection` in an item class".
- `docs/wiki/Copybook-generator.md`: the clause table and the limitations.
- `docs/wiki/Home.md` and the README: remove "nested collections" from the known limitations. Add "a `DEPENDING ON` table in a table entry".
- `docs/design/2026-11-10-bookmap-design.md` section 2.2: refer to this spec.
- `bookmap-examples`: an order with order lines and line taxes (`GetOrderTaxes`), with a fixed inner table in count mode, and a round-trip test.

## 13. Release

A `feat:` commit publishes a new minor version. The change of the item messages (section 8) is not a break of the API, so no `BREAKING CHANGE`.

## 14. Decisions for review

| # | Decision | Options | Choice | Reason |
|---|---|---|---|---|
| 1 | Count field of an inner collection | (a) Only in the item class. (b) Also outside, in the class of the outer collection (COBOL complex ODO). | (a) | (b) needs one count for all entries, equal list sizes, and a slot length that changes at runtime. It can be a separate issue. |
| 2 | Variable in variable | (a) Support it with the count in the item. (b) Reject it, as COBOL cannot declare it. | (a) | The runtime cursor already supports it. Non-COBOL partners send this layout. |
| 3 | Inner `emptyItem` default | (a) `LOW_VALUES`, as at the top level. (b) Inherit the outer `emptyItem`. | (a) | One rule for all levels. An inherited default is hidden. The wiki gives a caution. |
| 4 | Index in messages | (a) A prefix `<descriptor> item <i>: ` at each level, also for a flat collection. (b) Only for a nested collection. (c) No index. | (a) | One format for all books. The index is useful also for a flat collection. Item field messages of existing books change (not the bytes). |
| 5 | Depth limit | (a) 7 levels, as COBOL 85. (b) 2 levels. (c) No limit, only the cycle check. | (a) | It agrees with COBOL, and the code is recursive anyway. |
| 6 | Generator and inner `DEPENDING ON` | (a) Error. (b) Map it as a variable inner collection if the object is in the entry. | (a) | A valid COBOL copybook has the object outside the entry. (b) would accept only a copybook that COBOL rejects. |
| 7 | Direction on an inner collection | (a) Error, as for an item field. (b) Permit the same direction. | (a) | The same rule as an item field. |
| 8 | New generator options | (a) None. (b) New keys for inner tables. | (a) | The qualified COBOL name already finds an inner table. |
