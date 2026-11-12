package pt.dazz.bookmap.copybook.tree;

import com.palantir.javapoet.JavaFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
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
 * Runs the copybook generator for a list of copybooks in a directory tree. The build plugins
 * (Gradle and Maven) use this class. It uses no build tool API.
 */
public final class CopybookTree {

    private CopybookTree() {
    }

    /**
     * Gives the settings of a generation.
     *
     * @param sourceDir the root of the copybook tree
     * @param copyDirs the directories with {@code COPY} members, in search order
     * @param basePackage the base package, or {@code null} if it is not set
     * @param lombok {@code true} to generate Lombok classes
     * @param packagePerCopybook {@code true} to add a package segment from the copybook name
     * @param missingPackageMessage the message if the base package is necessary and not set,
     *     in the words of the build tool
     * @param packagePerCopybookHint the setting that turns on {@code packagePerCopybook}, in
     *     the syntax of the build tool, for the collision message
     */
    public record Settings(Path sourceDir, List<Path> copyDirs, String basePackage,
                           boolean lombok, boolean packagePerCopybook,
                           String missingPackageMessage, String packagePerCopybookHint) {

        /**
         * Creates the settings.
         *
         * @param sourceDir the source directory
         * @param copyDirs the copy directories
         * @param basePackage the base package
         * @param lombok the Lombok setting
         * @param packagePerCopybook the package-per-copybook setting
         * @param missingPackageMessage the missing-package message
         * @param packagePerCopybookHint the package-per-copybook hint
         */
        public Settings {
            copyDirs = List.copyOf(copyDirs);
        }
    }

    /** Receives the messages of a generation. */
    public interface Log {

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
     * @throws CopybookTreeException if a copybook has an error, or if two copybooks
     *     generate the same class
     */
    public static void run(Settings settings, List<Path> copybooks, Path outputDir, Log log)
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
            throw new CopybookTreeException(failed
                + " copybook(s) have errors. See the messages above.");
        }
        checkCollisions(settings, generated);
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
    public static List<Path> configCopyDirs(List<Path> copybooks) {
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
    public static Path optionsFile(Path copybook) {
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
        } catch (IOException e) {
            log.error(optionsFile(copybook) + ": cannot read the file: " + e.getMessage());
            return null;
        }
        String packageName = packageName(settings, copybook, config);
        if (!SourceVersion.isName(packageName)) {
            log.error(relative(settings.sourceDir(), copybook) + ": the package '" + packageName
                + "' is not a valid Java package name");
            return null;
        }
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
            throw new CopybookTreeException(settings.missingPackageMessage());
        }
        Path relativeDir = settings.sourceDir().relativize(copybook).getParent();
        return PackageNames.packageOf(settings.basePackage(),
            relativeDir == null ? Path.of("") : relativeDir, baseName(copybook),
            settings.packagePerCopybook());
    }

    /**
     * Reads the options file of a copybook. Gives {@code null} if the file does not exist. An
     * invalid path in {@code copyDirs} gives a {@link ModelJsonException}.
     */
    private static GeneratorConfig readConfig(Path copybook) throws IOException {
        Path file = optionsFile(copybook);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String text = Files.readString(file);
        try {
            return GeneratorConfigJson.read(text, file.toAbsolutePath().getParent());
        } catch (InvalidPathException e) {
            throw new ModelJsonException("a path in copyDirs is not valid: " + e.getMessage());
        }
    }

    private static void checkCollisions(Settings settings, List<Generated> generated) {
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
            throw new CopybookTreeException("Two copybooks generate the same class:\n  "
                + String.join("\n  ", collisions)
                + "\nSet " + settings.packagePerCopybookHint() + ", set \"package\" in an"
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

    /**
     * Gives the path of a file relative to a base directory, with {@code /} separators. A
     * relative path keeps the build caches of the plugins valid in another checkout. If the
     * file has another root than the base (for example another drive or a network share on
     * Windows), no relative path exists, and the method gives the absolute path.
     *
     * @param base the base directory
     * @param file the file or directory
     * @return the relative path, or the absolute path if the roots are different
     */
    public static String relativePath(Path base, Path file) {
        Path from = base.toAbsolutePath().normalize();
        Path to = file.toAbsolutePath().normalize();
        Path path = java.util.Objects.equals(from.getRoot(), to.getRoot())
            ? from.relativize(to) : to;
        return path.toString().replace('\\', '/');
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
