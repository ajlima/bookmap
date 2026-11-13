# Empty-item markers of a fixed collection (design)

Date: 2026-11-13
Status: approved.

Related documents: `docs/design/2026-11-11-bookmap-fixed-occurs-design.md` (fixed collections, section 2.2 lists this feature as a non-goal), `docs/design/2026-11-13-count-field-design.md` (the generator option `countFields`), `docs/design/2026-11-10-bookmap-design.md` (the main design), GitHub issue #44.

Decisions marked **[builder call]** come from the spec author, not from the project owner. Section 10 lists them for review.

## 1. Intent

Without a count field, a fixed collection (`fixed = true`) finds the unused slots through LOW-VALUES (bytes `0x00`). Many COBOL programs do not use LOW-VALUES. They prepare the table with one of these statements:

| COBOL statement | Bytes of an unused slot |
|---|---|
| `MOVE LOW-VALUES TO ORDER-TABLE` | All `0x00`. |
| `MOVE SPACES TO ORDER-TABLE` | All spaces of the charset (`0x40` in EBCDIC, `0x20` in ASCII). |
| `INITIALIZE ORDER-TABLE` | Spaces in the `X` items, zeros in the `9` items (`DISPLAY`, `COMP-3`, `COMP`). |
| `MOVE ZERO TO PRODUCT-ID (I)` | Only the key item is zero. The other bytes can have old data. |

Today, a slot with spaces reads as an item with empty fields. This is a wrong list, without an error.
This spec adds two markers: SPACES, and a key field that is blank or zero.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals

- A fixed collection without a count field can find empty slots through LOW-VALUES (as today), SPACES, or a key field.
- The same marker gives the bytes that a write puts in the unused slots.
- Support in `bookmap-core`, `bookmap-processor`, the copybook generator (options file and command line), and the documentation. 100% line and branch coverage.

### 2.2 Non-goals

- A marker for each subtype. The marker is for the full slot.
- A text key with the character `0` as the empty value (see decision 4).
- A marker of a variable collection. The count field gives the number of items there.
- A guess of the marker by the copybook generator.

### 2.3 Anti-goals

- Silent data loss. A read must never drop an item with data. A write must never produce bytes that read back as a different list.
- A change of behavior for an existing class. The default stays LOW-VALUES.
- A change of an existing public signature in `BookReader` or `BookWriter`. Codecs from an older processor must keep working.

## 3. Annotation API

A new enum in `pt.dazz.bookmap.annotation`:

```java
public enum EmptyItem {
    /** An unused slot has only LOW-VALUES (0x00). */
    LOW_VALUES,
    /** An unused slot has only spaces. A read also accepts LOW-VALUES in the slot. */
    SPACES
}
```

Two new attributes of `@BookCollection`:

| Attribute | Meaning | Default |
|---|---|---|
| `emptyItem` | The bytes of an unused slot. Without `emptyKey` and without `dependsOn`, it also tells which slot is empty. | `EmptyItem.LOW_VALUES` |
| `emptyKey` | The name of a field of the item class. A slot is empty when this field is blank or zero. | `""` (no key) |

```java
// MOVE SPACES TO the table.
@BookCollection(order = 30, maxOccurs = 20, fixed = true, emptyItem = EmptyItem.SPACES)
private List<OrderLine> lines;

// INITIALIZE the table, or MOVE ZERO to the key of an unused entry.
@BookCollection(order = 30, maxOccurs = 20, fixed = true, emptyKey = "productId")
private List<OrderLine> lines;

// Both: the key marks the empty slot, and a write fills the unused slots with spaces.
@BookCollection(order = 30, maxOccurs = 20, fixed = true, emptyKey = "productId",
    emptyItem = EmptyItem.SPACES)
private List<OrderLine> lines;
```

This gives four modes of a fixed collection:

| Mode | Attributes | Empty slot on read | Unused slot on write |
|---|---|---|---|
| LOW-VALUES (today) | none | All bytes `0x00`. | `0x00`. |
| SPACES | `emptyItem = SPACES` | Each byte is a space or `0x00`. | Spaces. |
| Key | `emptyKey = "k"` | The key is blank or zero (5.2). | The `emptyItem` bytes, and the key as zero (5.3). |
| Count | `dependsOn = "c"` | Not checked: the count field gives the items. | The `emptyItem` bytes. |

## 4. Compile rules

The processor gives these compile errors:

| Rule | Message |
|---|---|
| `emptyItem` or `emptyKey` on a collection without `fixed = true`. | `emptyItem and emptyKey need fixed = true` |
| `emptyKey` together with `dependsOn`. | `emptyKey is not permitted with dependsOn: the count field gives the number of items` |
| The key is not a field of the item class. With subtypes, the key must be a field of the base class (or of one of its superclasses), not of a subtype. | `emptyKey field 'k' is not a field of item class X` |
| The key has a type other than `String`, `int`, `Integer`, `long`, `Long`, or `BigDecimal`, or it has a converter, or it is an enum or a date. | `emptyKey field 'k' must be String or a number without a converter` |

The key is a field of the base layout, so its offset in the slot is a compile-time constant. The item fields have the direction of the collection, so the offset is the same in each direction.

## 5. Behavior

### 5.1 One rule for read and write

Each mode has one predicate "the slot is empty". One method in `bookmap-core` gives this predicate for read and for write. Thus, the two sides cannot be different. The codec obeys two invariants:

1. The bytes of an unused slot, as a write makes them, satisfy the predicate.
2. A write rejects each item whose bytes satisfy the predicate.

A read finds a slot as empty only through the predicate. Thus, a list that a write accepts reads back as the same list.

### 5.2 The predicates

- **LOW-VALUES:** all bytes of the slot are `0x00`. The check does not use the charset (unchanged).
- **SPACES:** each byte of the slot is the space of the context charset or `0x00`. This is the existing rule of a blank field. A read thus accepts a table that the program cleared with LOW-VALUES **[builder call]**.
- **Key, `String` key:** the key bytes are blank (spaces or `0x00`).
- **Key, numeric key:** the codec decodes the key bytes with the `PIC` and the usage of the key. The slot is empty if the value is blank (`DISPLAY` and `COMP-3` with only spaces or `0x00`) or zero (also `-0`). A `COMP` key is empty only if its value is zero: spaces in a `COMP` field are a number that is not zero.
- **Key, invalid bytes:** a numeric key with bytes that the codec cannot decode throws `MappingException`, as for a normal field. The message contains the collection descriptor and the slot index.

In key mode, the other bytes of the slot do not have an effect. The program uses the key to mark the entry, and the other bytes can have old data. This is the contract of the key mode: the developer selects it for a program that obeys it.

### 5.3 Write

1. A list larger than `maxOccurs`, a `null` list, and a `null` item: unchanged.
2. The codec writes each item. A shorter subtype item gets spaces at the end, up to the item length (unchanged).
3. Without a count field: if the bytes of an item satisfy the predicate, the codec throws `MappingException`:
   - LOW-VALUES: `<field>: item I is all LOW-VALUES, so a read cannot find it` (unchanged).
   - SPACES: `<field>: item I is all spaces, so a read cannot find it`.
   - Key: `<field>: item I has an empty key k, so a read cannot find it`.
4. The codec writes the unused slots, up to `maxOccurs`:
   - The bytes of `emptyItem` (`0x00` or spaces) in the full slot.
   - Key mode with a numeric key: the key gets the value zero, encoded with its `PIC` and usage (`0` digits in `DISPLAY`, a zero with the sign nibble in `COMP-3`, `0x00` bytes in `COMP`). A COBOL check such as `IF PRODUCT-ID = ZERO` is then true. A `String` key keeps the `emptyItem` bytes, which are blank.

### 5.4 Read

1. Count mode: unchanged. The `emptyItem` value has no effect on a read.
2. Other modes: the codec checks each slot with the predicate, before the discriminator of a subtype item. The rules of the LOW-VALUES mode apply (unchanged):
   - The list ends at the last slot with data.
   - An empty slot before a slot with data throws `MappingException`: `<field>: slot A is empty, but slot B after it has data`.
   - The response must contain the full area: `<field>: the response is too short for the fixed area`.
3. The cursor moves to the end of the full area.

### 5.5 Pagination, reset, and subtypes

Unchanged. A subtype item is checked on the full slot (SPACES), or on the key of the base class (key mode).

## 6. Runtime API in `bookmap-core`

Add a public final class `pt.dazz.bookmap.codec.EmptySlot`. It is immutable. The generated codec keeps one instance in a `static final` field for each collection with a marker other than LOW-VALUES.

```java
public static EmptySlot lowValues();
public static EmptySlot spaces();
public EmptySlot withTextKey(int offset, int length);
public EmptySlot withNumberKey(int offset, int length, int scale, boolean signed, Usage usage);
public boolean isEmpty(byte[] data, int from, int slotLength, Charset charset);
```

New public methods. The existing methods do not change, and they call the new methods with `EmptySlot.lowValues()`:

- `BookReader.countUsedSlots(String field, int slotLength, int slots, EmptySlot marker)`.
- `BookWriter.writeEmptySlots(String field, EmptySlot marker, int slotLength, int slots)`.
- `BookWriter.isEmptySlotFrom(EmptySlot marker, int start)`.

A codec for a LOW-VALUES collection stays the same as the code of the former processor. Thus, the generated code changes only for the new modes **[builder call]**.

## 7. Copybook generator

The generator does not guess the marker. Two new options give it, as `countFields` does:

| Option | Options file | Command line |
|---|---|---|
| `emptyItems` | `"emptyItems": { "ORDER-LINE": "SPACES" }`. The values are `SPACES` and `LOW-VALUES`. | `--empty-item ORDER-LINE=SPACES` |
| `emptyKeys` | `"emptyKeys": { "ORDER-LINE": "PRODUCT-ID" }` | `--empty-key ORDER-LINE=PRODUCT-ID` |

- A name can be qualified with `OF` or `IN`. A command line value has priority over the options file for the same table.
- The Gradle plugin and the Maven plugin use the options file `<NAME>.json`. They get no new property.
- `MappingOptions` gets the components `emptyItems` and `emptyKeys`. The existing constructors stay. `CopybookTree` and `Main` rebuild the options record, so they copy the new components. A test covers each place.
- The mapper writes `emptyItem` and `emptyKey` on the generated `@BookCollection`.

The mapper gives these errors:

| Check | Message |
|---|---|
| The table exists, is not ambiguous, and has `OCCURS` without `DEPENDING ON`. | The messages of `countFields`, with the label `empty item option` or `empty key option`. |
| The value of `emptyItems` is `SPACES` or `LOW-VALUES`. | `empty item option: X has the unknown value V. Use SPACES or LOW-VALUES.` |
| The key is an elementary item of the table entry. It is not in a `REDEFINES` alternative of the entry. | `empty key option: K is not an elementary item of the entry of T` |
| The key maps to a `String` or a number field (not a date hint, not a level 88 enum). | `empty key option: K must map to a text or a number field` |
| A table with an empty key has no count field (`countFields`). | `empty key option: T has a count field. Remove one of the two options.` |

The existing `info` diagnostic for a fixed table of `COMP` items (`a COMP value of 0 is all LOW-VALUES and cannot be written`) does not apply to a table with an empty key. Its text also gives the option `emptyKeys`.

The model JSON gets two optional keys on a collection member: `emptyItem` (`"SPACES"` or `"LOW_VALUES"`) and `emptyKey` (the Java field name). The writer writes them only if they are not the default. `formatVersion` stays `1`. A model from a former version is still valid. A former reader rejects a model with the new keys, with the error `unknown key` **[builder call]**.

## 8. Documentation

- `docs/wiki/Collections-and-fixed-OCCURS.md`: the new modes, the table of section 3, the new messages, and a change of the CAUTION about spaces.
- `docs/wiki/Annotations-reference.md`: the attributes `emptyItem` and `emptyKey`, and the enum `EmptyItem`.
- `docs/wiki/Copybook-generator.md`: the options `emptyItems` and `emptyKeys`, the command line options, and the `COMP` diagnostic.
- `docs/wiki/Home.md` and `docs/wiki/FAQ-and-troubleshooting.md`: remove the limitation "only LOW-VALUES or a count field".
- `ROADMAP.md`: remove #44 after the release.
- The Javadoc of `@BookCollection` and of `EmptyItem`.
- `bookmap-examples`: one example with `emptyKey` and international business data (orders).

## 9. Release

A `feat:` commit merged to `main` publishes a new minor version. The owner must approve the merge.

## 10. Decisions for review

1. **SPACES also accepts LOW-VALUES on read.**
   Options: (a) a SPACES slot has only spaces; (b) each byte is a space or `0x00`.
   Choice: (b). A CICS program often gets a COMMAREA with `0x00` and then moves spaces to only a part. With (a), a slot with only `0x00` reads as a phantom item. A write always uses spaces, so the round trip is safe.

2. **Two attributes, not one enum value for the key.**
   Options: (a) `emptyItem = KEY` and `emptyKey = "k"`; (b) `emptyKey` alone selects key mode, and `emptyItem` gives the fill bytes.
   Choice: (b). The two attributes are independent: the key tells which slot is empty, and `emptyItem` tells which bytes a write puts in an unused slot. There is no invalid combination such as `KEY` without a name.

3. **`emptyItem` with `dependsOn`.**
   Options: (a) a compile error; (b) `emptyItem` gives only the fill bytes of the unused slots.
   Choice: (b). A program that reads a request in count mode can expect spaces in the unused slots. `emptyKey` with `dependsOn` stays a compile error: the key would have no effect.

4. **A text key with `0` characters.**
   Options: (a) a `String` key `"000000"` is empty; (b) only a blank `String` key is empty.
   Choice: (b). The digits `0` in an `X` field can be a valid code. A numeric key covers the case "key with zeros". To use zeros, map the key as a number.

5. **An empty slot before a slot with data.**
   Choice: keep the `MappingException`, in all modes. In key mode, a program can clear an entry in the middle of the table. A skip of that slot would change the index of the next items, and the next write would move them. The error tells the developer to use a count field.

6. **The key mode ignores the other bytes of the slot.**
   Options: (a) the key decides; (b) the key and the other bytes must be blank.
   Choice: (a). A program that moves zero to the key of a removed entry keeps the old data in the other items. With (b), that slot reads as an item.

7. **Key types.**
   Choice: `String` and numbers (`int`, `Integer`, `long`, `Long`, `BigDecimal`), without a converter. An enum, a date, or a converter has no clear "zero" value.

8. **Model JSON version.**
   Options: (a) a new `formatVersion` 2; (b) optional keys in version 1.
   Choice: (b). The new keys are optional, and all former models stay valid. A former reader gives a clear `unknown key` error for a new model.
