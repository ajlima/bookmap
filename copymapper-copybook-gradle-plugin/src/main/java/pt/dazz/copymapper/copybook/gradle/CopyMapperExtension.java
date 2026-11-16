package pt.dazz.copymapper.copybook.gradle;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;

/**
 * The {@code copymapper} extension of the CopyMapper copybook plugin.
 *
 * <pre>
 * copymapper {
 *     copybooks {
 *         packageName = "com.example.mainframe"
 *     }
 * }
 * </pre>
 */
public abstract class CopyMapperExtension {

    /**
     * Creates the extension. Gradle calls this constructor.
     */
    public CopyMapperExtension() {
    }

    /**
     * Gives the options of the copybook generation.
     *
     * @return the {@code copybooks} block
     */
    @Nested
    public abstract CopybooksSpec getCopybooks();

    /**
     * Configures the copybook generation.
     *
     * @param action the configuration of the {@code copybooks} block
     */
    public void copybooks(Action<? super CopybooksSpec> action) {
        action.execute(getCopybooks());
    }
}
