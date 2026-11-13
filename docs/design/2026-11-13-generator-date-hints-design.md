# Copybook generator: date hints and optional Continuable/Validatable (design)

Date: 2026-11-13
Status: proposed.
Issue: [#35](https://github.com/ajlima/bookmap/issues/35)
Parent spec: `2026-11-11-bookmap-copybook-design.md`

## 1. Intent

The copybook generator writes a `PIC 9(8)` date as an `int`. The copybook does not tell if the item is a date, and the generator must not guess (parent spec, section 3.3).
The generated operation classes also do not implement `Continuable` or `Validatable`.
In scaffold mode, the developer adds these changes by hand. In build mode, the developer cannot edit the generated classes. Thus, build mode has no dates, no pagination, and no validation.

This design adds three explicit options:

- `dates`: map an item, or a name pattern, to a date or time pattern. The field becomes a `LocalDate`, `LocalTime`, or `LocalDateTime`.
- `continuable`: make a record class implement `Continuable`. The method calls a static method of a class that the developer writes.
- `validatable`: the same for `Validatable`.

## 2. Goals, non-goals, and anti-goals

Goals:

- A date field in the generated class, with the `pattern` of `@BookField`, from an explicit hint.
- A diagnostic for each hint that matches nothing.
- The generator checks the hint against the picture, and gives the copybook line in the message.
- A `Continuable` or `Validatable` operation class in build mode, without an edit of the generated code.
- No reflection at runtime. The generated code calls the developer code directly.
- The same options in the options file and on the command line. The build plugins read the options file, so they need no change.

Non-goals:

- Detection of dates from names or pictures.
- Packed dates (`S9(7) COMP-3`). The core has no built-in support (issue #50).
- Converters (`converter = ...`) from the options.
- Logic in the options, for example "continue while `MORE-DATA` is `Y`". The developer writes the logic in Java.

Anti-goals:

- A hint that changes nothing without a diagnostic.
- Generated code that does not compile with the processor because of a hint. The generator checks the BookMap rules that it can check, with the copybook line.

## 3. Date hints

### 3.1 Option

```json
{
  "dates": {
    "ORDER-DATE": "yyyyMMdd",
    "SHIP-TIME OF SHIPMENT-ENTRY": "HHmmss",
    "*-DATE": "yyyyMMdd",
    "LAST-UPDATE": "yyyyMMddHHmmss"
  }
}
```

The key is one of:

- **A name.** A bare or qualified COBOL name, with the rules of parent spec section 5.5.1.
- **A name pattern.** A key with `*` is a pattern. `*` matches zero or more characters. A pattern is one word, without `OF` or `IN`. It matches the name of each elementary item that is not `FILLER`. The match is not case-sensitive.

The value is a `DateTimeFormatter` pattern, with the rules of the core (`Formats.of`).

On the command line: `--date <name or pattern>=<pattern>`. Repeat the option for more hints. For the same key, the command line has priority over the options file.

### 3.2 Java type

The pattern gives the Java type. The generator tries the types in this order, and the first type that fits wins:

1. `LocalDateTime`
2. `LocalDate`
3. `LocalTime`

A type fits if the formatter can format a sample value of the type and parse the text back to the type. This is the same test as the processor. For example:

| Pattern | Java type |
|---|---|
| `yyyyMMdd`, `yyMMdd`, `yyyyDDD`, `yyyy-MM-dd` | `LocalDate` |
| `HHmmss`, `HHmm`, `HH:mm:ss` | `LocalTime` |
| `yyyyMMddHHmmss`, `yyyy-MM-dd-HH.mm.ss` | `LocalDateTime` |

### 3.3 Checks

For a field with a hint, the generator checks these rules. A failure is an error at the line of the item:

- The usage is `DISPLAY`.
- A numeric picture is unsigned and has no `V`.
- The pattern is valid, and it fits one of the three types.
- The pattern gives text of a fixed length.
- The length of the text is equal to the length of the picture. For example, `yyyyMMdd` needs `9(8)` or `X(8)`.

A field with a hint is not permitted as a subtype discriminator or as a `DEPENDING ON` count field. The existing checks give an error.

### 3.4 Priority and conflicts

- A name hint has priority over a pattern hint for the same item.
- If two pattern hints match an item and give different patterns, the generator reports an error. Add a name hint for the item to resolve it.
- If two pattern hints give the same pattern, there is no conflict.

### 3.5 Hints that match nothing

- A name that matches no item, or more than one item: error (parent spec, 5.5.1).
- A name of a group: error. A date hint applies to an elementary item.
- A name of an item that gives no field, for example an elementary item with `REDEFINES` that the generator skips: error.
- A pattern that matches no field: warning.

These errors have the position `<file>:1:1`, like the other option errors. A pattern does not match `FILLER`, groups, or the fillers that the generator adds.

### 3.6 Model and generated code

- `JavaType` gets three values: `LOCAL_DATE`, `LOCAL_TIME`, and `LOCAL_DATE_TIME`.
- A field member gets an optional key `pattern`.
- The generator writes `pattern` in `@BookField`, also for `yyyyMMdd`:

```java
/**
 * {@code 10 ORDER-DATE PIC 9(8).}
 */
@BookField(
        order = 20,
        pic = "9(8)",
        pattern = "yyyyMMdd"
)
private LocalDate orderDate;
```

## 4. Continuable and Validatable

### 4.1 Option

```json
{
  "continuable": { "LIST-SHIPMENTS": "com.example.shipments.ShipmentPaging" },
  "validatable": { "LIST-SHIPMENTS": "com.example.shipments.ReturnCodes" }
}
```

- The key is the name of a record: a level `01` item, or the record name of a copybook without level `01`. The match is not case-sensitive.
- The value is the name of a class that the developer writes: the **handler**. A name without a package is a class in the package of the generated classes.

On the command line: `--continuable <record>=<class>` and `--validatable <record>=<class>`.

### 4.2 Generated code

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

The developer writes the handler:

```java
public final class ShipmentPaging {
    public static boolean hasContinuation(ListShipments page) {
        return "Y".equals(page.getMoreData());
    }
}
```

One handler class can serve more operations, with one overload for each operation class.
The `javac` compiler checks the handler. A missing class or method is a compile error in the project.

### 4.3 Checks

- A key that is not a record name: error at `<file>:1:1`.
- A value that is not a Java class name (`SourceVersion.isName`): error at `<file>:1:1`.
- BookMap does not permit an `INPUT_OUTPUT` collection in a `Continuable` class. For a continuable record, each `INPUT_OUTPUT` collection of the operation class and of its subtypes is an error at the line of the collection. The message tells the developer to put the table in an output alternative of a `REDEFINES` area.

### 4.4 Model

A class gets two optional keys: `continuable` and `validatable`. Each value is the handler class name, as the option gives it.

## 5. Model JSON version

The new keys are optional, and the new `javaType` values are new values. A model of version 0.13.0 stays a valid model. Thus, `formatVersion` stays `1`.
An older reader rejects a model with the new keys. It reports an unknown key.

## 6. Tests

- Mapper tests for each check and each diagnostic.
- JSON tests for the new keys.
- Generator tests for the `pattern` member, the interfaces, and the methods.
- An end-to-end test: a copybook with dates and a `REDEFINES` split, with `continuable` and `validatable`. The test compiles the classes with a handler, and round-trips dates through the codec.
- CLI tests for `--date`, `--continuable`, and `--validatable`.
- 100% line and branch coverage.

## 7. Decisions for review

The author was away. The designer made these decisions. Each one lists the options and the reason.

1. **Where the Java type of a date comes from.**
   Options: (a) the pattern gives the type; (b) the hint gives the type and the pattern, for example `{"type": "LocalDate", "pattern": "yyyyMMdd"}`.
   Choice: (a). A pattern fits only one of the three types, so a second value adds nothing but a source of errors. The value stays a simple text.

2. **How to tell a name from a pattern.**
   Options: (a) a key with `*` is a pattern; (b) two options, `dates` and `datePatterns`; (c) regular expressions.
   Choice: (a). A COBOL name cannot contain `*`, so the rule is clear. A glob is easier to read than a regular expression in a JSON file. One option keeps all date hints in one place.

3. **The severity of a hint that matches nothing.**
   Options: (a) error for all; (b) warning for all; (c) error for a name, warning for a pattern.
   Choice: (c). A name that matches nothing is an error for all other options (parent spec, 5.5.1). A pattern is a general rule, and a team can copy the same patterns into each options file. An error for each unused pattern would make this hard. The warning still shows a typo.

4. **A pattern that matches an item with an incorrect picture.**
   Options: (a) error; (b) skip the item, with a warning or without.
   Choice: (a). A skip is a guess about the intent of the developer. A name hint has priority, so the developer can give the correct pattern for that item.

5. **`pattern` for the default `yyyyMMdd`.**
   Options: (a) always write `pattern`; (b) omit it when it is `yyyyMMdd`.
   Choice: (a). The generated code shows the hint, and it does not depend on `@BookDefaults`.

6. **A numeric picture with a pattern that gives other characters than digits**, for example `yyyy-MM-dd` on `9(10)`.
   Options: (a) error; (b) accept it, as the processor does.
   Choice: (b). The generator does not add rules that the processor does not have. The length check still applies.

7. **How a generated class gets the method body of `Continuable` and `Validatable`.**
   Options:
   (a) a static handler method that the generated method calls;
   (b) an `implements` option with an interface that the developer writes, with a default method;
   (c) an abstract generated class and a subclass that the developer writes;
   (d) declarative rules in the options, for example a flag item and a value;
   (e) a stub method that throws `UnsupportedOperationException`.
   Choice: (a). (c) is not possible: an operation class must not be abstract, and the codec belongs to the annotated class. (e) gives code that fails at runtime. (d) is a small rule language that is never complete, for example for a restart key or an error message. (b) works, but the developer must declare each getter in the interface. With (a), the handler gets the object and can use all getters and setters. The generator also knows that the class is `Continuable`, so it can check the collection rule with the copybook line.

8. **The method names of the handler.**
   Options: (a) fixed names, `hasContinuation` and `validate`; (b) `Class#method` in the option.
   Choice: (a). The names are the same as in the interfaces, so the code is easy to read. A handler class can serve more operations through overloads.

9. **The key of `continuable` and `validatable`.**
   Options: (a) the record name; (b) a boolean for all records; (c) a list of interfaces for all records.
   Choice: (a). A copybook can have more than one record, and each record can need a different handler. A name is also the style of the other options.

10. **The `INPUT_OUTPUT` collection rule.**
    Options: (a) error in the generator; (b) make such a collection `OUTPUT`; (c) let the processor report it.
    Choice: (a). (b) moves the offsets of the items after the table (parent spec, 5.5). (c) gives a message without the copybook line.

11. **The `formatVersion` of the model.**
    Options: (a) keep `1`; (b) change to `2`.
    Choice: (a). All version 1 models stay valid, and the reader of this version reads them. Other sessions also add model keys at the same time. One version change for all of them is easier to merge. The owner can change the version before the release.

12. **One pull request or two.**
    Options: (a) one pull request with separate commits; (b) one pull request for each feature.
    Choice: (a). Both features change the same files (options, model, JSON, generator, CLI). Two pull requests would conflict with each other. The commits stay separate.
