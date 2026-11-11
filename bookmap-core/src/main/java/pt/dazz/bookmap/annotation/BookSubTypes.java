package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gives more than one layout to the end of a book (COBOL {@code REDEFINES}). A discriminator
 * field selects the layout. Each layout is a subtype of the annotated base class.
 *
 * <p>Put this annotation on an operation class (with {@code @BookOperation}) or on the item
 * class of a collection. The discriminator is a {@code @BookField} of the base class or of one of
 * its superclasses. Each subtype member must come after all members of the base class.
 *
 * <pre>{@code
 * @BookOperation
 * @BookSubTypes(discriminator = "accountType", value = {
 *     @BookSubTypes.Type(value = "01", type = CheckingAccount.class),
 *     @BookSubTypes.Type(value = "02", type = SavingsAccount.class)
 * })
 * public class GetAccount { ... }
 * }</pre>
 *
 * <p>On write, the codec sets the discriminator to the value of the subtype. On read, the codec
 * reads the discriminator and creates the subtype. An unknown value causes a
 * {@code MappingException}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BookSubTypes {

    /**
     * Gives the name of the discriminator field.
     *
     * @return the field name
     */
    String discriminator();

    /**
     * Gives the subtypes and their discriminator values.
     *
     * @return the subtypes, at least one
     */
    Type[] value();

    /**
     * Gives one subtype and its discriminator value.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @interface Type {

        /**
         * Gives the discriminator value of the subtype, as text. For a numeric discriminator,
         * {@code "001"} and {@code "1"} are the same value. For a text discriminator, the
         * spaces on the right have no effect.
         *
         * @return the value
         */
        String value();

        /**
         * Gives the subtype.
         *
         * @return the subtype
         */
        Class<?> type();
    }
}
