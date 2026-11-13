The annotations are in the package `pt.dazz.bookmap.annotation`. The processor reads them at compile time.

## @BookOperation (class)

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The logical operation name. | The simple class name. |

Rules for an operation class (each rule gives a compile error):

- The class is public, and top-level or static.
- The class is not a record, not abstract, and not generic. An abstract base class with `@BookSubTypes` is permitted.
- The class has a public constructor without arguments.
- The operation name is not blank.

The transport maps the operation name to its destination. See [Transports and simulator](Transports-and-simulator).

## @BookField (field)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. Gaps are permitted. | Mandatory. |
| `pic` | A COBOL `PIC` string with an optional usage clause. It replaces `length`, `scale`, `signed`, and `usage`. | None. |
| `length` | Number of characters for text. Total number of digits for numbers (`S9(13)V99` is `length = 15`). | Mandatory without `pic`. |
| `direction` | `INPUT`, `OUTPUT`, or `INPUT_OUTPUT`. | `INPUT_OUTPUT`. A count field without `direction` takes the direction of its collection. |
| `usage` | `DISPLAY`, `COMP3`, or `COMP`. | From `@BookDefaults`, else `DISPLAY`. |
| `scale` | Number of implied decimal places (`V99` is 2). | 0. |
| `signed` | `true` if the field has a sign (`S9`). | From `@BookDefaults`, else `false`. |
| `pattern` | Date or time format. | From `@BookDefaults`, else `yyyyMMdd`. Set a pattern on each `LocalTime` and `LocalDateTime` field. |
| `converter` | A custom `FieldConverter` class. | None. |

A field needs `pic` or `length`. One exception exists: a date or time field with neither gets its length from the pattern.
If you set `pic`, do not set `length`, `scale`, `signed`, or `usage` on the same field. This is a compile error.
Do not set `signed` or `scale` on a `String` or a date and time field. This is a compile error.
An operation class and an item class need at least one annotated field.

### Rules for annotated fields

- The processor reads the annotated fields of the class and of all its superclasses.
- Each `order` is unique in that hierarchy. A duplicate gives `duplicate order, also used by <Class>.<field>`.
- An annotated field is not `static`.
- A field does not have both `@BookField` and `@BookCollection`.
- Each field has a public getter `getName()` that returns the field type, and a public setter `setName(T)`. The methods can be in a superclass.
- A primitive `boolean` field (only with a converter) uses `isName()`.
- A `final` field has no setter. Thus, it gives the compile error `missing setter setName`.

Example of inheritance, from the billing example: `ListInvoices` gets the field `accountId` (order 10) from its superclass `BaseRequest`.

```java
@Getter
@Setter
public class BaseRequest {
    @BookField(order = 10, pic = "X(10)")
    private String accountId;
}

@Data
@EqualsAndHashCode(callSuper = true)
@BookOperation("ListInvoices")
public class ListInvoices extends BaseRequest {
    @BookField(order = 20, pic = "9(3)", direction = OUTPUT)
    private int invoiceCount;

    @BookCollection(order = 30, maxOccurs = 10)
    private List<Invoice> invoices;
}
```

See [PIC syntax and type mapping](PIC-syntax-and-type-mapping) for the `pic` string and the Java types.

### Directions

An `INPUT` field is in the request only. An `OUTPUT` field is in the response only. An `INPUT_OUTPUT` field is in both.
An `INPUT` field does not occupy bytes in the response area. An `OUTPUT` field does not occupy bytes in the request area.

### Converters

A converter implements `pt.dazz.bookmap.convert.FieldConverter<J>`:

```java
public interface FieldConverter<J> {
    J decode(byte[] bytes, FieldContext context);
    byte[] encode(J value, FieldContext context);
}
```

`FieldContext` is a record with these components:

| Component | Meaning |
|---|---|
| `field` | The Java field name. |
| `length` | The number of digits or characters. |
| `scale` | The number of decimal places. |
| `signed` | `true` if the field has a sign. |
| `usage` | The usage. Without `pic` and `usage`, a converter field has `DISPLAY`. |
| `pattern` | The pattern. It is `yyyyMMdd` if the field sets no pattern. |
| `byteLength` | The length of the field in bytes. For `DISPLAY`, it is `length`. |
| `charset` | The charset of the codec at run time. |

Rules for a converter class (each rule gives a compile error):

- The class is public, concrete, and top-level or static. It has a public constructor without arguments.
- The class has no type parameters.
- The class implements `FieldConverter<J>` with a type argument. `J` is the field type, or the wrapper type for a primitive field.

Rules at run time:

- `decode` gets exactly `byteLength` bytes.
- `encode` must return exactly `byteLength` bytes. Otherwise, the codec throws `MappingException`.
- For a `null` value, `encode` gets `null`.
- For a primitive field, `decode` must not return `null`.
- The codec wraps a `RuntimeException` of the converter in a `MappingException`. A `BookmapException` goes to the caller unchanged.

Each codec makes one instance of each converter class. All threads use it. Thus, make the converter thread-safe.

Example: a `Y`/`N` flag as a `boolean`.

```java
public class FlagConverter implements FieldConverter<Boolean> {

    @Override
    public Boolean decode(byte[] bytes, FieldContext context) {
        String text = new String(bytes, context.charset());
        return text.equals("Y") ? Boolean.TRUE : text.equals("N") ? Boolean.FALSE : null;
    }

    @Override
    public byte[] encode(Boolean value, FieldContext context) {
        return (value == null ? " " : value ? "Y" : "N").getBytes(context.charset());
    }
}
```

```java
@BookField(order = 40, length = 1, converter = FlagConverter.class)
private boolean express;   // accessors: isExpress() and setExpress(boolean)
```

### Enum fields

An enum field needs no converter. Put `@CobolValue` on each enum constant. The processor generates the conversion code at compile time. The codec uses no reflection.

```java
public enum PartyRole {
    @CobolValue("SELLER") SELLER,
    @CobolValue("BILL-TO") BILL_TO,
    @CobolValue("SHIP-TO") SHIP_TO
}
```

```java
@BookField(order = 30, pic = "X(8)")
private PartyRole partyRole;
```

#### @CobolValue (enum constant)

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The discrete values of the constant. | None. |
| `ranges` | The `THRU` ranges of the constant, as `@CobolValue.Range(from = "B", thru = "D")`. Both bounds are in the range. | None. |

A constant needs at least one value or one range. The **canonical value** is the first discrete value, else the `from` value of the first range.

#### @CobolUnknown (enum constant)

`@CobolUnknown` marks the fallback constant. Only one constant of an enum can have it. The constant can also have `@CobolValue`.

```java
public enum Grade {
    @CobolValue({"A", "A+"}) EXCELLENT,
    @CobolValue(ranges = @CobolValue.Range(from = "B", thru = "D")) PASS,
    @CobolUnknown OTHER
}
```

#### Rules for enum values

- A text `pic` (`X`) or no `pic` makes a text enum field. A numeric `pic` makes a numeric enum field.
- A text value has no spaces on the right. The processor removes them, because a `PIC X(8)` value has spaces on the right. A value with only spaces is the blank value `""`.
- The comparison of text values is case-sensitive.
- A numeric value is a decimal number, for example `"1"`, `"-5"`, or `"12.50"`. The comparison is numeric, thus `"1"` and `"01"` are equal.
- A text range uses the order of Java `String.compareTo`. This order is not the EBCDIC order when a range mixes letters and digits.
- A text enum field has the rules of a `String` field. A numeric enum field has the rules of a `BigDecimal` field.
- The enum must be public, or in the package of the operation class.
- An enum field cannot be a discriminator or a count field.
- A field with a `converter` uses the converter, also for an enum type.

Compile errors on the field, for example `Invoice.partyRole (order 30): enum PartyRole: ...`:

| Problem | Message |
|---|---|
| No constant has values. | `no constant has @CobolValue` |
| A constant has no annotation and is not the fallback. | `the constant B has no @CobolValue` |
| `@CobolValue` without a value or a range. | `the constant B has no value` |
| Two fallback constants. | `only one constant can have @CobolUnknown: B, C` |
| A text value is too long. | `the value 'ABCD' of A is longer than 3 characters` |
| A numeric value is not correct. | `the value '1e3' of A is not a decimal number` |
| A numeric value does not fit the `pic`. | `the value '1000' of A has more than 4 digits` |
| A range is empty. | `the range 'C' THRU 'A' of A is empty: the first value is more than the last value` |
| Two constants share a value. | `the value '7' of C overlaps the range '5' THRU '9' of B` |

#### Read and write

- **Read.** The codec finds the constant that contains the field value. A blank field (spaces, or a blank number) gives `null`, unless a constant has the blank value `""`.
- **Write.** The codec writes the canonical value of the constant. A `null` value writes spaces in a text field and zero in a numeric field.
- **Unknown value.** Without a fallback constant, a value that no constant contains throws `MappingException`: `Invoice.partyRole (order 30): unknown value 'AGENT' for enum PartyRole`. With a fallback constant, the codec gives the fallback constant.
- **Fallback write.** The fallback constant writes its canonical value. If it has no `@CobolValue`, the write throws `MappingException`: `the constant OTHER of enum Grade has no COBOL value`. Thus, the codec does not write a value that it did not read.

## @BookCollection (field of type `List<Item>`)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. | Mandatory. |
| `direction` | As in `@BookField`. | `OUTPUT`. |
| `dependsOn` | The name of the count field. | Variable collection: the member immediately before the collection. Fixed collection: no count field. |
| `fixed` | `true` for a fixed table (`OCCURS n` without `DEPENDING ON`). | `false` |
| `emptyItem` | Fixed table: the bytes of an unused slot, `EmptyItem.LOW_VALUES` or `EmptyItem.SPACES`. Without `emptyKey` and `dependsOn`, these bytes also mark an empty slot on read. | `EmptyItem.LOW_VALUES` |
| `emptyKey` | Fixed table: the name of a field of the item class. A slot is empty if this key is blank or zero. Not permitted with `dependsOn`. | No key. |
| `maxOccurs` | The maximum number of items in one call. | Mandatory. |

The item class has `@BookField` fields without `direction`. They use the direction of the collection.
The item class needs a public constructor without arguments.
The digits of the count field must hold `maxOccurs`. For example, `9(1)` with `maxOccurs = 50` is a compile error.

See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS) for the behavior.

## @BookDefaults (class or package)

Use `package-info.java` to set defaults for a package. The billing example does this.

```java
@BookDefaults(usage = Usage.COMP3, signed = true)
package pt.dazz.bookmap.examples.billing;
```

| Attribute | Meaning |
|---|---|
| `charset` | The charset name for the codec. It must exist and be single-byte. Default: the client charset (`IBM037`, unless the builder sets another charset). |
| `usage` | The default usage of numeric fields without a usage in the `pic` and without an explicit `usage`. |
| `signed` | The default sign of numeric fields without `pic` and without an explicit `signed`. |
| `pattern` | The default date or time pattern. |

The priority is, from high to low:

1. An explicit attribute on the field.
2. The `pic` of the field.
3. `@BookDefaults` on the class that declares the field.
4. `@BookDefaults` on the package of that class.
5. The built-in default.

The `usage` and `signed` defaults apply only to numeric types. The `pattern` default applies only to date and time types. Fields with a converter ignore all three.

- The processor does not use the `@BookDefaults` of an enclosing class or of a superclass. A field in a superclass uses the defaults of the superclass and of its package.
- All classes of one operation (superclasses and item classes) must use the same charset. A different charset is a compile error.

## @BookSubTypes (class)

`@BookSubTypes` maps a `REDEFINES` area to subclasses. A discriminator field selects the subclass.

| Attribute | Meaning |
|---|---|
| `discriminator` | The name of the discriminator field. The base class or one of its superclasses declares it. |
| `value` | The list of `@BookSubTypes.Type`. |
| `Type.value` | The discriminator value of the subclass, as text. |
| `Type.type` | The subclass. |

See [Subtypes (REDEFINES)](Subtypes-REDEFINES) for the rules and an example.

## Interfaces

Two interfaces in `pt.dazz.bookmap` control the execution cycle.

```java
public interface Validatable {
    void validate();          // throw an exception if the response shows an error
}

public interface Continuable {
    boolean hasContinuation(); // return true to call the operation again
}
```

See [Collections and fixed OCCURS](Collections-and-fixed-OCCURS#pagination) for the cycle.

## Lombok

The processor finds the Lombok annotations by name. It does not depend on Lombok.

- `@Data`, `@Getter`, and `@Setter` give the accessors. `AccessLevel.PRIVATE` and `AccessLevel.NONE` give no accessor.
- `AccessLevel.PACKAGE`, `AccessLevel.MODULE`, and `AccessLevel.PROTECTED` give an accessor only in the package of the operation class. The codec is in that package. For a superclass or an item class in another package, use public accessors.
- `@AllArgsConstructor`, `@RequiredArgsConstructor`, and `@Builder` remove the default constructor. Add `@NoArgsConstructor` or a public constructor without arguments to the class.
- `@Data` on a class with a `final` or `@NonNull` field without a value also removes the default constructor. Add `@NoArgsConstructor` to such a class (`@NoArgsConstructor(force = true)` for a `final` field). If BookMap runs before Lombok, the processor cannot detect this case, and javac reports the error in the generated codec.
- `@NoArgsConstructor` must be public and must not have a `staticName`.
