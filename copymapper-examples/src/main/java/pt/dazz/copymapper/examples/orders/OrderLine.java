package pt.dazz.copymapper.examples.orders;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * One line of an order: 18 bytes.
 */
@Data
public class OrderLine {

    @Field(order = 10, pic = "X(8)")
    private String productCode;

    @Field(order = 20, pic = "9(5)")
    private int quantity;

    @Field(order = 30, pic = "S9(7)V99 COMP-3")
    private BigDecimal unitPrice;
}
