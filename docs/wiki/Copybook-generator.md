The copybook generator reads a COBOL copybook and writes the annotated Java classes.
The classes compile with the BookMap processor without a change.

This page gives the concepts, the options file, and the mapping rules. They are the same for all tools.
Each tool has its own page:

| Tool | Page | Use it when |
|---|---|---|
| Command line | [Copybook generator CLI](Copybook-generator-CLI) | You generate the classes one time, or you run the generator from a script or another build tool. |
| Gradle plugin | [Copybook generator Gradle plugin](Copybook-generator-Gradle-plugin) | Your project uses Gradle, and you generate the classes in each build. |
| Maven plugin | [Copybook generator Maven plugin](Copybook-generator-Maven-plugin) | Your project uses Maven, and you generate the classes in each build. |
| Java API | [Copybook generator CLI](Copybook-generator-CLI#java-api) | You write your own tool. |

All tools use the same code in the library `bookmap-copybook`. Thus, the same copybook and the same options give the same classes in all tools.

## Scaffold mode and build mode

Use the generator in one of two modes:

- **Scaffold mode.** Generate the classes one time. Then keep them by hand, as normal source files. Use the command line.
- **Build mode.** Keep the copybooks in the project. Generate the classes again in each build. Use the Gradle plugin or the Maven plugin.

| | Scaffold mode | Build mode |
|---|---|---|
| Source of truth | The Java classes. | The copybooks. |
| Generated classes | In `src/main/java`, in version control. | In the build directory, not in version control. |
| A change in a copybook | Change the Java classes by hand, or generate them again. | The next build generates the classes again. |
| Hand changes (`Continuable`, `Validatable`, dates, `dependsOn`) | Permitted. | Not permitted. Use the options file, or a subclass. |

**CAUTION:** Do not edit the generated classes in build mode. The next build deletes the output directory and your changes are lost.

## Pipeline

```
copybook ──parse──▶ AST ──map──▶ model (JSON) ──generate──▶ Java classes
```

1. **Parse.** The parser reads the copybook and expands `COPY ... REPLACING`. It gives a tree of COBOL data items.
2. **Map.** The mapper applies the BookMap rules: names, Java types, `pic`, `order`, collections, directions, and subtypes. It gives the model.
3. **Generate.** The generator writes one Java class for each class of the model, with JavaPoet.

The model has a stable JSON form with a format version. The same copybook and options always give the same JSON and the same Java.
The generator does not keep old models and does not compare them. Other tools can keep the JSON files and compare them to find changes in a copybook.

## Options file

An options file is a JSON file with the options of one copybook. The command line reads it with `--config`.
The build plugins read the file `<NAME>.json` next to `<NAME>.cpy`.

```json
{
  "format": "fixed",
  "charset": "UTF-8",
  "copyDirs": ["copylib"],
  "recordName": "GET-ORDER",
  "stripPrefixes": ["WS-"],
  "inputMarkers": ["INPUT", "IN", "REQUEST", "REQ"],
  "outputMarkers": ["OUTPUT", "OUT", "RESPONSE", "RESP"],
  "directions": { "INVOICE-QUERY": "INPUT", "INVOICE-DETAIL": "OUTPUT" },
  "subTypes": {
    "CHECKING-DATA": {
      "discriminator": "ACCOUNT-TYPE",
      "values": { "CHECKING-DATA": "01", "SAVINGS-DATA": "02" },
      "classNames": { "SAVINGS-DATA": "SavingsAccount" }
    }
  },
  "conditions": { "PARTY-ROLE": "enum", "ACCOUNT-FLAG": "constants" },
  "countFields": { "INVOICE-LINE": "INVOICE-LINE-COUNT" },
  "maxRecordLength": 32500,
  "dates": { "OPENED-ON": "yyyyMMdd", "*-TIME": "HHmmss" },
  "continuable": { "GET-ACCOUNT": "com.example.accounts.AccountPaging" },
  "validatable": { "GET-ACCOUNT": "ReturnCodes" },
  "package": "com.example.accounts",
  "lombok": false
}
```

All keys are optional. An unknown key is an error.

| Key | Type | Meaning | Default |
|---|---|---|---|
| `format` | `"fixed"` or `"free"` | The reference format. See [Source format](#source-format). | `fixed` |
| `charset` | text | The charset of the copybook and of its `COPY` members. | `UTF-8` |
| `copyDirs` | list of paths | The directories with the `COPY` members, in search order. A relative path starts at the directory of the options file. | empty |
| `recordName` | text | The record name for a copybook whose first item is not level `01`. | The file name without the extension, in upper case. |
| `stripPrefixes` | list of texts | The prefixes to remove from the Java names, for example `WS-`. See [Names](#names). | empty |
| `inputMarkers` | list of texts | The name parts that mark an input alternative. Write them in upper case. The list replaces the default list. | `INPUT`, `IN`, `REQUEST`, `REQ` |
| `outputMarkers` | list of texts | The name parts that mark an output alternative. Write them in upper case. The list replaces the default list. | `OUTPUT`, `OUT`, `RESPONSE`, `RESP` |
| `directions` | map | The direction (`INPUT`, `OUTPUT`, or `INPUT_OUTPUT`, in upper case) of each `REDEFINES` alternative. See [Directions](#directions). | empty |
| `subTypes` | map | The subtype decisions. See [Subtypes](#subtypes). | empty |
| `conditions` | map | The mode (`enum`, `constants`, or `none`) of the level 88 items of an elementary item. The mode is not case-sensitive. See [Level 88 items](#level-88-items). | empty: the generator selects the mode. |
| `countFields` | map | The count field of each fixed table: the table name, then the name of the count field. See [Count field of a fixed table](#count-field-of-a-fixed-table). | empty |
| `emptyItems` | map | The bytes of an unused entry of each fixed table: the table name, then `SPACES` or `LOW-VALUES`. See [Empty entries of a fixed table](#empty-entries-of-a-fixed-table). | empty |
| `emptyKeys` | map | The key item that marks an empty entry of each fixed table: the table name, then the name of the key item. See [Empty entries of a fixed table](#empty-entries-of-a-fixed-table). | empty |
| `maxRecordLength` | integer | The maximum record length in bytes. A longer record gives a warning. See [Record length](#record-length). | no check |
| `dates` | map | The date hints: a date or time pattern for each item name or name pattern. See [Date hints](#date-hints). | empty |
| `continuable` | map | The handler class of each `Continuable` record. See [Continuable and Validatable](#continuable-and-validatable). | empty |
| `validatable` | map | The handler class of each `Validatable` record. | empty |
| `package` | text | The Java package of the classes. | The tool gives it. |
| `lombok` | boolean | Write Lombok annotations instead of getters and setters. | `false` |

A name in an option can be qualified with `OF` or `IN`, for example `CITY OF BILLING-ADDRESS`. The qualifier can be any group around the item, not only the parent.
The names in the options are not case-sensitive. A name that matches more than one item is an error.
Write the options file in UTF-8.

## Source format

### Fixed format

The default format is `fixed`, the classic layout of a COBOL source line:

| Columns | Area | Use |
|---|---|---|
| 1 to 6 | Sequence area | The generator ignores it. |
| 7 | Indicator | See the next table. |
| 8 to 72 | Code | The data entries. |
| 73 and after | Identification area | The generator ignores it. |

| Indicator (column 7) | Meaning |
|---|---|
| space | A normal line. |
| `*` or `/` | A comment line. |
| `D` or `d` | A debugging line. The generator ignores it. |
| `-` | A continuation of a literal from the line before. Start the continued text with a quote. |
| Another character | An error: `unknown indicator 'X' in column 7`. |

- Do not use tab characters. A tab is an error: `a tab character makes the columns unclear. Replace tabs with spaces.`
- `*>` does not start a comment in fixed format. Remove the inline comments, or use the free format.

### Free format

Set `format` to `free` (or `--format free`) for a copybook without columns.

- `*>` starts a comment to the end of the line.
- A `*` in column 1 is not a comment.
- A literal cannot continue on the next line.

In both formats, lower case is permitted. The generator changes the names and the pictures to upper case.

**CAUTION:** If almost each line gives `an entry must start with a level number`, the format is wrong. Check the `format` option.

## Data entries

### Levels

- Levels 01 to 49, 66, 77, and 88 are permitted. Each level `01` gives one class with `@BookOperation`.
- One copybook can have more than one level `01`. Each one gives its own class.
- If the first item is not level `01`, the generator adds a record. The record name comes from `recordName`.
- A level 66 item (`RENAMES`) adds no bytes. The generator ignores it, with an `info` diagnostic: `level 66 item AB is ignored. RENAMES adds no bytes.`
- A level 77 item is not part of a record. The generator skips it, with a warning: `level 77 item COUNTER is not part of a record and is skipped`.
- A level 77 item ends the record. Put a level `01` before the next item of level 02 to 49. Otherwise, the error is `level 05 item D must follow a level 01 item, not a level 77 item`.
- Another level is an error, for example `level 50 is not valid`.
- A level `01` with `REDEFINES` gives a warning: `record OTHER-REC redefines record MAIN-REC. Both records are kept.`
- A group with a `PIC` is an error: `GRP is a group and must not have a PIC`. An elementary item without a `PIC` is an error: `NOPIC has no PIC`.
- A level 88 must follow an item: `a level 88 condition needs an item before it`.

### Clauses

| Clause | Result |
|---|---|
| `PIC` or `PICTURE [IS]` with `X`, `9`, `S` (first), `V`, and repeat counts | Accepted. |
| `PIC` with a numeric edited picture, for example `Z,ZZ9.99CR` or `$$$,$$9.99` | Accepted: a number with the edited picture in `pic`. See [Edited pictures](#edited-pictures). |
| `USAGE [IS]` with `DISPLAY`, `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY`, `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | Accepted. A usage on a group applies to its items. |
| `COMP-5`, `COMPUTATIONAL-5` | Accepted as `COMP`, with a warning. See [COMP-5](#comp-5). |
| `INDEX` | Accepted as `PIC S9(9) COMP`, with an `info` diagnostic. See [INDEX](#index). |
| `SYNC`, `SYNCHRONIZED`, with `LEFT` or `RIGHT` | Accepted. The generator adds slack bytes. See [SYNC](#sync). |
| `JUSTIFIED`, `JUST`, with `RIGHT` | Accepted, with a warning: `JUSTIFIED on D is ignored. BookMap writes the text on the left and keeps the spaces on the left when it reads.` |
| `BLANK [WHEN] ZERO`, `ZEROS`, or `ZEROES` | Accepted on an unsigned numeric `DISPLAY` item and on an edited picture without `*`. The generator writes the clause in `pic`, for example `9(5)V99 BLANK WHEN ZERO`. A zero writes as spaces. |
| `OCCURS n [TIMES]` | Accepted: a fixed collection. |
| `OCCURS m TO n [TIMES] DEPENDING [ON] name [OF\|IN group]` | Accepted: a variable collection. |
| `REDEFINES` | Accepted. See [Directions](#directions) and [Subtypes](#subtypes). |
| `[SIGN [IS]] LEADING` or `TRAILING`, with `SEPARATE [CHARACTER]` | Accepted on a signed numeric `DISPLAY` item and on a group. The generator writes the clause in `pic`, for example `S9(5)V99 SIGN LEADING SEPARATE`. `SIGN TRAILING` is the default and is not written. See [Sign clause](#sign-clause). |
| `VALUE [IS]` of an item, with one literal | Accepted: the initial value of the field. See [Initial values](#initial-values). Another form, for example `ALL '-'`, gives a warning: `the VALUE clause of NOTE is not supported and is ignored`. |
| Level 88 with `VALUE [IS]` or `VALUES [ARE]`, values, and `THRU` or `THROUGH` ranges | Accepted. See [Level 88 items](#level-88-items). |
| `INDEXED BY`, `ASCENDING KEY`, `DESCENDING KEY` | Ignored, with an `info` diagnostic, for example `INDEXED is ignored`. |
| `COMP-1`, `COMP-2`, `COMPUTATIONAL-1`, `COMPUTATIONAL-2` | Error: `COMP-1 is not supported by BookMap. See issue #46.` |
| `NATIONAL` | Error: `NATIONAL is not supported by BookMap. See issue #49.` |
| `POINTER` | Error. See [POINTER](#pointer). |
| An incorrect edited picture | Error, for example `edited picture Z*9: Z, *, and a floating string are mutually exclusive`. |
| Pictures with `P`, `A`, or `N` | Error, for example `picture symbol A is not supported by BookMap`. The message for `N` gives issue #49. |
| A picture with an incorrect form, for example `X9` | Error: `picture X9 is not valid`. |
| Another word | Error, for example `unknown clause FOO`. |

Other errors:

- `A12 has a text picture and must not have COMP or COMP-3`
- `A13: COMP supports at most 18 digits`
- `BLANK needs WHEN ZERO`
- `BLANK WHEN ZERO on TOTAL: edited picture S9(3): BLANK WHEN ZERO is not permitted with S or '*'`
- `TOTAL has an edited picture and must have usage DISPLAY`
- `the INDEX item IDX must not have a PIC`

A literal is a text in quotes, a number, or a figurative constant: `SPACE`, `SPACES`, `ZERO`, `ZEROS`, `ZEROES`, `HIGH-VALUE(S)`, `LOW-VALUE(S)`, or `QUOTE(S)`.
A level 88 without a `VALUE` clause, or with another value (for example `ALL '*'` or `X'FF'`), gives a warning, and the generator ignores it: `condition CODE-HEX: the value X'FF' is not supported. The condition is ignored.`

### SYNC

`SYNC` aligns a binary item (`COMP`, `COMP-4`, `BINARY`, `COMP-5`, or `INDEX`) on a boundary from the start of the record. The generator uses the rules of IBM Enterprise COBOL:

| Item | Boundary |
|---|---|
| Binary, 1 to 4 digits | 2 bytes |
| Binary, 5 to 18 digits, and `INDEX` | 4 bytes |

If the item is not on its boundary, the compiler adds slack bytes before it. The generator adds a `FILLER` with the same length, with an `info` diagnostic: `SYNC adds 1 slack byte before B. The generator adds a FILLER.`

- `SYNC` on a group applies to each item in the group.
- `SYNC` on a `DISPLAY` or `COMP-3` item has no effect and gives no diagnostic.
- Each alternative of a `REDEFINES` area starts at the offset of the redefined item.
- In an `OCCURS`, the generator cannot add slack bytes. An item that needs them is an error: `SYNC on B in OCCURS T needs slack bytes. BookMap cannot add them in a table. Align the item by hand.`
- An elementary item with `REDEFINES` that needs slack bytes is an error: `SYNC on C needs slack bytes, but C has REDEFINES. Align the item by hand.`

**CAUTION:** If the copybook has no level `01`, the generator does not know the offset of the copybook in the record. It assumes offset 0, with a warning: `the copybook has no level 01. SYNC assumes that ORDER starts on a doubleword boundary.` Make sure that the first item starts on a doubleword boundary. Otherwise, the offsets are incorrect.

### COMP-5

A `COMP-5` item has the same bytes as a `COMP` item. But it can hold all values of its bytes, for example 65535 in a `PIC 9(4) COMP-5`.

- The `pic` of the field is `COMP`, for example `pic = "9(4) COMP"`. The Javadoc keeps `COMP-5`.
- The Java type holds all values of the bytes:

| Picture | Java type |
|---|---|
| 1 to 4 digits | `int` |
| 5 to 9 digits, signed | `int` |
| 5 to 9 digits, unsigned | `long` |
| 10 to 18 digits, signed | `long` |
| 10 to 18 digits, unsigned, or decimals | `BigDecimal` |

BookMap reads all values. But it writes only the values with the digits of the picture. A larger value gives a `MappingException`. The generator gives a warning: `COMP-5 on I is mapped as COMP. BookMap reads all binary values, but writes only values with at most 4 digits.`

### INDEX

An index data item (`USAGE INDEX`) has no `PIC`. It has 4 bytes of signed binary data: a byte offset in a table.

The generator maps it as `PIC S9(9) COMP`, with the Java type `int`. The `info` diagnostic is `INDEX item IDX is mapped as PIC S9(9) COMP. Its value is a byte offset in a table.`
`USAGE INDEX` on a group applies to each item in the group.

### POINTER

A `POINTER` item is an error. Its size is 4 or 8 bytes, and it depends on the compiler option `LP`. The copybook does not give this option.
The message is `POINTER is not supported by BookMap: its size depends on the compiler option LP. Replace it with PIC X(4) or PIC X(8), for example with the REPLACE statement.`

Use the [REPLACE statement](#the-replace-statement) to change the item without a change to the copybook file:

```cobol
           REPLACE ==USAGE POINTER== BY ==PIC X(4)==.
```

Write the old text as it is in the copybook. For `05 P POINTER.`, use `==POINTER==`.

## COPY members

The parser expands each `COPY` statement with the member from a copy directory.

- The tools do not search for members. Declare the copy directories (`copyDirs`, or `--copy-dir`).
- The search in each directory is not recursive. A member in a subdirectory of a copy directory is not found.
- For `COPY ADDRESS.`, the parser looks in each directory for a file `ADDRESS`, `ADDRESS.cbl`, `ADDRESS.cob`, or `ADDRESS.cpy`. The case of the name is not important.
- If one directory has more than one of these files, the first name in alphabetical order wins. Keep only one file for each member.
- The first directory that has the member wins. Thus, the order of the directories is important.
- If no directory has the member, the generator reports an error, for example `copy member 'ADDRESS' not found in /work/src/main/copylib`.
- Without a copy directory, the message is `copy member 'ADDRESS' not found: no copy directory is set`.
- A copy directory that does not exist is not an error. The search skips it.
- The parser reads a member with the format and the charset of the copybook.

### The COPY statement

- Write the member name as a word or a literal: `COPY ADDRESS.` or `COPY 'ADDRESS'.`
- The generator ignores a library name in `COPY member OF library` or `COPY member IN library`.
- A member can contain `COPY`. The generator expands it with the same copy directories.
- A cycle is an error, for example `COPY cycle: LOOPA -> LOOPB -> LOOPA`.
- A member can give two items with the same name. The second field gets a number, for example `email2`.

### REPLACING

- A `REPLACING` operand is pseudo-text (`==...==`), a word, a literal, or a number. Give one or more `operand BY operand` pairs.
- The generator compares pseudo-text token by token. The number of spaces is not important.
- A pseudo-text such as `==:PFX:==` is a tag. It also replaces text inside words: `:PFX:-NAME` gives `SENDER-NAME`.
- To replace a picture string, put `PIC` in the pseudo-text: `==PIC S9(5)V99== BY ==PIC S9(7)V99==`.
- The generator applies the replacements one time. It does not scan the replaced text again.
- `REPLACING LEADING` and `REPLACING TRAILING` are not supported. They give `REPLACING needs BY`.

```
      * lib/PARTY.cpy
           05  :PFX:-NAME        PIC X(20).
           05  :PFX:-PHONE       PIC X(15).
```

```cobol
       01  SHIPMENT.
           COPY PARTY REPLACING ==:PFX:== BY ==SENDER==.
           COPY PARTY REPLACING ==:PFX:== BY ==RECEIVER==.
```

The class `Shipment` gets the fields `senderName`, `senderPhone`, `receiverName`, and `receiverPhone`.

Other messages of the `COPY` statement: `COPY needs a member name`, `REPLACING needs a value after BY`, `the COPY statement has an unknown word X`, `the COPY statement has no closing period`, `the pseudo-text has no closing ==`, and `the literal has no closing quote`.

### The REPLACE statement

The `REPLACE` statement changes the text after it, as in a COBOL compiler.

- Write `REPLACE ==old== BY ==new==.` Give one or more pairs. The operands must be pseudo-text.
- A new `REPLACE` statement replaces the current pairs. `REPLACE OFF.` stops the replacements.
- The generator applies `REPLACE` after the `COPY` expansion and after `REPLACING`. Thus, it also changes the text of the `COPY` members.
- The comparison and the tags (`==:PFX:==`) follow the rules of [REPLACING](#replacing).

```cobol
           REPLACE ==:TAG:== BY ==TRK==.
       01  GET-TRACKING.
           05  :TAG:-SHIPMENT-ID        PIC X(10).
           REPLACE OFF.
```

The field is `trkShipmentId`.

Messages: `REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==.`, `REPLACE needs BY`, `REPLACE needs a text to replace`, `the REPLACE statement has an unknown word X`, and `the REPLACE statement has no closing period`.
`REPLACE ALSO` and `REPLACE LAST` are not supported. They give the pseudo-text message.

## Mapping

| COBOL | Java |
|---|---|
| Level `01` | A class with `@BookOperation`. |
| A group without `OCCURS` | No class. Its items go into the class of the parent. |
| `OCCURS n` | `@BookCollection(fixed = true)` and an item class. With the option `countFields`, also `dependsOn`. |
| `OCCURS m TO n DEPENDING ON X` | `@BookCollection(dependsOn = "x")` and an item class. |
| `OCCURS m TO n` without `DEPENDING ON` | `@BookCollection(maxOccurs = n, fixed = true)`. |
| An elementary item with `OCCURS` | An item class with one field `value`. |
| An elementary item | A `@BookField` with `pic`, for example `pic = "S9(7)V99 COMP-3"`. |
| `FILLER` | A field `filler1`, `filler2`, ... See [FILLER](#filler). |
| `REDEFINES` of an elementary item | The first definition. The other item is skipped, with a warning: `E2 redefines E1 and is skipped`. See [REDEFINES of an elementary item](#redefines-of-an-elementary-item). |
| `REDEFINES` of a group | A direction split or subtypes. See the next sections. |
| Level `88` of an elementary item | An enum, or constants and boolean methods. See [Level 88 items](#level-88-items). |
| Level `88` of a group | Ignored, with an `info` diagnostic: `condition CARD-ACCOUNT is ignored`. |
| `VALUE` of an elementary item | The initial value of the field. See [Initial values](#initial-values). |
| `VALUE` of a group | Ignored. |
| Level `88` or `VALUE` of an item with a [date hint](#date-hints) | Ignored, with an `info` diagnostic. The date hint has priority. |
| Level `66` | Ignored, with an `info` diagnostic. |
| Level `77` | Skipped, with a warning. |
| `SYNC` on a binary item | A slack `FILLER` before the item, if necessary. See [SYNC](#sync). |

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9(n)` with 1 to 9 digits | `int` |
| `9(n)` with 10 to 18 digits | `long` |
| Decimals, or more than 18 digits | `BigDecimal` |
| An item with level 88 items in the `enum` mode | The generated enum |
| An item with a [date hint](#date-hints) | `LocalDate`, `LocalTime`, or `LocalDateTime` |

The `order` values are 10, 20, 30, ... in each class.

### Names

- `GET-CUSTOMER-ORDERS` gives the class `GetCustomerOrders`. `ORDER-AMOUNT` gives the field `orderAmount`. `-` and `_` separate the name parts.
- A Java keyword gets the suffix `Value`, for example `class` gives `classValue`.
- A field name that starts with a digit gets the prefix `field`: `1ST-DATE` gives `field1stDate`.
- A class name that starts with a digit gets the prefix `Record`. A class name equal to a `java.lang` class gets the suffix `Record`: `STRING OCCURS 5` gives `StringRecord`.

`stripPrefixes` removes a prefix from the Java names:

- The generator compares the prefixes without case. It removes only the first prefix that matches.
- It does not remove a prefix that is the full name.
- It applies to the field names and to the class names, also to the record class: `WS-LIST-ORDERS` gives `ListOrders`.
- It does not change the Javadoc, or the COBOL names in the options.

### Name collisions

If a field name is already in use in the class:

1. The generator adds the name of the nearest group in front of it, for example `addressCity`.
2. If that name is also in use, it tries the next outer group.
3. If all names are in use, it adds a number: `city2`, `city3`.

The class names are compared without case. If an item class name is in use, the generator adds the name of the parent class in front of it, for example `GetRouteLegs`. Then it adds a number. A record name in use gets a number, for example `Item2`.

### FILLER

- The number of `fillerN` restarts in each class.
- A `FILLER` group without `OCCURS` gives no field. Its items go into the class.
- A `FILLER` with `OCCURS` gives a collection `fillerN` and an item class `Filler`.

### OCCURS DEPENDING ON

- `dependsOn` contains the Java name of the count field, for example `dependsOn = "orderCount"`.
- The count field stays a normal `@BookField`, with the direction `INPUT_OUTPUT`.
- `maxOccurs` is the maximum. The generator does not keep the minimum.
- The count item must be an integer in the same class, before the table.

| Message | Cause |
|---|---|
| `DEPENDING ON item NOPE does not exist` | No item has this name. |
| `DEPENDING ON item X is ambiguous. Qualify it: ...` | More than one item has this name. Add `OF group`. |
| `DEPENDING ON item LATER must be in the same class, before the table` | The count item is after the table, or in another class. |
| `DEPENDING ON item CNTX must be an integer` | The count item is text, or has decimals. |
| `DEPENDING ON item CNT has 1 digits and cannot hold 20` | The count item is too small for the maximum. |
| `OCCURS T4B is inside OCCURS T4. BookMap does not support nested collections. See issue #43.` | A table in a table. |

### Count field of a fixed table

Many copybooks keep the number of used entries of a fixed table in a separate field:

```cobol
       01  GET-INVOICE.
           05  INVOICE-ID               PIC X(12).
           05  INVOICE-LINE-COUNT       PIC 9(3).
           05  INVOICE-LINE OCCURS 100 TIMES.
               10  PRODUCT-CODE         PIC X(12).
               10  LINE-AMOUNT          PIC S9(7)V99 COMP-3.
```

The copybook does not tell which field is the count. Thus, the generator does not guess it. Without an option, the table is a fixed collection in LOW-VALUES mode.
Use the option `countFields` to link the count field to the table:

```json
{
  "countFields": { "INVOICE-LINE": "INVOICE-LINE-COUNT" }
}
```

On the command line, use `--count-field INVOICE-LINE=INVOICE-LINE-COUNT`. The generator then writes `dependsOn` on the fixed collection:

```java
@BookField(order = 20, pic = "9(3)")
private int invoiceLineCount;

@BookCollection(order = 30, maxOccurs = 100, direction = Direction.INPUT_OUTPUT, fixed = true, dependsOn = "invoiceLineCount")
private List<InvoiceLine> invoiceLine;
```

The codec then uses count mode. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#count-mode).

Rules for the option:

- The key is the name of a table with `OCCURS n` and without `DEPENDING ON`. The value is the name of the count field.
- The count field is an elementary numeric item without decimals.
- The count field is in the same class as the table, before the table. A group around it is permitted.
- The digits of the count field must hold the maximum of the table.
- One count field serves only one table.

| Message | Cause |
|---|---|
| `count field option: item NOPE does not exist` | No item has this name. The position is `<file>:1:1`. |
| `count field option: item X is ambiguous. Qualify it: ...` | More than one item has this name. Add `OF group`. |
| `count field option: N has no OCCURS` | The table is not a table. |
| `count field option: T has OCCURS DEPENDING ON. Remove the option: the DEPENDING ON item is the count field.` | The table is not fixed. |
| `count field option: CODE must be an elementary numeric item without decimals` | The count field is a group, text, or has decimals. |
| `count field option: N is already the count field of T1` | Two tables have the same count field. |
| `count field LATE must be in the same class, before the table` | The count field is after the table, or in another class. |
| `count field HUGE must be an integer` | The count field has more than 18 digits. |
| `count field N has 1 digits and cannot hold 20` | The count field is too small for the maximum. |
| `count field option: T is not in a generated class, so the count field has no effect` | The table is skipped, for example a `REDEFINES` of an elementary item. |

The generator suggests the option when a numeric item without decimals comes just before a fixed table and its name ends with `-COUNT`. The suggestion is an `info` diagnostic:

```
GET-INVOICE.cpy:4:12: info: INVOICE-LINE: INVOICE-LINE-COUNT can be the count field of this fixed table. To link them, set "countFields": {"INVOICE-LINE": "INVOICE-LINE-COUNT"} in the options file, or --count-field INVOICE-LINE=INVOICE-LINE-COUNT.
```

The suggestion does not change the classes. Set the option only if the program uses the field as the count.

### Empty entries of a fixed table

Without a count field, a fixed table finds its unused entries through LOW-VALUES. Many programs use spaces or `INITIALIZE` instead:

```cobol
       01  LIST-OPEN-INVOICES.
           05  INVOICE-LINE OCCURS 5 TIMES.
               10  INVOICE-NUMBER       PIC X(10).
               10  AMOUNT-DUE           PIC S9(7)V99 COMP-3.
```

The copybook does not tell how the program marks an unused entry. Thus, the generator does not guess it. Use these options:

```json
{
  "emptyItems": { "INVOICE-LINE": "SPACES" },
  "emptyKeys": { "INVOICE-LINE": "INVOICE-NUMBER" }
}
```

On the command line, use `--empty-item INVOICE-LINE=SPACES` and `--empty-key INVOICE-LINE=INVOICE-NUMBER`. The generator then writes `emptyItem` and `emptyKey` on the fixed collection:

```java
@BookCollection(order = 10, maxOccurs = 5, direction = Direction.INPUT_OUTPUT, fixed = true, emptyKey = "invoiceNumber", emptyItem = EmptyItem.SPACES)
private List<InvoiceLine> invoiceLine;
```

The codec then uses key mode, and writes spaces in the unused entries. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#key-mode).

Rules for the options:

- The key of each option is the name of a table with `OCCURS n` and without `DEPENDING ON`. A name can be qualified with `OF` or `IN`.
- The value of `emptyItems` is `SPACES` or `LOW-VALUES`. `emptyItems` is permitted with `countFields`: it then gives only the bytes of the unused entries.
- The value of `emptyKeys` is an elementary item of the entry. It is not in a `REDEFINES` area of the entry. A group around it is permitted.
- The key item maps to a text or a number field: not a date hint, and not a level 88 enum.
- A table with `emptyKeys` has no `countFields` entry.

| Message | Cause |
|---|---|
| `empty item option: item NOPE does not exist` | No item has this name. The position is `<file>:1:1`. The same messages apply to `empty key option`. |
| `empty item option: N has no OCCURS` | The table is not a table. |
| `empty item option: T has OCCURS DEPENDING ON. Remove the option: the DEPENDING ON item gives the number of items.` | The table is not fixed. |
| `empty item option: T has the unknown value ZEROS. Use SPACES or LOW-VALUES.` | An incorrect value of `emptyItems`. |
| `empty key option: K is not an elementary item of the entry of T` | The key is a group, is outside the entry, or is in a `REDEFINES` area. |
| `empty key option: K must map to a text or a number field` | The key has a date hint, or it maps to an enum. |
| `empty key option: T has a count field. Remove one of the two options.` | The table also has `countFields`. |
| `empty item options: T is not in a generated class, so the options have no effect` | The table is skipped, for example a `REDEFINES` of an elementary item. |

### REDEFINES of an elementary item

The generator keeps the first definition and skips the other item, with the warning `E2 redefines E1 and is skipped`. Two cases are errors:

- The redefining item is longer: `E3 redefines E1 and is longer than E1. BookMap keeps E1, so the items after it would move.`
- An elementary item redefines a group: `... is an elementary item that redefines the group ... BookMap does not support this.`

### A REDEFINES area in another REDEFINES area

BookMap cannot map a group `REDEFINES` area in an alternative of another area (see issues #38 and #39). The generator uses the rule of an elementary item: it keeps the redefined group, and it skips each group that redefines it, with a warning:

```
the REDEFINES area LIMITS is inside another REDEFINES area. BookMap keeps LIMITS and skips OTHER-LIMITS. See issues #38 and #39.
```

The bytes of the skipped group stay in the fields of the kept group. A skipped group that is longer than the kept group is an error: `C redefines A and is longer than A. BookMap keeps A, so the items after it would move.`

### Example

This copybook:

```cobol
      * GET-ORDER: READ ONE ORDER WITH ITS LINES.
       01  GET-ORDER.
           05  ORDER-ID                 PIC X(10).
           05  ORDER-TOTAL              PIC S9(7)V99 COMP-3.
           05  ORDER-LINE OCCURS 3 TIMES.
               10  PRODUCT-CODE         PIC X(12).
               10  QUANTITY             PIC 9(5).
```

gives two classes, `GetOrder` and `OrderLine`. This is `GetOrder`, shortened: without the `package` and `import` lines and the getters and setters. The generator writes each annotation member on its own line.

```java
/**
 * Generated by the BookMap copybook generator from {@code GET-ORDER.cpy}.
 */
@BookOperation
public class GetOrder {
    /**
     * {@code 05 ORDER-ID PIC X(10).}
     */
    @BookField(
            order = 10,
            pic = "X(10)"
    )
    private String orderId;

    /**
     * {@code 05 ORDER-TOTAL PIC S9(7)V99 COMP-3.}
     */
    @BookField(
            order = 20,
            pic = "S9(7)V99 COMP-3"
    )
    private BigDecimal orderTotal;

    /**
     * {@code 05 ORDER-LINE OCCURS 3 TIMES.}
     */
    @BookCollection(
            order = 30,
            maxOccurs = 3,
            direction = Direction.INPUT_OUTPUT,
            fixed = true
    )
    private List<OrderLine> orderLine;

    // getters and setters
}
```

### The generated classes

- Each class gets the Javadoc `Generated by the BookMap copybook generator from {@code GET-ORDER.cpy}.`
- Each field gets a Javadoc with its declaration, after `COPY REPLACING`, with single spaces and without `VALUE`.
- `@BookCollection` always has `direction`. `@BookField` has `direction` only if it is not `INPUT_OUTPUT`.
- A usage on a group shows in the `pic` of each item, for example `pic = "S9(5)V99 COMP-3"`. The Javadoc shows the declaration as written.
- The item classes have no class annotation.
- Without Lombok, each class gets a public getter and a public setter for each field.
- With Lombok, each class gets `@Data` and `@NoArgsConstructor`. A subclass also gets `@EqualsAndHashCode(callSuper = true)` and `@ToString(callSuper = true)`.

## Level 88 items

The level 88 items of an elementary item give an enum, or constants and boolean methods.

```cobol
05  PARTY-ROLE              PIC X(8).
    88  PARTY-IS-SELLER     VALUE 'SELLER'.
    88  PARTY-IS-BILL-TO    VALUE 'BILL-TO'.
    88  PARTY-IS-SHIP-TO    VALUE 'SHIP-TO'.
```

### The mode of an item

The option `conditions` sets the mode of an item, by its COBOL name:

| Mode | Result |
|---|---|
| `enum` | A Java enum, and a field of the enum type. |
| `constants` | A constant and a boolean method for each level 88 item. |
| `none` | Nothing. Each level 88 item gives an `info` diagnostic: `condition NOTE-A is ignored`. |

Without the option, the generator writes an enum. It writes constants in these cases, with an `info` diagnostic that tells why:

- The item is a `FILLER`.
- The item is the discriminator of a subtype decision, or the item of a `DEPENDING ON`. These items keep their Java type.
- The item has only one level 88 item, for example a flag with no opposite value.
- A level 88 item has a `THRU` range.
- A value is not valid for the item. See [Values](#values).
- Two level 88 items share a value.

```
ORDERS.cpy:7:12: info: MORE-DATA: the level 88 items give constants, not an enum, because it has only one level 88 item. Set the condition mode of MORE-DATA to change this.
```

With the mode `enum`, a range and a single level 88 item are permitted. A `FILLER`, a discriminator or count item, a value that is not valid, and a shared value are errors:

```
ORDER.cpy:2:1: error: DUP: the level 88 items cannot be an enum: the level 88 items DUP-A and DUP-B share a value
```

A name in `conditions` that is not an elementary item with level 88 items is an error, at the position `<file>:1:1`.

### Values

The generator converts each value to a value of the item:

| Item | Text literal | Number | `SPACE` | `ZERO` | Other figurative constants |
|---|---|---|---|---|---|
| Text (`PIC X`) | The text without the spaces on the right. It must not be longer than the item. | Not valid. | `""` | `0` in each position. | Not valid. |
| Number (`PIC 9`) | Not valid. | The number. It must fit the picture: digits, decimal places, and sign. | Not valid. | `0` | Not valid. |

The spaces on the right are not part of a text value, because the codec removes them on read. Thus, `'SELLER'` in a `PIC X(8)` item is the value `SELLER`.

### Enum mode

The generator writes the enum in the package of the classes. Each constant gets `@CobolValue` with the values and ranges of its level 88 item. See [Enum fields](Annotations-reference#enum-fields).

```java
/**
 * Generated by the BookMap copybook generator from {@code GET-INVOICE.cpy}.
 * The level 88 items of {@code PARTY-ROLE}.
 */
public enum PartyRole {
    /**
     * {@code PARTY-IS-SELLER}
     */
    @CobolValue("SELLER")
    SELLER,
    ...
}
```

```java
@BookField(order = 20, pic = "X(8)")
private PartyRole partyRole;
```

- The enum name comes from the item name, with the rules of a class name. A name that a class or an enum already has gets the owner class name as a prefix, then a number.
- The constant names are the names of the level 88 items without their longest common prefix. `PARTY-IS-SELLER`, `PARTY-IS-BILL-TO`, and `PARTY-IS-SHIP-TO` give `SELLER`, `BILL_TO`, and `SHIP_TO`.
- The generator keeps the full names if a short name starts with a digit or is not unique.
- An unknown value causes a `MappingException` on read. Add a `@CobolUnknown` constant by hand in scaffold mode if the program can send other values.

### Constants mode

Each level 88 item gives a constant with its first value, and a boolean method that tests all its values and ranges:

```cobol
05  PARTY-STATUS            PIC 9(2).
    88  PARTY-ACTIVE        VALUE 1 THRU 5.
    88  PARTY-CLOSED        VALUE 9.
```

```java
/**
 * The first value of {@code PARTY-ACTIVE}.
 */
public static final int PARTY_ACTIVE = 1;

/**
 * Tells if {@code partyStatus} holds a value of {@code PARTY-ACTIVE}.
 *
 * @return {@code true} for a value of the level 88 item
 */
public boolean isPartyActive() {
    return partyStatus >= 1 && partyStatus <= 5;
}
```

- The type of the constant is the Java type of the field: `String`, `int`, `long`, or `BigDecimal`.
- The names come from the name of the level 88 item, with `stripPrefixes`. A name that is already in the class gets a number.
- A level 88 item with a value that is not valid gets no constant, and a warning.

## Initial values

The `VALUE` clause of an elementary item gives the initial value of the field. The rules of [Values](#values) apply.

| COBOL | Java |
|---|---|
| `PIC X(3) VALUE 'EUR'` | `private String currencyCode = "EUR";` |
| `PIC X(30) VALUE SPACES` | `private String partyName = "";` |
| `PIC 9(2) VALUE 1` | `private int partyStatus = 1;` |
| `PIC S9(7)V99 COMP-3 VALUE 1000.50` | `private BigDecimal creditLimit = new BigDecimal("1000.50");` |
| `PIC X(8) VALUE 'SELLER'`, in the enum mode | `private PartyRole partyRole = PartyRole.SELLER;` |

A value that is not valid gives a warning, and the field gets no initial value:

```
ORDER.cpy:2:1: warning: the VALUE 'ABC' of A is longer than 2 characters. The field has no initial value.
```

In the enum mode, a value that no level 88 item contains gives a warning: `the VALUE of CODE is not a value of a level 88 item. The field has no initial value.`

## Directions

The copybook does not give the direction of a field. Thus, all fields are `INPUT_OUTPUT`, with one exception: the alternatives of a `REDEFINES` area.

In BookMap, an `INPUT` field does not occupy bytes in the output area.
Outside a `REDEFINES` area, a direction would move the offsets of the fields after it. The offsets would not agree with COBOL.
Inside a `REDEFINES` area, the input alternative and the output alternative share the same bytes. This is a **direction split**:

```cobol
01  GET-INVOICE.
    05  RETURN-CODE          PIC X(4).
    05  INVOICE-REQUEST.
        10  INVOICE-ID       PIC X(12).
    05  INVOICE-RESPONSE REDEFINES INVOICE-REQUEST.
        10  INVOICE-AMOUNT   PIC S9(7)V99 COMP-3.
        10  DUE-DATE         PIC 9(8).
    05  TRAILER              PIC X(10).
```

The generator gives these fields:

| Field | `pic` | Direction | `order` |
|---|---|---|---|
| `returnCode` | `X(4)` | `INPUT_OUTPUT` | 10 |
| `invoiceId` | `X(12)` | `INPUT` | 20 |
| `filler1` | `X(1)` | `INPUT` | 30 |
| `invoiceAmount` | `S9(7)V99 COMP-3` | `OUTPUT` | 40 |
| `dueDate` | `9(8)` | `OUTPUT` | 50 |
| `trailer` | `X(10)` | `INPUT_OUTPUT` | 60 |

The request alternative is 1 byte shorter. The generator adds a 1-byte `INPUT` filler, so `trailer` has the COBOL offset in both directions.
The Javadoc of the filler is `FILLER PIC X(1). Added to give INVOICE-REQUEST the length of INVOICE-RESPONSE.`

The generator decides what to do with a `REDEFINES` area in this order:

1. A subtype decision. The area becomes subtypes. The generator ignores the directions and the markers of the area (next section).
2. A direction decision (`--direction` or `directions`).
3. A marker in the group name. The default input markers are `INPUT`, `IN`, `REQUEST`, and `REQ`. The default output markers are `OUTPUT`, `OUT`, `RESPONSE`, and `RESP`.

A marker must be equal to one full part of the name, between hyphens. Thus, `REQUEST` marks `INVOICE-REQUEST`, but not `INVOICEREQUEST`. The markers are case-sensitive: write them in upper case.

If none applies, the generator does not guess. It reports an error with an example of each decision:

```
QUERY-INVOICE.cpy:3:12: error: REDEFINES area INVOICE-QUERY at line 3 needs a decision. Add a direction for each alternative, for example "directions": {"INVOICE-QUERY": "INPUT", "INVOICE-DETAIL": "OUTPUT"}. Or add a subtype decision, for example "subTypes": {"INVOICE-QUERY": {"discriminator": "...", "values": {"INVOICE-QUERY": "01", "INVOICE-DETAIL": "02"}}}.
```

Rules for a direction split:

- A direction split needs exactly two alternatives: one `INPUT` and one `OUTPUT`. For three or more alternatives, use subtypes.
- A name with an input marker and an output marker gives a warning: `INVOICE-RESPONSE has an input marker and an output marker. Its direction is not clear.`
- A split is an error inside an `OCCURS`, with `OCCURS DEPENDING ON` in an alternative, or with `OCCURS` on an alternative.
- Set the decision on the alternative group. A decision on an item inside it is an error: `direction decision: INVOICE-ID is inside a REDEFINES alternative. Set the decision on the alternative group.`
- A decision on an item outside a `REDEFINES` area is an error: `direction decision: RETURN-CODE is not a REDEFINES alternative. ...`

## Subtypes

A subtype decision maps a `REDEFINES` area to [subtypes](Subtypes-REDEFINES). It gives the discriminator and the value of each alternative.
The class that holds the area gets `@BookSubTypes`. Each alternative becomes a subclass.

```json
{
  "subTypes": {
    "CARD-SUMMARY": {
      "discriminator": "ACCOUNT-KIND",
      "values": { "CARD-SUMMARY": "C", "LOAN-SUMMARY": "L" },
      "classNames": { "CARD-SUMMARY": "CardAccount", "LOAN-SUMMARY": "LoanAccount" }
    }
  }
}
```

| Key | Meaning |
|---|---|
| The map key | The name of one alternative group of the `REDEFINES` area. Give one decision for each area. |
| `discriminator` | The name of the discriminator item. |
| `values` | The discriminator value of each alternative. |
| `classNames` | Optional. The class name of an alternative. Default: the class name from the name of the alternative. |

Rules for subtypes:

- The discriminator must be an item before the area, in the same class. It must be text or an integer.
- Give a value for each alternative. Otherwise, the error is `the REDEFINES alternative SAVING-DATA has no discriminator value`.
- The area must be at the end of the class: `TRAILER comes after the REDEFINES area CARD-DATA. BookMap needs the redefined area at the end of the layout.`
- `@BookSubTypes(discriminator = ...)` contains the Java field name, for example `accountKind`.
- Each subclass extends the class that holds the area. Its `order` values continue after the base class.

With the example options above, `GET-ACCOUNT-SUMMARY.cpy` (in `bookmap-examples`) gives this base class:

```java
@BookOperation
@BookSubTypes(
        discriminator = "accountKind",
        value = {
                @BookSubTypes.Type(value = "C", type = CardAccount.class),
                @BookSubTypes.Type(value = "L", type = LoanAccount.class)
        }
)
public class GetAccountSummary {
```

## Date hints

The copybook does not tell if a `PIC 9(8)` item is a date. Thus, the generator does not guess. Without a hint, the item is an `int`.
A date hint maps an item to a `DateTimeFormatter` pattern. The field becomes a date or time field, with `pattern` in `@BookField`.

```json
{
  "dates": {
    "ORDER-DATE": "yyyyMMdd",
    "SHIP-TIME OF SHIPMENT-ENTRY": "HH:mm:ss",
    "*-DATE": "yyyyMMdd",
    "LAST-UPDATE": "yyyyMMddHHmmss"
  }
}
```

The key is an item name or a name pattern:

- An item name is a bare or qualified name, as in the other options. It must match one elementary item.
- A key with `*` is a name pattern. `*` matches zero or more characters. A name pattern is one word, without `OF` or `IN`. It applies to all elementary items with a matching name, also in item classes. It does not apply to `FILLER`.
- The match is not case-sensitive.
- An item name has priority over a name pattern.

The pattern gives the Java type. The pattern rules are the same as for `@BookField` (see [Dates and times](PIC-syntax-and-type-mapping#dates-and-times)).

| Pattern | Java type |
|---|---|
| `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `yyyy-MM-dd` | `LocalDate` |
| `HHmmss`, `HH:mm:ss` | `LocalTime` |
| `yyyyMMddHHmmss` | `LocalDateTime` |

For `"*-DATE": "yyyyMMdd"`, the item `10 SHIP-DATE PIC 9(8).` gives this field:

```java
/**
 * {@code 10 SHIP-DATE PIC 9(8).}
 */
@BookField(
        order = 20,
        pic = "9(8)",
        pattern = "yyyyMMdd"
)
private LocalDate shipDate;
```

A date hint also applies to a `COMP-3` item (see [Packed dates](PIC-syntax-and-type-mapping#packed-dates)). The item can be signed. The pattern must give digits only. For `"ORDER-DATE": "yyyyMMdd"`, the item `05 ORDER-DATE PIC S9(8) COMP-3.` gives a `LocalDate` field with `pic = "S9(8) COMP-3"` and `pattern = "yyyyMMdd"`.

The generator checks each hint against the item. These errors have the line of the item:

| Message | Cause |
|---|---|
| `D: a date or time field must have the usage DISPLAY or COMP-3` | The item is `COMP`, `COMP-4`, or `COMP-5`. |
| `D: a DISPLAY date or time field must not have S or V in the picture` | The `DISPLAY` picture is signed or has decimals. |
| `D: a COMP-3 date or time field must not have V in the picture` | The `COMP-3` picture has decimals. |
| `D: the date pattern 'yyyy-MM-dd' gives characters that are not digits, but a COMP-3 item holds only digits` | The item is `COMP-3`, and the pattern gives other characters than digits. |
| `D: the date pattern 'yyyyMMdd' gives 8 characters, but the picture 9(6) has 6` | The length of the pattern does not agree with the picture. |
| `D: the date pattern 'd.M.yyyy' gives text of variable length` | The pattern does not give a fixed length. |
| `D: the date pattern 'MMdd' does not fit LocalDateTime, LocalDate, or LocalTime` | The pattern gives no complete date or time. |
| `D: the date pattern 'x{' is not valid: ...` | The pattern has an incorrect symbol. |
| `D matches the date hints *-DATE, ORDER-* with different patterns. Add a date hint for D.` | Two name patterns match the item and give different patterns. |

The generator also reports each hint that matches nothing, with the position `<file>:1:1`:

| Message | Severity |
|---|---|
| `date hint: item NOPE does not exist` | error |
| `date hint: item CITY is ambiguous. Qualify it: ...` | error |
| `date hint: BILLING is a group. A date hint applies to an elementary item.` | error |
| `date hint: ORDER-TEXT gives no field. The generator skips this item.` | error |
| `date hint: the name pattern '*-DATE OF HEADER' must be one word, without OF or IN` | error |
| `date hint: the name pattern *-DTE matches no field` | warning |

A name pattern that matches nothing is only a warning. Thus, you can use the same name patterns in the options files of all copybooks.

A date field cannot be a discriminator or a `DEPENDING ON` count field.

## Continuable and Validatable

The options `continuable` and `validatable` make an operation class implement [`Continuable` or `Validatable`](Collections-and-fixed-OCCURS#pagination).
The key is the name of a record. The value is the name of a class that you write: the handler.
A handler name without a package is a class in the package of the generated classes.

```json
{
  "continuable": { "LIST-SHIPMENTS": "com.example.shipments.ShipmentPaging" },
  "validatable": { "LIST-SHIPMENTS": "com.example.shipments.ReturnCodes" }
}
```

The generated class implements the interfaces. Each method calls a static method of the handler with the same name:

```java
@BookOperation
public class ListShipments implements Continuable, Validatable {
    // fields, getters, and setters

    /**
     * Calls {@code ShipmentPaging.hasContinuation(this)}.
     */
    @Override
    public boolean hasContinuation() {
        return ShipmentPaging.hasContinuation(this);
    }

    /**
     * Calls {@code ReturnCodes.validate(this)}.
     */
    @Override
    public void validate() {
        ReturnCodes.validate(this);
    }
}
```

Write the handler in your project:

```java
public final class ShipmentPaging {

    private ShipmentPaging() {
    }

    public static boolean hasContinuation(ListShipments page) {
        return "Y".equals(page.getMoreData());
    }
}

public final class ReturnCodes {

    private ReturnCodes() {
    }

    public static void validate(ListShipments page) {
        if (!"00".equals(page.getReturnCode())) {
            throw new BusinessException("the program returned " + page.getReturnCode());
        }
    }
}
```

- Use these options in build mode. You cannot edit the generated classes, but you can write the handler.
- One handler class can serve more operations. Write one method for each operation class.
- The compiler checks the handler. A missing class or method is a compile error.

BookMap does not permit an `INPUT_OUTPUT` collection in a `Continuable` class.
For a `Continuable` record, each `INPUT_OUTPUT` collection is an error, with the line of the collection:

```
ORDERS.cpy:9:12: error: ORDER-ENTRY is an INPUT_OUTPUT collection. BookMap does not permit it in a Continuable class. Put the table in the output alternative of a REDEFINES area.
```

Put the table in the output alternative of a [direction split](#directions). The table then gets the direction `OUTPUT`:

```cobol
01  LIST-SHIPMENTS.
    05  RETURN-CODE              PIC X(2).
    05  RESTART-KEY              PIC X(12).
    05  SHIPMENT-REQUEST.
        10  CUSTOMER-ID          PIC X(10).
    05  SHIPMENT-RESPONSE REDEFINES SHIPMENT-REQUEST.
        10  MORE-DATA            PIC X.
        10  SHIPMENT-ENTRY OCCURS 2 TIMES.
            15  SHIPMENT-ID      PIC X(12).
            15  SHIP-DATE        PIC 9(8).
```

Other errors, with the position `<file>:1:1`:

- `continuable: LIST-ORDRS is not a record. Use the name of a level 01 item.`
- `validatable: 'Return Codes' is not a Java class name`

## Model JSON

The `parse` command writes the model as JSON. The `generate` command reads it again.

- The root keys are `formatVersion` (now `1`), `sourceName`, `classes`, and the optional `enums`.
- A class has `name`, `kind` (`OPERATION`, `ITEM`, or `SUBTYPE`), an optional `superclass`, optional `subTypes` (`discriminator`, and `types` with `value` and `className`), and `members`.
- A class can also have `continuable` and `validatable`: the handler class names.
- A field member has `kind: FIELD`, `name`, `javaType` (`STRING`, `INT`, `LONG`, `BIG_DECIMAL`, `ENUM`, `LOCAL_DATE`, `LOCAL_TIME`, or `LOCAL_DATE_TIME`), `order`, `pic`, `direction`, an optional `pattern`, `cobolName`, and `declaration`.
- A field member can also have `enumType` (only with `javaType: ENUM`), `initialValue`, and `conditions`. A condition has `cobolName`, `constant`, `method`, and `values`.
- For a date or time field, `initialValue` is an ISO-8601 text, for example `2026-01-01`. The mapper does not write it, because it ignores the `VALUE` of an item with a date hint.
- An enum has `name`, `cobolName`, and `constants`. A constant has `name`, `cobolName`, and `values`.
- A value has `from`, and `thru` for a range.
- The writer writes the optional keys only when they have a value. Thus, a model without level 88 items and `VALUE` clauses does not change.
- A collection member has `kind: COLLECTION`, `name`, `order`, `direction`, `maxOccurs`, `fixed`, an optional `dependsOn`, `itemClass`, `cobolName`, and `declaration`. A fixed collection can also have `emptyItem` (`SPACES`) and `emptyKey` (the Java name of the key field). The generator writes them only if they are set. A former generator does not read a model with these keys: it gives the error `unknown key`.
- The reader rejects an unknown key, and another `formatVersion`: `unsupported formatVersion 2. This reader supports 1.`
- The generator does not check a model that you edit by hand. It writes the classes as the model gives them.

```json
{
  "formatVersion" : 1,
  "sourceName" : "GET-ORDER.cpy",
  "classes" : [ {
    "name" : "GetOrder",
    "kind" : "OPERATION",
    "members" : [ {
      "kind" : "FIELD",
      "name" : "orderId",
      "javaType" : "STRING",
      "order" : 10,
      "pic" : "X(10)",
      "direction" : "INPUT_OUTPUT",
      "cobolName" : "ORDER-ID",
      "declaration" : "05 ORDER-ID PIC X(10)."
    }, {
      "kind" : "COLLECTION",
      "name" : "orderLine",
      "order" : 30,
      "direction" : "INPUT_OUTPUT",
      "maxOccurs" : 3,
      "fixed" : true,
      "itemClass" : "OrderLine",
      "cobolName" : "ORDER-LINE",
      "declaration" : "05 ORDER-LINE OCCURS 3 TIMES."
    } ]
  } ]
}
```

This excerpt is shortened: the real model also has the field `orderTotal` and the class `OrderLine`.

## Diagnostics

Each diagnostic has the format `<file>:<line>:<column>: <severity>: <message>`:

```
GET-INVOICE.cpy:5:12: error: copy member 'ADDRESS' not found: no copy directory is set
```

The severity is `error`, `warning`, or `info`. Only an `error` stops the generation.

- The generator reports all problems of one step before it stops.
- If the parse step has errors, the mapping step does not run. Correct the parse errors first, then run the generator again. The mapping errors can then show.
- An error in a direction decision, a subtype decision, or a condition mode has the position `<file>:1:1`. A name in the option `countFields` that the generator cannot find has the same position.

### Record length

The map step gives an `info` diagnostic with the length of each record, in bytes:

```
GET-INVOICE.cpy:1:8: info: record GET-INVOICE has 35556 bytes
```

The length is the length of the encoded record. A `REDEFINES` area counts with its longest alternative.
With `OCCURS DEPENDING ON`, the length is the maximum length, and the message says `at most`.

A transport can have a size limit. For example, the COMMAREA of `CtgTransport` holds 32,500 bytes at most. The generator does not know the transport, so it does not check a limit by default.
Set `maxRecordLength` to get a warning when a record is longer:

```
GET-INVOICE.cpy:1:8: warning: record GET-INVOICE has 35556 bytes, more than the maximum record length of 32500 bytes. The largest tables: INVOICE-LINE (100 x 298 = 29800 bytes).
```

The warning names the three largest tables (`OCCURS` items) of the record, because a table is usually the cause.
The warning does not stop the generation. To make the record shorter, decrease the number of occurrences, or split the operation into more calls.

## Sign clause

A `SIGN` clause on a group applies to all signed numeric `DISPLAY` items of the group, also in the groups below it.
The clause of an item replaces the clause of its group.
The group clause does not change the other items, for example `PIC X`, `PIC 9`, or `COMP-3` items.

```cobol
05 AMOUNTS SIGN IS LEADING SEPARATE CHARACTER.
   10 NET-AMOUNT PIC S9(7)V99.
   10 TAX-AMOUNT PIC S9(5)V99 SIGN TRAILING.
   10 LINE-COUNT PIC 9(3).
```

```java
@BookField(order = 10, pic = "S9(7)V99 SIGN LEADING SEPARATE")
private BigDecimal netAmount;
@BookField(order = 20, pic = "S9(5)V99")
private BigDecimal taxAmount;
@BookField(order = 30, pic = "9(3)")
private int lineCount;
```

A separate sign adds one byte to the item. The record length, the `SYNC` slack bytes, and the `REDEFINES` sizes include this byte.
A `SIGN` clause on an item that is not a signed numeric `DISPLAY` item is an error, for example `SIGN on LINE-COUNT needs a signed numeric DISPLAY item`.

### Edited pictures

An item with a numeric edited picture becomes a number field. The Java type follows the digits, like the other numbers: `int`, `long`, or `BigDecimal`.
The `pic` of the field is the picture of the copybook. See [Edited pictures](PIC-syntax-and-type-mapping#edited-pictures) for the rules of the codec.

```cobol
       05  INV-LINES           PIC ZZ9.
       05  INV-TOTAL           PIC $Z,ZZ9.99CR.
       05  INV-DISCOUNT        PIC 9(3)V99 BLANK WHEN ZERO.
```

```java
@BookField(order = 10, pic = "ZZ9")
private int invLines;

@BookField(order = 20, pic = "$Z,ZZ9.99CR")
private BigDecimal invTotal;

@BookField(order = 30, pic = "9(3)V99 BLANK WHEN ZERO")
private BigDecimal invDiscount;
```

- The item must have the usage `DISPLAY`. A `COMP` or `COMP-3` usage, also from a group, is an error: `INV-TOTAL has an edited picture and must have usage DISPLAY`.
- The `SIGN` clause of a group does not apply to an edited item. The picture has its own sign.
- The `VALUE` and the level 88 items of an edited item are ignored, with an `info` diagnostic, for example `condition INV-NONE is ignored, because INV-TOTAL has an edited picture`.
- A date hint on an edited item is an error: `INV-DATE has an edited picture. A date hint needs PIC 9(n) or PIC X(n).`
- An edited item cannot be the count field of a table: `DEPENDING ON item LINE-COUNT must not have an edited picture`.
- A comma in a picture is part of the picture. A comma is a separator only before a space.

## Limitations of the generator

- The generator does not detect dates. A `PIC 9(8)` date stays an `int`, unless a [date hint](#date-hints) names it.
- A packed date with a century digit that changes, for example `CYYMMDD`, cannot get a date hint. Write a `FieldConverter` for it.
- A fixed `OCCURS` of `COMP` items without `dependsOn` and without `emptyKey` cannot write the value 0: its bytes are LOW-VALUES. The generator gives an `info` diagnostic: `T4: a COMP value of 0 is all LOW-VALUES and cannot be written. Link the count field with the option countFields, or the key item with the option emptyKeys, if the program has one.`
- These items are errors: `COMP-1` and `COMP-2` (#46), `NATIONAL` and `N` (#49), alphanumeric edited pictures, `POINTER`, `P`, `A`, and an `OCCURS` in an `OCCURS` (#43).
- The generator skips the inner alternatives of a `REDEFINES` area in another one (#38, #39).
- `SYNC` in an `OCCURS` is an error if the item needs slack bytes.
- BookMap writes a `COMP-5` value only if it has the digits of the picture.

The [Roadmap](Roadmap) lists the plan for these limitations.
