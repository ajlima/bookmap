# PIC syntax and type mapping

## PIC syntax

The processor parses `pic` at compile time. An incorrect or unsupported `pic` is a compile error.

- The prefix `PIC` or `PICTURE` and a final `.` are optional. Letters are not case-sensitive.
- Text: `X(n)` or a sequence of `X`.
- Number: optional `S`, integer digits `9(n)` or `999`, and optional `V` followed by decimal digits.
  Examples: `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`.
- Usage clause (optional, after the picture, with an optional `USAGE` or `USAGE IS`):

| Clause | Usage |
|---|---|
| `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | `COMP3` |
| `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY` | `COMP` |
| `DISPLAY` | `DISPLAY` |

Without a clause, the usage comes from `@BookDefaults`, else `DISPLAY`.

## Byte size

The byte size comes from the usage and the length:

- `DISPLAY`: `length` bytes.
- `COMP3`: `length / 2 + 1` bytes.
- `COMP`: 2 bytes for 1 to 4 digits, 4 bytes for 5 to 9 digits, 8 bytes for 10 to 18 digits.

## Type mapping

| Java type | COBOL definition |
|---|---|
| `String` | `PIC X(n)` |
| `int`, `Integer`, `long`, `Long` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP`; scale 0 |
| `BigDecimal` | Any numeric definition, with `scale` |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY`, with a `pattern` |
| Any type | Through a `converter` |

An incorrect combination is a compile error (for example `String` with `COMP3`, or `int` with `scale > 0`).

## Dates and times

Date patterns use `DateTimeFormatter` (for example `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `HHmmss`).
The `length` must agree with the pattern. The formatters are strict and use `Locale.ROOT`.
A two-digit year (`yy`) reads as a year from 2000 to 2099.

On write, the codec reads the text again and compares it with the value.
If the pattern cannot keep the value, the codec throws `MappingException`.
Examples: the year 1975 with `yyMMdd`, or a time with seconds or nanoseconds with `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.

Packed dates (for example `S9(7) COMP-3` in the format `0CYYDDD`) have no built-in support. Write a `FieldConverter` for them.
See [Annotations reference](Annotations-reference#converters).

## Conversion rules

- Text, write: the codec adds spaces on the right.
- Text, read: the codec changes low-values to spaces and removes the spaces on the right. An empty field becomes `""`, never `null`.
- Read: a byte that the charset cannot decode throws `MappingException`. The codec never puts a replacement character in a value.
- Number or date, read: a field with only spaces or low-values becomes `null` for a wrapper type and `0` for a primitive type.
- Signed `DISPLAY` numbers use a trailing overpunch sign.
- Too large a value (text, digits, or a negative value in an unsigned field) throws `MappingException`. The codec never truncates.
