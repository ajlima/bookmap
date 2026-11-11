package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class SubTypesValidationTest {

    private static final String ACCOUNT_ID = field(
        "@BookField(order = 10, length = 12, direction = Direction.INPUT)", "String", "accountId");
    private static final String ACCOUNT_TYPE = field("@BookField(order = 20, length = 2)", "int",
        "accountType");
    private static final String CARD = field(
        "@BookField(order = 30, length = 19, direction = Direction.OUTPUT)", "String",
        "cardNumberMask");
    private static final String RATE = field(
        "@BookField(order = 30, length = 5, scale = 4, direction = Direction.OUTPUT)",
        "BigDecimal", "interestRate");
    private static final String STANDARD = subTypes("accountType",
        "\"01\", CheckingAccount.class", "\"02\", SavingsAccount.class");

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /** Gives a {@code @BookSubTypes} annotation. Each entry is {@code "<value>", <Class>.class}. */
    private static String subTypes(String discriminator, String... entries) {
        return "@BookSubTypes(discriminator = \"" + discriminator + "\", value = {"
            + Arrays.stream(entries).map(entry -> {
                int comma = entry.lastIndexOf(',');
                return "@BookSubTypes.Type(value = " + entry.substring(0, comma).strip()
                    + ", type = " + entry.substring(comma + 1).strip() + ")";
            }).collect(Collectors.joining(", ")) + "})";
    }

    /** The operation base class {@code GetAccount}. */
    private static JavaFileObject base(String annotation, String... members) {
        return declare("", annotation, members);
    }

    /** The abstract operation base class {@code GetAccount}. */
    private static JavaFileObject abstractBase(String annotation, String... members) {
        return declare("abstract ", annotation, members);
    }

    private static JavaFileObject declare(String modifiers, String annotation,
                                          String... members) {
        return source("GetAccount", "@BookOperation " + annotation,
            "public " + modifiers + "class GetAccount {", String.join("\n", members), "}");
    }

    private static JavaFileObject standardBase() {
        return base(STANDARD, ACCOUNT_ID, ACCOUNT_TYPE);
    }

    /** A subclass. */
    private static JavaFileObject sub(String name, String parent, String... members) {
        return source(name, "public class " + name + " extends " + parent + " {",
            String.join("\n", members), "}");
    }

    private static JavaFileObject checking(String... members) {
        return sub("CheckingAccount", "GetAccount", members.length == 0 ? new String[] {CARD}
            : members);
    }

    private static JavaFileObject savings() {
        return sub("SavingsAccount", "GetAccount", RATE);
    }

    private Compilation compile(JavaFileObject... files) {
        return compiler.compile(files);
    }

    private static void assertError(Compilation compilation, String message) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
    }

    // ---- valid hierarchies ----

    @Test
    void validHierarchyCompiles() {
        Compilation compilation = compile(standardBase(), checking(), savings());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT,
            "test/GetAccount_BookCodec.class")).isPresent();
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT,
            "test/CheckingAccount_BookCodec.class")).isEmpty();
        ModelSnapshot snapshot = compiler.only();
        assertThat(snapshot.discriminator()).isEqualTo("accountType");
        assertThat(snapshot.variants()).containsExactly(
            new ModelSnapshot.Variant("CheckingAccount", "1",
                List.of("accountId", "accountType", "cardNumberMask")),
            new ModelSnapshot.Variant("SavingsAccount", "2",
                List.of("accountId", "accountType", "interestRate")));
    }

    @Test
    void classWithoutSubTypesHasNoVariants() {
        compile(source("Plain", "@BookOperation public class Plain {", ACCOUNT_ID, "}"));
        assertThat(compiler.only().discriminator()).isEmpty();
        assertThat(compiler.only().variants()).isEmpty();
    }

    @Test
    void abstractBaseCompiles() {
        CompilationSubject.assertThat(compile(abstractBase(STANDARD, ACCOUNT_ID,
            ACCOUNT_TYPE), checking(), savings())).succeededWithoutWarnings();
    }

    @Test
    void baseCanBeListed() {
        Compilation compilation = compile(base(subTypes("accountType",
            "\"00\", GetAccount.class", "\"01\", CheckingAccount.class"), ACCOUNT_ID,
            ACCOUNT_TYPE), checking());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().variants()).extracting(ModelSnapshot.Variant::value)
            .containsExactly("0", "1");
    }

    @Test
    void siblingsShareOrders() {
        CompilationSubject.assertThat(compile(standardBase(),
            checking(CARD, field("@BookField(order = 40, length = 1)", "String", "flag")),
            sub("SavingsAccount", "GetAccount", RATE,
                field("@BookField(order = 40, length = 1)", "String", "flag"))))
            .succeededWithoutWarnings();
    }

    @Test
    void subclassWithoutMembers() {
        CompilationSubject.assertThat(compile(standardBase(), checking(),
            sub("SavingsAccount", "GetAccount"))).succeededWithoutWarnings();
    }

    @Test
    void intermediateClassFieldsBelongToTheSubtype() {
        Compilation compilation = compile(standardBase(),
            sub("Card", "GetAccount", CARD), sub("CheckingAccount", "Card"), savings());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().variants().get(0).members())
            .containsExactly("accountId", "accountType", "cardNumberMask");
    }

    @Test
    void textDiscriminatorValuesLoseTrailingSpaces() {
        compile(base(subTypes("kind", "\"C  \", CheckingAccount.class"),
            field("@BookField(order = 20, length = 3)", "String", "kind")), checking());
        assertThat(compiler.only().variants().get(0).value()).isEqualTo("C");
    }

    @Test
    void subclassCollectionUsesItsOwnCount() {
        CompilationSubject.assertThat(compile(standardBase(),
            checking(field("@BookField(order = 30, length = 2)", "int", "entryCount"),
                field("@BookCollection(order = 40, maxOccurs = 5)", "List<Entry>", "entries")),
            savings(),
            source("Entry", "public class Entry {",
                field("@BookField(order = 1, length = 8)", "String", "reference"), "}")))
            .succeededWithoutWarnings();
    }

    // ---- discriminator ----

    @Test
    void discriminatorDoesNotExist() {
        assertError(compile(base(subTypes("kind", "\"01\", CheckingAccount.class"), ACCOUNT_ID,
            ACCOUNT_TYPE), checking()), "GetAccount: discriminator field 'kind' does not exist");
    }

    @Test
    void discriminatorInSubclass() {
        assertError(compile(base(subTypes("level", "\"01\", CheckingAccount.class"), ACCOUNT_ID,
                ACCOUNT_TYPE),
            checking(field("@BookField(order = 30, length = 2)", "int", "level"))),
            "GetAccount: discriminator field 'level' must be in the base class or its"
                + " superclasses");
    }

    @Test
    void discriminatorIsACollection() {
        assertError(compile(base(subTypes("lines", "\"01\", CheckingAccount.class"),
                field("@BookField(order = 10, length = 2)", "int", "count"),
                field("@BookCollection(order = 20, maxOccurs = 2)", "List<Entry>", "lines")),
            checking(),
            source("Entry", "public class Entry {",
                field("@BookField(order = 1, length = 8)", "String", "reference"), "}")),
            "GetAccount.lines (order 20): discriminator must be String, int, Integer, long, or"
                + " Long");
    }

    @Test
    void discriminatorWrongType() {
        assertError(compile(base(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2)", "BigDecimal", "accountType")),
            checking(), savings()),
            "GetAccount.accountType (order 20): discriminator must be String, int, Integer, long,"
                + " or Long");
    }

    @Test
    void discriminatorWithConverter() {
        assertError(compile(base(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, converter = CodeConverter.class)",
                    "String", "accountType")),
            checking(), savings(),
            source("CodeConverter",
                "public class CodeConverter implements FieldConverter<String> {",
                "public String decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(String v, FieldContext c) { return null; }",
                "}")),
            "GetAccount.accountType (order 20): discriminator must not have a converter");
    }

    @Test
    void discriminatorInputOnly() {
        assertError(compile(base(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, direction = Direction.INPUT)", "int",
                    "accountType")),
            checking(), savings()),
            "GetAccount.accountType (order 20): discriminator must have direction OUTPUT or"
                + " INPUT_OUTPUT");
    }

    @Test
    void outputDiscriminatorOnAConcreteBaseCompiles() {
        CompilationSubject.assertThat(compile(base(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, direction = Direction.OUTPUT)", "int",
                    "accountType")),
            checking(), savings())).succeededWithoutWarnings();
    }

    @Test
    void abstractBaseWithOutputDiscriminator() {
        assertError(compile(abstractBase(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, direction = Direction.OUTPUT)", "int",
                    "accountType")),
            checking(), savings()),
            "GetAccount: an abstract base class needs an INPUT_OUTPUT discriminator");
    }

    @Test
    void discriminatorWithOwnError() {
        assertError(compile(base(STANDARD, ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, usage = Usage.COMP3)", "String",
                    "accountType")),
            checking(), savings()),
            "GetAccount.accountType (order 20): COMP3 is not permitted for String");
    }

    @Test
    void baseCollectionAfterDiscriminator() {
        CompilationSubject.assertThat(compile(base(STANDARD, ACCOUNT_ID, ACCOUNT_TYPE,
                field("@BookField(order = 25, length = 2)", "int", "count"),
                field("@BookCollection(order = 26, maxOccurs = 2)", "List<Entry>", "lines")),
            checking(), savings(),
            source("Entry", "public class Entry {",
                field("@BookField(order = 1, length = 8)", "String", "reference"), "}")))
            .succeededWithoutWarnings();
    }

    @Test
    void fixedCollectionBeforeDiscriminatorIsPermitted() {
        CompilationSubject.assertThat(compile(base(STANDARD,
                field("@BookCollection(order = 10, maxOccurs = 2, fixed = true)", "List<Entry>",
                    "lines"),
                ACCOUNT_TYPE),
            checking(), savings(),
            source("Entry", "public class Entry {",
                field("@BookField(order = 1, length = 8)", "String", "reference"), "}")))
            .succeededWithoutWarnings();
    }

    @Test
    void collectionBeforeDiscriminator() {
        assertError(compile(base(STANDARD,
                field("@BookField(order = 5, length = 2)", "int", "count"),
                field("@BookCollection(order = 10, maxOccurs = 2)", "List<Entry>", "lines"),
                ACCOUNT_TYPE),
            checking(), savings(),
            source("Entry", "public class Entry {",
                field("@BookField(order = 1, length = 8)", "String", "reference"), "}")),
            "GetAccount.accountType (order 20): discriminator must not come after a variable collection");
    }

    // ---- types ----

    @Test
    void emptyTypeList() {
        assertError(compile(base("@BookSubTypes(discriminator = \"accountType\", value = {})",
            ACCOUNT_ID, ACCOUNT_TYPE)), "GetAccount: @BookSubTypes must list at least one type");
    }

    @Test
    void typeNotASubclass() {
        assertError(compile(base(subTypes("accountType", "\"01\", String.class"), ACCOUNT_ID,
            ACCOUNT_TYPE)), "GetAccount: java.lang.String is not a subclass of GetAccount");
    }

    @Test
    void typeIsAnInterface() {
        assertError(compile(base(subTypes("accountType", "\"01\", Runnable.class"), ACCOUNT_ID,
            ACCOUNT_TYPE)), "GetAccount: java.lang.Runnable is not a subclass of GetAccount");
    }

    @Test
    void typeIsPrimitive() {
        assertError(compile(base(subTypes("accountType", "\"01\", int.class"), ACCOUNT_ID,
            ACCOUNT_TYPE)), "GetAccount: int is not a subclass of GetAccount");
    }

    @Test
    void subtypeGeneratedLater() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.CheckingAccount", String.join("\n",
                "public class CheckingAccount extends GetAccount {", CARD, "}")));
        Compilation compilation = compiler.compile(List.of(generator),
            base(subTypes("accountType", "\"01\", CheckingAccount.class"), ACCOUNT_ID,
                ACCOUNT_TYPE));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().variants()).extracting(ModelSnapshot.Variant::type)
            .containsExactly("CheckingAccount");
    }

    @Test
    void unresolvedTypeIsLeftToJavac() {
        Compilation compilation = compile(base(subTypes("accountType",
            "\"01\", NoSuchAccount.class"), ACCOUNT_ID, ACCOUNT_TYPE));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("NoSuchAccount");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void unresolvedDiscriminatorIsLeftToJavac() {
        Compilation compilation = compile(base(subTypes("accountType",
                "\"01\", CheckingAccount.class").replace("\"accountType\"", "Names.MISSING"),
            ACCOUNT_ID, ACCOUNT_TYPE), checking());
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("Names");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void unresolvedValueIsLeftToJavac() {
        Compilation compilation = compile(base(subTypes("accountType",
            "Names.MISSING, CheckingAccount.class"), ACCOUNT_ID, ACCOUNT_TYPE), checking());
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("Names");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void abstractType() {
        assertError(compile(standardBase(),
                source("CheckingAccount", "public abstract class CheckingAccount extends"
                    + " GetAccount {", CARD, "}"),
                savings()),
            "GetAccount: type CheckingAccount must be public, not abstract, not generic, and have"
                + " a public no-argument constructor");
    }

    @Test
    void genericType() {
        assertError(compile(standardBase(),
                source("CheckingAccount", "public class CheckingAccount<X> extends GetAccount {",
                    CARD, "}"),
                savings()),
            "GetAccount: type CheckingAccount must be public, not abstract, not generic, and have"
                + " a public no-argument constructor");
    }

    @Test
    void abstractBaseListed() {
        assertError(compile(abstractBase(subTypes("accountType",
                "\"00\", GetAccount.class", "\"01\", CheckingAccount.class"), ACCOUNT_ID,
                ACCOUNT_TYPE), checking()),
            "GetAccount: type GetAccount must be public, not abstract, not generic, and have a"
                + " public no-argument constructor");
    }

    @Test
    void sameTypeTwice() {
        assertError(compile(base(subTypes("accountType", "\"01\", CheckingAccount.class",
                "\"03\", CheckingAccount.class"), ACCOUNT_ID, ACCOUNT_TYPE), checking()),
            "GetAccount: type CheckingAccount is listed more than once");
    }

    @Test
    void nestedSubTypes() {
        assertError(compile(standardBase(),
                source("CheckingAccount", subTypes("accountType", "\"09\", Premium.class"),
                    "public class CheckingAccount extends GetAccount {", CARD, "}"),
                sub("Premium", "CheckingAccount"), savings()),
            "CheckingAccount: subtypes in a subtype are not supported");
    }

    @Test
    void nestedSubTypesOnAnIntermediateClass() {
        assertError(compile(standardBase(),
                source("Card", subTypes("accountType", "\"09\", CheckingAccount.class"),
                    "public class Card extends GetAccount {", CARD, "}"),
                sub("CheckingAccount", "Card"), savings()),
            "Card: subtypes in a subtype are not supported");
    }

    @Test
    void typeWithBookOperation() {
        assertError(compile(standardBase(),
                source("CheckingAccount", "@BookOperation(\"CHECKING\")",
                    "public class CheckingAccount extends GetAccount {", CARD, "}"),
                savings()),
            "CheckingAccount: a subtype must not have @BookOperation");
    }

    @Test
    void typeIsSubclassOfAnotherType() {
        assertError(compile(base(subTypes("accountType", "\"01\", CheckingAccount.class",
                "\"02\", PremiumChecking.class"), ACCOUNT_ID, ACCOUNT_TYPE),
                checking(), sub("PremiumChecking", "CheckingAccount")),
            "PremiumChecking: a subtype must not extend another subtype");
    }

    // ---- values ----

    @Test
    void blankValue() {
        assertError(compile(base(subTypes("accountType", "\" \", CheckingAccount.class"),
                ACCOUNT_ID, ACCOUNT_TYPE), checking()),
            "GetAccount.accountType (order 20): discriminator value must not be blank");
    }

    @Test
    void duplicateValueAfterNormalization() {
        assertError(compile(base(subTypes("accountType", "\"01\", CheckingAccount.class",
                "\"1\", SavingsAccount.class"), ACCOUNT_ID, ACCOUNT_TYPE), checking(), savings()),
            "GetAccount.accountType (order 20): discriminator value '1' is used by"
                + " CheckingAccount and SavingsAccount");
    }

    @Test
    void valueNotANumber() {
        assertError(compile(base(subTypes("accountType", "\"A1\", CheckingAccount.class"),
                ACCOUNT_ID, ACCOUNT_TYPE), checking()),
            "GetAccount.accountType (order 20): discriminator value 'A1' is not a number");
    }

    @Test
    void numberTooLarge() {
        assertError(compile(base(subTypes("accountType", "\"123\", CheckingAccount.class"),
                ACCOUNT_ID, ACCOUNT_TYPE), checking()),
            "GetAccount.accountType (order 20): discriminator value '123' does not fit the field");
    }

    @Test
    void negativeNumberOnUnsignedField() {
        assertError(compile(base(subTypes("accountType", "\"-1\", CheckingAccount.class"),
                ACCOUNT_ID, ACCOUNT_TYPE), checking()),
            "GetAccount.accountType (order 20): discriminator value '-1' does not fit the field");
    }

    @Test
    void negativeNumberOnSignedField() {
        CompilationSubject.assertThat(compile(base(subTypes("accountType",
                "\"-1\", CheckingAccount.class"), ACCOUNT_ID,
                field("@BookField(order = 20, length = 2, signed = true)", "Long", "accountType")),
            checking())).succeededWithoutWarnings();
    }

    @Test
    void textTooLong() {
        assertError(compile(base(subTypes("kind", "\"ABCD\", CheckingAccount.class"),
                field("@BookField(order = 20, length = 3)", "String", "kind")), checking()),
            "GetAccount.kind (order 20): discriminator value 'ABCD' does not fit the field");
    }

    // ---- order and layout ----

    @Test
    void subclassOrderNotAfterBase() {
        assertError(compile(standardBase(),
                checking(field("@BookField(order = 15, length = 6, direction = Direction.OUTPUT)",
                    "String", "overdraftLimit")),
                savings()),
            "CheckingAccount.overdraftLimit (order 15): a subtype member must come after all base"
                + " members");
    }

    @Test
    void duplicateOrderInSubclassPath() {
        assertError(compile(standardBase(),
                checking(CARD, field("@BookField(order = 30, length = 1)", "String", "flag")),
                savings()),
            "CheckingAccount.flag (order 30): duplicate order, also used by"
                + " CheckingAccount.cardNumberMask");
    }

    @Test
    void subclassCountInBase() {
        assertError(compile(base(STANDARD, ACCOUNT_ID, ACCOUNT_TYPE,
                    field("@BookField(order = 25, length = 2)", "int", "entryCount")),
                checking(field("@BookCollection(order = 40, maxOccurs = 5, dependsOn ="
                    + " \"entryCount\")", "List<Entry>", "entries")),
                savings(),
                source("Entry", "public class Entry {",
                    field("@BookField(order = 1, length = 8)", "String", "reference"), "}")),
            "CheckingAccount.entries (order 40): dependsOn field 'entryCount' must be in the"
                + " subtype layout");
    }

    @Test
    void subclassCollectionWithoutCountByConvention() {
        assertError(compile(standardBase(),
                checking(field("@BookCollection(order = 40, maxOccurs = 5)", "List<Entry>",
                    "entries")),
                savings(),
                source("Entry", "public class Entry {",
                    field("@BookField(order = 1, length = 8)", "String", "reference"), "}")),
            "CheckingAccount.entries (order 40): dependsOn is missing and no member comes before"
                + " the collection");
    }

    @Test
    void continuableSubtypeWithInputOutputBaseCollection() {
        assertError(compile(base(STANDARD, ACCOUNT_ID, ACCOUNT_TYPE,
                field("@BookField(order = 25, length = 2)", "int", "count"),
                field("@BookCollection(order = 26, direction = Direction.INPUT_OUTPUT,"
                    + " maxOccurs = 2)", "List<Entry>", "lines")),
                source("CheckingAccount", "public class CheckingAccount extends GetAccount"
                    + " implements Continuable {", CARD,
                    "public boolean hasContinuation() { return false; }", "}"),
                savings(),
                source("Entry", "public class Entry {",
                    field("@BookField(order = 1, length = 8)", "String", "reference"), "}")),
            "GetAccount.lines (order 26): an INPUT_OUTPUT collection is not permitted in a"
                + " Continuable class (CheckingAccount)");
    }

    @Test
    void continuableSubtypeWithOutputBaseCollectionCompiles() {
        CompilationSubject.assertThat(compile(base(STANDARD, ACCOUNT_ID, ACCOUNT_TYPE,
                field("@BookField(order = 25, length = 2)", "int", "count"),
                field("@BookCollection(order = 26, maxOccurs = 2)", "List<Entry>", "lines")),
                source("CheckingAccount", "public class CheckingAccount extends GetAccount"
                    + " implements Continuable {", CARD,
                    "public boolean hasContinuation() { return false; }", "}"),
                savings(),
                source("Entry", "public class Entry {",
                    field("@BookField(order = 1, length = 8)", "String", "reference"), "}")))
            .succeededWithoutWarnings();
    }

    @Test
    void continuableBaseCompiles() {
        CompilationSubject.assertThat(compile(source("GetAccount", "@BookOperation " + STANDARD,
                "public class GetAccount implements Continuable {", ACCOUNT_ID, ACCOUNT_TYPE,
                "public boolean hasContinuation() { return false; }", "}"),
            checking(), savings())).succeededWithoutWarnings();
    }

    @Test
    void subclassFieldError() {
        assertError(compile(standardBase(),
                checking(field("@BookField(order = 30, length = 5, usage = Usage.COMP3)",
                    "String", "code")),
                savings()),
            "CheckingAccount.code (order 30): COMP3 is not permitted for String");
    }

    @Test
    void subclassMissingSetter() {
        assertError(compile(standardBase(),
                checking("@BookField(order = 30, length = 4) private String code;"
                    + " public String getCode() { return code; }"),
                savings()),
            "CheckingAccount.code (order 30): missing setter setCode");
    }
}
