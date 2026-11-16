package pt.dazz.copymapper.examples.shipments;

import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * The parts of a tracking code: a view of the same 16 bytes.
 */
@Data
public class TrackingParts {

    @Field(order = 10, pic = "X(4)")
    private String carrierCode;

    @Field(order = 20, pic = "9(6)")
    private int routeNumber;

    @Field(order = 30, pic = "9(6)")
    private int sequenceNumber;
}
