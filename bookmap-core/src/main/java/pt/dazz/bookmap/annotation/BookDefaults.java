package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import pt.dazz.bookmap.cobol.Usage;

/**
 * Sets default values for a class or a package.
 * Only attributes that the source code sets explicitly override the lower levels.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.CLASS)
public @interface BookDefaults {

    /**
     * Gives the default charset name. An empty value means no default.
     *
     * @return the charset name
     */
    String charset() default "";

    /**
     * Gives the default storage format of numeric fields.
     *
     * @return the usage
     */
    Usage usage() default Usage.DISPLAY;

    /**
     * Gives the default sign setting of numeric fields.
     *
     * @return true for signed fields
     */
    boolean signed() default false;

    /**
     * Gives the default date or time pattern.
     *
     * @return the pattern
     */
    String pattern() default "yyyyMMdd";
}
