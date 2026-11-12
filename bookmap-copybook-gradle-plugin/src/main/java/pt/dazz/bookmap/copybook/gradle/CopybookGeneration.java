package pt.dazz.bookmap.copybook.gradle;

import com.palantir.javapoet.JavaFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import pt.dazz.bookmap.copybook.Copybooks;
import pt.dazz.bookmap.copybook.config.GeneratorConfig;
import pt.dazz.bookmap.copybook.config.GeneratorConfigJson;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.generate.GenerateOptions;
import pt.dazz.bookmap.copybook.generate.JavaClassGenerator;
import pt.dazz.bookmap.copybook.json.ModelJsonException;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.parse.ParseOptions;

/**
 * Runs the copybook generator for a list of copybooks in a source directory. This class does
 * not use the Gradle API.
 */
final class CopybookGeneration {

    private CopybookGeneration() {
    }

    /**
     * Gives the settings of a generation.
     *
     * @param sourceDir the root of the copybook tree
     * @param copyDirs the directories with {@code COPY} members, in search order
     * @param basePackage the base package, or {@code null} if it is not set
     * @param lombok {@code true} to generate Lombok classes
     * @param packagePerCopybook {@code true} to add a package segment from the copybook name
     */
    record Settings(Path sourceDir, List<Path> copyDirs, String basePackage, boolean lombok,
                    boolean packagePerCopybook) {

        /**
         * Creates the settings.
         *
         * @param sourceDir the source directory
         * @param copyDirs the copy directories
         * @param basePackage the base package
         * @param lombok the Lombok setting
         * @param packagePerCopybook the package-per-copybook setting
         */
        Settings {
            copyDirs = List.copyOf(copyDirs);
        }
    }

    /** Receives the messages of a generation. */
    interface Log {

        /**
         * Writes a warning.
         *
         * @param message the message
         */
        void warn(String message);

        /**
         * Writes an error.
         *
         * @param message the message
         */
        void error(String message);
    }

    /** The result of one copybook. */
    private record Generated(String relativePath, List<JavaFile> files) {
    }

    /**
     * Generates the classes of the copybooks. The method first deletes the contents of the
     * output directory. It writes no file if a copybook has an error.
     *
     * @param settings the settings
     * @param copybooks the copybook files, below the source directory
     * @param outputDir the directory for the Java files
     * @param log the receiver of the diagnostics
     * @throws IOException if a file cannot be read or written
     * @throws CopybookGenerationException if a copybook has an error, or if two copybooks
     *     generate the same class
     */
    static void run(Settings settings, List<Path> copybooks, Path outputDir, Log log)
        throws IOException {
        clean(outputDir);
        List<Path> sorted = copybooks.stream()
            .sorted(Comparator.comparing(c -> relative(settings.sourceDir(), c))).toList();
        List<Generated> generated = new ArrayList<>();
        int failed = 0;
        for (Path copybook : sorted) {
            Generated result = generate(settings, copybook, log);
            if (result == null) {
                failed++;
            } else {
                generated.add(result);
            }
        }
        if (failed > 0) {
            throw new CopybookGenerationException(failed
                + " copybook(s) have errors. See the messages above.");
        }
        checkCollisions(generated);
        for (Generated result : generated) {
            JavaClassGenerator.writeTo(result.files(), outputDir);
        }
    }

    /**
     * Gives the copy directories that the options files of the copybooks declare. The method
     * ignores an options file that is not valid: {@link #run} reports it.
     *
     * @param copybooks the copybook files
     * @return the copy directories, as absolute paths
     */
    static List<Path> configCopyDirs(List<Path> copybooks) {
        List<Path> dirs = new ArrayList<>();
        for (Path copybook : copybooks) {
            try {
                GeneratorConfig config = readConfig(copybook);
                if (config != null) {
                    dirs.addAll(config.parse().copyDirs());
                }
            } catch (IOException | ModelJsonException e) {
                // run() reports the error.
            }
        }
        return dirs;
    }

    /**
     * Gives the options file of a copybook: {@code <NAME>.json} next to {@code <NAME>.cpy}.
     *
     * @param copybook the copybook file
     * @return the options file, which can be missing
     */
    static Path optionsFile(Path copybook) {
        return copybook.resolveSibling(baseName(copybook) + ".json");
    }

    /** Generates one copybook. Gives {@code null} if the copybook has an error. */
    private static Generated generate(Settings settings, Path copybook, Log log)
        throws IOException {
        GeneratorConfig config;
        try {
            config = readConfig(copybook);
        } catch (ModelJsonException e) {
            log.error(optionsFile(copybook) + ": " + e.getMessage());
            return null;
        }
        String packageName = packageName(settings, copybook, config);
        boolean lombok = config != null && config.lombok() != null ? config.lombok()
            : settings.lombok();
        ParseOptions fileParse = config == null ? ParseOptions.defaults() : config.parse();
        List<Path> copyDirs = new ArrayList<>(fileParse.copyDirs());
        copyDirs.addAll(settings.copyDirs());
        ParseOptions parse = new ParseOptions(fileParse.format(), fileParse.charset(), copyDirs,
            fileParse.recordName());
        MappingOptions mapping = config == null ? MappingOptions.defaults() : config.mapping();
        Copybooks.Result result = Copybooks.generate(copybook, parse, mapping,
            new GenerateOptions(packageName, lombok));
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == Severity.ERROR) {
                log.error(diagnostic.format());
            } else {
                log.warn(diagnostic.format());
            }
        }
        if (result.hasErrors()) {
            return null;
        }
        return new Generated(relative(settings.sourceDir(), copybook), result.files());
    }

    private static String packageName(Settings settings, Path copybook,
                                      GeneratorConfig config) {
        if (config != null && config.packageName() != null) {
            return config.packageName();
        }
        if (settings.basePackage() == null) {
            throw new CopybookGenerationException("Set bookmap.copybooks.packageName.");
        }
        Path relativeDir = settings.sourceDir().relativize(copybook).getParent();
        return PackageNames.packageOf(settings.basePackage(),
            relativeDir == null ? Path.of("") : relativeDir, baseName(copybook),
            settings.packagePerCopybook());
    }

    /** Reads the options file of a copybook. Gives {@code null} if the file does not exist. */
    private static GeneratorConfig readConfig(Path copybook) throws IOException {
        Path file = optionsFile(copybook);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return GeneratorConfigJson.read(Files.readString(file),
            file.toAbsolutePath().getParent());
    }

    private static void checkCollisions(List<Generated> generated) {
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();
        for (Generated result : generated) {
            for (JavaFile file : result.files()) {
                String name = file.packageName() + "." + file.typeSpec().name();
                String owner = owners.putIfAbsent(name, result.relativePath());
                if (owner != null) {
                    collisions.add("class " + name + " comes from " + owner + " and "
                        + result.relativePath());
                }
            }
        }
        if (!collisions.isEmpty()) {
            throw new CopybookGenerationException("Two copybooks generate the same class:\n  "
                + String.join("\n  ", collisions)
                + "\nSet bookmap.copybooks.packagePerCopybook = true, set \"package\" in an"
                + " options file, or put the copybooks in different directories.");
        }
    }

    private static void clean(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            return;
        }
        try (Stream<Path> files = Files.walk(outputDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                if (!file.equals(outputDir)) {
                    Files.delete(file);
                }
            }
        }
    }

    private static String relative(Path sourceDir, Path copybook) {
        return sourceDir.relativize(copybook).toString().replace('\\', '/');
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
