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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

class SubTypesGenerationTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    /** The checking data: overdraft limit 1500.00 (COMP-3, 6 bytes) and a card mask (19). */
    private static final byte[] CHECKING = concat(hex("00000150000C"),
        bytes("4111-XXXX-XXXX-1111"));
    /** The savings data: interest rate 0.0325 (5 digits) and the maturity date 2030-12-31. */
    private static final byte[] SAVINGS = bytes("0032520301231");

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static JavaFileObject getAccount(String modifiers, String discriminatorDirection,
                                             String... extraTypes) {
        return source("GetAccount",
            "@BookOperation @BookSubTypes(discriminator = \"accountType\", value = {",
            "  @BookSubTypes.Type(value = \"01\", type = CheckingAccount.class),",
            "  @BookSubTypes.Type(value = \"02\", type = SavingsAccount.class)"
                + String.join("", extraTypes) + "})",
            "public " + modifiers + "class GetAccount {",
            field("@BookField(order = 10, length = 12, direction = Direction.INPUT)", "String",
                "accountId"),
            field("@BookField(order = 20, length = 2, direction = Direction."
                + discriminatorDirection + ")", "int", "accountType"),
            "}");
    }

    private static JavaFileObject checking() {
        return source("CheckingAccount", "public class CheckingAccount extends GetAccount {",
            field("@BookField(order = 30, length = 11, scale = 2, signed = true,"
                + " usage = Usage.COMP3, direction = Direction.OUTPUT)", "BigDecimal",
                "overdraftLimit"),
            field("@BookField(order = 40, length = 19, direction = Direction.OUTPUT)", "String",
                "cardNumberMask"),
            "}");
    }

    private static JavaFileObject savings() {
        return source("SavingsAccount", "public class SavingsAccount extends GetAccount {",
            field("@BookField(order = 30, length = 5, scale = 4, direction = Direction.OUTPUT)",
                "BigDecimal", "interestRate"),
            field("@BookField(order = 40, direction = Direction.OUTPUT)", "LocalDate",
                "maturityDate"),
            "}");
    }

    /** A subclass that the annotation does not list. */
    private static JavaFileObject other() {
        return source("OtherAccount", "public class OtherAccount extends GetAccount { }");
    }

    /** Compiles the sources. The first source is the operation class. */
    private BookCodec<Object> codec(JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        String path = files[0].toUri().getPath();
        String simpleName = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
        return CompiledCodec.load(compilation, "test." + simpleName);
    }

    private BookCodec<Object> accounts() {
        return codec(getAccount("", "INPUT_OUTPUT"), checking(), savings(), other());
    }

    private BookCodec<Object> abstractAccounts() {
        return codec(getAccount("abstract ", "INPUT_OUTPUT"), checking(), savings());
    }

    private static Object create(BookCodec<Object> codec, String simpleName) {
        try {
            return codec.getClass().getClassLoader().loadClass("test." + simpleName)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] hex(String digits) {
        return HexFormat.of().parseHex(digits);
    }

    private static byte[] concat(byte[]... parts) {
        List<Byte> all = new ArrayList<>();
        Arrays.stream(parts).forEach(part -> {
            for (byte b : part) {
                all.add(b);
            }
        });
        byte[] result = new byte[all.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = all.get(i);
        }
        return result;
    }

    private static String simpleName(Object value) {
        return value.getClass().getSimpleName();
    }

    // ---- codec metadata ----

    @Test
    void typesListsBaseAndSubtypes() {
        assertThat(accounts().types()).extracting(Class::getSimpleName)
            .containsExactly("GetAccount", "CheckingAccount", "SavingsAccount");
    }

    @Test
    void typesOfAnAbstractBaseListOnlySubtypes() {
        assertThat(abstractAccounts().types()).extracting(Class::getSimpleName)
            .containsExactly("CheckingAccount", "SavingsAccount");
    }

    @Test
    void maxResponseLengthIsTheLongestVariant() {
        assertThat(accounts().maxResponseLength()).isEqualTo(2 + Math.max(6 + 19, 5 + 8));
    }

    @Test
    void abstractBaseNewInstanceFails() {
        assertThatThrownBy(() -> abstractAccounts().newInstance())
            .isInstanceOf(MappingException.class)
            .hasMessage("GetAccount: the base class is abstract");
    }

    // ---- write ----

    @Test
    void encodeSubtypeSetsTheDiscriminator() {
        BookCodec<Object> codec = accounts();
        Object savings = create(codec, "SavingsAccount");
        set(savings, "accountId", "ACC-0000001");
        assertThat(text(codec.encodeRequest(savings, LATIN))).isEqualTo("ACC-0000001 02");
        assertThat(get(savings, "accountType")).isEqualTo(2);
    }

    @Test
    void encodeConcreteBaseKeepsTheDiscriminator() {
        BookCodec<Object> codec = accounts();
        Object base = codec.newInstance();
        set(base, "accountId", "ACC-0000001");
        set(base, "accountType", 7);
        assertThat(text(codec.encodeRequest(base, LATIN))).isEqualTo("ACC-0000001 07");
    }

    @Test
    void encodeUnknownSubclassFails() {
        BookCodec<Object> codec = accounts();
        Object other = create(codec, "OtherAccount");
        assertThatThrownBy(() -> codec.encodeRequest(other, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetAccount: unknown subclass test.OtherAccount");
        assertThatThrownBy(() -> codec.encodeResponse(other, LATIN))
            .isInstanceOf(MappingException.class);
        assertThatThrownBy(() -> codec.resetOutputCollections(other))
            .isInstanceOf(MappingException.class);
    }

    @Test
    void encodeResponseWritesTheRuntimeLayout() {
        BookCodec<Object> codec = accounts();
        Object savings = create(codec, "SavingsAccount");
        set(savings, "interestRate", new BigDecimal("0.0325"));
        set(savings, "maturityDate", LocalDate.of(2030, 12, 31));
        assertThat(codec.encodeResponse(savings, LATIN))
            .isEqualTo(concat(bytes("02"), SAVINGS));
        Object base = codec.newInstance();
        set(base, "accountType", 9);
        assertThat(text(codec.encodeResponse(base, LATIN))).isEqualTo("09");
    }

    // ---- read ----

    @Test
    void readResponseCreatesTheSubtype() {
        BookCodec<Object> codec = accounts();
        Object base = codec.newInstance();
        set(base, "accountId", "ACC-0000001");
        Object result = codec.readResponse(concat(bytes("02"), SAVINGS), base, LATIN);
        assertThat(simpleName(result)).isEqualTo("SavingsAccount");
        assertThat(result).isNotSameAs(base);
        assertThat(get(result, "accountId")).isEqualTo("ACC-0000001");
        assertThat(get(result, "accountType")).isEqualTo(2);
        assertThat(get(result, "interestRate")).isEqualTo(new BigDecimal("0.0325"));
        assertThat(get(result, "maturityDate")).isEqualTo(LocalDate.of(2030, 12, 31));
    }

    @Test
    void readResponseFillsTheSameSubtype() {
        BookCodec<Object> codec = accounts();
        Object checking = create(codec, "CheckingAccount");
        Object result = codec.readResponse(concat(bytes("01"), CHECKING), checking, LATIN);
        assertThat(result).isSameAs(checking);
        assertThat(get(checking, "overdraftLimit")).isEqualTo(new BigDecimal("1500.00"));
        assertThat(get(checking, "cardNumberMask")).isEqualTo("4111-XXXX-XXXX-1111");
    }

    @Test
    void readResponseRejectsASibling() {
        BookCodec<Object> codec = accounts();
        Object checking = create(codec, "CheckingAccount");
        assertThatThrownBy(() -> codec.readResponse(concat(bytes("02"), SAVINGS), checking,
            LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetAccount: the response has type SavingsAccount, which is not a"
                + " CheckingAccount");
    }

    @Test
    void decodeResponseFillsTheSameSubtype() {
        BookCodec<Object> codec = accounts();
        Object savings = create(codec, "SavingsAccount");
        codec.decodeResponse(concat(bytes("02"), SAVINGS), savings, LATIN);
        assertThat(get(savings, "interestRate")).isEqualTo(new BigDecimal("0.0325"));
    }

    @Test
    void decodeResponseRejectsAClassChange() {
        BookCodec<Object> codec = accounts();
        Object base = codec.newInstance();
        assertThatThrownBy(() -> codec.decodeResponse(concat(bytes("01"), CHECKING), base,
            LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetAccount: the response has type CheckingAccount, but the object has"
                + " type GetAccount. Only the first response can change the type");
    }

    @Test
    void unknownDiscriminatorValue() {
        BookCodec<Object> codec = accounts();
        assertThatThrownBy(() -> codec.readResponse(bytes("07"), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetAccount.accountType (order 20): unknown discriminator value '7'."
                + " Known values: 1, 2");
        assertThatThrownBy(() -> codec.decodeResponse(bytes("07"), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("unknown discriminator value '7'");
    }

    @Test
    void blankNumericDiscriminatorIsUnknown() {
        BookCodec<Object> codec = accounts();
        assertThatThrownBy(() -> codec.readResponse(bytes("  "), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("unknown discriminator value '(blank)'");
    }

    @Test
    void shortResponseUsesTheSelectedLayout() {
        BookCodec<Object> codec = accounts();
        assertThatThrownBy(() -> codec.readResponse(bytes("01"), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("CheckingAccount.overdraftLimit (order 30): response too short");
    }

    @Test
    void textDiscriminatorIgnoresTrailingSpaces() {
        BookCodec<Object> codec = codec(
            source("Ledger",
                "@BookOperation @BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"C\", type = CardEntry.class)})",
                "public abstract class Ledger {",
                field("@BookField(order = 1, length = 3)", "String", "kind"),
                "}"),
            source("CardEntry", "public class CardEntry extends Ledger {",
                field("@BookField(order = 2, length = 2, direction = Direction.OUTPUT)",
                    "String", "code"),
                "}"));
        Object card = codec.decodeRequest(bytes("C  "), LATIN);
        Object result = codec.readResponse(bytes("C  AB"), card, LATIN);
        assertThat(simpleName(result)).isEqualTo("CardEntry");
        assertThat(get(result, "code")).isEqualTo("AB");
        assertThatThrownBy(() -> codec.readResponse(bytes("X  AB"), card, LATIN))
            .hasMessage("Ledger.kind (order 1): unknown discriminator value 'X'. Known values:"
                + " C");
    }

    @Test
    void numericValueNormalization() {
        BookCodec<Object> codec = codec(
            source("Numbered",
                "@BookOperation @BookSubTypes(discriminator = \"code\", value = {",
                "  @BookSubTypes.Type(value = \"001\", type = First.class)})",
                "public class Numbered {",
                field("@BookField(order = 1, length = 3)", "Long", "code"),
                "}"),
            source("First", "public class First extends Numbered {",
                field("@BookField(order = 2, length = 1, direction = Direction.OUTPUT)",
                    "String", "flag"),
                "}"));
        Object result = codec.readResponse(bytes("001Y"), codec.newInstance(), LATIN);
        assertThat(simpleName(result)).isEqualTo("First");
        Object first = create(codec, "First");
        codec.encodeRequest(first, LATIN);
        assertThat(get(first, "code")).isEqualTo(1L);
    }

    @Test
    void decodeRequestSelectsTheSubtype() {
        BookCodec<Object> codec = accounts();
        assertThat(simpleName(codec.decodeRequest(bytes("ACC-0000001 01"), LATIN)))
            .isEqualTo("CheckingAccount");
        Object unknown = codec.decodeRequest(bytes("ACC-0000001 07"), LATIN);
        assertThat(simpleName(unknown)).isEqualTo("GetAccount");
        assertThat(get(unknown, "accountId")).isEqualTo("ACC-0000001");
    }

    @Test
    void decodeRequestWithAnAbstractBaseFailsForAnUnknownValue() {
        BookCodec<Object> codec = abstractAccounts();
        assertThat(simpleName(codec.decodeRequest(bytes("ACC-0000001 02"), LATIN)))
            .isEqualTo("SavingsAccount");
        assertThatThrownBy(() -> codec.decodeRequest(bytes("ACC-0000001 07"), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("unknown discriminator value '7'");
    }

    @Test
    void outputDiscriminatorDecodesTheBaseRequest() {
        BookCodec<Object> codec = codec(getAccount("", "OUTPUT"), checking(), savings());
        assertThat(simpleName(codec.decodeRequest(bytes("ACC-0000001 "), LATIN)))
            .isEqualTo("GetAccount");
        Object savings = create(codec, "SavingsAccount");
        set(savings, "accountId", "ACC-0000001");
        assertThat(text(codec.encodeRequest(savings, LATIN))).isEqualTo("ACC-0000001 ");
        assertThat(get(savings, "accountType")).isEqualTo(0);
    }

    @Test
    void listedBaseClassHasItsOwnValue() {
        BookCodec<Object> codec = codec(getAccount("", "INPUT_OUTPUT",
            ", @BookSubTypes.Type(value = \"00\", type = GetAccount.class)"), checking(),
            savings());
        assertThat(codec.types()).extracting(Class::getSimpleName)
            .containsExactly("CheckingAccount", "SavingsAccount", "GetAccount");
        Object base = codec.newInstance();
        set(base, "accountType", 5);
        assertThat(text(codec.encodeRequest(base, LATIN))).endsWith("00");
        assertThat(codec.readResponse(bytes("00"), base, LATIN)).isSameAs(base);
    }

    // ---- collections in a subtype ----

    private BookCodec<Object> statements() {
        return codec(
            source("GetStatement",
                "@BookOperation @BookSubTypes(discriminator = \"format\", value = {",
                "  @BookSubTypes.Type(value = \"L\", type = LongStatement.class)})",
                "public class GetStatement {",
                field("@BookField(order = 1, length = 1)", "String", "format"),
                "}"),
            source("LongStatement", "public class LongStatement extends GetStatement {",
                field("@BookField(order = 2, length = 2, direction = Direction.OUTPUT)", "int",
                    "lineCount"),
                field("@BookCollection(order = 3, maxOccurs = 3)", "List<Line>", "lines"),
                "}"),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = 4)", "String", "text"), "}"));
    }

    @Test
    void subclassCollectionRoundTrip() {
        BookCodec<Object> codec = statements();
        Object statement = codec.readResponse(bytes("L02ABCDEFGH"), codec.newInstance(), LATIN);
        assertThat(simpleName(statement)).isEqualTo("LongStatement");
        assertThat((List<?>) get(statement, "lines")).hasSize(2);
        assertThat(text(codec.encodeResponse(statement, LATIN))).isEqualTo("L02ABCDEFGH");
        assertThat(codec.maxResponseLength()).isEqualTo(1 + 2 + 3 * 4);
    }

    @Test
    void resetClearsSubclassCollections() {
        BookCodec<Object> codec = statements();
        Object statement = create(codec, "LongStatement");
        set(statement, "lines", null);
        codec.resetOutputCollections(statement);
        assertThat((List<?>) get(statement, "lines")).isEmpty();
        codec.resetOutputCollections(codec.newInstance());
    }

    @Test
    void copyIncludesBaseCollections() {
        BookCodec<Object> codec = codec(
            source("GetBatch",
                "@BookOperation @BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"P\", type = PaidBatch.class)})",
                "public class GetBatch {",
                field("@BookField(order = 1, length = 1)", "String", "kind"),
                field("@BookField(order = 2, length = 1, direction = Direction.INPUT)", "int",
                    "lineCount"),
                field("@BookCollection(order = 3, direction = Direction.INPUT, maxOccurs = 3)",
                    "List<Line>", "lines"),
                "}"),
            source("PaidBatch", "public class PaidBatch extends GetBatch {",
                field("@BookField(order = 4, length = 2, direction = Direction.OUTPUT)",
                    "String", "status"),
                "}"),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = 4)", "String", "text"), "}"));
        Object batch = codec.newInstance();
        List<Object> lines = List.of(create(codec, "Line"));
        set(batch, "lines", lines);
        Object result = codec.readResponse(bytes("POK"), batch, LATIN);
        assertThat(get(result, "lines")).isSameAs(lines);
        assertThat(get(result, "status")).isEqualTo("OK");
    }
}
