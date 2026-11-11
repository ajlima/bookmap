package pt.dazz.bookmap.processor;

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
import com.palantir.javapoet.WildcardTypeName;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.FieldModel.JavaKind;
import pt.dazz.bookmap.processor.model.ItemModel;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.OperationModel;
import pt.dazz.bookmap.processor.model.SubTypesModel;

/**
 * Generates the {@code BookCodec} implementation of one operation class.
 *
 * <p>The generated codec uses the getters and setters of the class, {@code BookWriter}, and
 * {@code BookReader}. It does not use reflection. Each field descriptor is a
 * {@code private static final String} constant. Each date or time formatter is a
 * {@code private static final DateTimeFormatter}. Each converter class has one
 * {@code private final} instance, which all fields with that converter share.
 *
 * <p>Each item class of a collection has one private method that writes an item and one private
 * method that reads an item. The encode methods set the count field of each collection to the
 * list size. For an {@code OUTPUT} collection, the {@code decodeResponse} method sets a new list
 * with the existing items and the decoded items. It replaces the list of an
 * {@code INPUT_OUTPUT} collection. The
 * {@code decodeRequest} method always makes a new list. The fields after a collection use the
 * current position of the reader and the writer.
 */
final class CodecGenerator {

    /** The value of the {@code @Generated} annotation of each codec. */
    static final String GENERATOR = "pt.dazz.bookmap.processor.BookmapProcessor";

    private static final ClassName BOOK_CODEC = ClassName.get("pt.dazz.bookmap.spi", "BookCodec");
    private static final ClassName BOOK_WRITER = ClassName.get("pt.dazz.bookmap.codec",
        "BookWriter");
    private static final ClassName BOOK_READER = ClassName.get("pt.dazz.bookmap.codec",
        "BookReader");
    private static final ClassName FORMATS = ClassName.get("pt.dazz.bookmap.codec", "Formats");
    private static final ClassName CODEC_CONTEXT = ClassName.get("pt.dazz.bookmap.convert",
        "CodecContext");
    private static final ClassName FIELD_CONTEXT = ClassName.get("pt.dazz.bookmap.convert",
        "FieldContext");
    private static final ClassName USAGE = ClassName.get("pt.dazz.bookmap.annotation", "Usage");
    private static final ClassName MAPPING_EXCEPTION = ClassName.get("pt.dazz.bookmap.exception",
        "MappingException");
    private static final ClassName GENERATED = ClassName.get("javax.annotation.processing",
        "Generated");
    private static final ClassName DECIMAL = ClassName.get(BigDecimal.class);
    private static final TypeName BYTES = TypeName.get(byte[].class);

    /** The helper methods that a generated codec can contain. */
    private enum Helper {
        INT_VALUE, INTEGER_VALUE, LONG_VALUE, LONG_BOXED_VALUE, DECIMAL_OF_INTEGER,
        DECIMAL_OF_LONG, NOT_NULL, COLLECTION_SIZE
    }

    /** The names of the generated members of one field. */
    private record Names(String descriptor, String formatter, String converter) {
    }

    /** The names of the generated members and local variables of one collection. */
    private record CollectionNames(String descriptor, String list, String count) {
    }

    /** The names of the methods that write and read one item class. */
    private record ItemMethods(String write, String read) {
    }

    private final OperationModel model;
    private final ClassName type;
    private final NameAllocator allocator = new NameAllocator();
    private final Map<FieldModel, Names> names = new HashMap<>();
    private final Map<CollectionModel, CollectionNames> collections = new HashMap<>();
    /** The converter field of each converter class, by qualified class name. */
    private final Map<String, String> converters = new HashMap<>();
    /** The item methods of each item class, by qualified class name. */
    private final Map<String, ItemMethods> items = new HashMap<>();
    private final List<MethodSpec> itemMethods = new ArrayList<>();
    private final Set<Helper> helpers = EnumSet.noneOf(Helper.class);
    /** The layouts of the codec: one without subtypes, else one for each class. */
    private final List<Layout> layouts;

    /**
     * One layout of the codec.
     *
     * @param type the class of the layout
     * @param value the normalized discriminator value, or null for a base class that is not
     *     in the list
     * @param members the members, sorted by order
     */
    private record Layout(TypeElement type, String value, List<MemberModel> members) {
    }

    /**
     * Gives the layouts. Without subtypes, the one layout is the operation class. With
     * subtypes, a base class that is not abstract and not in the list comes first, then the
     * listed classes in the order of the annotation.
     */
    private static List<Layout> layouts(OperationModel model) {
        SubTypesModel subTypes = model.subTypes();
        List<Layout> result = new ArrayList<>();
        if (subTypes == null) {
            result.add(new Layout(model.type(), null, model.members()));
            return result;
        }
        boolean baseListed = subTypes.variants().stream()
            .anyMatch(variant -> variant.type().equals(model.type()));
        if (!subTypes.baseAbstract() && !baseListed) {
            result.add(new Layout(model.type(), null, model.members()));
        }
        subTypes.variants().forEach(variant ->
            result.add(new Layout(variant.type(), variant.value(), variant.members())));
        return result;
    }

    private CodecGenerator(OperationModel model) {
        this.model = model;
        this.type = ClassName.get(model.type());
        this.layouts = layouts(model);
        // The local variables of the generated methods.
        List.of("value", "target", "data", "ctx", "writer", "reader", "item", "i")
            .forEach(allocator::newName);
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
        Set<MemberModel> added = new HashSet<>();
        for (Layout layout : layouts) {
            for (MemberModel member : layout.members()) {
                if (added.add(member)) {
                    switch (member) {
                        case FieldModel field -> addFieldMembers(builder, field);
                        case CollectionModel collection -> addCollectionMembers(builder, collection);
                    }
                }
            }
        }
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
            .addStatement("return $L", layouts.stream()
                .mapToInt(layout -> layout.members().stream()
                    .filter(member -> member.direction().isOutput())
                    .mapToInt(CodecGenerator::maxByteLength).sum())
                .max().orElseThrow()).build());
        MethodSpec.Builder newInstance = override("newInstance").returns(type);
        if (model.type().getModifiers().contains(Modifier.ABSTRACT)) {
            newInstance.addStatement("throw new $T($S)", MAPPING_EXCEPTION,
                type.simpleName() + ": the base class is abstract");
        } else {
            newInstance.addStatement("return new $T()", type);
        }
        builder.addMethod(newInstance.build());
        if (model.subTypes() == null) {
            List<MemberModel> members = model.members();
            builder.addMethod(encode(override("encodeRequest"), type, members,
                Direction::isInput));
            builder.addMethod(override("decodeResponse")
                .addParameter(BYTES, "data").addParameter(type, "target")
                .addParameter(CODEC_CONTEXT, "ctx")
                .addCode(decode(members, Direction::isOutput, true)).build());
            builder.addMethod(override("decodeRequest").returns(type)
                .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T target = newInstance()", type)
                .addCode(decode(members, Direction::isInput, false))
                .addStatement("return target").build());
            builder.addMethod(encode(override("encodeResponse"), type, members,
                Direction::isOutput));
            builder.addMethod(resetOutputCollections(override("resetOutputCollections"), type,
                members));
        } else {
            new SubTypesGenerator().addMethods(builder);
        }
        builder.addMethods(itemMethods);
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
            case CONVERTED -> converter = converters.computeIfAbsent(
                field.converter().getQualifiedName().toString(),
                key -> addConverter(builder, field.converter()));
            default -> {
                // Text and numbers need no other member.
            }
        }
        names.put(field, new Names(descriptor, formatter, converter));
    }

    /** Adds the one instance of a converter class. */
    private String addConverter(TypeSpec.Builder builder, TypeElement converter) {
        String simpleName = converter.getSimpleName().toString();
        String name = allocator.newName(
            Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1));
        ClassName converterType = ClassName.get(converter);
        builder.addField(FieldSpec.builder(converterType, name, Modifier.PRIVATE,
            Modifier.FINAL).initializer("new $T()", converterType).build());
        return name;
    }

    private void addCollectionMembers(TypeSpec.Builder builder, CollectionModel collection) {
        String descriptor = allocator.newName(upperSnake(collection.name()));
        builder.addField(FieldSpec.builder(String.class, descriptor, Modifier.PRIVATE,
            Modifier.STATIC, Modifier.FINAL).initializer("$S", collection.descriptor()).build());
        collections.put(collection, new CollectionNames(descriptor,
            allocator.newName(collection.name()), allocator.newName(collection.name() + "Count")));
        items.computeIfAbsent(collection.item().type().getQualifiedName().toString(),
            key -> addItemMembers(builder, collection.item()));
    }

    /** Adds the members of an item class and makes its write and read methods. */
    private ItemMethods addItemMembers(TypeSpec.Builder builder, ItemModel item) {
        item.fields().forEach(field -> addFieldMembers(builder, field));
        ClassName itemType = ClassName.get(item.type());
        String suffix = String.join("_", itemType.simpleNames());
        ItemMethods methods = new ItemMethods(allocator.newName("writeItem_" + suffix),
            allocator.newName("readItem_" + suffix));
        MethodSpec.Builder write = MethodSpec.methodBuilder(methods.write())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_WRITER, "writer")
            .addParameter(itemType, "value").addParameter(CODEC_CONTEXT, "ctx");
        MethodSpec.Builder read = MethodSpec.methodBuilder(methods.read())
            .addModifiers(Modifier.PRIVATE).returns(itemType)
            .addParameter(BOOK_READER, "reader").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T target = new $T()", itemType, itemType);
        for (FieldModel field : item.fields()) {
            write.addStatement(write(field));
            read.addStatement("target.$N($L)", field.setter(), read(field));
        }
        itemMethods.add(write.build());
        itemMethods.add(read.addStatement("return target").build());
        return methods;
    }

    /** Gives the maximum number of bytes of a member in the book. */
    private static int maxByteLength(MemberModel member) {
        return switch (member) {
            case FieldModel field -> field.byteLength();
            case CollectionModel collection ->
                collection.maxOccurs() * collection.item().byteLength();
        };
    }

    private static TypeName listOf(CollectionModel collection) {
        return ParameterizedTypeName.get(ClassName.get(List.class),
            ClassName.get(collection.item().type()));
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

    /**
     * Makes an encode method. First, the method sets the count field of each collection to the
     * list size. Then it writes the members in order.
     */
    private MethodSpec encode(MethodSpec.Builder method, TypeName valueType,
                              List<MemberModel> layout, Predicate<Direction> direction) {
        List<MemberModel> members = layout.stream()
            .filter(member -> direction.test(member.direction())).toList();
        method.returns(BYTES).addParameter(valueType, "value").addParameter(CODEC_CONTEXT, "ctx");
        for (MemberModel member : members) {
            if (member instanceof CollectionModel collection) {
                method.addCode(setCount(collection));
            }
        }
        method.addStatement("$T writer = new $T(ctx)", BOOK_WRITER, BOOK_WRITER);
        for (MemberModel member : members) {
            switch (member) {
                case FieldModel field -> method.addStatement(write(field));
                case CollectionModel collection -> method.addCode(writeItems(collection));
            }
        }
        return method.addStatement("return writer.toByteArray()").build();
    }

    private CodeBlock setCount(CollectionModel collection) {
        CollectionNames n = collections.get(collection);
        FieldModel count = collection.countField();
        return CodeBlock.builder()
            .addStatement("$T $N = value.$N()", listOf(collection), n.list(), collection.getter())
            .addStatement("int $N = $N == null ? 0 : $N.size()", n.count(), n.list(), n.list())
            .beginControlFlow("if ($N > $L)", n.count(), collection.maxOccurs())
            .addStatement("throw new $T($N + $S + $N + $S)", MAPPING_EXCEPTION, n.descriptor(),
                ": the list has ", n.count(),
                " items, more than maxOccurs " + collection.maxOccurs())
            .endControlFlow()
            // A Long setter needs a long value. The other count types accept an int.
            .addStatement("value.$N($L$N)", count.setter(),
                count.kind() == JavaKind.LONG_BOXED ? "(long) " : "", n.count())
            .build();
    }

    private CodeBlock writeItems(CollectionModel collection) {
        CollectionNames n = collections.get(collection);
        return CodeBlock.builder()
            .beginControlFlow("if ($N != null)", n.list())
            .beginControlFlow("for ($T item : $N)", ClassName.get(collection.item().type()),
                n.list())
            .beginControlFlow("if (item == null)")
            .addStatement("throw new $T($N + $S)", MAPPING_EXCEPTION, n.descriptor(),
                ": the list contains a null item")
            .endControlFlow()
            .addStatement("$N(writer, item, ctx)", itemMethodsOf(collection).write())
            .endControlFlow()
            .endControlFlow()
            .build();
    }

    /**
     * Makes the body of a decode method.
     *
     * @param direction the members to read
     * @param response {@code true} for {@code decodeResponse}, which adds the items of an
     *     {@code OUTPUT} collection to a new list with the existing items
     */
    private CodeBlock decode(List<MemberModel> layout, Predicate<Direction> direction,
                             boolean response) {
        CodeBlock.Builder code = CodeBlock.builder()
            .addStatement("$T reader = new $T(data, ctx)", BOOK_READER, BOOK_READER);
        for (MemberModel member : layout) {
            if (direction.test(member.direction())) {
                switch (member) {
                    case FieldModel field ->
                        code.addStatement("target.$N($L)", field.setter(), read(field));
                    case CollectionModel collection -> code.add(readItems(collection,
                        response && collection.direction() == Direction.OUTPUT));
                }
            }
        }
        return code.build();
    }

    private CodeBlock readItems(CollectionModel collection, boolean append) {
        CollectionNames n = collections.get(collection);
        CodeBlock.Builder code = CodeBlock.builder().addStatement("int $N = $L", n.count(),
            helperCall(Helper.COLLECTION_SIZE,
                CodeBlock.of("target.$N()", collection.countField().getter()),
                CodeBlock.of("$N", n.descriptor()), CodeBlock.of("$L", collection.maxOccurs())));
        if (append) {
            // A new list: the getter can return a copy or an unmodifiable list.
            code.addStatement("$T $N = new $T<>($T.requireNonNullElse(target.$N(), $T.of()))",
                listOf(collection), n.list(), ArrayList.class, Objects.class,
                collection.getter(), List.class);
        } else {
            code.addStatement("$T $N = new $T<>($N)", listOf(collection), n.list(),
                ArrayList.class, n.count());
        }
        return code.beginControlFlow("for (int i = 0; i < $N; i++)", n.count())
            .addStatement("$N.add($N(reader, ctx))", n.list(), itemMethodsOf(collection).read())
            .endControlFlow()
            .addStatement("target.$N($N)", collection.setter(), n.list())
            .build();
    }

    private ItemMethods itemMethodsOf(CollectionModel collection) {
        return items.get(collection.item().type().getQualifiedName().toString());
    }

    private MethodSpec resetOutputCollections(MethodSpec.Builder method, TypeName valueType,
                                              List<MemberModel> layout) {
        method.addParameter(valueType, "value");
        for (MemberModel member : layout) {
            if (member instanceof CollectionModel collection
                && collection.direction() == Direction.OUTPUT) {
                method.addStatement("value.$N(new $T<>())", collection.setter(), ArrayList.class);
            }
        }
        return method.build();
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
            case COLLECTION_SIZE -> "collectionSize";
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
            case COLLECTION_SIZE -> MethodSpec.methodBuilder(helperName(helper))
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

    /**
     * Generates the methods of a codec with subtypes. Each layout gets private methods. The
     * public methods select the layout by the exact class of the object or by the value of the
     * discriminator.
     */
    private final class SubTypesGenerator {

        private final SubTypesModel subTypes = model.subTypes();
        private final FieldModel discriminator = subTypes.discriminator();
        private final boolean numeric = discriminator.kind() != JavaKind.STRING;
        private final TypeName keyType = numeric ? DECIMAL : ClassName.get(String.class);
        private final TypeName subtypeClass = ParameterizedTypeName.get(
            ClassName.get(Class.class), WildcardTypeName.subtypeOf(type));
        private final String prefix = type.simpleName() + ": ";
        private final Map<Layout, LayoutMethods> methods = new HashMap<>();
        private final String typeOf = allocator.newName("typeOf");
        private final String unknownValue = allocator.newName("unknownValue");
        private final String unknownSubclass = allocator.newName("unknownSubclass");
        private final String selectResponse = allocator.newName("selectResponse");
        private final String decodeInto = allocator.newName("decodeInto");
        private final String newSubtype = allocator.newName("newSubtype");

        /** The names of the private methods of one layout. */
        private record LayoutMethods(String encodeRequest, String encodeResponse,
                                     String decodeResponse, String decodeRequest, String reset) {
        }

        void addMethods(TypeSpec.Builder builder) {
            for (Layout layout : layouts) {
                String suffix = String.join("_", ClassName.get(layout.type()).simpleNames());
                LayoutMethods names = new LayoutMethods(
                    allocator.newName("encodeRequest_" + suffix),
                    allocator.newName("encodeResponse_" + suffix),
                    allocator.newName("decodeResponse_" + suffix),
                    allocator.newName("decodeRequest_" + suffix),
                    allocator.newName("resetOutputCollections_" + suffix));
                methods.put(layout, names);
                TypeName layoutType = ClassName.get(layout.type());
                builder.addMethod(encode(privateMethod(names.encodeRequest()), layoutType,
                    layout.members(), Direction::isInput));
                builder.addMethod(encode(privateMethod(names.encodeResponse()), layoutType,
                    layout.members(), Direction::isOutput));
                builder.addMethod(privateMethod(names.decodeResponse())
                    .addParameter(BYTES, "data").addParameter(layoutType, "target")
                    .addParameter(CODEC_CONTEXT, "ctx")
                    .addCode(decode(layout.members(), Direction::isOutput, true)).build());
                builder.addMethod(privateMethod(names.decodeRequest()).returns(layoutType)
                    .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
                    .addStatement("$T target = new $T()", layoutType, layoutType)
                    .addCode(decode(layout.members(), Direction::isInput, false))
                    .addStatement("return target").build());
                builder.addMethod(resetOutputCollections(privateMethod(names.reset()),
                    layoutType, layout.members()));
            }
            builder.addMethod(types());
            builder.addMethod(encodeDispatch("encodeRequest", Direction::isInput, true));
            builder.addMethod(encodeDispatch("encodeResponse", Direction::isOutput, false));
            builder.addMethod(readResponse());
            builder.addMethod(decodeResponse());
            builder.addMethod(decodeRequest());
            builder.addMethod(resetDispatch());
            builder.addMethod(selectResponse());
            builder.addMethod(decodeInto());
            builder.addMethod(newSubtype());
            builder.addMethod(typeOf());
            builder.addMethod(unknownValue());
            builder.addMethod(unknownSubclass());
        }

        private MethodSpec.Builder privateMethod(String name) {
            return MethodSpec.methodBuilder(name).addModifiers(Modifier.PRIVATE);
        }

        private TypeName typeOf(Layout layout) {
            return ClassName.get(layout.type());
        }

        private boolean isBase(Layout layout) {
            return layout.type().equals(model.type());
        }

        /** Gives a variable, with a cast to the layout class if it is not the base class. */
        private CodeBlock cast(Layout layout, String variable) {
            return isBase(layout) ? CodeBlock.of("$N", variable)
                : CodeBlock.of("($T) $N", typeOf(layout), variable);
        }

        private MethodSpec types() {
            return override("types")
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), subtypeClass))
                .addStatement("return $T.of($L)", List.class, CodeBlock.join(layouts.stream()
                    .map(layout -> CodeBlock.of("$T.class", typeOf(layout))).toList(), ", "))
                .build();
        }

        /**
         * Makes a public encode method. It selects the layout by the exact class. For a listed
         * class, it first sets the discriminator, if the discriminator is in the direction.
         */
        private MethodSpec encodeDispatch(String name, Predicate<Direction> direction,
                                          boolean request) {
            MethodSpec.Builder method = override(name).returns(BYTES)
                .addParameter(type, "value").addParameter(CODEC_CONTEXT, "ctx");
            for (Layout layout : layouts) {
                LayoutMethods names = methods.get(layout);
                method.beginControlFlow("if (value.getClass() == $T.class)", typeOf(layout));
                if (layout.value() != null && direction.test(discriminator.direction())) {
                    method.addStatement("$L.$N($L)", isBase(layout) ? CodeBlock.of("value")
                            : CodeBlock.of("(($T) value)", typeOf(layout)),
                        discriminator.setter(), literal(layout.value()));
                }
                method.addStatement("return $N($L, ctx)",
                    request ? names.encodeRequest() : names.encodeResponse(),
                    cast(layout, "value"))
                    .endControlFlow();
            }
            return method.addStatement("throw $N(value)", unknownSubclass).build();
        }

        private CodeBlock literal(String value) {
            return switch (discriminator.kind()) {
                case STRING -> CodeBlock.of("$S", value);
                case LONG, LONG_BOXED -> CodeBlock.of("$LL", value);
                default -> CodeBlock.of("$L", value);
            };
        }

        /** Reads the discriminator at its fixed offset in one direction. */
        private CodeBlock readKey(Predicate<Direction> direction) {
            int offset = model.members().stream()
                .filter(member -> member.order() < discriminator.order())
                .filter(member -> direction.test(member.direction()))
                .mapToInt(CodecGenerator::maxByteLength).sum();
            String descriptor = names.get(discriminator).descriptor();
            CodeBlock reader = CodeBlock.of("new $T(data, ctx).peek($L)", BOOK_READER, offset);
            return numeric
                ? CodeBlock.of("$L.readNumber($N, $L)", reader, descriptor,
                    numberAttributes(discriminator))
                : CodeBlock.of("$L.readText($N, $L)", reader, descriptor,
                    discriminator.length());
        }

        private MethodSpec readResponse() {
            MethodSpec.Builder method = override("readResponse").returns(type)
                .addParameter(BYTES, "data").addParameter(type, "target")
                .addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T selected = $N(data, ctx)", subtypeClass, selectResponse)
                .beginControlFlow("if (selected == target.getClass())")
                .addStatement("$N(data, target, ctx)", decodeInto)
                .addStatement("return target")
                .endControlFlow();
            if (!subTypes.baseAbstract()) {
                method.beginControlFlow("if (target.getClass() == $T.class)", type)
                    .addStatement("$T copy = $N(selected)", type, newSubtype);
                for (MemberModel member : model.members()) {
                    method.addStatement("copy.$N(target.$N())", setter(member), getter(member));
                }
                method.addStatement("$N(data, copy, ctx)", decodeInto)
                    .addStatement("return copy")
                    .endControlFlow();
            }
            return method.addStatement("throw new $T($S + selected.getSimpleName() + $S"
                    + " + target.getClass().getSimpleName())", MAPPING_EXCEPTION,
                prefix + "the response has type ", ", which is not a ").build();
        }

        private static String setter(MemberModel member) {
            return switch (member) {
                case FieldModel field -> field.setter();
                case CollectionModel collection -> collection.setter();
            };
        }

        private static String getter(MemberModel member) {
            return switch (member) {
                case FieldModel field -> field.getter();
                case CollectionModel collection -> collection.getter();
            };
        }

        private MethodSpec decodeResponse() {
            return override("decodeResponse")
                .addParameter(BYTES, "data").addParameter(type, "target")
                .addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T selected = $N(data, ctx)", subtypeClass, selectResponse)
                .beginControlFlow("if (selected != target.getClass())")
                .addStatement("throw new $T($S + selected.getSimpleName() + $S"
                        + " + target.getClass().getSimpleName() + $S)", MAPPING_EXCEPTION,
                    prefix + "the response has type ", ", but the object has type ",
                    ". Only the first response can change the type")
                .endControlFlow()
                .addStatement("$N(data, target, ctx)", decodeInto).build();
        }

        private MethodSpec decodeRequest() {
            MethodSpec.Builder method = override("decodeRequest").returns(type)
                .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx");
            if (!discriminator.direction().isInput()) {
                // The request has no discriminator. The base class is not abstract.
                Layout base = layouts.stream().filter(this::isBase).findFirst().orElseThrow();
                return method.addStatement("return $N(data, ctx)",
                    methods.get(base).decodeRequest()).build();
            }
            method.addStatement("$T key = $L", keyType, readKey(Direction::isInput))
                .addStatement("$T selected = $N(key)", subtypeClass, typeOf)
                .beginControlFlow("if (selected == null)");
            if (subTypes.baseAbstract()) {
                method.addStatement("throw $N(key)", unknownValue);
            } else {
                method.addStatement("selected = $T.class", type);
            }
            method.endControlFlow();
            for (Layout layout : layouts) {
                method.beginControlFlow("if (selected == $T.class)", typeOf(layout))
                    .addStatement("return $N(data, ctx)", methods.get(layout).decodeRequest())
                    .endControlFlow();
            }
            return method.addStatement("throw new $T($S + selected.getName())",
                MAPPING_EXCEPTION, prefix + "unknown subclass ").build();
        }

        private MethodSpec resetDispatch() {
            MethodSpec.Builder method = override("resetOutputCollections")
                .addParameter(type, "value");
            for (Layout layout : layouts) {
                method.beginControlFlow("if (value.getClass() == $T.class)", typeOf(layout))
                    .addStatement("$N($L)", methods.get(layout).reset(), cast(layout, "value"))
                    .addStatement("return")
                    .endControlFlow();
            }
            return method.addStatement("throw $N(value)", unknownSubclass).build();
        }

        /** Reads the discriminator of a response and gives the class. */
        private MethodSpec selectResponse() {
            return privateMethod(selectResponse).addModifiers(Modifier.STATIC)
                .returns(subtypeClass)
                .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T key = $L", keyType, readKey(Direction::isOutput))
                .addStatement("$T selected = $N(key)", subtypeClass, typeOf)
                .beginControlFlow("if (selected == null)")
                .addStatement("throw $N(key)", unknownValue)
                .endControlFlow()
                .addStatement("return selected").build();
        }

        /** Decodes a response into an object of a known class. */
        private MethodSpec decodeInto() {
            MethodSpec.Builder method = privateMethod(decodeInto)
                .addParameter(BYTES, "data").addParameter(type, "target")
                .addParameter(CODEC_CONTEXT, "ctx");
            for (Layout layout : layouts) {
                method.beginControlFlow("if (target.getClass() == $T.class)", typeOf(layout))
                    .addStatement("$N(data, $L, ctx)", methods.get(layout).decodeResponse(),
                        cast(layout, "target"))
                    .addStatement("return")
                    .endControlFlow();
            }
            return method.addStatement("throw $N(target)", unknownSubclass).build();
        }

        /** Creates an instance of a listed subtype. */
        private MethodSpec newSubtype() {
            MethodSpec.Builder method = privateMethod(newSubtype).addModifiers(Modifier.STATIC)
                .returns(type).addParameter(subtypeClass, "selected");
            for (Layout layout : layouts) {
                if (!isBase(layout)) {
                    method.beginControlFlow("if (selected == $T.class)", typeOf(layout))
                        .addStatement("return new $T()", typeOf(layout))
                        .endControlFlow();
                }
            }
            return method.addStatement("throw new $T($S + selected.getName())",
                MAPPING_EXCEPTION, prefix + "unknown subclass ").build();
        }

        /** Gives the class of a discriminator value, or null for an unknown value. */
        private MethodSpec typeOf() {
            MethodSpec.Builder method = privateMethod(typeOf).addModifiers(Modifier.STATIC)
                .returns(subtypeClass).addParameter(keyType, "key");
            for (Layout layout : layouts) {
                if (layout.value() != null) {
                    if (numeric) {
                        method.beginControlFlow("if (key != null && key.compareTo($T.valueOf($LL))"
                            + " == 0)", DECIMAL, layout.value());
                    } else {
                        method.beginControlFlow("if (key.equals($S))", layout.value());
                    }
                    method.addStatement("return $T.class", typeOf(layout)).endControlFlow();
                }
            }
            return method.addStatement("return null").build();
        }

        private MethodSpec unknownValue() {
            String known = layouts.stream().map(Layout::value).filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
            CodeBlock key = numeric
                ? CodeBlock.of("(key == null ? $S : key.toPlainString())", "(blank)")
                : CodeBlock.of("key");
            return privateMethod(unknownValue).addModifiers(Modifier.STATIC)
                .returns(MAPPING_EXCEPTION).addParameter(keyType, "key")
                .addStatement("return new $T($N + $S + $L + $S)", MAPPING_EXCEPTION,
                    names.get(discriminator).descriptor(), ": unknown discriminator value '", key,
                    "'. Known values: " + known)
                .build();
        }

        private MethodSpec unknownSubclass() {
            return privateMethod(unknownSubclass).addModifiers(Modifier.STATIC)
                .returns(MAPPING_EXCEPTION).addParameter(Object.class, "value")
                .addStatement("return new $T($S + value.getClass().getName())",
                    MAPPING_EXCEPTION, prefix + "unknown subclass ")
                .build();
        }
    }
}
