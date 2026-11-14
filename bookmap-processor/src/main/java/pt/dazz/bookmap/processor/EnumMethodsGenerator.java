package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.DECIMAL;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.NameAllocator;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import pt.dazz.bookmap.processor.model.EnumModel;

/**
 * Generates the methods that read and write one enum type in one category. A codec has one pair
 * of methods for each enum type and category, which all fields of that enum type share.
 */
final class EnumMethodsGenerator {

    /** The names of the methods that read and write one enum type in one category. */
    record EnumMethods(String read, String write) {
    }

    private final NameAllocator allocator;
    /** The methods of each enum type and category, by qualified name and category. */
    private final Map<String, EnumMethods> enums = new HashMap<>();
    private final List<MethodSpec> methods = new ArrayList<>();

    EnumMethodsGenerator(NameAllocator allocator) {
        this.allocator = allocator;
    }

    /** Gives the methods of an enum type, and makes them the first time. */
    EnumMethods methodsOf(EnumModel model) {
        return enums.computeIfAbsent(model.type().getQualifiedName() + (model.numeric()
            ? "/number" : "/text"), key -> add(model));
    }

    /** Gives the methods of all enum types, in the order of their first use. */
    List<MethodSpec> methods() {
        return methods;
    }

    /**
     * Adds the methods that read and write one enum type in one category. The read method
     * gives the constant that contains a value. The write method gives the canonical value of
     * a constant. The methods use {@code ==} and {@code equals}, not a {@code switch}, which
     * needs a synthetic class.
     */
    private EnumMethods add(EnumModel model) {
        ClassName enumType = ClassName.get(model.type());
        String suffix = String.join("", enumType.simpleNames());
        String base = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        EnumMethods names = new EnumMethods(allocator.newName(base + "Of"),
            allocator.newName(base + "Value"));
        TypeName valueType = model.numeric() ? DECIMAL : ClassName.get(String.class);
        MethodSpec.Builder read = MethodSpec.methodBuilder(names.read())
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(enumType)
            .addParameter(valueType, "key").addParameter(String.class, "field");
        if (model.numeric()) {
            read.beginControlFlow("if (key == null)").addStatement("return null")
                .endControlFlow();
        }
        MethodSpec.Builder write = MethodSpec.methodBuilder(names.write())
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(valueType)
            .addParameter(enumType, "value").addParameter(String.class, "field")
            .beginControlFlow("if (value == null)").addStatement("return null")
            .endControlFlow();
        for (EnumModel.Constant constant : model.constants()) {
            List<CodeBlock> tests = new ArrayList<>();
            constant.values().forEach(value -> tests.add(model.numeric()
                ? CodeBlock.of("key.compareTo($L) == 0", value(model, value))
                : CodeBlock.of("key.equals($S)", value)));
            constant.ranges().forEach(range -> tests.add(CodeBlock.of(
                "key.compareTo($L) >= 0 && key.compareTo($L) <= 0",
                value(model, range.from()), value(model, range.thru()))));
            read.beginControlFlow("if ($L)", CodeBlock.join(tests.stream()
                    .map(test -> CodeBlock.of("($L)", test)).toList(), " || "))
                .addStatement("return $T.$N", enumType, constant.name())
                .endControlFlow();
            write.beginControlFlow("if (value == $T.$N)", enumType, constant.name())
                .addStatement("return $L", value(model, constant.canonical()))
                .endControlFlow();
        }
        if (!model.numeric()) {
            read.beginControlFlow("if (key.isEmpty())").addStatement("return null")
                .endControlFlow();
        }
        if (model.unknown() == null) {
            read.addStatement("throw new $T(field + $S + $L + $S)", MAPPING_EXCEPTION,
                ": unknown value '", model.numeric() ? "key.toPlainString()" : "key",
                "' for enum " + enumType.simpleName());
        } else {
            read.addStatement("return $T.$N", enumType, model.unknown());
        }
        write.addStatement("throw new $T(field + $S + value.name() + $S)", MAPPING_EXCEPTION,
            ": the constant ", " of enum " + enumType.simpleName() + " has no COBOL value");
        methods.add(read.build());
        methods.add(write.build());
        return names;
    }

    /** Gives one value of an enum as a Java expression. */
    private static CodeBlock value(EnumModel model, String value) {
        return model.numeric() ? CodeBlock.of("new $T($S)", DECIMAL, value)
            : CodeBlock.of("$S", value);
    }
}
