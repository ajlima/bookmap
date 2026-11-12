# Copybook generator

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
  "package": "com.example.accounts",
  "lombok": false
}
```

All keys are optional. An unknown key is an error.

| Key | Type | Meaning | Default |
|---|---|---|---|
| `format` | `"fixed"` or `"free"` | The reference format. `fixed` reads the columns 7 to 72. | `fixed` |
| `charset` | text | The charset of the copybook files. | `UTF-8` |
| `copyDirs` | list of paths | The directories with the `COPY` members, in search order. A relative path starts at the directory of the options file. | empty |
| `recordName` | text | The record name for a copybook without level `01`. | The file name. |
| `stripPrefixes` | list of texts | The prefixes to remove from names, for example `WS-`. | empty |
| `inputMarkers` | list of texts | The name parts that mark an input alternative. The list replaces the default list. | `INPUT`, `IN`, `REQUEST`, `REQ` |
| `outputMarkers` | list of texts | The name parts that mark an output alternative. The list replaces the default list. | `OUTPUT`, `OUT`, `RESPONSE`, `RESP` |
| `directions` | map | The direction (`INPUT` or `OUTPUT`) of each `REDEFINES` alternative. See [Directions](#directions). | empty |
| `subTypes` | map | The subtype decisions. See [Subtypes](#subtypes). | empty |
| `package` | text | The Java package of the classes. | The tool gives it. |
| `lombok` | boolean | Write Lombok annotations instead of getters and setters. | `false` |

A name in an option can be qualified, for example `CITY OF BILLING-ADDRESS`. A name that matches more than one item is an error.

## COPY members

The parser expands each `COPY` statement with the member from a copy directory. `COPY ... REPLACING` is supported.

- The tools do not search for members. Declare the copy directories (`copyDirs`, or `--copy-dir`).
- The search in each directory is not recursive. A member in a subdirectory of a copy directory is not found.
- For `COPY ADDRESS.`, the parser looks in each directory for a file `ADDRESS`, `ADDRESS.cbl`, `ADDRESS.cob`, or `ADDRESS.cpy`. The case of the name is not important.
- If one directory has more than one of these files, the first name in alphabetical order wins. Keep only one file for each member.
- The first directory that has the member wins. Thus, the order of the directories is important.
- If no directory has the member, the generator reports an error, for example `copy member 'ADDRESS' not found in /work/src/main/copylib`.

## Mapping

| COBOL | Java |
|---|---|
| Level `01` | A class with `@BookOperation`. |
| A group without `OCCURS` | No class. Its items go into the class of the parent. |
| `OCCURS n` | `@BookCollection(fixed = true)` and an item class. |
| `OCCURS m TO n DEPENDING ON X` | `@BookCollection(dependsOn = "x")` and an item class. |
| An elementary item with `OCCURS` | An item class with one field `value`. |
| An elementary item | A `@BookField` with `pic`, for example `pic = "S9(7)V99 COMP-3"`. |
| `FILLER` | A field `filler1`, `filler2`, ... |
| `REDEFINES` of an elementary item | The first definition. The other item is skipped, with a warning. |
| `REDEFINES` of a group | A direction split or subtypes. See the next sections. |
| Level `88`, `VALUE` | Ignored. |

| Picture | Java type |
|---|---|
| `X(n)` | `String` |
| `9(n)` with 1 to 9 digits | `int` |
| `9(n)` with 10 to 18 digits | `long` |
| Decimals, or more than 18 digits | `BigDecimal` |

Names: `GET-CUSTOMER-ORDERS` gives the class `GetCustomerOrders`, and `ORDER-AMOUNT` gives the field `orderAmount`.
A Java keyword gets the suffix `Value`. If two items in one class have the same name, the second name starts with its group name.
The `order` values are 10, 20, 30, ... in each class. Each field has a Javadoc comment with its COBOL declaration.

A fixed `OCCURS` has no `dependsOn`, because the copybook does not tell which field is the count.
Add `dependsOn` by hand if the program has a count field. See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#fixed-collections).

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

gives two classes, `GetOrder` and `OrderLine`. This is `GetOrder`, without the getters and setters:

```java
/**
 * Generated by the BookMap copybook generator from {@code GET-ORDER.cpy}.
 */
@BookOperation
public class GetOrder {
    /**
     * {@code 05 ORDER-ID PIC X(10).}
     */
    @BookField(order = 10, pic = "X(10)")
    private String orderId;

    /**
     * {@code 05 ORDER-TOTAL PIC S9(7)V99 COMP-3.}
     */
    @BookField(order = 20, pic = "S9(7)V99 COMP-3")
    private BigDecimal orderTotal;

    /**
     * {@code 05 ORDER-LINE OCCURS 3 TIMES.}
     */
    @BookCollection(order = 30, maxOccurs = 3, direction = Direction.INPUT_OUTPUT, fixed = true)
    private List<OrderLine> orderLine;

    // getters and setters
}
```

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

`invoiceId` is `INPUT`. `invoiceAmount` and `dueDate` are `OUTPUT`. `returnCode` and `trailer` are `INPUT_OUTPUT`.
The request alternative is 1 byte shorter. The generator adds a 1-byte `INPUT` filler, so `trailer` has the COBOL offset in both directions.

The generator finds the direction of an alternative in this order:

1. A direction decision (`--direction` or `directions`).
2. A marker in the group name. The default input markers are `INPUT`, `IN`, `REQUEST`, and `REQ`. The default output markers are `OUTPUT`, `OUT`, `RESPONSE`, and `RESP`.
3. A subtype decision makes subtypes instead (next section).

If none applies, the generator does not guess. It reports an error with an example of each decision.

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

## Diagnostics

The generator reports all problems of a copybook before it stops. Each diagnostic has the format `<file>:<line>:<column>: <severity>: <message>`:

```
GET-INVOICE.cpy:5:12: error: copy member 'ADDRESS' not found: no copy directory is set
```

The severity is `error`, `warning`, or `info`. Only an `error` stops the generation.

## Limitations of the generator

- The generator does not detect dates. A `PIC 9(8)` date stays an `int`.
- The generated operation classes do not implement `Continuable` or `Validatable`. In scaffold mode, add them by hand.
- A fixed `OCCURS` of `COMP` items without `dependsOn` cannot write the value 0: its bytes are LOW-VALUES. The generator gives a note.
- These items are errors: level `66` and `77`, `SIGN LEADING`, `SEPARATE`, `SYNC`, `JUSTIFIED`, `BLANK WHEN ZERO`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL`, edited pictures, `P`, `A`, `N`, `REPLACE`, an `OCCURS` in an `OCCURS`, and a `REDEFINES` area in another one.

The [Roadmap](Roadmap) lists the plan for these limitations.
