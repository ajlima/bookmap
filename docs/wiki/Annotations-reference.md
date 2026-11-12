# Annotations reference

The annotations are in the package `pt.dazz.bookmap.annotation`. The processor reads them at compile time.

## @BookOperation (class)

| Attribute | Meaning | Default |
|---|---|---|
| `value` | The logical operation name. | The simple class name. |

The transport maps the operation name to its destination. See [Transports and simulator](Transports-and-simulator).

## @BookField (field)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. Gaps are permitted. | Mandatory. |
| `pic` | A COBOL `PIC` string with an optional usage clause. It replaces `length`, `scale`, `signed`, and `usage`. | None. |
| `length` | Number of characters for text. Total number of digits for numbers (`S9(13)V99` is `length = 15`). | Mandatory without `pic`. |
| `direction` | `INPUT`, `OUTPUT`, or `INPUT_OUTPUT`. | `INPUT_OUTPUT`. A count field takes the direction of its collection. |
| `usage` | `DISPLAY`, `COMP3`, or `COMP`. | From `@BookDefaults`, else `DISPLAY`. |
| `scale` | Number of implied decimal places (`V99` is 2). | 0. |
| `signed` | `true` if the field has a sign (`S9`). | From `@BookDefaults`, else `false`. |
| `pattern` | Date or time format. | From `@BookDefaults`, else `yyyyMMdd`. |
| `converter` | A custom `FieldConverter` class. | None. |

A field needs `pic` or `length`. One exception exists: a date or time field with neither gets its length from the pattern.
If you set `pic`, do not set `length`, `scale`, `signed`, or `usage` on the same field. This is a compile error.
Do not set `signed` or `scale` on a `String` or a date and time field. This is a compile error.
An operation class and an item class need at least one annotated field.

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

`FieldContext` gives the field name, `length`, `scale`, `signed`, `usage`, `pattern`, the length in bytes, and the charset.
The converter class must be public and have a constructor without arguments.
All threads share one instance, so it must be thread-safe.

## @BookCollection (field of type `List<Item>`)

| Attribute | Meaning | Default |
|---|---|---|
| `order` | Position in the book. | Mandatory. |
| `direction` | As in `@BookField`. | `OUTPUT`. |
| `dependsOn` | The name of the count field. | Variable collection: the member immediately before the collection. Fixed collection: no count field. |
| `fixed` | `true` for a fixed table (`OCCURS n` without `DEPENDING ON`). | `false` |
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
| `charset` | The charset name for the codec. It must be single-byte. Default: the client charset. |
| `usage` | The default usage of numeric fields without a usage in the `pic` and without an explicit `usage`. |
| `signed` | The default sign of numeric fields without `pic` and without an explicit `signed`. |
| `pattern` | The default date or time pattern. |

The priority is, from high to low:

1. An explicit attribute on the field.
2. `@BookDefaults` on the class that declares the field.
3. `@BookDefaults` on the package of that class.
4. The built-in default.

The `usage` and `signed` defaults apply only to numeric Java types. Text and date fields ignore them.

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
