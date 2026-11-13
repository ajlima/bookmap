package pt.dazz.bookmap.spring.ctg;

import pt.dazz.bookmap.ctg.CtgTransport;

/**
 * Changes the builder of the CTG transport before the auto-configuration builds it.
 *
 * <p>The auto-configuration calls each customizer bean after it applies the
 * {@code bookmap.transport.ctg.*} properties. The order is the {@code @Order} of the beans.
 */
@FunctionalInterface
public interface CtgTransportBuilderCustomizer {

    /**
     * Changes the builder.
     *
     * @param builder the builder, with the values of the properties
     */
    void customize(CtgTransport.Builder builder);
}
