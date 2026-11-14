package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.BOOK_READER;
import static pt.dazz.bookmap.processor.CodecTypes.BYTES;
import static pt.dazz.bookmap.processor.CodecTypes.CODEC_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;
import static pt.dazz.bookmap.processor.LayoutCode.override;
import static pt.dazz.bookmap.processor.LayoutCode.resetOutputCollections;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.OperationModel;
import pt.dazz.bookmap.processor.model.SubTypesModel;

/**
 * Generates the methods of a codec with subtypes. Each layout gets private methods. The
 * public methods select the layout by the exact class of the object or by the value of the
 * discriminator.
 */
final class SubTypesGenerator {

    private final OperationModel model;
    private final ClassName type;
    private final List<Layout> layouts;
    private final CodecMembers members;
    private final LayoutCode code;
    private final SubTypesModel subTypes;
    private final FieldModel discriminator;
    private final Discriminator key;
    private final TypeName keyType;
    private final TypeName subtypeClass;
    private final String typeOf;
    private final String unknownValue;
    private final String prefix;
    private final Map<Layout, LayoutMethods> methods = new HashMap<>();
    private final String unknownSubclass;
    private final String selectResponse;
    private final String decodeInto;
    private final String newSubtype;

    /** The names of the private methods of one layout. */
    private record LayoutMethods(String encodeRequest, String encodeResponse,
                                 String decodeResponse, String decodeRequest, String reset) {
    }

    SubTypesGenerator(OperationModel model, List<Layout> layouts, CodecMembers members,
                      LayoutCode code) {
        this.model = model;
        this.type = ClassName.get(model.type());
        this.layouts = layouts;
        this.members = members;
        this.code = code;
        this.subTypes = model.subTypes();
        this.discriminator = subTypes.discriminator();
        this.key = new Discriminator(members, discriminator, layouts, type);
        this.keyType = key.keyType();
        this.subtypeClass = key.subtypeClass();
        this.typeOf = key.typeOf();
        this.unknownValue = key.unknownValue();
        this.prefix = type.simpleName() + ": ";
        this.unknownSubclass = members.newName("unknownSubclass");
        this.selectResponse = members.newName("selectResponse");
        this.decodeInto = members.newName("decodeInto");
        this.newSubtype = members.newName("newSubtype");
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
            builder.addMethod(code.encode(privateMethod(names.encodeRequest()), layoutType,
                layout.members(), Direction::isInput));
            builder.addMethod(code.encode(privateMethod(names.encodeResponse()), layoutType,
                layout.members(), Direction::isOutput));
            builder.addMethod(privateMethod(names.decodeResponse())
                .addParameter(BYTES, "data").addParameter(layoutType, "target")
                .addParameter(CODEC_CONTEXT, "ctx")
                .addCode(code.decode(layout.members(), Direction::isOutput, true)).build());
            builder.addMethod(privateMethod(names.decodeRequest()).returns(layoutType)
                .addParameter(BYTES, "data").addParameter(CODEC_CONTEXT, "ctx")
                .addStatement("$T target = new $T()", layoutType, layoutType)
                .addCode(code.decode(layout.members(), Direction::isInput, false))
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
