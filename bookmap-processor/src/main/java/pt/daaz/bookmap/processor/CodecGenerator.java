package pt.daaz.bookmap.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.NameAllocator;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.daaz.bookmap.annotation.Direction;
import pt.daaz.bookmap.processor.model.FieldModel;
import pt.daaz.bookmap.processor.model.MemberModel;
import pt.daaz.bookmap.processor.model.OperationModel;

/**
 * Generates the {@code BookCodec} implementation of one operation class.
 *
 * <p>The generated codec uses the getters and setters of the class, {@code BookWriter}, and
 * {@code BookReader}. It does not use reflection. Each field descriptor is a
 * {@code private static final String} constant. Each date or time formatter is a
 * {@code private static final DateTimeFormatter}. Each converter is a {@code private final}
 * field. Collections are not encoded or decoded yet.
 */
final class CodecGenerator {

    /** The value of the {@code @Generated} annotation of each codec. */
    static final String GENERATOR = "pt.daaz.bookmap.processor.BookmapProcessor";

    private static final ClassName BOOK_CODEC = ClassName.get("pt.daaz.bookmap.spi", "BookCodec");
    private static final ClassName BOOK_WRITER = ClassName.get("pt.daaz.bookmap.codec",
        "BookWriter");
    private static final ClassName BOOK_READER = ClassName.get("pt.daaz.bookmap.codec",
        "BookReader");
    private static final ClassName FORMATS = ClassName.get("pt.daaz.bookmap.codec", "Formats");
    private static final ClassName CODEC_CONTEXT = ClassName.get("pt.daaz.bookmap.convert",
        "CodecContext");
    private static final ClassName FIELD_CONTEXT = ClassName.get("pt.daaz.bookmap.convert",
        "FieldContext");
    private static final ClassName USAGE = ClassName.get("pt.daaz.bookmap.annotation", "Usage");
    private static final ClassName MAPPING_EXCEPTION = ClassName.get("pt.daaz.bookmap.exception",
        "MappingException");
    private static final ClassName GENERATED = ClassName.get("javax.annotation.processing",
        "Generated");
    private static final ClassName DECIMAL = ClassName.get(BigDecimal.class);
    private static final TypeName BYTES = TypeName.get(byte[].class);

    /** The helper methods that a generated codec can contain. */
    private enum Helper {
        INT_VALUE, INTEGER_VALUE, LONG_VALUE, LONG_BOXED_VALUE, DECIMAL_OF_INTEGER,
        DECIMAL_OF_LONG, NOT_NULL
    }

    /** The names of the generated members of one field. */
    private record Names(String descriptor, String formatter, String converter) {
    }

    private final OperationModel model;
    private final ClassName type;
    private final NameAllocator allocator = new NameAllocator();
    private final Map<FieldModel, Names> names = new HashMap<>();
    private final Set<Helper> helpers = EnumSet.noneOf(Helper.class);

    private CodecGenerator(OperationModel model) {
        this.model = model;
        this.type = ClassName.get(model.type());
        // The local variables of the generated methods.
        List.of("value", "target", "data", "ctx", "writer", "reader").forEach(allocator::newName);
    }

    /**
     * Gives the name of the codec of an operation class: {@code <Class>_BookCodec} in the same
     * package. For a nested class {@code Outer.Inner}, the name is {@code Outer_Inner_BookCodec}.
     *
     * @param operationClass the operation class
     * @return the codec class name
     */
    static ClassName codecName(TypeElement operationClass) {
        ClassName name = ClassName.get(operationClass);
        return ClassName.get(name.packageName(),
            String.join("_", name.simpleNames()) + "_BookCodec");
    }

    /**
     * Generates the codec of an operation class.
     *
     * @param model the model of the operation class
     * @return the source file of the codec
     */
    static JavaFile generate(OperationModel model) {
        return new CodecGenerator(model).file();
    }

    private JavaFile file() {
        ClassName codec = codecName(model.type());
        TypeSpec.Builder builder = TypeSpec.classBuilder(codec)
            .addAnnotation(AnnotationSpec.builder(GENERATED).addMember("value", "$S", GENERATOR)
                .build())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(model.type())
            .addSuperinterface(ParameterizedTypeName.get(BOOK_CODEC, type));
        List<FieldModel> fields = model.members().stream()
            .filter(FieldModel.class::isInstance).map(FieldModel.class::cast).toList();
        fields.forEach(field -> addFieldMembers(builder, field));
        builder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
            .addJavadoc("Makes the codec. The {@code ServiceLoader} calls this constructor.\n")
            .build());
        builder.addMethod(override("type")
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), type))
            .addStatement("return $T.class", type).build());
        builder.addMethod(override("operationName").returns(String.class)
            .addStatement("return $S", model.operationName()).build());
        builder.addMethod(override("charsetName").returns(String.class)
            .addStatement("return $S", model.charsetName()).build());
        builder.addMethod(override("maxResponseLength").returns(int.class)
            .addStatement("return $L", fields.stream().filter(f -> f.direction().isOutput())
                .mapToInt(FieldModel::byteLength).sum()).build());
        builder.addMethod(override("newInstance").returns(type)
            .addStatement("return new $T()", type).build());
        builder.addMethod(encode("encodeRequest", fields, Direction::isInput));
        builder.addMethod(override("decodeResponse")
            .addParameter(BYTES, "data").addParameter(type, "target")
            .addParameter(CODEC_CONTEXT, "ctx")
            .addCode(decode(fields, Direction::isOutput)).build());
        builder.addMethod(override("decodeRequest").returns(type)
            .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T target = newInstance()", type)
            .addCode(decode(fields, Direction::isInput))
            .addStatement("return target").build());
        builder.addMethod(encode("encodeResponse", fields, Direction::isOutput));
        builder.addMethod(override("resetOutputCollections").addParameter(type, "value")
            .build());
        helpers.forEach(helper -> builder.addMethod(helper(helper)));
        return JavaFile.builder(codec.packageName(), builder.build()).build();
    }

    private static MethodSpec.Builder override(String name) {
        return MethodSpec.methodBuilder(name).addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC);
    }

    private void addFieldMembers(TypeSpec.Builder builder, FieldModel field) {
        String constant = upperSnake(field.name());
        String descriptor = allocator.newName(constant);
        builder.addField(FieldSpec.builder(String.class, descriptor, Modifier.PRIVATE,
            Modifier.STATIC, Modifier.FINAL).initializer("$S", field.descriptor()).build());
        String formatter = null;
        String converter = null;
        switch (field.kind()) {
            case LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME -> {
                formatter = allocator.newName(constant + "_FORMAT");
                builder.addField(FieldSpec.builder(DateTimeFormatter.class, formatter,
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of($S)", FORMATS, field.pattern()).build());
            }
            case CONVERTED -> {
                converter = allocator.newName(field.name() + "Converter");
                ClassName converterType = ClassName.get(field.converter());
                builder.addField(FieldSpec.builder(converterType, converter, Modifier.PRIVATE,
                    Modifier.FINAL).initializer("new $T()", converterType).build());
            }
            default -> {
                // Text and numbers need no other member.
            }
        }
        names.put(field, new Names(descriptor, formatter, converter));
    }

    /** Changes {@code orderId} to {@code ORDER_ID}. */
    private static String upperSnake(String name) {
        StringBuilder result = new StringBuilder().append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) {
                result.append('_');
            }
            result.append(c);
        }
        return result.toString().toUpperCase(Locale.ROOT);
    }

    private MethodSpec encode(String name, List<FieldModel> fields,
                              Predicate<Direction> direction) {
        MethodSpec.Builder method = override(name).returns(BYTES)
            .addParameter(type, "value").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T writer = new $T(ctx)", BOOK_WRITER, BOOK_WRITER);
        for (FieldModel field : fields) {
            if (direction.test(field.direction())) {
                method.addStatement(write(field));
            }
        }
        return method.addStatement("return writer.toByteArray()").build();
    }

    private CodeBlock decode(List<FieldModel> fields, Predicate<Direction> direction) {
        CodeBlock.Builder code = CodeBlock.builder()
            .addStatement("$T reader = new $T(data, ctx)", BOOK_READER, BOOK_READER);
        for (FieldModel field : fields) {
            if (direction.test(field.direction())) {
                code.addStatement("target.$N($L)", field.setter(), read(field));
            }
        }
        return code.build();
    }

    private CodeBlock write(FieldModel field) {
        Names n = names.get(field);
        CodeBlock value = CodeBlock.of("value.$N()", field.getter());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("writer.writeText($N, $L, $L)", n.descriptor(), value,
                field.length());
            case INT, LONG -> writeNumber(field, CodeBlock.of("$T.valueOf($L)", DECIMAL, value));
            case INTEGER -> writeNumber(field, helperCall(Helper.DECIMAL_OF_INTEGER, value));
            case LONG_BOXED -> writeNumber(field, helperCall(Helper.DECIMAL_OF_LONG, value));
            case BIG_DECIMAL -> writeNumber(field, value);
            case LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME -> CodeBlock.of(
                "writer.writeTemporal($N, $L, $L, $N)", n.descriptor(), value, field.length(),
                n.formatter());
            case CONVERTED -> CodeBlock.of("writer.writeConverted($N, $L, $N, $L)",
                n.descriptor(), value, n.converter(), fieldContext(field));
        };
    }

    private CodeBlock writeNumber(FieldModel field, CodeBlock decimal) {
        return CodeBlock.of("writer.writeNumber($N, $L, $L)", names.get(field).descriptor(),
            decimal, numberAttributes(field));
    }

    private CodeBlock read(FieldModel field) {
        Names n = names.get(field);
        CodeBlock number = CodeBlock.of("reader.readNumber($N, $L)", n.descriptor(),
            numberAttributes(field));
        CodeBlock descriptor = CodeBlock.of("$N", n.descriptor());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("reader.readText($N, $L)", n.descriptor(), field.length());
            case INT -> helperCall(Helper.INT_VALUE, number, descriptor);
            case INTEGER -> helperCall(Helper.INTEGER_VALUE, number, descriptor);
            case LONG -> helperCall(Helper.LONG_VALUE, number, descriptor);
            case LONG_BOXED -> helperCall(Helper.LONG_BOXED_VALUE, number, descriptor);
            case BIG_DECIMAL -> number;
            case LOCAL_DATE -> readTemporal("readDate", field);
            case LOCAL_TIME -> readTemporal("readTime", field);
            case LOCAL_DATE_TIME -> readTemporal("readDateTime", field);
            case CONVERTED -> {
                CodeBlock converted = CodeBlock.of("reader.readConverted($N, $N, $L)",
                    n.descriptor(), n.converter(), fieldContext(field));
                yield field.type().getKind().isPrimitive()
                    ? helperCall(Helper.NOT_NULL, converted, descriptor) : converted;
            }
        };
    }

    private CodeBlock readTemporal(String method, FieldModel field) {
        Names n = names.get(field);
        return CodeBlock.of("reader.$N($N, $L, $N)", method, n.descriptor(), field.length(),
            n.formatter());
    }

    private static CodeBlock numberAttributes(FieldModel field) {
        return CodeBlock.of("$L, $L, $L, $T.$N", field.length(), field.scale(), field.signed(),
            USAGE, field.usage().name());
    }

    private static CodeBlock fieldContext(FieldModel field) {
        return CodeBlock.of("new $T($S, $L, $L, $L, $T.$N, $S, $L, ctx.charset())", FIELD_CONTEXT,
            field.name(), field.length(), field.scale(), field.signed(), USAGE,
            field.usage().name(), field.pattern(), field.byteLength());
    }

    private CodeBlock helperCall(Helper helper, CodeBlock... arguments) {
        helpers.add(helper);
        return CodeBlock.of("$N($L)", helperName(helper), CodeBlock.join(List.of(arguments), ", "));
    }

    private static String helperName(Helper helper) {
        return switch (helper) {
            case INT_VALUE -> "intValue";
            case INTEGER_VALUE -> "integerValue";
            case LONG_VALUE -> "longValue";
            case LONG_BOXED_VALUE -> "longBoxedValue";
            case DECIMAL_OF_INTEGER, DECIMAL_OF_LONG -> "decimal";
            case NOT_NULL -> "notNull";
        };
    }

    private static MethodSpec helper(Helper helper) {
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
                yield MethodSpec.methodBuilder(helperName(helper))
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC).addTypeVariable(j)
                    .returns(j).addParameter(j, "value").addParameter(String.class, "field")
                    .beginControlFlow("if (value == null)")
                    .addStatement("throw new $T(field + $S)", MAPPING_EXCEPTION,
                        ": converter returned null for a primitive field")
                    .endControlFlow()
                    .addStatement("return value").build();
            }
        };
    }

    /** Converts a read number to the Java type. A value that does not fit fails. */
    private static MethodSpec exact(Helper helper, TypeName returns, String method,
                                    String blank, String typeName) {
        return MethodSpec.methodBuilder(helperName(helper))
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
