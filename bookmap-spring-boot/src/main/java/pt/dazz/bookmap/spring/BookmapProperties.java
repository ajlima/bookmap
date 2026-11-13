package pt.dazz.bookmap.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the {@code BookmapClient} bean, with the prefix {@code bookmap}.
 *
 * <p>The Spring Boot configuration processor copies the text of each {@code @param} tag into
 * {@code META-INF/spring-configuration-metadata.json}. Thus, the IDE shows this text.
 * Do not use inline Javadoc tags in the {@code @param} tags: the IDE shows them as raw text.
 *
 * @param charset The default charset of the client. The charset must be single-byte, for example IBM037 or IBM500.
 *     A book with its own charset in BookDefaults uses that charset.
 * @param maxCalls The maximum number of calls for one execution of a book with continuation.
 *     The value must be 1 or more.
 */
@ConfigurationProperties("bookmap")
public record BookmapProperties(
        @DefaultValue("IBM037") String charset,
        @DefaultValue("1000") int maxCalls) {
}
