package pt.dazz.bookmap.examples.orders;

import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One shipment in a list: 14 bytes.
 */
@Data
public class ShipmentSummary {

    @BookField(order = 10, pic = "X(12)")
    private String shipmentId;

    @BookField(order = 20, pic = "X(2)")
    private String status;
}
