package pt.dazz.bookmap.copybook.gradle;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;

/**
 * The {@code bookmap} extension of the BookMap copybook plugin.
 *
 * <pre>
 * bookmap {
 *     copybooks {
 *         packageName = "com.example.mainframe"
 *     }
 * }
 * </pre>
 */
public abstract class BookmapExtension {

    /**
     * Creates the extension. Gradle calls this constructor.
     */
    public BookmapExtension() {
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
