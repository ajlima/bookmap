package pt.dazz.copymapper.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.BookmapClient;
import pt.dazz.copymapper.examples.copybook.GetShipmentStatus;
import pt.dazz.copymapper.examples.shipments.DeliveryData;
import pt.dazz.copymapper.examples.shipments.GetShipment;
import pt.dazz.copymapper.examples.shipments.ResultDetail;
import pt.dazz.copymapper.simulator.SimulatorTransport;

/**
 * Runs the {@code REDEFINES} views example through the client and the simulator.
 */
class RedefinesViewsEndToEndTest {

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static GetShipment request(String shipmentId) {
        GetShipment request = new GetShipment();
        request.setShipmentId(shipmentId);
        return request;
    }

    @Test
    void aDeliveredShipmentHasTheDeliveryView() {
        simulator.register(GetShipment.class, call -> {
            call.setTrackingCode("CARR123456000789");
            call.setResultCode("00");
            DeliveryData delivery = new DeliveryData();
            delivery.setDeliveryDate(LocalDate.of(2026, 11, 16));
            delivery.setDeliveredWeight(new BigDecimal("12.50"));
            delivery.setSignedBy("A. SMITH");
            call.setDeliveryData(delivery);
            call.setShippingCharge(new BigDecimal("10.00"));
        });

        GetShipment result = client.execute(request("SHP-000001"));

        assertThat(result.delivered()).isTrue();
        assertThat(result.getTrackingParts().getCarrierCode()).isEqualTo("CARR");
        assertThat(result.getTrackingParts().getRouteNumber()).isEqualTo(123456);
        assertThat(result.getTrackingParts().getSequenceNumber()).isEqualTo(789);
        assertThat(result.getDeliveryData().getDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(result.getDeliveryData().getDeliveredWeight()).isEqualByComparingTo("12.50");
        assertThat(result.getDeliveryData().getSignedBy()).isEqualTo("A. SMITH");
        assertThat(result.getShippingCharge()).isEqualByComparingTo("10.00");
    }

    @Test
    void aFailedShipmentHasTheErrorView() {
        simulator.register(GetShipment.class, call -> {
            call.setTrackingCode("CARR-NOT-FOUND");
            call.setResultCode("E1");
            ResultDetail detail = new ResultDetail();
            detail.setErrorText("ADDRESS NOT FOUND");
            call.setResultDetail(detail);
            call.setShippingCharge(BigDecimal.ZERO);
        });

        GetShipment result = client.execute(request("SHP-000002"));

        assertThat(result.delivered()).isFalse();
        assertThat(result.getResultDetail().getErrorText()).isEqualTo("ADDRESS NOT FOUND");
        // The bytes are not a valid date or tracking number, so these views are null.
        assertThat(result.getDeliveryData()).isNull();
        assertThat(result.getTrackingParts()).isNull();
    }

    @Test
    void theGeneratorMapsTheAreasToViews() {
        simulator.register(GetShipmentStatus.class, call -> {
            call.setTrackingCode("CARR123456000789");
            call.setResultCode("00");
            pt.dazz.copymapper.examples.copybook.DeliveryData delivery =
                new pt.dazz.copymapper.examples.copybook.DeliveryData();
            delivery.setDeliveryDate(LocalDate.of(2026, 11, 16));
            delivery.setDeliveredWeight(new BigDecimal("12.50"));
            delivery.setSignedBy("A. SMITH");
            call.setDeliveryData(delivery);
            call.setResultDetail(null);
            call.setTrackingParts(null);
            call.setShippingCharge(new BigDecimal("10.00"));
        });
        GetShipmentStatus request = new GetShipmentStatus();
        request.setShipmentId("SHP-000003");

        GetShipmentStatus result = client.execute(request);

        assertThat(result.getTrackingParts().getRouteNumber()).isEqualTo(123456);
        assertThat(result.getDeliveryData().getDeliveryDate())
            .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(result.getDeliveryData().getSignedBy()).isEqualTo("A. SMITH");
        assertThat(result.getResultDetail()).isNotNull();
    }
}
