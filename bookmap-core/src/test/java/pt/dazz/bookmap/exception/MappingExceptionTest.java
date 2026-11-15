package pt.dazz.bookmap.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests the item prefix of {@link MappingException}. */
class MappingExceptionTest {

    @Test
    void messageWithoutItemIsUnchanged() {
        MappingException e = new MappingException("ShipmentLine.sku (order 1): text is long");

        assertThat(e.getMessage()).isEqualTo("ShipmentLine.sku (order 1): text is long");
    }

    @Test
    void nullMessageWithoutItemStaysNull() {
        assertThat(new MappingException(null).getMessage()).isNull();
    }

    @Test
    void atItemAddsTheCollectionAndTheIndex() {
        MappingException e = new MappingException("ShipmentLine.sku (order 1): text is long");

        MappingException result = e.atItem("CreateShipment.lines (order 30)", 2);

        assertThat(result).isSameAs(e);
        assertThat(e.getMessage()).isEqualTo(
            "CreateShipment.lines (order 30) item 2: ShipmentLine.sku (order 1): text is long");
    }

    @Test
    void outerLevelComesFirst() {
        MappingException e = new MappingException("ShipmentLine.sku (order 10): text is long");

        e.atItem("ShipmentPackage.lines (order 40)", 0).atItem("GetShipment.packages (order 30)",
            1);

        assertThat(e.getMessage()).isEqualTo("GetShipment.packages (order 30) item 1: "
            + "ShipmentPackage.lines (order 40) item 0: ShipmentLine.sku (order 10): text is long");
        assertThat(e.toString()).isEqualTo(MappingException.class.getName() + ": "
            + e.getMessage());
    }

    @Test
    void atItemKeepsTheClassTheCauseAndTheStackTrace() {
        IllegalStateException cause = new IllegalStateException("bad");
        MappingException e = new SpecialMapping("ShipmentLine.sku (order 1): bad", cause);
        StackTraceElement[] trace = e.getStackTrace();

        MappingException result = e.atItem("GetShipment.lines (order 30)", 0);

        assertThat(result).isSameAs(e).isInstanceOf(SpecialMapping.class);
        assertThat(result.getCause()).isSameAs(cause);
        assertThat(result.getStackTrace()).isEqualTo(trace);
        assertThat(result.getMessage())
            .isEqualTo("GetShipment.lines (order 30) item 0: ShipmentLine.sku (order 1): bad");
    }

    /** A subclass of the exception, as an application can declare. */
    private static final class SpecialMapping extends MappingException {

        private static final long serialVersionUID = 1L;

        SpecialMapping(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
