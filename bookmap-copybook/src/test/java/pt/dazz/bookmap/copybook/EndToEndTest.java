package pt.dazz.bookmap.copybook;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.copybook.Compiled.get;
import static pt.dazz.bookmap.copybook.Compiled.set;

import com.palantir.javapoet.JavaFile;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.generate.GenerateOptions;
import pt.dazz.bookmap.copybook.json.ModelJson;
import pt.dazz.bookmap.copybook.map.ConditionMode;
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
    @ValueSource(strings = {"ORDERS.cpy", "INVOICE.cpy", "ACCOUNT.cpy", "SHIPMENT.cpy",
        "INVOICE-PARTY.cpy"})
    void generatesCompilableClasses(String name) {
        compile(name);
        Compiled.compile(generate(name, new GenerateOptions(PACKAGE, true)).files(), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDERS.cpy", "INVOICE.cpy", "ACCOUNT.cpy", "SHIPMENT.cpy",
        "INVOICE-PARTY.cpy"})
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
    void invoicePartyUsesTheEnumTheConstantsAndTheInitialValues() {
        Copybooks.Result result = generate("INVOICE-PARTY.cpy", PLAIN);
        assertThat(result.files()).extracting(file -> file.typeSpec().name())
            .containsExactly("GetInvoiceParty", "PartyRole");
        assertThat(result.diagnostics()).extracting(Diagnostic::message).containsExactly(
            "PARTY-STATUS: the level 88 items give constants, not an enum, because a level 88"
                + " item has a THRU range. Set the condition mode of PARTY-STATUS to change"
                + " this.",
            "PARTY-GRADE: the level 88 items give constants, not an enum, because it has only"
                + " one level 88 item. Set the condition mode of PARTY-GRADE to change this.",
            "BATCH-NUMBER: the level 88 items give constants, not an enum, because a level 88"
                + " item has a THRU range. Set the condition mode of BATCH-NUMBER to change"
                + " this.",
            "CREDIT-LIMIT: the level 88 items give constants, not an enum, because a level 88"
                + " item has a THRU range. Set the condition mode of CREDIT-LIMIT to change"
                + " this.",
            "PARTY-COUNTRY: the level 88 items give constants, not an enum, because it has"
                + " only one level 88 item. Set the condition mode of PARTY-COUNTRY to change"
                + " this.");
        Compiled classes = Compiled.compile(result.files());
        String type = PACKAGE + ".GetInvoiceParty";
        BookCodec<Object> codec = classes.codec(type);
        Object party = codec.newInstance();
        assertThat(get(party, "partyRole"))
            .isSameAs(classes.staticField(PACKAGE + ".PartyRole", "SELLER"));
        assertThat(get(party, "partyStatus")).isEqualTo(1);
        assertThat(get(party, "batchNumber")).isEqualTo(100L);
        assertThat(get(party, "currencyCode")).isEqualTo("EUR");
        assertThat(get(party, "creditLimit")).isEqualTo(new BigDecimal("1000.50"));
        assertThat(get(party, "partyName")).isEqualTo("");
        assertThat(classes.staticField(type, "PARTY_ACTIVE")).isEqualTo(1);
        assertThat(classes.staticField(type, "BATCH_RANGE")).isEqualTo(100L);
        assertThat(classes.staticField(type, "NO_CREDIT")).isEqualTo(new BigDecimal("0"));
        assertThat(classes.staticField(type, "GRADE_GOOD")).isEqualTo("A");
        assertThat(classes.staticField(type, "COUNTRY_UNKNOWN")).isEqualTo("");
        assertThat(Compiled.call(party, "isPartyActive")).isEqualTo(true);
        assertThat(Compiled.call(party, "isPartyClosed")).isEqualTo(false);
        assertThat(Compiled.call(party, "isBatchFirst")).isEqualTo(false);
        assertThat(Compiled.call(party, "isBatchRange")).isEqualTo(true);
        assertThat(Compiled.call(party, "isNoCredit")).isEqualTo(false);
        assertThat(Compiled.call(party, "isSmallCredit")).isEqualTo(false);
        assertThat(Compiled.call(party, "isGradeGood")).isEqualTo(false);
        assertThat(Compiled.call(party, "isCountryUnknown")).isEqualTo(false);

        set(party, "invoiceId", "INV-0000001");
        set(party, "partyRole", classes.staticField(PACKAGE + ".PartyRole", "SHIP_TO"));
        set(party, "partyGrade", "X");
        set(party, "creditLimit", new BigDecimal("50"));
        byte[] bytes = codec.encodeRequest(party, LATIN);
        assertThat(new String(bytes, 12, 8, StandardCharsets.ISO_8859_1)).isEqualTo("SHIP-TO ");
        Object decoded = codec.decodeRequest(bytes, LATIN);
        assertThat(get(decoded, "partyRole")).hasToString("SHIP_TO");
        assertThat(Compiled.call(decoded, "isGradeGood")).isEqualTo(true);
        assertThat(Compiled.call(decoded, "isSmallCredit")).isEqualTo(true);
        assertThat(Compiled.call(decoded, "isCountryUnknown")).isEqualTo(true);
        set(decoded, "partyGrade", "B");
        set(decoded, "partyStatus", 9);
        set(decoded, "creditLimit", null);
        set(decoded, "partyCountry", null);
        assertThat(Compiled.call(decoded, "isGradeGood")).isEqualTo(true);
        assertThat(Compiled.call(decoded, "isPartyClosed")).isEqualTo(true);
        assertThat(Compiled.call(decoded, "isNoCredit")).isEqualTo(false);
        assertThat(Compiled.call(decoded, "isCountryUnknown")).isEqualTo(false);
        set(decoded, "creditLimit", new BigDecimal("0.00"));
        assertThat(Compiled.call(decoded, "isNoCredit")).isEqualTo(true);
    }

    @Test
    void explicitEnumModeWritesRanges() {
        Copybooks.Result result = Copybooks.generate(copybook("INVOICE-PARTY.cpy"),
            parseOptions(), new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(),
                Map.of("PARTY-STATUS", ConditionMode.ENUM, "CREDIT-LIMIT", ConditionMode.ENUM)),
            PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::message).contains(
            "the VALUE of CREDIT-LIMIT is not a value of a level 88 item. The field has no"
                + " initial value.");
        assertThat(result.files().get(2).toString())
            .contains("ranges = @CobolValue.Range(from = \"1\", thru = \"5\")");
        Compiled classes = Compiled.compile(result.files());
        BookCodec<Object> codec = classes.codec(PACKAGE + ".GetInvoiceParty");
        Object party = codec.newInstance();
        assertThat(get(party, "partyStatus")).hasToString("ACTIVE");
        set(party, "creditLimit", classes.staticField(PACKAGE + ".CreditLimit", "SMALL_CREDIT"));
        Object decoded = codec.decodeRequest(codec.encodeRequest(party, LATIN), LATIN);
        assertThat(get(decoded, "creditLimit")).hasToString("SMALL_CREDIT");
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
