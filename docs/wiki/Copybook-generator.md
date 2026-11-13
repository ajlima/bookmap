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
  "countFields": { "INVOICE-LINE": "INVOICE-LINE-COUNT" },
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
| `countFields` | map | The count field of each fixed table: the table name, then the name of the count field. See [Count field of a fixed table](#count-field-of-a-fixed-table). | empty |
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

- Levels 01 to 49 and 88 are permitted. Each level `01` gives one class with `@BookOperation`.
- One copybook can have more than one level `01`. Each one gives its own class.
- If the first item is not level `01`, the generator adds a record. The record name comes from `recordName`.
- Level 66 and level 77 are errors: `level 66 is not supported by BookMap`. Another level is an error, for example `level 50 is not valid`.
- A level `01` with `REDEFINES` gives a warning: `record OTHER-REC redefines record MAIN-REC. Both records are kept.`
- A group with a `PIC` is an error: `GRP is a group and must not have a PIC`. An elementary item without a `PIC` is an error: `NOPIC has no PIC`.
- A level 88 must follow an item: `a level 88 condition needs an item before it`.

### Clauses

| Clause | Result |
|---|---|
| `PIC` or `PICTURE [IS]` with `X`, `9`, `S` (first), `V`, and repeat counts | Accepted. |
| `USAGE [IS]` with `DISPLAY`, `COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY`, `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` | Accepted. A usage on a group applies to its items. |
| `OCCURS n [TIMES]` | Accepted: a fixed collection. |
| `OCCURS m TO n [TIMES] DEPENDING [ON] name [OF\|IN group]` | Accepted: a variable collection. |
| `REDEFINES` | Accepted. See [Directions](#directions) and [Subtypes](#subtypes). |
| `SIGN [IS] TRAILING`, `TRAILING` | Accepted. It is the default sign of BookMap. |
| `VALUE`, `VALUES` | Ignored, without a diagnostic. |
| Level 88 | Ignored, with an `info` diagnostic: `condition CARD-ACCOUNT is ignored`. |
| `INDEXED BY`, `ASCENDING KEY`, `DESCENDING KEY` | Ignored, with an `info` diagnostic, for example `INDEXED is ignored`. |
| `LEADING`, `SEPARATE`, `SYNC`, `SYNCHRONIZED`, `JUST`, `JUSTIFIED`, `BLANK`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL` | Error, for example `LEADING is not supported by BookMap`. |
| Edited pictures with `Z * , . + - B 0 / CR DB` | Error, for example `edited picture ZZ9 is not supported by BookMap`. |
| Pictures with `P`, `A`, or `N` | Error, for example `picture symbol A is not supported by BookMap`. |
| A picture with an incorrect form, for example `X9` | Error: `picture X9 is not valid`. |
| Another word | Error, for example `unknown clause FOO`. |

Other errors:

- `A12 has a text picture and must not have COMP or COMP-3`
- `A13: COMP supports at most 18 digits`

The diagnostic of a level 88 gives the line of its parent item, not the line of the level 88.

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
- The `REPLACE` statement is an error: `the REPLACE statement is not supported by BookMap`.

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
| Level `88`, `VALUE` | Ignored. A level 88 gives an `info` diagnostic. |

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9(n)` with 1 to 9 digits | `int` |
| `9(n)` with 10 to 18 digits | `long` |
| Decimals, or more than 18 digits | `BigDecimal` |

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
| `OCCURS T4B is inside OCCURS T4. BookMap does not support nested collections.` | A table in a table. |

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

### REDEFINES of an elementary item

The generator keeps the first definition and skips the other item, with the warning `E2 redefines E1 and is skipped`. Two cases are errors:

- The redefining item is longer: `E3 redefines E1 and is longer than E1. BookMap keeps E1, so the items after it would move.`
- An elementary item redefines a group: `... is an elementary item that redefines the group ... BookMap does not support this.`

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

## Model JSON

The `parse` command writes the model as JSON. The `generate` command reads it again.

- The root keys are `formatVersion` (now `1`), `sourceName`, and `classes`.
- A class has `name`, `kind` (`OPERATION`, `ITEM`, or `SUBTYPE`), an optional `superclass`, optional `subTypes` (`discriminator`, and `types` with `value` and `className`), and `members`.
- A field member has `kind: FIELD`, `name`, `javaType` (`STRING`, `INT`, `LONG`, or `BIG_DECIMAL`), `order`, `pic`, `direction`, `cobolName`, and `declaration`.
- A collection member has `kind: COLLECTION`, `name`, `order`, `direction`, `maxOccurs`, `fixed`, an optional `dependsOn`, `itemClass`, `cobolName`, and `declaration`.
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
- An error in a direction decision or a subtype decision has the position `<file>:1:1`. A name in the option `countFields` that the generator cannot find has the same position.

## Limitations of the generator

- The generator does not detect dates. A `PIC 9(8)` date stays an `int`.
- The generated operation classes do not implement `Continuable` or `Validatable`. In scaffold mode, add them by hand.
- A fixed `OCCURS` of `COMP` items without `dependsOn` cannot write the value 0: its bytes are LOW-VALUES. The generator gives an `info` diagnostic: `T4: a COMP value of 0 is all LOW-VALUES and cannot be written. Link the count field with the option countFields if the program has one.`
- These items are errors: level `66` and `77`, `SIGN LEADING`, `SEPARATE`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL`, edited pictures, `P`, `A`, `N`, `REPLACE`, an `OCCURS` in an `OCCURS`, and a `REDEFINES` area in another one.

The [Roadmap](Roadmap) lists the plan for these limitations.
