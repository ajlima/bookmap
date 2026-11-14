package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.DECIMAL;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * The private static helper methods of a codec. A codec contains only the helpers that its code
 * calls, in the order of {@link Helper}.
 */
final class HelperMethods {

    /** The helper methods that a generated codec can contain. */
    enum Helper {
        INT_VALUE, INTEGER_VALUE, LONG_VALUE, LONG_BOXED_VALUE, DECIMAL_OF_INTEGER,
        DECIMAL_OF_LONG, NOT_NULL, COLLECTION_SIZE, AT_ITEM
    }

    private final Set<Helper> used = EnumSet.noneOf(Helper.class);

    /** Gives a call of a helper, and adds the helper to the codec. */
    CodeBlock call(Helper helper, CodeBlock... arguments) {
        used.add(helper);
        return CodeBlock.of("$N($L)", name(helper), CodeBlock.join(List.of(arguments), ", "));
    }

    /** Gives the helpers that the code calls, in the order of {@link Helper}. */
    List<MethodSpec> methods() {
        return used.stream().map(HelperMethods::method).toList();
    }

    private static String name(Helper helper) {
        return switch (helper) {
            case INT_VALUE -> "intValue";
            case INTEGER_VALUE -> "integerValue";
            case LONG_VALUE -> "longValue";
            case LONG_BOXED_VALUE -> "longBoxedValue";
            case DECIMAL_OF_INTEGER, DECIMAL_OF_LONG -> "decimal";
            case NOT_NULL -> "notNull";
            case COLLECTION_SIZE -> "collectionSize";
            case AT_ITEM -> "atItem";
        };
    }

    private static MethodSpec method(Helper helper) {
        return switch (helper) {
            case INT_VALUE -> exact(helper, TypeName.INT, "intValueExact", "0", "int");
            case INTEGER_VALUE -> exact(helper, TypeName.INT.box(), "intValueExact", "null",
                "Integer");
            case LONG_VALUE -> exact(helper, TypeName.LONG, "longValueExact", "0", "long");
            case LONG_BOXED_VALUE -> exact(helper, TypeName.LONG.box(), "longValueExact", "null",
                "Long");
            case DECIMAL_OF_INTEGER -> decimal(TypeName.INT.box());
            case DECIMAL_OF_LONG -> decimal(TypeName.LONG.box());
            case NOT_NULL -> {
                TypeVariableName j = TypeVariableName.get("J");
                yield MethodSpec.methodBuilder(name(helper))
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC).addTypeVariable(j)
                    .returns(j).addParameter(j, "value").addParameter(String.class, "field")
                    .beginControlFlow("if (value == null)")
                    .addStatement("throw new $T(field + $S)", MAPPING_EXCEPTION,
                        ": converter returned null for a primitive field")
                    .endControlFlow()
                    .addStatement("return value").build();
            }
            case COLLECTION_SIZE -> MethodSpec.methodBuilder(name(helper))
                .addJavadoc("Checks the count of a collection. A blank count is 0.\n")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(int.class)
                .addParameter(Number.class, "count").addParameter(String.class, "field")
                .addParameter(int.class, "maxOccurs")
                .addStatement("long value = count == null ? 0 : count.longValue()")
                .beginControlFlow("if (value < 0 || value > maxOccurs)")
                .addStatement("throw new $T(field + $S + value + $S + maxOccurs)",
                    MAPPING_EXCEPTION, ": count ", " is not between 0 and maxOccurs ")
                .endControlFlow()
                .addStatement("return (int) value").build();
            case AT_ITEM -> MethodSpec.methodBuilder(name(helper))
                .addJavadoc("Adds the collection and the index of an item to a message.\n")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(MAPPING_EXCEPTION)
                .addParameter(String.class, "field").addParameter(int.class, "index")
                .addParameter(MAPPING_EXCEPTION, "e")
                .addStatement("return new $T(field + $S + index + $S + e.getMessage(), e)",
                    MAPPING_EXCEPTION, " item ", ": ")
                .build();
        };
    }

    /** Converts a read number to the Java type. A value that does not fit fails. */
    private static MethodSpec exact(Helper helper, TypeName returns, String method,
                                    String blank, String typeName) {
        return MethodSpec.methodBuilder(name(helper))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(returns)
            .addParameter(DECIMAL, "value").addParameter(String.class, "field")
            .beginControlFlow("if (value == null)")
            .addStatement("return $L", blank)
            .endControlFlow()
            .beginControlFlow("try")
            .addStatement("return value.$N()", method)
            .nextControlFlow("catch ($T e)", ArithmeticException.class)
            .addStatement("throw new $T(field + $S + value + $S, e)", MAPPING_EXCEPTION,
                ": value ", " does not fit " + typeName)
            .endControlFlow().build();
    }

    private static MethodSpec decimal(TypeName parameter) {
        return MethodSpec.methodBuilder("decimal")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(DECIMAL)
            .addParameter(parameter, "value")
            .addStatement("return value == null ? null : $T.valueOf(value)", DECIMAL).build();
    }
}
