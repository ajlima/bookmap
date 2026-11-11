# Subtypes (`REDEFINES` with a discriminator) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let one book area have more than one layout. A discriminator field selects a Java subclass, on an operation class and on a collection item class.

**Architecture:** A new annotation `@BookSubTypes` lists the subclasses of a base class. The processor builds one layout for each subclass and generates **one codec for each hierarchy**. The codec dispatches with generated `if` chains on the discriminator value and on `getClass()`. Two new `default` methods on `BookCodec` (`types()`, `readResponse(...)`) let the client return an instance of a different subclass. A codec from the 0.1.0 processor stays compatible.

**Tech Stack:** Java 21, Gradle, JavaPoet (`com.palantir.javapoet`), compile-testing, JUnit 5, AssertJ, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-11-11-bookmap-subtypes-design.md` (parent: `docs/superpowers/specs/2026-11-10-bookmap-design.md`).

## Global Constraints

- Branch `feat/redefines`. One commit for each task, at minimum. Commit email `alexandre.lima@dazz.pt`.
- Do not add a `Co-Authored-By` trailer or any Claude signature to a commit, a PR, or a file.
- Commit types: `feat:` for tasks 1 to 6, `docs:` for task 7. A `feat:` merge to `main` publishes 0.2.0. Do not merge without the author's approval.
- No runtime reflection in production code. The processor uses JavaPoet.
- The core and processor modules have no dependency on Spring, Quarkus, Guice, or a middleware library.
- 100% line and branch coverage (JaCoCo) in core, processor, simulator, and Spring Boot modules. `./gradlew build` must pass after each task.
- All code, messages, Javadoc, and docs in English. Docs follow ASD-STE100. Examples use an international business domain (bank accounts, statements). No Brazil- or Portugal-specific identifiers.
- Each processor error message starts with the member descriptor `"<Owner>.<name> (order <n>): "` or the class prefix `"<SimpleClassName>: "`, as today.

## Review Focus

1. A text discriminator with trailing spaces in the bytes (`"C  "` in `X(3)`) must select the type with value `"C"`. Test: Task 3, `textDiscriminatorIgnoresTrailingSpaces`.
2. A blank numeric discriminator (all spaces or low-values) must give the "unknown discriminator value" `MappingException` with the text `(blank)`, not a `NullPointerException`. Test: Task 3, `blankNumericDiscriminatorIsUnknown`.
3. A `Continuable` base request that changes to a subclass on page 1 must accumulate the subclass collection over page 2. A page 2 with another value must fail. Test: Task 1, `classChangeOnlyOnFirstPage`, and Task 6, `pagesContinueOnTheNewSubclass`.
4. A list with an item of a class that is not in the list (for example, an anonymous subclass) must give `MappingException`, not a wrong layout. Test: Task 4, `unlistedItemSubclassFails`.
5. A subclass in another package with Lombok `@Data` must compile and round-trip. Test: Task 2, `subclassWithLombokData` (both processor orders).

---

### Task 1: Core API: annotation, SPI defaults, registry, reader and writer helpers, client

**Files:**
- Create: `bookmap-core/src/main/java/pt/dazz/bookmap/annotation/BookSubTypes.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/spi/BookCodec.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/codec/BookCodecRegistry.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/codec/BookReader.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/codec/BookWriter.java`
- Modify: `bookmap-core/src/main/java/pt/dazz/bookmap/BookmapClient.java`
- Test: `bookmap-core/src/test/java/pt/dazz/bookmap/BookmapClientTest.java`, `.../codec/BookCodecRegistryTest.java`, `.../codec/TextAndZonedTest.java` (or a new `ReaderWriterPositionTest.java`), `.../FakeCodec.java`

**Interfaces:**
- Produces:
  - `@Retention(CLASS) @Target(TYPE) @Documented public @interface BookSubTypes { String discriminator(); Type[] value(); @Retention(CLASS) @Target({}) @interface Type { String value(); Class<?> type(); } }`. Use the same retention as `@BookField`.
  - `BookCodec<T>`: `default List<Class<? extends T>> types() { return List.of(type()); }` and `default T readResponse(byte[] data, T target, CodecContext ctx) { decodeResponse(data, target, ctx); return target; }`.
  - `BookReader`: `public BookReader peek(int offset)` gives a new reader on the same data at `position() + offset`. `public void skip(int length)` moves the position. A short buffer fails with the usual short-data `MappingException` when the reader reads, not on `peek`.
  - `BookWriter`: `public int position()` gives the number of bytes written. `public void writeFiller(int length)` writes `length` spaces of the context charset.
  - `BookCodecRegistry` indexes each class of `codec.types()`.

- [ ] **Step 1: Write the failing tests**
  - `BookCodecRegistryTest.indexesAllTypesOfACodec`: a fake codec with `types() = List.of(Base.class, Sub.class)` → `codecFor(Sub.class)` is the same codec as `codecFor(Base.class)`.
  - `BookCodecRegistryTest.twoCodecsForOneSubtypeFail`: two codecs whose `types()` both contain `Sub.class` → `IllegalStateException` "More than one codec for ...Sub".
  - `BookmapClientTest.returnsTheObjectFromReadResponse`: a fake codec whose `readResponse` returns a new `Sub` → `execute(base)` returns that `Sub`, and `validate()` and `hasContinuation()` run on the `Sub`.
  - `BookmapClientTest.classChangeOnlyOnFirstPage`: a `Continuable` fake that continues once. The client calls `readResponse` on call 1 and `decodeResponse` on call 2 (record the calls in the fake). Call 2 encodes the object that call 1 returned.
  - `BookmapClientTest.defaultReadResponseFillsTheTarget`: a fake codec that does not override `readResponse` → `execute(x)` returns `x` (same instance).
  - Reader and writer: `peek(3)` then `readText(…, 2)` reads bytes 3–4 and does not move the original reader; `skip(2)` then `position() == 2`; `writeFiller(3)` writes three `0x40` bytes in IBM037 and `position()` is 3.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-core:test`
Expected: FAIL (compile errors: `types`, `readResponse`, `peek`, `skip`, `position`, `writeFiller` missing).

- [ ] **Step 3: Implement the API above**
  - Client loop: `T current = request;` On call 1, `current = codec.readResponse(response, current, ctx)`. On a later call, `codec.decodeResponse(response, current, ctx)`. Validation, continuation, and the next encode use `current`. Return `current`.
  - Change the Javadoc of `execute` to "returns the response object. Without subtypes, this is the request object." Document the two new `BookCodec` methods as in spec 4.9. Document `BookSubTypes` with the spec 3 example.

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :bookmap-core:build`
Expected: BUILD SUCCESSFUL, JaCoCo verification passes.

- [ ] **Step 5: Commit**

```bash
git add bookmap-core
git commit -m "feat: add the subtypes API to the core"
```

---

### Task 2: Processor model and validation for operation subtypes

**Files:**
- Create: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/model/VariantModel.java`
- Create: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/SubTypesResolver.java` (reads `@BookSubTypes`, checks the rules, and gives the variants)
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/model/OperationModel.java`
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/ModelBuilder.java`
- Test: create `bookmap-processor/src/test/java/pt/dazz/bookmap/processor/SubTypesValidationTest.java`; modify `LombokRulesTest.java`

**Interfaces:**
- Consumes: `BookSubTypes` (Task 1).
- Produces:
  - `record VariantModel(TypeElement type, String value, List<MemberModel> members)`. `value` is the normalized value: the text without trailing spaces, or the `long` value as decimal text (`"001"` → `"1"`). `members` is the full layout of the subclass (base members first, all sorted by `order`).
  - `OperationModel` gets two components at the end: `FieldModel discriminator` (`null` without subtypes) and `List<VariantModel> variants` (empty without subtypes). `members()` stays the layout of the base class. `OperationModel` also gets `boolean baseAbstract()` (or derive it from `type().getModifiers()` in the generator; builder's choice).
  - `static String SubTypesResolver.normalize(FieldModel discriminator, String value)`: used by the generator for the literals.

Build the base members once. Build only the members that each subclass declares (the subclass and the classes between it and the base) for each variant. Do not resolve the base fields again, so that a base error is reported only once. The count field of a subclass collection is found only in the members of the subclass layout.

- [ ] **Step 1: Write the failing tests** in `SubTypesValidationTest`. Each test compiles a small base `GetAccount` plus subclasses and checks the error text with the existing `assertError(compilation, descriptor, phrase)` pattern.

| Test | Source change | Expected error contains |
|---|---|---|
| `validHierarchyCompiles` | base with `accountType` `9(2)` INPUT_OUTPUT, `CheckingAccount` `"01"`, `SavingsAccount` `"02"` | compilation succeeds; one `GetAccount_BookCodec`; no `CheckingAccount_BookCodec` |
| `abstractBaseCompiles` | `abstract` base, discriminator INPUT_OUTPUT | succeeds |
| `discriminatorDoesNotExist` | `discriminator = "kind"` | `GetAccount: discriminator field 'kind' does not exist` |
| `discriminatorInSubclass` | field declared in `CheckingAccount` | `GetAccount: discriminator field 'accountType' must be in the base class or its superclasses` |
| `discriminatorWrongType` | `BigDecimal accountType` | `discriminator must be String, int, Integer, long, or Long` |
| `discriminatorWithConverter` | `converter = …` | `discriminator must not have a converter` |
| `discriminatorInputOnly` | `direction = INPUT` | `discriminator must have direction OUTPUT or INPUT_OUTPUT` |
| `abstractBaseWithOutputDiscriminator` | abstract base, discriminator OUTPUT | `an abstract base class needs an INPUT_OUTPUT discriminator` |
| `collectionBeforeDiscriminator` | base collection with order lower than the discriminator | `discriminator must not come after a collection` |
| `emptyTypeList` | `value = {}` | `GetAccount: @BookSubTypes must list at least one type` |
| `typeNotASubclass` | `type = String.class` | `GetAccount: java.lang.String is not a subclass of GetAccount` |
| `abstractType` | abstract `CheckingAccount` | `type CheckingAccount must be public, not abstract, not generic, and have a public no-argument constructor` |
| `sameTypeTwice` | `CheckingAccount` listed with `"01"` and `"03"` | `type CheckingAccount is listed more than once` |
| `blankValue` | `value = " "` | `discriminator value must not be blank` |
| `duplicateValueAfterNormalization` | `"01"` and `"1"` | `discriminator value '1' is used by CheckingAccount and SavingsAccount` |
| `valueNotANumber` | `"A1"` on `9(2)` | `discriminator value 'A1' is not a number` |
| `valueDoesNotFit` | `"123"` on `9(2)`; `"-1"` on unsigned; `"ABCD"` on `X(3)` | `discriminator value '123' does not fit the field` (one assertion for each case) |
| `nestedSubTypes` | `CheckingAccount` has its own `@BookSubTypes` | `CheckingAccount: subtypes in a subtype are not supported` |
| `typeWithBookOperation` | `@BookOperation` on `CheckingAccount` | `CheckingAccount: a subtype must not have @BookOperation` |
| `typeIsSubclassOfAnotherType` | `PremiumChecking extends CheckingAccount`, both listed | `PremiumChecking: a subtype must not extend another subtype` |
| `subclassOrderNotAfterBase` | subclass field `order = 15`, base max order 20 | `CheckingAccount.overdraftLimit (order 15): a subtype member must come after all base members` |
| `duplicateOrderInSubclassPath` | two fields in `CheckingAccount` with order 30 | `duplicate order` |
| `siblingsShareOrders` | both subclasses use orders 30 and 40 | succeeds |
| `subclassCountInBase` | subclass collection with `dependsOn` a base field | `dependsOn field 'count' must be in the subtype layout` |
| `subclassWithoutMembers` | `ClosedAccount` with no fields | succeeds |
| `subclassMissingSetter` | subclass field without setter, last round | `missing setter` |

  - `LombokRulesTest.subclassWithLombokData` (both `ProcessorOrder` values): subclass in package `test.accounts` with `@Data` and a public no-argument constructor → compilation succeeds.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-processor:test --tests '*SubTypesValidationTest*' --tests '*LombokRulesTest*'`
Expected: FAIL (`@BookSubTypes` is ignored; for example, `abstractBaseCompiles` fails with "operation class must not be abstract").

- [ ] **Step 3: Implement the model and the checks**
  - In `ModelBuilder.checkOperationClass`, permit `abstract` when the class has `@BookSubTypes`.
  - Treat an unresolved `type` class value (`Mirrors.UNRESOLVED`) as today: set `problems.unresolved`.
  - Until Task 3 exists, `CodecGenerator` uses only `members()`. The validation tests do not run the codec.

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :bookmap-processor:build`
Expected: BUILD SUCCESSFUL. If coverage fails only for the variant data that the generator does not read yet, merge Task 2 and Task 3 into one commit pair (tests of Task 3 in the same build).

- [ ] **Step 5: Commit**

```bash
git add bookmap-processor
git commit -m "feat: validate subtypes of operation classes"
```

---

### Task 3: Code generation for operation subtypes

**Files:**
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/CodecGenerator.java`
- Test: create `bookmap-processor/src/test/java/pt/dazz/bookmap/processor/SubTypesGenerationTest.java`

**Interfaces:**
- Consumes: `OperationModel.discriminator()`, `variants()`, `VariantModel` (Task 2); `BookReader.peek`, `BookCodec.types/readResponse` (Task 1).
- Produces: generated `<Base>_BookCodec` with the behavior below. Without subtypes, the generated code must stay the same as today (run the existing `GenerationTest` and `CollectionGenerationTest` unchanged).

Generated behavior (spec 4.6 to 4.10):
- `types()`: the base class if it is not abstract, then the listed types in list order. Without subtypes, do not generate `types()`.
- `newInstance()`: `new Base()`, or `throw new MappingException("<Base>: the base class is abstract")`.
- `maxResponseLength()`: the maximum of the `OUTPUT` sums of each layout (the base, if it is not abstract, and each variant).
- `encodeRequest` / `encodeResponse(value)`: choose the layout by `value.getClass() == X.class`. For a listed type, call the discriminator setter with the typed literal, then write that layout. For the concrete base, write the base layout without a change to the discriminator. Else throw `MappingException("<Base>: unknown subclass " + value.getClass().getName())`. If the discriminator is not in the direction, do not call its setter.
- `readResponse(data, target)`: read the discriminator with `reader.peek(offset)`, where `offset` is the sum of the `byteLength` of the base fields before the discriminator in the `OUTPUT` direction. Select the class.
  - If the selected class is `target.getClass()`, decode into `target`.
  - If `target.getClass()` is the base class and the selected class is a listed type, create it, copy each base member (fields and collections, all directions) with `copy.setX(target.getX())`, decode into the copy, and return the copy.
  - Else throw `MappingException("<Base>: the response has type <Selected>, which is not a <TargetSimpleName>")`.
- `decodeResponse(data, target)`: as `readResponse`, but a class different from `target.getClass()` always throws the same message.
- `decodeRequest(data)`: if the discriminator is `INPUT_OUTPUT`, peek at the `INPUT` offset and select the class. For an unknown value, use the concrete base, else throw. With an `OUTPUT` discriminator, use the base.
- `resetOutputCollections(value)`: choose the layout by exact class and reset its `OUTPUT` collections.
- Unknown value message: `"<descriptor of discriminator>: unknown discriminator value '<v>'. Known values: <v1>, <v2>"`. `<v>` is the decoded text, or the decoded number as plain digits, or `(blank)` for a blank number.
- Numeric match: compare the decoded `BigDecimal` with `BigDecimal.valueOf(<long>)` through `compareTo`. Text match: `equals` on the decoded text (it has no trailing spaces).
- Generate one private method per layout and direction (for example `encodeRequest_CheckingAccount(CheckingAccount value, CodecContext ctx)`). Reuse the current `encode` and `decode` builders with a member list and a target type as parameters.

- [ ] **Step 1: Write the failing tests** in `SubTypesGenerationTest`. Use `CompiledCodec.load`, `get`, `set`, and the `LATIN` context. Use base `GetAccount` (`accountId X(12)` INPUT, `accountType 9(2)` INPUT_OUTPUT), `CheckingAccount` `"01"` (`overdraftLimit S9(9)V99 COMP-3` OUTPUT, `cardNumberMask X(19)` OUTPUT), `SavingsAccount` `"02"` (`interestRate 9V9(4)` OUTPUT, `maturityDate` OUTPUT).
  - `typesListsBaseAndSubtypes`: `types()` equals `[GetAccount, CheckingAccount, SavingsAccount]`; with an abstract base, `[CheckingAccount, SavingsAccount]`.
  - `encodeSubtypeSetsTheDiscriminator`: encode a `SavingsAccount` with `accountType = 0` → request bytes `"ACC-0000001 " + "02"`, and `get(obj, "accountType") == 2`.
  - `encodeConcreteBaseKeepsTheDiscriminator`: base with `accountType = 7` → bytes end with `"07"`.
  - `readResponseCreatesTheSubtype`: target is a base with `accountId = "ACC-0000001"`; response `"02"` + savings bytes → the result is a `SavingsAccount`, `accountId` was copied, `interestRate == 0.0325`, and the result is not the target.
  - `readResponseFillsTheSameSubtype`: target is a `CheckingAccount`; response `"01"` → the same instance is returned.
  - `readResponseRejectsASibling`: target is a `CheckingAccount`; response `"02"` → `MappingException` containing `the response has type SavingsAccount, which is not a CheckingAccount`.
  - `decodeResponseRejectsAClassChange`: target is a base; response `"01"` → `MappingException`.
  - `unknownDiscriminatorValue`: response `"07"` → `MappingException` containing `GetAccount.accountType (order 20): unknown discriminator value '7'. Known values: 1, 2`.
  - `blankNumericDiscriminatorIsUnknown`: response with two spaces in `accountType` → message contains `'(blank)'`.
  - `textDiscriminatorIgnoresTrailingSpaces`: a variant hierarchy with `String kind X(3)` and value `"C"`; response bytes `"C  "` → the `C` subtype.
  - `numericValueNormalization`: values `"001"` on `9(3)` match the bytes `"001"`.
  - `shortResponseUsesTheSelectedLayout`: response `"01"` cut after the discriminator → the short-data `MappingException`.
  - `maxResponseLengthIsTheLongestVariant`: equals `2 + max(6 + 19, 5 + 8)` = 27.
  - `abstractBaseNewInstanceFails`: `newInstance()` → `MappingException` "the base class is abstract".
  - `encodeUnknownSubclassFails`: an instance of an unlisted subclass (compile one in the test source) → `MappingException` "unknown subclass".
  - `decodeRequestSelectsTheSubtype`: request bytes with `"01"` → `CheckingAccount`; with `"07"` and a concrete base → `GetAccount`; with `"07"` and an abstract base → `MappingException`; with an `OUTPUT` discriminator → `GetAccount`.
  - `encodeResponseWritesTheRuntimeLayout`: a `SavingsAccount` → response bytes contain the savings fields.
  - `resetClearsSubclassCollections`: a subclass with an `OUTPUT` collection → `resetOutputCollections` sets an empty list.
  - `subclassCollectionRoundTrip`: a subclass with a count and a collection after the base → encode, decode, and the items are equal.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-processor:test --tests '*SubTypesGenerationTest*'`
Expected: FAIL (`types()` returns only the base; the response is not dispatched).

- [ ] **Step 3: Implement the generation above in `CodecGenerator`**

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :bookmap-processor:build`
Expected: BUILD SUCCESSFUL; existing generation tests unchanged and green; 100% coverage.

- [ ] **Step 5: Commit**

```bash
git add bookmap-processor
git commit -m "feat: generate codecs for operation subtypes"
```

---

### Task 4: Item subtypes (validation and generation)

**Files:**
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/model/ItemModel.java`
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/ModelBuilder.java` (`item(...)`), `SubTypesResolver.java`
- Modify: `bookmap-processor/src/main/java/pt/dazz/bookmap/processor/CodecGenerator.java`
- Test: create `bookmap-processor/src/test/java/pt/dazz/bookmap/processor/ItemSubTypesTest.java`

**Interfaces:**
- Consumes: `SubTypesResolver`, `VariantModel` (Task 2); `BookWriter.position/writeFiller`, `BookReader.peek/skip` (Task 1).
- Produces: `ItemModel(TypeElement type, List<FieldModel> fields, int byteLength, FieldModel discriminator, List<VariantModel> variants)`. `byteLength` is the length of the **longest** layout (the base, if it is not abstract, and each variant). `fields` stays the base layout.

Rules (spec 4.1 to 4.5, 4.12): the same checks as Task 2, except the direction rules (item fields have no direction). An abstract item base class is permitted when it has `@BookSubTypes`. The rule "item class must not contain a `@BookCollection`" applies to each subclass too.

Generated behavior:
- Write item: record `int start = writer.position()`. Choose the layout by exact class, set the discriminator, and write the fields. Then call `writer.writeFiller(byteLength - (writer.position() - start))` if the result is more than 0. An unlisted class throws `MappingException("<collection descriptor>: unknown subclass <name>")`.
- Read item: peek at the item offset of the discriminator, select the class, read its fields, then `reader.skip(byteLength - <layout length>)` if it is more than 0. An unknown value gives the message of Task 3, with the item discriminator descriptor.
- `maxResponseLength()` uses `maxOccurs * byteLength` with the longest `byteLength`.

- [ ] **Step 1: Write the failing tests** in `ItemSubTypesTest`. Operation `GetStatement` (`accountId X(12)` INPUT, `entryCount 9(2)` OUTPUT, `entries` `maxOccurs 5` OUTPUT). Abstract item base `StatementEntry` (`entryType X`, `amount S9(13)V99 COMP-3`), `CardPayment` `"C"` (`merchant X(20)`), `Transfer` `"T"` (`counterpartyIban X(34)`).
  - `mixedListRoundTrip`: encode a response with `[CardPayment, Transfer]` → each item occupies `1 + 8 + 34 = 43` bytes; the card item ends with 14 spaces; decode gives `[CardPayment, Transfer]` with equal values.
  - `discriminatorIsSetOnWrite`: a `CardPayment` with `entryType = null` → bytes start with `"C"`, and `get(item, "entryType")` is `"C"`.
  - `unknownItemDiscriminator`: an item with `"X"` → `MappingException` containing `StatementEntry.entryType (order 10): unknown discriminator value 'X'. Known values: C, T`.
  - `unlistedItemSubclassFails`: an anonymous subclass `new CardPayment() { }` in the list → `MappingException` containing `unknown subclass`.
  - `fieldsAfterAPolymorphicCollection`: a trailer field after `entries` → read at the correct offset (`2 + n * 43`).
  - `maxResponseLengthUsesLongestItem`: equals `2 + 5 * 43`.
  - Validation: `itemDiscriminatorDoesNotExist`, `itemSubtypeWithCollection` (`must not contain a @BookCollection`), `itemDuplicateValue`, `abstractItemWithoutSubTypes` (still the old "must be a public class with a public no-argument constructor" error).

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-processor:test --tests '*ItemSubTypesTest*'`
Expected: FAIL (an abstract item class is rejected).

- [ ] **Step 3: Implement the model, the checks, and the generation above**

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :bookmap-processor:build`
Expected: BUILD SUCCESSFUL, 100% coverage.

- [ ] **Step 5: Commit**

```bash
git add bookmap-processor
git commit -m "feat: support subtypes of collection items"
```

---

### Task 5: Simulator support

**Files:**
- Create: `bookmap-transport-simulator/src/main/java/pt/dazz/bookmap/simulator/SimulatedResponder.java`
- Modify: `bookmap-transport-simulator/src/main/java/pt/dazz/bookmap/simulator/SimulatorTransport.java`
- Test: `bookmap-transport-simulator/src/test/java/...` (the existing simulator test class, plus hand-written fake codecs in the test sources)

**Interfaces:**
- Consumes: `BookCodec.type()`, `types()` (Task 1).
- Produces:
  - `@FunctionalInterface public interface SimulatedResponder<T> { T respond(T call); }`
  - `public <T> SimulatorTransport registerResponder(Class<T> type, SimulatedResponder<T> responder)`: `decodeRequest` → `respond` → `encodeResponse(result)`. A `null` result throws `MappingException("<operation>: the responder returned null")`.
  - `register(Class<T>, SimulatedHandler<T>)` and `registerResponder` throw `IllegalArgumentException("<type> is a subtype of <base>. Register <base>.")` when `codec.type() != type`.

- [ ] **Step 1: Write the failing tests**
  - `registerResponderAnswersWithAnotherObject`: a fake codec; the responder returns a new object → the encoded bytes come from that object.
  - `responderReturnsNull`: → `MappingException` "the responder returned null" (it reaches the caller unchanged, as a `BookmapException`).
  - `registerWithASubtypeFails`: a fake codec with `type() = Base.class` and `types() = [Base, Sub]`, registry built with `BookCodecRegistry.of(codec)` → `register(Sub.class, …)` and `registerResponder(Sub.class, …)` throw `IllegalArgumentException` containing `Register ` and the base name.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-transport-simulator:test`
Expected: FAIL (`registerResponder` does not exist).

- [ ] **Step 3: Implement the API above**

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :bookmap-transport-simulator:build`
Expected: BUILD SUCCESSFUL, 100% coverage.

- [ ] **Step 5: Commit**

```bash
git add bookmap-transport-simulator
git commit -m "feat: let the simulator answer with a subtype"
```

---

### Task 6: Example and end-to-end test

**Files:**
- Create in `bookmap-examples/src/main/java/pt/dazz/bookmap/examples/accounts/`: `GetAccount.java` (concrete base, `Continuable`, `@BookSubTypes` with `CheckingAccount` `"01"`, `SavingsAccount` `"02"`), `CheckingAccount.java`, `SavingsAccount.java`, `StatementEntry.java` (abstract item base), `CardPayment.java` (`"C"`), `Transfer.java` (`"T"`)
- Test: create `bookmap-examples/src/test/java/pt/dazz/bookmap/examples/SubTypesEndToEndTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1 to 5.
- Produces: the README example in Task 7 copies these classes.

Layout: `GetAccount` has `accountId X(12)` INPUT, `restartKey X(10)` INPUT_OUTPUT, `moreData X` OUTPUT, `accountType 9(2)` INPUT_OUTPUT. `CheckingAccount` adds `overdraftLimit S9(9)V99 COMP-3`, `entryCount 9(2)`, and `entries` (`List<StatementEntry>`, `maxOccurs 10`). `SavingsAccount` adds `interestRate 9V9(4)` and `maturityDate`. `hasContinuation()` returns `"Y".equals(moreData)`.

- [ ] **Step 1: Write the failing tests** with `SimulatorTransport` and `BookmapClient`:
  - `baseRequestReturnsTheSubtype`: `registerResponder(GetAccount.class, …)` returns a `SavingsAccount` → `client.execute(new GetAccount())` returns a `SavingsAccount` with the request `accountId` and the simulated `interestRate`.
  - `pagesContinueOnTheNewSubclass`: the responder returns a `CheckingAccount` with 10 entries and `moreData = "Y"`, then 3 entries and `"N"` → the result is a `CheckingAccount` with 13 entries (a mix of `CardPayment` and `Transfer`). The second received request has `accountType` `"01"`.
  - `pageWithAnotherTypeFails`: page 1 gives `"01"`, page 2 gives `"02"` → `MappingException`.
  - `unknownAccountType`: a byte-level `register("GetAccount", bytes -> …)` answers `"07"` → `MappingException` containing `unknown discriminator value '7'`.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :bookmap-examples:test`
Expected: FAIL (the example classes do not exist).

- [ ] **Step 3: Write the example classes** with getters and setters (no Lombok, as in `GetCustomerOrders`).

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL in all modules.

- [ ] **Step 5: Commit**

```bash
git add bookmap-examples
git commit -m "feat: add a subtypes example with bank accounts"
```

---

### Task 7: Documentation

**Files:**
- Modify: `README.md` (new section "Subtypes (`REDEFINES`)" after "Collections and pagination"; the annotation table for `@BookSubTypes`; quick start comment `// the same object, filled` → `// the response object`; the limitation list)
- Modify: `docs/superpowers/specs/2026-11-10-bookmap-design.md` (2.2: "`REDEFINES` without a discriminator", with a link to the subtypes spec; 5.8: the returned object and the class change; 5.5: the "abstract" exception for a base class)
- Modify: `docs/superpowers/plans/2026-11-11-bookmap-followups.md` (deferred: subtypes in a subtype, base trailer after the redefined area, a base count field for a subclass collection, `REDEFINES` without a discriminator)

- [ ] **Step 1: Write the README section.** Include the COBOL copybook of spec 1, the `GetAccount` classes from Task 6, the item example, the rules of spec 4.4 to 4.8 in short form, and the list of compile errors. Use ASD-STE100: short sentences, imperative, one instruction in each sentence.
- [ ] **Step 2: Change the limitation** "No `REDEFINES`." to "No `REDEFINES` without a discriminator. See [Subtypes](#subtypes-redefines)."
- [ ] **Step 3: Update the parent spec and the follow-ups file** as listed above.
- [ ] **Step 4: Check the links and the build**

Run: `grep -n "REDEFINES" README.md docs/superpowers/specs/*.md` and `./gradlew build`
Expected: no remaining "No `REDEFINES`." line; BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add README.md docs
git commit -m "docs: document subtypes for REDEFINES"
```

---

### Task 8: Pull request

- [ ] **Step 1:** `git push -u origin feat/redefines`
- [ ] **Step 2:** `gh pr create --base main --title "feat: support REDEFINES with subtypes and a discriminator"`. Put a summary of the spec and the test evidence in the body. Do not add a Claude signature.
- [ ] **Step 3:** Wait for the `build` check to pass. Fix failures on the branch.
- [ ] **Step 4:** Do not merge. Ask the author for approval. The merge publishes 0.2.0 to Maven Central, and you cannot undo it. Use a merge commit or a rebase merge, never a squash.
