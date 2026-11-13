package pt.dazz.bookmap.copybook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.copybook.Compiled.get;
import static pt.dazz.bookmap.copybook.Compiled.set;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.Continuable;
import pt.dazz.bookmap.Validatable;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.generate.GenerateOptions;
import pt.dazz.bookmap.copybook.json.ModelJson;
import pt.dazz.bookmap.copybook.map.ConditionMode;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.map.SubTypeDecision;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.SourceFormat;
import pt.dazz.bookmap.exception.BusinessException;
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
        "INVOICE-PARTY.cpy", "TRACKING.cpy"})
    void generatesCompilableClasses(String name) {
        compile(name);
        Compiled.compile(generate(name, new GenerateOptions(PACKAGE, true)).files(), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDERS.cpy", "INVOICE.cpy", "ACCOUNT.cpy", "SHIPMENT.cpy",
        "INVOICE-PARTY.cpy", "TRACKING.cpy"})
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
        MappingOptions dates = MappingOptions.defaults().withHints(
            Map.of("*-DATE", "yyyyMMdd"), Map.of(), Map.of());
        Copybooks.Result result = Copybooks.generate(copybook("INVOICE.cpy"), parseOptions(),
            dates, PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .containsOnly(pt.dazz.bookmap.copybook.diag.Severity.INFO);
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
    void packedDateHintsRoundTripThroughTheCodec() {
        Map<String, String> dates = new LinkedHashMap<>();
        dates.put("ORDER-DATE", "yyyyMMdd");
        dates.put("SHIP-DATE", "yyyyDDD");
        dates.put("SHIP-TIME", "'0'HHmmss");
        dates.put("LAST-UPDATE", "yyyyMMddHHmmss");
        Copybooks.Result result = Copybooks.generate(copybook("PACKED-DATES.cpy"),
            parseOptions(), MappingOptions.defaults().withHints(dates, Map.of(), Map.of()),
            PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .containsOnly(pt.dazz.bookmap.copybook.diag.Severity.INFO);
        BookCodec<Object> codec = Compiled.compile(result.files())
            .codec(PACKAGE + ".GetOrderDates");
        Object request = codec.newInstance();
        set(request, "orderId", "ORD-1");
        set(request, "orderDate", LocalDate.of(2026, 11, 13));
        set(request, "shipDate", LocalDate.of(2026, 11, 13));
        set(request, "shipTime", LocalTime.of(13, 5, 9));
        set(request, "lastUpdate", LocalDateTime.of(2026, 11, 13, 23, 59, 58));
        byte[] bytes = codec.encodeRequest(request, LATIN);
        assertThat(HexFormat.of().withUpperCase().formatHex(bytes, 10, bytes.length))
            .isEqualTo("020261113C" + "2026317F" + "0130509C" + "020261113235958C");
        Object decoded = codec.decodeRequest(bytes, LATIN);
        assertThat(get(decoded, "orderDate")).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(get(decoded, "shipDate")).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(get(decoded, "shipTime")).isEqualTo(LocalTime.of(13, 5, 9));
        assertThat(get(decoded, "lastUpdate"))
            .isEqualTo(LocalDateTime.of(2026, 11, 13, 23, 59, 58));
    }

    /** Writes a handler class with one static method that calls a getter of the page. */
    private static JavaFile handler(String packageName, String name, MethodSpec method) {
        return JavaFile.builder(packageName, TypeSpec.classBuilder(name)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(method).build()).build();
    }

    @Test
    void pagedListWithDatesAndHandlersRoundTrips() {
        Map<String, String> dates = new LinkedHashMap<>();
        dates.put("*-DATE", "yyyyMMdd");
        dates.put("SHIP-TIME", "HH:mm:ss");
        dates.put("LAST-UPDATE", "yyyyMMddHHmmss");
        MappingOptions options = MappingOptions.defaults().withHints(dates,
            Map.of("LIST-SHIPMENTS", "com.example.handlers.ShipmentPaging"),
            Map.of("LIST-SHIPMENTS", "ShipmentChecks"));
        Copybooks.Result result = Copybooks.generate(copybook("LIST-SHIPMENTS.cpy"),
            parseOptions(), options, PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .containsOnly(pt.dazz.bookmap.copybook.diag.Severity.INFO);
        ClassName page = ClassName.get(PACKAGE, "ListShipments");
        List<JavaFile> files = new ArrayList<>(result.files());
        files.add(handler("com.example.handlers", "ShipmentPaging", MethodSpec
            .methodBuilder("hasContinuation").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(boolean.class).addParameter(page, "page")
            .addStatement("return $S.equals(page.getMoreData())", "Y").build()));
        files.add(handler(PACKAGE, "ShipmentChecks", MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC).addParameter(page, "page")
            .beginControlFlow("if (!$S.equals(page.getReturnCode()))", "00")
            .addStatement("throw new $T($S + page.getReturnCode())", BusinessException.class,
                "return code ")
            .endControlFlow().build()));
        Compiled classes = Compiled.compile(files);
        BookCodec<Object> codec = classes.codec(PACKAGE + ".ListShipments");

        Object request = codec.newInstance();
        set(request, "customerId", "CUST000042");
        set(request, "fromDate", LocalDate.of(2026, 11, 1));
        Object decodedRequest = codec.decodeRequest(codec.encodeRequest(request, LATIN), LATIN);
        assertThat(get(decodedRequest, "fromDate")).isEqualTo(LocalDate.of(2026, 11, 1));

        Object response = codec.newInstance();
        set(response, "returnCode", "00");
        set(response, "moreData", "Y");
        Object entry = classes.create(PACKAGE + ".ShipmentEntry");
        set(entry, "shipmentId", "SHP000000001");
        set(entry, "shipDate", LocalDate.of(2026, 11, 12));
        set(entry, "shipTime", LocalTime.of(14, 30, 5));
        set(entry, "lastUpdate", LocalDateTime.of(2026, 11, 12, 14, 31, 0));
        set(response, "shipmentEntry", List.of(entry));
        Object decoded = codec.newInstance();
        codec.decodeResponse(codec.encodeResponse(response, LATIN), decoded, LATIN);

        Object shipment = ((List<?>) get(decoded, "shipmentEntry")).get(0);
        assertThat(get(shipment, "shipTime")).isEqualTo(LocalTime.of(14, 30, 5));
        assertThat(get(shipment, "lastUpdate"))
            .isEqualTo(LocalDateTime.of(2026, 11, 12, 14, 31, 0));
        assertThat(((Continuable) decoded).hasContinuation()).isTrue();
        ((Validatable) decoded).validate();
        set(decoded, "returnCode", "12");
        assertThatThrownBy(((Validatable) decoded)::validate)
            .isInstanceOf(BusinessException.class).hasMessage("return code 12");
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
        assertThat(result.diagnostics()).extracting(Diagnostic::message)
            .filteredOn(message -> !message.startsWith("record "))
            .containsExactly(
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
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of(), null,
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
    void trackingRoundTripKeepsTheCobolOffsets() {
        Copybooks.Result result = generate("TRACKING.cpy", PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::message).containsExactly(
            "level 77 item WS-RETRY-COUNT is not part of a record and is skipped",
            "JUSTIFIED on TRK-CARRIER is ignored. BookMap writes the text on the left and keeps"
                + " the spaces on the left when it reads.",
            "level 66 item TRK-KEY is ignored. RENAMES adds no bytes.",
            "INDEX item TRK-EVENT-IDX is mapped as PIC S9(9) COMP. Its value is a byte offset"
                + " in a table.",
            "SYNC adds 1 slack byte before TRK-EVENT-COUNT. The generator adds a FILLER.",
            "SYNC adds 2 slack bytes before TRK-WEIGHT-GRAMS. The generator adds a FILLER.",
            "record GET-TRACKING has 39 bytes",
            "COMP-5 on TRK-WEIGHT-GRAMS is mapped as COMP. BookMap reads all binary values, but"
                + " writes only values with at most 9 digits.");
        Compiled classes = Compiled.compile(result.files());
        BookCodec<Object> codec = classes.codec(PACKAGE + ".GetTracking");
        Object tracking = codec.newInstance();
        set(tracking, "trkShipmentId", "SHP0000042");
        set(tracking, "trkStatus", "D");
        set(tracking, "trkEventCount", 7);
        set(tracking, "trkWeightGrams", 125000L);
        set(tracking, "trkCarrier", "OCEAN");
        set(tracking, "trkCharge", new BigDecimal("99.90"));
        set(tracking, "trkEventIdx", 48);

        byte[] bytes = codec.encodeRequest(tracking, LATIN);
        // 10 + 1 + 1 slack + 2 + 2 slack + 4 + 8 + 7 + 4: the offsets of the COBOL compiler.
        assertThat(bytes).hasSize(39);
        assertThat(new String(bytes, 20, 8, StandardCharsets.ISO_8859_1)).isEqualTo("OCEAN   ");
        // COMP-5 can hold all values of its 4 bytes, and the long field reads them.
        bytes[16] = (byte) 0xFF;
        bytes[17] = (byte) 0xFF;
        bytes[18] = (byte) 0xFF;
        bytes[19] = (byte) 0xFF;
        // BLANK WHEN ZERO: a zero amount is all spaces.
        Arrays.fill(bytes, 28, 35, (byte) ' ');
        Object decoded = codec.decodeRequest(bytes, LATIN);
        set(tracking, "trkCharge", BigDecimal.ZERO);
        assertThat(new String(codec.encodeRequest(tracking, LATIN), 28, 7,
            StandardCharsets.ISO_8859_1)).isBlank();
        assertThat(get(decoded, "trkEventCount")).isEqualTo(7);
        assertThat(get(decoded, "trkWeightGrams")).isEqualTo(4_294_967_295L);
        assertThat(get(decoded, "trkCarrier")).isEqualTo("OCEAN");
        assertThat(get(decoded, "trkCharge")).isNull();
        assertThat(get(decoded, "trkEventIdx")).isEqualTo(48);
    }

    @Test
    void editedPicturesRoundTrip() {
        Copybooks.Result result = generate("INVOICE-PRINT.cpy", PLAIN);
        assertThat(result.diagnostics()).extracting(Diagnostic::message).containsExactly(
            "record PRINT-INVOICE has 51 bytes",
            "PRT-STATUS: the level 88 items give constants, not an enum, because it has only one"
                + " level 88 item. Set the condition mode of PRT-STATUS to change this.",
            "the VALUE of PRT-FEE is ignored, because PRT-FEE has an edited picture");
        String source = result.files().stream()
            .filter(file -> file.typeSpec().name().equals("PrintInvoice")).findFirst()
            .orElseThrow().toString();
        assertThat(source).contains("pic = \"$$,$$9.99CR\"")
            .contains("<code>05 PRT-DUE-DAY PIC **&#47;**&#47;99.</code>")
            .contains("pic = \"9(3)V99 BLANK WHEN ZERO\"");
        Compiled classes = Compiled.compile(result.files());
        assertThat(classes.staticField(PACKAGE + ".PrintInvoice", "PRT_OPEN")).isEqualTo(1);
        BookCodec<Object> codec = classes.codec(PACKAGE + ".PrintInvoice");
        Object invoice = codec.newInstance();
        set(invoice, "prtInvoiceId", "INV-0042");
        set(invoice, "prtLineCount", 7);
        set(invoice, "prtTotal", new BigDecimal("-1234.5"));
        set(invoice, "prtTax", new BigDecimal("12.34"));
        set(invoice, "prtDueDay", 10226);
        set(invoice, "prtDiscount", BigDecimal.ZERO);
        set(invoice, "prtStatus", 1);
        set(invoice, "prtFee", -5);

        byte[] bytes = codec.encodeRequest(invoice, LATIN);

        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).isEqualTo("INV-0042  "
            + "  7" + "$1,234.50CR" + "   12.34 " + "*1/02/26" + "     " + "1" + "  -5");
        Object decoded = codec.decodeRequest(bytes, LATIN);
        assertThat(get(decoded, "prtLineCount")).isEqualTo(7);
        assertThat(get(decoded, "prtTotal")).isEqualTo(new BigDecimal("-1234.50"));
        assertThat(get(decoded, "prtTax")).isEqualTo(new BigDecimal("12.34"));
        assertThat(get(decoded, "prtDueDay")).isEqualTo(10226);
        assertThat(get(decoded, "prtDiscount")).isNull();
        assertThat(get(decoded, "prtStatus")).isEqualTo(1);
        assertThat(get(decoded, "prtFee")).isEqualTo(-5);
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
