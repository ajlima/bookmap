package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.BOOK_CODEC;
import static pt.dazz.bookmap.processor.CodecTypes.BYTES;
import static pt.dazz.bookmap.processor.CodecTypes.CODEC_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.GENERATED;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;
import static pt.dazz.bookmap.processor.LayoutCode.override;
import static pt.dazz.bookmap.processor.LayoutCode.resetOutputCollections;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.OperationModel;

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
 *
 * <p>This class makes the frame of the codec class. {@link CodecMembers} gives the names and
 * the constant fields, {@link FieldCode} and {@link CollectionCode} the code of the members,
 * {@link ItemMethodsGenerator} the item methods, {@link LayoutCode} the encode and decode code,
 * and {@link SubTypesGenerator} the methods of a codec with subtypes.
 */
final class CodecGenerator {

    /** The value of the {@code @Generated} annotation of each codec. */
    static final String GENERATOR = "pt.dazz.bookmap.processor.BookmapProcessor";

    private final OperationModel model;
    private final ClassName type;
    /** The layouts of the codec: one without subtypes, else one for each class. */
    private final List<Layout> layouts;
    private final HelperMethods helpers = new HelperMethods();
    private final CodecMembers members = new CodecMembers();
    private final ItemMethodsGenerator items;
    private final LayoutCode code;

    private CodecGenerator(OperationModel model) {
        this.model = model;
        this.type = ClassName.get(model.type());
        this.layouts = Layout.of(model.type(), model.members(), model.subTypes());
        FieldCode fields = new FieldCode(members, helpers);
        CollectionCode collections = new CollectionCode(members, helpers);
        this.items = new ItemMethodsGenerator(members, fields, collections);
        this.code = new LayoutCode(fields, collections);
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
                            items.addCollection(builder, collection, false);
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
            addMethods(builder);
        } else {
            new SubTypesGenerator(model, layouts, members, code).addMethods(builder);
        }
        builder.addMethods(items.methods());
        builder.addMethods(members.enumMethods());
        builder.addMethods(helpers.methods());
        return JavaFile.builder(codec.packageName(), builder.build()).build();
    }

    /** Adds the public encode, decode, and reset methods of a codec without subtypes. */
    private void addMethods(TypeSpec.Builder builder) {
        List<MemberModel> layout = model.members();
        builder.addMethod(code.encode(override("encodeRequest"), type, layout,
            Direction::isInput));
        builder.addMethod(override("decodeResponse")
            .addParameter(BYTES, "data").addParameter(type, "target")
            .addParameter(CODEC_CONTEXT, "ctx")
            .addCode(code.decode(layout, Direction::isOutput, true)).build());
        builder.addMethod(override("decodeRequest").returns(type)
            .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T target = newInstance()", type)
            .addCode(code.decode(layout, Direction::isInput, false))
            .addStatement("return target").build());
        builder.addMethod(code.encode(override("encodeResponse"), type, layout,
            Direction::isOutput));
        builder.addMethod(resetOutputCollections(override("resetOutputCollections"), type,
            layout));
    }
}
