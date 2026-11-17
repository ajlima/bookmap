package pt.dazz.copymapper.copybook.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.Golden;
import pt.dazz.copymapper.copybook.ast.Usage;

class MappingSpecJsonTest {

    private static SpecNode node(String name, int level, String path, String picture,
            Usage usage, List<SpecNode> children) {
        return new SpecNode(name, level, path, picture, usage, null, null, null, null, List.of(),
                null, false, false, null, null, null, null, null, null, null, null, children);
    }

    private static MappingSpec spec(SpecNode root) {
        return new MappingSpec(1, "ACCOUNT.cpy", null, "IBM037", "BIG_ENDIAN", root);
    }

    private static MappingSpec account() {
        var id = node("ACCOUNT-ID", 5, "GET-ACCOUNT.ACCOUNT-ID", "X(12)", Usage.DISPLAY,
                List.of());
        var balance = node("BALANCE", 5, "GET-ACCOUNT.BALANCE", "9(7)V99", Usage.COMP3,
                List.of());
        var filler = node("FILLER", 5, "GET-ACCOUNT.FILLER", "X(3)", Usage.DISPLAY, List.of());
        return spec(node("GET-ACCOUNT", 1, "GET-ACCOUNT", null, null,
                List.of(id, balance, filler)));
    }

    private static MappingSpec everything() {
        var values = new LinkedHashMap<String, String>();
        values.put("01", "R.A");
        values.put("02", "R.B");
        var condition = new ConditionSpec("IS-OK", List.of(
                new ConditionRange(new ValueSpec("ALPHANUMERIC", "A"), null),
                new ConditionRange(new ValueSpec("NUMERIC", "1"), new ValueSpec("NUMERIC", "9"))));
        var alt = new SpecNode("A", 10, "R.A", "X(4)", Usage.DISPLAY, null, null, "R.B", null,
                List.of(), null, false, false, null, null, null, null, null, null,
                new VariantSpec.FixedSelection("R.A", null), null, List.of());
        var leaf = new SpecNode("AMOUNT", 10, "R.AMOUNT", "S9(5)V99", Usage.DISPLAY,
                Sign.TRAILING_SEPARATE, new OccursSpec(1, 5, "R.COUNT"), "R.OTHER",
                new ValueSpec("NUMERIC", "0"), List.of(condition),
                new SourceLocation("ACCOUNT.cpy", 12, 8), true, true, "amount",
                FieldDirection.INPUT_OUTPUT, JsonType.DECIMAL_STRING, "#,##0.00",
                new TableSpec(TableMode.KEY, "R.KEY"), "R.COUNT",
                new VariantSpec.Discriminator("R.TYPE", values), "amountValue", List.of(alt));
        var counted = new SpecNode("ROWS", 10, "R.ROWS", null, null, null, null, null, null,
                List.of(), null, false, false, null, FieldDirection.OUTPUT, JsonType.TEXT, null,
                new TableSpec(TableMode.COUNT, "R.N"), null,
                new VariantSpec.FixedSelection(null, "R.B"), null, List.of());
        var marker = new SpecNode("MARK", 10, "R.MARK", null, null, null, null, null, null,
                List.of(), null, false, false, null, FieldDirection.DERIVED, JsonType.DATE_TIME,
                null, new TableSpec(TableMode.LOW_VALUES, null), null,
                new VariantSpec.FixedSelection("R.A", "R.B"), null, List.of());
        var root = node("R", 1, "R", null, null, List.of(leaf, counted, marker));
        return new MappingSpec(1, "R.cpy", "abc123", "IBM037", "BIG_ENDIAN", root);
    }

    @Test
    void roundTripsEveryComponent() {
        var spec = everything();
        assertThat(MappingSpecJson.read(MappingSpecJson.write(spec))).isEqualTo(spec);
    }

    @Test
    void writesTheCanonicalForm() {
        String json = MappingSpecJson.write(account());
        Golden.assertMatches("spec/account.mapping.json", json);
        assertThat(json.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"")).doesNotContainPattern("\\s");
    }

    @Test
    void freezesTheHash() {
        assertThat(MappingSpecJson.sha256(account())).isEqualTo("9debff85d2aba256f0630a61e970f40d12870e717f0a6646f6321f3ab6f2f38c");
    }

    @Test
    void hashesTheUtf8OfTheCanonicalText() throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(MappingSpecJson.write(account())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(MappingSpecJson.sha256(account()))
                .isEqualTo(java.util.HexFormat.of().formatHex(digest))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void omitsNullsFalseAndEmptyLists() {
        String json = MappingSpecJson.write(account());
        assertThat(json).doesNotContain("\"sign\"", "\"blankWhenZero\"", "\"conditions\"",
                "\"edited\"", "null", "\"occurs\"");
        assertThat(json).contains("\"sourceName\"");
    }

    @Test
    void writesTheTrueFlags() {
        var leaf = new SpecNode("A", 5, "R.A", "X", Usage.DISPLAY, null, null, null, null,
                List.of(), null, true, true, null, null, null, null, null, null, null, null,
                List.of());
        String json = MappingSpecJson.write(spec(node("R", 1, "R", null, null, List.of(leaf))));
        assertThat(json).contains("\"blankWhenZero\":true", "\"edited\":true");
    }

    @Test
    void writesTheNodeKeysInOrder() {
        String json = MappingSpecJson.write(everything());
        List<String> keys = List.of("\"cobolName\"", "\"level\"", "\"path\"", "\"picture\"",
                "\"usage\"", "\"sign\"", "\"occurs\"", "\"redefines\"", "\"value\"",
                "\"conditions\"", "\"location\"", "\"blankWhenZero\"", "\"edited\"",
                "\"jsonName\"", "\"direction\"", "\"jsonType\"", "\"format\"", "\"table\"",
                "\"counter\"", "\"variant\"", "\"javaName\"", "\"children\"");
        int at = json.indexOf("\"cobolName\":\"AMOUNT\"");
        for (String key : keys) {
            int next = json.indexOf(key, at);
            assertThat(next).as(key).isGreaterThanOrEqualTo(at);
            at = key.equals("\"cobolName\"") ? at : next;
        }
        assertThat(json).startsWith("{\"formatVersion\":1,\"sourceName\":\"R.cpy\","
                + "\"sourceHash\":\"abc123\",\"charset\":\"IBM037\",\"byteOrder\":\"BIG_ENDIAN\","
                + "\"root\":{");
    }

    private static String account(String replace, String with) {
        String json = MappingSpecJson.write(account());
        assertThat(json).contains(replace);
        return json.replace(replace, with);
    }

    private static void assertRejects(String json, String message) {
        assertThatThrownBy(() -> MappingSpecJson.read(json))
                .isInstanceOf(MappingSpecFormatException.class).hasMessage(message);
    }

    private static void assertRejectsContaining(String json, String text) {
        assertThatThrownBy(() -> MappingSpecJson.read(json))
                .isInstanceOf(MappingSpecFormatException.class).hasMessageContaining(text);
    }

    @Test
    void rejectsUnknownKey() {
        assertRejects(account("\"root\":{", "\"root\":{\"colour\":\"red\","),
                "unknown key 'colour' in $.root");
    }

    @Test
    void rejectsFormatVersion2() {
        assertRejects(account("\"formatVersion\":1", "\"formatVersion\":2"),
                "unsupported formatVersion 2. This reader supports 1.");
    }

    @Test
    void rejectsLittleEndian() {
        assertRejectsContaining(account("BIG_ENDIAN", "LITTLE_ENDIAN"), "$.byteOrder");
    }

    @Test
    void rejectsMultiByteCharset() {
        assertRejectsContaining(account("IBM037", "UTF-8"), "UTF-8");
        assertRejectsContaining(account("IBM037", "UTF-8"), "$.charset");
    }

    @Test
    void rejectsNotJson() {
        assertThatThrownBy(() -> MappingSpecJson.read("{oops"))
                .isInstanceOf(MappingSpecFormatException.class)
                .hasMessageStartingWith("the text is not valid JSON");
    }

    @Test
    void rejectsTextAfterTheValue() {
        assertThatThrownBy(() -> MappingSpecJson.read(MappingSpecJson.write(account()) + " {}"))
                .isInstanceOf(MappingSpecFormatException.class)
                .hasMessageStartingWith("the text is not valid JSON");
    }

    @Test
    void rejectsNullValue() {
        assertRejectsContaining(account("\"root\":{", "\"root\":{\"picture\":null,"),
                "$.root.picture");
    }

    @Test
    void rejectsUnknownEnum() {
        assertRejectsContaining(account("\"root\":{", "\"root\":{\"direction\":\"SIDEWAYS\","),
                "$.root.direction");
    }

    @Test
    void rejectsWrongTypesAndMissingKeys() {
        assertRejectsContaining(account("\"level\":1", "\"level\":\"1\""), "level");
        assertRejectsContaining(account("\"level\":1,", ""), "missing key 'level'");
        assertRejectsContaining("[]", "$ must be an object");
        assertRejectsContaining("{\"formatVersion\":1,\"sourceName\":\"A\",\"charset\":"
                + "\"IBM037\",\"byteOrder\":\"BIG_ENDIAN\",\"root\":{\"cobolName\":\"R\","
                + "\"level\":1,\"path\":\"R\",\"children\":{}}}", "'children' in $.root");
        assertRejectsContaining(account("\"root\":{", "\"root\":{\"table\":{},"),
                "missing key 'mode' in $.root.table");
    }

    @Test
    void rejectsABadTable() {
        var spec = everything();
        String json = MappingSpecJson.write(spec).replace("\"mode\":\"KEY\"",
                "\"mode\":\"SPACES\"");
        assertRejectsContaining(json, "$.root.children[0].table");
    }

    @Test
    void readsOptionalFlagsWrittenAsFalse() {
        var read = MappingSpecJson.read(account("\"root\":{", "\"root\":{\"edited\":false,"));
        assertThat(read.root().edited()).isFalse();
    }

    @Test
    void readsAnEmptyVariantObject() {
        var read = MappingSpecJson.read(account("\"root\":{", "\"root\":{\"variant\":{},"));
        assertThat(read.root().variant()).isEqualTo(new VariantSpec.FixedSelection(null, null));
    }

    @Test
    void sortsNothing() {
        Map<String, String> order = new LinkedHashMap<>();
        order.put("Z", "R.Z");
        order.put("A", "R.A");
        var variant = new VariantSpec.Discriminator("R.T", order);
        var root = new SpecNode("R", 1, "R", null, null, null, null, null, null, List.of(), null,
                false, false, null, null, null, null, null, null, variant, null, List.of());
        var read = MappingSpecJson.read(MappingSpecJson.write(spec(root)));
        assertThat(((VariantSpec.Discriminator) read.root().variant()).values().keySet())
                .containsExactly("Z", "A");
    }

    @Test
    void rejectsAVariantThatIsNotAnObject() {
        assertRejectsContaining(account("\"root\":{", "\"root\":{\"variant\":[],"),
                "$.root.variant must be an object");
    }

    @Test
    void reportsAnUnavailableDigest() {
        assertThatThrownBy(() -> MappingSpecJson.digest("NOPE"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NOPE");
    }
}
