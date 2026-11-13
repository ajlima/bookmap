package pt.dazz.bookmap.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The selection of the transport, with the prefix {@code bookmap.transport}.
 *
 * <p>The Spring Boot configuration processor copies the text of each {@code @param} tag into the
 * configuration metadata. Do not use inline Javadoc tags in the {@code @param} tags.
 *
 * @param type The transport that the auto-configuration creates: ctg, mq, or http.
 *     Without this property, no transport auto-configuration runs. Then declare a Transport bean.
 */
@ConfigurationProperties("bookmap.transport")
public record BookmapTransportProperties(TransportType type) {
}
