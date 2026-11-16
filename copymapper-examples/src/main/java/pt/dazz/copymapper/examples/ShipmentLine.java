package pt.dazz.copymapper.examples;

import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * One line of {@link UpdateShipment}.
 */
@Data
public class ShipmentLine {

    @Field(order = 10, pic = "X(10)")
    private String sku;

    @Field(order = 20, pic = "9(4)")
    private int quantity;
}
