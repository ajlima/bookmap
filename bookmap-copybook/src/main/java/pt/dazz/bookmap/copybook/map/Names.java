package pt.dazz.bookmap.copybook.map;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;

/**
 * Changes COBOL data names to Java class and field names.
 */
public final class Names {

    private static final Set<String> JAVA_LANG = Set.of("String", "Object", "Integer", "Long",
        "Number", "Math", "System", "Thread", "Record", "Enum", "Class", "Character", "Boolean",
        "Byte", "Short", "Double", "Float", "Void", "Process", "Runtime", "Package", "Module",
        "Error", "Exception");

    private final List<String> stripPrefixes;

    /**
     * Creates the name rules.
     *
     * @param stripPrefixes the prefixes to remove, for example {@code WS-}
     */
    public Names(List<String> stripPrefixes) {
        this.stripPrefixes = stripPrefixes.stream()
            .map(prefix -> prefix.toUpperCase(Locale.ROOT)).toList();
    }

    /**
     * Gives a class name in PascalCase.
     *
     * @param cobolName the COBOL name
     * @return the class name
     */
    public String className(String cobolName) {
        String name = camel(strip(cobolName), true);
        if (Character.isDigit(name.charAt(0))) {
            return "Record" + name;
        }
        return JAVA_LANG.contains(name) ? name + "Record" : name;
    }

    /**
     * Gives a field name in camelCase.
     *
     * @param cobolName the COBOL name
     * @return the field name
     */
    public String fieldName(String cobolName) {
        String name = camel(strip(cobolName), false);
        if (Character.isDigit(name.charAt(0))) {
            return "field" + name;
        }
        return SourceVersion.isKeyword(name) ? name + "Value" : name;
    }

    private String strip(String cobolName) {
        String upper = cobolName.toUpperCase(Locale.ROOT);
        for (String prefix : stripPrefixes) {
            if (upper.startsWith(prefix) && upper.length() > prefix.length()) {
                return upper.substring(prefix.length());
            }
        }
        return upper;
    }

    private static String camel(String name, boolean upperFirst) {
        StringBuilder out = new StringBuilder();
        for (String part : name.split("[-_]+")) {
            String lower = part.toLowerCase(Locale.ROOT);
            boolean first = out.isEmpty() && !upperFirst;
            out.append(first ? lower.charAt(0) : Character.toUpperCase(lower.charAt(0)))
                .append(lower, 1, lower.length());
        }
        return out.toString();
    }
}
