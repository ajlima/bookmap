package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.processor.CompiledCodec.get;
import static pt.dazz.bookmap.processor.CompiledCodec.set;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

class ItemSubTypesTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final String ENTRY_TYPES = "@BookSubTypes(discriminator = \"entryType\","
        + " value = {@BookSubTypes.Type(value = \"C\", type = CardPayment.class),"
        + " @BookSubTypes.Type(value = \"T\", type = Transfer.class)})";
    /** Card payment of 12.50 at a merchant: 1 + 8 + 20 bytes, then 14 spaces of padding. */
    private static final String CARD = "C" + hexText("000000000001250C")
        + "GRAND HOTEL LISBON  " + " ".repeat(14);
    /** Transfer of -3.00 to an IBAN: 1 + 8 + 34 bytes. */
    private static final String TRANSFER = "T" + hexText("000000000000300D")
        + "DE89370400440532013000            ";

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static String hexText(String digits) {
        return new String(HexFormat.of().parseHex(digits), StandardCharsets.ISO_8859_1);
    }

    private static JavaFileObject getStatement(String... extraMembers) {
        return source("GetStatement", "@BookOperation public class GetStatement {",
            field("@BookField(order = 10, length = 12, direction = Direction.INPUT)", "String",
                "accountId"),
            field("@BookField(order = 20, length = 2, direction = Direction.OUTPUT)", "int",
                "entryCount"),
            field("@BookCollection(order = 30, maxOccurs = 5)", "List<StatementEntry>",
                "entries"),
            String.join("\n", extraMembers),
            "}");
    }

    private static JavaFileObject statementEntry(String modifiers, String annotation,
                                                 String discriminator) {
        return source("StatementEntry", annotation,
            "public " + modifiers + "class StatementEntry {",
            discriminator,
            field("@BookField(order = 20, length = 15, scale = 2, signed = true,"
                + " usage = Usage.COMP3)", "BigDecimal", "amount"),
            "}");
    }

    private static JavaFileObject abstractEntry() {
        return statementEntry("abstract ", ENTRY_TYPES,
            field("@BookField(order = 10, length = 1)", "String", "entryType"));
    }

    private static JavaFileObject cardPayment(String... members) {
        return source("CardPayment", "public class CardPayment extends StatementEntry {",
            members.length == 0
                ? field("@BookField(order = 30, length = 20)", "String", "merchant")
                : String.join("\n", members),
            "}");
    }

    private static JavaFileObject transfer() {
        return source("Transfer", "public class Transfer extends StatementEntry {",
            field("@BookField(order = 30, length = 34)", "String", "counterpartyIban"), "}");
    }

    private static JavaFileObject refund() {
        return source("Refund", "public class Refund extends StatementEntry { }");
    }

    private BookCodec<Object> codec(JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test.GetStatement");
    }

    private Compilation compile(JavaFileObject... files) {
        return compiler.compile(files);
    }

    private static Object create(BookCodec<Object> codec, String simpleName) {
        try {
            return codec.getClass().getClassLoader().loadClass("test." + simpleName)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object card(BookCodec<Object> codec) {
        Object card = create(codec, "CardPayment");
        set(card, "amount", new BigDecimal("12.50"));
        set(card, "merchant", "GRAND HOTEL LISBON");
        return card;
    }

    private static Object transferOf(BookCodec<Object> codec) {
        Object transfer = create(codec, "Transfer");
        set(transfer, "amount", new BigDecimal("-3.00"));
        set(transfer, "counterpartyIban", "DE89370400440532013000");
        return transfer;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    // ---- generation ----

    @Test
    void mixedListRoundTrip() {
        BookCodec<Object> codec = codec(getStatement(), abstractEntry(), cardPayment(),
            transfer());
        Object statement = codec.newInstance();
        set(statement, "entries", List.of(card(codec), transferOf(codec)));
        byte[] response = codec.encodeResponse(statement, LATIN);
        assertThat(text(response)).isEqualTo("02" + CARD + TRANSFER);
        Object decoded = codec.newInstance();
        codec.decodeResponse(response, decoded, LATIN);
        List<?> entries = (List<?>) get(decoded, "entries");
        assertThat(entries).extracting(entry -> entry.getClass().getSimpleName())
            .containsExactly("CardPayment", "Transfer");
        assertThat(get(entries.get(0), "merchant")).isEqualTo("GRAND HOTEL LISBON");
        assertThat(get(entries.get(1), "amount")).isEqualTo(new BigDecimal("-3.00"));
        assertThat(get(entries.get(1), "entryType")).isEqualTo("T");
    }

    @Test
    void discriminatorIsSetOnWrite() {
        BookCodec<Object> codec = codec(getStatement(), abstractEntry(), cardPayment(),
            transfer());
        Object card = card(codec);
        Object statement = codec.newInstance();
        set(statement, "entries", List.of(card));
        assertThat(text(codec.encodeResponse(statement, LATIN))).startsWith("01C");
        assertThat(get(card, "entryType")).isEqualTo("C");
    }

    @Test
    void unknownItemDiscriminator() {
        BookCodec<Object> codec = codec(getStatement(), abstractEntry(), cardPayment(),
            transfer());
        assertThatThrownBy(() -> codec.decodeResponse(bytes("01X" + CARD.substring(1)),
            codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("StatementEntry.entryType (order 10): unknown discriminator value 'X'."
                + " Known values: C, T");
    }

    @Test
    void unlistedItemSubclassFails() {
        BookCodec<Object> codec = codec(getStatement(), abstractEntry(), cardPayment(),
            transfer(), refund());
        Object statement = codec.newInstance();
        set(statement, "entries", List.of(create(codec, "Refund")));
        assertThatThrownBy(() -> codec.encodeResponse(statement, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("StatementEntry: unknown subclass test.Refund");
    }

    @Test
    void fieldsAfterAPolymorphicCollection() {
        BookCodec<Object> codec = codec(
            getStatement(field("@BookField(order = 40, length = 4, direction = Direction.OUTPUT)",
                "String", "trailer")),
            abstractEntry(), cardPayment(), transfer());
        Object decoded = codec.newInstance();
        codec.decodeResponse(bytes("02" + TRANSFER + CARD + "END."), decoded, LATIN);
        assertThat(get(decoded, "trailer")).isEqualTo("END.");
        assertThat(codec.maxResponseLength()).isEqualTo(2 + 5 * 43 + 4);
    }

    @Test
    void maxResponseLengthUsesLongestItem() {
        assertThat(codec(getStatement(), abstractEntry(), cardPayment(), transfer())
            .maxResponseLength()).isEqualTo(2 + 5 * 43);
    }

    @Test
    void concreteItemBaseIsWrittenAndPadded() {
        BookCodec<Object> codec = codec(getStatement(),
            statementEntry("", ENTRY_TYPES,
                field("@BookField(order = 10, length = 1)", "String", "entryType")),
            cardPayment(), transfer());
        Object base = create(codec, "StatementEntry");
        set(base, "entryType", "N");
        set(base, "amount", BigDecimal.ZERO);
        Object statement = codec.newInstance();
        set(statement, "entries", List.of(base));
        assertThat(codec.encodeResponse(statement, LATIN)).hasSize(2 + 43);
    }

    @Test
    void numericItemDiscriminator() {
        BookCodec<Object> codec = codec(getStatement(),
            statementEntry("abstract ", "@BookSubTypes(discriminator = \"entryType\","
                    + " value = {@BookSubTypes.Type(value = \"01\", type = CardPayment.class)})",
                field("@BookField(order = 10, length = 2)", "int", "entryType")),
            cardPayment());
        Object decoded = codec.newInstance();
        String card = "01" + CARD.substring(1, 29);
        codec.decodeResponse(bytes("01" + card), decoded, LATIN);
        assertThat((List<?>) get(decoded, "entries")).hasSize(1);
        assertThatThrownBy(() -> codec.decodeResponse(bytes("01  " + card.substring(2)),
            codec.newInstance(), LATIN))
            .hasMessageContaining("unknown discriminator value '(blank)'");
    }

    @Test
    void itemDiscriminatorAfterABaseField() {
        BookCodec<Object> codec = codec(getStatement(),
            statementEntry("abstract ", ENTRY_TYPES,
                field("@BookField(order = 5, length = 4)", "String", "reference")
                    + field("@BookField(order = 10, length = 1)", "String", "entryType")),
            cardPayment(), transfer());
        Object decoded = codec.newInstance();
        codec.decodeResponse(bytes("01REF1" + TRANSFER), decoded, LATIN);
        Object entry = ((List<?>) get(decoded, "entries")).get(0);
        assertThat(entry.getClass().getSimpleName()).isEqualTo("Transfer");
        assertThat(get(entry, "reference")).isEqualTo("REF1");
    }

    // ---- validation ----

    private static void assertError(Compilation compilation, String message) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
    }

    @Test
    void itemDiscriminatorDoesNotExist() {
        assertError(compile(getStatement(), statementEntry("abstract ",
                ENTRY_TYPES.replace("\"entryType\"", "\"kind\""),
                field("@BookField(order = 10, length = 1)", "String", "entryType")),
            cardPayment(), transfer()),
            "StatementEntry: discriminator field 'kind' does not exist");
    }

    @Test
    void itemSubtypeWithCollection() {
        assertError(compile(getStatement(), abstractEntry(),
                cardPayment(field("@BookField(order = 30, length = 1)", "int", "count"),
                    field("@BookCollection(order = 40, maxOccurs = 2)", "List<Transfer>",
                        "parts")),
                transfer()),
            "CardPayment.parts (order 40): an item class must not contain a @BookCollection"
                + " (nested collection)");
    }

    @Test
    void itemDuplicateValue() {
        assertError(compile(getStatement(), statementEntry("abstract ",
                ENTRY_TYPES.replace("\"T\"", "\"C\""),
                field("@BookField(order = 10, length = 1)", "String", "entryType")),
            cardPayment(), transfer()),
            "StatementEntry.entryType (order 10): discriminator value 'C' is used by CardPayment"
                + " and Transfer");
    }

    @Test
    void itemSubtypeOrderNotAfterBase() {
        assertError(compile(getStatement(), abstractEntry(),
                cardPayment(field("@BookField(order = 15, length = 20)", "String", "merchant")),
                transfer()),
            "CardPayment.merchant (order 15): a subtype member must come after all base members");
    }

    @Test
    void abstractItemWithoutSubTypes() {
        assertError(compile(getStatement(), statementEntry("abstract ", "",
                field("@BookField(order = 10, length = 1)", "String", "entryType"))),
            "GetStatement.entries (order 30): item class test.StatementEntry must be a public"
                + " class with a public no-argument constructor");
    }

    @Test
    void nonPublicAbstractItemBase() {
        assertError(compile(getStatement(), source("StatementEntry", ENTRY_TYPES,
                "abstract class StatementEntry {",
                field("@BookField(order = 10, length = 1)", "String", "entryType"), "}"),
            cardPayment(), transfer()),
            "GetStatement.entries (order 30): item class test.StatementEntry must be a public"
                + " class with a public no-argument constructor");
    }
}
