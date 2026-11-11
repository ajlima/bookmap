package pt.dazz.bookmap.copybook.generate;

/**
 * Gives the options of the generate step.
 *
 * @param packageName the Java package of the generated classes
 * @param lombok {@code true} for Lombok annotations instead of getters and setters
 */
public record GenerateOptions(String packageName, boolean lombok) {
}
