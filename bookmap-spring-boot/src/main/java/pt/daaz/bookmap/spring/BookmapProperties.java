package pt.daaz.bookmap.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the {@code BookmapClient} bean, with the prefix {@code bookmap}.
 *
 * @param charset the name of the single-byte client charset; the default is {@code IBM037}
 * @param maxCalls the maximum number of calls for one request with continuation; the default is 1000
 */
@ConfigurationProperties("bookmap")
public record BookmapProperties(
        @DefaultValue("IBM037") String charset,
        @DefaultValue("1000") int maxCalls) {
}
