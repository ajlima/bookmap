package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.DECIMAL;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;
import static pt.dazz.bookmap.processor.CodecTypes.numberAttributes;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.FieldModel.JavaKind;

/**
 * Generates the code that reads a discriminator and selects a class: a {@code typeOf} method
 * that gives the class of a value, and an {@code unknownValue} method that makes the exception
 * for an unknown value.
 */
final class Discriminator {

    private final CodecMembers members;
    private final FieldModel field;
    private final List<Layout> layouts;
    private final ClassName base;
    private final boolean numeric;
    private final String typeOf;
    private final String unknownValue;

    Discriminator(CodecMembers members, FieldModel field, List<Layout> layouts, ClassName base) {
        this.members = members;
        this.field = field;
        this.layouts = layouts;
        this.base = base;
        this.numeric = field.kind() != JavaKind.STRING;
        String suffix = String.join("_", base.simpleNames());
        this.typeOf = members.newName("typeOf_" + suffix);
        this.unknownValue = members.newName("unknownValue_" + suffix);
    }

    String typeOf() {
        return typeOf;
    }

    String unknownValue() {
        return unknownValue;
    }

    /** Gives the type of a read value: {@code BigDecimal} or {@code String}. */
    TypeName keyType() {
        return numeric ? DECIMAL : ClassName.get(String.class);
    }

    /** Gives {@code Class<? extends Base>}. */
    TypeName subtypeClass() {
        return ParameterizedTypeName.get(ClassName.get(Class.class),
            WildcardTypeName.subtypeOf(base));
    }

    /** Reads the value with a reader at the position of the discriminator. */
    CodeBlock read(CodeBlock reader) {
        String descriptor = members.names(field).descriptor();
        return numeric
            ? CodeBlock.of("$L.readNumber($N, $L)", reader, descriptor, numberAttributes(field))
            : CodeBlock.of("$L.readText($N, $L)", reader, descriptor, field.length());
    }

    /** Gives a value as a Java literal for the setter of the discriminator. */
    CodeBlock literal(String value) {
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("$S", value);
            case LONG, LONG_BOXED -> CodeBlock.of("$LL", value);
            default -> CodeBlock.of("$L", value);
        };
    }

    /** Gives the class of a discriminator value, or null for an unknown value. */
    MethodSpec typeOfMethod() {
        MethodSpec.Builder method = MethodSpec.methodBuilder(typeOf)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(subtypeClass()).addParameter(keyType(), "key");
        for (Layout layout : layouts) {
            if (layout.value() != null) {
                if (numeric) {
                    method.beginControlFlow("if (key != null && key.compareTo($T.valueOf($LL))"
                        + " == 0)", DECIMAL, layout.value());
                } else {
                    method.beginControlFlow("if (key.equals($S))", layout.value());
                }
                method.addStatement("return $T.class", ClassName.get(layout.type()))
                    .endControlFlow();
            }
        }
        return method.addStatement("return null").build();
    }

    /** Makes the exception for an unknown discriminator value. */
    MethodSpec unknownValueMethod() {
        String known = layouts.stream().map(Layout::value).filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
        CodeBlock key = numeric
            ? CodeBlock.of("(key == null ? $S : key.toPlainString())", "(blank)")
            : CodeBlock.of("key");
        return MethodSpec.methodBuilder(unknownValue)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(MAPPING_EXCEPTION).addParameter(keyType(), "key")
            .addStatement("return new $T($N + $S + $L + $S)", MAPPING_EXCEPTION,
                members.names(field).descriptor(), ": unknown discriminator value '", key,
                "'. Known values: " + known)
            .build();
    }
}
