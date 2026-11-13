package pt.dazz.bookmap.spring.mq;

import pt.dazz.bookmap.mq.MqTransport;

/**
 * Changes the builder of the MQ transport before the auto-configuration builds it.
 *
 * <p>The auto-configuration calls each customizer bean after it applies the
 * {@code bookmap.transport.mq.*} properties. The order is the {@code @Order} of the beans.
 */
@FunctionalInterface
public interface MqTransportBuilderCustomizer {

    /**
     * Changes the builder.
     *
     * @param builder the builder, with the values of the properties
     */
    void customize(MqTransport.Builder builder);
}
