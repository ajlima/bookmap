package pt.dazz.copymapper.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.copymapper.processor.TestCompiler.accessors;
import static pt.dazz.copymapper.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.convert.CodecContext;
import pt.dazz.copymapper.exception.MappingException;
import pt.dazz.copymapper.spi.BookCodec;

/** Tests a {@code REDEFINES} area without a discriminator: views of the same bytes. */
class RedefinesViewTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /**
     * shipmentId (INPUT, 10), trackingCode (OUTPUT, 16) with the view trackingParts,
     * resultCode (OUTPUT, 2), resultDetail (OUTPUT, 40) with the view deliveryData (32), and
     * shippingCharge (OUTPUT, 5).
     */
    private static JavaFileObject[] shipment() {
        return new JavaFileObject[] {
            source("GetShipment", "@Operation public class GetShipment {",
                field("@Field(order = 10, pic = \"X(10)\", direction = Direction.INPUT)",
                    "String", "shipmentId"),
                field("@Field(order = 20, pic = \"X(16)\", direction = Direction.OUTPUT)",
                    "String", "trackingCode"),
                field("@Group(order = 30, redefines = \"trackingCode\")", "TrackingParts",
                    "trackingParts"),
                field("@Field(order = 40, pic = \"X(2)\", direction = Direction.OUTPUT)",
                    "String", "resultCode"),
                field("@Group(order = 50, direction = Direction.OUTPUT)", "ResultDetail",
                    "resultDetail"),
                field("@Group(order = 60, redefines = \"resultDetail\")", "DeliveryData",
                    "deliveryData"),
                field("@Field(order = 70, pic = \"S9(7)V99 COMP-3\","
                    + " direction = Direction.OUTPUT)", "BigDecimal", "shippingCharge"),
                "}"),
            source("TrackingParts", "public class TrackingParts {",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "carrierCode"),
                field("@Field(order = 20, pic = \"9(6)\")", "int", "routeNumber"),
                field("@Field(order = 30, pic = \"9(6)\")", "int", "sequenceNumber"),
                "}"),
            source("ResultDetail", "public class ResultDetail {",
                field("@Field(order = 10, pic = \"X(40)\")", "String", "errorText"),
                "}"),
            source("DeliveryData", "public class DeliveryData {",
                field("@Field(order = 10, pic = \"9(8)\")", "LocalDate", "deliveryDate"),
                field("@Field(order = 20, pic = \"S9(5)V99 COMP-3\")", "BigDecimal",
                    "deliveredWeight"),
                field("@Field(order = 30, pic = \"X(20)\")", "String", "signedBy"),
                "}")};
    }

    private BookCodec<Object> codec(String name, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test." + name);
    }

    private void assertError(String message, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
    }

    /** An operation class with the given members. */
    private static JavaFileObject operation(String name, String... members) {
        List<String> lines = new ArrayList<>();
        lines.add("@Operation public class " + name + " {");
        lines.addAll(List.of(members));
        lines.add("}");
        return source(name, lines.toArray(String[]::new));
    }

    private static JavaFileObject part() {
        return source("Part", "public class Part {",
            field("@Field(order = 1, pic = \"X(2)\")", "String", "code"), "}");
    }

    // ---- helpers ----

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static Object read(BookCodec<Object> codec, byte[] response) {
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, LATIN);
        return target;
    }

    private static Object newBean(Object sibling, String simpleName) {
        try {
            return sibling.getClass().getClassLoader().loadClass("test." + simpleName)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object get(Object bean, String... path) {
        Object current = bean;
        for (String property : path) {
            current = CompiledCodec.get(current, property);
        }
        return current;
    }

    /** 20261116, 12.50 as S9(5)V99 COMP-3, and the signer: 32 bytes. */
    private static byte[] delivery() {
        return concat(bytes("20261116"), new byte[] {0x00, 0x01, 0x25, 0x0C},
            bytes(String.format("%-20s", "A. SMITH")));
    }

    /** 10.00 as S9(7)V99 COMP-3. */
    private static final byte[] CHARGE = {0x00, 0x00, 0x01, 0x00, 0x0C};

    // ---- read ----

    @Test
    void aReadDecodesAllViewsOfTheArea() {
        BookCodec<Object> codec = codec("GetShipment", shipment());
        Object target = read(codec, concat(bytes("CARR123456000789"), bytes("00"), delivery(),
            bytes("        "), CHARGE));
        assertThat(get(target, "trackingCode")).isEqualTo("CARR123456000789");
        assertThat(get(target, "trackingParts", "carrierCode")).isEqualTo("CARR");
        assertThat(get(target, "trackingParts", "routeNumber")).isEqualTo(123456);
        assertThat(get(target, "trackingParts", "sequenceNumber")).isEqualTo(789);
        assertThat(get(target, "deliveryData", "deliveryDate"))
            .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat((BigDecimal) get(target, "deliveryData", "deliveredWeight"))
            .isEqualByComparingTo("12.50");
        assertThat(get(target, "deliveryData", "signedBy")).isEqualTo("A. SMITH");
        assertThat((String) get(target, "resultDetail", "errorText")).startsWith("20261116");
        assertThat((BigDecimal) get(target, "shippingCharge")).isEqualByComparingTo("10.00");
        assertThat(codec.maxResponseLength()).isEqualTo(16 + 2 + 40 + 5);
    }

    @Test
    void aViewWithBytesThatAreNotValidIsNull() {
        BookCodec<Object> codec = codec("GetShipment", shipment());
        Object target = read(codec, concat(bytes("CARRABCDEF000789"), bytes("E1"),
            bytes(String.format("%-40s", "ADDRESS NOT FOUND")), CHARGE));
        assertThat(get(target, "trackingParts")).isNull();
        assertThat(get(target, "deliveryData")).isNull();
        assertThat(get(target, "resultDetail", "errorText")).isEqualTo("ADDRESS NOT FOUND");
        assertThat((BigDecimal) get(target, "shippingCharge")).isEqualByComparingTo("10.00");
    }

    @Test
    void aResponseThatEndsInsideTheAreaIsTooShort() {
        BookCodec<Object> codec = codec("GetShipment", shipment());
        byte[] response = concat(bytes("CARR123456000789"), bytes("00"), bytes("20261116ABCD"));
        assertThatThrownBy(() -> read(codec, response))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetShipment.resultDetail (order 50): response too short, need 40 bytes"
                + " at position 18 but only 12 remain");
    }

    // ---- write ----

    @Test
    void aWriteUsesTheFirstMemberThatIsNotNullAndFillsTheArea() {
        BookCodec<Object> codec = codec("GetShipment", shipment());
        Object value = codec.newInstance();
        CompiledCodec.set(value, "trackingCode", "CARR123456000789");
        CompiledCodec.set(value, "resultCode", "00");
        Object delivery = newBean(value, "DeliveryData");
        CompiledCodec.set(delivery, "deliveryDate", LocalDate.of(2026, 11, 16));
        CompiledCodec.set(delivery, "deliveredWeight", new BigDecimal("12.50"));
        CompiledCodec.set(delivery, "signedBy", "A. SMITH");
        CompiledCodec.set(value, "deliveryData", delivery);
        CompiledCodec.set(value, "shippingCharge", new BigDecimal("10.00"));
        assertThat(codec.encodeResponse(value, LATIN)).containsExactly(concat(
            bytes("CARR123456000789"), bytes("00"), delivery(), bytes("        "), CHARGE));

        Object detail = newBean(value, "ResultDetail");
        CompiledCodec.set(detail, "errorText", "LATE");
        CompiledCodec.set(value, "resultDetail", detail);
        assertThat(codec.encodeResponse(value, LATIN)).containsExactly(concat(
            bytes("CARR123456000789"), bytes("00"), bytes(String.format("%-40s", "LATE")),
            CHARGE));
    }

    @Test
    void aWriteOfAnAreaWithoutValuesGivesSpaces() {
        BookCodec<Object> codec = codec("GetShipment", shipment());
        Object value = codec.newInstance();
        byte[] encoded = codec.encodeResponse(value, LATIN);
        assertThat(encoded).hasSize(63);
        assertThat(new String(encoded, 0, 58, StandardCharsets.ISO_8859_1)).isBlank();
    }

    @Test
    void aViewOfAnElementaryItemReadsAndWritesTheSameBytes() {
        BookCodec<Object> codec = codec("GetCode", operation("GetCode",
            field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
            field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "Integer",
                "number"),
            field("@Field(order = 30, pic = \"X(1)\")", "String", "trailer")));
        Object target = read(codec, bytes("0042Z"));
        assertThat(get(target, "code")).isEqualTo("0042");
        assertThat(get(target, "number")).isEqualTo(42);
        assertThat(get(read(codec, bytes("AB12Z")), "number")).isNull();
        Object value = codec.newInstance();
        CompiledCodec.set(value, "number", 7);
        CompiledCodec.set(value, "trailer", "Z");
        assertThat(codec.encodeRequest(value, LATIN)).containsExactly(bytes("0007Z"));
        // After a read, the first member that is not null wins.
        CompiledCodec.set(target, "number", 99);
        assertThat(codec.encodeRequest(target, LATIN)).containsExactly(bytes("0042Z"));
        assertThat(codec.decodeRequest(bytes("0042Z"), LATIN)).isNotNull();
    }

    @Test
    void aShorterViewIsFollowedBySpaces() {
        BookCodec<Object> codec = codec("GetCode", operation("GetCode",
            field("@Field(order = 10, pic = \"X(6)\")", "String", "code"),
            field("@Field(order = 20, pic = \"9(2)\", redefines = \"code\")", "Integer",
                "prefix")));
        Object value = codec.newInstance();
        CompiledCodec.set(value, "prefix", 12);
        assertThat(codec.encodeRequest(value, LATIN)).containsExactly(bytes("12    "));
        assertThat(get(read(codec, bytes("34ABCD")), "prefix")).isEqualTo(34);
    }

    @Test
    void aLongerViewGivesTheLengthOfTheArea() {
        BookCodec<Object> codec = codec("GetCode", operation("GetCode",
            field("@Field(order = 10, pic = \"X(2)\", direction = Direction.OUTPUT)",
                "String", "code"),
            field("@Field(order = 20, pic = \"X(5)\", redefines = \"code\")", "String",
                "longCode"),
            field("@Field(order = 30, pic = \"X(1)\", direction = Direction.OUTPUT)",
                "String", "trailer")));
        assertThat(codec.maxResponseLength()).isEqualTo(6);
        Object target = read(codec, bytes("ABCDEZ"));
        assertThat(get(target, "code")).isEqualTo("AB");
        assertThat(get(target, "longCode")).isEqualTo("ABCDE");
        assertThat(get(target, "trailer")).isEqualTo("Z");
    }

    // ---- collections ----

    @Test
    void aCollectionViewReadsAndWritesTheItems() {
        BookCodec<Object> codec = codec("GetParts", operation("GetParts",
            field("@Field(order = 10, pic = \"X(6)\", direction = Direction.OUTPUT)",
                "String", "raw"),
            field("@Occurs(order = 20, maxOccurs = 3, fixed = true,"
                + " redefines = \"raw\")", "List<Part>", "parts")), part());
        Object target = read(codec, bytes("AABBCC"));
        assertThat((List<?>) get(target, "parts")).hasSize(3);
        codec.decodeResponse(bytes("DDEE\0\0"), target, LATIN);
        assertThat((List<?>) get(target, "parts")).hasSize(2);
        Object value = codec.newInstance();
        Object item = newBean(value, "Part");
        CompiledCodec.set(item, "code", "XY");
        CompiledCodec.set(value, "parts", List.of(item));
        assertThat(codec.encodeResponse(value, LATIN))
            .containsExactly(concat(bytes("XY"), new byte[4]));
        codec.resetOutputCollections(target);
        assertThat((List<?>) get(target, "parts")).hasSize(2);
    }

    @Test
    void anAreaInAnItemGivesTheItemPrefix() {
        BookCodec<Object> codec = codec("GetRoute", operation("GetRoute",
                field("@Field(order = 10, pic = \"9(2)\", direction = Direction.OUTPUT)",
                    "int", "stopCount"),
                field("@Occurs(order = 20, maxOccurs = 5)", "List<Stop>", "stops")),
            source("Stop", "public class Stop {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 2, pic = \"9(4)\", redefines = \"code\")", "Long",
                    "number"),
                "}"));
        Object target = read(codec, bytes("021234ABCD"));
        List<?> stops = (List<?>) get(target, "stops");
        assertThat(get(stops.get(0), "number")).isEqualTo(1234L);
        assertThat(get(stops.get(1), "number")).isNull();
        assertThatThrownBy(() -> read(codec, bytes("021234AB")))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetRoute.stops (order 20) item 1: Stop.code (order 1): response too"
                + " short, need 4 bytes at position 6 but only 2 remain");
        Object value = codec.newInstance();
        Object stop = newBean(value, "Stop");
        CompiledCodec.set(stop, "number", 5L);
        CompiledCodec.set(value, "stops", List.of(stop));
        assertThat(codec.encodeResponse(value, LATIN)).containsExactly(bytes("010005"));
    }

    // ---- nested areas, subtypes, converters, enums ----

    @Test
    void aGroupViewCanHoldAnotherArea() {
        BookCodec<Object> codec = codec("GetLimits", operation("GetLimits",
                field("@Field(order = 10, pic = \"X(6)\")", "String", "raw"),
                field("@Group(order = 20, redefines = \"raw\")", "Limits", "limits")),
            source("Limits", "public class Limits {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "kind"),
                field("@Field(order = 2, pic = \"X(4)\")", "String", "text"),
                field("@Field(order = 3, pic = \"9(4)\", redefines = \"text\")", "Integer",
                    "amount"),
                "}"));
        Object target = read(codec, bytes("CR0500"));
        assertThat(get(target, "limits", "amount")).isEqualTo(500);
        Object value = codec.newInstance();
        Object limits = newBean(value, "Limits");
        CompiledCodec.set(limits, "kind", "DB");
        CompiledCodec.set(limits, "amount", 42);
        CompiledCodec.set(value, "limits", limits);
        assertThat(codec.encodeRequest(value, LATIN)).containsExactly(bytes("DB0042"));
    }

    @Test
    void aSubtypeCopiesTheMembersOfABaseArea() {
        BookCodec<Object> codec = codec("GetAccount",
            source("GetAccount",
                "@Operation",
                "@SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"S\", type = Savings.class)})",
                "public class GetAccount {",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "Integer",
                    "number"),
                field("@Field(order = 30, pic = \"X\")", "String", "kind"),
                "}"),
            source("Savings", "public class Savings extends GetAccount {",
                field("@Field(order = 40, pic = \"X(2)\")", "String", "rate"),
                field("@Field(order = 50, pic = \"9(2)\", redefines = \"rate\")", "Integer",
                    "rateNumber"),
                "}"));
        Object request = codec.newInstance();
        CompiledCodec.set(request, "number", 12);
        Object response = codec.readResponse(bytes("0012S05"), request, LATIN);
        assertThat(response.getClass().getSimpleName()).isEqualTo("Savings");
        assertThat(get(response, "number")).isEqualTo(12);
        assertThat(get(response, "rateNumber")).isEqualTo(5);
    }

    @Test
    void aFailureOfAConverterIsNotAnInvalidValue() {
        BookCodec<Object> codec = codec("GetFlag", operation("GetFlag",
                field("@Field(order = 10, pic = \"X(1)\")", "String", "code"),
                field("@Field(order = 20, length = 1, redefines = \"code\","
                    + " converter = FailingConverter.class)", "String", "flag")),
            source("FailingConverter",
                "public class FailingConverter implements FieldConverter<String> {",
                "  public String decode(byte[] bytes, FieldContext context) {",
                "    throw new IllegalStateException(\"bad flag\"); }",
                "  public byte[] encode(String value, FieldContext context) {",
                "    return new byte[] {65}; }",
                "}"));
        assertThatThrownBy(() -> read(codec, bytes("Y")))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("converter failed");
    }

    @Test
    void anUnknownEnumValueInAnAreaIsNull() {
        BookCodec<Object> codec = codec("GetStatus", operation("GetStatus",
                field("@Field(order = 10, pic = \"X(1)\")", "String", "code"),
                field("@Field(order = 20, pic = \"X(1)\", redefines = \"code\")", "Status",
                    "status")),
            source("Status", "public enum Status {",
                "  @CobolValue(\"A\") ACTIVE, @CobolValue(\"C\") CLOSED }"));
        assertThat(get(read(codec, bytes("A")), "status").toString()).isEqualTo("ACTIVE");
        assertThat(get(read(codec, bytes("X")), "status")).isNull();
    }

    // ---- model ----

    @Test
    void snapshotHasTheAreaAndTheGroups() {
        compiler.compile(shipment());
        List<ModelSnapshot.Member> members = compiler.only().members();
        assertThat(members).extracting(ModelSnapshot.Member::name).containsExactly(
            "shipmentId", "trackingCode", "resultCode", "resultDetail", "shippingCharge");
        ModelSnapshot.Member area = members.get(3);
        assertThat(area.javaKind()).isEqualTo("AREA");
        assertThat(area.byteLength()).isEqualTo(40);
        assertThat(area.direction()).isEqualTo("OUTPUT");
        assertThat(area.itemMembers()).extracting(ModelSnapshot.Member::name)
            .containsExactly("resultDetail", "deliveryData");
        ModelSnapshot.Member delivery = area.itemMembers().get(1);
        assertThat(delivery.javaKind()).isEqualTo("GROUP");
        assertThat(delivery.direction()).isEqualTo("OUTPUT");
        assertThat(delivery.byteLength()).isEqualTo(32);
        assertThat(delivery.itemMembers()).hasSize(3);
    }

    // ---- compile errors ----

    @Test
    void aViewMustNameAMemberOfTheClass() {
        assertError("GetCode.number (order 20): redefines member 'kode' does not exist in"
            + " GetCode", operation("GetCode",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9(4)\", redefines = \"kode\")", "Integer",
                    "number")));
    }

    @Test
    void aViewMustNameTheRedefinedMember() {
        assertError("GetCode.text (order 30): redefines member 'number' is a view. Name the"
            + " redefined member 'code'", operation("GetCode",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "Integer",
                    "number"),
                field("@Field(order = 30, pic = \"X(4)\", redefines = \"number\")", "String",
                    "text")));
    }

    @Test
    void theRedefinedMemberMustComeFirst() {
        assertError("GetCode.number (order 10): redefines member 'code' must come before the"
            + " view", operation("GetCode",
                field("@Field(order = 20, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 10, pic = \"9(4)\", redefines = \"code\")", "Integer",
                    "number")));
    }

    @Test
    void aViewMustComeRightAfterTheRedefinedMember() {
        assertError("GetCode.number (order 30): a view must come right after the member it"
            + " redefines: GetCode.status (order 20) is between them", operation("GetCode",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"X(1)\")", "String", "status"),
                field("@Field(order = 30, pic = \"9(4)\", redefines = \"code\")", "Integer",
                    "number")));
    }

    @Test
    void aViewMustNotSetDirection() {
        assertError("GetCode.number (order 20): a view must not set direction. It uses the"
            + " direction of GetCode.code (order 10)", operation("GetCode",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\","
                    + " direction = Direction.OUTPUT)", "Integer", "number")));
    }

    @Test
    void aMemberOfAnAreaMustNotBePrimitive() {
        assertError("GetCode.number (order 20): a member of a REDEFINES area must not have a"
            + " primitive type. Use Integer, Long, or BigDecimal", operation("GetCode",
                field("@Field(order = 10, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "int",
                    "number")));
    }

    @Test
    void aCollectionInAnAreaMustBeFixedWithoutCount() {
        assertError("GetParts.parts (order 20): a collection in a REDEFINES area must have"
            + " fixed = true and no dependsOn", operation("GetParts",
                field("@Field(order = 5, pic = \"9\")", "int", "count"),
                field("@Field(order = 10, pic = \"X(6)\")", "String", "raw"),
                field("@Occurs(order = 20, maxOccurs = 3, fixed = true,"
                    + " dependsOn = \"count\", redefines = \"raw\")", "List<Part>", "parts")),
            part());
    }

    @Test
    void aCollectionInAnAreaMustNotBePartial() {
        assertError("GetParts.parts (order 20): partial is not permitted in a REDEFINES area",
            operation("GetParts",
                field("@Field(order = 10, pic = \"X(6)\", direction = Direction.OUTPUT)",
                    "String", "raw"),
                field("@Occurs(order = 20, maxOccurs = 3, fixed = true, partial = true,"
                    + " redefines = \"raw\")", "List<Part>", "parts")),
            part());
    }

    @Test
    void aViewCollectionInAContinuableClassMustNotBeInputOutput() {
        assertError("GetParts.parts (order 20): an INPUT_OUTPUT collection is not permitted in"
            + " a Continuable class", source("GetParts",
                "@Operation public class GetParts implements Continuable {",
                field("@Field(order = 10, pic = \"X(6)\")", "String", "raw"),
                field("@Occurs(order = 20, maxOccurs = 3, fixed = true,"
                    + " redefines = \"raw\")", "List<Part>", "parts"),
                "public boolean hasContinuation() { return false; }",
                "}"),
            part());
    }

    @Test
    void aCountFieldMustNotBeInAnArea() {
        assertError("GetParts.parts (order 30): dependsOn field 'number' must not be in a"
            + " REDEFINES area", operation("GetParts",
                field("@Field(order = 10, pic = \"X(1)\")", "String", "code"),
                field("@Field(order = 20, pic = \"9\", redefines = \"code\")", "Integer",
                    "number"),
                field("@Occurs(order = 30, maxOccurs = 3)", "List<Part>", "parts")),
            part());
    }

    @Test
    void aDiscriminatorMustNotBeInAnArea() {
        assertError("GetAccount.kind (order 20): discriminator must not be in a REDEFINES area",
            source("GetAccount",
                "@Operation",
                "@SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"S\", type = Savings.class)})",
                "public class GetAccount {",
                field("@Field(order = 10, pic = \"X(1)\")", "String", "code"),
                field("@Field(order = 20, pic = \"X(1)\", redefines = \"code\")", "String",
                    "kind"),
                "}"),
            source("Savings", "public class Savings extends GetAccount {",
                field("@Field(order = 40, pic = \"X(2)\")", "String", "rate"), "}"));
    }

    @Test
    void anEmptyKeyMustNotBeInAnArea() {
        assertError("GetParts.parts (order 10): emptyKey field 'number' must not be in a"
            + " REDEFINES area", operation("GetParts",
                field("@Occurs(order = 10, maxOccurs = 3, fixed = true,"
                    + " emptyKey = \"number\")", "List<Item>", "parts")),
            source("Item", "public class Item {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "code"),
                field("@Field(order = 2, pic = \"9(2)\", redefines = \"code\")", "Integer",
                    "number"),
                "}"));
    }

    @Test
    void aGroupMustBeInAnArea() {
        assertError("GetCode.detail (order 10): a @Group must be in a REDEFINES area",
            operation("GetCode",
                field("@Group(order = 10)", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "code"), "}"));
    }

    @Test
    void aFieldMustHaveOnlyOneAnnotation() {
        assertError("GetCode.detail (order 10): a field must have only one of @Field,"
            + " @Occurs, and @Group", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\") @Group(order = 10)", "String",
                    "detail"),
                field("@Field(order = 20, pic = \"X(2)\")", "String", "other")));
    }

    @Test
    void aGroupClassMustBeInstantiable() {
        assertError("GetCode.detail (order 20): group class java.util.List<java.lang.String>"
            + " must be a public class with a public no-argument constructor",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "List<String>",
                    "detail")));
        assertError("GetCode.detail (order 20): group class int must be a public class",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "int", "detail")));
    }

    @Test
    void aGroupClassMustNotBeGeneric() {
        assertError("GetCode.detail (order 20): group class test.Detail must not have type"
            + " parameters", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail<String>",
                    "detail")),
            source("Detail", "public class Detail<T> {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "code"), "}"));
    }

    @Test
    void aGroupClassMustHaveAField() {
        assertError("GetCode.detail (order 20): group class test.Detail must have at least one"
            + " @Field", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail { }"));
    }

    @Test
    void aGroupClassMustNotHaveSubtypes() {
        assertError("GetCode.detail (order 20): group class test.Detail must not have"
            + " @SubTypes", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail",
                "@SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"A\", type = Detail.class)})",
                "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "kind"), "}"));
    }

    @Test
    void aGroupClassMustNotContainItself() {
        assertError("Detail.inner (order 2): group class Detail contains itself: Detail > Detail",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"),
                field("@Group(order = 2, redefines = \"text\")", "Detail", "inner"), "}"));
    }

    @Test
    void aGroupInAGroupClassMustNotSetDirection() {
        assertError("Detail.inner (order 2): direction must not be set on an item field",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"),
                field("@Group(order = 2, redefines = \"text\", direction = Direction.INPUT)",
                    "Inner", "inner"), "}"),
            source("Inner", "public class Inner {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "value"), "}"));
    }

    @Test
    void aGroupNeedsAccessors() {
        assertError("GetCode.detail (order 20): missing getter getDetail", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                "@Group(order = 20, redefines = \"code\") private Detail detail;"),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
    }

    // ---- more rules ----

    @Test
    void aViewOfAnUnresolvedMemberIsLeftToJavac() {
        Compilation compilation = compiler.compile(operation("GetCode",
            field("@Field(order = 10, length = MISSING)", "String", "code"),
            field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "Integer",
                "number")));
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void aViewOfAMemberWithErrorsGetsNoOtherError() {
        Compilation compilation = compiler.compile(operation("GetCode",
            field("@Field(order = 10, pic = \"Q(4)\")", "String", "code"),
            field("@Field(order = 20, pic = \"9(4)\", redefines = \"code\")", "Integer",
                "number")));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void aFieldMustNotHaveBothBookCollectionAndBookGroup() {
        assertError("GetCode.detail (order 10): a field must have only one of @Field,"
            + " @Occurs, and @Group", operation("GetCode",
                field("@Occurs(order = 10, maxOccurs = 2) @Group(order = 10)",
                    "List<Part>", "detail"),
                field("@Field(order = 20, pic = \"X(2)\")", "String", "other")), part());
    }

    @Test
    void aVariableCollectionMustNotBeInAnArea() {
        assertError("GetParts.parts (order 20): a collection in a REDEFINES area must have"
            + " fixed = true and no dependsOn", operation("GetParts",
                field("@Field(order = 10, pic = \"9\")", "int", "count"),
                field("@Occurs(order = 20, maxOccurs = 3, dependsOn = \"count\")",
                    "List<Part>", "parts"),
                field("@Field(order = 30, pic = \"X(6)\", redefines = \"parts\")", "String",
                    "raw")),
            part());
    }

    @Test
    void aContinuableClassAcceptsAnOutputCollectionInAnArea() {
        BookCodec<Object> codec = codec("GetParts", source("GetParts",
                "@Operation public class GetParts implements Continuable {",
                field("@Occurs(order = 10, maxOccurs = 3, fixed = true)", "List<Part>",
                    "parts"),
                field("@Field(order = 20, pic = \"X(6)\", redefines = \"parts\")", "String",
                    "raw"),
                field("@Field(order = 30, pic = \"X(6)\", direction = Direction.OUTPUT)",
                    "String", "next"),
                field("@Occurs(order = 40, maxOccurs = 3, fixed = true)", "List<Part>",
                    "more"),
                field("@Occurs(order = 50, maxOccurs = 3, fixed = true,"
                    + " redefines = \"more\")", "List<Part>", "moreParts"),
                "public boolean hasContinuation() { return false; }",
                "}"),
            part());
        Object target = read(codec, bytes("AABBCCNEXT  DDEEFF"));
        assertThat(get(target, "raw")).isEqualTo("AABBCC");
        assertThat((List<?>) get(target, "moreParts")).hasSize(3);
    }

    @Test
    void aGroupViewOfACollectionHasTheDirectionOfTheCollection() {
        compiler.compile(operation("GetParts",
                field("@Occurs(order = 10, maxOccurs = 1, fixed = true)", "List<Part>",
                    "parts"),
                field("@Group(order = 20, redefines = \"parts\")", "Detail", "detail")),
            part(),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
        ModelSnapshot.Member area = compiler.only().members().get(0);
        assertThat(area.itemMembers()).extracting(ModelSnapshot.Member::direction)
            .containsExactly("OUTPUT", "OUTPUT");
    }

    @Test
    void twoGroupsOfOneClassShareTheMethods() {
        BookCodec<Object> codec = codec("GetCodes", operation("GetCodes",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "first"),
                field("@Group(order = 20, redefines = \"first\")", "Detail", "firstDetail"),
                field("@Field(order = 30, pic = \"X(2)\")", "String", "second"),
                field("@Group(order = 40, redefines = \"second\")", "Detail",
                    "secondDetail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
        Object target = read(codec, bytes("ABCD"));
        assertThat(get(target, "secondDetail", "text")).isEqualTo("CD");
    }

    @Test
    void aGroupCycleNamesOnlyTheClassesInTheCycle() {
        assertError("Detail.inner (order 2): group class Detail contains itself: Detail > Detail",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Outer", "outer")),
            source("Outer", "public class Outer {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"),
                field("@Group(order = 2, redefines = \"text\")", "Detail", "detail"), "}"),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"),
                field("@Group(order = 2, redefines = \"text\")", "Detail", "inner"), "}"));
    }

    @Test
    void aGroupNeedsASetter() {
        assertError("GetCode.detail (order 20): missing setter setDetail", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                "@Group(order = 20, redefines = \"code\") private Detail detail;"
                    + " public Detail getDetail() { return detail; }"),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
    }

    @Test
    void aFieldOfAGroupClassNeedsAccessors() {
        assertError("Detail.text (order 1): missing getter getText", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                "@Field(order = 1, pic = \"X(2)\") private String text;", "}"));
    }

    @Test
    void aGroupClassMustNotBeTooLong() {
        assertError("GetCode.detail (order 20): group class Detail has 3000000000 bytes, more"
            + " than the maximum of 2147483647 bytes", operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, length = 1500000000)", "String", "first"),
                field("@Field(order = 2, length = 1500000000)", "String", "second"), "}"));
    }

    @Test
    void theFieldsOfAGroupHaveTheDirectionOfTheArea() {
        compiler.compile(operation("GetParts",
                field("@Occurs(order = 10, maxOccurs = 1, fixed = true)", "List<Part>",
                    "parts"),
                field("@Group(order = 20, redefines = \"parts\")", "Detail", "detail")),
            part(),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
        ModelSnapshot.Member group = compiler.only().members().get(0).itemMembers().get(1);
        assertThat(group.itemMembers().get(0).direction()).isEqualTo("OUTPUT");
    }

    @Test
    void aGroupInAnItemClassHasTheDirectionOfTheCollection() {
        compiler.compile(operation("GetStops",
                field("@Occurs(order = 10, maxOccurs = 2, fixed = true,"
                    + " direction = Direction.INPUT)", "List<Stop>", "stops")),
            source("Stop", "public class Stop {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 2, redefines = \"code\")", "Detail", "detail"), "}"),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "text"), "}"));
        ModelSnapshot.Member area = compiler.only().members().get(0).itemMembers().get(0);
        ModelSnapshot.Member group = area.itemMembers().get(1);
        assertThat(group.direction()).isEqualTo("INPUT");
        assertThat(group.itemMembers().get(0).direction()).isEqualTo("INPUT");
    }

    @Test
    void aGroupClassMustNotHaveADuplicateOrder() {
        assertError("Detail.second (order 1): duplicate order, also used by Detail.first",
            operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(1)\")", "String", "first"),
                field("@Field(order = 1, pic = \"X(1)\")", "String", "second"), "}"));
    }

    @Test
    void aGroupClassCanHaveTheMaximumLength() {
        Compilation compilation = compiler.compile(operation("GetCode",
                field("@Field(order = 10, pic = \"X(2)\")", "String", "code"),
                field("@Group(order = 20, redefines = \"code\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, length = 1073741824)", "String", "first"),
                field("@Field(order = 2, length = 1073741823)", "String", "second"), "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).byteLength()).isEqualTo(Integer.MAX_VALUE);
    }
}
