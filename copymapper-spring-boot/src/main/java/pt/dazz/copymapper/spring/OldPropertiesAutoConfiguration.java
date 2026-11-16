package pt.dazz.copymapper.spring;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Stops the application if the environment has a property with the name that the library used
 * before release 0.28.0 ({@code bookmap.charset}, {@code bookmap.max-calls}, and the keys under
 * {@code bookmap.transport}).
 *
 * <p>Spring Boot ignores an unknown property. Without this check, an application with
 * {@code bookmap.charset} would start with the default charset and write incorrect data.
 * The check uses the relaxed binding of Spring Boot, so it also finds the environment variables,
 * for example {@code BOOKMAP_TRANSPORT_CTG_PASSWORD}. The message shows the names, never the values.
 *
 * <p>This class is temporary. A later release can remove it.
 */
@AutoConfiguration(beforeName = {
    "pt.dazz.copymapper.spring.CopyMapperAutoConfiguration",
    "pt.dazz.copymapper.spring.ctg.CtgTransportAutoConfiguration",
    "pt.dazz.copymapper.spring.http.HttpTransportAutoConfiguration",
    "pt.dazz.copymapper.spring.mq.MqTransportAutoConfiguration"})
public class OldPropertiesAutoConfiguration {

    private static final String MIGRATION =
        "https://github.com/dazz-pt/copymapper/wiki/Migrate-to-CopyMapper";
    private static final List<ConfigurationPropertyName> OLD_KEYS = List.of(
        ConfigurationPropertyName.of("bookmap.charset"),
        ConfigurationPropertyName.of("bookmap.max-calls"));
    private static final ConfigurationPropertyName OLD_TRANSPORT =
        ConfigurationPropertyName.of("bookmap.transport");

    /**
     * Checks the environment. Spring Boot calls this constructor.
     *
     * @param environment the environment of the application
     * @throws IllegalStateException if the environment has an old property
     */
    public OldPropertiesAutoConfiguration(Environment environment) {
        List<String> keys = oldKeys(environment);
        if (!keys.isEmpty()) {
            throw new IllegalStateException("The properties bookmap.* have a new name. "
                + "Rename the properties bookmap.* to copymapper.*: " + String.join(", ", keys)
                + ". See " + MIGRATION);
        }
    }

    /**
     * Gives the names of the old properties in the environment.
     *
     * @param environment the environment of the application
     * @return the names, in alphabetical order
     */
    static List<String> oldKeys(Environment environment) {
        Set<String> keys = new TreeSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource iterable) {
                iterable.stream().filter(OldPropertiesAutoConfiguration::isOld)
                    .forEach(name -> keys.add(name.toString()));
            }
        }
        return List.copyOf(keys);
    }

    private static boolean isOld(ConfigurationPropertyName name) {
        return OLD_KEYS.contains(name) || OLD_TRANSPORT.isAncestorOf(name);
    }
}
