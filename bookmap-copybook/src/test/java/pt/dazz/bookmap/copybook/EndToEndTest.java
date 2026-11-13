package pt.dazz.bookmap.copybook;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.copybook.Compiled.get;
import static pt.dazz.bookmap.copybook.Compiled.set;

import com.palantir.javapoet.JavaFile;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.generate.GenerateOptions;
import pt.dazz.bookmap.copybook.json.ModelJson;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.map.SubTypeDecision;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.SourceFormat;
import pt.dazz.bookmap.spi.BookCodec;

class EndToEndTest {

    private static final String PACKAGE = "com.example.generated";
    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final GenerateOptions PLAIN = new GenerateOptions(PACKAGE, false);

    static Path copybook(String name) {
        try {
            return Path.of(EndToEndTest.class.getResource("/copybooks/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ParseOptions parseOptions() {
        return new ParseOptions(SourceFormat.FIXED, StandardCharsets.UTF_8,
            List.of(copybook("copylib")), null);
    }

    private static MappingOptions mappingOptions(String name) {
        if (!name.equals("ACCOUNT.cpy")) {
            return MappingOptions.defaults();
        }
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("CHECKING-DATA",
                new SubTypeDecision("ACCOUNT-TYPE",
                    Map.of("CHECKING-DATA", "01", "SAVINGS-DATA", "02"), Map.of())));
    }

    private static Copybooks.Result generate(String name, GenerateOptions options) {
        Copybooks.Result result = Copybooks.generate(copybook(name), parseOptions(),
            mappingOptions(name), options);
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .doesNotContain(pt.dazz.bookmap.copybook.diag.Severity.ERROR);
        return result;
    }

    private static Compiled compile(String name) {
        return Compiled.compile(generate(name, PLAIN).files());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDERS.cpy", "INVOICE.cpy", "ACCOUNT.cpy", "SHIPMENT.cpy"})
    void generatesCompilableClasses(String name) {
        compile(name);
        Compiled.compile(generate(name, new GenerateOptions(PACKAGE, true)).files(), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDERS.cpy", "INVOICE.cpy", "ACCOUNT.cpy", "SHIPMENT.cpy"})
    void modelJsonRoundTripGivesSameJava(String name) {
        Copybooks.Result direct = generate(name, PLAIN);
        Copybooks.Result viaJson = Copybooks.generate(
            ModelJson.read(ModelJson.write(direct.model())), PLAIN);
        assertThat(viaJson.files().stream().map(JavaFile::toString).toList())
            .isEqualTo(direct.files().stream().map(JavaFile::toString).toList());
        assertThat(viaJson.hasErrors()).isFalse();
    }

    @Test
    void ordersRoundTrip() {
        Compiled classes = compile("ORDERS.cpy");
        BookCodec<Object> codec = classes.codec(PACKAGE + ".ListOrders");
        Object orders = codec.newInstance();
        set(orders, "customerId", "CUST000042");
        set(orders, "moreData", "N");
        Object entry = classes.create(PACKAGE + ".OrderEntry");
        set(entry, "orderNumber", "ORD0000001");
        set(entry, "orderDate", 20261111);
        set(entry, "orderTotal", new BigDecimal("1234.50"));
        set(orders, "orderEntry", List.of(entry));
        Object product = classes.create(PACKAGE + ".TopProduct");
        set(product, "productCode", "PRD00001");
        set(product, "quantity", 12);
        set(orders, "topProduct", List.of(product));
        set(orders, "grandTotal", new BigDecimal("1234.50"));

        byte[] bytes = codec.encodeResponse(orders, LATIN);
        assertThat(bytes).hasSize(10 + 12 + 1 + 3 + 24 + 3 * 13 + 7);
        Object decoded = codec.newInstance();
        codec.decodeResponse(bytes, decoded, LATIN);

        assertThat(get(decoded, "orderCount")).isEqualTo(1);
        List<?> entries = (List<?>) get(decoded, "orderEntry");
        assertThat(get(entries.get(0), "orderTotal")).isEqualTo(new BigDecimal("1234.50"));
        assertThat((List<?>) get(decoded, "topProduct")).hasSize(1);
        assertThat(get(decoded, "grandTotal")).isEqualTo(new BigDecimal("1234.50"));
    }

    @Test
    void invoiceRoundTripUsesOneAreaInEachDirection() {
        Compiled classes = compile("INVOICE.cpy");
        BookCodec<Object> codec = classes.codec(PACKAGE + ".GetInvoice");
        Object request = codec.newInstance();
        set(request, "returnCode", "");
        set(request, "invoiceId", "INV-0000001");
        set(request, "currencyCode", "EUR");
        set(request, "trailer", "END");
        byte[] requestBytes = codec.encodeRequest(request, LATIN);
        assertThat(requestBytes).hasSize(4 + 18 + 10);
        assertThat(get(codec.decodeRequest(requestBytes, LATIN), "invoiceId"))
            .isEqualTo("INV-0000001");

        Object response = codec.newInstance();
        set(response, "returnCode", "0000");
        set(response, "invoiceAmount", new BigDecimal("99.90"));
        set(response, "taxAmount", new BigDecimal("20.98"));
        set(response, "dueDate", 20261231);
        set(response, "trailer", "END");
        byte[] responseBytes = codec.encodeResponse(response, LATIN);
        assertThat(responseBytes).hasSize(4 + 18 + 10);
        Object decoded = codec.newInstance();
        codec.decodeResponse(responseBytes, decoded, LATIN);
        assertThat(get(decoded, "taxAmount")).isEqualTo(new BigDecimal("20.98"));
        assertThat(get(decoded, "trailer")).isEqualTo("END");
    }

    @Test
    void dateHintsRoundTripThroughTheCodec() {
        MappingOptions dates = new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of("*-DATE", "yyyyMMdd"));
        Copybooks.Result result = Copybooks.generate(copybook("INVOICE.cpy"), parseOptions(),
            dates, PLAIN);
        assertThat(result.diagnostics()).isEmpty();
        Compiled classes = Compiled.compile(result.files());
        BookCodec<Object> codec = classes.codec(PACKAGE + ".GetInvoice");
        Object response = codec.newInstance();
        set(response, "invoiceAmount", new BigDecimal("99.90"));
        set(response, "dueDate", LocalDate.of(2026, 12, 31));
        byte[] bytes = codec.encodeResponse(response, LATIN);
        assertThat(new String(bytes, 14, 8, StandardCharsets.ISO_8859_1)).isEqualTo("20261231");
        Object decoded = codec.newInstance();
        codec.decodeResponse(bytes, decoded, LATIN);
        assertThat(get(decoded, "dueDate")).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void accountRoundTripSelectsTheSubtype() {
        Compiled classes = compile("ACCOUNT.cpy");
        BookCodec<Object> codec = classes.codec(PACKAGE + ".GetAccount");
        Object savings = classes.create(PACKAGE + ".SavingsData");
        set(savings, "accountId", "ACC-00000001");
        set(savings, "interestRate", new BigDecimal("0.0325"));
        set(savings, "maturityDate", 20301231);
        byte[] bytes = codec.encodeResponse(savings, LATIN);

        Object result = codec.readResponse(bytes, codec.newInstance(), LATIN);

        assertThat(result.getClass().getSimpleName()).isEqualTo("SavingsData");
        assertThat(get(result, "accountType")).isEqualTo(2);
        assertThat(get(result, "interestRate")).isEqualTo(new BigDecimal("0.0325"));
    }

    @Test
    void shipmentRoundTripUsesTheCopyMember() {
        Compiled classes = compile("SHIPMENT.cpy");
        BookCodec<Object> codec = classes.codec(PACKAGE + ".CreateShipment");
        Object shipment = codec.newInstance();
        set(shipment, "shipmentId", "SHP000000001");
        set(shipment, "shipToStreet", "1 Harbour Road");
        set(shipment, "shipToCity", "Rotterdam");
        set(shipment, "shipToPostalCode", "3011");
        set(shipment, "shipToCountry", "NL");
        set(shipment, "weightKg", new BigDecimal("12.500"));
        set(shipment, "packageCount", 3);
        byte[] bytes = codec.encodeRequest(shipment, LATIN);
        Object decoded = codec.decodeRequest(bytes, LATIN);
        assertThat(get(decoded, "shipToCity")).isEqualTo("Rotterdam");
        assertThat(get(decoded, "weightKg")).isEqualTo(new BigDecimal("12.500"));
        assertThat(get(decoded, "packageCount")).isEqualTo(3);
    }

    @Test
    void errorsStopThePipeline() {
        Copybooks.Result parseError = Copybooks.toModel(copybook("copylib/ADDRESS.cpy"),
            ParseOptions.defaults(), MappingOptions.defaults());
        assertThat(parseError.hasErrors()).isFalse();
        Copybooks.Result mapError = Copybooks.generate(copybook("ACCOUNT.cpy"), parseOptions(),
            MappingOptions.defaults(), PLAIN);
        assertThat(mapError.hasErrors()).isTrue();
        assertThat(mapError.model()).isNull();
        assertThat(mapError.files()).isEmpty();
        Copybooks.Result missing = Copybooks.toModel(copybook("copylib").resolve("NONE.cpy"),
            ParseOptions.defaults(), MappingOptions.defaults());
        assertThat(missing.hasErrors()).isTrue();
    }
}
