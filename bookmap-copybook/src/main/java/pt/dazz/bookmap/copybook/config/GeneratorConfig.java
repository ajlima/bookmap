package pt.dazz.bookmap.copybook.config;

import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.parse.ParseOptions;

/**
 * Gives all options of the generator, as a configuration file sets them.
 *
 * @param parse the parse options
 * @param mapping the mapping options
 * @param packageName the Java package, or {@code null} if the file does not set it
 * @param lombok the Lombok setting, or {@code null} if the file does not set it
 */
public record GeneratorConfig(ParseOptions parse, MappingOptions mapping, String packageName,
                              Boolean lombok) {
}
