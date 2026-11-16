package pt.dazz.copymapper.examples.orders;

import java.util.List;
import lombok.Data;
import pt.dazz.copymapper.annotation.Occurs;
import pt.dazz.copymapper.annotation.Field;

/**
 * One order line with its taxes: 8 + 5 + 1 + 3 x 9 = 41 bytes. The inner collection is fixed,
 * and its count field {@code taxCount} is in the same entry.
 */
@Data
public class TaxedOrderLine {

    @Field(order = 10, pic = "X(8)")
    private String productCode;

    @Field(order = 20, pic = "9(5)")
    private int quantity;

    @Field(order = 30, pic = "9")
    private int taxCount;

    @Occurs(order = 40, maxOccurs = 3, fixed = true, dependsOn = "taxCount")
    private List<LineTax> taxes;
}
