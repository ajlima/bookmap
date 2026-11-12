package pt.dazz.bookmap.copybook.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;
import pt.dazz.bookmap.copybook.tree.CopybookTree;
import pt.dazz.bookmap.copybook.tree.CopybookTreeException;

/**
 * Generates the BookMap classes from the COBOL copybooks in a directory tree.
 */
@CacheableTask
public abstract class GenerateCopybookClasses extends DefaultTask {

    /**
     * Creates the task. Gradle calls this constructor.
     */
    public GenerateCopybookClasses() {
    }

    /**
     * Gives the root of the copybook tree.
     *
     * @return the source directory
     */
    @Internal
    public abstract DirectoryProperty getSourceDir();

    /**
     * Gives the include patterns. An empty list means {@code **}{@code /*.cpy}.
     *
     * @return the include patterns
     */
    @Input
    public abstract ListProperty<String> getIncludes();

    /**
     * Gives the exclude patterns.
     *
     * @return the exclude patterns
     */
    @Input
    public abstract ListProperty<String> getExcludes();

    /**
     * Gives the directories with the {@code COPY} members, in search order.
     *
     * @return the copy directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getCopyDirs();

    /**
     * Gives the search order of the copy directories, as paths relative to the source
     * directory. The file fingerprint of {@link #getCopyDirs()} ignores the order, but the
     * first match wins in the search. Relative paths keep the build cache relocatable.
     *
     * @return the copy directories, in search order
     */
    @Input
    public List<String> getCopyDirOrder() {
        Path source = getSourceDir().get().getAsFile().toPath();
        return paths(getCopyDirs()).stream()
            .map(dir -> CopybookTree.relativePath(source, dir)).toList();
    }

    /**
     * Gives the base package.
     *
     * @return the base package
     */
    @Input
    @Optional
    public abstract Property<String> getPackageName();

    /**
     * Tells if the task generates Lombok classes.
     *
     * @return the Lombok setting
     */
    @Input
    public abstract Property<Boolean> getLombok();

    /**
     * Tells if the task adds one package segment from the copybook name.
     *
     * @return the package-per-copybook setting
     */
    @Input
    public abstract Property<Boolean> getPackagePerCopybook();

    /**
     * Gives the directory for the generated Java files.
     *
     * @return the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /**
     * Gives the object factory. Gradle injects it.
     *
     * @return the object factory
     */
    @Inject
    protected abstract ObjectFactory getObjects();

    /**
     * Gives the copybook files: the files in the source directory that the include and the
     * exclude patterns select.
     *
     * @return the copybook files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getCopybookFiles() {
        PatternSet patterns = new PatternSet();
        List<String> includes = getIncludes().get();
        patterns.include(includes.isEmpty() ? List.of("**/*.cpy") : includes);
        patterns.exclude(getExcludes().get());
        return getSourceDir().getAsFileTree().matching(patterns);
    }

    /**
     * Gives the options files: the {@code .json} files in the source directory.
     *
     * @return the options files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getOptionsFiles() {
        return getSourceDir().getAsFileTree().matching(new PatternSet().include("**/*.json"));
    }

    /**
     * Gives the copy directories that the options files declare. Gradle reads the options
     * files when it takes the fingerprint of the inputs.
     *
     * @return the copy directories of the options files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getConfigCopyDirs() {
        FileTree copybooks = getCopybookFiles();
        return getObjects().fileCollection().from((Callable<List<File>>)
            () -> CopybookTree.configCopyDirs(paths(copybooks)).stream()
                .map(Path::toFile).toList());
    }

    /**
     * Generates the classes.
     */
    @TaskAction
    public void generate() {
        CopybookTree.Settings settings = new CopybookTree.Settings(
            getSourceDir().get().getAsFile().toPath(), paths(getCopyDirs()),
            getPackageName().getOrNull(), getLombok().get(), getPackagePerCopybook().get(),
            "Set bookmap.copybooks.packageName.", "bookmap.copybooks.packagePerCopybook = true");
        Logger logger = getLogger();
        try {
            CopybookTree.run(settings, paths(getCopybookFiles()),
                getOutputDir().get().getAsFile().toPath(), new CopybookTree.Log() {
                    @Override
                    public void warn(String message) {
                        logger.warn(message);
                    }

                    @Override
                    public void error(String message) {
                        logger.error(message);
                    }
                });
        } catch (CopybookTreeException e) {
            throw new GradleException(e.getMessage());
        } catch (IOException e) {
            throw new GradleException("Cannot write the generated classes: " + e.getMessage(),
                e);
        }
    }

    private static List<Path> paths(FileCollection files) {
        return files.getFiles().stream().map(File::toPath).toList();
    }
}
