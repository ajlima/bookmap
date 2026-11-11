# Fixed OCCURS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed collection (`@BookCollection(fixed = true)`) for COBOL `OCCURS n` without `DEPENDING ON`, with an optional count field and LOW-VALUES detection of empty slots.

**Architecture:** `bookmap-core` gets the annotation attribute and three small helpers on `BookReader` and `BookWriter`. The processor reads `fixed`, does not apply the count-field convention to a fixed collection, and generates code that always writes and skips the full area. The codec stays sequential, so members after a fixed collection land at constant offsets with no other change.

**Tech Stack:** Java 21, Gradle 9, JavaPoet, google compile-testing, JUnit 5, AssertJ, JaCoCo (100% line and branch).

**Spec:** `docs/superpowers/specs/2026-11-11-bookmap-fixed-occurs-design.md`

## Global Constraints

- Java 21. No runtime reflection in `main` code. JavaPoet for generated code.
- `bookmap-core` and `bookmap-processor` take no new dependency.
- Do not change the signature of any existing public method in `bookmap-core`: codecs from the 0.2.0 processor must keep working.
- 100% line and branch coverage in every module (`./gradlew build` runs the JaCoCo verification).
- English for code and docs. ASD-STE100 for docs. International business data in tests (orders, invoices, shipments).
- Commit email `alexandre.lima@dazz.pt`. No Claude signature and no `Co-Authored-By` trailer.
- Work in the worktree `.worktrees/fixed-occurs`, branch `feat/fixed-occurs`. One commit per task at minimum.

## Review Focus

1. A response that ends inside the fixed area (fixed collection is the last member): expect `MappingException` ("too short"), never `ArrayIndexOutOfBoundsException`. Test in Task 1 (`countUsedSlots`) and Task 3.
2. A slot of EBCDIC spaces (`0x40`) under `Cp037`: it is **not** empty. Test in Task 1.
3. An item whose bytes are all `0x00` in **count** mode: write and read succeed (the check applies only to LOW-VALUES mode). Test in Task 3.
4. A `null` list in LOW-VALUES mode: the whole area is `0x00`, and a read gives an empty list. Test in Task 3.
5. Pagination of an `OUTPUT` fixed collection in a `Continuable` operation: items of page 2 are added to page 1. Test in Task 4 (simulator round-trip).

---

### Task 1: Core — annotation attribute and reader/writer helpers

**Files:**
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/annotation/BookCollection.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/codec/BookReader.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/codec/BookWriter.java`
- Test: `bookmap-core/src/test/java/pt/dazz/bookmap/codec/LowValuesTest.java` (new)

**Interfaces:**
- Produces:
  - `boolean BookCollection.fixed() default false`. Update the Javadoc of `dependsOn()`: for a fixed collection, an empty value means "no count field"; the "member immediately before" convention applies only when `fixed` is `false`.
  - `public void BookWriter.writeLowValues(int length)` — writes `length` bytes `0x00`.
  - `public boolean BookWriter.isLowValuesFrom(int start)` — `true` if `start < position()` and all bytes from `start` to `position()` are `0x00`.
  - `public int BookReader.countUsedSlots(String field, int slotLength, int slots)` — does not move the position. Returns the index of the last slot that is not all `0x00`, plus 1 (0 if all slots are empty). Throws `MappingException` if the data ends before `slots * slotLength` bytes after the position (message: `<field>: the response is too short for the fixed area`), or if an empty slot comes before a slot that is not empty (message: `<field>: slot <i> is empty, but slot <j> after it has data`, indexes from 0).

- [ ] **Step 1: Write the failing tests** in `LowValuesTest`:
  - `writeLowValuesWritesZeroBytes`: `writeLowValues(3)` → `toByteArray()` equals `{0,0,0}`.
  - `isLowValuesFromIsTrueOnlyForZeroBytes`: after `writeText("id","A",1)` then `writeLowValues(2)`: `isLowValuesFrom(1)` is true, `isLowValuesFrom(0)` is false, `isLowValuesFrom(3)` is false (empty range).
  - `countUsedSlotsGivesLastUsedSlotPlusOne`: data `"AB" + 00 00 + 00 00` with slot length 2, 3 slots, but slot 1 empty and slot 0 used → 1. Data with all slots used → 3. All zero → 0.
  - `countUsedSlotsRejectsAGap`: slots `00 00`, `"CD"` → `MappingException` with message `lines: slot 0 is empty, but slot 1 after it has data`.
  - `countUsedSlotsRejectsShortData`: 3 bytes for 2 slots of 2 → `MappingException` containing `too short for the fixed area`.
  - `countUsedSlotsDoesNotMove`: `position()` is unchanged after the call; with a reader after `skip(2)`, the scan starts at 2.
  - `ebcdicSpacesAreNotEmpty`: `CodecContext(Charset.forName("Cp037"))`, slot `40 40` → 1.
- [ ] **Step 2: Run** `./gradlew :bookmap-core:test --tests '*LowValuesTest'` — expect compile failure (methods missing).
- [ ] **Step 3: Implement** the attribute and the three methods. `BookWriter` keeps its `ByteArrayOutputStream`; read its bytes for `isLowValuesFrom` (a copy is acceptable; the method is called once per item).
- [ ] **Step 4: Run** `./gradlew :bookmap-core:build` — expect PASS with 100% coverage.
- [ ] **Step 5: Commit** `feat: add the fixed attribute and LOW-VALUES helpers to the core`

### Task 2: Processor model — fixed collections and the count field

**Files:**
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/model/CollectionModel.java`
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/ModelBuilder.java` (`collection`, `countField`, `discriminator`)
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/ModelSnapshot.java`
- Modify: any other user of `CollectionModel.countField()` that assumes non-null (`CodecGenerator` compiles in this task; its behavior changes in Task 3).
- Test: `bookmap-processor/src/test/java/pt/dazz/bookmap/processor/ModelTest.java`, `ValidationTest.java`, `SubTypesValidationTest.java`

**Interfaces:**
- Produces:
  - `CollectionModel(String ownerSimpleName, String name, int order, Direction direction, FieldModel countField, int maxOccurs, boolean fixed, ItemModel item, String getter, String setter)` — `countField` is `null` only when `fixed` is true and `dependsOn` is empty.
  - `ModelSnapshot.Member` gets a new component `boolean fixed` after `maxOccurs`. `countField` is `""` when there is no count field.

- [ ] **Step 1: Write the failing tests**
  - `ModelTest.fixedCollectionWithoutDependsOnHasNoCountField`: a book with `orderId`, `lineCount` (int), `@BookCollection(order = 30, maxOccurs = 20, fixed = true) lines` → snapshot member `lines` has `fixed = true`, `countField = ""`; `lineCount` keeps its own direction (`INPUT_OUTPUT`), it is not a count field.
  - `ModelTest.fixedCollectionWithDependsOnUsesTheCountField`: same with `dependsOn = "lineCount"` → `countField = "lineCount"`, `lineCount` gets direction `OUTPUT`.
  - `ValidationTest.fixedCollectionWithMissingDependsOn`: `dependsOn = "nothing"` → error `dependsOn field 'nothing' does not exist`.
  - `ValidationTest.fixedCollectionCountDigits`: `dependsOn` on a `9(1)` field with `maxOccurs = 20` → existing digits error.
  - `SubTypesValidationTest.discriminatorAfterFixedCollectionIsPermitted`: base class with a fixed collection (order 20) then the discriminator (order 30) → compiles.
  - `SubTypesValidationTest.discriminatorAfterVariableCollectionIsAnError`: keep or add the case with a variable collection → `discriminator must not come after a collection`. Change the message to `discriminator must not come after a variable collection`, and update existing assertions.
- [ ] **Step 2: Run** `./gradlew :bookmap-processor:test` — expect failures.
- [ ] **Step 3: Implement.** In `collection(...)`: read `fixed` from `values` (default `false`). In `countField(...)`: if `fixed` and `dependsOn` is empty, return a marker that means "no count field" without an error (for example, change the method to return `Optional<FieldModel>` wrapped in a small result, or check `fixed` before the call). Do not apply the "member before" convention. In `discriminator(...)`: reject only `CollectionModel` members with `!fixed()`.
- [ ] **Step 4: Run** `./gradlew :bookmap-processor:build` — expect PASS (all old tests too).
- [ ] **Step 5: Commit** `feat: model fixed collections in the processor`

### Task 3: Processor codec generation — write and read the full area

**Files:**
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/CodecGenerator.java` (`encode`, `setCount`, `writeItems`, `readItems`)
- Test: `bookmap-processor/src/test/java/pt/dazz/bookmap/processor/FixedCollectionGenerationTest.java` (new; follow the style of `CollectionGenerationTest`, with `TestCompiler` and `CompiledCodec`)

**Interfaces:**
- Consumes: Task 1 helpers, Task 2 `CollectionModel.fixed()` and nullable `countField()`.

Generated behavior (spec 5.1–5.3):
- Encode, any fixed collection: list size check as today. If `countField != null`, set it (as today). After the items, `writer.writeLowValues((maxOccurs - count) * itemLength)`.
- Encode, LOW-VALUES mode: around each item, `int start = writer.position()`; after the item write, if `writer.isLowValuesFrom(start)` throw `MappingException` with `<descriptor>: item <i> is all LOW-VALUES, so a read cannot find it`.
- Decode, count mode: `count` from the count field with the existing `COLLECTION_SIZE` helper; read `count` items; `reader.skip((maxOccurs - count) * itemLength)`.
- Decode, LOW-VALUES mode: `int used = reader.countUsedSlots(descriptor, itemLength, maxOccurs)`; read `used` items; `reader.skip((maxOccurs - used) * itemLength)`.
- `append` behavior for `OUTPUT` collections in `decodeResponse` stays as today.

Test book (one source, reused): `GetOrder` — `orderId` X(10) INPUT; `lineCount` 9(2) OUTPUT; `lines` fixed, `maxOccurs = 3`, OUTPUT, item `OrderLine` (`productCode` X(4), `quantity` 9(3)); `orderTotal` S9(7)V99 COMP-3 OUTPUT. A second variant with `dependsOn = "lineCount"`.

- [ ] **Step 1: Write the failing tests** (LATIN context):
  - `lowValuesModeWritesTheFullArea`: request with 1 line → the output layout of `encodeResponse` is `lineCount` + `"P001" + "005"` + 14 bytes `0x00` + 5-byte total. Total length 2 + 21 + 5.
  - `lowValuesModeReadsUsedSlotsAndTheFieldAfter`: response bytes with 2 lines and 1 empty slot, then total `+123.45` → 2 lines, `orderTotal` 123.45.
  - `lowValuesModeNullListWritesZeroes` and reads back an empty list (Review Focus 4).
  - `lowValuesModeRejectsAnAllZeroItem`: an item that encodes to all `0x00` needs a `COMP` item field: add a variant item with `quantity` `9(4) COMP`, `productCode` absent → `MappingException` containing `is all LOW-VALUES`.
  - `lowValuesModeRejectsAGap`: response with slot 0 empty and slot 1 used → `MappingException` containing `slot 0 is empty`.
  - `lowValuesModeRejectsAShortResponse`: response that ends inside the area (no total) → `MappingException` containing `too short` (Review Focus 1).
  - `countModeReadsCountItemsAndSkipsTheRest`: `lineCount = 1`, slot 1 has data (`"P002010"`) → 1 line; total read at the fixed offset.
  - `countModeAcceptsAnAllZeroItem` (Review Focus 3), with the `COMP` variant.
  - `countModeRejectsACountAboveMax`: `lineCount = 4` → existing count error.
  - `fixedItemsWithSubtypes`: a fixed collection of the subtype item class from `ItemSubTypesTest` (reuse its sources) → a round-trip with one item of each subclass keeps the classes, and the empty slots are `0x00`.
- [ ] **Step 2: Run** `./gradlew :bookmap-processor:test --tests '*FixedCollectionGenerationTest'` — expect FAIL.
- [ ] **Step 3: Implement** the generation changes above.
- [ ] **Step 4: Run** `./gradlew :bookmap-processor:build` — expect PASS with 100% coverage.
- [ ] **Step 5: Commit** `feat: generate codecs for fixed collections`

### Task 4: Examples and simulator round-trip

**Files:**
- Create: `bookmap-examples/src/main/java/pt/dazz/bookmap/examples/orders/GetOrder.java` (LOW-VALUES mode, `Continuable` with `moreData`, a field after the table)
- Create: `bookmap-examples/src/main/java/pt/dazz/bookmap/examples/orders/OrderLine.java`
- Create: `bookmap-examples/src/main/java/pt/dazz/bookmap/examples/orders/ListShipments.java` (count mode with `dependsOn`) and `ShipmentSummary.java` item
- Test: `bookmap-examples/src/test/java/pt/dazz/bookmap/examples/FixedOccursEndToEndTest.java`

- [ ] **Step 1: Write the failing tests** with the simulator transport (follow `EndToEndTest`):
  - `getOrderRoundTrip`: a responder returns 2 lines → the client gives 2 lines and the total.
  - `getOrderPagination`: page 1 has 3 lines and `moreData = "Y"`, page 2 has 1 line and `"N"` → 4 lines (Review Focus 5).
  - `listShipmentsCountMode`: responder returns 2 summaries → 2 summaries; the count field is 2.
- [ ] **Step 2: Run** `./gradlew :bookmap-examples:test` — expect FAIL.
- [ ] **Step 3: Write** the example classes. Use `@BookField` convention style with `pic`. Class Javadoc shows the COBOL copybook.
- [ ] **Step 4: Run** `./gradlew build` — expect PASS in all modules.
- [ ] **Step 5: Commit** `feat: add fixed OCCURS examples`

### Task 5: Documentation

**Files:**
- Modify: `README.md` — `@BookCollection` table (`fixed`, `dependsOn` default for a fixed collection), "Conventions" (convention 4 does not apply to a fixed collection), "Collections and pagination" (a "Fixed collections" sub-section with the COBOL example of the spec, both modes, the write and read rules, the LOW-VALUES item error, the full-area rule for responses), subtype rules (discriminator after a fixed collection is permitted; the compile-error list says "variable collection"), "Known limitations".
- Modify: `docs/superpowers/specs/2026-11-10-bookmap-design.md` only if it lists collection rules that are now wrong (add a pointer to the fixed OCCURS spec).

- [ ] **Step 1: Edit** the README. Follow ASD-STE100.
- [ ] **Step 2: Run** `./gradlew build` — expect PASS.
- [ ] **Step 3: Commit** `docs: document fixed collections`
