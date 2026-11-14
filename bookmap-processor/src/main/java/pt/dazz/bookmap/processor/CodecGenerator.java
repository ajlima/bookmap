package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.BOOK_CODEC;
import static pt.dazz.bookmap.processor.CodecTypes.BOOK_READER;
import static pt.dazz.bookmap.processor.CodecTypes.BOOK_WRITER;
import static pt.dazz.bookmap.processor.CodecTypes.BYTES;
import static pt.dazz.bookmap.processor.CodecTypes.CODEC_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.DECIMAL;
import static pt.dazz.bookmap.processor.CodecTypes.FIELD_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.GENERATED;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;
import static pt.dazz.bookmap.processor.CodecTypes.USAGE;
import static pt.dazz.bookmap.processor.CodecTypes.numberAttributes;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.processor.CodecMembers.CollectionNames;
import pt.dazz.bookmap.processor.CodecMembers.ItemMethods;
import pt.dazz.bookmap.processor.CodecMembers.Names;
import pt.dazz.bookmap.processor.HelperMethods.Helper;
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

    private final OperationModel model;
    private final ClassName type;
    private final List<MethodSpec> itemMethods = new ArrayList<>();
    private final HelperMethods helpers = new HelperMethods();
    private final CodecMembers members = new CodecMembers();
    /** The layouts of the codec: one without subtypes, else one for each class. */
    private final List<Layout> layouts;

    private CodecGenerator(OperationModel model) {
        this.model = model;
        this.type = ClassName.get(model.type());
        this.layouts = Layout.of(model.type(), model.members(), model.subTypes());
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
                        case FieldModel field -> members.addField(builder, field);
                        case CollectionModel collection ->
                            addCollectionMembers(builder, collection, false);
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
                    .mapToInt(MemberModel::areaLength).sum())
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
            List<MemberModel> layout = model.members();
            builder.addMethod(encode(override("encodeRequest"), type, layout,
                Direction::isInput));
            builder.addMethod(override("decodeResponse")
                .addParameter(BYTES, "data").addParameter(type, "target")
                .addParameter(CODEC_CONTEXT, "ctx")
                .addCode(decode(layout, Direction::isOutput, true)).build());
            builder.addMethod(override("decodeRequest").returns(type)
                .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T target = newInstance()", type)
                .addCode(decode(layout, Direction::isInput, false))
                .addStatement("return target").build());
            builder.addMethod(encode(override("encodeResponse"), type, layout,
                Direction::isOutput));
            builder.addMethod(resetOutputCollections(override("resetOutputCollections"), type,
                layout));
        } else {
            new SubTypesGenerator().addMethods(builder);
        }
        builder.addMethods(itemMethods);
        builder.addMethods(members.enumMethods());
        builder.addMethods(helpers.methods());
        return JavaFile.builder(codec.packageName(), builder.build()).build();
    }

    private static MethodSpec.Builder override(String name) {
        return MethodSpec.methodBuilder(name).addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC);
    }

    /**
     * Adds the members of a collection and of its item class.
     *
     * @param inner {@code true} for a collection in an item class
     */
    private void addCollectionMembers(TypeSpec.Builder builder, CollectionModel collection,
                                      boolean inner) {
        members.addCollection(builder, collection, inner);
        ItemModel item = collection.item();
        if (members.itemMethods(item) == null) {
            // Not computeIfAbsent: the item members can add the items of inner collections.
            members.putItemMethods(item, addItemMembers(builder, item));
        }
    }

    /**
     * Gives a statement on one item. In an indexed collection, a {@code MappingException} of
     * the statement gets the descriptor of the collection and the index {@code i} of the item.
     */
    private CodeBlock onItem(CollectionNames n, CodeBlock statement) {
        if (!n.indexed()) {
            return CodeBlock.builder().addStatement(statement).build();
        }
        return CodeBlock.builder()
            .beginControlFlow("try")
            .addStatement(statement)
            .nextControlFlow("catch ($T e)", MAPPING_EXCEPTION)
            .addStatement("throw $L", helpers.call(Helper.AT_ITEM,
                CodeBlock.of("$N", n.descriptor()), CodeBlock.of("i"), CodeBlock.of("e")))
            .endControlFlow()
            .build();
    }

    /** Adds the members of an item class and makes its write and read methods. */
    private ItemMethods addItemMembers(TypeSpec.Builder builder, ItemModel item) {
        List<Layout> itemLayouts = Layout.of(item.type(), item.members(), item.subTypes());
        for (Layout layout : itemLayouts) {
            for (MemberModel member : layout.members()) {
                switch (member) {
                    case FieldModel field when !members.hasNames(field) ->
                        members.addField(builder, field);
                    case CollectionModel inner when !members.hasNames(inner) ->
                        addCollectionMembers(builder, inner, true);
                    default -> {
                        // The members of a base class are already in another layout.
                    }
                }
            }
        }
        ClassName itemType = ClassName.get(item.type());
        String suffix = String.join("_", itemType.simpleNames());
        ItemMethods methods = new ItemMethods(members.newName("writeItem_" + suffix),
            members.newName("readItem_" + suffix));
        if (item.subTypes() == null) {
            addItemLayoutMethods(methods, item.type(), item.members());
            return methods;
        }
        Map<Layout, ItemMethods> layoutMethods = new HashMap<>();
        for (Layout layout : itemLayouts) {
            String layoutSuffix = String.join("_", ClassName.get(layout.type()).simpleNames());
            ItemMethods names = new ItemMethods(members.newName("writeItem_" + layoutSuffix),
                members.newName("readItem_" + layoutSuffix));
            addItemLayoutMethods(names, layout.type(), layout.members());
            layoutMethods.put(layout, names);
        }
        FieldModel discriminator = item.subTypes().discriminator();
        Discriminator key = new Discriminator(members, discriminator, itemLayouts, itemType);
        itemMethods.add(key.typeOfMethod());
        itemMethods.add(key.unknownValueMethod());
        int offset = item.members().stream()
            .filter(member -> member.order() < discriminator.order())
            .mapToInt(MemberModel::areaLength).sum();
        String prefix = itemType.simpleName() + ": ";
        // Write: select the layout by the exact class, then fill to the longest layout.
        MethodSpec.Builder write = MethodSpec.methodBuilder(methods.write())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_WRITER, "writer")
            .addParameter(itemType, "value").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("int start = writer.position()");
        String flow = "if";
        for (Layout layout : itemLayouts) {
            TypeName layoutType = ClassName.get(layout.type());
            write.addCode(flow.equals("if") ? CodeBlock.builder()
                    .beginControlFlow("if (value.getClass() == $T.class)", layoutType).build()
                : CodeBlock.builder()
                    .nextControlFlow("else if (value.getClass() == $T.class)", layoutType)
                    .build());
            flow = "else";
            if (layout.value() != null) {
                write.addStatement("value.$N($L)", discriminator.setter(),
                    key.literal(layout.value()));
            }
            write.addStatement("$N(writer, $L, ctx)", layoutMethods.get(layout).write(),
                layout.type().equals(item.type()) ? CodeBlock.of("value")
                    : CodeBlock.of("($T) value", layoutType));
        }
        itemMethods.add(write.nextControlFlow("else")
            .addStatement("throw new $T($S + value.getClass().getName())", MAPPING_EXCEPTION,
                prefix + "unknown subclass ")
            .endControlFlow()
            .addStatement("writer.writeFiller($L - (writer.position() - start))",
                item.byteLength())
            .build());
        // Read: read the discriminator, select the layout, then skip to the next entry.
        MethodSpec.Builder read = MethodSpec.methodBuilder(methods.read())
            .addModifiers(Modifier.PRIVATE).returns(itemType)
            .addParameter(BOOK_READER, "reader").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("int start = reader.position()")
            .addStatement("$T key = $L", key.keyType(),
                key.read(CodeBlock.of("reader.peek($L)", offset)))
            .addStatement("$T selected = $N(key)", key.subtypeClass(), key.typeOf())
            .beginControlFlow("if (selected == null)")
            .addStatement("throw $N(key)", key.unknownValue())
            .endControlFlow()
            .addStatement("$T item", itemType);
        flow = "if";
        for (Layout layout : itemLayouts) {
            if (layout.value() == null) {
                // A base class that is not in the list is never read.
                continue;
            }
            TypeName layoutType = ClassName.get(layout.type());
            read.addCode(flow.equals("if") ? CodeBlock.builder()
                    .beginControlFlow("if (selected == $T.class)", layoutType).build()
                : CodeBlock.builder()
                    .nextControlFlow("else if (selected == $T.class)", layoutType).build());
            flow = "else";
            read.addStatement("item = $N(reader, ctx)", layoutMethods.get(layout).read());
        }
        itemMethods.add(read.nextControlFlow("else")
            .addStatement("throw new $T($S + selected.getName())", MAPPING_EXCEPTION,
                prefix + "unknown subclass ")
            .endControlFlow()
            .addStatement("reader.skip($L - (reader.position() - start))", item.byteLength())
            .addStatement("return item")
            .build());
        return methods;
    }

    /** Adds the methods that write and read one layout of an item class. */
    private void addItemLayoutMethods(ItemMethods methods, TypeElement layoutClass,
                                      List<MemberModel> members) {
        ClassName itemType = ClassName.get(layoutClass);
        MethodSpec.Builder write = MethodSpec.methodBuilder(methods.write())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_WRITER, "writer")
            .addParameter(itemType, "value").addParameter(CODEC_CONTEXT, "ctx");
        MethodSpec.Builder read = MethodSpec.methodBuilder(methods.read())
            .addModifiers(Modifier.PRIVATE).returns(itemType)
            .addParameter(BOOK_READER, "reader").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T target = new $T()", itemType, itemType);
        for (MemberModel member : members) {
            if (member instanceof CollectionModel inner) {
                write.addCode(setCount(inner));
            }
        }
        for (MemberModel member : members) {
            switch (member) {
                case FieldModel field -> {
                    write.addStatement(write(field));
                    read.addStatement("target.$N($L)", field.setter(), read(field));
                }
                case CollectionModel inner -> {
                    write.addCode(writeItems(inner));
                    read.addCode(readItems(inner, false));
                }
            }
        }
        itemMethods.add(write.build());
        itemMethods.add(read.addStatement("return target").build());
    }

    private static TypeName listOf(CollectionModel collection) {
        return ParameterizedTypeName.get(ClassName.get(List.class),
            ClassName.get(collection.item().type()));
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
        CollectionNames n = members.names(collection);
        FieldModel count = collection.countField();
        CodeBlock.Builder code = CodeBlock.builder()
            .addStatement("$T $N = value.$N()", listOf(collection), n.list(), collection.getter())
            .addStatement("int $N = $N == null ? 0 : $N.size()", n.count(), n.list(), n.list())
            .beginControlFlow("if ($N > $L)", n.count(), collection.maxOccurs())
            .addStatement("throw new $T($N + $S + $N + $S)", MAPPING_EXCEPTION, n.descriptor(),
                ": the list has ", n.count(),
                " items, more than maxOccurs " + collection.maxOccurs())
            .endControlFlow();
        if (count != null) {
            // A Long setter needs a long value. The other count types accept an int.
            code.addStatement("value.$N($L$N)", count.setter(),
                count.kind() == JavaKind.LONG_BOXED ? "(long) " : "", n.count());
        }
        return code.build();
    }

    private CodeBlock writeItems(CollectionModel collection) {
        if (collection.fixed()) {
            return writeFixedItems(collection);
        }
        CollectionNames n = members.names(collection);
        if (n.indexed()) {
            // The count is 0 for a null list.
            return CodeBlock.builder()
                .beginControlFlow("for (int i = 0; i < $N; i++)", n.count())
                .add(nullItemCheck(collection, n))
                .add(onItem(n, CodeBlock.of("$N(writer, item, ctx)",
                    itemMethodsOf(collection).write())))
                .endControlFlow()
                .build();
        }
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
     * Writes the items of a fixed collection, then LOW-VALUES in the unused slots. Without a
     * count field, an item that is all LOW-VALUES is an error: a read cannot find it.
     */
    private CodeBlock writeFixedItems(CollectionModel collection) {
        CollectionNames n = members.names(collection);
        CodeBlock writeItem = CodeBlock.of("$N(writer, item, ctx)",
            itemMethodsOf(collection).write());
        CodeBlock.Builder code = CodeBlock.builder()
            .beginControlFlow("for (int i = 0; i < $N; i++)", n.count())
            .add(nullItemCheck(collection, n));
        if (collection.countField() == null) {
            code.addStatement("int start = writer.position()")
                .add(onItem(n, writeItem))
                .beginControlFlow("if ($L)", n.marker() == null
                    ? CodeBlock.of("writer.isLowValuesFrom(start)")
                    : CodeBlock.of("writer.isEmptySlotFrom($N, $N, start)", n.descriptor(),
                        n.marker()))
                .addStatement("throw new $T($N + $S + i + $S)", MAPPING_EXCEPTION, n.descriptor(),
                    ": item ", emptyItemError(collection))
                .endControlFlow();
        } else {
            code.add(onItem(n, writeItem));
        }
        code.endControlFlow();
        if (n.marker() == null) {
            return code.addStatement("writer.writeLowValues(($L - $N) * $L)",
                collection.maxOccurs(), n.count(), collection.item().byteLength()).build();
        }
        return code.addStatement("writer.writeEmptySlots($N, $L, $L - $N)", n.marker(),
            collection.item().byteLength(), collection.maxOccurs(), n.count()).build();
    }

    /** Reads item {@code i} of the list, and checks that it is not null. */
    private static CodeBlock nullItemCheck(CollectionModel collection, CollectionNames n) {
        return CodeBlock.builder()
            .addStatement("$T item = $N.get(i)", ClassName.get(collection.item().type()), n.list())
            .beginControlFlow("if (item == null)")
            .addStatement("throw new $T($N + $S)", MAPPING_EXCEPTION, n.descriptor(),
                ": the list contains a null item")
            .endControlFlow()
            .build();
    }

    /** Gives the end of the message for an item that a read would find empty. */
    private static String emptyItemError(CollectionModel collection) {
        if (collection.emptyKey() != null) {
            return " has an empty key " + collection.emptyKey().name()
                + ", so a read cannot find it";
        }
        return collection.emptyItem() == EmptyItem.SPACES
            ? " is all spaces, so a read cannot find it"
            : " is all LOW-VALUES, so a read cannot find it";
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
        CollectionNames n = members.names(collection);
        int itemLength = collection.item().byteLength();
        CodeBlock marker = n.marker() == null ? CodeBlock.of("")
            : CodeBlock.of(", $N", n.marker());
        CodeBlock size = collection.countField() == null
            ? CodeBlock.of("reader.countUsedSlots($N, $L, $L$L)", n.descriptor(), itemLength,
                collection.maxOccurs(), marker)
            : helpers.call(Helper.COLLECTION_SIZE,
                CodeBlock.of("target.$N()", collection.countField().getter()),
                CodeBlock.of("$N", n.descriptor()), CodeBlock.of("$L", collection.maxOccurs()));
        CodeBlock.Builder code = CodeBlock.builder().addStatement("int $N = $L", n.count(), size);
        if (append) {
            // A new list: the getter can return a copy or an unmodifiable list.
            code.addStatement("$T $N = new $T<>($T.requireNonNullElse(target.$N(), $T.of()))",
                listOf(collection), n.list(), ArrayList.class, Objects.class,
                collection.getter(), List.class);
        } else {
            code.addStatement("$T $N = new $T<>($N)", listOf(collection), n.list(),
                ArrayList.class, n.count());
        }
        code.beginControlFlow("for (int i = 0; i < $N; i++)", n.count())
            .add(onItem(n, CodeBlock.of("$N.add($N(reader, ctx))", n.list(),
                itemMethodsOf(collection).read())))
            .endControlFlow();
        if (collection.fixed()) {
            code.addStatement("reader.skipArea($N, ($L - $N) * $L)", n.descriptor(),
                collection.maxOccurs(), n.count(), itemLength);
        }
        return code.addStatement("target.$N($N)", collection.setter(), n.list()).build();
    }

    private ItemMethods itemMethodsOf(CollectionModel collection) {
        return members.itemMethods(collection.item());
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
        Names n = members.names(field);
        CodeBlock value = CodeBlock.of("value.$N()", field.getter());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("writer.writeText($N, $L, $L)", n.descriptor(), value,
                field.length());
            case INT, LONG -> writeNumber(field, CodeBlock.of("$T.valueOf($L)", DECIMAL, value));
            case INTEGER -> writeNumber(field, helpers.call(Helper.DECIMAL_OF_INTEGER, value));
            case LONG_BOXED -> writeNumber(field, helpers.call(Helper.DECIMAL_OF_LONG, value));
            case BIG_DECIMAL -> writeNumber(field, value);
            case LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME -> field.usage() == Usage.COMP3
                ? CodeBlock.of("writer.writePackedTemporal($N, $L, $L, $L, $N)", n.descriptor(),
                    value, field.length(), field.signed(), n.formatter())
                : CodeBlock.of("writer.writeTemporal($N, $L, $L, $N)", n.descriptor(), value,
                    field.length(), n.formatter());
            case ENUM -> {
                CodeBlock text = CodeBlock.of("$N($L, $N)", n.enumMethods().write(), value,
                    n.descriptor());
                yield field.enumModel().numeric() ? writeNumber(field, text)
                    : CodeBlock.of("writer.writeText($N, $L, $L)", n.descriptor(), text,
                        field.length());
            }
            case CONVERTED -> CodeBlock.of("writer.writeConverted($N, $L, $N, $L)",
                n.descriptor(), value, n.converter(), fieldContext(field));
        };
    }

    private CodeBlock writeNumber(FieldModel field, CodeBlock decimal) {
        Names n = members.names(field);
        return field.edited() != null
            ? CodeBlock.of("writer.writeEdited($N, $L, $N)", n.descriptor(), decimal, n.picture())
            : CodeBlock.of("writer.writeNumber($N, $L, $L)", n.descriptor(), decimal,
                numberAttributes(field));
    }

    private CodeBlock read(FieldModel field) {
        Names n = members.names(field);
        CodeBlock number = field.edited() != null
            ? CodeBlock.of("reader.readEdited($N, $N)", n.descriptor(), n.picture())
            : CodeBlock.of("reader.readNumber($N, $L)", n.descriptor(), numberAttributes(field));
        CodeBlock descriptor = CodeBlock.of("$N", n.descriptor());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("reader.readText($N, $L)", n.descriptor(), field.length());
            case INT -> helpers.call(Helper.INT_VALUE, number, descriptor);
            case INTEGER -> helpers.call(Helper.INTEGER_VALUE, number, descriptor);
            case LONG -> helpers.call(Helper.LONG_VALUE, number, descriptor);
            case LONG_BOXED -> helpers.call(Helper.LONG_BOXED_VALUE, number, descriptor);
            case BIG_DECIMAL -> number;
            case LOCAL_DATE -> readTemporal("Date", field);
            case LOCAL_TIME -> readTemporal("Time", field);
            case LOCAL_DATE_TIME -> readTemporal("DateTime", field);
            case ENUM -> CodeBlock.of("$N($L, $N)", n.enumMethods().read(),
                field.enumModel().numeric() ? number
                    : CodeBlock.of("reader.readText($N, $L)", n.descriptor(), field.length()),
                n.descriptor());
            case CONVERTED -> {
                CodeBlock converted = CodeBlock.of("reader.readConverted($N, $N, $L)",
                    n.descriptor(), n.converter(), fieldContext(field));
                yield field.type().getKind().isPrimitive()
                    ? helpers.call(Helper.NOT_NULL, converted, descriptor) : converted;
            }
        };
    }

    /** Reads a date or time field. A packed field calls {@code readPacked<type>}. */
    private CodeBlock readTemporal(String type, FieldModel field) {
        Names n = members.names(field);
        return field.usage() == Usage.COMP3
            ? CodeBlock.of("reader.$N($N, $L, $L, $N)", "readPacked" + type, n.descriptor(),
                field.length(), field.signed(), n.formatter())
            : CodeBlock.of("reader.$N($N, $L, $N)", "read" + type, n.descriptor(),
                field.length(), n.formatter());
    }

    private static CodeBlock fieldContext(FieldModel field) {
        return CodeBlock.of("new $T($S, $L, $L, $L, $T.$N, $S, $L, ctx.charset())", FIELD_CONTEXT,
            field.name(), field.length(), field.scale(), field.signed(), USAGE,
            field.usage().name(), field.pattern(), field.byteLength());
    }

    /**
     * Generates the methods of a codec with subtypes. Each layout gets private methods. The
     * public methods select the layout by the exact class of the object or by the value of the
     * discriminator.
     */
    private final class SubTypesGenerator {

        private final SubTypesModel subTypes = model.subTypes();
        private final FieldModel discriminator = subTypes.discriminator();
        private final Discriminator key = new Discriminator(members, discriminator, layouts, type);
        private final TypeName keyType = key.keyType();
        private final TypeName subtypeClass = key.subtypeClass();
        private final String typeOf = key.typeOf();
        private final String unknownValue = key.unknownValue();
        private final String prefix = type.simpleName() + ": ";
        private final Map<Layout, LayoutMethods> methods = new HashMap<>();
        private final String unknownSubclass = members.newName("unknownSubclass");
        private final String selectResponse = members.newName("selectResponse");
        private final String decodeInto = members.newName("decodeInto");
        private final String newSubtype = members.newName("newSubtype");

        /** The names of the private methods of one layout. */
        private record LayoutMethods(String encodeRequest, String encodeResponse,
                                     String decodeResponse, String decodeRequest, String reset) {
        }

        void addMethods(TypeSpec.Builder builder) {
            for (Layout layout : layouts) {
                String suffix = String.join("_", ClassName.get(layout.type()).simpleNames());
                LayoutMethods names = new LayoutMethods(
                    members.newName("encodeRequest_" + suffix),
                    members.newName("encodeResponse_" + suffix),
                    members.newName("decodeResponse_" + suffix),
                    members.newName("decodeRequest_" + suffix),
                    members.newName("resetOutputCollections_" + suffix));
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
            builder.addMethod(key.typeOfMethod());
            builder.addMethod(key.unknownValueMethod());
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
                        discriminator.setter(), key.literal(layout.value()));
                }
                method.addStatement("return $N($L, ctx)",
                    request ? names.encodeRequest() : names.encodeResponse(),
                    cast(layout, "value"))
                    .endControlFlow();
            }
            return method.addStatement("throw $N(value)", unknownSubclass).build();
        }

        /** Reads the discriminator at its fixed offset in one direction. */
        private CodeBlock readKey(Predicate<Direction> direction) {
            int offset = model.members().stream()
                .filter(member -> member.order() < discriminator.order())
                .filter(member -> direction.test(member.direction()))
                .mapToInt(MemberModel::areaLength).sum();
            return key.read(CodeBlock.of("new $T(data, ctx).peek($L)", BOOK_READER, offset));
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

        private MethodSpec unknownSubclass() {
            return privateMethod(unknownSubclass).addModifiers(Modifier.STATIC)
                .returns(MAPPING_EXCEPTION).addParameter(Object.class, "value")
                .addStatement("return new $T($S + value.getClass().getName())",
                    MAPPING_EXCEPTION, prefix + "unknown subclass ")
                .build();
        }
    }
}
