package pt.dazz.copymapper.nativetest;

import static pt.dazz.copymapper.annotation.Direction.INPUT;
import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import pt.dazz.copymapper.annotation.Defaults;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;

/**
 * Adds a note to a shipment. The book uses IBM1047, not the default charset IBM037.
 * The characters {@code [} and {@code ]} have different codes in the two charsets.
 */
@Operation("ShipmentNote")
@Defaults(charset = "IBM1047")
public class ShipmentNote {

    @Field(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @Field(order = 20, length = 8, direction = INPUT)
    private String note;

    @Field(order = 30, length = 2, direction = OUTPUT)
    private String status;

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
