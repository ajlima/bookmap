package pt.dazz.bookmap.examples.shipments;

import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * The parts of a tracking code: a view of the same 16 bytes.
 */
@Data
public class TrackingParts {

    @BookField(order = 10, pic = "X(4)")
    private String carrierCode;

    @BookField(order = 20, pic = "9(6)")
    private int routeNumber;

    @BookField(order = 30, pic = "9(6)")
    private int sequenceNumber;
}
