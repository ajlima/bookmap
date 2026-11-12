## PIC syntax

The processor parses `pic` at compile time. An incorrect or unsupported `pic` is a compile error.

- The prefix `PIC` or `PICTURE` and a final `.` are optional. Letters are not case-sensitive.
- Text: `X(n)` or a sequence of `X`.
- Number: optional `S`, integer digits `9(n)` or `999`, and optional `V` followed by decimal digits.
  Examples: `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`.
- A repeat count `(n)` is from 1 to 999999999.
- Only the symbols `X`, `S`, `9`, and `V` are permitted. Other symbols, for example `Z`, `P`, `-`, or `.`, are compile errors.
- A text picture must not have a numeric usage, for example `X(5) COMP-3`.
- Usage clause (optional, after the picture, with an optional `USAGE` or `USAGE IS`):

| Clause | Usage |
|---|---|
| `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | `COMP3` |
| `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY` | `COMP` |
| `DISPLAY` | `DISPLAY` |

Without a clause, the usage comes from `@BookDefaults`, else `DISPLAY`.
Other usages, for example `COMP-1`, `COMP-2`, or `COMP-5`, are compile errors.

```java
@BookField(order = 10, pic = "S9(7)V99 COMP-3")           // BigDecimal, 5 bytes, scale 2
private BigDecimal balance;

@BookField(order = 20, pic = "9(4) COMP")                 // int, 2 bytes, big-endian
private int lineCount;

@BookField(order = 30, pic = "9(6)", pattern = "HHmmss")  // LocalTime needs a pattern
private LocalTime shippedAt;
```

## Byte size

The byte size comes from the usage and the length:

- `DISPLAY`: `length` bytes.
- `COMP3`: `length / 2 + 1` bytes.
- `COMP`: 2 bytes for 1 to 4 digits, 4 bytes for 5 to 9 digits, 8 bytes for 10 to 18 digits. More than 18 digits is a compile error.

## Type mapping

Only these Java types are permitted without a converter:

| Java type | COBOL definition | Limits |
|---|---|---|
| `String` | `PIC X(n)` | `DISPLAY` only. |
| `int`, `Integer` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP` | 9 digits maximum. Scale 0. |
| `long`, `Long` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP` | 18 digits maximum. Scale 0. |
| `BigDecimal` | Any numeric definition, with `scale` | The scale must not be more than the length. |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY`, with a `pattern` | A numeric `pic` must be unsigned and without `V`. |
| Any other type | Through a `converter` | See [Annotations reference](Annotations-reference#converters). |

Other types, for example `boolean`, `short`, `double`, enums, and `byte[]`, need a `converter`.
Without a converter, the compiler reports `type <T> is not supported without a converter`.

An incorrect combination is a compile error. For example:

| Combination | Message |
|---|---|
| `int` with `V99` | `scale must be 0 for int` |
| `int` with `9(10)` | `length 10 is more than 9 digits for int` |
| `String` with `COMP3` | `COMP3 is not permitted for String` |
| `String` with `9(5)` | `a numeric pic is not permitted for String` |
| `int` with `X(5)` | `a text pic is not permitted for int` |
| `LocalDate` with `S9(8)` | `a signed or decimal pic is not permitted for LocalDate` |
| `9(19) COMP` | `COMP length must not be more than 18` |

## Signs

### DISPLAY (zoned decimal)

The codec puts the sign in the last byte. The format depends on the charset:

- **EBCDIC** (for example `IBM037`): the last byte has the zone `C` for a positive value and `D` for a negative value. The other bytes, and all bytes of an unsigned field, have the zone `F`.
- **Other charsets**: the last character is an overpunch character: `{ABCDEFGHI` for a positive value (digits 0 to 9), and `}JKLMNOPQR` for a negative value.

On read, the last EBCDIC byte can have the zone `C`, `F`, `A`, or `E` (positive), or `D` or `B` (negative). The other bytes must have the zone `F`.
A space or another character inside the digits causes a `MappingException`.
An unsigned field accepts a positive sign. A negative sign in an unsigned field causes a `MappingException`.

### COMP-3 (packed decimal)

- On write, the last nibble is `C` (positive) or `D` (negative). An unsigned field gets `F`.
- On read, `A`, `C`, `E`, and `F` are positive. `B` and `D` are negative. Other sign nibbles cause a `MappingException`.
- For an even number of digits, the first nibble must be 0.

### COMP (binary)

- The bytes are big-endian. A signed field uses two's complement. An unsigned field reads all bits as the value.
- On write, the limit is the number of digits, not the binary range. For example, `9(4) COMP` permits 9999, not 65535.
- On read, the codec accepts each value that the bytes can hold, also a value with more digits.
- A read value that does not fit the Java type causes a `MappingException`, for example `value 3000000000 does not fit int`.

### Examples of bytes

| Value | Definition | Bytes |
|---|---|---|
| +123 | `S9(3)`, EBCDIC | `F1 F2 C3` |
| -123 | `S9(3)`, EBCDIC | `F1 F2 D3` |
| +123 | `S9(3)`, ASCII | `12C` |
| -120 | `S9(3)`, ASCII | `12}` |
| +12345 | `S9(5) COMP-3` | `12 34 5C` |
| 123 | `9(3) COMP-3` | `12 3F` |
| -2 | `S9(4) COMP` | `FF FE` |

## Decimal places

- The codec does not round. A value with more decimal places than the `scale` causes a `MappingException`.
- A value with fewer decimal places is correct.
- On read, a `BigDecimal` always has the scale of the field. For example, `9(2)V99` gives `12.30`, not `12.3`.

Round the value before you set it, for example with `value.setScale(2, RoundingMode.HALF_EVEN)`.

## Dates and times

Date patterns use `DateTimeFormatter` (for example `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `HHmmss`).
The default pattern is `yyyyMMdd`. It fits only `LocalDate`. Set a `pattern` on each `LocalTime` and `LocalDateTime` field.
Otherwise, the compiler reports `pattern 'yyyyMMdd' does not fit LocalDateTime`.

- The pattern must give text of a fixed length. For example, `Hmmss` is a compile error.
- The pattern must fit the Java type. For example, `HHmmss` on a `LocalDate` is a compile error.
- An explicit `length` must agree with the pattern. Otherwise, the compiler reports `length N does not agree with pattern 'P' (M characters)`.
- The formatters are strict and use `Locale.ROOT`.
- A two-digit year (`yy`) reads as a year from 2000 to 2099.

On write, the codec reads the text again and compares it with the value.
If the pattern cannot keep the value, the codec throws `MappingException`.
Examples: the year 1975 with `yyMMdd`, or a time with seconds or nanoseconds with `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.

Packed dates (for example `S9(7) COMP-3` in the format `0CYYDDD`) have no built-in support. Write a `FieldConverter` for them.

## Conversion rules

### Read

- Text: the codec changes low-values to spaces and removes the spaces on the right. An empty field becomes `""`, never `null`.
- A byte that the charset cannot decode throws `MappingException`. The codec never puts a replacement character in a value.
- `DISPLAY` or `COMP3` number with only spaces or low-values: `null` for `Integer`, `Long`, and `BigDecimal`, and `0` for `int` and `long`.
- `COMP` number: never `null`. Low-values read as `0`.
- Date or time with only spaces or low-values: `null`.
- `LocalDate` or `LocalDateTime` with only zeros: `null`. `LocalTime` with only zeros: `LocalTime.MIDNIGHT`.

### Write

- Text: the codec adds spaces on the right. A `null` text writes spaces.
- Number: a `null` number writes zero, with a positive sign. For example, `S9(3)` writes `F0 F0 C0` in EBCDIC.
- Date or time: a `null` value writes the digit `0` in all positions. Thus, a `null` `LocalTime` reads back as midnight.
- Converter: the converter gets the `null` value.
- Too large a value (text, digits, or a negative value in an unsigned field) throws `MappingException`. The codec never truncates.
