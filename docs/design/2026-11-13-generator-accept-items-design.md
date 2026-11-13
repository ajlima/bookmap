# bookmap — Generator: accept the COBOL items that are errors today (design)

Date: 2026-11-13
Status: proposed.
Issue: #37
Parent spec: `2026-11-11-bookmap-copybook-design.md`

## 1. Intent

Today the copybook generator stops with an error on these items: level `66` and `77`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO`, `COMP-5`, `INDEX`, `POINTER`, the `REPLACE` statement, and a `REDEFINES` area in another one.

Real copybooks often contain these items. A developer must then edit the copybook before the generator can read it.

This change accepts each item that BookMap can map without data loss. Each other item stays an error, with a clear message.

## 2. Rules

- Do not lose data silently. A skipped item always gives a diagnostic (anti-goal 3.3 of the parent spec).
- Do not change `bookmap-core` or `bookmap-processor`. The generated code uses only the current BookMap features.
- Do not give generated code with incorrect offsets.
- Do not change the level 88 handling or the `VALUE` clause. Issue #36 changes them.
- Other issues add new core features. Keep the errors for their items, and give the issue number in the message:

| Item | Issue | New message |
|---|---|---|
| `COMP-1`, `COMP-2` | #46 | `COMP-1 is not supported by BookMap. See issue #46.` |
| `SIGN LEADING`, `SEPARATE` | #47 | `LEADING is not supported by BookMap. See issue #47.` |
| Edited pictures | #48 | `edited picture ZZ9 is not supported by BookMap. See issue #48.` |
| `NATIONAL`, picture symbol `N` | #49 | `NATIONAL is not supported by BookMap. See issue #49.` |
| `OCCURS` in an `OCCURS` | #43 | `... BookMap does not support nested collections. See issue #43.` |

The picture symbols `P` and `A` have no issue. Their messages do not change.

## 3. The items

### 3.1 Level 66 (`RENAMES`)

A level 66 item gives a second name to a range of items. It adds no bytes to the record.

- The parser skips the entry, with an `info` diagnostic: `level 66 item B is ignored. RENAMES adds no bytes.`
- A level 66 entry does not end the record. A level 88 after a level 66 attaches to the item before the level 66.

### 3.2 Level 77

A level 77 item is an independent elementary item. It is not part of a record.

- The parser skips the entry, with a `warning`: `level 77 item C is not part of a record and is skipped`.
- A level 77 ends the current record. A level 88 after it attaches to the skipped item, and the generator ignores it.
- An item of level 02 to 49 after a level 77, without a new level `01`, is an error: `level 05 item D must follow a level 01 item, not a level 77 item`.
- A copybook with only level 77 items gives the error `the copybook has no data items`.

### 3.3 `SYNC` and `SYNCHRONIZED`

The parser accepts `SYNC [LEFT | RIGHT]` and `SYNCHRONIZED [LEFT | RIGHT]`. `LEFT` and `RIGHT` have no effect, as in IBM Enterprise COBOL.

On a group, `SYNC` applies to each elementary item of the group.

`SYNC` has an effect only on a binary item: `COMP`, `COMP-4`, `BINARY`, `COMP-5`, and `INDEX`. The compiler aligns the item on a boundary from the start of the record:

| Item | Boundary |
|---|---|
| Binary, 1 to 4 digits | 2 bytes (halfword) |
| Binary, 5 to 18 digits | 4 bytes (fullword) |
| `INDEX` | 4 bytes (fullword) |

If the offset of the item is not on the boundary, the compiler adds slack bytes before the item. The generator does the same: it adds a `FILLER PIC X(n)` item before the item, with an `info` diagnostic: `SYNC adds 2 slack bytes before AMOUNT. The generator adds a FILLER.`

The generator calculates the offsets with the rules of the mapper. Each alternative of a `REDEFINES` area starts at the offset of the redefined item. The group length is the length of its longest alternative.

These cases are errors, because the generator cannot add the slack bytes in the correct place:

- An item in an `OCCURS` that needs slack bytes. This includes the slack bytes between two occurrences. The message is `SYNC on AMOUNT in OCCURS LINES needs slack bytes. BookMap cannot add them in a table. Align the item by hand.`
- An elementary item with `REDEFINES` that needs slack bytes.

A copybook without level `01` has a synthetic record. The generator does not know the offset of the copybook in the real record. If a binary item has `SYNC`, the generator gives a `warning`: `the copybook has no level 01. SYNC assumes that ORDER starts on a doubleword boundary.`

`SYNC` on a `DISPLAY` or `COMP-3` item has no effect and gives no diagnostic.

### 3.4 `JUSTIFIED` and `JUST`

The parser accepts `JUSTIFIED [RIGHT]` and `JUST [RIGHT]`. The item is a text field, as before.

`JUSTIFIED` changes only the result of a COBOL `MOVE`. It does not change the bytes in the record. But BookMap always writes a text with spaces on the right. A program that compares the field with a right-aligned value can then give a different result. Thus, the generator gives a `warning`: `JUSTIFIED on NAME is ignored. BookMap writes the text on the left and keeps the spaces on the left when it reads.`

### 3.5 `BLANK WHEN ZERO`

The parser accepts `BLANK [WHEN] ZERO`, `ZEROS`, or `ZEROES`. Another word after `BLANK` is an error: `BLANK needs WHEN ZERO`.

BookMap reads a blank number as `0` (`int`, `long`) or `null` (`BigDecimal`). It writes the value 0 as digits, which a COBOL program reads as zero. No data is lost. The generator gives an `info` diagnostic: `BLANK WHEN ZERO on TOTAL is ignored. BookMap reads spaces as no value and writes zero as digits.`

### 3.6 `COMP-5` and `COMPUTATIONAL-5`

A `COMP-5` item has the same bytes as a `COMP` item: big-endian binary, two's complement if signed. The difference is the range: `COMP-5` can hold all values of its bytes, for example 65535 in a `PIC 9(4) COMP-5`.

- The generator maps the item as `COMP`. The `pic` is `9(4) COMP`. The Javadoc keeps the COBOL declaration with `COMP-5`.
- The Java type can hold all values of the bytes:

| Picture | Bytes | Java type |
|---|---|---|
| Decimals | any | `BigDecimal` |
| 1 to 4 digits | 2 | `int` |
| 5 to 9 digits, signed | 4 | `int` |
| 5 to 9 digits, unsigned | 4 | `long` |
| 10 to 18 digits, signed | 8 | `long` |
| 10 to 18 digits, unsigned | 8 | `BigDecimal` |

- The BookMap reader accepts all values of the bytes. The BookMap writer accepts only values with the digits of the picture. A larger value gives a `MappingException`, not silent data loss.
- The generator gives a `warning`: `COMP-5 on COUNTER is mapped as COMP. BookMap reads all binary values, but writes only values with at most 4 digits.`

### 3.7 `INDEX`

An index data item (`USAGE INDEX`) has no picture. In IBM Enterprise COBOL, it has 4 bytes of signed binary data: a byte offset in a table.

- The generator maps the item as `PIC S9(9) COMP`, Java type `int`. An `int` holds all values of the 4 bytes.
- The generator gives an `info` diagnostic: `INDEX item IDX is mapped as PIC S9(9) COMP. Its value is a byte offset in a table.`
- An `INDEX` item with a `PIC` is an error: `the INDEX item IDX must not have a PIC`.
- On a group, `USAGE INDEX` applies to each elementary item of the group.

### 3.8 `POINTER`: the error stays

A pointer has 4 bytes or 8 bytes. The size depends on the compiler option `LP`, and the copybook does not tell it. A size guess can give incorrect offsets. Also, an address has no meaning outside the program.

The message tells the developer what to do: `POINTER is not supported by BookMap: its size depends on the compiler option LP. Replace it with PIC X(4) or PIC X(8), for example with REPLACE ==POINTER== BY ==PIC X(4)==.`

### 3.9 The `REPLACE` statement

The generator applies the `REPLACE` statement, as a COBOL compiler does:

- `REPLACE ==a== BY ==b== [==c== BY ==d== ...].` starts a set of replacements. It applies to the text after the statement.
- A new `REPLACE` statement replaces the current set. `REPLACE OFF.` stops the replacements.
- The generator applies `REPLACE` after the `COPY` expansion, and after `COPY ... REPLACING`. Thus, it also changes the text of the `COPY` members.
- The operands must be pseudo-text. Another operand is an error: `REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==.`
- An empty pseudo-text before `BY` is an error: `REPLACE needs a text to replace`.
- The comparison and the tag rule (`==:PFX:==`) are the same as for `REPLACING`.
- `REPLACE ALSO` and `REPLACE LAST` are not supported. They give the pseudo-text error above.

### 3.10 A `REDEFINES` area in another `REDEFINES` area

BookMap has no subtypes in a subtype (#39), and no `REDEFINES` without a discriminator (#38). Thus, BookMap cannot map the inner alternatives.

The generator uses the rule for an elementary item with `REDEFINES` (parent spec 5.6): it keeps the redefined group, and it skips each group that redefines it, with a `warning`: `the REDEFINES area C is inside another REDEFINES area. BookMap keeps C and skips D. See issues #38 and #39.`

The bytes of the skipped group are still in the fields of the kept group. Thus, no data is lost.

A skipped group that is longer than the kept group is an error, because the items after the area would move: `D redefines C and is longer than C. BookMap keeps C, so the items after it would move.`

## 4. AST and model

- The AST `Usage` enum gets the value `COMP5`. The mapper uses it for the Java type. The `pic` text and the byte length are the same as for `COMP`.
- An `INDEX` item becomes, in the AST, an item with the picture `S9(9)` and the usage `COMP`. Its declaration text keeps `INDEX`.
- A slack `FILLER` is a normal `FILLER` item in the AST. Its declaration text is `05 FILLER PIC X(2). Slack bytes before AMOUNT for SYNC.`
- The model JSON does not change. The format version stays 1.

## 5. Tests

- Parser tests for each new clause, each new diagnostic, and each error.
- Mapper tests for `COMP-5` types, slack fillers in a direction split, and a nested `REDEFINES` area.
- An end-to-end test: a copybook with `SYNC`, `COMP-5`, `INDEX`, `JUSTIFIED`, `BLANK WHEN ZERO`, level 66, level 77, and `REPLACE`. Generate it, compile it with the processor, and round-trip an object through the simulator. Check the record length.
- 100% line and branch coverage stays.

## 6. Documentation

- Wiki page "Copybook generator": the levels, the clause table, the `REPLACE` statement, `REDEFINES`, and the limitations.
- Parent spec: the table in 4.3 points to this spec.

## 7. Decisions for review

All decisions in this section come from the builder, not from the owner. The owner was not available.

| # | Item | Options | Decision | Reason |
|---|---|---|---|---|
| 1 | Level 66 | (a) Keep the error. (b) Map an alias. (c) Ignore with `info`. | (c) | `RENAMES` adds no bytes, so nothing is lost. BookMap has no alias field. |
| 2 | Level 77 | (a) Keep the error. (b) Make an operation class for each item. (c) Skip with a `warning`. | (c) | A level 77 item is not part of a record, so the message layout does not change. A class with one field is not useful as an operation. A `warning`, not an `info`, because a developer can expect the item in the class. |
| 3 | `SYNC` | (a) Keep the error. (b) Ignore it. (c) Add slack `FILLER` items with the IBM rules; error in a table. | (c) | (b) gives incorrect offsets when the compiler adds slack bytes. (c) gives the COBOL offsets. In a table, the slack bytes go between the occurrences, and a BookMap item class cannot have them in all cases. |
| 4 | `SYNC` in a copybook without level `01` | (a) Error. (b) Assume offset 0, with a `warning`. | (b) | Most copybooks without level `01` start at offset 0 of the record. The warning tells the assumption. |
| 5 | `JUSTIFIED` | (a) Keep the error. (b) Accept with `warning`. | (b) | The bytes do not change. Only the alignment of a written value changes. |
| 6 | `BLANK WHEN ZERO` | (a) Keep the error. (b) Accept with `info`. | (b) | BookMap already reads a blank number. A written zero is valid for COBOL. |
| 7 | `COMP-5` | (a) Keep the error. (b) Map as `COMP` with the type of the digits. (c) Map as `COMP` with the type of the bytes, with a `warning`. | (c) | The bytes are the same as `COMP`. The Java type of (c) holds all values that the reader gives. The writer limit is in the core; a new core feature can remove it. |
| 8 | `INDEX` | (a) Keep the error. (b) Map as `PIC S9(9) COMP`, with `info`. | (b) | IBM Enterprise COBOL stores an index in 4 bytes of signed binary data. `int` holds all values. |
| 9 | `POINTER` | (a) Map as 4 bytes. (b) Keep the error with a better message. | (b) | The size depends on the `LP` compiler option. A guess can give incorrect offsets (anti-goal of the parent spec). |
| 10 | `REPLACE` | (a) Keep the error. (b) Implement it. | (b) | The `REPLACING` code already compares pseudo-text. `REPLACE` uses the same code. |
| 11 | Nested `REDEFINES` area | (a) Keep the error. (b) Keep the redefined group, skip the others with a `warning`. | (b) | This is the existing rule for an elementary `REDEFINES`. The bytes stay in the kept fields. #38 and #39 can add a full mapping later. |
| 12 | Errors of other issues | (a) Keep the messages. (b) Add the issue number. | (b) | The developer can follow the issue. |
| 13 | `SYNC` on a group | (a) Error. (b) Apply to each elementary item. | (b) | This is the IBM rule for a level `01` group. The generator also accepts it at other levels, because it adds no risk. |
