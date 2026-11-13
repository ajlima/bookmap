package pt.dazz.bookmap.copybook.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the parsed command line.
 */
final class Arguments {

    /** The options that need a value, and the commands that accept them. */
    private static final Map<String, Set<String>> VALUE_OPTIONS = Map.ofEntries(
        Map.entry("--format", Set.of("parse", "generate")),
        Map.entry("--charset", Set.of("parse", "generate")),
        Map.entry("--copy-dir", Set.of("parse", "generate")),
        Map.entry("--record-name", Set.of("parse", "generate")),
        Map.entry("--strip-prefix", Set.of("parse", "generate")),
        Map.entry("--input-marker", Set.of("parse", "generate")),
        Map.entry("--output-marker", Set.of("parse", "generate")),
        Map.entry("--direction", Set.of("parse", "generate")),
        Map.entry("--count-field", Set.of("parse", "generate")),
        Map.entry("--max-record-length", Set.of("parse", "generate")),
        Map.entry("--date", Set.of("parse", "generate")),
        Map.entry("--continuable", Set.of("parse", "generate")),
        Map.entry("--validatable", Set.of("parse", "generate")),
        Map.entry("--config", Set.of("parse", "generate")),
        Map.entry("--output", Set.of("parse")),
        Map.entry("--package", Set.of("generate")),
        Map.entry("--output-dir", Set.of("generate")));

    /** The options that change the parse or map step. A model input does not accept them. */
    static final Set<String> MODEL_OPTIONS = Set.of("--format", "--charset", "--copy-dir",
        "--record-name", "--strip-prefix", "--input-marker", "--output-marker", "--direction",
        "--count-field", "--max-record-length", "--date", "--continuable", "--validatable");

    final String command;
    final Path input;
    final Map<String, List<String>> values = new LinkedHashMap<>();
    boolean lombok;

    private Arguments(String command, Path input) {
        this.command = command;
        this.input = input;
    }

    /**
     * Parses the command line.
     *
     * @param args the arguments
     * @return the arguments, or {@code null} for a help request
     * @throws UsageException if the command line is not correct
     */
    static Arguments parse(String... args) {
        List<String> list = List.of(args);
        if (list.contains("--help") || list.contains("-h")) {
            return null;
        }
        if (list.isEmpty()) {
            throw new UsageException("the command is missing");
        }
        String command = list.get(0);
        if (!command.equals("parse") && !command.equals("generate")) {
            throw new UsageException("unknown command '" + command
                + "'. Use parse or generate.");
        }
        Path input = null;
        List<String[]> options = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) {
            String arg = list.get(i);
            if (arg.equals("--lombok")) {
                options.add(new String[] {arg, null});
            } else if (arg.startsWith("--")) {
                if (i + 1 >= list.size()) {
                    throw new UsageException("the option " + arg + " needs a value");
                }
                options.add(new String[] {arg, list.get(++i)});
            } else if (input == null) {
                input = Path.of(arg);
            } else {
                throw new UsageException("only one input file is permitted");
            }
        }
        if (input == null) {
            throw new UsageException("the input file is missing");
        }
        Arguments arguments = new Arguments(command, input);
        for (String[] option : options) {
            arguments.add(option[0], option[1]);
        }
        return arguments;
    }

    private void add(String name, String value) {
        if (name.equals("--lombok")) {
            if (!command.equals("generate")) {
                throw new UsageException("the option --lombok is only for generate");
            }
            lombok = true;
            return;
        }
        Set<String> commands = VALUE_OPTIONS.get(name);
        if (commands == null) {
            throw new UsageException("unknown option " + name);
        }
        if (!commands.contains(command)) {
            throw new UsageException("the option " + name + " is only for "
                + commands.iterator().next());
        }
        values.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
    }

    /** Gives the last value of an option, or {@code null}. */
    String value(String name) {
        List<String> all = values.get(name);
        return all == null ? null : all.get(all.size() - 1);
    }

    /** Gives all values of an option. */
    List<String> all(String name) {
        return values.getOrDefault(name, List.of());
    }

    /** Tells if the input is a model JSON file. */
    boolean modelInput() {
        return input.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
            .endsWith(".json");
    }
}
