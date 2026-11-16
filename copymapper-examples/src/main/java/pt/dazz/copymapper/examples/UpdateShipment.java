package pt.dazz.copymapper.examples;

import static pt.dazz.copymapper.annotation.Direction.INPUT;
import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import pt.dazz.copymapper.annotation.Occurs;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;
import pt.dazz.copymapper.cobol.Usage;

/**
 * Updates a shipment. The class uses the explicit style. Some fields have Lombok accessors, and
 * the other fields have handwritten accessors.
 */
@Operation("UpdateShipment")
public class UpdateShipment {

    @Getter
    @Setter
    @Field(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @Field(order = 20, length = 9, scale = 2, signed = true, usage = Usage.COMP3, direction = INPUT)
    private BigDecimal weightKg;

    @Getter
    @Setter
    @Field(order = 30, length = 3, direction = INPUT)
    private int lineCount;

    @Occurs(order = 40, direction = INPUT, dependsOn = "lineCount", maxOccurs = 20)
    private List<ShipmentLine> lines;

    @Field(order = 50, length = 2, direction = OUTPUT)
    private String status;

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public List<ShipmentLine> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentLine> lines) {
        this.lines = lines;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
