package pt.dazz.bookmap.examples.orders;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.util.List;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/**
 * Lists the shipments of a customer. The book has a fixed table and a count field before it,
 * without DEPENDING ON. The collection names the count field with {@code dependsOn}.
 *
 * <pre>
 * 01  LIST-SHIPMENTS.
 *     05  CUSTOMER-ID         PIC X(10).
 *     05  SHIPMENT-COUNT      PIC 9(2).
 *     05  SHIPMENT OCCURS 5.
 *         10  SHIPMENT-ID     PIC X(12).
 *         10  STATUS          PIC X(2).
 *     05  RETURN-CODE         PIC X(4).
 * </pre>
 */
@Data
@BookOperation
public class ListShipments {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookField(order = 20, pic = "9(2)")
    private int shipmentCount;

    @BookCollection(order = 30, maxOccurs = 5, fixed = true, dependsOn = "shipmentCount")
    private List<ShipmentSummary> shipments;

    @BookField(order = 40, pic = "X(4)", direction = OUTPUT)
    private String returnCode;
}
