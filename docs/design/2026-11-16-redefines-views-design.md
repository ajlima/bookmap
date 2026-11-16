# REDEFINES without a discriminator: views (design)

Date: 2026-11-16
Status: approved.
Base package: `pt.dazz.bookmap`
Parent specs: `2026-11-10-bookmap-design.md`, `2026-11-11-bookmap-subtypes-design.md`
Issue: #38

## 1. Intent

A COBOL book often gives two or more layouts to the same bytes with `REDEFINES`. Subtypes (v0.2.0) support this case when a field in the book selects the layout (a discriminator).
Many books have no discriminator. The program and the caller select the layout from a return code, a range of values, or other data. Some books use `REDEFINES` only to give a second name to the same bytes, for example a structured view of a text code.

```cobol
01  GET-SHIPMENT.
    05  SHIPMENT-ID              PIC X(10).
    05  TRACKING-CODE            PIC X(16).
    05  TRACKING-PARTS REDEFINES TRACKING-CODE.
        10  CARRIER-CODE         PIC X(4).
        10  ROUTE-NUMBER         PIC 9(6).
        10  SEQUENCE-NUMBER      PIC 9(6).
    05  RESULT-CODE              PIC X(2).
    05  RESULT-DETAIL.
        10  ERROR-TEXT           PIC X(40).
    05  DELIVERY-DATA REDEFINES RESULT-DETAIL.
        10  DELIVERY-DATE        PIC 9(8).
        10  DELIVERED-WEIGHT     PIC S9(5)V99 COMP-3.
        10  SIGNED-BY            PIC X(20).
    05  SHIPPING-CHARGE          PIC S9(7)V99 COMP-3.
```

This design adds **views**. A view is a member that reads and writes the bytes of an earlier member. The codec decodes all views of an area on each read. Thus, all layouts are available at the same time, and the caller selects the layout.

## 2. What the code does today

These are facts from `main` (v0.26.0):

- `bookmap-processor`: a member is a `FieldModel` or a `CollectionModel` (sealed `MemberModel`). Each member occupies its own bytes after the member before it. The offset of a member is the sum of `areaLength()` of the members before it. `SubTypesGenerator` and `ItemMethodsGenerator` (the discriminator offset of an operation and of an item), `ModelBuilder` (`emptyKeyOffset`, record length in `long`), and `CodecGenerator.maxResponseLength()` all use this sum.
- A group without `OCCURS` has no Java class. The processor knows only operation classes, item classes, and subclasses.
- Two members with the same `order` along one path is a compile error: `duplicate order, also used by <Owner>.<name>`.
- `BookWriter` is append-only (`ByteArrayOutputStream`). It cannot write over bytes that it wrote before. `BookReader.peek(offset)` gives a second reader on the same bytes. The subtypes code uses it to read the discriminator.
- A decode error is a `MappingException`. Examples: `<field>: byte 0x40 at offset 0 of the field is not valid in a packed decimal`, and `<field>: response too short, need N bytes at position P but only R remain`. In an item, the message gets the prefix `<collection> item <i>: ` (v0.25.0, `MappingException.atItem`).
- On write, a `null` text gives spaces, and a `null` number gives zero.
- A text read changes each `0x00` byte to a space and removes the spaces on the right. A write fills the field with spaces. A packed write uses the sign nibble `F` (unsigned), `C`, or `D`. A packed read accepts `A`, `C`, `E`, and `F` as positive.
- The generator writes an initial value for an elementary item with `VALUE`. A collection field has no initial value.
- `ModelMapper` ignores the level 88 items of a `REDEFINES` item, with the `info` diagnostic `condition X is ignored`.
- The copybook generator (`RedefinesResolver`):
  - A group `REDEFINES` area becomes a direction split (`directions`, or a marker in the name) or subtypes (`subTypes`). Without a decision, the generator stops with `REDEFINES area INVOICE-QUERY at line 3 needs a decision. ...`.
  - An elementary item with `REDEFINES` is skipped, with the warning `E2 redefines E1 and is skipped`. A longer item is an error. An elementary item that redefines a group is an error.
  - A group that redefines an elementary item is an error: `<item> redefines <name>, which is not a group with the same parent`.
  - A group area in an alternative of another area (in a subtype, or in a split alternative): the generator keeps the redefined group and skips the others, with the warning `the REDEFINES area LIMITS is inside another REDEFINES area. BookMap keeps LIMITS and skips OTHER-LIMITS. See issues #38 and #39.`
  - An alternative with `OCCURS` is an error: `the REDEFINES alternative X has OCCURS. BookMap does not support this.`
  - `RecordLength` and `RedefinesResolver.length` already use the longest alternative.

## 3. Goals, non-goals, and anti-goals

### 3.1 Goals

- Map a `REDEFINES` area without a discriminator. Each alternative is a member of the same class.
- An alternative can be an elementary item, a group, or a fixed table.
- Views of different lengths. The area has the length of the longest view.
- A view can be in an operation class, an item class, a subclass, or a group class. A view can contain a fixed table and another area.
- Members after the area (a trailer) are permitted.
- The copybook generator maps an area to views with a new option.
- Support in the processor, `bookmap-core`, the simulator, the generator, the CLI, and the plugins. 100% line and branch coverage.

### 3.2 Non-goals

- A member of an area that is a variable collection (`OCCURS DEPENDING ON`) or that contains one. COBOL does not permit a variable item in a `REDEFINES` area either. An area in the item class of a variable collection is permitted, because the item length is fixed.
- A `@BookGroup` outside a `REDEFINES` area. See decision 3.
- Subtypes in a group class.
- A view with a different direction from its area. The direction split covers this case.
- A lazy decode on the first call of a getter. The POJO is the class of the user, so a getter cannot call the codec.

### 3.3 Anti-goals

- Runtime reflection.
- A change to a codec or a generated class of a book without views.
- A silent change of the output of the copybook generator for an existing copybook and options file.

## 4. Annotation API

### 4.1 The attribute `redefines`

`@BookField`, `@BookCollection`, and the new `@BookGroup` get one attribute:

| Attribute | Meaning | Default |
|---|---|---|
| `redefines` | The Java field name of the member that this member redefines. The member is in the same class. | `""`: not a view. |

### 4.2 The annotation `@BookGroup`

`@BookGroup` (field, in `pt.dazz.bookmap.annotation`, retention `CLASS`) maps a COBOL group without `OCCURS` to a field of a **group class**.

| Attribute | Meaning | Default |
|---|---|---|
| `order` | The position in the book. | Mandatory. |
| `direction` | The direction. Only on the redefined member of an area at the operation level. | `INPUT_OUTPUT` |
| `redefines` | See 4.1. | `""` |

A group class follows the rules of an item class: public, not generic, a public no-argument constructor, at least one `@BookField`, and only fixed collections. Its members have no `direction`. `@BookDefaults` and the charset rule of an item class apply.
A group class can contain a `REDEFINES` area. Thus, an area in an area is possible.

### 4.3 Terms

- An **area** is one redefined member and all its views.
- The **redefined member** is the first member of the area. It has no `redefines`. It can be a `@BookField`, a `@BookGroup`, or a fixed `@BookCollection`.
- A **view** is a member with `redefines`. Each view names the redefined member, never another view. This is the COBOL rule.

### 4.4 Example

```java
@BookOperation
public class GetShipment {

    @BookField(order = 10, pic = "X(10)", direction = Direction.INPUT)
    private String shipmentId;

    @BookField(order = 20, pic = "X(16)", direction = Direction.OUTPUT)
    private String trackingCode;

    @BookGroup(order = 30, redefines = "trackingCode")
    private TrackingParts trackingParts;

    @BookField(order = 40, pic = "X(2)", direction = Direction.OUTPUT)
    private String resultCode;

    @BookGroup(order = 50, direction = Direction.OUTPUT)
    private ResultDetail resultDetail;

    @BookGroup(order = 60, redefines = "resultDetail")
    private DeliveryData deliveryData;

    @BookField(order = 70, pic = "S9(7)V99 COMP-3", direction = Direction.OUTPUT)
    private BigDecimal shippingCharge;
}

public class TrackingParts {
    @BookField(order = 10, pic = "X(4)") private String carrierCode;
    @BookField(order = 20, pic = "9(6)") private int routeNumber;
    @BookField(order = 30, pic = "9(6)") private int sequenceNumber;
}

public class ResultDetail {
    @BookField(order = 10, pic = "X(40)") private String errorText;
}

public class DeliveryData {
    @BookField(order = 10, pic = "9(8)") private LocalDate deliveryDate;
    @BookField(order = 20, pic = "S9(5)V99 COMP-3") private BigDecimal deliveredWeight;
    @BookField(order = 30, pic = "X(20)") private String signedBy;
}
```

The area `resultDetail` has 40 bytes. `deliveryData` has 32 bytes. `shippingCharge` starts after the 40 bytes.

## 5. Rules

### 5.1 Order and position

- A view has its own unique `order`. The duplicate `order` rule does not change.
- A view comes right after the redefined member, or after another view of the same member. No other member is between them.
- The area occupies the bytes once. Its length is the length of the longest member of the area.
- A view occupies 0 bytes after the area start. Thus, a view does not move the members after the area.

The model gets a new member `AreaModel` (section 9). It contains the redefined member and its views, in order. `AreaModel.areaLength()` is the length of the longest member.
All code that adds `areaLength()` sees the area once: the record length check, `maxResponseLength()`, the discriminator offset, `emptyKeyOffset`, the item length, and the padding of a shorter item subtype.

### 5.2 Direction

- The area has the direction of the redefined member. A view must not set `direction`.
- In an item class or a group class, no member has a direction. The rule does not change.

### 5.3 Java types

- A redefined member and a view at the top of an area must not have a primitive type (`int`, `long`). A view that is not valid reads as `null` (5.5), and a primitive cannot hold `null`. Use `Integer`, `Long`, or `BigDecimal`.
- A field in a group class or in an item class of a view can have a primitive type. If the view is not valid, the full view is `null`.

### 5.4 Members that a top member of an area cannot be

The **top members** of an area are the redefined member and its views. A top member must not be:

- A count field (`dependsOn`, also the default "member immediately before the collection").
- A discriminator of `@BookSubTypes`.
- An `emptyKey` field.
- A collection with `dependsOn`, or a collection that is not fixed.
- A collection with `partial = true`.

The members inside a view (in a group class, or in the item class of a collection view) follow the rules of an item class. For example, a fixed collection in a group class can have an `emptyKey` or a count field in the same group class.

### 5.5 Read

1. The codec checks that the data holds the full area (the longest member). If not, it throws the usual short-data `MappingException` with the descriptor of the redefined member.
2. The codec decodes each member of the area from the start of the area, in order. Each member uses its own length.
3. If the decode of a member throws `MappingException`, the codec sets the member to `null`. It does not throw. Then it decodes the next member.
4. After the area, the reader continues at the start of the area plus the area length.

All members of an area are equal. The redefined member is not stricter than a view.
A text view is almost always valid. A numeric, date, or enum view is `null` if its bytes are not valid for its type.
The caller selects the view. In the example, the caller reads `deliveryData` if `resultCode` is `"00"`, and `resultDetail` otherwise.

A collection in an area does not keep the items of earlier pages: each response sets a new list. `resetOutputCollections` does not change a collection in an area.

### 5.6 Write

1. The codec writes the **first member of the area, in `order`, that is not `null`**. For a collection, an empty list is not `null`.
2. If the member is shorter than the area, the codec fills the remaining bytes with spaces, as for a shorter subtype.
3. If all members of the area are `null`, the codec writes spaces for the full area.
4. The codec ignores the other members. It does not compare them.

The caller who sends a view sets the members before it to `null`.
The processor cannot see a Java initializer. Do not give an initial value to a top member of an area: that member then always wins.

The codec keeps no raw bytes of the area. The object holds only the decoded members.
After a read, all valid members are not `null`. A second write of the same object (the next page of a `Continuable` request, or the simulator) writes the first valid member. The bytes can then be different from the bytes of the read:

- A text member changes each `0x00` byte to a space. Thus, a text member over a packed or binary view changes the bytes of that view.
- A signed packed or zoned member writes the positive sign `C`, also for a read with `F`, `A`, or `E`.

To send the bytes of a view again, set the members before it to `null`. For an `INPUT_OUTPUT` area of a `Continuable` operation, the caller does this before each page.

### 5.7 Collections

- An area can be in an item class. The item length counts the area once.
- A view can be a fixed collection, or a group class that contains fixed collections. The `MAX_LEVELS` limit counts the collections, not the groups.
- An area in an item gives the error prefix `<collection> item <i>: ` as other item errors.
- The marker modes (`emptyItem`, `emptyKey`) read the bytes of the slot. A view does not change them.

### 5.8 Subtypes

- A base class, a subclass, and an item subclass can contain an area.
- The discriminator must not be in an area (5.4).
- A group class must not have `@BookSubTypes`.
- The redefined member and its views are in the same class. A subclass cannot redefine a member of the base class.

### 5.9 Partial responses and `maxResponseLength()`

- A `partial` collection must not be in an area (5.4).
- An `OUTPUT` area after a `partial` collection gives the existing error `partial needs the collection at the end of the response: <descriptor> comes after it`. The descriptor is the one of the redefined member.
- A response that ends inside an area is too short. Only a `partial` collection can end a response.
- `maxResponseLength()` counts the area once, with its longest member.

## 6. Compile errors

Each message starts with the descriptor `<Owner>.<name> (order <n>): ` of the annotated field, or with `<Class>: ` for a class.

| Message | Cause |
|---|---|
| `redefines member 'resultDetails' does not exist in GetShipment` | No annotated member has this name in the class. |
| `redefines member 'deliveryData' is a view. Name the redefined member 'resultDetail'` | The view names another view. |
| `redefines member 'shippingCharge' must come before the view` | The redefined member has a larger `order`. |
| `a view must come right after the member it redefines: GetShipment.resultCode (order 40) is between them` | Another member is between the redefined member and the view. |
| `a view must not set direction. It uses the direction of GetShipment.resultDetail (order 50)` | A view has `direction`. |
| `a member of a REDEFINES area must not have a primitive type. Use Integer, Long, or BigDecimal` | 5.3. |
| `a collection in a REDEFINES area must have fixed = true and no dependsOn` | 5.4. |
| `partial is not permitted in a REDEFINES area` | 5.4. |
| `dependsOn field 'x' must not be in a REDEFINES area` | 5.4. Also for the default count field. |
| `discriminator must not be in a REDEFINES area` | 5.4. |
| `emptyKey field 'x' must not be in a REDEFINES area` | 5.4. |
| `a @BookGroup must be in a REDEFINES area` | A `@BookGroup` that no view names, and without `redefines`. |
| `group class X must be a public class with a public no-argument constructor` | 4.2. |
| `group class X must not have type parameters` | 4.2. |
| `group class X must have at least one @BookField` | 4.2. |
| `group class X must not have @BookSubTypes` | 5.8. |
| `group class X contains itself: A > B > A` | A group class that contains itself, directly or through an item class. |
| `the REDEFINES area has <n> bytes, more than the maximum of 2147483647 bytes` | The longest member is too long. |

A field with `@BookField(redefines = ...)` and `@BookCollection` gives the existing error `a field must not have both @BookField and @BookCollection`. The same rule applies to `@BookGroup`: `a field must have only one of @BookField, @BookCollection, and @BookGroup`.

## 7. Runtime messages

The area adds no new runtime message. These existing messages apply:

| Case | Message |
|---|---|
| The response ends inside the area. | `GetShipment.resultDetail (order 50): response too short, need 40 bytes at position 18 but only 12 remain` |
| The same, in an item. | `GetRoute.stops (order 30) item 2: RouteStop.stopDetail (order 20): response too short, need 24 bytes at position 118 but only 5 remain` |
| The written member has a text that is too long. | `ResultDetail.errorText (order 10): text is longer than 40 characters (43)` |

A member that is not valid on read gives `null` and no message (5.5).

## 8. Runtime API in `bookmap-core`

- `@BookGroup` (new) and the attribute `redefines` on `@BookField` and `@BookCollection`.
- `BookReader.area(String field, int length)` (new). It checks that `length` bytes remain, with the message of `take`. It gives a reader at the start of the area, and it moves this reader after the area.
- `BookReader.orNull(Supplier<J> read)` (new, static). It calls `read`, and it gives `null` if `read` throws `MappingException`. A generated codec calls it once for each member of an area. A lambda is not reflection.
- `BookWriter`: no change. The generated code selects the member before it writes, and it calls `writeFiller` for the remaining bytes.

The method names are a builder call.

## 9. Processor

- `MemberModel` permits two new records:
  - `GroupModel(ownerSimpleName, name, order, direction, type, members, byteLength, getter, setter, redefines)`.
  - `AreaModel(List<MemberModel> members)`. `name()`, `order()`, `direction()`, and `ownerSimpleName()` come from the redefined member. `areaLength()` is the maximum.
- `FieldModel` and `CollectionModel` get no new component. The `ModelBuilder` puts each member of an area into its `AreaModel`, so the codec code sees one member.
- `LayoutCode`, `ItemMethodsGenerator`, `SubTypesGenerator`, and `CodecGenerator` get a case for `AreaModel` and `GroupModel`. A group class gets generated `write<Group>` and `read<Group>` methods, as an item class.
- `ModelSnapshot` (main code that the tests read) gets the values `redefines` and `group` in `Member`.

## 10. Copybook generator

### 10.1 The option

A new option `views` lists the `REDEFINES` areas to map to views:

```json
{
  "views": ["TRACKING-CODE", "RESULT-DETAIL"]
}
```

On the command line, use `--view TRACKING-CODE --view RESULT-DETAIL`.

- A name is the redefined item or one alternative of the area. The usual name rules apply (`OF`, `IN`, no case).
- The area can be a group area or an elementary area. A group can redefine an elementary item, and the reverse.
- The area can be at any level: in a record, in an `OCCURS` entry, in a subtype, in a split alternative, or in another view.
- Without the option, the generator output does not change.

### 10.2 What the generator writes

- An elementary alternative gives a `@BookField`. An integer gets `Integer` or `Long`, not `int` or `long`.
- A group alternative gives a `@BookGroup` and a group class (`ClassKind.GROUP`). The class name comes from the item name, with the rules of an item class name.
- An alternative with `OCCURS n` gives a fixed `@BookCollection`.
- Each alternative after the first gets `redefines` with the Java name of the first.
- An elementary top member gets no initial value (5.6). Its `VALUE` gives the `info` diagnostic `the VALUE of X is ignored, because X is in a REDEFINES view`. The members inside a group class keep their initial values.
- The level 88 items of an elementary top member give an enum or constants, as for other fields.
- The members of a view get the direction of the area: `INPUT_OUTPUT`, or the direction of the split alternative that contains the area.
- The Javadoc of each member shows the COBOL line, as today.

For the example of section 1, with `"views": ["TRACKING-CODE", "RESULT-DETAIL"]`, and a date hint for `DELIVERY-DATE`, the generator writes the classes of 4.4, without the directions. `routeNumber` stays `int` in the group class `TrackingParts`.

### 10.3 Diagnostics

New errors:

| Message | Cause |
|---|---|
| `views option: X is not a REDEFINES item` | The item has no `REDEFINES` and no item redefines it. |
| `views option: the REDEFINES area X also has a subtype decision. Give one decision.` | `views` and `subTypes` name the same area. |
| `views option: the REDEFINES area X also has a direction decision. Give one decision.` | `views` and `directions` name the same area. |
| `views option: two names give the REDEFINES area X` | Two names in `views` give the same area. |
| `the REDEFINES alternative X has OCCURS DEPENDING ON. A view needs a fixed size.` | An alternative contains a variable table. |
| `count field option: T is in a REDEFINES view. A table in a view must not have a count field.` | `countFields` names a table in a view. |
| `partial table option: T is in a REDEFINES view` | `partialTables` names a table in a view. |
| `the discriminator D must not be in a REDEFINES view` | A subtype decision names an item in a view. |

Changed messages. Each one gets a hint about the option:

| Today | With this design |
|---|---|
| `REDEFINES area INVOICE-QUERY at line 3 needs a decision. ... "02"}}}.` | The same text, then ` Or map the area to views: "views": ["INVOICE-QUERY"].` |
| `E2 redefines E1 and is skipped` | `E2 redefines E1 and is skipped. To keep both, add "views": ["E1"].` |
| `the REDEFINES area LIMITS is inside another REDEFINES area. BookMap keeps LIMITS and skips OTHER-LIMITS. See issues #38 and #39.` | `... BookMap keeps LIMITS and skips OTHER-LIMITS. To keep both, add "views": ["LIMITS"].` |

These three messages are in `docs/wiki/Copybook-generator.md`. The two errors about a longer elementary item and an elementary item that redefines a group stay, if the area has no `views` option.

### 10.4 The places to change

`MappingOptions` gets a new component `List<String> views`, at the end. These places build a `MappingOptions` record, and each one must pass the new component:

- `MappingOptions`: the canonical constructor (a copy of the list). A new overload keeps the current constructor with 14 components and gives an empty list. The other overloads call it through the chain. `defaults()` uses an overload. `withHints` passes `views`.
- `GeneratorConfigJson`: the key `views` in `KEYS`, and the constructor call (line 65).
- `Main` (CLI), line 273: the option `--view`, the constructor call, and the usage text.
- `CopybookTree`, line 202: the constructor call. The Gradle plugin and the Maven plugin only call `CopybookTree.run`, so they need no change. The option is in the options file.

Other generator changes:

- `RedefinesResolver`: a views decision, checked before the subtype and direction decisions. The nested-area rule and the elementary rule use the views decision first.
- `model`: a new `GroupMember` in the sealed `MemberModel`, and a component `redefines` in `FieldMember`, `CollectionMember`, and `GroupMember`. `ClassKind.GROUP`.
- `JavaClassGenerator` and `ModelJson`: the new member and component.
- `CountFieldResolver`, `PartialTableResolver`, and the discriminator check: the new errors of 10.3.
- `ModelMapper.info` and `ConditionMapper`: the level 88 items of an elementary member of a views area. Today they are ignored.
- The initial value rule of 10.2.
- `RecordLength`: no change. It already uses the longest alternative.

## 11. Tests

- Processor: each compile error of section 6, and a `ModelSnapshot` of the example.
- Core: `BookReader.area` and `orNull`.
- End to end with the simulator, in `bookmap-examples`: the example of 4.4 with a delivered and a failed shipment; an area in an item; a view with a fixed table; an area in an area; an `INPUT_OUTPUT` area on two pages of a `Continuable` operation.
- Generator: a golden test for each case of 10.1, each diagnostic of 10.3, and the unchanged output without the option.
- Mutation tests on `bookmap-core`, `bookmap-processor`, and `bookmap-copybook`.

## 12. Documentation

- A new wiki page "REDEFINES views", with the example of 4.4 and the rules of 5.5 and 5.6.
- `docs/wiki/Annotations-reference.md`: `@BookGroup` and `redefines`.
- `docs/wiki/Copybook-generator.md` and `docs/wiki/Copybook-generator-CLI.md`: the option `views` and `--view`, and the changed messages.
- `docs/wiki/Home.md`: remove the limitation "No `REDEFINES` without a discriminator". The README has no list of limitations.
- `README.md`: a row "`REDEFINES` without a discriminator" in the feature table, next to the row of subtypes.
- The subtypes design, 2.2, and the main design: a link to this spec.
- `ROADMAP.md`: remove #38.

## 13. Release

A `feat:` commit merged to `main` publishes a new minor version. The owner must approve the merge.

## 14. Decisions for review

1. Names: the attribute `redefines` and the annotation `@BookGroup`. Other names: `@BookView`, `overlays`. `redefines` uses the COBOL word.
2. A view names the redefined member, not the view before it. This is the COBOL rule.
3. `@BookGroup` only in a `REDEFINES` area. A general `@BookGroup` (a group without `OCCURS` anywhere) is a small extension. It needs a rule for a `null` group on write. The generator would still flatten a normal group.
4. Read: decode all members of the area, every time. A lazy decode is not possible without a codec in the POJO.
5. Read: a member that is not valid gives `null`, with no error and no log (5.5). Alternatives:
   - Strict by default, with an opt-in `lenient` attribute. Most areas with a number view would then need the attribute.
   - Keep the reason: a `MappingException` list in the `CodecContext`, or a log line at `DEBUG`.
6. Read: all members of an area are equal. The redefined member is not stricter.
7. A primitive type is a compile error at the top of an area (5.3). The generator writes `Integer` and `Long` there.
8. Write: the first member in `order` that is not `null` wins (5.6). The others are ignored. Alternatives:
   - The redefined member always writes. The caller then cannot send another view.
   - An error if more than one member is not `null`. This fails on the second page of a `Continuable` request after a read, and in the simulator.
   - Write all members that are not `null`, and fail if their bytes differ. This can fail after a read because of a sign nibble or a `0x00` byte.
   - Keep the raw bytes: a top member of type `byte[]`, or a hidden copy in the codec. A `byte[]` member gives an exact round trip, but it is a new Java type for all fields. A hidden copy needs state in the POJO.
9. Write: an area with all members `null` gives spaces.
10. A view has the direction of the area. A different direction stays the direction split.
11. A longer view is permitted. The area has the length of the longest member.
12. Members after the area are permitted. Subtypes keep their rule (no trailer).
13. A collection in an area does not keep the items of earlier pages.
14. The generator option `views` (`--view`). Without it, the output does not change. The three hints of 10.3 change documented messages.
15. In a `views` area, the generator writes `Integer` and `Long` at the top of the area, but `int` and `long` in a group class.
16. A top member of a `views` area gets no initial value from `VALUE`.
