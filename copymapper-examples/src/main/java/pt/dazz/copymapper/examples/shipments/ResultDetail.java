package pt.dazz.copymapper.examples.shipments;

import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * The detail of a failed shipment.
 */
@Data
public class ResultDetail {

    @Field(order = 10, pic = "X(40)")
    private String errorText;
}
