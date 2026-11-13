## PIC syntax

The processor parses `pic` at compile time. An incorrect or unsupported `pic` is a compile error.

- The prefix `PIC` or `PICTURE` and a final `.` are optional. Letters are not case-sensitive.
- Write the picture as one word, without spaces. For example, `S9(7) V99` is a compile error. A space is necessary only between the picture and a clause.
- `PIC IS` is not supported. Write `PIC X(3)`, not `PIC IS X(3)`.
- Text: `X(n)` or a sequence of `X`.
- Number: optional `S`, integer digits `9(n)` or `999`, and optional `V` followed by decimal digits.
  Examples: `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`.
- A repeat count `(n)` is from 1 to 999999999.
- The symbols `X`, `S`, `9`, and `V` are permitted. A numeric edited picture can also have `Z * . , B 0 / + - CR DB $`. See [Edited pictures](#edited-pictures).
- Other symbols, for example `P`, `A`, or `N`, are compile errors.
- A text picture must not have a numeric usage, for example `X(5) COMP-3`.
- Usage clause (optional, after the picture, with an optional `USAGE` or `USAGE IS`):

| Clause | Usage |
|---|---|
| `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | `COMP3` |
| `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY` | `COMP` |
| `DISPLAY` | `DISPLAY` |

Without a clause, the usage comes from `@BookDefaults`, else `DISPLAY`.
Other usages, for example `COMP-1`, `COMP-2`, or `COMP-5`, are compile errors.

- Sign clause (optional, after the picture): `[SIGN [IS]] LEADING|TRAILING [SEPARATE [CHARACTER]]`. See [Sign formats](#sign-formats).
- `BLANK [WHEN] ZERO`, `ZEROS`, or `ZEROES` (optional, after the picture): a zero writes as spaces. See [Edited pictures](#edited-pictures).
- The clauses can come in all orders. Each clause can occur one time only.
- The sign clause needs a signed picture (`S`) and the usage `DISPLAY`. Otherwise, it is a compile error.

```java
@BookField(order = 10, pic = "S9(7)V99 COMP-3")           // BigDecimal, 5 bytes, scale 2
private BigDecimal balance;

@BookField(order = 15, pic = "S9(5)V99 SIGN LEADING SEPARATE")  // BigDecimal, 8 bytes
private BigDecimal tax;

@BookField(order = 20, pic = "9(4) COMP")                 // int, 2 bytes, big-endian
private int lineCount;

@BookField(order = 30, pic = "9(6)", pattern = "HHmmss")  // LocalTime needs a pattern
private LocalTime shippedAt;
```

## Byte size

The byte size comes from the usage and the length:

- `DISPLAY`: `length` bytes. A separate sign adds one byte.
- `COMP3`: `length / 2 + 1` bytes.
- `COMP`: 2 bytes for 1 to 4 digits, 4 bytes for 5 to 9 digits, 8 bytes for 10 to 18 digits. More than 18 digits is a compile error.
- An edited picture: one byte for each character position. `CR` and `DB` give two bytes. `V` gives no byte.

## Type mapping

Only these Java types are permitted without a converter:

| Java type | COBOL definition | Limits |
|---|---|---|
| `String` | `PIC X(n)` | `DISPLAY` only. |
| `int`, `Integer` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP`. Also an [edited picture](#edited-pictures). | 9 digits maximum. Scale 0. |
| `long`, `Long` | `PIC 9` or `PIC S9`; `DISPLAY`, `COMP3`, or `COMP`. Also an [edited picture](#edited-pictures). | 18 digits maximum. Scale 0. |
| `BigDecimal` | Any numeric definition, with `scale`. Also an [edited picture](#edited-pictures). | The scale must not be more than the length. |
| `LocalDate`, `LocalTime`, `LocalDateTime` | `PIC 9(n)` or `PIC X(n)` `DISPLAY`, with a `pattern` | A numeric `pic` must be unsigned and without `V`. |
| An enum with `@CobolValue` constants | `PIC X(n)`, or any numeric definition without an edited picture | Text: the rules of `String`. Number: the rules of `BigDecimal`. See [Enum fields](Annotations-reference#enum-fields). |
| Any other type | Through a `converter` | See [Annotations reference](Annotations-reference#converters). |

Other types, for example `boolean`, `short`, `double`, and `byte[]`, need a `converter`.
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

By default, the codec puts the sign in the last byte (a trailing overpunch). The format depends on the charset:

- **EBCDIC** (for example `IBM037`): the last byte has the zone `C` for a positive value and `D` for a negative value. The other bytes, and all bytes of an unsigned field, have the zone `F`.
- **Other charsets**: the last character is an overpunch character: `{ABCDEFGHI` for a positive value (digits 0 to 9), and `}JKLMNOPQR` for a negative value.

On read, the last EBCDIC byte can have the zone `C`, `F`, `A`, or `E` (positive), or `D` or `B` (negative). The other bytes must have the zone `F`.
A space or another character inside the digits causes a `MappingException`.
An unsigned field accepts a positive sign. A negative sign in an unsigned field causes a `MappingException`.

### Sign formats

A signed `DISPLAY` number can use one of four sign formats. Set the format with the `SIGN` clause in `pic`, or with the `sign` attribute.

| `sign` | `pic` clause | Sign | Bytes |
|---|---|---|---|
| `TRAILING` (default) | none, or `SIGN TRAILING` | Overpunch in the last digit. | `length` |
| `LEADING` | `SIGN LEADING` | Overpunch in the first digit. | `length` |
| `TRAILING_SEPARATE` | `SIGN TRAILING SEPARATE` | `+` or `-` after the digits. | `length + 1` |
| `LEADING_SEPARATE` | `SIGN LEADING SEPARATE` | `+` or `-` before the digits. | `length + 1` |

```java
@BookField(order = 20, length = 5, scale = 2, signed = true, sign = Sign.TRAILING_SEPARATE)
private BigDecimal discount;
```

- An overpunch sign in the first digit follows the rules of the last digit above.
- A separate sign is the character `+` or `-` of the charset. In EBCDIC, it is `4E` or `60`.
- With a separate sign, all digit bytes must be plain digits: zone `F` in EBCDIC, `0` to `9` in other charsets.
- On read, a separate sign other than `+` or `-` causes a `MappingException`, for example `byte 0x40 at offset 0 of the field is not a valid sign. The sign must be '+' or '-'`. A space is not a positive sign.
- A blank field (all bytes, also the sign byte) reads as `null`.
- On write, a positive value, zero, and `null` get the positive sign.

Set `sign` only on a signed `DISPLAY` number. Otherwise, the compiler reports `sign needs a signed field` or `sign is permitted only with usage DISPLAY`.
A field with a converter must use the default sign.

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
| -123 | `S9(3) SIGN LEADING`, EBCDIC | `D1 F2 F3` |
| -123 | `S9(3) SIGN LEADING`, ASCII | `J23` |
| -123 | `S9(3) SIGN TRAILING SEPARATE`, EBCDIC | `F1 F2 F3 60` |
| +123 | `S9(3) SIGN LEADING SEPARATE`, ASCII | `+123` |
| +12345 | `S9(5) COMP-3` | `12 34 5C` |
| 123 | `9(3) COMP-3` | `12 3F` |
| -2 | `S9(4) COMP` | `FF FE` |

## Edited pictures

A numeric edited picture gives a printable number, for example `Z,ZZ9.99CR`. The field is a number in Java: `int`, `Integer`, `long`, `Long`, or `BigDecimal`.
The codec writes the edited text and reads it back as a number.

```java
@BookField(order = 10, pic = "ZZ9")                    // int, 3 bytes
private int lineCount;

@BookField(order = 20, pic = "$Z,ZZ9.99CR")            // BigDecimal, 11 bytes
private BigDecimal total;

@BookField(order = 30, pic = "9(3)V99 BLANK WHEN ZERO") // BigDecimal, 5 bytes
private BigDecimal discount;
```

### Edit symbols

| Symbol | Write |
|---|---|
| `9` | A digit. |
| `Z` | A digit. A leading zero becomes a space. |
| `*` | A digit. A leading zero becomes `*` (check protection). |
| `.` | The decimal point. It sets the scale. |
| `V` | An implied decimal point. It writes no character. |
| `,` `B` `0` `/` | The character `,`, a space, `0`, or `/`. |
| `+` | `+` for a positive value or zero, `-` for a negative value. |
| `-` | A space for a positive value or zero, `-` for a negative value. |
| `CR` `DB` | Two spaces for a positive value or zero, `CR` or `DB` for a negative value. |
| `$` | The character `$`. |

A floating string is two or more `+`, `-`, or `$`, for example `$$$,$$9.99` or `----9`. The first symbol holds only the sign or the currency sign. Each other symbol is a digit position.

### Rules of the picture

- A fixed `+` or `-` is the first or the last symbol. `CR` or `DB` is the last symbol.
- A picture has one sign at most, and one decimal point at most.
- A fixed `$` is the first symbol, or the second after a sign.
- `Z`, `*`, and a floating string are mutually exclusive.
- A `9` must not come before `Z`, `*`, or a floating symbol.
- A `Z`, `*`, or floating symbol after the decimal point needs that symbol in all digit positions.
- `S` is a compile error. Use `+`, `-`, `CR`, or `DB`.
- An edited picture must have the usage `DISPLAY`. A `SIGN` clause is a compile error.
- `BLANK WHEN ZERO` is permitted on an edited picture without `*`, and on an unsigned `DISPLAY` number. Then the number is also an edited picture.

The attributes come from the picture:

| Picture | Digits (`length`) | Scale | Signed | Bytes |
|---|---|---|---|---|
| `ZZ9` | 3 | 0 | no | 3 |
| `Z,ZZ9.99CR` | 6 | 2 | yes | 10 |
| `$$$,$$9.99` | 7 | 2 | no | 10 |
| `-9(5)` | 5 | 0 | yes | 6 |

`@BookDefaults(usage = ...)` does not apply to an edited field.

### Write an edited number

- The write does not round. A value with more decimal places than the scale, or with too many digits, causes a `MappingException`.
- A negative value in a picture without a sign causes a `MappingException`.
- A `null` value writes as zero.
- The zero suppression stops at the first nonzero digit, at a `9`, or at the decimal point. An insertion character in the suppressed area becomes the fill character.
- A floating string writes its character immediately left of the first digit that is not suppressed.
- A zero writes as spaces if all digit positions are `Z` or floating symbols, and with `BLANK WHEN ZERO`. With `*` in all digit positions, a zero writes `*` in all positions, except the decimal point.

| Picture | Value | Text (`_` is a space) |
|---|---|---|
| `ZZZZ.99` | 0 | `____.00` |
| `****.**` | 0 | `****.**` |
| `Z,ZZZ.ZZ+` | 123.45 | `__123.45+` |
| `*,***.**+` | -123.45 | `**123.45-` |
| `$Z,ZZZ,ZZZ.ZZCR` | 12345.67 | `$___12,345.67__` |
| `$,$$$,999.99` | 1234.56 | `___$1,234.56` |
| `+,+++,999.99` | -123456.78 | `_-123,456.78` |
| `-9(5)` | 42 | `_00042` |

### Read an edited number

- A field with only spaces or LOW-VALUES reads as `null`. An `int` or a `long` gets 0.
- A `9` position must hold a digit.
- A `Z`, `*`, or floating position holds a digit, a space, `*`, or the floating character.
- An insertion position holds its character, a space, or `*`.
- A sign position holds a valid sign. For example, a fixed `-` holds a space or `-`.
- The read does not check where the zero suppression stops. For example, `ZZZ9` reads `0 12` as 12.
- Another byte causes a `MappingException`, for example `byte 0x41 at offset 1 of the field is not valid in the edited picture ZZ9.99`.

Each value that the write accepts reads back with the scale of the picture. A zero that writes as spaces reads back as `null`.

### Edited pictures that are not supported

- Alphanumeric edited pictures, for example `XXBXX`. Use `X(n)`.
- A currency sign other than `$`, and `DECIMAL-POINT IS COMMA`.
- An edited picture on a date or time field, an enum field, a field with a converter, a discriminator, a count field, or an `emptyKey` field.

## Decimal places

- The codec does not round. A value with more decimal places than the `scale` causes a `MappingException`.
- A value with fewer decimal places is correct.
- On read, a `BigDecimal` always has the scale of the field. For example, `9(2)V99` gives `12.30`, not `12.3`.

Round the value before you set it, for example with `value.setScale(2, RoundingMode.HALF_EVEN)`.

## Dates and times

Date patterns use `DateTimeFormatter` (for example `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `HHmmss`).
The default pattern is `yyyyMMdd`. It fits only `LocalDate`. Set a `pattern` on each `LocalTime` and `LocalDateTime` field.
Otherwise, the compiler reports `pattern 'yyyyMMdd' does not fit LocalDateTime`.

- The pattern must give text of a fixed length. For example, `Hmmss` and `HHmmssn` are compile errors. Use `SSS` for a fixed number of fraction digits.
- The pattern must fit the Java type. For example, `HHmmss` on a `LocalDate` is a compile error.
- An explicit `length` must agree with the pattern. Otherwise, the compiler reports `length N does not agree with pattern 'P' (M characters)`.
- The formatters are strict and use `Locale.ROOT`.
- A two-digit year (`yy` or `uu`) reads as a year from 2000 to 2099.

On write, the codec reads the text again and compares it with the value.
If the pattern cannot keep the value, the codec throws `MappingException`.
Examples: the year 1975 with `yyMMdd`, or a time with seconds or nanoseconds with `HHmm`.
Remove the extra precision before you set the value, for example with `truncatedTo(ChronoUnit.MINUTES)`.

### Packed dates

A date or time field can have the usage `COMP3`. Then the digits of the packed number are the text of the pattern.

```java
@BookField(order = 10, pic = "S9(8) COMP-3", pattern = "yyyyMMdd")
private LocalDate orderDate;

@BookField(order = 20, pic = "S9(7) COMP-3", pattern = "'0'HHmmss")
private LocalTime shipTime;

@BookField(order = 30, usage = Usage.COMP3, pattern = "yyyyMMddHHmmss")
private LocalDateTime lastUpdate;
```

- The pattern must give digits only. Otherwise, the compiler reports `pattern 'P' gives characters that are not digits, but a COMP3 field holds only digits`.
- The number of digits must be equal to the length of the pattern text. Use a quoted literal for a constant digit.
- A packed date can be signed: `S` in the `pic`, or `signed = true`. A `V` in the `pic` is a compile error.
- Without `pic`, the length is the length of the pattern text. `lastUpdate` above has 14 digits, thus 8 bytes.
- `@BookDefaults(usage = Usage.COMP3)` does not apply to a date or time field. Set the usage on the field.
- `COMP` is not permitted for a date or time field.

| Picture | Pattern | Java type | Example value | Bytes |
|---|---|---|---|---|
| `S9(8) COMP-3` | `yyyyMMdd` | `LocalDate` | 2026-11-13 | `02 02 61 11 3C` |
| `9(7) COMP-3` | `yyyyDDD` | `LocalDate` | 2026-11-13 | `20 26 31 7F` |
| `S9(7) COMP-3` | `'01'yyDDD` (CICS `EIBDATE`, `0CYYDDD` for the years 2000 to 2099) | `LocalDate` | 2026-11-13 | `01 26 31 7C` |
| `S9(7) COMP-3` | `'0'HHmmss` (CICS `EIBTIME`) | `LocalTime` | 13:05:09 | `01 30 50 9C` |
| `S9(14) COMP-3` | `yyyyMMddHHmmss` | `LocalDateTime` | 2026-11-13T23:59:58 | `02 02 61 11 32 35 95 8C` |

On write, the sign nibble is `C` for a signed field and `F` for an unsigned field. A `null` value writes packed zero.
On read, the rules are the same as for a `DISPLAY` date:

- A field with only LOW-VALUES or only spaces is `null`.
- Packed zero is `null`, or `LocalTime.MIDNIGHT` for a time.
- A negative sign (`B` or `D`) causes a `MappingException`, also with zero digits.
- A nibble that is not valid, or digits that are not a valid date, cause a `MappingException`.

A century digit that changes in one field, for example `CYYMMDD` with `0` for 19xx and `1` for 20xx, has no `DateTimeFormatter` letter. Write a `FieldConverter` for it.

## Conversion rules

### Read

- Text: the codec changes low-values to spaces and removes the spaces on the right. An empty field becomes `""`, never `null`.
- A byte that the charset cannot decode throws `MappingException`. The codec never puts a replacement character in a value.
- `DISPLAY` or `COMP3` number with only spaces or low-values: `null` for `Integer`, `Long`, and `BigDecimal`, and `0` for `int` and `long`.
- `COMP` number: never `null`. Low-values read as `0`.
- Date or time with only spaces or low-values: `null`.
- `LocalDate` or `LocalDateTime` with only zeros: `null`. `LocalTime` with only zeros: `LocalTime.MIDNIGHT`.
  "Only zeros" means that each character is the digit `0`. With a pattern that has separators, for example `yyyy-MM-dd`, the text `0000-00-00` is not "only zeros": it throws `MappingException`.
- Enum: the constant that contains the value. A blank field gives `null`, unless a constant has the blank value. An unknown value throws `MappingException`, or gives the `@CobolUnknown` constant.

### Write

- Text: the codec adds spaces on the right. A `null` text writes spaces.
- Number: a `null` number writes zero, with a positive sign. For example, `S9(3)` writes `F0 F0 C0` in EBCDIC.
- Date or time: a `null` value writes the digit `0` in all positions, also in the positions of the separators. For example, `yyyy-MM-dd` writes `0000000000`. Thus, a `null` `LocalTime` reads back as midnight.
- Converter: the converter gets the `null` value.
- Enum: the canonical value of the constant. A `null` enum writes spaces (text) or zero (number).
- Too large a value (text, digits, or a negative value in an unsigned field) throws `MappingException`. The codec never truncates.
