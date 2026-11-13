package pt.dazz.bookmap.nativetest;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import pt.dazz.bookmap.annotation.BookDefaults;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/**
 * Adds a note to a shipment. The book uses IBM1047, not the default charset IBM037.
 * The characters {@code [} and {@code ]} have different codes in the two charsets.
 */
@BookOperation("ShipmentNote")
@BookDefaults(charset = "IBM1047")
public class ShipmentNote {

    @BookField(order = 10, length = 12, direction = INPUT)
    private String shipmentId;

    @BookField(order = 20, length = 8, direction = INPUT)
    private String note;

    @BookField(order = 30, length = 2, direction = OUTPUT)
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
