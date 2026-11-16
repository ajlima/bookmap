package pt.dazz.copymapper.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.copymapper.processor.TestCompiler.accessors;
import static pt.dazz.copymapper.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.convert.CodecContext;
import pt.dazz.copymapper.exception.MappingException;
import pt.dazz.copymapper.spi.BookCodec;

/** Tests a fixed collection with {@code partial = true}: a response that ends inside the area. */
class PartialCollectionTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final String ORDERS = "ListOpenOrders.orders (order 40)";

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /**
     * customerId (INPUT, 10), returnCode (OUTPUT, 4), orderCount (2), orders (fixed,
     * maxOccurs 3, 14 bytes each), then the extra members.
     */
    private static JavaFileObject listOpenOrders(String attributes, String... extra) {
        List<String> lines = new ArrayList<>(List.of(
            "@Operation public class ListOpenOrders {",
            field("@Field(order = 10, pic = \"X(10)\", direction = Direction.INPUT)",
                "String", "customerId"),
            field("@Field(order = 20, pic = \"X(4)\", direction = Direction.OUTPUT)",
                "String", "returnCode"),
            field("@Field(order = 30, pic = \"9(2)\", direction = Direction.OUTPUT)", "int",
                "orderCount"),
            field("@Occurs(order = 40, maxOccurs = 3, fixed = true, partial = true"
                + attributes + ")", "List<OpenOrder>", "orders")));
        lines.addAll(List.of(extra));
        lines.add("}");
        return source("ListOpenOrders", lines.toArray(String[]::new));
    }

    /** orderId (12), orderStatus (2): 14 bytes. */
    private static JavaFileObject openOrder() {
        return source("OpenOrder", "public class OpenOrder {",
            field("@Field(order = 1, pic = \"X(12)\")", "String", "orderId"),
            field("@Field(order = 2, pic = \"X(2)\")", "String", "orderStatus"),
            "}");
    }

    private BookCodec<Object> codec(String name, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test." + name);
    }

    private BookCodec<Object> countCodec() {
        return codec("ListOpenOrders", listOpenOrders(", dependsOn = \"orderCount\""),
            openOrder());
    }

    private BookCodec<Object> markerCodec(String attributes) {
        return codec("ListOpenOrders", listOpenOrders(attributes), openOrder());
    }

    private void assertError(String message, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
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

    private static byte[] order(int number) {
        return bytes("ORD-" + String.format("%08d", number) + "OP");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> orders(Object bean) {
        return (List<Object>) CompiledCodec.get(bean, "orders");
    }

    private static List<Object> orderIds(Object bean) {
        return orders(bean).stream().map(order -> CompiledCodec.get(order, "orderId")).toList();
    }

    private static Object read(BookCodec<Object> codec, byte[] response) {
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, LATIN);
        return target;
    }

    // ---- count mode ----

    @Test
    void countModeReadsAResponseWithTheUsedSlotsOnly() {
        Object target = read(countCodec(), concat(bytes("0000"), bytes("02"), order(1),
            order(2)));
        assertThat(orderIds(target)).containsExactly("ORD-00000001", "ORD-00000002");
        assertThat(CompiledCodec.get(target, "returnCode")).isEqualTo("0000");
    }

    @Test
    void countModeReadsTheFullArea() {
        Object target = read(countCodec(), concat(bytes("000003"), order(1), order(2), order(3),
            bytes("extra")));
        assertThat(orderIds(target)).hasSize(3);
    }

    @Test
    void countModeIgnoresThePresentSlotsAfterTheCount() {
        Object target = read(countCodec(), concat(bytes("000001"), order(1), order(2)));
        assertThat(orderIds(target)).containsExactly("ORD-00000001");
    }

    @Test
    void countModeReadsAResponseThatEndsAtTheStartOfTheArea() {
        assertThat(orders(read(countCodec(), bytes("000000")))).isEmpty();
    }

    @Test
    void countModeRejectsACountLargerThanThePresentSlots() {
        BookCodec<Object> codec = countCodec();
        assertThatThrownBy(() -> read(codec, concat(bytes("000003"), order(1), order(2))))
            .isInstanceOf(MappingException.class)
            .hasMessage(ORDERS + ": count 3 is more than the number of slots in the response (2)");
    }

    @Test
    void countModeChecksTheRangeOfTheCountFirst() {
        BookCodec<Object> codec = countCodec();
        assertThatThrownBy(() -> read(codec, concat(bytes("000004"), order(1))))
            .isInstanceOf(MappingException.class)
            .hasMessage(ORDERS + ": count 4 is not between 0 and maxOccurs 3");
    }

    @Test
    void aCutInsideASlotIsAnError() {
        BookCodec<Object> codec = countCodec();
        byte[] response = concat(bytes("000003"), order(1), order(2), bytes("ORD-0000000"));
        assertThatThrownBy(() -> read(codec, response))
            .isInstanceOf(MappingException.class)
            .hasMessage(ORDERS + ": the response ends inside slot 2, after 11 of 14 bytes");
    }

    @Test
    void aCutBeforeTheAreaGivesTheMessageOfTheField() {
        BookCodec<Object> codec = countCodec();
        assertThatThrownBy(() -> read(codec, bytes("0000")))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListOpenOrders.orderCount (order 30): response too short, need 2 bytes"
                + " at position 4 but only 0 remain");
    }

    @Test
    void aWriteAndARequestStillUseTheFullArea() {
        BookCodec<Object> codec = countCodec();
        Object value = codec.newInstance();
        CompiledCodec.set(value, "returnCode", "0000");
        CompiledCodec.set(value, "orders", new ArrayList<>());
        assertThat(codec.encodeResponse(value, LATIN)).hasSize(6 + 3 * 14);
        assertThat(codec.maxResponseLength()).isEqualTo(6 + 3 * 14);
    }

    @Test
    void pagesAddTheItemsOfEachPartialResponse() {
        BookCodec<Object> codec = countCodec();
        Object target = codec.newInstance();
        CompiledCodec.set(target, "orders", new ArrayList<>());
        codec.decodeResponse(concat(bytes("000001"), order(1)), target, LATIN);
        codec.decodeResponse(concat(bytes("000002"), order(2), order(3)), target, LATIN);
        assertThat(orderIds(target)).hasSize(3);
    }

    // ---- marker modes ----

    @Test
    void lowValuesModeReadsThePresentSlots() {
        BookCodec<Object> codec = codec("ListOpenOrders", source("ListOpenOrders",
            "@Operation public class ListOpenOrders {",
            field("@Field(order = 20, pic = \"X(4)\", direction = Direction.OUTPUT)",
                "String", "returnCode"),
            field("@Occurs(order = 40, maxOccurs = 3, fixed = true, partial = true)",
                "List<OpenOrder>", "orders"),
            "}"), openOrder());
        assertThat(orderIds(read(codec, concat(bytes("0000"), order(1), order(2)))))
            .containsExactly("ORD-00000001", "ORD-00000002");
        assertThat(orderIds(read(codec, concat(bytes("0000"), order(1), new byte[14]))))
            .containsExactly("ORD-00000001");
        assertThat(orders(read(codec, bytes("0000")))).isEmpty();
        assertThatThrownBy(() -> read(codec, concat(bytes("0000"), new byte[14], order(2))))
            .isInstanceOf(MappingException.class)
            .hasMessage(ORDERS + ": slot 0 is empty, but slot 1 after it has data");
        assertThatThrownBy(() -> read(codec, concat(bytes("0000"), order(1), bytes("ORD"))))
            .isInstanceOf(MappingException.class)
            .hasMessage(ORDERS + ": the response ends inside slot 1, after 3 of 14 bytes");
    }

    @Test
    void spacesModeReadsThePresentSlots() {
        BookCodec<Object> codec = markerCodec(", emptyItem = EmptyItem.SPACES");
        Object target = read(codec, concat(bytes("000000"), order(1), bytes(" ".repeat(14))));
        assertThat(orderIds(target)).containsExactly("ORD-00000001");
    }

    @Test
    void keyModeReadsThePresentSlots() {
        BookCodec<Object> codec = markerCodec(", emptyKey = \"orderId\"");
        Object target = read(codec, concat(bytes("000000"), order(1), order(2)));
        assertThat(orderIds(target)).hasSize(2);
    }

    // ---- other layouts ----

    @Test
    void anInputOutputCollectionNeedsTheFullAreaInARequest() {
        BookCodec<Object> codec = codec("UpdateOrders", source("UpdateOrders",
            "@Operation public class UpdateOrders {",
            field("@Field(order = 10, pic = \"X(10)\", direction = Direction.INPUT)",
                "String", "customerId"),
            field("@Field(order = 30, pic = \"9(2)\")", "int", "orderCount"),
            field("@Occurs(order = 40, maxOccurs = 3, fixed = true, partial = true,"
                + " dependsOn = \"orderCount\", direction = Direction.INPUT_OUTPUT)",
                "List<OpenOrder>", "orders"),
            "}"), openOrder());
        Object target = read(codec, concat(bytes("01"), order(1)));
        assertThat(orderIds(target)).containsExactly("ORD-00000001");
        byte[] request = concat(bytes("C-00000001"), bytes("01"), order(1));
        assertThatThrownBy(() -> codec.decodeRequest(request, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("UpdateOrders.orders (order 40): the response is too short for the"
                + " fixed area");
    }

    @Test
    void aSlotWithASubtypeItemIsTheUnit() {
        BookCodec<Object> codec = codec("GetStatement", source("GetStatement",
                "@Operation public class GetStatement {",
                field("@Occurs(order = 10, maxOccurs = 3, fixed = true, partial = true)",
                    "List<Entry>", "entries"),
                "}"),
            source("Entry",
                "@SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"C\", type = CardEntry.class),",
                "  @SubTypes.Type(value = \"W\", type = WireEntry.class)})",
                "public abstract class Entry {",
                field("@Field(order = 1, pic = \"X\")", "String", "kind"),
                "}"),
            source("CardEntry", "public class CardEntry extends Entry {",
                field("@Field(order = 2, pic = \"9(3)\")", "int", "amount"), "}"),
            source("WireEntry", "public class WireEntry extends Entry {",
                field("@Field(order = 2, pic = \"X(5)\")", "String", "iban"), "}"));
        Object target = codec.newInstance();
        codec.decodeResponse(bytes("C012  WGB001"), target, LATIN);
        assertThat(orders2(target)).hasSize(2);
        assertThatThrownBy(() -> codec.decodeResponse(bytes("WGB001C012"), codec.newInstance(),
            LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetStatement.entries (order 10): the response ends inside slot 1,"
                + " after 4 of 6 bytes");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> orders2(Object bean) {
        return (List<Object>) CompiledCodec.get(bean, "entries");
    }

    @Test
    void anItemWithAnInnerCollectionIsCompleteInEachPresentSlot() {
        BookCodec<Object> codec = codec("GetOrder", source("GetOrder",
                "@Operation public class GetOrder {",
                field("@Occurs(order = 10, maxOccurs = 2, fixed = true, partial = true)",
                    "List<OrderLine>", "lines"),
                "}"),
            source("OrderLine", "public class OrderLine {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "productCode"),
                field("@Occurs(order = 2, maxOccurs = 2, fixed = true)", "List<LineTax>",
                    "taxes"),
                "}"),
            source("LineTax", "public class LineTax {",
                field("@Field(order = 1, pic = \"X(3)\")", "String", "taxCode"), "}"));
        Object target = codec.newInstance();
        codec.decodeResponse(concat(bytes("P001VAT"), new byte[3]), target, LATIN);
        @SuppressWarnings("unchecked")
        List<Object> lines = (List<Object>) CompiledCodec.get(target, "lines");
        assertThat(lines).hasSize(1);
        assertThat((List<?>) CompiledCodec.get(lines.get(0), "taxes")).hasSize(1);
        assertThatThrownBy(() -> codec.decodeResponse(concat(bytes("P001VAT"), new byte[3],
            bytes("P002VA")), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetOrder.lines (order 10): the response ends inside slot 1,"
                + " after 6 of 10 bytes");
    }

    @Test
    void aPartialCollectionInASubtypeOfTheOperationClass() {
        BookCodec<Object> codec = codec("GetShipment", source("GetShipment",
                "@Operation @SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"E\", type = ExpressShipment.class)})",
                "public class GetShipment {",
                field("@Field(order = 10, pic = \"X\", direction = Direction.OUTPUT)",
                    "String", "kind"),
                "}"),
            source("ExpressShipment", "public class ExpressShipment extends GetShipment {",
                field("@Occurs(order = 20, maxOccurs = 3, fixed = true,"
                    + " partial = true)", "List<OpenOrder>", "orders"),
                "}"),
            openOrder());
        Object result = codec.readResponse(concat(bytes("E"), order(7)), codec.newInstance(),
            LATIN);
        assertThat(orderIds(result)).containsExactly("ORD-00000007");
    }

    // ---- compile errors ----

    @Test
    void partialNeedsAFixedCollection() {
        assertError("ListLines.lines (order 10): partial needs fixed = true",
            source("ListLines", "@Operation public class ListLines {",
                field("@Field(order = 5, pic = \"9\")", "int", "lineCount"),
                field("@Occurs(order = 10, maxOccurs = 3, partial = true)",
                    "List<OpenOrder>", "lines"),
                "}"),
            openOrder());
    }

    @Test
    void partialNeedsAnOutputDirection() {
        assertError(ORDERS + ": partial needs the direction OUTPUT or INPUT_OUTPUT",
            listOpenOrders(", direction = Direction.INPUT"), openOrder());
    }

    @Test
    void partialIsOnlyForATopLevelCollection() {
        assertError("OrderLine.taxes (order 2): partial is permitted only on a top-level"
                + " collection",
            source("GetOrder", "@Operation public class GetOrder {",
                field("@Occurs(order = 10, maxOccurs = 2, fixed = true)",
                    "List<OrderLine>", "lines"),
                "}"),
            source("OrderLine", "public class OrderLine {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "productCode"),
                field("@Occurs(order = 2, maxOccurs = 2, fixed = true, partial = true)",
                    "List<OpenOrder>", "taxes"),
                "}"),
            openOrder());
    }

    @Test
    void anOutputMemberAfterThePartialCollectionIsAnError() {
        assertError(ORDERS + ": partial needs the collection at the end of the response:"
                + " ListOpenOrders.returnCode2 (order 60) comes after it",
            listOpenOrders("",
                field("@Field(order = 50, pic = \"X(4)\", direction = Direction.INPUT)",
                    "String", "branch"),
                field("@Field(order = 60, pic = \"X(4)\", direction = Direction.OUTPUT)",
                    "String", "returnCode2")),
            openOrder());
    }

    @Test
    void anInputMemberAfterThePartialCollectionIsPermitted() {
        BookCodec<Object> codec = codec("ListOpenOrders", listOpenOrders("",
                field("@Field(order = 50, pic = \"X(4)\", direction = Direction.INPUT)",
                    "String", "branch")),
            openOrder());
        assertThat(orderIds(read(codec, concat(bytes("000000"), order(1))))).hasSize(1);
    }

    @Test
    void aSubtypeMemberAfterThePartialCollectionIsAnError() {
        assertError("GetShipment.orders (order 20): partial needs the collection at the end of"
                + " the response: ExpressShipment.carrier (order 30) comes after it",
            source("GetShipment",
                "@Operation @SubTypes(discriminator = \"kind\", value = {",
                "  @SubTypes.Type(value = \"E\", type = ExpressShipment.class)})",
                "public class GetShipment {",
                field("@Field(order = 10, pic = \"X\", direction = Direction.OUTPUT)",
                    "String", "kind"),
                field("@Occurs(order = 20, maxOccurs = 3, fixed = true,"
                    + " partial = true)", "List<OpenOrder>", "orders"),
                "}"),
            source("ExpressShipment", "public class ExpressShipment extends GetShipment {",
                field("@Field(order = 30, pic = \"X(8)\", direction = Direction.OUTPUT)",
                    "String", "carrier"),
                "}"),
            openOrder());
    }
}
