# BookMap, SIGN LEADING and SIGN SEPARATE: design

Date: 2026-11-13
Status: approved.

Related documents: `docs/design/2026-11-10-bookmap-design.md` (sections 2.2 and 5.3), `docs/design/2026-11-11-bookmap-copybook-design.md` (the generator), GitHub issue #47.

## 1. Intent

Today a signed zoned decimal (`PIC S9`, `USAGE DISPLAY`) has only one sign format: the trailing overpunch, the COBOL default.
COBOL has three more formats, with the `SIGN` clause:

```cobol
05 ORDER-AMOUNT   PIC S9(5)V99 SIGN IS LEADING.
05 ORDER-DISCOUNT PIC S9(3)V99 SIGN IS TRAILING SEPARATE CHARACTER.
05 ORDER-TAX      PIC S9(5)V99 SIGN IS LEADING SEPARATE CHARACTER.
```

This change adds the four formats to the annotations, to the codecs, and to the copybook generator.

## 2. Goals and non-goals

Goals:

- Read and write the four sign formats, for EBCDIC and ASCII charsets.
- Accept the `SIGN` clause in the `pic` attribute, and add the attribute `sign` for a field without `pic`.
- Map the `SIGN` clause of a copybook, also at group level.
- Keep the generated code and the bytes of all books without a `SIGN` clause the same.

Non-goals:

- No `sign` default in `@BookDefaults`. COBOL has no such default. The group-level clause of the generator covers the copybooks.
- No sign format for `COMP-3` or `COMP`. COBOL permits the `SIGN` clause only for `DISPLAY`.
- No change to `FieldContext`. A field with a converter cannot have a sign format other than the default (see 3.4).
- No compiler options such as `NUMPROC`. The read rules for the zones stay as today.

## 3. Annotation API

### 3.1 The enum `Sign`

A new enum in `pt.dazz.bookmap.annotation`:

| Constant | COBOL clause | Bytes |
|---|---|---|
| `TRAILING` | none, or `SIGN IS TRAILING` | `length` |
| `LEADING` | `SIGN IS LEADING` | `length` |
| `TRAILING_SEPARATE` | `SIGN IS TRAILING SEPARATE CHARACTER` | `length + 1` |
| `LEADING_SEPARATE` | `SIGN IS LEADING SEPARATE CHARACTER` | `length + 1` |

`length` is the number of digits. The separate sign character is one more byte.

### 3.2 The attribute `BookField.sign`

```java
@BookField(order = 10, length = 7, scale = 2, signed = true, sign = Sign.LEADING_SEPARATE)
private BigDecimal tax;
```

The default is `Sign.TRAILING`. The processor treats the attribute as set only when the source code sets it, as for the other attributes.

### 3.3 The `SIGN` clause in `pic`

```java
@BookField(order = 10, pic = "S9(5)V99 SIGN LEADING SEPARATE")
private BigDecimal tax;
```

The syntax after the picture is:

```
[SIGN [IS]] {LEADING | TRAILING} [SEPARATE [CHARACTER]]
```

- The words `SIGN` and `IS` are optional, as in COBOL.
- The usage clause and the sign clause can come in the two orders. Each clause can occur one time only.
- `sign` is added to the attributes that `pic` replaces. Thus, `pic` together with `sign` is a compile error: `pic must not be combined with sign`.

### 3.4 Compile errors

| Case | Message |
|---|---|
| A sign clause in a text picture. | `a SIGN clause needs a signed numeric picture` |
| A sign clause in a picture without `S`. | `a SIGN clause needs a signed numeric picture` |
| A sign clause with a usage other than `DISPLAY`. | `a SIGN clause is permitted only with usage DISPLAY` |
| `SIGN` without `LEADING` or `TRAILING`. | `SIGN needs LEADING or TRAILING` |
| A second sign clause. | `unexpected text '<word>' after the sign clause` |
| An explicit `sign` on a `String`, a date and time field, or a text enum. | `sign is not permitted for <type>` |
| An explicit `sign` on an unsigned field. | `sign needs a signed field` |
| An explicit `sign` with a usage other than `DISPLAY`. | `sign is permitted only with usage DISPLAY` |
| A sign other than `TRAILING` on a field with a converter. | `sign <value> is not permitted with a converter` |

An explicit `sign = Sign.TRAILING` on an unsigned field is an error too. COBOL permits the `SIGN` clause only on a signed item.

The sign format applies to all signed `DISPLAY` numbers: `int`, `Integer`, `long`, `Long`, `BigDecimal`, a numeric enum, a numeric discriminator of subtypes, and the number key of an empty item of a fixed collection.

## 4. Byte layout

The table shows the value `-12.34` and the value `+12.34` with `PIC S9(3)V99` (the digits `01234`).

| Format | EBCDIC, -12.34 | EBCDIC, +12.34 | ASCII, -12.34 | ASCII, +12.34 |
|---|---|---|---|---|
| `TRAILING` | `F0 F1 F2 F3 D4` | `F0 F1 F2 F3 C4` | `0123M` | `0123D` |
| `LEADING` | `D0 F1 F2 F3 F4` | `C0 F1 F2 F3 F4` | `}1234` | `{1234` |
| `TRAILING_SEPARATE` | `F0 F1 F2 F3 F4 60` | `F0 F1 F2 F3 F4 4E` | `01234-` | `01234+` |
| `LEADING_SEPARATE` | `60 F0 F1 F2 F3 F4` | `4E F0 F1 F2 F3 F4` | `-01234` | `+01234` |

- **Overpunch (`TRAILING`, `LEADING`).** The sign is in the zone of the last byte or of the first byte. The rules of the design, section 5.3, stay the same. For EBCDIC, the codec works on bytes. For ASCII, the codec uses the characters `{ABCDEFGHI` and `}JKLMNOPQR`.
- **Separate.** The sign is the character `+` or `-` of the charset, in one more byte. All digit bytes are plain digits: zone `F` for EBCDIC, `0` to `9` for ASCII. The `+` and the `-` are the same bytes in all usual EBCDIC code pages (`0x4E` and `0x60`). The codec encodes them with the charset.

## 5. Read rules

- **Blank field.** All bytes of the field are spaces or low-values, also the sign byte. The result is `null`, as today.
- **Overpunch.** The byte with the sign accepts the zones `C`, `F`, `A`, `E` (positive) and `D`, `B` (negative). For ASCII, it accepts a digit, a character of `{ABCDEFGHI` (positive), and a character of `}JKLMNOPQR` (negative). The other bytes must be plain digits.
- **Separate.** The sign byte must be `+` or `-`. All other bytes must be plain digits. Zones `C` or `D` in a digit byte are not valid.
- **Invalid byte.** A byte that is not valid causes `MappingException`. The message names the field, the byte, and its offset in the field (see section 8).
- **Negative zero.** `-00000` or a zone `D` with zero digits reads as zero. A `BigDecimal` keeps the scale of the field, as today.

## 6. Write rules

- A positive value and zero get the positive sign: zone `C`, or `+`.
- A negative value gets the negative sign: zone `D`, or `-`.
- A `null` value writes zero with the positive sign. For example, `LEADING_SEPARATE` writes `+00000`.
- The other rules stay as today: a value with too many digits or decimal places causes `MappingException`. The codec never rounds or truncates.

## 7. Round trip

- For each value that fits the field, a write and then a read give the same value, with the scale of the field.
- A write gives the canonical bytes: zone `C` or `D` (or `+` or `-`) for the sign, and zone `F` for the digits.
- A read and then a write of canonical bytes give the same bytes.
- A read accepts the zones `F`, `A`, `E`, and `B` for an overpunch sign. A write then gives zone `C` or `D`. Thus, the round trip of bytes is exact only for canonical bytes.

## 8. Error messages

| Case | Message |
|---|---|
| Overpunch, or a digit byte that is not valid. | `<field>: byte 0xXX at offset <n> of the field is not valid in a zoned decimal` (as today) |
| A separate sign byte that is not `+` or `-`. | `<field>: byte 0xXX at offset <n> of the field is not a valid sign. The sign must be '+' or '-'` |

A space in the sign byte of a field that is not blank is an error. The codec does not read it as a positive sign.

## 9. Runtime API

The generated code calls `BookReader` and `BookWriter`. This change adds overloads with a `Sign` parameter:

- `BookReader.readNumber(field, length, scale, signed, usage, sign)`
- `BookWriter.writeNumber(field, value, length, scale, signed, usage, sign)`
- `BookWriter.byteLength(usage, length, sign)`
- `EmptySlot.withNumberKey(offset, length, scale, signed, usage, sign)`

The old methods stay. They use `Sign.TRAILING`. The generated code passes the sign only when it is not `TRAILING`. Thus, the generated code of the books without a `SIGN` clause does not change.

`ZonedDecimal.encode` and `ZonedDecimal.decode` get a `Sign` parameter. The class is internal.

## 10. Copybook generator

### 10.1 Parser

The parser accepts this clause in a data description entry:

```
[SIGN [IS]] {LEADING | TRAILING} [SEPARATE [CHARACTER]]
```

- `LEADING` and `SEPARATE` are no longer errors. The message `See issue #47` goes away.
- The clause can be on an elementary item or on a group.

### 10.2 The effective sign

The parser resolves the effective sign of each elementary item, in the check step after the parse:

1. The clause of the item, if it has one.
2. Else the clause of the nearest group that has one.
3. Else the default, `TRAILING`.

The effective sign applies only to an elementary item with a signed numeric picture and the effective usage `DISPLAY`.
A group clause does not change the other children, for example `PIC X`, `PIC 9`, or `COMP-3`. This is not a diagnostic.
A clause on an elementary item that is not a signed numeric `DISPLAY` item is an error: `SIGN on <name> needs a signed numeric DISPLAY item`.

The effective sign is a new component of `DataItem`. It is `null` for a group and for the default.

### 10.3 Length

A separate sign adds one byte to the item. All length calculations use the effective sign: the record length, the offsets of `SYNC` slack bytes, `REDEFINES`, the tables, and the key of an empty item.

### 10.4 Generated field

The generator writes the sign clause in `pic`. It does not write the default:

```cobol
05 ORDER-TAX PIC S9(5)V99 SIGN IS LEADING SEPARATE CHARACTER.
```

```java
@BookField(order = 30, pic = "S9(5)V99 SIGN LEADING SEPARATE")
private BigDecimal orderTax;
```

Thus, the JSON model, `MappingOptions`, and the Gradle and Maven plugins do not change.

## 11. Documentation

- The main design: remove `SIGN LEADING`/`SIGN SEPARATE` from the non-goals (section 2.2), and add the formats to section 5.3.
- The wiki: "Annotations reference", "PIC syntax and type mapping", "Copybook generator" (the table of clauses and "Limitations of the generator"), and "Home" ("Known limitations").
- `ROADMAP.md`: remove #47.

## 12. Decisions for review

1. **API.** The sign goes into `pic`, as the usage. The attribute `sign` is for a field without `pic`. `pic` together with `sign` is a compile error.
2. **One enum with four constants.** The alternative is two attributes (`signLeading`, `signSeparate`). One enum gives no invalid combination.
3. **No `sign` in `@BookDefaults`.**
4. **No sign format with a converter.** `FieldContext` does not change. A later change can add the sign to `FieldContext`.
5. **Explicit `sign` on an unsigned field is an error**, also `TRAILING`. This is the COBOL rule.
6. **Strict separate sign.** Only `+` and `-`. A space in the sign byte is an error.
7. **Strict digits with a separate sign.** Only zone `F` (EBCDIC) or `0` to `9` (ASCII).
8. **Zero and `null` get the positive sign** (`C` or `+`).
9. **Group clause in the generator.** It applies only to signed numeric `DISPLAY` children. The other children do not get a diagnostic.
10. **Overloads in the runtime API.** The old methods stay, and the generated code of the existing books does not change.
11. **README.** The README has no section "Limitations of the generator". The limitations are on the wiki page "Copybook generator". This change updates the wiki.
