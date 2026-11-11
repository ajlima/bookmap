package pt.daaz.bookmap.examples;

import static pt.daaz.bookmap.annotation.Direction.INPUT;
import static pt.daaz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import pt.daaz.bookmap.annotation.BookCollection;
import pt.daaz.bookmap.annotation.BookField;
import pt.daaz.bookmap.annotation.BookOperation;
import pt.daaz.bookmap.annotation.Usage;

/**
 * Updates a shipment. Explicit style. Lombok on some fields, handwritten accessors on the others.
 */
@BookOperation("UpdateShipment")
public class UpdateShipment {

    @Getter
    @Setter
    @BookField(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @BookField(order = 20, length = 9, scale = 2, signed = true, usage = Usage.COMP3, direction = INPUT)
    private BigDecimal weightKg;

    @Getter
    @Setter
    @BookField(order = 30, length = 3, direction = INPUT)
    private int lineCount;

    @BookCollection(order = 40, direction = INPUT, dependsOn = "lineCount", maxOccurs = 20)
    private List<ShipmentLine> lines;

    @BookField(order = 50, length = 2, direction = OUTPUT)
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
