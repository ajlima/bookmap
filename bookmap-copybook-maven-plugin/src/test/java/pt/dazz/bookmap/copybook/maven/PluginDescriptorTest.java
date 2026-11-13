package pt.dazz.bookmap.copybook.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Compares the hand-written plugin descriptor with {@link GenerateMojo}. */
class PluginDescriptorTest {

    private static Element plugin;
    private static Element mojo;

    @BeforeAll
    static void read() throws Exception {
        try (InputStream in = PluginDescriptorTest.class
            .getResourceAsStream("/META-INF/maven/plugin.xml")) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(in);
            plugin = document.getDocumentElement();
        }
        mojo = (Element) plugin.getElementsByTagName("mojo").item(0);
    }

    private static String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String child(Element parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element e && e.getTagName().equals(name)) {
                return e.getTextContent().trim();
            }
        }
        return null;
    }

    @Test
    void pluginHeader() {
        assertThat(child(plugin, "groupId")).isEqualTo("pt.dazz.bookmap");
        assertThat(child(plugin, "artifactId")).isEqualTo("bookmap-copybook-maven-plugin");
        assertThat(child(plugin, "version")).isNotBlank().doesNotContain("@");
        assertThat(child(plugin, "goalPrefix")).isEqualTo("bookmap-copybook");
        assertThat(plugin.getElementsByTagName("mojo").getLength()).isEqualTo(1);
    }

    @Test
    void mojoHeader() {
        assertThat(child(mojo, "goal")).isEqualTo("generate");
        assertThat(child(mojo, "phase")).isEqualTo("generate-sources");
        assertThat(child(mojo, "implementation")).isEqualTo(GenerateMojo.class.getName());
        assertThat(child(mojo, "language")).isEqualTo("java");
        assertThat(child(mojo, "threadSafe")).isEqualTo("true");
        assertThat(child(mojo, "requiresProject")).isEqualTo("true");
    }

    @Test
    void parametersAreTheFieldsOfTheMojo() {
        Map<String, String> fields = Arrays.stream(GenerateMojo.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .collect(Collectors.toMap(Field::getName, f -> f.getType().getName(),
                (a, b) -> a, LinkedHashMap::new));
        Map<String, String> parameters = new LinkedHashMap<>();
        NodeList nodes = ((Element) mojo.getElementsByTagName("parameters").item(0))
            .getElementsByTagName("parameter");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element parameter = (Element) nodes.item(i);
            parameters.put(child(parameter, "name"), child(parameter, "type"));
        }
        assertThat(parameters).isEqualTo(fields);
    }

    @Test
    void configurationGivesThePropertiesAndTheDefaults() {
        Element configuration = (Element) mojo.getElementsByTagName("configuration").item(0);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("project", "${project}");
        expected.put("sourceDir",
            "${bookmap.copybook.sourceDir}|${project.basedir}/src/main/copybook");
        expected.put("packageName", "${bookmap.copybook.packageName}|");
        expected.put("lombok", "${bookmap.copybook.lombok}|false");
        expected.put("packagePerCopybook", "${bookmap.copybook.packagePerCopybook}|false");
        expected.put("maxRecordLength", "${bookmap.copybook.maxRecordLength}");
        expected.put("outputDir", "${bookmap.copybook.outputDir}|"
            + "${project.build.directory}/generated-sources/copybook");
        expected.put("skip", "${bookmap.copybook.skip}|false");
        expected.put("buildDirectory", "${project.build.directory}");
        expected.put("pluginVersion", "${plugin.version}");
        Map<String, String> actual = new LinkedHashMap<>();
        for (var node = configuration.getFirstChild(); node != null;
             node = node.getNextSibling()) {
            if (node instanceof Element e) {
                String value = e.getTextContent().trim();
                String defaultValue = e.getAttribute("default-value");
                actual.put(e.getTagName(), e.hasAttribute("default-value")
                    || e.getTagName().equals("packageName") ? value + "|" + defaultValue : value);
            }
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(text(configuration, "project")).isEqualTo("${project}");
    }

    @Test
    void readOnlyAndRequiredParameters() {
        NodeList nodes = mojo.getElementsByTagName("parameter");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element parameter = (Element) nodes.item(i);
            String name = child(parameter, "name");
            boolean project = name.equals("project");
            boolean readOnly = project || name.equals("buildDirectory")
                || name.equals("pluginVersion");
            assertThat(child(parameter, "editable")).isEqualTo(readOnly ? "false" : "true");
            assertThat(child(parameter, "required")).isEqualTo(project ? "true" : "false");
            assertThat(child(parameter, "description")).isNotBlank();
        }
    }
}
