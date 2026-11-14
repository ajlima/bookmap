# BookMap, edited pictures: design

Date: 2026-11-13
Status: implemented in v0.23.0.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (sections 2.2 and 5.3), `docs/design/2026-11-11-bookmap-copybook-design.md`, GitHub issue #48.

## 1. Intent

Today an edited picture is a compile error in the processor and an error in the copybook generator. Examples: `ZZ9`, `Z,ZZ9.99CR`, `$$$,$$9.99`, `***9.99`, `-9(5)`.
A COBOL program uses an edited picture for a printable number, for example an amount on a report or a screen. When such an item is in a COMMAREA, the Java side must read it and write it.

This change adds numeric edited pictures to the `pic` of `@BookField` and to the copybook generator. A field with an edited picture is a number in Java. The codec writes it as edited text, and reads the edited text back as a number.

## 2. Scope

### 2.1 Symbols in scope

| Symbol | Name | Effect on write |
|---|---|---|
| `9` | Digit | A digit. |
| `Z` | Zero suppression | A leading zero becomes a space. |
| `*` | Check protection | A leading zero becomes `*`. |
| `.` | Actual decimal point | The character `.`. It sets the scale. |
| `V` | Implied decimal point | No character. It sets the scale. |
| `,` | Comma insertion | The character `,`. |
| `B` | Space insertion | A space. |
| `0` | Zero insertion | The character `0`. |
| `/` | Slash insertion | The character `/`. |
| `+` | Fixed or floating sign | `+` for a positive value, `-` for a negative value. |
| `-` | Fixed or floating sign | A space for a positive value, `-` for a negative value. |
| `CR` | Credit | Two spaces for a positive value, `CR` for a negative value. |
| `DB` | Debit | Two spaces for a positive value, `DB` for a negative value. |
| `$` | Currency, fixed or floating | The character `$`. |

A repeat count is permitted on each one-character symbol, for example `Z(5)`, `$(4)`, or `B(2)`.
The `BLANK WHEN ZERO` clause is in scope (section 5.4).

### 2.2 Out of scope

These items stay compile errors:

- Alphanumeric edited pictures, for example `XXBXX` or `X(3)/X(2)`.
- The symbols `P`, `A`, `N`, `E` (floating-point display), and `S` in an edited picture.
- A currency sign other than `$`. The `CURRENCY SIGN` clause is in `SPECIAL-NAMES`, not in a copybook.
- `DECIMAL-POINT IS COMMA`. See decision D8.
- A usage other than `DISPLAY` on an edited picture.

## 3. Picture grammar

The engine expands the repeat counts, then reads the symbols from left to right. These rules apply. A broken rule is an error with the message in section 9.

1. The picture has at least one digit position: `9`, `Z`, `*`, or a floating symbol after the first one.
2. A fixed `+` or `-` is the first or the last symbol. `CR` or `DB` is the last symbol.
3. The picture has one sign at most: a fixed `+` or `-`, `CR`, `DB`, or a floating `+` or `-` string.
4. A fixed `$` is the first symbol, or the second symbol after a leading fixed sign. The picture has one `$` at most, unless the `$` symbols make a floating string.
5. A floating string is two or more `+`, two or more `-`, or two or more `$`. Insertion symbols (`,` `B` `0` `/`) between them are part of the string.
6. `Z`, `*`, and a floating string are mutually exclusive. The picture has one floating string at most.
7. A `9` must not come before a `Z`, a `*`, or a floating symbol.
8. A `Z`, a `*`, or a floating symbol after the decimal point is permitted only if all digit positions use that symbol.
9. The picture has one decimal point at most: `.` or `V`, not both.
10. A picture with only `9` and `V` is not an edited picture. It keeps the present rules. The exception is `BLANK WHEN ZERO` (section 5.4).

These rules are a subset of the rules of IBM Enterprise COBOL. Each picture that BookMap accepts is also a valid COBOL picture.

## 4. Field attributes

The `pic` gives all attributes. `length`, `scale`, `signed`, `usage`, and `sign` must not be set with `pic` (the present rule).

| Attribute | Value for an edited picture |
|---|---|
| `length` | The number of digit positions. A floating string gives one position less than its symbols. |
| `scale` | The number of digit positions after `.` or `V`. |
| `signed` | `true` if the picture has a sign symbol. |
| `usage` | `DISPLAY`. |
| Byte length | The number of character positions. `CR` and `DB` give two. `V` gives zero. |

Examples:

| Picture | Digits | Scale | Signed | Bytes |
|---|---|---|---|---|
| `ZZ9` | 3 | 0 | no | 3 |
| `Z,ZZ9.99CR` | 6 | 2 | yes | 10 |
| `$$$,$$9.99` | 7 | 2 | no | 10 |
| `-9(5)` | 5 | 0 | yes | 6 |
| `***9.99` | 6 | 2 | no | 7 |
| `99/99/99` | 6 | 0 | no | 8 |

`@BookDefaults(usage = ...)` and `@BookDefaults(signed = ...)` do not apply to an edited field. The picture gives the usage and the sign.

This is the first field type where the number of digits and the number of bytes are not the same. Section 8 lists each place that uses the one or the other.

## 5. Write (encode)

### 5.1 Value check

The write uses the present check of a number (`Unscaled.of`), with the digits, the scale, and the sign of the picture:

- A `null` value writes as zero.
- A value with more decimal places than the scale causes a `MappingException`. The codec does not round and does not truncate. COBOL truncates on a `MOVE`, but BookMap keeps its present rule.
- A value with more integer digits than the picture causes a `MappingException`.
- A negative value in a picture without a sign causes a `MappingException`.

### 5.2 Editing rules

The rules follow the IBM Enterprise COBOL Language Reference, "Zero suppression and replacement editing" and "Floating insertion editing".

1. The digits of the value fill the digit positions from the right, with leading zeros.
2. Suppression covers the leading positions of `Z`, `*`, or the floating string. It stops at the first position that has a nonzero digit, that is not a suppression symbol, or that is the decimal point.
3. In the suppressed area, a digit position and an insertion position get the fill character: a space for `Z` and a floating string, `*` for `*`.
4. A floating string puts one floating character immediately left of the first position that is not suppressed. Positions to its left get spaces.
5. After the suppressed area, an insertion symbol gives its character, and `.` gives `.`.
6. A sign symbol gives its character from the table in section 2.1. Zero is positive.

### 5.3 Zero with all digit positions suppressed

If all digit positions are `Z`, `*`, or a floating symbol, and the value is zero:

- With `Z` or a floating string, all positions are spaces, also the sign and the currency positions.
- With `*`, all positions are `*`, except the decimal point `.`, which stays.

### 5.4 BLANK WHEN ZERO

The `pic` string accepts the clause `BLANK WHEN ZERO`, also `BLANK ZERO`, `ZEROS`, and `ZEROES`. A zero value writes spaces in all positions.

- The clause is permitted on an edited picture without `*`, and on an unsigned `DISPLAY` number (only `9` and `V`). This follows COBOL: the clause is not permitted with `S` or with `*`.
- With the clause, a `9(5)` or a `9(3)V99` is an edited picture: five bytes, no sign, and the rules of this design.
- A sign clause and a usage clause other than `DISPLAY` are errors with `BLANK WHEN ZERO`.

### 5.5 Examples

From the IBM tables and the rules above. Results use `.` for a space where the space matters.

| Picture | Value | Result |
|---|---|---|
| `ZZZZ.99` | 0 | `....00` (4 spaces, then `.00`) |
| `ZZZZ.ZZ` | 0 | 7 spaces |
| `****.**` | 0 | `****.**` |
| `****.99` | 0 | `****.00` |
| `ZZ99.99` | 0 | `..00.00` |
| `Z,ZZZ.ZZ+` | 123.45 | `..123.45+` |
| `*,***.**+` | -123.45 | `**123.45-` |
| `$Z,ZZZ,ZZZ.ZZCR` | 12345.67 | `$...12,345.67..` |
| `$B*,***,***.**BBDB` | -12345.67 | `$.***12,345.67..DB` |
| `$$$$.99` | 0.12 | `...$.12` |
| `$$$9.99` | 0.12 | `..$0.12` |
| `$,$$$,999.99` | -1234.56 (unsigned) | `MappingException` |
| `$,$$$,999.99` | 1234.56 | `...$1,234.56` |
| `+,+++,999.99` | -123456.78 | `.-123,456.78` |
| `-9(5)` | 42 | `.00042` |
| `ZZ9 BLANK WHEN ZERO` | 0 | 3 spaces |

The implementation tests use these rows as the test oracle.

## 6. Read (decode)

### 6.1 Why the read is necessary

`CodecGenerator` makes four methods for each codec: `encodeRequest`, `decodeResponse`, `decodeRequest`, and `encodeResponse`.
The client writes an `INPUT` field and reads an `OUTPUT` field. The simulator and a server do the opposite.
Thus, each field needs a write and a read. A field with "write only" would make one of the four methods fail. See decision D1.

### 6.2 Read rules

The read goes through the bytes position by position. The charset is the charset of the context, and must have one byte for each character (the present rule).

1. A field with only spaces or `0x00` bytes reads as `null`, the present rule for a blank number. A primitive `int` or `long` gets 0 through the present helper.
2. A `9` position must hold a digit.
3. A `Z`, `*`, or floating digit position holds a digit, a space, `*`, or the floating character. A space, `*`, or the floating character is a zero.
4. An insertion position and the `.` position hold their character, a space, or `*`.
5. A fixed `+` holds `+` or `-`. A fixed `-` holds a space or `-`. `CR` holds `CR` or two spaces. `DB` holds `DB` or two spaces. A floating `+` or `-` string can hold the sign in one of its positions.
6. A fixed `$` holds `$`, a space, or `*`.
7. Each other byte causes a `MappingException`, for example `byte 0x41 at offset 2 of the field is not valid in the edited picture ZZ9.99`.

The read does not check that the suppression stops at the correct position. For example, `ZZZ9` accepts `0 12` as 12. This is lenient on purpose: other producers sometimes write leading zeros.

The result is a `BigDecimal` with the scale of the picture, like the present read.

### 6.3 Round-trip guarantee

For each value that the write accepts, the read of the written bytes gives the same number, with the scale of the picture.
There is one exception: a zero that writes as all spaces (section 5.3 with `Z` or a floating string, and `BLANK WHEN ZERO`) reads as `null`. See decision D4.

## 7. Java types

| Java type | Edited picture | Message |
|---|---|---|
| `int`, `Integer` | Permitted. 9 digits maximum, scale 0. | The present messages. |
| `long`, `Long` | Permitted. 18 digits maximum, scale 0. | The present messages. |
| `BigDecimal` | Permitted. | |
| `String` | Error. | `a numeric pic is not permitted for String` (present). |
| `LocalDate`, `LocalTime`, `LocalDateTime` | Error. | `an edited pic is not permitted for LocalDate. Use X(n) with a pattern` |
| An enum with `@CobolValue` | Error. | `an edited pic is not permitted for an enum` |
| A field with a converter | Error. | `an edited pic is not permitted with a converter` |
| A discriminator of `@BookSubTypes` | Error. | `discriminator must not have an edited pic` |
| A count field of a collection | Error. | `dependsOn field '<name>' must not have an edited pic` |
| The `emptyKey` field of a collection | Error. | `emptyKey field '<name>' must not have an edited pic` |

## 8. Implementation

### 8.1 One engine in the core

A new public class `pt.dazz.bookmap.codec.EditedPicture` holds the parsed picture and the editing rules.

- `EditedPicture.parse(String picture, boolean blankWhenZero)` checks the grammar and gives the instance. It throws `IllegalArgumentException` with the messages of section 9.
- `isEdited(String picture)` tells if a picture has an edit symbol.
- `digits()`, `scale()`, `signed()`, and `byteLength()` give the attributes of section 4.
- `encode(...)` and `decode(...)` give the text and the number. The point character and the comma character are fields of the engine. Thus, a later `DECIMAL-POINT IS COMMA` needs no new engine.

The processor and the copybook generator both use `EditedPicture.parse`. Thus, the grammar has one source. The processor and the copybook generator already depend on `bookmap-core`.

`BookWriter.writeEdited(String field, BigDecimal value, EditedPicture picture)` and `BookReader.readEdited(String field, EditedPicture picture)` are new methods.

### 8.2 Processor

- `PicParser`: an edited picture or a `BLANK WHEN ZERO` clause gives `PicSpec` with a new `Optional<EditedPicture> edited`. The checks of section 3 come from `EditedPicture.parse`.
- `FieldResolver`: the checks of section 7; no `@BookDefaults` usage or sign for an edited field; the byte length comes from `EditedPicture.byteLength()`, not from `BookWriter.byteLength`.
- `FieldModel`: a new component `edited` (the picture string and the `BLANK WHEN ZERO` flag, or `null`).
- `CodecGenerator`: for an edited field, a `private static final EditedPicture` constant, like the date formatter. New branches in `write()` and `read()` only. The number helpers for `int`, `Integer`, `long`, and `Long` stay the same.
- `ModelSnapshot` (a test helper): the snapshot records the edited picture.
- `FieldContext` does not change, because a converter is not permitted.
- `SubTypesResolver` and the count field check: the errors of section 7.

### 8.3 Copybook generator

- `Picture.parse` accepts an edited picture through `EditedPicture.parse`. `Picture` gets the component `edited`. `length()` stays the number of digits. `byteLength()` gives the number of characters for an edited picture.
- The Java type follows the present rule for numbers: `int`, `long`, or `BigDecimal`.
- `PicText.of` writes the picture as it is, and adds ` BLANK WHEN ZERO` when the item has the clause.
- `DataParser`: `BLANK WHEN ZERO` is kept on the item, and is not ignored. See decision D6.
- `Lexer`: in a picture, a comma or a semicolon is a separator only before a space, as in COBOL. Thus, `Z,ZZ9` is one picture.
- An edited item with a `USAGE` other than `DISPLAY`, also from the group, is an error: `<NAME> has an edited picture and must have usage DISPLAY`.
- An item with edit symbols and with level 88 items or a `VALUE`: the generator ignores them with an info message, like a date hint. The `VALUE` of an edited item is a text literal in COBOL. A `9(n)` with `BLANK WHEN ZERO` keeps its numeric `VALUE` and its level 88 items.
- A date hint on an item with edit symbols is an error: `<NAME> has an edited picture. A date hint needs PIC 9(n) or PIC X(n)`. On a `9(n)` with `BLANK WHEN ZERO`, the date hint applies, and the generator ignores the clause with an info message.
- An edited item as an `emptyKeys` key or as a discriminator is an error: `empty key option: <NAME> must not have an edited picture`, `the discriminator <NAME> must not have an edited picture`.
- The Javadoc of a generated field holds the COBOL declaration. A picture with `*/`, for example `**/**/99`, uses HTML in the Javadoc, so that the comment does not end.
- An edited item as a count field is an error: `DEPENDING ON item <NAME> must not have an edited picture`. The generator does not suggest an edited item as a count field.
- `RecordLength` and `RedefinesResolver` use `Picture.byteLength`. Thus, they get the correct size without other change.
- No change to `MappingOptions`, the model JSON format, the CLI, or the plugins.

### 8.4 Size

The change adds one class to the core, and new branches in `PicParser`, `FieldResolver`, `CodecGenerator`, and the copybook mapper. The other modules do not change.

## 9. Error messages

The processor shows each message as `Invalid PIC string "<pic>": <problem>`, the present format. The generator shows `edited picture <pic>: <problem>`.

| Problem | Message |
|---|---|
| No digit position | `the edited picture has no digit position` |
| A fixed sign in the middle | `a fixed '+' or '-' must be the first or the last symbol` |
| `CR` or `DB` not at the end | `CR or DB must be the last symbol` |
| Two signs | `the picture has more than one sign` |
| `$` in the wrong place, or two `$` | `a fixed '$' must be the first symbol, or the second after a sign` |
| `Z` with `*`, or a floating string with `Z` or `*` | `Z, *, and a floating string are mutually exclusive` |
| Two floating strings | `the picture has more than one floating string` |
| `9` before a suppression symbol | `a '9' must not come before 'Z', '*', or a floating symbol` |
| Suppression after the point, not in all positions | `'Z' after the decimal point needs 'Z' in all digit positions` |
| Two points, or `.` with `V` | `the picture has more than one decimal point` |
| `S` | `S is not permitted in an edited picture. Use '+', '-', CR, or DB` |
| `BLANK WHEN ZERO` with `S` or `*` | `BLANK WHEN ZERO is not permitted with S or '*'` |
| A usage other than `DISPLAY` | `an edited picture must have usage DISPLAY` |
| A `SIGN` clause | `a SIGN clause is not permitted with an edited picture` |

## 10. Decisions for review

- **D1. Write and read.** The design supports the write and the read, not only the write (section 6.1). A write-only field would break `decodeResponse` on the client or `decodeRequest` on the simulator.
- **D2. No rounding.** A value with too many decimal places fails, like the other numbers. COBOL truncates.
- **D3. Lenient read.** The read accepts a digit, a space, `*`, or the floating character in each suppressible position (section 6.2). It does not check the exact suppression.
- **D4. A blank field reads as `null`.** This keeps the present rule for a blank number. Thus, a zero that writes as spaces reads as `null` (`0` for `int` and `long`). The other option is to read it as zero.
- **D5. Only `$`.** No `CURRENCY SIGN` clause and no other currency character.
- **D6. `BLANK WHEN ZERO` on a plain `9(n)`.** Today the generator ignores the clause with an info message, and the field is a zoned number. With this design, the generator writes `pic = "9(5) BLANK WHEN ZERO"`, and the field is an edited picture. The bytes are the same for a nonzero value. A zero writes spaces instead of zeros, and a field with a sign overpunch is not affected, because the clause is not permitted with `S`. This changes the generated code of existing copybooks with the clause.
- **D7. Rejected Java types.** Date and time, enum, converter, discriminator, and count field are errors (section 7). Each can come later if a user needs it.
- **D8. `DECIMAL-POINT IS COMMA` is out of scope.** It is a program setting, not a copybook setting. It would need a new option in `@BookDefaults` or in `MappingOptions`, and thus in `CopybookTree` of the Gradle and Maven plugins. The engine has the point and the comma characters as fields, so a later issue can add it.
- **D9. `S` is an error, not a synonym.** COBOL does not permit `S` in an edited picture.
- **D10. One engine in `bookmap-core`.** The processor and the generator use the same parser at build time, and the codec uses it at run time.
