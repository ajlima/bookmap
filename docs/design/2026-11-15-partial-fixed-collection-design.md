# A response that ends inside a fixed collection (design)

Date: 2026-11-15
Status: implemented in v0.26.0.

Related documents: `docs/design/2026-11-11-copymapper-fixed-occurs-design.md` (fixed collections, section 2.2 lists this feature as a non-goal), `docs/design/2026-11-13-empty-item-markers-design.md` (#44), `docs/design/2026-11-14-nested-collections-design.md` (#43), `docs/design/2026-11-14-processor-limits-and-item-errors-design.md` (#92 to #94), GitHub issue #45.

Decisions marked **[builder call]** come from the spec author, not from the project owner. Section 15 lists them for review.

## 1. Intent

A fixed collection (`fixed = true`) always has `maxOccurs` slots. Today the response must contain the full area. Some programs send only the used part of the table:

```cobol
01  LIST-OPEN-ORDERS.
    05  CUSTOMER-ID       PIC X(10).
    05  RETURN-CODE       PIC X(4).
    05  ORDER-COUNT       PIC 9(2).
    05  OPEN-ORDER OCCURS 10.
        10  ORDER-ID      PIC X(12).
        10  ORDER-STATUS  PIC X(2).
```

The response layout has `4 + 2 + 10 × 14 = 146` bytes. A program with 3 open orders puts a message of `4 + 2 + 3 × 14 = 48` bytes on an MQ queue, or in an HTTP body. Today the codec reads this message and throws `ListOpenOrders.orders (order 40): the response is too short for the fixed area`.

This spec adds an opt-in attribute. With it, a response can end on a slot boundary inside the fixed area. The missing slots are unused slots.

## 2. What the code does today

These are facts from `main` (v0.25.0):

- `CollectionCode.readItems` generates the read of a collection. `LayoutCode.decode` calls it for `decodeRequest` and for `decodeResponse`. `readResponse` of a class with subtypes uses the same code.
- The reader is sequential. A response has only the members with an output direction (`OUTPUT` or `INPUT_OUTPUT`), in order. The `INPUT` members are not in the response.
- Count mode: the codec reads `count` items with `take`. Then `BookReader.skipArea` moves over the unused slots. A short response gives one of two messages:
  - `<item field>: response too short, need N bytes at position P but only R remain`, with the prefix `<collection> item <i>: `, if the response ends inside one of the `count` slots.
  - `<field>: the response is too short for the fixed area`, if it ends after them.
- Marker modes (LOW-VALUES, SPACES, key): `BookReader.countUsedSlots` first checks the full area. A short response gives `<field>: the response is too short for the fixed area`.
- An item with subtypes reads its layout, then moves to the end of the slot with the unchecked `BookReader.skip`. The next read or `skipArea` finds a short response.
- A variable collection has no partial behavior. A short response gives the `take` message. The client checks only for a `null` response (`<operation>: empty response from transport`). The codec ignores extra bytes at the end.
- `maxResponseLength()` is the sum of the area lengths of the output members, and the maximum over the layouts of the subtypes. It uses `maxOccurs` for each fixed collection.
- Transports:
  - CTG: `CtgTransport` sends a COMMAREA of `max(request length, maxResponseLength())` bytes. `JavaGatewayConnector` sets the inbound length to the full COMMAREA length. Thus, CTG does not remove the trailing `0x00` bytes, and the response always has the full length.
  - HTTP and MQ: the transport gives the body or the message as the remote program sent it. These transports do not use `maxResponseLength()`. A short response comes from these transports.
  - Simulator: a typed handler uses `encodeResponse`, which always writes the full area. A test makes a short response with a byte-level `SimulatedOperation`.

## 3. Goals, non-goals, and anti-goals

### 3.1 Goals

- A top-level fixed collection can be cut in a response, on a slot boundary.
- All four modes of a fixed collection: count, LOW-VALUES, SPACES, and key.
- Items with subtypes and items with inner fixed collections (the slot is the unit).
- Support in `copymapper-core`, `copymapper-processor`, the copybook generator (options file and command line), and the documentation. 100% line and branch coverage. Mutation score of 95% or more.

### 3.2 Non-goals

- A cut inside a slot (section 4.2).
- A response from a transport that removes trailing `0x00` bytes at any position (decision 2).
- A partial variable collection. Its area already holds only the used items.
- A partial inner collection (section 4.3).
- A partial request. `encodeRequest` and `decodeRequest` do not change.

### 3.3 Anti-goals

- Silent data loss. A read must never drop an item with data. A cut that the codec can detect must give an error.
- A change of behavior for an existing class. Without the new attribute, the full area stays mandatory, with the same messages.
- A change of an existing public signature in `BookReader` or `BookWriter`. Codecs from an older processor must keep working.

## 4. What "ends inside the fixed area" means

The area of a fixed collection starts at offset `A` of the response. It has `maxOccurs` slots of `L` bytes. The response has `R` bytes. The bytes of the area in the response are `P = R - A`.

| Case | Condition | Result with `partial = true` |
|---|---|---|
| Full area | `P >= maxOccurs × L` | As today. Extra bytes are ignored. |
| Cut on a slot boundary | `0 <= P < maxOccurs × L` and `P % L == 0` | `P / L` slots are present. The other slots are missing. |
| Cut inside a slot | `P % L != 0` | `MappingException` (section 7). |
| Cut before the area | `P < 0` | The existing `take` message of the member that is cut. If the reader is already past the end of the data, `presentSlots` throws the existing message `<field>: the response is too short for the fixed area`. |

A response that ends exactly at the start of the area (`P = 0`) has 0 present slots. This is valid.

### 4.1 On a slot boundary

The program calculates the length of the message as "the fixed part, plus the used entries multiplied by the entry length". This gives a cut on a slot boundary. This is the only cut that this spec supports **[builder call]**.

### 4.2 Inside a slot

A cut inside a slot is an error in all modes. The codec cannot tell an item that the program cut from an item with a short value. For example, a text field that is cut reads as a shorter text, without an error. The error protects against this data loss.

Decision 2 gives an alternative for a transport that removes trailing `0x00` bytes.

### 4.3 Inside a nested collection

An inner fixed collection is a part of the outer slot. A cut inside the inner area of an outer slot is a cut inside the outer slot: `MappingException`.

`partial = true` is permitted only on a top-level collection. On a collection in an item class, it is a compile error. An inner collection cannot be at the end of the response if the outer slot is complete.

### 4.4 Inside an item with subtypes

The slot length is the length of the longest layout (existing rule). A cut must be on the boundary of a full slot, also when the last item has a shorter subtype. The reader position after the read of a present slot is then always inside the response. Thus, the unchecked `skip` at the end of a subtype item cannot move past the end of the data.

### 4.5 A class with subtypes

A collection of a base class or of a subtype can have `partial = true`. The rule of section 5 applies in each layout that contains the collection.

## 5. Members after the collection

A cut inside the area removes all bytes after it. Thus, the members after the collection in the response are never in a partial response.

Rule: a collection with `partial = true` must be the last member of the response layout. No member with the direction `OUTPUT` or `INPUT_OUTPUT` can come after it. In a class with subtypes, this includes the members of each subtype layout that contains the collection.

A member with the direction `INPUT` can come after the collection. It is not in the response.

The alternative is to permit members after the collection, and not set them when the area is cut. The codec then cannot tell a cut table from a short response of a different layout version. A full response and a partial response would also set different members. This spec rejects the alternative **[builder call]**.

## 6. Annotation API

Add one attribute to `@Occurs`:

| Attribute | Meaning | Default |
|---|---|---|
| `partial` | `true` if the response can end on a slot boundary inside the fixed area. The missing slots are unused slots. A request always has the full area. | `false` |

```java
@Operation
public class ListOpenOrders {

    @Field(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @Field(order = 20, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @Field(order = 30, pic = "9(2)")          // gets the direction OUTPUT from the collection
    private int orderCount;

    @Occurs(order = 40, maxOccurs = 10, fixed = true, dependsOn = "orderCount",
        partial = true)
    private List<OpenOrder> orders;

    // getters and setters
}
```

The attribute is opt-in, not the default. Reasons:

- In the marker modes, a response that a transport or a gateway cut on a slot boundary is identical to a partial response. With a default of `true`, this cut gives a shorter list without an error. This is the silent data loss of section 3.3.
- Today a short response is often a layout mismatch, for example a program of a different version. The current error shows this mismatch. The default keeps it.
- The rule of section 5 would give a compile error for each existing book with a member after a fixed collection.
- Count mode can detect a cut through the count. The marker modes cannot. The developer selects `partial` for a program that sends the used entries only.

## 7. Compile errors

The processor gives these compile errors:

| Rule | Message |
|---|---|
| `partial = true` without `fixed = true`. | `partial needs fixed = true` |
| `partial = true` with the direction `INPUT`. | `partial needs the direction OUTPUT or INPUT_OUTPUT` |
| `partial = true` on a collection in an item class. | `partial is permitted only on a top-level collection` |
| An output member after the collection in the response layout. | `partial needs the collection at the end of the response: <member> comes after it` |

`<member>` has the format `Owner.field (order n)`. For example, a `returnCode` field at order 50, after the collection, gives `partial needs the collection at the end of the response: ListOpenOrders.returnCode (order 50) comes after it`.

In a class with subtypes, the processor checks each layout that contains the collection. The message names the first member after the collection, for example `ExpressShipment.carrier (order 70)`.

A second collection with `partial = true` in the same layout comes after the first one. Thus, the existing rule gives the error for the first one.

## 8. Behavior

### 8.1 Write

No change. `encodeRequest` and `encodeResponse` always write the full area. They write the `emptyItem` bytes in the unused slots, as today.

### 8.2 Read of a request

No change. `decodeRequest` needs the full area, with the existing messages. The request of an `INPUT_OUTPUT` collection always has the full area. The rule of section 5 does not apply to the request layout.

### 8.3 Read of a response

`decodeResponse` and `readResponse` read a collection with `partial = true` in these steps:

1. The codec calculates the present slots `S` with the new method `BookReader.presentSlots` (section 9). A cut inside a slot throws `MappingException`.
2. Count mode:
   1. The codec reads the count field and checks it, as today. A blank count is 0. A negative count, or a count larger than `maxOccurs`, throws `MappingException`.
   2. If the count is larger than `S`, the codec throws `MappingException`. The program said that it sent more items than the response has.
   3. The codec decodes `count` items. It ignores the other present slots.
3. Marker modes (LOW-VALUES, SPACES, key):
   1. The codec checks only the `S` present slots with the predicate of the mode (#44). A missing slot is an unused slot.
   2. The existing rules apply to the present slots. The list ends at the last present slot with data. An empty slot before a slot with data throws `MappingException`.
   3. The codec decodes the used slots.
4. The reader moves to the end of the present slots. This is the end of the response, or the end of the full area if the response has the full area.

The behavior for a full area is the same as today. The `emptyItem` value has no effect on the missing slots.

### 8.4 Empty-item markers

| Mode | Missing slots | Cut that the codec detects |
|---|---|---|
| Count | Not read. | A count larger than the present slots. A cut inside a slot. |
| LOW-VALUES | Unused. | A cut inside a slot. |
| SPACES | Unused. | A cut inside a slot. |
| Key | Unused. | A cut inside a slot. |

In the marker modes, a cut on a slot boundary after a used slot is not detected. The CAUTION in the documentation (section 13) tells this risk.

### 8.5 Nested collections and subtypes

- An outer slot that is present is complete. The codec reads its inner collections as today.
- The outer marker checks the full outer slot, with the inner area (unchanged).
- A slot with a subtype item is read through the discriminator, as today.

### 8.6 Pagination and reset

No change. Each page is read on its own. On each page, an `OUTPUT` collection adds the decoded items to a new list with the existing items. A partial response on one page has no effect on the next page.

## 9. Runtime API in `copymapper-core`

Add one public method to `BookReader`. The existing signatures do not change.

```java
/**
 * Gives the number of slots of a fixed collection that the data holds, for a response that
 * can end inside the area. The reader does not move.
 *
 * @param field the collection descriptor, for error messages
 * @param slotLength the number of bytes of one slot
 * @param slots the number of slots of the full area
 * @return the number of complete slots, from 0 to {@code slots}
 * @throws MappingException if the data ends inside a slot, or before the start of the area
 */
public int presentSlots(String field, int slotLength, int slots)
```

The method calculates in `long` (#92). The reader position can be past the end of the data, for example after a variable collection of subtype items: the last item ends with the unchecked `skip`. Then the method throws `<field>: the response is too short for the fixed area`, as `skipArea` and `countUsedSlots` do today. The generated code then calls the existing methods with `S` in place of `maxOccurs`:

```java
int ordersPresent = reader.presentSlots(ORDERS, 14, 10);
// Count mode:
int ordersCount = collectionSize(target.getOrderCount(), ORDERS, 10);
if (ordersCount > ordersPresent) {
    throw new MappingException(ORDERS + ": count " + ordersCount
        + " is more than the number of slots in the response (" + ordersPresent + ")");
}
// Marker modes:
int ordersCount = reader.countUsedSlots(ORDERS, 14, ordersPresent, ORDERS_MARKER);
// Both: read the items, then skip the present slots that are not used.
reader.skipArea(ORDERS, (ordersPresent - ordersCount) * 14);
```

`countUsedSlots` checks the length of `S` slots. `skipArea` checks the length of the present slots that are not used. Both checks pass, because `presentSlots` found complete slots. The processor uses this code only in `decodeResponse` and `readResponse`, and only for `partial = true`. The `response` flag of `LayoutCode.decode` selects it.

`maxResponseLength()` does not change. It still uses the full area. CTG still sends a COMMAREA of the full length.

## 10. Messages

Two new runtime messages of `MappingException`. `<field>` has the format `Owner.field (order n)`.

| Message | Cause |
|---|---|
| `<field>: the response ends inside slot S, after B of L bytes` | `partial = true`: the response ends inside slot `S`. The index starts at 0. |
| `<field>: count C is more than the number of slots in the response (P)` | `partial = true`, count mode: the count is larger than the present slots. |

These messages are about the collection. They do not get the prefix of their own collection. A partial collection is always top-level, so they have no prefix.

The existing messages do not change. Without `partial = true`, a short response still gives `<field>: the response is too short for the fixed area`.

### 10.1 Examples

`ListOpenOrders` of section 6. The area starts at offset 6. A slot has 14 bytes.

| Response length | Count field | Result |
|---|---|---|
| 146 | 3 | 3 items. As today. |
| 48 | 3 | 3 items. 3 slots are present. |
| 6 | 0 | An empty list. 0 slots are present. |
| 45 | 3 | `ListOpenOrders.orders (order 40): the response ends inside slot 2, after 11 of 14 bytes` |
| 34 | 3 | `ListOpenOrders.orders (order 40): count 3 is more than the number of slots in the response (2)` |
| 4 | none | `ListOpenOrders.orderCount (order 30): response too short, need 2 bytes at position 4 but only 0 remain` |

The same class in LOW-VALUES mode, without `orderCount`. The area starts at offset 4:

```java
@Occurs(order = 40, maxOccurs = 10, fixed = true, partial = true)
private List<OpenOrder> orders;
```

| Response | Result |
|---|---|
| 46 bytes, 3 slots with data. | 3 items. |
| 46 bytes, slot 2 has only `0x00`. | 2 items. |
| 46 bytes, slot 1 has only `0x00`, slot 2 has data. | `ListOpenOrders.orders (order 40): slot 1 is empty, but slot 2 after it has data` |

## 11. Copybook generator

The copybook does not tell that the program sends only the used entries. Thus, the generator does not guess `partial`. A new option gives it.

### 11.1 The option

| Place | Form |
|---|---|
| Options file | `"partialTables": ["OPEN-ORDER"]`: a list of table names. |
| Command line | `--partial-table OPEN-ORDER`. The option can occur more than one time. The values add to the list of the options file. |

The names follow the existing rules: bare or qualified with `OF` or `IN`, not case-sensitive.

The generator writes `partial = true` on the `@Occurs` of each table in the list.

### 11.2 Diagnostics

The generator gives an error for each wrong table. The message starts with `partial table option: `:

| Rule | Message |
|---|---|
| No item has the name. | The existing message of `findNamed`. |
| The item has no `OCCURS`. | `partial table option: <name> has no OCCURS` |
| The table has `DEPENDING ON`. | `partial table option: <name> has OCCURS DEPENDING ON. Remove the option: the response has only the used entries.` |
| The table is in a table entry. | `partial table option: <name> is in an entry of <outer>. Only a top-level table can end the response.` |
| The table has the direction `INPUT`. | `partial table option: <name> has the direction INPUT. A request always has the full table.` |
| An output item comes after the table. | `partial table option: <name> is not at the end of the response: <item> comes after it` |

The last two checks use the directions that the generator gives to the items. The processor makes the same checks again (section 7).

### 11.3 The places to change

`MappingOptions` gets a new component `List<String> partialTables`. These places build a `MappingOptions` record, and each one must pass the new component:

- `MappingOptions`: the canonical constructor (a copy of the list). A new overload keeps the current constructor with 13 components and gives an empty list. The constructor with 11 components calls this overload, and the other overloads call it through a chain. `defaults()` uses an overload. `withHints` passes `partialTables`.
- `GeneratorConfigJson`: the key `partialTables` in `KEYS`, and the constructor call.
- `Main` (CLI), line 271: the option `--partial-table`, the constructor call, and the usage text.
- `CopybookTree`, line 202: the constructor call. `CopybookTree` is in `copymapper-copybook`. The Gradle plugin (`GenerateCopybookClasses`) and the Maven plugin (`GenerateMojo`) only call `CopybookTree.run`. They need no change, because the option is in the options file. The Maven `Fingerprint` already reads the options files.

The model and the generated classes also change:

- `CollectionMember`: a new component `boolean partial`. A new overload keeps the current constructor.
- `ModelMapper`, line 397: sets `partial`.
- `ModelJson`: writes and reads the key `partial`. A missing key is `false`.
- `JavaClassGenerator`, line 316: writes `partial = true`. It writes nothing for `false`.
- `ModelSnapshot` in `copymapper-processor` (main code that the tests read): a new value `partial` in `Member`.

## 12. Tests

- `copymapper-core`: `presentSlots` for a full area, a longer response, each slot boundary, 0 slots, a cut inside the first and the last slot, a reader position past the end of the data, and a large area in `long`.
- `copymapper-processor`: each compile error of section 7. The generated code for each mode, for a response with 0 slots, some slots, and the full area. Each message of section 10. A class with subtypes, items with subtypes, and items with an inner fixed collection. A class without `partial` keeps the current messages. An `INPUT_OUTPUT` collection: the request needs the full area, the response can be partial.
- `copymapper-transport-simulator`: a byte-level `SimulatedOperation` returns a short response.
- `copymapper-copybook`, CLI, and plugins: the option in the options file and on the command line, each diagnostic of section 11.2, the model JSON, and the generated annotation.
- `copymapper-examples`: the example `ListOpenOrders` in package `pt.dazz.copymapper.examples.orders`, with a round-trip test through the simulator.

## 13. Documentation

- `docs/wiki/Collections-and-fixed-OCCURS.md`: a new section "Partial responses". Change the line "The response must contain the full area, in all modes" in "Rules for all modes". Add the two messages to the table of "Messages". Add a CAUTION: "Use `partial` only for a program that sends the used entries. In the marker modes, a response that a gateway cuts on a slot boundary gives a shorter list without an error."
- `docs/wiki/Annotations-reference.md`: the attribute `partial`.
- `docs/wiki/Error-handling.md`: a reference to the new messages.
- `docs/wiki/FAQ-and-troubleshooting.md`: change the line "For a fixed collection, the response must contain the full area." Refer to `partial`.
- `docs/wiki/Copybook-generator.md` and `docs/wiki/Copybook-generator-CLI.md`: the option `partialTables` and `--partial-table`.
- `docs/wiki/Transports-and-simulator.md`, `docs/wiki/HTTP-transport.md`, and `docs/wiki/IBM-MQ.md`: a short note that these transports give the response as the program sent it.
- Javadoc of `@Occurs` and of `BookReader`.
- `docs/design/2026-11-11-copymapper-fixed-occurs-design.md` section 2.2: refer to this spec.
- `ROADMAP.md` line 36 (#45): remove the item after the release.

## 14. Release

A `feat:` commit merged to `main` publishes a new minor version. The owner must approve the merge.

## 15. Decisions for review

1. Opt-in through the new attribute `partial` on `@Occurs`, default `false`. Other names: `endsResponse`, `shortResponse`. A default of `true` would hide a cut in the marker modes.
2. Only a cut on a slot boundary (section 4.1). A cut inside a slot is an error in all modes. The alternative is to fill the missing bytes with `0x00` up to `maxResponseLength()`. This alternative supports a transport that removes trailing `0x00` bytes:
   - LOW-VALUES, SPACES, and key modes would read the filled slots as unused. They would also read a last item whose bytes end in `0x00`.
   - Count mode would read the filled bytes of the counted items. A cut item would read with `0x00` values, or fail on a `DISPLAY` or `COMP-3` field.
   - The members after the collection could then stay in the layout. They would read as blank.
   - A text field that a program cut would read as a shorter text, without an error.
   - The CTG transport of CopyMapper does not remove the bytes. This spec does not take the alternative.
3. The collection must be the last member of the response layout (section 5). A member with the direction `INPUT` can come after it.
4. `partial` only on a top-level collection. An inner collection cannot be `partial`.
5. A cut must be on the boundary of a full slot, also for a last item with a shorter subtype.
6. Count mode: a count larger than the present slots is an error. Present slots after the count are ignored.
7. Marker modes: a missing slot is an unused slot. The `emptyItem` value has no effect on a missing slot.
8. A response that ends at the start of the area (0 present slots) is valid. In count mode, the count must then be 0.
9. The write direction and the read of a request do not change: `encodeRequest`, `encodeResponse`, and `decodeRequest` always use the full area.
10. The two new messages of section 10 do not use the form `<collection> item <i>: `. They are about the collection, as the existing collection messages.
11. `maxResponseLength()` does not change.
12. The copybook generator gets the option `partialTables` and `--partial-table` (section 11). The Gradle and Maven plugins get no new setting.
