package pt.dazz.bookmap.examples.orders;

import java.util.List;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One order line with its taxes: 8 + 5 + 1 + 3 x 9 = 41 bytes. The inner collection is fixed,
 * and its count field {@code taxCount} is in the same entry.
 */
@Data
public class TaxedOrderLine {

    @BookField(order = 10, pic = "X(8)")
    private String productCode;

    @BookField(order = 20, pic = "9(5)")
    private int quantity;

    @BookField(order = 30, pic = "9")
    private int taxCount;

    @BookCollection(order = 40, maxOccurs = 3, fixed = true, dependsOn = "taxCount")
    private List<LineTax> taxes;
}
