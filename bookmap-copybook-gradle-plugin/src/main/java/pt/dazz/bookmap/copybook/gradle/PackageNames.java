package pt.dazz.bookmap.copybook.gradle;

import java.nio.file.Path;
import java.util.Locale;
import javax.lang.model.SourceVersion;

/**
 * Derives the Java package of a copybook from a base package and the directory of the copybook.
 */
final class PackageNames {

    private PackageNames() {
    }

    /**
     * Converts a directory name or a copybook name to one Java package segment.
     *
     * <ol>
     *   <li>Change the name to lower case.</li>
     *   <li>Replace each character that is not an ASCII letter, an ASCII digit, or {@code _}
     *       with {@code _}.</li>
     *   <li>Add {@code _} before a leading digit.</li>
     *   <li>Add {@code _} after a Java keyword or a reserved literal.</li>
     * </ol>
     *
     * @param name the name
     * @return the package segment
     */
    static String segment(String name) {
        StringBuilder segment = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            segment.append(valid ? c : '_');
        }
        if (Character.isDigit(segment.charAt(0))) {
            segment.insert(0, '_');
        }
        if (SourceVersion.isKeyword(segment)) {
            segment.append('_');
        }
        return segment.toString();
    }

    /**
     * Gives the package of a copybook.
     *
     * @param basePackage the base package
     * @param relativeDir the directory of the copybook, relative to the source directory; an
     *     empty path for the source directory itself
     * @param copybookName the copybook file name without the extension
     * @param perCopybook {@code true} to add a segment from the copybook name
     * @return the package
     */
    static String packageOf(String basePackage, Path relativeDir, String copybookName,
                            boolean perCopybook) {
        StringBuilder name = new StringBuilder(basePackage);
        for (Path part : relativeDir) {
            if (!part.toString().isEmpty()) {
                name.append('.').append(segment(part.toString()));
            }
        }
        if (perCopybook) {
            name.append('.').append(segment(copybookName));
        }
        return name.toString();
    }
}
