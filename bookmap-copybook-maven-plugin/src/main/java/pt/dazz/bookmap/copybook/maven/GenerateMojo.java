package pt.dazz.bookmap.copybook.maven;

import java.io.File;
import java.io.IOException;
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
        } catch (CopybookTreeException e) {
            throw new MojoFailureException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write the generated classes: "
                + e.getMessage(), e);
        }
    }

    /** Gives the copybook files that the patterns select, as absolute paths. */
    List<Path> copybooks() {
        if (!sourceDir.isDirectory()) {
            return List.of();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDir);
        scanner.setIncludes(includes == null || includes.isEmpty()
            ? new String[] {DEFAULT_INCLUDE} : includes.toArray(String[]::new));
        if (excludes != null) {
            scanner.setExcludes(excludes.toArray(String[]::new));
        }
        scanner.scan();
        List<Path> files = new ArrayList<>();
        Arrays.stream(scanner.getIncludedFiles()).sorted()
            .forEach(name -> files.add(sourceDir.toPath().resolve(name)));
        return files;
    }
}
