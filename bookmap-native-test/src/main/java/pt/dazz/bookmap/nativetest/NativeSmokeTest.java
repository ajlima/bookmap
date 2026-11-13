package pt.dazz.bookmap.nativetest;

import java.util.Arrays;
import java.util.HexFormat;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.simulator.SimulatorTransport;

/**
 * Runs one operation through the simulator transport, in a native image.
 *
 * <p>The program exits with the code 1 if a check fails. The checks prove that the native image
 * contains the EBCDIC charset IBM1047 and that the generated codec works without extra
 * reachability metadata.
 */
public final class NativeSmokeTest {

    // "SHP000000042" and "[ORDER] " in IBM1047.
    private static final byte[] EXPECTED_REQUEST = HexFormat.of().parseHex(
        "e2c8d7f0f0f0f0f0f0f0f4f2" + "add6d9c4c5d9bd40");

    private NativeSmokeTest() {
    }

    public static void main(String[] args) {
        SimulatorTransport simulator = SimulatorTransport.builder().build();
        simulator.register(ShipmentNote.class,
            call -> call.setStatus("[ORDER]".equals(call.getNote()) ? "OK" : "KO"));
        BookmapClient client = BookmapClient.builder().transport(simulator).build();

        ShipmentNote request = new ShipmentNote();
        request.setShipmentId("SHP000000042");
        request.setNote("[ORDER]");
        ShipmentNote response = client.execute(request);

        byte[] payload = simulator.receivedRequests().get(0).payload();
        check(Arrays.equals(payload, EXPECTED_REQUEST),
            "request bytes are " + HexFormat.of().formatHex(payload));
        check("OK".equals(response.getStatus()), "status is " + response.getStatus());
        System.out.println("Native smoke test passed: IBM1047 request "
            + HexFormat.of().formatHex(payload) + ", status " + response.getStatus());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.err.println("Native smoke test failed: " + message);
            System.exit(1);
        }
    }
}
