package pt.dazz.copymapper.examples.shipments;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import pt.dazz.copymapper.annotation.Field;

/**
 * The detail of a delivered shipment: 32 bytes over the 40 bytes of the result detail.
 */
@Data
public class DeliveryData {

    @Field(order = 10, pic = "9(8)")
    private LocalDate deliveryDate;

    @Field(order = 20, pic = "S9(5)V99 COMP-3")
    private BigDecimal deliveredWeight;

    @Field(order = 30, pic = "X(20)")
    private String signedBy;
}
