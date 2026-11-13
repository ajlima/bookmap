# bookmap — Enums from level 88 items and `VALUE` initial values (design)

Date: 2026-11-13
Status: proposed.
Base package: `pt.dazz.bookmap`
Parent specs: `2026-11-10-bookmap-design.md`, `2026-11-11-bookmap-copybook-design.md`
Issue: [#36](https://github.com/ajlima/bookmap/issues/36)

The spec author wrote this spec without the project owner. Section 8 lists each decision for review, with the options and the reason for each choice.

## 1. Intent

A COBOL program often gives names to the values of a field with level 88 items:

```cobol
05  PARTY-ROLE              PIC X(8).
    88  PARTY-IS-SELLER     VALUE 'SELLER'.
    88  PARTY-IS-BILL-TO    VALUE 'BILL-TO'.
    88  PARTY-IS-SHIP-TO    VALUE 'SHIP-TO'.
```

Today, BookMap maps an enum field only through a hand-written `converter`. The copybook generator keeps only the names of the level 88 items, and ignores them. The generator also ignores the `VALUE` clause of an elementary item.

This design adds three features:

1. **Native enum fields** in `bookmap-core` and `bookmap-processor`. The developer puts `@CobolValue` on the enum constants. The processor generates the conversion code at compile time.
2. **Enums from level 88 items** in `bookmap-copybook`. The generator writes a Java `enum` for a field with discrete level 88 values. In the other cases, it writes constants and boolean methods.
3. **Initial values from `VALUE`**. The generator uses the `VALUE` clause of an elementary item as the initial value of the Java field.

Part 1 is one pull request. Parts 2 and 3 are a second pull request, after part 1.

## 2. Goals, non-goals, and anti-goals

### 2.1 Goals

- An enum field works without a converter, in hand-written classes and in generated classes.
- No reflection at runtime. The codec does not call `Enum.valueOf`, `values()`, or `getAnnotation`.
- The generated enum for the example in section 1 is `PartyRole` with the constants `SELLER`, `BILL_TO`, and `SHIP_TO`.
- 100% line and branch coverage in each module.

### 2.2 Non-goals

- An enum as a discriminator of subtypes, or as the count field of a collection.
- A comparison of text ranges in the EBCDIC collating sequence (see section 8.4).
- `VALUE` clauses of group items.

### 2.3 Anti-goals

- Silent data loss. An unknown value is an error by default. A fallback constant cannot be written back.
- A guess when the level 88 items are ambiguous. The generator then writes constants, not an enum, and tells why.

## 3. Part 1: native enum fields

### 3.1 Annotations

Two new annotations are in `pt.dazz.bookmap.annotation`. Both have `CLASS` retention, as `@BookField` has.

```java
public enum PartyRole {
    @CobolValue("SELLER") SELLER,
    @CobolValue("BILL-TO") BILL_TO,
    @CobolValue("SHIP-TO") SHIP_TO
}

public enum Grade {
    @CobolValue(value = {"A", "A+"}) EXCELLENT,
    @CobolValue(ranges = @CobolValue.Range(from = "B", thru = "D")) PASS,
    @CobolUnknown OTHER
}
```

- `@CobolValue` goes on an enum constant. `value` gives zero or more discrete values. `ranges` gives zero or more `THRU` ranges. A constant needs at least one value or one range.
- The **canonical value** of a constant is the first discrete value. If the constant has no discrete value, it is the `from` value of the first range. This is the value that COBOL `SET condition TO TRUE` moves.
- `@CobolUnknown` goes on one enum constant. It is the fallback constant (section 3.4). It can also have `@CobolValue`.

### 3.2 Field rules

A field is an enum field if its type is an enum and it has no `converter`. A field with a `converter` keeps the converter mapping, as today.

| Item | Rule |
|---|---|
| Category | A text `pic` (`X`) or no `pic` makes a text enum field. A numeric `pic` (`9`, `S`, `V`, `COMP`, `COMP-3`) makes a numeric enum field. |
| Text value | The processor removes the spaces on the right of each value, because a `PIC X(n)` value has spaces on the right. A value with only spaces is the blank value `""`. The value must not be longer than the field. The comparison is case-sensitive. |
| Numeric value | Each value is a decimal number, for example `"1"`, `"-5"`, or `"12.50"`. It must fit the `pic`: digits, decimal places, and sign. The comparison is numeric, thus `"1"` and `"01"` are the same value. |
| Overlap | Two constants must not share a value. A discrete value of one constant must not be in a range of another constant. Two ranges of two constants must not overlap. The values of one constant can overlap. |
| Range | `from` must not be more than `thru`. |
| Attributes | A text enum field has the rules of a `String` field: no `usage`, `signed`, or `scale`. A numeric enum field has the rules of a `BigDecimal` field. |
| Not permitted | An enum field cannot be a discriminator or a count field. |
| Access | The enum must be public, or in the package of the codec. |

The processor reports each problem as a compile error on the field, for example `Invoice.partyRole (order 30): enum PartyRole: value 'SELLER' of BILL_TO is also a value of SELLER`.

### 3.3 Read and write

- **Read.** The codec reads the text (spaces on the right removed) or the number. It finds the constant that contains the value: the discrete values first, then the ranges, in the order of the constants. A blank field (spaces, or a blank number) gives `null`, unless a constant has the blank value `""`.
- **Write.** The codec writes the canonical value of the constant. A `null` value writes spaces in a text field and zeros in a numeric field, as for `String` and `BigDecimal`.

### 3.4 Unknown values

- By default, an unknown value causes a `MappingException`: `Invoice.partyRole (order 30): unknown value 'AGENT' for enum PartyRole`.
- If the enum has a constant with `@CobolUnknown`, an unknown value gives that constant.
- A write of the fallback constant uses its canonical value if it has `@CobolValue`. Else the write causes a `MappingException`: `the constant OTHER has no COBOL value`. Thus, the codec does not write a value that it did not read.

### 3.5 Generated code

For each enum and category, the codec gets two private static methods. Two fields with the same enum and category share them.

```java
private static PartyRole partyRoleOf(String key, String field) {
    if (key.equals("SELLER")) return PartyRole.SELLER;
    if (key.equals("BILL-TO")) return PartyRole.BILL_TO;
    if (key.equals("SHIP-TO")) return PartyRole.SHIP_TO;
    if (key.isEmpty()) return null;
    throw new MappingException(field + ": unknown value '" + key + "' for enum PartyRole");
}

private static String partyRoleValue(PartyRole value, String field) {
    if (value == null) return null;
    if (value == PartyRole.SELLER) return "SELLER";
    ...
    throw new MappingException(field + ": the constant " + value.name() + " has no COBOL value");
}
```

The code uses `==` and `equals`. It does not use a `switch` on the enum, because a `switch` needs a synthetic class that maps the ordinals. It does not use reflection. The GraalVM native image needs no configuration.

## 4. Part 2: enums from level 88 items

### 4.1 Parse

`Condition` keeps the values: `Condition(String name, List<ConditionValue> values)`. `ConditionValue(Literal from, Literal thru)` is one value or one `THRU` range (`thru` is `null` for one value). `THROUGH` is a synonym of `THRU`.

`Literal(Kind kind, String text)` is one COBOL literal:

| Kind | Source | Text |
|---|---|---|
| `TEXT` | `'SELLER'` or `"SELLER"` | The characters between the quotes. |
| `NUMBER` | `1`, `-5`, `12.50` | The number as in the source. |
| `FIGURATIVE` | `SPACE`, `SPACES`, `ZERO`, `ZEROS`, `ZEROES`, `HIGH-VALUE(S)`, `LOW-VALUE(S)`, `QUOTE(S)` | The word, with the singular form: `SPACE`, `ZERO`, `HIGH-VALUE`, `LOW-VALUE`, `QUOTE`. |

`ALL literal` and a hexadecimal literal (`X'00'`) are not supported. The parser gives a `warning` and does not keep the condition.

`DataItem` gets a new component `value`: the `Literal` of the `VALUE` clause, or `null`.

### 4.2 Values for a field

The mapper converts each literal to a value of the field:

| Field | `TEXT` | `NUMBER` | `SPACE` | `ZERO` | Other figurative |
|---|---|---|---|---|---|
| Text (`PIC X`) | The text, with the spaces on the right removed. It must not be longer than the field. | Not valid. | `""` | `0` for each character of the field. | Not valid. |
| Number (`PIC 9`) | Not valid. | The number. It must fit the `pic`. | Not valid. | `0` | Not valid. |

### 4.3 The mode of a field

The new mapping option `conditions` sets the mode of a field, by the COBOL name of the elementary item. The values are `enum`, `constants`, and `none`.

Without the option, the mapper selects the mode:

1. **Constants** if one of these rules applies. An `info` diagnostic tells the rule and the option to change the mode:
   - The item is a `FILLER`.
   - The item is the discriminator of a subtype decision, or the item of a `DEPENDING ON`.
   - The item has only one level 88 item. For example, a flag with no opposite value.
   - A level 88 item has a `THRU` range.
   - A value is not valid for the field (section 4.2).
   - Two level 88 items share a value.
2. **Enum** in all other cases.

With `enum`, the mapper writes an enum. A range and one level 88 item are permitted. These cases are errors: a `FILLER`, a discriminator or count item, a value that is not valid, and two level 88 items that share a value.

With `constants`, the mapper writes constants. A level 88 item with a value that is not valid gets no constant, and a `warning`.

With `none`, the mapper ignores the level 88 items, as today, with an `info` diagnostic for each.

A name in `conditions` that is not an elementary item with level 88 items is an error.

A level 88 item of a group item is ignored, with an `info` diagnostic, as today.

### 4.4 Enum mode

The mapper adds an enum to the model. The field gets the Java type `ENUM` and the name of the enum.

- **Enum name.** The class name of the item, from the same rules as a class name (`stripPrefixes`, then upper camel case). It must be unique among all classes and enums of the copybook. A name that is already in use gets the class name of the owner as a prefix, then a number.
- **Constant names.** The names of the level 88 items, without the longest common prefix of hyphen-separated parts, with `_` for `-`. Example: `PARTY-IS-SELLER`, `PARTY-IS-BILL-TO`, `PARTY-IS-SHIP-TO` give `SELLER`, `BILL_TO`, `SHIP_TO`. The mapper keeps the full names if a short name is empty, starts with a digit, or is not unique.
- **Values.** Each constant gets `@CobolValue` with the values and ranges of its level 88 item, in source order.

The generated enum for section 1:

```java
/**
 * Generated by the BookMap copybook generator from {@code GET-INVOICE.cpy}.
 * The level 88 items of {@code PARTY-ROLE}.
 */
public enum PartyRole {
    /** {@code PARTY-IS-SELLER} */
    @CobolValue("SELLER")
    SELLER,
    ...
}
```

### 4.5 Constants mode

For each level 88 item, the class of the field gets:

- A constant with the canonical value: `public static final String PARTY_IS_SELLER = "SELLER";`. The type is the Java type of the field: `String`, `int`, `long`, or `BigDecimal`.
- A boolean method that tests the field: `public boolean isPartyIsSeller()`. It tests all values and ranges of the level 88 item.

The names come from the name of the level 88 item, with `stripPrefixes`. A name that is already in use in the class gets a number.

### 4.6 Model and JSON

- `JavaType` gets `ENUM`.
- `FieldMember` gets three components: `enumType` (the enum name, or `null`), `initialValue` (section 5, or `null`), and `conditions` (the constants of section 4.5, empty in the other modes).
- `BookModel` gets `enums`: each enum has `name`, `cobolName`, and `constants`. Each constant has `name`, `cobolName`, and `values`. Each value has `from` and an optional `thru`.
- The JSON keys `enumType`, `initialValue`, `conditions`, and `enums` are optional. The writer writes them only when they have a value. Thus, a copybook without level 88 items and `VALUE` clauses gives the same JSON as before. The `formatVersion` stays `1`.

### 4.7 Options

| Place | Format |
|---|---|
| Options file | `"conditions": { "PARTY-ROLE": "enum", "ACCOUNT-FLAG": "constants" }` |
| CLI | `--conditions PARTY-ROLE=enum`. The option can occur more than once. |
| Gradle and Maven plugins | The options file of each copybook. |

The mode is not case-sensitive.

## 5. Part 3: `VALUE` initial values

The `VALUE` clause of an elementary item gives the initial value of the Java field:

| Java type | Example | Initial value |
|---|---|---|
| `String` | `PIC X(3) VALUE 'EUR'` | `private String currency = "EUR";` |
| `String` | `PIC X(8) VALUE SPACES` | `private String note = "";` |
| `int`, `long` | `PIC 9(3) VALUE 0` | `private int count = 0;` |
| `BigDecimal` | `PIC S9(5)V99 VALUE 12.50` | `private BigDecimal rate = new BigDecimal("12.50");` |
| Enum | `PIC X(8) VALUE 'SELLER'` | `private PartyRole partyRole = PartyRole.SELLER;` |

The rules of section 4.2 apply. A value that is not valid gives a `warning`, and the field gets no initial value. An enum value that no constant contains gives a `warning`, and no initial value. A `VALUE` of a group is ignored.

## 6. Documentation

- `Annotations-reference.md`: `@CobolValue` and `@CobolUnknown`.
- `PIC-syntax-and-type-mapping.md`: enum fields.
- `Copybook-generator.md`: level 88 items, the `conditions` option, `VALUE`, and the JSON keys.
- `Copybook-generator-CLI.md`: `--conditions`.

## 7. Tests

- Processor: text and numeric enum fields, values with spaces, multiple values, ranges, the fallback constant, blank values, all compile errors, item fields, and a hand-written class.
- Copybook: the parser keeps the values, the three modes, all fallback rules, the names, the JSON, and the Java output.
- The examples: `GET-INVOICE.cpy` in the Gradle and Maven plugin examples gets `PARTY-ROLE` with three level 88 items. A test reads and writes the enum through the simulator.

## 8. Decisions for review

### 8.1 Annotation name and shape

- **Options:** (a) `@CobolValue` on the constants, with `value` and nested `@CobolValue.Range` for `THRU`. (b) Text with `THRU` in one string, for example `@CobolValue("A THRU C")`. (c) A `@BookEnum` on the enum type with a map from constants to values. (d) A name with the `Book` prefix, for example `@BookValue`.
- **Choice:** (a). The issue suggests `@CobolValue`. The values stay next to the constant. A nested annotation does not parse text, and a text value can contain ` THRU `. The `Book` prefix is for the parts of a book (operation, field, collection). A value is a COBOL value.

### 8.2 Unknown value handling

- **Options:** (a) A `MappingException` by default, and a fallback constant with `@CobolUnknown`. (b) An attribute on `@BookField`, for example `unknown = "OTHER"`. (c) `null` for an unknown value.
- **Choice:** (a). The fallback is a property of the enum, so all fields of the enum agree. A constant name in a string has no compile-time check in the IDE. Option (c) loses data silently. A fallback constant with no `@CobolValue` cannot be written: the codec does not write a value that it did not read.

### 8.3 Space padding and trim rules for text values

- **Choice:** The processor removes the spaces on the right of each text value. The reader removes them from the field, as for a `String` field. Spaces on the left stay, and the comparison is case-sensitive. A blank field gives `null`, unless a constant has the blank value.
- **Reason:** `PIC X(8) VALUE 'SELLER'` holds `SELLER` and two spaces. COBOL compares the two with spaces on the right. A blank field is usually "no value", as a blank number gives `null`.

### 8.4 Order of text ranges

- **Options:** (a) The order of Java `String.compareTo` (Unicode). (b) The order of the encoded bytes in the charset of the operation (EBCDIC).
- **Choice:** (a). The order is the same for letters and for digits, which are the usual range bounds. The codec does not encode the bounds for each read. The documentation tells that the order is not the EBCDIC order for mixed letters and digits.

### 8.5 Numeric values

- **Choice:** A numeric enum value is a decimal string. The comparison uses `BigDecimal.compareTo`, thus `"1"`, `"01"`, and `"1.0"` are equal. The processor checks that each value fits the `pic`. A `null` numeric enum writes zeros, as a `null` `BigDecimal` does.
- **Reason:** One rule for all numeric usages (`DISPLAY`, `COMP`, `COMP-3`). The check at compile time prevents a write error at runtime.

### 8.6 Overlap rules

- **Choice:** In the processor, an overlap between two constants is an error. In the generator without an option, an overlap gives constants mode. With the option `enum`, it is an error. An overlap in one constant is permitted.
- **Reason:** An overlap makes the read ambiguous. The generator does not guess.

### 8.7 When the generator writes an enum

- **Options:** (a) Discrete values only, two or more level 88 items. (b) Also with `THRU` ranges.
- **Choice:** (a) without the option, as the issue asks. A range or a single flag gives constants. The option `enum` permits ranges and one level 88 item, because the core supports them.

### 8.8 Enum naming

- **Choice:** The enum name comes from the item name (`PARTY-ROLE` gives `PartyRole`). The constant names drop the longest common prefix of the level 88 names (`PARTY-IS-`).
- **Reason:** Level 88 names usually repeat a prefix. The field name is not always that prefix. The full names stay when the short names do not work.

### 8.9 Option format

- **Options:** (a) A map from the COBOL name to the mode, as `directions`. (b) A list of names for each mode. (c) One global mode.
- **Choice:** (a). It follows `directions` and `subTypes`, and the CLI form `NAME=MODE` follows `--direction`. The modes are the words of the issue: `enum`, `constants`, `none`.

### 8.10 Model JSON

- **Choice:** New keys are optional, and `formatVersion` stays `1`.
- **Reason:** A model without level 88 items and `VALUE` clauses does not change. An older reader rejects a model with the new keys, because it rejects unknown keys.

### 8.11 Initial values

- **Choice:** All valid `VALUE` clauses of elementary items give an initial value, also `VALUE 0` for an `int`. `VALUE SPACES` gives `""`.
- **Reason:** The Java class then shows the same initial state as the COBOL program. A rule with exceptions is harder to explain.
