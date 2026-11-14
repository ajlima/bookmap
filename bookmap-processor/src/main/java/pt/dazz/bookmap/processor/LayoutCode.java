package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.BOOK_READER;
import static pt.dazz.bookmap.processor.CodecTypes.BOOK_WRITER;
import static pt.dazz.bookmap.processor.CodecTypes.BYTES;
import static pt.dazz.bookmap.processor.CodecTypes.CODEC_CONTEXT;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.MemberModel;

/**
 * Generates the encode, decode, and reset code of one layout: the members of a class in one
 * direction, in order.
 */
final class LayoutCode {

    private final FieldCode fields;
    private final CollectionCode collections;

    LayoutCode(FieldCode fields, CollectionCode collections) {
        this.fields = fields;
        this.collections = collections;
    }

    /** Starts a public method that overrides a method of {@code BookCodec}. */
    static MethodSpec.Builder override(String name) {
        return MethodSpec.methodBuilder(name).addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC);
    }

    /**
     * Makes an encode method. First, the method sets the count field of each collection to the
     * list size. Then it writes the members in order.
     */
    MethodSpec encode(MethodSpec.Builder method, TypeName valueType, List<MemberModel> layout,
                      Predicate<Direction> direction) {
        List<MemberModel> members = layout.stream()
            .filter(member -> direction.test(member.direction())).toList();
        method.returns(BYTES).addParameter(valueType, "value").addParameter(CODEC_CONTEXT, "ctx");
        for (MemberModel member : members) {
            if (member instanceof CollectionModel collection) {
                method.addCode(collections.setCount(collection));
            }
        }
        method.addStatement("$T writer = new $T(ctx)", BOOK_WRITER, BOOK_WRITER);
        for (MemberModel member : members) {
            switch (member) {
                case FieldModel field -> method.addStatement(fields.write(field));
                case CollectionModel collection ->
                    method.addCode(collections.writeItems(collection));
            }
        }
        return method.addStatement("return writer.toByteArray()").build();
    }

    /**
     * Makes the body of a decode method.
     *
     * @param direction the members to read
     * @param response {@code true} for {@code decodeResponse}, which adds the items of an
     *     {@code OUTPUT} collection to a new list with the existing items
     */
    CodeBlock decode(List<MemberModel> layout, Predicate<Direction> direction,
                     boolean response) {
        CodeBlock.Builder code = CodeBlock.builder()
            .addStatement("$T reader = new $T(data, ctx)", BOOK_READER, BOOK_READER);
        for (MemberModel member : layout) {
            if (direction.test(member.direction())) {
                switch (member) {
                    case FieldModel field ->
                        code.addStatement("target.$N($L)", field.setter(), fields.read(field));
                    case CollectionModel collection -> code.add(collections.readItems(collection,
                        response && collection.direction() == Direction.OUTPUT));
                }
            }
        }
        return code.build();
    }

    /** Makes a method that sets a new empty list in each {@code OUTPUT} collection. */
    static MethodSpec resetOutputCollections(MethodSpec.Builder method, TypeName valueType,
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
}
