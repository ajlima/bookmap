package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the fallback constant of an enum. A read of a value that no constant contains gives
 * this constant. Without this annotation, an unknown value causes a
 * {@link pt.dazz.bookmap.exception.MappingException}.
 *
 * <p>Only one constant of an enum can have this annotation. A write of this constant uses its
 * {@link CobolValue} canonical value. If the constant has no {@link CobolValue}, the write
 * causes a {@link pt.dazz.bookmap.exception.MappingException}: the codec does not write a
 * value that it did not read.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface CobolUnknown {
}
