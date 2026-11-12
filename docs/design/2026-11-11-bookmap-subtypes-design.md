# bookmap — Subtypes for `REDEFINES` (design)

Date: 2026-11-11
Status: implemented in v0.2.0.
Base package: `pt.dazz.bookmap`
Parent spec: `2026-11-10-bookmap-design.md`

Decisions marked **[builder call]** come from the spec author, not from the project owner. You can change them if you have a good reason. All other decisions come from the project owner.

## 1. Intent

A COBOL book often uses `REDEFINES` to give one area more than one layout. A field in the book (a record type) tells the program which layout is valid. For example:

```cobol
01  GET-ACCOUNT.
    05  ACCOUNT-ID        PIC X(12).
    05  ACCOUNT-TYPE      PIC 9(2).
    05  CHECKING-DATA.
        10  OVERDRAFT-LIMIT   PIC S9(9)V99 COMP-3.
        10  CARD-NUMBER-MASK  PIC X(19).
    05  SAVINGS-DATA REDEFINES CHECKING-DATA.
        10  INTEREST-RATE     PIC 9V9(4).
        10  MATURITY-DATE     PIC 9(8).
```

bookmap v1 does not support `REDEFINES`. The old framework (`cicsannotations`) did not support it either. There, each layout was a separate Java subclass, and the caller selected the subclass before the call. That solution fails when the remote program selects the layout in the response.

This design adds **subtypes**, similar to `@JsonTypeInfo` and `@JsonSubTypes` in Jackson:

- A base class contains the common fields and the **discriminator** field.
- Each layout of the redefined area is a subclass of the base class.
- The codec reads the discriminator from the bytes and creates an instance of the correct subclass.

The processor generates all dispatch code at compile time. The framework uses no reflection.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals

- Subtypes on an **operation class**. The response can select a subclass that is different from the class of the request.
- Subtypes on a **collection item class**. Each item of one list can have a different subclass.
- The codec writes the discriminator value of a subclass automatically.
- A clear `MappingException` for a discriminator value that has no subclass. The message contains the value.
- Compile-time validation of the full hierarchy.
- Support in the processor, `bookmap-core`, the simulator transport, and `bookmap-examples`. 100% line and branch coverage in all modules.

### 2.2 Non-goals

- A `REDEFINES` without a discriminator, where all layouts of the same bytes are available at the same time.
- Subtypes in a subtype (a subclass that declares its own `@BookSubTypes`).
- A change of subclass between two pages of one execution.
- Base fields after the redefined area (a trailer after the `REDEFINES`). See 4.4.
- A discriminator with a custom converter, a scale, or a date and time type.

### 2.3 Anti-goals

- Runtime reflection to find or create subclasses.
- A silent fallback for an unknown discriminator value (for example, an instance of the base class).
- A change that breaks a codec that the 0.1.0 processor generated, or a book without subtypes.

## 3. Annotations

```java
@BookOperation
@BookSubTypes(discriminator = "accountType", value = {
    @BookSubTypes.Type(value = "01", type = CheckingAccount.class),
    @BookSubTypes.Type(value = "02", type = SavingsAccount.class)
})
public class GetAccount {

    @BookField(order = 10, pic = "X(12)", direction = INPUT)
    private String accountId;

    @BookField(order = 20, pic = "9(2)")
    private int accountType;                 // the discriminator, INPUT_OUTPUT
}

public class CheckingAccount extends GetAccount {

    @BookField(order = 30, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
    private BigDecimal overdraftLimit;

    @BookField(order = 40, pic = "X(19)", direction = OUTPUT)
    private String cardNumberMask;
}

public class SavingsAccount extends GetAccount {

    @BookField(order = 30, pic = "9V9(4)", direction = OUTPUT)
    private BigDecimal interestRate;

    @BookField(order = 40, direction = OUTPUT)
    private LocalDate maturityDate;
}
```

`@BookSubTypes` (class, in `pt.dazz.bookmap.annotation`):

| Attribute | Meaning | Default |
|---|---|---|
| `discriminator` | The name of the discriminator field. The base class or one of its superclasses declares the field. | Mandatory. |
| `value` | The list of `@BookSubTypes.Type`. | Mandatory, at least one. |

`@BookSubTypes.Type` (nested annotation):

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The discriminator value of the subclass, as text. | Mandatory. |
| `type` | The subclass. | Mandatory. |

The owner selected the Jackson style: the base class contains the full list. The owner selected the names `@BookSubTypes` and `@BookSubTypes.Type`, with the attributes `value` (the discriminator value) and `type` (the class).

The same annotation goes on the base class of a collection item:

```java
@BookSubTypes(discriminator = "entryType", value = {
    @BookSubTypes.Type(value = "C", type = CardPayment.class),
    @BookSubTypes.Type(value = "T", type = Transfer.class)
})
public abstract class StatementEntry {
    @BookField(order = 10, pic = "X") private String entryType;
    @BookField(order = 20, pic = "S9(13)V99 COMP-3") private BigDecimal amount;
}

// in the operation class
@BookCollection(order = 70, maxOccurs = 50)
private List<StatementEntry> entries;
```

## 4. Rules

### 4.1 The base class and the subclasses

- The base class has `@BookSubTypes`. For an operation, the base class also has `@BookOperation`. The subclasses do not have `@BookOperation`. They use the operation name of the base class.
- A `type` must be a subclass of the base class (direct or indirect). It must be public, not abstract, not generic, and it must have a public no-argument constructor. The Lombok rules of the parent spec (5.5) apply.
- A `type` can be the base class itself, if the base class is not abstract. Then the base class has its own discriminator value **[builder call]**.
- The base class can be `abstract`. Then the caller always sends a subclass. The rule "operation class must not be abstract" does not apply to a base class with `@BookSubTypes`.
- A class between the base class and a subclass can declare annotated fields. These fields belong to the subclass layout.
- A `type` must not be a subclass of another `type` in the list. The base class is the only exception. Thus, a class change (4.8) always starts at the base class **[builder call]**.
- An instance of a class that is not in the list causes `MappingException` ("No codec" for an operation, "unknown subclass" for an item). The codec compares the exact class.

### 4.2 The discriminator field

- The discriminator is a `@BookField` of the base class or of one of its superclasses. It must not be in a subclass.
- Its Java type is `String`, `int`, `Integer`, `long`, or `Long`. It has no `converter` and no `scale`. Any `usage` is permitted.
- **Operation level:** its direction is `OUTPUT` or `INPUT_OUTPUT`, because the response must contain it **[builder call]**. Item fields have no direction of their own. Thus, this rule does not apply to items.
- An abstract operation base class with an `OUTPUT` discriminator is a compile error. In that case, `decodeRequest` (the simulator) cannot select a subclass **[builder call]**.
- No collection is before the discriminator. Thus, the discriminator has a fixed offset in each direction **[builder call]**.

### 4.3 Discriminator values

- **Numeric discriminator:** the processor parses `value` as a `long`. `"001"` and `"1"` are the same value. The value must fit in the digits and sign of the field. A value that is not a number is a compile error.
- **Text discriminator:** the processor removes the trailing spaces of `value`. The value must fit in the field length. The codec compares it with the decoded text, which also has no trailing spaces.
- A blank value is a compile error.
- Two types with the same value (after normalization) is a compile error.

### 4.4 Order and layout

- The `order` must be unique along each path from the base class to a subclass. Two subclasses can use the same `order` (they describe the same bytes).
- Each member of a subclass must have an `order` greater than all members of the base class and its superclasses. Thus, the redefined area is the end of the layout. A base field after the area (a trailer) is a compile error in this version **[builder call]**. To model a trailer, repeat the field in each subclass, and add a filler field to align the offsets.
- Fields after a collection, `dependsOn`, `@BookDefaults`, and the Lombok rules apply in each subclass as in the parent spec. A field of a subclass uses the defaults of its own declaring class and package.
- A subclass in another package needs public accessors, as a superclass does today.
- The count field (`dependsOn`) of a collection in a subclass must be in the subclass layout: in the subclass, or in a class between the base class and the subclass. A count field in the base class is a compile error **[builder call]**. Thus, the base layout and the discriminator offset are the same for all subclasses, and the convention "the member immediately before the collection" never selects the discriminator. To model a count in the common header, declare it in each subclass.
- A collection in the base class follows the parent spec, with its count field in the base class.

### 4.5 Length

- **Items.** An item has a fixed length, because nested collections are not permitted. In each direction, each item occupies the length of the **longest** subclass. On write, the codec fills the remaining bytes of a shorter subclass with spaces. On read, the codec skips them. `maxResponseLength` uses the longest item.
- **Operations.** The codec does not add padding. A request contains the layout of its own class. A subclass can contain a collection, so the longest layout is not a fixed number. `maxResponseLength` is the maximum of `maxResponseLength` of all subclasses **[builder call]**.
- The short-response check uses the layout of the subclass that the discriminator selects.

### 4.6 Write

- For an instance of a subclass in the list, the codec sets the discriminator field to the value of that subclass. It calls the setter, then it writes the bytes. Thus, the object agrees with the bytes. (The codec sets a count field in the same way.)
- For an instance of a non-abstract base class that is not in the list, the codec writes the discriminator as the caller set it.
- If the discriminator is not in the request direction, the codec does not write it.

### 4.7 Read

- The codec reads the discriminator at its fixed offset. It selects the subclass. Then it decodes the layout of that subclass.
- An unknown value causes `MappingException`. Example message: `GetAccount.accountType (order 20): unknown discriminator value '07'. Known values: 01, 02`.

### 4.8 Client and class change

`BookmapClient.execute(request)` returns the response object. That object is not always the request object.

1. The client finds the codec by the exact class of the request. The registry has an entry for the base class and for each subclass in the list. All entries give the same codec.
2. On the first response, the codec selects the subclass.
   - If it is the class of the request, the codec fills the request object, as today.
   - If it is a subclass of the request class (the request is a non-abstract base class), the codec creates an instance of the subclass. It copies the annotated members of the base class and its superclasses from the request (all directions, collections as the same list), through the accessors. Then it decodes the response into the new instance. Fields without an annotation are not copied.
   - If the class of the request is not a superclass of the selected class (for example, the request is `CheckingAccount` and the response is `02`), the codec throws `MappingException`.
3. The client uses the returned object for validation, for `hasContinuation()`, and for the next page.
4. On a later page, the selected subclass must be the class of the current object. If not, the codec throws `MappingException`.

At the start, `resetOutputCollections` clears the `OUTPUT` collections of the runtime class, including the collections of the subclass.

The documentation of `execute()` changes from "returns the same object, filled" to "returns the response object. Without subtypes, this is the request object."

### 4.9 SPI change (compatible)

`BookCodec` gets two new `default` methods. A codec from the 0.1.0 processor still compiles and runs:

```java
/** Gives the classes that this codec handles. The default is type(). */
default List<Class<? extends T>> types() { return List.of(type()); }

/**
 * Decodes a response. Returns the object that contains the response: the target,
 * or a new instance of a subclass. The default calls decodeResponse and returns the target.
 */
default T readResponse(byte[] data, T target, CodecContext ctx) {
    decodeResponse(data, target, ctx);
    return target;
}
```

- `BookCodecRegistry` indexes each class of `types()`. Two codecs for one class is still an error.
- The client calls `readResponse`.
- In a codec with subtypes, `decodeResponse` fills the target if the response selects the class of the target. Else it throws `MappingException`.
- In a codec with an abstract base class, `newInstance()` throws `MappingException`.
- The method names are a **[builder call]**.

### 4.10 Generated code

- The processor generates **one codec for each hierarchy**: `<Base>_BookCodec implements BookCodec<Base>`. `type()` returns the base class. `types()` returns the base class (if it is not abstract) and the subclasses in the list. `META-INF/services` contains the one codec.
- The codec selects the subclass with generated code: a `switch` on the discriminator value, and a comparison of `getClass()` with class literals on write. No reflection.
- For an item base class, the operation codec contains the item dispatch code.
- The internal structure of the generated code is a **[builder call]**.

### 4.11 Simulator

- `register(Class<T>, SimulatedHandler<T>)` accepts the base class of a hierarchy. `decodeRequest` creates the subclass that the request discriminator selects. If the discriminator is not in the request, or the value has no subclass and the base class is not abstract, it creates the base class. Else it throws `MappingException`. The handler sets the output fields. `encodeResponse` writes the layout of the runtime class.
- If the request is shorter than the layout of the selected subclass, `decodeRequest` throws the usual short-data `MappingException`. This is an error in the test setup: the client sent a base instance with the value of a subclass that has `INPUT` fields. The simulator does not fall back to the base class **[builder call]**.
- Registration with a subclass of a hierarchy throws `IllegalArgumentException`. The message tells you to register the base class **[builder call]**.
- A new registration form lets a handler answer with a different object, for example a subclass:

```java
simulator.registerResponder(GetAccount.class, call -> {
    SavingsAccount account = new SavingsAccount();
    account.setAccountId(call.getAccountId());
    account.setInterestRate(new BigDecimal("0.0325"));
    return account;
});
```

`SimulatedResponder<T>` has one method: `T respond(T call)`. The different method name prevents an ambiguous lambda **[builder call]**.

### 4.12 Compile errors

The processor reports these new errors. Each message identifies the class, and the field and `order` where they apply.

- `discriminator` names a field that does not exist in the base class or its superclasses, or that is in a subclass.
- The discriminator has a type, a `converter`, or a `scale` that 4.2 does not permit.
- An operation discriminator with direction `INPUT`.
- An abstract operation base class with an `OUTPUT` discriminator.
- A collection before the discriminator.
- An empty `value` list.
- A `type` that is not a subclass of the base class, or that is abstract, not public, generic, or without a public no-argument constructor.
- The same `type` two times.
- A blank `value`, a duplicate `value` (after normalization), a value that is not a number for a numeric field, or a value that does not fit in the field.
- A `type` or a class between the base class and a `type` that has `@BookSubTypes` (subtypes in a subtype).
- A `type` that has `@BookOperation`.
- A `type` that is a subclass of another `type` (except the base class).
- A collection in a subclass with a count field in the base class.
- A duplicate `order` along a path from the base class to a subclass.
- A subclass member with an `order` that is not greater than all base members.
- A subclass without annotated members is permitted (for example, a record type without data) **[builder call]**.

## 5. Documentation and examples

- README: add a section "Subtypes (`REDEFINES`)". Change the limitation "No `REDEFINES`" to "No `REDEFINES` without a discriminator". Change "the same object, filled".
- Parent spec: in 2.2, change `REDEFINES` to "`REDEFINES` without a discriminator", with a link to this spec. In 5.8, describe the returned object.
- `bookmap-examples`: an operation `GetAccount` with the subclasses `CheckingAccount` and `SavingsAccount`, and a statement collection with the item subclasses `CardPayment` and `Transfer`. An end-to-end test with the simulator shows: a request with the base class and a response with a subclass, a mixed list of items, and an unknown discriminator value.

## 6. Left to the builder

- The names of the new SPI methods and of `SimulatedResponder`.
- The internal structure of the generated code and of the processor model.
- The exact text of error messages, if they contain the information in 4.7 and 4.12.
