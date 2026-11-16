package pt.dazz.bookmap.examples.shipments;

import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * The detail of a failed shipment.
 */
@Data
public class ResultDetail {

    @BookField(order = 10, pic = "X(40)")
    private String errorText;
}
