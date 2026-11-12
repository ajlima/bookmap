package pt.dazz.bookmap.copybook.gradle;

import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code copybooks} block of the {@code bookmap} extension.
 */
public abstract class CopybooksSpec {

    /**
     * Creates the block. Gradle calls this constructor.
     */
    public CopybooksSpec() {
    }

    /**
     * Gives the root of the copybook tree. The plugin scans it recursively. The default is
     * {@code src/main/copybook}.
     *
     * @return the source directory
     */
    public abstract DirectoryProperty getSourceDir();

    /**
     * Gives the directories with the {@code COPY} members, in search order. The search in
     * each directory is not recursive.
     *
     * @return the copy directories
     */
    public abstract ConfigurableFileCollection getCopyDirs();

    /**
     * Gives the base package. The directories below the source directory give the
     * subpackages.
     *
     * @return the base package
     */
    public abstract Property<String> getPackageName();

    /**
     * Tells if the plugin generates Lombok classes. The default is {@code false}.
     *
     * @return the Lombok setting
     */
    public abstract Property<Boolean> getLombok();

    /**
     * Tells if the plugin adds one package segment from the copybook name. The default is
     * {@code false}.
     *
     * @return the package-per-copybook setting
     */
    public abstract Property<Boolean> getPackagePerCopybook();

    /**
     * Gives the directory for the generated Java files. The default is
     * {@code build/generated/sources/copybook/main}.
     *
     * @return the output directory
     */
    public abstract DirectoryProperty getOutputDir();

    /**
     * Gives the include patterns for the copybook files. An empty list means
     * {@code **}{@code /*.cpy}.
     *
     * @return the include patterns
     */
    public abstract ListProperty<String> getIncludes();

    /**
     * Gives the exclude patterns for the copybook files.
     *
     * @return the exclude patterns
     */
    public abstract ListProperty<String> getExcludes();

    /**
     * Adds include patterns. The patterns replace the default {@code **}{@code /*.cpy}.
     *
     * @param patterns the Ant-style patterns, relative to the source directory
     * @return this block
     */
    public CopybooksSpec include(String... patterns) {
        getIncludes().addAll(List.of(patterns));
        return this;
    }

    /**
     * Adds exclude patterns.
     *
     * @param patterns the Ant-style patterns, relative to the source directory
     * @return this block
     */
    public CopybooksSpec exclude(String... patterns) {
        getExcludes().addAll(List.of(patterns));
        return this;
    }
}
