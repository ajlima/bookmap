package pt.dazz.bookmap.examples;

import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One line of {@link UpdateShipment}.
 */
@Data
public class ShipmentLine {

    @BookField(order = 10, pic = "X(10)")
    private String sku;

    @BookField(order = 20, pic = "9(4)")
    private int quantity;
}
