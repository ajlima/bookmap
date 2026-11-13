# BookMap, packed dates: design

Date: 2026-11-13
Status: approved.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (sections 2.2 and 5.3), `docs/design/2026-11-13-generator-date-hints-design.md` (the date hints of the generator), GitHub issue #50.

## 1. Intent

A COBOL program often keeps a date in a packed field, for example `ORDER-DATE PIC S9(8) COMP-3` with the value `20261113`.
Today, a `LocalDate` field must have the usage `DISPLAY`. A packed date needs a `FieldConverter`.
The copybook generator also rejects a date hint on a `COMP-3` item.

This design lets `@BookField` map a packed field to `LocalDate`, `LocalTime`, or `LocalDateTime` with a pattern.
The pattern rules are the same as for a `DISPLAY` date. The digits of the packed number are the text of the pattern.

## 2. Goals, non-goals, and anti-goals

Goals:

- A packed date, time, or date and time without a converter.
- The same pattern, zero, and round-trip rules as a `DISPLAY` date.
- A compile error for each packed date that the codec cannot read or write.
- A date hint of the generator on a `COMP-3` item.

Non-goals:

- Binary dates (`COMP`, `COMP-5`). They stay a compile error. Write a converter for them.
- A century digit that is not a constant, for example `CYYMMDD` with `C` = `0` for 19xx and `1` for 20xx in one field. `DateTimeFormatter` has no letter for it. Write a converter for it.
- Signed or decimal `DISPLAY` dates. They stay a compile error.
- A change of the `@BookDefaults` rules.

Anti-goals:

- A change of the bytes of an existing class. A field that compiles today gives the same bytes after this change.
- A silent change of a value. The codec never truncates a value and never ignores a sign.

## 3. Annotation API

The API does not change. A date field gets the usage `COMP3` from its `pic`, or from an explicit `usage`:

```java
@BookField(order = 10, pic = "S9(8) COMP-3", pattern = "yyyyMMdd")
private LocalDate orderDate;

@BookField(order = 20, pic = "9(7) COMP-3", pattern = "yyyyDDD")
private LocalDate shipDate;

@BookField(order = 30, pic = "S9(7) COMP-3", pattern = "'0'HHmmss")
private LocalTime shipTime;

@BookField(order = 40, usage = Usage.COMP3, pattern = "yyyyMMddHHmmss")
private LocalDateTime lastUpdate;
```

Without `pic`, the length is the length of the pattern text, as for a `DISPLAY` date. The field `lastUpdate` has 14 digits, thus 8 bytes.
The attribute `signed` is permitted on a packed date without `pic`. It is not permitted on a `DISPLAY` date.

`@BookDefaults(usage = ...)` does not apply to a date field. This rule does not change: a date field is `DISPLAY` unless its `pic` or its `usage` says `COMP-3`.

## 4. Valid fields

### 4.1 Pattern and digit count

- The pattern follows the rules of a `DISPLAY` date: valid, a fixed length, and it fits the Java type.
- The pattern must give digits only. The processor formats the two sample values of the type (see `FieldResolver`), and each character must be `0` to `9`. A quoted literal of digits is permitted.
- The number of digits of the field must be equal to the length of the pattern text.

Examples:

| Picture | Pattern | Java type | Bytes |
|---|---|---|---|
| `S9(8) COMP-3` or `9(8) COMP-3` | `yyyyMMdd` | `LocalDate` | 5 |
| `S9(7) COMP-3` | `yyyyDDD` | `LocalDate` | 4 |
| `S9(7) COMP-3` | `'0'yyMMdd` | `LocalDate` | 4 |
| `S9(7) COMP-3` | `'01'yyDDD` (CICS `EIBDATE`, `0CYYDDD` for the years 2000 to 2099) | `LocalDate` | 4 |
| `S9(6) COMP-3` | `HHmmss` | `LocalTime` | 4 |
| `S9(7) COMP-3` | `'0'HHmmss` (CICS `EIBTIME`, `0HHMMSS`) | `LocalTime` | 4 |
| `S9(14) COMP-3` | `yyyyMMddHHmmss` | `LocalDateTime` | 8 |

Compile errors, in addition to the errors of a `DISPLAY` date:

| Field | Message |
|---|---|
| `pic = "S9(8) COMP-3"`, `pattern = "yyyy-MM-dd"` | `pattern 'yyyy-MM-dd' gives characters that are not digits, but a COMP3 field holds only digits` |
| `pic = "S9(7) COMP-3"`, `pattern = "yyyyMMdd"` | `length 7 does not agree with pattern 'yyyyMMdd' (8 characters)` (the current message) |
| `pic = "S9(6)V99 COMP-3"` | `a decimal pic is not permitted for LocalDate` |
| `pic = "S9(8)"` (signed `DISPLAY`) | `a signed pic is permitted only with COMP-3 for LocalDate` |
| `pic = "9(8) COMP"` | `COMP is not permitted for LocalDate` (the current message) |
| `signed = true` on a `DISPLAY` date | `signed is not permitted for LocalDate` (the current message) |
| `sign = ...` on a date | `sign is not permitted for LocalDate` (the current message) |

### 4.2 Sign nibble on write

The codec writes the sign of the field rule of `PackedDecimal`: `C` for a signed field, `F` for an unsigned field.
A date is never negative, so `D` never occurs.

### 4.3 Read

| Bytes | `LocalDate`, `LocalDateTime` | `LocalTime` |
|---|---|---|
| Only `0x00` (LOW-VALUES) | `null` | `null` |
| Only spaces of the charset | `null` | `null` |
| Packed zero, for example `00 00 00 0C` or `00 00 00 0F` | `null` | `LocalTime.MIDNIGHT` |
| Valid digits and a positive sign (`A`, `C`, `E`, `F`) | the parsed value | the parsed value |
| A negative sign (`B`, `D`), also with zero digits | `MappingException` | `MappingException` |
| A digit nibble more than 9, a pad nibble that is not 0, or a sign nibble that is not valid | `MappingException` | `MappingException` |
| Digits that are not a valid date or time, for example `20261332` | `MappingException` | `MappingException` |

These rules are the rules of a `DISPLAY` date, and the blank rules of a packed number.
For an unsigned field, the codec also accepts `C`, as it does for a packed number.

The text for the parser has the leading zeros of the field. For example, the time `00:15:00` in `S9(6) COMP-3` is the number `1500`, and the parser gets `001500`.

### 4.4 Write

- A `null` value writes packed zero with the sign nibble of the field. It does not write LOW-VALUES. This is the rule of a `DISPLAY` date.
- The codec formats the value, checks the length, and parses the text again. If the pattern cannot keep the value, the codec throws `MappingException`. This is the round-trip check of a `DISPLAY` date.

### 4.5 Round trip

For each value that the codec writes without an error, a read gives the same value.
There is one exception, the same as for a `DISPLAY` time: a `null` `LocalTime` writes zeros, and the read gives `LocalTime.MIDNIGHT`.

## 5. Core and processor

### 5.1 Core

New public methods. The current methods do not change.

```java
// BookReader
public LocalDate readPackedDate(String field, int length, boolean signed, DateTimeFormatter formatter)
public LocalTime readPackedTime(String field, int length, boolean signed, DateTimeFormatter formatter)
public LocalDateTime readPackedDateTime(String field, int length, boolean signed, DateTimeFormatter formatter)

// BookWriter
public void writePackedTemporal(String field, TemporalAccessor value, int length, boolean signed,
                                DateTimeFormatter formatter)
```

`length` is the number of digits. `TemporalCodec` gets two packed methods. They use `PackedDecimal` for the bytes, and the current format, parse, and round-trip code for the text.

### 5.2 Processor

- `FieldResolver` permits the usage `COMP3` for a date type. `COMP` stays an error.
- A signed `pic` and the attribute `signed` are permitted on a date type only with `COMP3`. A decimal `pic` stays an error.
- For `COMP3`, the processor checks that the pattern gives digits only.
- `CodecGenerator` calls the packed methods for a date field with the usage `COMP3`. The change stays in the two cases of date types.

## 6. Generator

The date hints get the same rules. `DateHints.check` changes:

- The usage must be `DISPLAY` or `COMP-3`. `COMP`, `COMP-4`, and `COMP-5` stay an error: `a date or time field must have the usage DISPLAY or COMP-3`.
- A `DISPLAY` item must not have `S` or `V`, as today.
- A `COMP-3` item can have `S`. It must not have `V`.
- For a `COMP-3` item, the pattern must give digits only.
- The length of the pattern text must be equal to the digits of the picture, as today.

The generated field keeps the picture with the usage, for example `pic = "S9(7) COMP-3"`, and the `pattern`.
The generator needs no new option. `MappingOptions`, the model JSON, `formatVersion`, the CLI, and the build plugins do not change.

## 7. Tests

- Core: encode and decode of each row of section 4.3, the sign nibble, an even and an odd digit count, and the round-trip check.
- Processor: each compile error of section 4.1, and a compile test for each Java type with `pic` and without `pic`.
- Codec: a round trip through a generated codec for each Java type, also in a collection.
- Generator: a hinted `COMP-3` item, the errors of section 6, and an end-to-end test that compiles the generated class and round-trips a date.
- 100% line and branch coverage, and a mutation score of 95% or more for core, processor, and copybook.

## 8. Documentation

- `docs/wiki/Home.md`: remove "Packed dates need a converter" from the known limitations.
- `docs/wiki/PIC-syntax-and-type-mapping.md`: a section for packed dates.
- `docs/wiki/Annotations-reference.md` and the Javadoc of `BookField.pattern`.
- `docs/wiki/Copybook-generator.md`: the new rule, the new message, and the limitations.
- `ROADMAP.md`: remove #50.

## 9. Decisions for review

The designer made these decisions. Each one lists the options and the reason.

1. **Digit count against the pattern length.**
   Options: (a) the digits must be equal to the pattern length; (b) the field can have more digits, and the extra leading digits must be zero.
   Choice: (a). A quoted literal covers the common layouts: `'0'HHmmss` for `0HHMMSS` in `S9(7)`, `'01'yyDDD` for `EIBDATE`. The strict parser rejects a different digit, so a value for the year 1999 in `EIBDATE` is an error, not a wrong date. The rule is the same as for `DISPLAY`.

2. **The sign nibble on write.**
   Options: (a) the rule of the field: `C` if signed, `F` if not; (b) always `F`; (c) always `C`.
   Choice: (a). It is the rule of a packed number. The bytes agree with a COBOL `MOVE` to the same field.

3. **A negative sign on read.**
   Options: (a) `MappingException`; (b) ignore the sign.
   Choice: (a). A negative date is a data error. To ignore it is a silent change of a value.

4. **`S` in the picture.**
   Options: (a) permitted only with `COMP-3`; (b) permitted for all usages; (c) never permitted.
   Choice: (a). Most packed dates are signed in the copybooks. A signed `DISPLAY` date has an overpunch sign in its last digit, so it needs a different text rule. That is out of scope.

5. **The attribute `signed` without `pic`.**
   Options: (a) permitted with `COMP3`; (b) not permitted, use `pic`.
   Choice: (a). Without it, a signed packed date needs `pic`. The two ways to declare a field stay equal.

6. **`@BookDefaults(usage = COMP3)` and date fields.**
   Options: (a) no change: the default usage applies only to numeric types; (b) the default usage also applies to date fields.
   Choice: (a). Option (b) changes the bytes of the existing classes with this default.

7. **The Java types.**
   Options: (a) `LocalDate` only; (b) `LocalDate`, `LocalTime`, and `LocalDateTime`.
   Choice: (b). The codec is the same for the three types, and `EIBTIME` is a packed time.

8. **Zero and blank values.**
   Options: (a) the rules of a `DISPLAY` date for zero, and the rules of a packed number for LOW-VALUES and spaces; (b) packed zero is always `null`, also for a time.
   Choice: (a). A time field reads the same value for zeros in `DISPLAY` and in `COMP-3`.

9. **New core methods or a new parameter.**
   Options: (a) new methods `readPackedDate`, `readPackedTime`, `readPackedDateTime`, and `writePackedTemporal`; (b) overloads of the current methods with a `Usage` parameter.
   Choice: (a). A `Usage` parameter permits `COMP`, which has no meaning here. The names tell the format.
