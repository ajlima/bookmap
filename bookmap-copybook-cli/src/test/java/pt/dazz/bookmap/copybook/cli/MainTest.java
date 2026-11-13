package pt.dazz.bookmap.copybook.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private static String resource(String name) {
        try {
            return Path.of(MainTest.class.getResource("/" + name).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private int run(String... args) {
        return Main.run(new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8), args);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path generated(String name) {
        return dir.resolve("src/com/example/orders/" + name + ".java");
    }

    @Test
    void parseWritesModelJson() throws IOException {
        Path model = dir.resolve("orders.json");
        assertThat(run("parse", resource("ORDERS.cpy"), "--output", model.toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(model)).contains("\"name\" : \"ListOrders\"");
        assertThat(out()).contains("wrote the model of ORDERS.cpy to");
        assertThat(err()).contains("info: condition MORE-ORDERS is ignored");
    }

    @Test
    void generateFromCopybookWritesJavaFiles() throws IOException {
        assertThat(run("generate", resource("ORDERS.cpy"), "--package", "com.example.orders",
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(Files.readString(generated("ListOrders"))).contains("public String"
            + " getCustomerId()");
        assertThat(generated("OrderEntry")).exists();
        assertThat(out()).contains("wrote 3 classes to");
    }

    @Test
    void generateFromModelJson() throws IOException {
        Path model = dir.resolve("orders.json");
        run("parse", resource("ORDERS.cpy"), "--output", model.toString());
        assertThat(run("generate", model.toString(), "--package", "com.example.orders",
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(generated("TopProduct")).exists();
    }

    @Test
    void generateWithLombok() throws IOException {
        assertThat(run("generate", resource("ORDERS.cpy"), "--lombok", "--package",
            "com.example.orders", "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(generated("ListOrders"))).contains("@Data")
            .doesNotContain("getCustomerId");
    }

    @Test
    void configFileIsApplied() throws IOException {
        assertThat(run("generate", resource("ACCOUNT.cpy"), "--config",
            resource("account-config.json"), "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(dir.resolve("src/com/example/accounts/SavingsAccount.java")).exists();
    }

    @Test
    void lombokInConfigFile() throws IOException {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"lombok\": true, \"package\": \"com.example.orders\"}");
        assertThat(run("generate", resource("ORDERS.cpy"), "--config", config.toString(),
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(Files.readString(generated("ListOrders"))).contains("@Data");
    }

    @Test
    void commandLineWinsOverConfig() throws IOException {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"package\": \"com.example.other\", \"format\": \"free\","
            + " \"charset\": \"UTF-8\", \"recordName\": \"X\"}");
        assertThat(run("generate", resource("ORDERS.cpy"), "--config", config.toString(),
            "--package", "com.example.orders", "--format", "FIXED", "--charset", "ISO-8859-1",
            "--record-name", "ORDERS", "--strip-prefix", "ORDER-", "--copy-dir", dir.toString(),
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(Files.readString(generated("ListOrders"))).contains("getEntry()");
    }

    @Test
    void directionOptionsResolveAnArea() throws IOException {
        Path copybook = dir.resolve("QUERY.cpy");
        Files.writeString(copybook, String.join("\n",
            "       01  GET-ORDER.",
            "           05  QUERY-AREA.",
            "               10  ORDER-ID PIC X(10).",
            "           05  DETAIL-AREA REDEFINES QUERY-AREA.",
            "               10  STATUS PIC X(10)."));
        assertThat(run("generate", copybook.toString(), "--direction", "QUERY-AREA=input",
            "--direction", "DETAIL-AREA=OUTPUT", "--package", "com.example.orders",
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(Files.readString(generated("GetOrder"))).contains("Direction.OUTPUT");
        assertThat(run("generate", copybook.toString(), "--input-marker", "QUERY",
            "--output-marker", "DETAIL", "--package", "com.example.orders",
            "--output-dir", dir.resolve("src2").toString())).isEqualTo(Main.OK);
    }

    @Test
    void countFieldOptionLinksAFixedTable() throws IOException {
        Path copybook = dir.resolve("INVOICE.cpy");
        Files.writeString(copybook, String.join("\n",
            "       01  GET-INVOICE.",
            "           05  INVOICE-LINE-COUNT PIC 9(3).",
            "           05  LINE-TOTAL PIC 9(3).",
            "           05  INVOICE-LINE OCCURS 100 TIMES.",
            "               10  PRODUCT-CODE PIC X(12)."));
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"countFields\": {\"INVOICE-LINE\": \"LINE-TOTAL\"}}");
        assertThat(run("generate", copybook.toString(), "--config", config.toString(),
            "--count-field", "INVOICE-LINE=INVOICE-LINE-COUNT", "--package",
            "com.example.orders", "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(generated("GetInvoice")))
            .contains("dependsOn = \"invoiceLineCount\"");
        assertThat(run("generate", copybook.toString(), "--config", config.toString(),
            "--package", "com.example.orders", "--output-dir", dir.resolve("src2").toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(dir.resolve("src2/com/example/orders/GetInvoice.java")))
            .contains("dependsOn = \"lineTotal\"");
    }

    @Test
    void countFieldSuggestionIsPrinted() throws IOException {
        Path copybook = dir.resolve("INVOICE.cpy");
        Files.writeString(copybook, String.join("\n",
            "       01  GET-INVOICE.",
            "           05  INVOICE-LINE-COUNT PIC 9(3).",
            "           05  INVOICE-LINE OCCURS 100 TIMES.",
            "               10  PRODUCT-CODE PIC X(12)."));
        assertThat(run("parse", copybook.toString(), "--output",
            dir.resolve("model.json").toString())).isEqualTo(Main.OK);
        assertThat(err()).contains("info: INVOICE-LINE: INVOICE-LINE-COUNT can be the count"
            + " field of this fixed table")
            .contains("--count-field INVOICE-LINE=INVOICE-LINE-COUNT.");
    }

    @Test
    void dateOptionsGiveDateFields() throws IOException {
        Path config = dir.resolve("dates.json");
        Files.writeString(config, "{\"dates\": {\"ORDER-DATE\": \"ddMMyyyy\"}}");
        assertThat(run("generate", resource("ORDERS.cpy"), "--config", config.toString(),
            "--date", "*-DATE=yyyyMMdd", "--date", "ORDER-DATE=yyyyMMdd", "--package",
            "com.example.orders", "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(generated("OrderEntry")))
            .contains("pattern = \"yyyyMMdd\"", "private LocalDate orderDate;");
    }

    @Test
    void handlerOptionsImplementTheInterfaces() throws IOException {
        Path config = dir.resolve("handlers.json");
        Files.writeString(config, "{\"validatable\": {\"LIST-ORDERS\": \"Old\"}}");
        assertThat(run("generate", resource("ORDERS.cpy"), "--config", config.toString(),
            "--validatable", "LIST-ORDERS=com.example.checks.ReturnCodes", "--package",
            "com.example.orders", "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(Files.readString(generated("ListOrders"))).contains(
            "import com.example.checks.ReturnCodes;",
            "public class ListOrders implements Validatable {", "ReturnCodes.validate(this);");
        assertThat(run("generate", resource("ORDERS.cpy"), "--continuable",
            "LIST-ORDERS=OrderPaging", "--package", "com.example.orders", "--output-dir",
            dir.resolve("src2").toString())).isEqualTo(Main.FAILED);
        assertThat(err()).contains("error: ORDER-ENTRY is an INPUT_OUTPUT collection.");
    }

    @Test
    void errorsGiveExitCode1AndDiagnostics() throws IOException {
        Path copybook = dir.resolve("BAD.cpy");
        Files.writeString(copybook, "       01  BAD.\n           05  A PIC ZZ9.");
        assertThat(run("generate", copybook.toString(), "--package", "a", "--output-dir",
            dir.toString())).isEqualTo(Main.FAILED);
        assertThat(err()).contains("BAD.cpy:2:22: error: edited picture ZZ9 is not supported");
        assertThat(run("parse", copybook.toString(), "--output", "x.json"))
            .isEqualTo(Main.FAILED);
    }

    @Test
    void invalidModelGivesExitCode1() throws IOException {
        Path model = dir.resolve("broken.json");
        Files.writeString(model, "{}");
        assertThat(run("generate", model.toString(), "--package", "a", "--output-dir",
            dir.toString())).isEqualTo(Main.FAILED);
        assertThat(err()).contains("broken.json: error: missing key 'formatVersion' in $");
    }

    @Test
    void missingInputFileGivesExitCode1() {
        assertThat(run("generate", dir.resolve("none.json").toString(), "--package", "a",
            "--output-dir", dir.toString())).isEqualTo(Main.FAILED);
        assertThat(err()).startsWith("error: ");
    }

    @Test
    void freeFormat() throws IOException {
        Path copybook = dir.resolve("FREE.cpy");
        Files.writeString(copybook, "01 ORDER. *> free format\n05 ORDER-ID PIC X(10).");
        assertThat(run("generate", copybook.toString(), "--format", "free", "--package", "a",
            "--output-dir", dir.toString())).isEqualTo(Main.OK);
    }

    @Test
    void warningsGiveExitCode0() throws IOException {
        Path copybook = dir.resolve("WARN.cpy");
        Files.writeString(copybook, String.join("\n", "       01  ORDER.",
            "           05  ORDER-DATE PIC 9(8).",
            "           05  ORDER-DATE-X REDEFINES ORDER-DATE PIC X(8)."));
        assertThat(run("generate", copybook.toString(), "--package", "a", "--output-dir",
            dir.toString())).isEqualTo(Main.OK);
        assertThat(err()).contains("warning: ORDER-DATE-X redefines ORDER-DATE and is skipped");
    }

    @Test
    void generateShowsTheRecordLength() {
        assertThat(run("generate", resource("ORDERS.cpy"), "--package", "com.example.orders",
            "--output-dir", dir.resolve("src").toString())).isEqualTo(Main.OK);
        assertThat(err()).contains("info: record LIST-ORDERS has at most 552 bytes")
            .doesNotContain("warning:");
    }

    @Test
    void maxRecordLengthGivesAWarning() {
        assertThat(run("generate", resource("ORDERS.cpy"), "--max-record-length", "500",
            "--package", "com.example.orders", "--output-dir", dir.resolve("src").toString()))
            .isEqualTo(Main.OK);
        assertThat(err()).contains("ORDERS.cpy:4:8: warning: record LIST-ORDERS has at most"
            + " 552 bytes, more than the maximum record length of 500 bytes. The largest"
            + " tables: ORDER-ENTRY (20 x 24 = 480 bytes), TOP-PRODUCT (3 x 13 = 39 bytes).");
        assertThat(generated("ListOrders")).exists();
    }

    @Test
    void maxRecordLengthOnTheCommandLineWinsOverConfig() throws IOException {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"maxRecordLength\": 500}");
        assertThat(run("parse", resource("ORDERS.cpy"), "--config", config.toString(),
            "--output", dir.resolve("a.json").toString())).isEqualTo(Main.OK);
        assertThat(err()).contains("more than the maximum record length of 500 bytes");
        err.reset();
        assertThat(run("parse", resource("ORDERS.cpy"), "--config", config.toString(),
            "--max-record-length", "552", "--output", dir.resolve("b.json").toString()))
            .isEqualTo(Main.OK);
        assertThat(err()).doesNotContain("warning:");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "|the command is missing",
        "build ORDERS.cpy|unknown command 'build'. Use parse or generate.",
        "parse|the input file is missing",
        "parse A.cpy B.cpy|only one input file is permitted",
        "parse A.cpy --output|the option --output needs a value",
        "parse A.cpy --colour red|unknown option --colour",
        "parse A.cpy --package a|the option --package is only for generate",
        "parse A.cpy --lombok|the option --lombok is only for generate",
        "generate A.cpy --output a.json|the option --output is only for parse",
        "parse A.cpy|the option --output is missing",
        "generate A.cpy --output-dir x|the option --package is missing",
        "generate A.cpy --package a|the option --output-dir is missing",
        "generate A.json --package a --output-dir x --format free|parse and mapping options"
            + " are not permitted with a model input",
        "generate A.cpy --package a --output-dir x --format wide|unknown format 'wide'. Use"
            + " fixed or free.",
        "generate A.cpy --package a --output-dir x --charset NOPE|unknown charset 'NOPE'",
        "generate A.cpy --package a --output-dir x --direction A|--direction needs"
            + " NAME=DIRECTION, not 'A'",
        "generate A.cpy --package a --output-dir x --direction A=SIDEWAYS|unknown direction"
            + " in 'A=SIDEWAYS'. Use INPUT, OUTPUT, or INPUT_OUTPUT.",
        "generate A.cpy --package a --output-dir x --count-field LINE|--count-field needs"
            + " TABLE=FIELD, not 'LINE'",
        "generate A.json --package a --output-dir x --count-field A=B|parse and mapping"
            + " options are not permitted with a model input",
        "generate A.cpy --package a --output-dir x --max-record-length 0|--max-record-length"
            + " needs a number of bytes of 1 or more, not '0'",
        "parse A.cpy --output a.json --max-record-length 32K|--max-record-length needs a"
            + " number of bytes of 1 or more, not '32K'",
        "generate A.json --package a --output-dir x --max-record-length 100|parse and mapping"
            + " options are not permitted with a model input",
        "generate A.cpy --package a --output-dir x --date A|--date needs NAME=PATTERN, not 'A'",
        "generate A.cpy --package a --output-dir x --date A=|--date needs NAME=PATTERN, not"
            + " 'A='",
        "generate A.cpy --package a --output-dir x --date =yyyyMMdd|--date needs NAME=PATTERN,"
            + " not '=yyyyMMdd'",
        "generate A.cpy --package a --output-dir x --continuable A|--continuable needs"
            + " RECORD=CLASS, not 'A'",
        "generate A.cpy --package a --output-dir x --validatable A=|--validatable needs"
            + " RECORD=CLASS, not 'A='",
    })
    void usageErrorsGiveExitCode2(String commandLine, String message) {
        String[] args = commandLine == null ? new String[0] : commandLine.split(" ");
        assertThat(run(args)).isEqualTo(Main.USAGE);
        assertThat(err()).isEqualTo("error: " + message + System.lineSeparator()
            + "Run bookmap-copybook --help for the options." + System.lineSeparator());
    }

    @Test
    void invalidConfigGivesExitCode2() throws IOException {
        Path config = dir.resolve("bad.json");
        Files.writeString(config, "{\"colour\": 1}");
        assertThat(run("parse", "A.cpy", "--output", "a.json", "--config", config.toString()))
            .isEqualTo(Main.USAGE);
        assertThat(err()).contains("bad.json: unknown key 'colour' in $");
    }

    @Test
    void helpGivesExitCode0() {
        assertThat(run("--help")).isEqualTo(Main.OK);
        assertThat(out()).startsWith("Usage:");
        assertThat(run("generate", "-h")).isEqualTo(Main.OK);
    }

    @Test
    void mainPassesTheExitCode() {
        List<Integer> codes = new ArrayList<>();
        java.util.function.IntConsumer original = Main.exit;
        Main.exit = codes::add;
        PrintStream stdout = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));
            Main.main(new String[] {"--help"});
        } finally {
            System.setOut(stdout);
            Main.exit = original;
        }
        assertThat(codes).containsExactly(Main.OK);
    }
}
