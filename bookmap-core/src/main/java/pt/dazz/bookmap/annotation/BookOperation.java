package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a book for one remote operation.
 * The annotation processor generates a codec for the class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BookOperation {

    /**
     * Gives the operation name. An empty value means the simple class name.
     *
     * @return the operation name
     */
    String value() default "";
}
