package pt.dazz.bookmap.copybook.model;

/**
 * Links a discriminator value to a subclass.
 *
 * @param value the discriminator value, as text
 * @param className the simple name of the subclass
 */
public record SubTypeEntry(String value, String className) {
}
