package pt.dazz.bookmap.copybook.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import pt.dazz.bookmap.copybook.tree.CopybookTree;
import pt.dazz.bookmap.copybook.tree.CopybookTreeException;

/**
 * Generates the BookMap classes from the COBOL copybooks in a directory tree, and adds the
 * generated sources to the compile source roots of the project.
 *
 * <p>The goal is {@code generate}, in the phase {@code generate-sources}. The descriptor
 * {@code META-INF/maven/plugin.xml} declares the parameters, their properties, and their
 * defaults. Maven sets the fields.
 */
public class GenerateMojo extends AbstractMojo {

    /** The message if the base package is necessary and not set. */
    static final String MISSING_PACKAGE =
        "Set the packageName parameter of bookmap-copybook-maven-plugin.";

    /** The setting that turns on packagePerCopybook, for the collision message. */
    static final String PACKAGE_PER_COPYBOOK_HINT = "<packagePerCopybook>true</packagePerCopybook>";

    /** The default include pattern. */
    static final String DEFAULT_INCLUDE = "**/*.cpy";

    /** The Maven project. */
    MavenProject project;

    /** The root of the copybook tree. The goal scans it recursively. */
    File sourceDir;

    /** The directories with the {@code COPY} members, in search order. */
    List<File> copyDirs;

    /** The base package. The directories below the source directory give the subpackages. */
    String packageName;

    /** Generate Lombok classes. */
    boolean lombok;

    /** Add one package segment from the copybook name. */
    boolean packagePerCopybook;

    /** The directory for the generated Java files. */
    File outputDir;

    /** The include patterns of the copybook files. The default is {@code **}{@code /*.cpy}. */
    List<String> includes;

    /** The exclude patterns of the files in the source directory that are not copybooks. */
    List<String> excludes;

    /** Do not generate the classes. The goal still adds the output directory as a source root. */
    boolean skip;

    /** The build directory of the project. The goal stores the fingerprint below it. */
    File buildDirectory;

    /** The version of this plugin. It is part of the fingerprint. */
    String pluginVersion;

    /**
     * Creates the goal. Maven calls this constructor.
     */
    public GenerateMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        project.addCompileSourceRoot(outputDir.getAbsolutePath());
        if (skip) {
            getLog().info("Skipped.");
            return;
        }
        List<Path> copybooks = copybooks();
        Path fingerprintFile = buildDirectory.toPath().resolve("bookmap-copybook")
            .resolve("fingerprint-" + Fingerprint.key(outputDir.getAbsolutePath()));
        String fingerprint;
        try {
            fingerprint = fingerprint(copybooks);
            if (outputDir.isDirectory() && Files.isRegularFile(fingerprintFile)
                && Files.readString(fingerprintFile).equals(fingerprint)) {
                getLog().info("The generated classes are up to date.");
                return;
            }
            Files.deleteIfExists(fingerprintFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read the inputs of the generation: "
                + e.getMessage(), e);
        }
        CopybookTree.Settings settings = new CopybookTree.Settings(sourceDir.toPath(),
            copyDirs == null ? List.of() : copyDirs.stream().map(File::toPath).toList(),
            packageName, lombok, packagePerCopybook, MISSING_PACKAGE, PACKAGE_PER_COPYBOOK_HINT);
        Log log = getLog();
        try {
            CopybookTree.run(settings, copybooks, outputDir.toPath(), new CopybookTree.Log() {
                @Override
                public void warn(String message) {
                    log.warn(message);
                }

                @Override
                public void error(String message) {
                    log.error(message);
                }
            });
            Files.createDirectories(fingerprintFile.getParent());
            Files.writeString(fingerprintFile, fingerprint);
        } catch (CopybookTreeException e) {
            throw new MojoFailureException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write the generated classes: "
                + e.getMessage(), e);
        }
    }

    /** Computes the fingerprint of all inputs of the generation. */
    private String fingerprint(List<Path> copybooks) throws IOException {
        List<Path> dirs = new ArrayList<>();
        if (copyDirs != null) {
            copyDirs.forEach(dir -> dirs.add(dir.toPath()));
        }
        dirs.addAll(CopybookTree.configCopyDirs(copybooks));
        List<String> parameters = Arrays.asList(packageName, Boolean.toString(lombok),
            Boolean.toString(packagePerCopybook), outputDir.getAbsolutePath(),
            String.valueOf(includes), String.valueOf(excludes));
        return Fingerprint.of(pluginVersion, parameters, sourceDir.toPath(), copybooks,
            scan(List.of("**/*.json"), null), dirs);
    }

    /** Gives the copybook files that the include and exclude patterns select. */
    List<Path> copybooks() {
        return scan(includes == null || includes.isEmpty() ? List.of(DEFAULT_INCLUDE) : includes,
            excludes);
    }

    /** Gives the files in the source directory that the patterns select, sorted. */
    private List<Path> scan(List<String> include, List<String> exclude) {
        if (!sourceDir.isDirectory()) {
            return List.of();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDir);
        scanner.setIncludes(include.toArray(String[]::new));
        if (exclude != null) {
            scanner.setExcludes(exclude.toArray(String[]::new));
        }
        scanner.scan();
        List<Path> files = new ArrayList<>();
        Arrays.stream(scanner.getIncludedFiles()).sorted()
            .forEach(name -> files.add(sourceDir.toPath().resolve(name)));
        return files;
    }
}
