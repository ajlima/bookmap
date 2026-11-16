A COBOL book can use `REDEFINES` to give one area more than one layout. Sometimes no field in the book tells which layout is valid. The program and the caller select the layout from a return code or from other data. For this case, map each layout to a **view**.
If a field selects the layout (a discriminator), use [subtypes](Subtypes-REDEFINES).

```cobol
01  GET-SHIPMENT.
    05  SHIPMENT-ID              PIC X(10).
    05  TRACKING-CODE            PIC X(16).
    05  TRACKING-PARTS REDEFINES TRACKING-CODE.
        10  CARRIER-CODE         PIC X(4).
        10  ROUTE-NUMBER         PIC 9(6).
        10  SEQUENCE-NUMBER      PIC 9(6).
    05  RESULT-CODE              PIC X(2).
    05  RESULT-DETAIL.
        10  ERROR-TEXT           PIC X(40).
    05  DELIVERY-DATA REDEFINES RESULT-DETAIL.
        10  DELIVERY-DATE        PIC 9(8).
        10  DELIVERED-WEIGHT     PIC S9(5)V99 COMP-3.
        10  SIGNED-BY            PIC X(20).
    05  SHIPPING-CHARGE          PIC S9(7)V99 COMP-3.
```

## Map the views

A **REDEFINES area** is one redefined member and its views. A view is a member with the attribute `redefines`. The value is the Java name of the redefined member.
Map a group to a field of a group class with `@BookGroup`. A group class has the rules of an item class.

```java
@BookOperation
public class GetShipment {

    @BookField(order = 10, pic = "X(10)", direction = Direction.INPUT)
    private String shipmentId;

    @BookField(order = 20, pic = "X(16)", direction = Direction.OUTPUT)
    private String trackingCode;

    @BookGroup(order = 30, redefines = "trackingCode")
    private TrackingParts trackingParts;

    @BookField(order = 40, pic = "X(2)", direction = Direction.OUTPUT)
    private String resultCode;

    @BookGroup(order = 50, direction = Direction.OUTPUT)
    private ResultDetail resultDetail;

    @BookGroup(order = 60, redefines = "resultDetail")
    private DeliveryData deliveryData;

    @BookField(order = 70, pic = "S9(7)V99 COMP-3", direction = Direction.OUTPUT)
    private BigDecimal shippingCharge;
}

public class TrackingParts {
    @BookField(order = 10, pic = "X(4)") private String carrierCode;
    @BookField(order = 20, pic = "9(6)") private int routeNumber;
    @BookField(order = 30, pic = "9(6)") private int sequenceNumber;
}

public class DeliveryData {
    @BookField(order = 10, pic = "9(8)") private LocalDate deliveryDate;
    @BookField(order = 20, pic = "S9(5)V99 COMP-3") private BigDecimal deliveredWeight;
    @BookField(order = 30, pic = "X(20)") private String signedBy;
}
```

The class `ResultDetail` has one field `errorText`, `PIC X(40)`. The getters and setters are not shown.

Select the view in the caller:

```java
GetShipment result = client.execute(request);
if ("00".equals(result.getResultCode())) {
    LocalDate date = result.getDeliveryData().getDeliveryDate();
} else {
    String error = result.getResultDetail().getErrorText();
}
```

## Rules

- A view names the redefined member, never another view. The redefined member is in the same class.
- A view comes right after the redefined member, or after another view of it. Each view has its own `order`.
- A view has no `direction`. It uses the direction of the redefined member.
- A member can be a `@BookField`, a fixed `@BookCollection`, or a `@BookGroup`. A group class can contain fixed collections and another area.
- The area has the length of its longest member. A view can be shorter or longer than the redefined member. The members after the area have the offsets of COBOL.
- A `@BookGroup` must be in an area.
- A member at the top of an area must not have a primitive type. Use `Integer`, `Long`, or `BigDecimal`. A field in a group class can be primitive.
- A member at the top of an area must not be a count field (`dependsOn`), a discriminator, or an `emptyKey`. A collection at the top of an area must have `fixed = true`, no `dependsOn`, and no `partial`.
- An area can be in an operation class, an item class, a subclass, or a group class.
- `maxResponseLength()` counts the area one time, with its longest member.

## Read

The codec checks that the data holds the full area. Then it reads each member of the area from the start of the area.
If the bytes of a member are not a valid value of its type, the member is `null`. Examples: a byte that is not valid in a packed or zoned number, a text that is not a date, or an unknown enum value.
In the example, a failed shipment gives `deliveryData == null`, because the error text is not a date.

These problems are not invalid values. They stop the read with `MappingException`:

- A response that ends inside the area: `GetShipment.resultDetail (order 50): response too short, need 40 bytes at position 18 but only 12 remain`. In an item, the message starts with `<collection> item <i>: `.
- A failure of a converter.

A member never reads past the end of its area.
A collection in an area does not keep the items of earlier pages. Each response sets a new list.

## Write

The codec writes the **first member of the area, in `order`, that is not `null`**. For a collection, an empty list is not `null`. The codec ignores the other members.
If the member is shorter than the area, the codec writes spaces in the remaining bytes. If all members are `null`, the codec writes spaces for the full area.

To send a view, set the members before it to `null`.

**CAUTION:** Do not give an initial value to a member at the top of an area. That member is then never `null`, and it always wins.

The codec keeps no copy of the bytes. After a read, all valid members are not `null`. A second write of the same object writes the first valid member. The bytes can then be different from the bytes of the read:

- A text member changes each `0x00` byte to a space.
- A signed packed or zoned number writes the positive sign `C`, also for a read with `F`.

**CAUTION:** For an `INPUT_OUTPUT` area of a `Continuable` operation, set the members before the wanted view to `null` before each page. Else the next request can contain other bytes.

## Copybook generator

The option `views` maps an area to views. See [Views](Copybook-generator#views).
