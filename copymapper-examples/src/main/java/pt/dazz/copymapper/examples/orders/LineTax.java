package pt.dazz.copymapper.examples.orders;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * One tax of an order line: 9 bytes.
 */
@Data
public class LineTax {

    @Field(order = 10, pic = "X(4)")
    private String taxCode;

    @Field(order = 20, pic = "S9(7)V99 COMP-3")
    private BigDecimal taxAmount;
}
