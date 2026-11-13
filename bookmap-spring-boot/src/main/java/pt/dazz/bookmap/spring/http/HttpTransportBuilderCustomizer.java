package pt.dazz.bookmap.spring.http;

import pt.dazz.bookmap.http.HttpTransport;

/**
 * Changes the builder of the HTTP transport before the auto-configuration builds it, for example
 * to set a header supplier with a bearer token, or an {@code HttpClient} with a proxy.
 *
 * <p>The auto-configuration calls each customizer bean after it applies the
 * {@code bookmap.transport.http.*} properties. The order is the {@code @Order} of the beans.
 */
@FunctionalInterface
public interface HttpTransportBuilderCustomizer {

    /**
     * Changes the builder.
     *
     * @param builder the builder, with the values of the properties
     */
    void customize(HttpTransport.Builder builder);
}
