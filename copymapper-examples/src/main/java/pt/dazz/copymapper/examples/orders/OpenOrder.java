package pt.dazz.copymapper.examples.orders;

import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * One open order in a list: 14 bytes.
 */
@Data
public class OpenOrder {

    @Field(order = 10, pic = "X(12)")
    private String orderId;

    @Field(order = 20, pic = "X(2)")
    private String orderStatus;
}
