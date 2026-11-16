package pt.dazz.bookmap.examples.shipments;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookGroup;
import pt.dazz.bookmap.annotation.BookOperation;
import pt.dazz.bookmap.annotation.Direction;

/**
 * Reads the status of a shipment. Two {@code REDEFINES} areas have no discriminator: the
 * caller selects the view.
 *
 * <pre>
 * 01  GET-SHIPMENT.
 *     05  SHIPMENT-ID              PIC X(10).
 *     05  TRACKING-CODE            PIC X(16).
 *     05  TRACKING-PARTS REDEFINES TRACKING-CODE.
 *         10  CARRIER-CODE         PIC X(4).
 *         10  ROUTE-NUMBER         PIC 9(6).
 *         10  SEQUENCE-NUMBER      PIC 9(6).
 *     05  RESULT-CODE              PIC X(2).
 *     05  RESULT-DETAIL.
 *         10  ERROR-TEXT           PIC X(40).
 *     05  DELIVERY-DATA REDEFINES RESULT-DETAIL.
 *         10  DELIVERY-DATE        PIC 9(8).
 *         10  DELIVERED-WEIGHT     PIC S9(5)V99 COMP-3.
 *         10  SIGNED-BY            PIC X(20).
 *     05  SHIPPING-CHARGE          PIC S9(7)V99 COMP-3.
 * </pre>
 */
@Data
@BookOperation("GETSHIP")
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

    /**
     * Tells if the shipment is delivered. Then {@link #getDeliveryData()} is valid.
     *
     * @return {@code true} for the result code {@code 00}
     */
    public boolean delivered() {
        return "00".equals(resultCode);
    }
}
