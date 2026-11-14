# Processor limits and item field errors (design)

Date: 2026-11-14
Status: proposed.
Issues: #92, #93, #94
Parent specs: `2026-11-10-bookmap-design.md`, `2026-11-14-nested-collections-design.md` (section 8)

This spec has three small changes from the triage of #54. They are independent. Each change gets its own commit.

Decisions marked **[builder call]** come from the spec author, not from the project owner. Section 6 lists them for review.

## 1. Intent

- #92: The processor adds the byte lengths of a layout in `int`. A very large layout overflows. Then `maxResponseLength()` and the generated offsets are wrong, and javac shows no error.
- #93: A read or write error in an item field names only the item class field, for example `ShipmentLine.sku (order 1)`. The message does not tell which collection or which item has the error.
- #94: `PicParser` upper-cases the `pic` string with `Locale.ROOT`. Then `ſ9(5)` reads as `S9(5)`, and `PıC` reads as `PIC`. A typing error or a copy from a document gives a valid field with no error.

## 2. Goals and non-goals

Goals:

- A compile error for each layout that is longer than 2147483647 bytes.
- The collection and the item index in each message about an item, at all levels.
- The same exception object, with the same class, after the codec adds the index.
- A compile error for each `pic` string with a character that is not ASCII. The message shows the original character.

Non-goals:

- No limit for a transport. A COMMAREA limit is a check of the transport or of the copybook generator option `maxRecordLength`.
- No new index format. The codec uses the prefix of the nested collections spec.
- No change to `BookReader` or `BookWriter`.

## 3. #92: record length in `long`

### 3.1 The int sites

These sites add or multiply byte lengths in `int` today:

| Site | Computation |
|---|---|
| `BookWriter.byteLength`, called by `FieldResolver` | `length + 1` for a `DISPLAY` number with a separate sign. |
| `ModelBuilder.itemLayout` | The sum of the members of the item class, and `longest` for subtypes. |
| `CollectionModel.areaLength` | `maxOccurs * item.byteLength()`. |
| `CodecGenerator` (`maxResponseLength`) | The sum of the `OUTPUT` members of each operation layout. |
| `ModelBuilder.collection` | `emptyKeyOffset`, the sum of the members before the key. |
| `ItemMethodsGenerator`, `SubTypesGenerator` | The offset of a discriminator, the sum of the members before it. |
| `CollectionCode` (generated code) | `(maxOccurs - count) * itemLength` for the unused slots. |

A `pic` repeat count can be 999999999, and `PicParser` accepts a total length up to 2147483647. `FieldResolver` limits the digits of `int` and `long` fields, but not of a `BigDecimal` field. Thus, one field can overflow: `S9(2147483647) SIGN LEADING SEPARATE` gives a byte length of `-2147483648`.

### 3.2 The check

The check runs bottom-up, before the processor keeps a length in an `int`. Each step adds the lengths in `long`. If a step finds an error, the member gets no model. Thus, an outer step never adds a wrapped value, and it does not report the same problem again.

1. `FieldResolver`: the byte length of a field. Only a `DISPLAY` number with a separate sign and 2147483647 digits can be too long.
2. `ModelBuilder.itemLayout`: the length of each layout of an item class. With subtypes, this is the base layout (if it is not abstract) and each subtype layout. The entry length is the longest layout.
3. `ModelBuilder.collection`: the area of the collection, `(long) maxOccurs * entry length`.
4. `ModelBuilder.build`: the length of each layout of the operation class (`Layout.of`): the class itself, or the base layout and each subtype layout.

The record is all the members of a layout, in both directions. **[builder call]** The request and the response each contain a part of these members. Thus, each direction is also safe.

The maximum is `Integer.MAX_VALUE`. A layout of exactly 2147483647 bytes passes.

After the check, all the other sites of section 3.1 are safe: each offset and each area is a part of a checked layout. Do not change them.

### 3.3 The messages

The messages follow the convention of `Problems`: `<descriptor>: ...` on a member, `<SimpleClassName>: ...` on a class.

| Step | Element | Message |
|---|---|---|
| 1 | The field | `<field>: the field has 2147483648 bytes, more than the maximum of 2147483647 bytes` |
| 2 | The collection | `<collection>: item class <ItemClass> has <N> bytes, more than the maximum of 2147483647 bytes` |
| 2 | The collection | `<collection>: subtype <SubtypeClass> of item class <ItemClass> has <N> bytes, more than the maximum of 2147483647 bytes` |
| 3 | The collection | `<collection>: the collection has <N> bytes (maxOccurs <M> x <E> bytes), more than the maximum of 2147483647 bytes` |
| 4 | The operation class | `<OperationClass>: the record has <N> bytes, more than the maximum of 2147483647 bytes` |
| 4 | The operation class | `<OperationClass>: the record of subtype <SubtypeClass> has <N> bytes, more than the maximum of 2147483647 bytes` |

Examples:

```
GetShipments.packages (order 30): the collection has 4294967296 bytes (maxOccurs 4096 x 1048576 bytes), more than the maximum of 2147483647 bytes
GetShipments: the record has 2147483650 bytes, more than the maximum of 2147483647 bytes
```

The words "has N bytes" are the words of the copybook generator warning (`record GET-INVOICE has 35556 bytes, more than ...`).

### 3.4 Tests

- One test for each message, with the boundary: 2147483647 bytes passes, 2147483648 bytes fails.
- A test with an inner collection: the inner area fails, and the outer collection gives no second error.
- The tests use large `maxOccurs` values. The processor does not allocate memory for a layout, so the tests are fast.

## 4. #93: the collection and the index in an item error

### 4.1 Today

The nested collections spec (section 8) added the prefix `<collection> item <i>: ` for a collection in a nested path. The generated code catches a `MappingException` of an item. Then the helper `atItem` throws a new `MappingException`, with the original exception as the cause.

This has three problems:

- A collection that is not in a nested path has no prefix. This is the case of #93.
- A subclass of `MappingException`, for example from a converter, becomes a plain `MappingException`. The subclass is only in the cause.
- Each level adds one more wrapper to the cause chain.

### 4.2 The message format

All collections get the prefix `<collection> item <i>: `. The index starts at 0. Each level adds its prefix, outer level first. This is the format of the nested collections spec. There is no new format.

Single collection, write:

```
CreateShipment.lines (order 30) item 2: ShipmentLine.sku (order 1): text is longer than 10 characters (12)
```

Single collection, read:

```
GetShipment.lines (order 30) item 0: ShipmentLine.quantity (order 2): byte 0x4B at offset 3 of the field is not valid in a zoned decimal
```

Nested collections (unchanged):

```
GetShipment.packages (order 30) item 1: ShipmentPackage.lines (order 40) item 0: ShipmentLine.sku (order 10): text is longer than 10 characters (12)
```

An item subtype message gets the prefix too:

```
CreateShipment.lines (order 30) item 2: ShipmentLine: unknown subclass com.example.SpecialLine
```

### 4.3 The messages that keep their format

These messages are about the collection, not about one item field. They do not get the prefix of their own collection. An outer collection adds its prefix, as today.

| Message | Index |
|---|---|
| `<collection>: the list has N items, more than maxOccurs M` | None. |
| `<collection>: count V is not between 0 and maxOccurs M` | None. |
| `<collection>: item I is all LOW-VALUES, so a read cannot find it` | `item I` after the colon. |
| `<collection>: item I is all spaces, so a read cannot find it` | `item I` after the colon. |
| `<collection>: item I has an empty key K, so a read cannot find it` | `item I` after the colon. |
| `<collection>: slot A is empty, but slot B after it has data` | `slot` numbers. |
| `<collection> slot S: byte 0x.. at offset N of the field is not valid in a zoned decimal` | `slot S` before the colon. |

**[builder call]** The three empty-item messages keep the form `: item I is ...`. They tell about the item as a whole, and they already name the index. The generated code throws them after the write of the item, outside the `try` block of the item. Thus, they do not get the prefix of their own collection.

A nested example from the code of `writeFixedItems`, today and after the change:

```
CreateShipment.packages (order 30): item 1 is all LOW-VALUES, so a read cannot find it
CreateShipment.packages (order 30) item 1: ShipmentPackage.lines (order 40): item 2 is all LOW-VALUES, so a read cannot find it
```

**[builder call]** The null item message gets the index: `<collection>: item I is null`. Today it is `<collection>: the list contains a null item`. After the change, all collections loop with an index, so the index is known. The form is the form of the empty-item messages.

### 4.4 The mechanism: keep the exception object

Add one public method to `MappingException` in `bookmap-core`:

```java
/**
 * Adds the collection and the index of an item to the start of the message.
 * The generated codecs call this method. Do not call it from application code.
 *
 * @param collection the descriptor of the collection
 * @param index the index of the item, from 0
 * @return this exception
 */
public MappingException atItem(String collection, int index)
```

- The exception keeps a prefix in a private field. `atItem` adds `<collection> item <index>: ` to the start of the prefix.
- `getMessage()` gives the prefix, then the original message.
- The method returns `this`. The generated code is `throw e.atItem(LINES, i);`.
- The class, the cause, and the stack trace do not change.

Remove the helper `AT_ITEM` from `HelperMethods`. Remove the field `indexed` from `CodecMembers.CollectionNames`. `CollectionCode.onItem` always adds the `try` block. The `for (Item item : list)` loop of a flat collection becomes an indexed loop.

The behavior for each exception from an item:

| Exception | Result |
|---|---|
| `MappingException` | The same object, with the prefix. |
| A subclass of `MappingException` | The same object and the same class, with the prefix. |
| Another `BookmapException`, for example a `BusinessException` from a converter | Unchanged, without the prefix. The wiki says that it goes to the caller unchanged. |
| Another `RuntimeException` from a converter | `BookReader` and `BookWriter` already make a `MappingException` (`converter failed`). Then it gets the prefix. |
| Another `RuntimeException` from a getter or a setter | Unchanged, as today. |

A `try` block costs nothing when no exception occurs. A `MappingException` from an inner level gets all prefixes on the same object. Thus, the cause chain has no wrapper.

### 4.5 Changes to the current promises

Three places say that a flat collection keeps the messages without an index. Change them:

- `docs/design/2026-11-14-nested-collections-design.md`, section 8: "A collection that is not in a nested path does not get the prefix." Do not change an implemented spec. This spec replaces the rule.
- `docs/wiki/Collections-and-fixed-OCCURS.md`, "Messages with an index": remove "A collection without an inner collection, and not in an item class, keeps the messages without an index."
- The test `NestedCollectionTest.flatCollectionMessagesHaveNoIndex`: change it to expect the prefix. A nested test (`NestedCollectionTest`, line 418) expects a wrapper in the cause. Change it to expect the original cause.

### 4.6 Documentation

- `docs/wiki/Error-handling.md`, section "Fields": add that a message about an item field starts with `<collection> item <i>: `, with one example. Add the null item message.
- `docs/wiki/Collections-and-fixed-OCCURS.md`: the change of section 4.5, and the null item message in the table.

### 4.7 Tests

- A flat collection: a write error and a read error of an item field have the prefix.
- A subclass of `MappingException` from a converter in an item keeps its class, and has the prefix.
- A `BusinessException` from a converter in an item is unchanged.
- A nested error has two prefixes and no wrapper in the cause.
- A unit test of `MappingException.atItem` and `getMessage` in `bookmap-core`.

## 5. #94: reject non-ASCII characters in a `pic` string

### 5.1 The processor

`PicParser.parse` checks the original string first, before `strip()` and before `toUpperCase`. `strip()` removes the Unicode spaces too, for example U+2003, so the check must come before it.

The check reads the string by code point. The first code point above U+007F gives a `PicSyntaxException`. The message shows the original character and its code point, because many of these characters look like ASCII letters.

Message:

```
<field>: Invalid PIC string "<original>": character '<c>' (U+XXXX) is not ASCII
```

Examples:

```
GetOrder.amount (order 20): Invalid PIC string "ſ9(5)": character 'ſ' (U+017F) is not ASCII
GetOrder.code (order 10): Invalid PIC string "PıC X(5)": character 'ı' (U+0131) is not ASCII
```

The code point has 4 hexadecimal digits, or more for a supplementary character (`U+1D7D7`).

After the check, `toUpperCase(Locale.ROOT)` changes only ASCII letters. **[builder call]** The other messages keep the upper-case text, for example `unexpected text 'FOO' after the usage`. The full original string is in the message, so the user can find the text.

### 5.2 `EditedPicture` in `bookmap-core`

`EditedPicture.parse` and `EditedPicture.isEdited` are public, and they upper-case in the same way. Their callers in BookMap give them a checked string: `PicParser`, the copybook `Picture`, and the generated codecs.

**[builder call]** Add the same check to `EditedPicture.parse`. It throws `IllegalArgumentException` with the message `character 'ſ' (U+017F) is not ASCII`. `isEdited` gives a boolean and does not change. This protects a direct call of the public API.

### 5.3 The copybook generator

`bookmap-copybook` has the same problem in two places:

- `Lexer.word` upper-cases a `PICTURE` token and a `WORD` token with `Locale.ROOT`. Thus, `PıC` becomes the keyword `PIC`, and `ſ9(5)` becomes `S9(5)`.
- `Picture.parse` upper-cases the picture again.

A copybook can have non-ASCII text in a literal, in pseudo-text, and in a comment. The fix must not reject these.

The proposed fix:

1. `Lexer` keeps the original text of a `PICTURE` token.
2. `Picture.parse` checks the original text first, with the message `picture string 'ſ9(5)' has the character 'ſ' (U+017F), which is not ASCII`. `DataParser` gives it as an error diagnostic at the position of the token, as for the other picture errors.
3. `Lexer` upper-cases only the ASCII letters of a `WORD` token. Thus, a word with a non-ASCII character never matches a keyword. The parser then reports the word as for each other unknown word.

**[builder call]** Do this fix in the same branch, in its own commit. The generator gives its `pic` strings to the processor, so the two parsers must agree.

### 5.4 Tests

- `PicParserTest`: `ſ9(5)`, `PıC X(5)`, a non-ASCII space (U+2003) before the picture, a supplementary character, and `ß` (upper case `SS`).
- `EditedPicture`: one non-ASCII picture.
- Copybook: `PIC ſ9(5)` gives the error, `PıC X(5)` does not map to a field, and a non-ASCII literal in a `VALUE` clause still works.

## 6. Decisions for review

1. #92: The record is all members of a layout, in both directions, not each direction alone. A layout where only the sum of both directions is too long gets an error.
2. #92: The check also covers one field: a `DISPLAY` number with a separate sign and 2147483647 digits.
3. #92: The copybook generator also adds lengths in `int` (`RedefinesResolver.length` and `RecordLength`). This spec does not change it. Open a follow-up issue.
4. #93: All collections get the prefix `<collection> item <i>: `. This changes the messages of flat collections, against the promise of the nested collections spec, of the wiki, and of the test `flatCollectionMessagesHaveNoIndex`.
5. #93: The new public method `MappingException.atItem` changes the exception in place, and `getMessage()` adds the prefix. This keeps the class, the cause, and the stack trace. The alternative, a new `MappingException` with the cause, loses the subclass. The method is a `feat:` change in `bookmap-core`.
6. #93: A `BookmapException` that is not a `MappingException` stays unchanged, without the index.
7. #93: The three empty-item messages keep the form `<collection>: item I is ...`. They do not change to `<collection> item I: ...`.
8. #93: The null item message changes to `<collection>: item I is null`.
9. #94: The message shows the character and its code point: `character 'ſ' (U+017F) is not ASCII`.
10. #94: The other `PicParser` messages keep the upper-case ASCII text.
11. #94: `EditedPicture.parse` in `bookmap-core` gets the same check.
12. #94: The copybook generator gets the fix of section 5.3 in the same branch: a check of the picture string, and ASCII-only upper case for words.
