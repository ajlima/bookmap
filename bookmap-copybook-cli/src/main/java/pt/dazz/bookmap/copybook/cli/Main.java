package pt.dazz.bookmap.copybook.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.Copybooks;
import pt.dazz.bookmap.copybook.config.GeneratorConfig;
import pt.dazz.bookmap.copybook.config.GeneratorConfigJson;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.generate.GenerateOptions;
import pt.dazz.bookmap.copybook.generate.JavaClassGenerator;
import pt.dazz.bookmap.copybook.json.ModelJson;
import pt.dazz.bookmap.copybook.json.ModelJsonException;
import pt.dazz.bookmap.copybook.map.ConditionMode;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.SourceFormat;

/**
 * The command line of the BookMap copybook generator.
 *
 * <pre>
 * bookmap-copybook parse    &lt;copybook&gt; --output model.json [options]
 * bookmap-copybook generate &lt;copybook | model.json&gt; --package p --output-dir dir [options]
 * </pre>
 */
public final class Main {

    /** Success. Warnings are permitted. */
    static final int OK = 0;
    /** The copybook or the model has an error. */
    static final int FAILED = 1;
    /** The command line or the options are not correct. */
    static final int USAGE = 2;

    static final String HELP = """
        Usage:
          bookmap-copybook parse <copybook> --output <model.json> [options]
          bookmap-copybook generate <copybook | model.json> --package <name> \
        --output-dir <dir> [--lombok] [options]

        Options for a copybook input:
          --format fixed|free        The reference format. Default: fixed.
          --charset <name>           The charset of the copybook files. Default: UTF-8.
          --copy-dir <dir>           A directory with COPY members. Repeat for more.
          --record-name <name>       The record name for a copybook without level 01.
          --strip-prefix <prefix>    A prefix to remove from names, for example WS-.
          --input-marker <word>      A name part that marks an input alternative.
          --output-marker <word>     A name part that marks an output alternative.
          --direction <name>=<dir>   The direction (INPUT or OUTPUT) of an alternative.
          --conditions <name>=<mode> The mode (enum, constants, or none) of the level 88
                                     items of an item.
          --count-field <table>=<field>
                                     The count field of a fixed table (OCCURS n).
          --max-record-length <bytes>
                                     Warn if a record is longer. Default: no check.
          --date <name>=<pattern>    A date or time pattern for an item. A name with *
                                     is a name pattern, for example *-DATE=yyyyMMdd.
          --continuable <rec>=<cls>  The class with hasContinuation(record) of a record.
          --validatable <rec>=<cls>  The class with validate(record) of a record.
          --empty-item <table>=SPACES|LOW-VALUES
                                     The bytes of an unused slot of a fixed table.
          --empty-key <table>=<item> The key item that marks an empty slot of a fixed
                                     table: blank or zero.
          --config <file.json>       A file with all options. The command line has priority.
        """;

    /** The exit action. Tests replace it. */
    static IntConsumer exit = System::exit;

    private Main() {
    }

    /**
     * Runs the program and exits with the exit code.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        exit.accept(run(System.out, System.err, args));
    }

    /**
     * Runs the program.
     *
     * @param out the stream for results
     * @param err the stream for diagnostics and errors
     * @param args the arguments
     * @return the exit code: 0 success, 1 diagnostics with an error, 2 incorrect command line
     */
    public static int run(PrintStream out, PrintStream err, String... args) {
        try {
            Arguments arguments = Arguments.parse(args);
            if (arguments == null) {
                out.print(HELP);
                return OK;
            }
            return arguments.command.equals("parse") ? parse(arguments, out, err)
                : generate(arguments, out, err);
        } catch (UsageException e) {
            err.println("error: " + e.getMessage());
            err.println("Run bookmap-copybook --help for the options.");
            return USAGE;
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return FAILED;
        }
    }

    private static int parse(Arguments arguments, PrintStream out, PrintStream err)
        throws IOException {
        String output = arguments.value("--output");
        if (output == null) {
            throw new UsageException("the option --output is missing");
        }
        GeneratorConfig config = config(arguments);
        Copybooks.Result result = Copybooks.toModel(arguments.input, parseOptions(arguments,
            config), mappingOptions(arguments, config));
        if (report(result, err)) {
            return FAILED;
        }
        Files.writeString(Path.of(output), ModelJson.write(result.model()));
        out.println("wrote the model of " + arguments.input.getFileName() + " to " + output);
        return OK;
    }

    private static int generate(Arguments arguments, PrintStream out, PrintStream err)
        throws IOException {
        GeneratorConfig config = config(arguments);
        String packageName = arguments.value("--package") != null
            ? arguments.value("--package") : config.packageName();
        if (packageName == null) {
            throw new UsageException("the option --package is missing");
        }
        String outputDir = arguments.value("--output-dir");
        if (outputDir == null) {
            throw new UsageException("the option --output-dir is missing");
        }
        GenerateOptions options = new GenerateOptions(packageName,
            arguments.lombok || Boolean.TRUE.equals(config.lombok()));
        Copybooks.Result result;
        if (arguments.modelInput()) {
            for (String option : Arguments.MODEL_OPTIONS) {
                if (!arguments.all(option).isEmpty()) {
                    throw new UsageException("parse and mapping options are not permitted"
                        + " with a model input");
                }
            }
            try {
                result = Copybooks.generate(ModelJson.read(Files.readString(arguments.input)),
                    options);
            } catch (ModelJsonException e) {
                err.println(arguments.input.getFileName() + ": error: " + e.getMessage());
                return FAILED;
            }
        } else {
            result = Copybooks.generate(arguments.input, parseOptions(arguments, config),
                mappingOptions(arguments, config), options);
        }
        if (report(result, err)) {
            return FAILED;
        }
        JavaClassGenerator.writeTo(result.files(), Path.of(outputDir));
        out.println("wrote " + result.files().size() + " classes to " + outputDir);
        return OK;
    }

    /** Prints the diagnostics. Gives {@code true} if an error exists. */
    private static boolean report(Copybooks.Result result, PrintStream err) {
        for (Diagnostic diagnostic : result.diagnostics()) {
            err.println(diagnostic.format());
        }
        return result.hasErrors();
    }

    private static GeneratorConfig config(Arguments arguments) throws IOException {
        String file = arguments.value("--config");
        if (file == null) {
            return new GeneratorConfig(ParseOptions.defaults(), MappingOptions.defaults(),
                null, null);
        }
        Path path = Path.of(file);
        Path base = path.toAbsolutePath().getParent();
        try {
            return GeneratorConfigJson.read(Files.readString(path), base);
        } catch (ModelJsonException e) {
            throw new UsageException(file + ": " + e.getMessage());
        }
    }

    private static ParseOptions parseOptions(Arguments arguments, GeneratorConfig config) {
        ParseOptions base = config.parse();
        String format = arguments.value("--format");
        String charset = arguments.value("--charset");
        List<Path> copyDirs = new ArrayList<>(base.copyDirs());
        arguments.all("--copy-dir").forEach(dir -> copyDirs.add(Path.of(dir)));
        return new ParseOptions(
            format == null ? base.format() : format(format),
            charset == null ? base.charset() : charset(charset),
            copyDirs,
            arguments.value("--record-name") != null ? arguments.value("--record-name")
                : base.recordName());
    }

    private static SourceFormat format(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "fixed" -> SourceFormat.FIXED;
            case "free" -> SourceFormat.FREE;
            default -> throw new UsageException("unknown format '" + value
                + "'. Use fixed or free.");
        };
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            throw new UsageException("unknown charset '" + name + "'");
        }
    }

    private static MappingOptions mappingOptions(Arguments arguments, GeneratorConfig config) {
        MappingOptions base = config.mapping();
        Map<String, Direction> directions = new LinkedHashMap<>(base.directions());
        for (String value : arguments.all("--direction")) {
            int equals = value.indexOf('=');
            if (equals < 1) {
                throw new UsageException("--direction needs NAME=DIRECTION, not '" + value
                    + "'");
            }
            try {
                directions.put(value.substring(0, equals),
                    Direction.valueOf(value.substring(equals + 1).toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new UsageException("unknown direction in '" + value
                    + "'. Use INPUT, OUTPUT, or INPUT_OUTPUT.");
            }
        }
        Map<String, ConditionMode> conditions = new LinkedHashMap<>(base.conditions());
        for (String value : arguments.all("--conditions")) {
            int equals = value.indexOf('=');
            if (equals < 1) {
                throw new UsageException("--conditions needs NAME=MODE, not '" + value + "'");
            }
            try {
                conditions.put(value.substring(0, equals),
                    ConditionMode.of(value.substring(equals + 1)));
            } catch (IllegalArgumentException e) {
                throw new UsageException("unknown condition mode in '" + value
                    + "'. Use enum, constants, or none.");
            }
        }
        Map<String, String> countFields = new LinkedHashMap<>(base.countFields());
        for (String value : arguments.all("--count-field")) {
            int equals = value.indexOf('=');
            if (equals < 1) {
                throw new UsageException("--count-field needs TABLE=FIELD, not '" + value
                    + "'");
            }
            countFields.put(value.substring(0, equals), value.substring(equals + 1));
        }
        return new MappingOptions(
            concat(base.stripPrefixes(), arguments.all("--strip-prefix")),
            concat(base.inputMarkers(), arguments.all("--input-marker")),
            concat(base.outputMarkers(), arguments.all("--output-marker")),
            directions, base.subTypes(), countFields, maxRecordLength(arguments, base),
            pairs(arguments, "--date", "NAME=PATTERN", base.dates()),
            pairs(arguments, "--continuable", "RECORD=CLASS", base.continuable()),
            pairs(arguments, "--validatable", "RECORD=CLASS", base.validatable()),
            conditions,
            pairs(arguments, "--empty-item", "TABLE=SPACES or TABLE=LOW-VALUES", base.emptyItems()),
            pairs(arguments, "--empty-key", "TABLE=ITEM", base.emptyKeys()));
    }

    /** Adds the KEY=VALUE values of an option to the values of the options file. */
    private static Map<String, String> pairs(Arguments arguments, String option, String form,
                                             Map<String, String> base) {
        Map<String, String> pairs = new LinkedHashMap<>(base);
        for (String value : arguments.all(option)) {
            int equals = value.indexOf('=');
            if (equals < 1 || equals == value.length() - 1) {
                throw new UsageException(option + " needs " + form + ", not '" + value + "'");
            }
            pairs.put(value.substring(0, equals), value.substring(equals + 1));
        }
        return pairs;
    }

    private static Integer maxRecordLength(Arguments arguments, MappingOptions base) {
        String value = arguments.value("--max-record-length");
        if (value == null) {
            return base.maxRecordLength();
        }
        try {
            int length = Integer.parseInt(value);
            if (length >= 1) {
                return length;
            }
        } catch (NumberFormatException e) {
            // The message below applies.
        }
        throw new UsageException("--max-record-length needs a number of bytes of 1 or more,"
            + " not '" + value + "'");
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
