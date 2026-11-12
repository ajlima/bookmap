A COBOL book can use `REDEFINES` to give one area more than one layout. A field in the book (the discriminator) tells which layout is valid:

```cobol
01  GET-ACCOUNT.
    05  ACCOUNT-ID        PIC X(12).
    05  RESTART-KEY       PIC X(10).
    05  MORE-DATA         PIC X.
    05  ACCOUNT-TYPE      PIC 9(2).
    05  CHECKING-DATA.
        10  OVERDRAFT-LIMIT   PIC S9(9)V99 COMP-3.
        10  ENTRY-COUNT       PIC 9(2).
        10  ENTRIES OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.
            ...
    05  SAVINGS-DATA REDEFINES CHECKING-DATA.
        10  INTEREST-RATE     PIC 9V9(4).
        10  MATURITY-DATE     PIC 9(8).
```

## Map the layouts

Map each layout to a subclass. Put `@BookSubTypes` on the base class. The base class (or one of its superclasses) declares the common fields and the discriminator:

```java
@BookOperation
@BookSubTypes(discriminator = "accountType", value = {
    @BookSubTypes.Type(value = "01", type = CheckingAccount.class),
    @BookSubTypes.Type(value = "02", type = SavingsAccount.class)
})
public class GetAccount implements Continuable {
    @BookField(order = 10, pic = "X(12)", direction = INPUT) private String accountId;
    @BookField(order = 20, pic = "X(10)")                    private String restartKey;
    @BookField(order = 30, pic = "X", direction = OUTPUT)    private String moreData;
    @BookField(order = 40, pic = "9(2)")                     private int accountType;
}

public class CheckingAccount extends GetAccount {
    @BookField(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT) private BigDecimal overdraftLimit;
    @BookField(order = 60, pic = "9(2)")                                 private int entryCount;
    @BookCollection(order = 70, maxOccurs = 10)                          private List<StatementEntry> entries;
}

public class SavingsAccount extends GetAccount {
    @BookField(order = 50, pic = "9V9(4)", direction = OUTPUT) private BigDecimal interestRate;
    @BookField(order = 60, direction = OUTPUT)                 private LocalDate maturityDate;
}
```

Send the base class. The client gives the subclass that the response selects:

```java
GetAccount request = new GetAccount();
request.setAccountId("ACC-0000001");
request.setRestartKey("");
GetAccount result = client.execute(request);   // a CheckingAccount or a SavingsAccount
```

**CAUTION:** Use the return value of `execute()`. If the response selects a subclass, the client gives a new object. The request object does not get the response.

```java
if (result instanceof CheckingAccount checking) {
    List<StatementEntry> entries = checking.getEntries();
} else if (result instanceof SavingsAccount savings) {
    BigDecimal rate = savings.getInterestRate();
}
```

## Subtypes in collection items

The same annotation goes on the item class of a collection. Then each item can have a different subclass:

```java
@BookSubTypes(discriminator = "entryType", value = {
    @BookSubTypes.Type(value = "C", type = CardPayment.class),
    @BookSubTypes.Type(value = "T", type = Transfer.class)
})
public abstract class StatementEntry {
    @BookField(order = 10, pic = "X")                private String entryType;
    @BookField(order = 20, pic = "S9(13)V99 COMP-3") private BigDecimal amount;
}
```

The full example is in `bookmap-examples`, package `pt.dazz.bookmap.examples.accounts`.

## Test with the simulator

In a test, use `registerResponder` to answer with a subclass:

```java
simulator.registerResponder(GetAccount.class, call -> {
    SavingsAccount account = new SavingsAccount();
    account.setInterestRate(new BigDecimal("0.0325"));
    account.setMoreData("N");
    return account;
});
```

See [Transports and simulator](Transports-and-simulator).

## Attributes

| Attribute | Meaning |
|---|---|
| `discriminator` | The name of the discriminator field. The base class or one of its superclasses declares it. |
| `value` | The list of `@BookSubTypes.Type`. |
| `Type.value` | The discriminator value of the subclass, as text. |
| `Type.type` | The subclass. |

## Rules

- The discriminator is `String`, `int`, `Integer`, `long`, or `Long`. It has no converter.
- For a numeric discriminator, `"001"` and `"1"` are the same value. For a text discriminator, the spaces on the right have no effect.
- In an operation class, the discriminator is `OUTPUT` or `INPUT_OUTPUT`. An abstract base class needs an `INPUT_OUTPUT` discriminator.
- Each member of a subclass must have an `order` greater than all members of the base class. Thus, the redefined area is the end of the layout. Two subclasses can use the same `order` values.
- A variable collection in a subclass, or a fixed collection with `dependsOn`, needs its count field in the same subclass. A fixed collection without `dependsOn` needs no count field.
- The subclasses do not have `@BookOperation`. They use the operation name of the base class.
- The base class can be abstract. Then the caller always sends a subclass.
- The base class can be in the list with its own value.

## Behavior

- Write: for a class in the list, the codec sets the discriminator to the value of that class, if the discriminator is in the direction. Then it writes the layout of the class. For a request, the discriminator must thus be `INPUT_OUTPUT`.
- Read: the codec reads the discriminator and selects the subclass. An unknown value throws `MappingException`, for example `GetAccount.accountType (order 40): unknown discriminator value '7'. Known values: 1, 2`.
- Class change: if the first response selects a subclass of the request class, the client creates the subclass. The client copies the annotated members of the base class from the request to the new object. Then it decodes the response into the new object. The copy uses the getter and the setter of each member. A list goes to the new object as the same instance. Fields without an annotation are not copied.
- The next pages use the object of the first response. The next requests thus contain the discriminator value of the new class.
- A later page that selects a different class throws `MappingException`: `GetAccount: the response has type X, but the object has type Y. Only the first response can change the type`.
- If the request is a subclass and the response selects a different subclass, the client throws `MappingException`: `GetAccount: the response has type SavingsAccount, which is not a CheckingAccount`.
- With an abstract base class, the response must select the class of the request.
- Items: each item occupies the length of the longest layout. The longest layout includes a base class that is not abstract. The codec writes spaces at the end of a shorter item. On read, it skips to the end of the slot.
- `maxResponseLength()` is the length of the longest response layout. A collection counts with `maxOccurs` items. The client gives this value to the transport in `BookRequest`.

## Compile errors

- The discriminator does not exist in the base class, has a type or converter that the rules do not permit, has direction `INPUT`, or comes after a variable collection. A discriminator after a fixed collection is permitted.
- An empty list, a type that is not an instantiable subclass of the base class, or a type in the list two times.
- A blank value, a value that is used two times, a value that is not a number for a numeric field, or a value that does not fit in the field.
- `@BookSubTypes` on a subclass (subtypes in a subtype), `@BookOperation` on a subclass, or a subclass that extends another subclass in the list.
- A subclass member with an `order` that is not greater than all base members, or a collection in a subclass with a count field in the base class. The message is `dependsOn field '<name>' must be in the subtype layout`.
- A discriminator in a subclass: `discriminator field '<name>' must be in the base class or its superclasses`.
