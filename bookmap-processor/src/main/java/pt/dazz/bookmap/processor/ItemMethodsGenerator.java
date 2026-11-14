package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.BOOK_READER;
import static pt.dazz.bookmap.processor.CodecTypes.BOOK_WRITER;
import static pt.dazz.bookmap.processor.CodecTypes.CODEC_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.MAPPING_EXCEPTION;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.processor.CodecMembers.ItemMethods;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.ItemModel;
import pt.dazz.bookmap.processor.model.MemberModel;

/**
 * Generates the members of a collection and of its item class. Each item class has one private
 * method that writes an item and one private method that reads an item. An item class with
 * subtypes has one pair of methods for each layout, and a pair that selects the layout.
 */
final class ItemMethodsGenerator {

    private final CodecMembers members;
    private final FieldCode fields;
    private final CollectionCode collections;
    private final List<MethodSpec> methods = new ArrayList<>();

    ItemMethodsGenerator(CodecMembers members, FieldCode fields, CollectionCode collections) {
        this.members = members;
        this.fields = fields;
        this.collections = collections;
    }

    /** Gives the item methods, in the order of their creation. */
    List<MethodSpec> methods() {
        return methods;
    }

    /**
     * Adds the members of a collection and of its item class.
     *
     * @param inner {@code true} for a collection in an item class
     */
    void addCollection(TypeSpec.Builder builder, CollectionModel collection, boolean inner) {
        members.addCollection(builder, collection, inner);
        ItemModel item = collection.item();
        if (members.itemMethods(item) == null) {
            // Not computeIfAbsent: the item members can add the items of inner collections.
            members.putItemMethods(item, addItemMembers(builder, item));
        }
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
                        addCollection(builder, inner, true);
                    default -> {
                        // The members of a base class are already in another layout.
                    }
                }
            }
        }
        ClassName itemType = ClassName.get(item.type());
        String suffix = String.join("_", itemType.simpleNames());
        ItemMethods itemMethods = new ItemMethods(members.newName("writeItem_" + suffix),
            members.newName("readItem_" + suffix));
        if (item.subTypes() == null) {
            addLayoutMethods(itemMethods, item.type(), item.members());
            return itemMethods;
        }
        Map<Layout, ItemMethods> layoutMethods = new HashMap<>();
        for (Layout layout : itemLayouts) {
            String layoutSuffix = String.join("_", ClassName.get(layout.type()).simpleNames());
            ItemMethods names = new ItemMethods(members.newName("writeItem_" + layoutSuffix),
                members.newName("readItem_" + layoutSuffix));
            addLayoutMethods(names, layout.type(), layout.members());
            layoutMethods.put(layout, names);
        }
        FieldModel discriminator = item.subTypes().discriminator();
        Discriminator key = new Discriminator(members, discriminator, itemLayouts, itemType);
        methods.add(key.typeOfMethod());
        methods.add(key.unknownValueMethod());
        int offset = item.members().stream()
            .filter(member -> member.order() < discriminator.order())
            .mapToInt(MemberModel::areaLength).sum();
        String prefix = itemType.simpleName() + ": ";
        // Write: select the layout by the exact class, then fill to the longest layout.
        MethodSpec.Builder write = MethodSpec.methodBuilder(itemMethods.write())
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
        methods.add(write.nextControlFlow("else")
            .addStatement("throw new $T($S + value.getClass().getName())", MAPPING_EXCEPTION,
                prefix + "unknown subclass ")
            .endControlFlow()
            .addStatement("writer.writeFiller($L - (writer.position() - start))",
                item.byteLength())
            .build());
        // Read: read the discriminator, select the layout, then skip to the next entry.
        MethodSpec.Builder read = MethodSpec.methodBuilder(itemMethods.read())
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
        methods.add(read.nextControlFlow("else")
            .addStatement("throw new $T($S + selected.getName())", MAPPING_EXCEPTION,
                prefix + "unknown subclass ")
            .endControlFlow()
            .addStatement("reader.skip($L - (reader.position() - start))", item.byteLength())
            .addStatement("return item")
            .build());
        return itemMethods;
    }

    /** Adds the methods that write and read one layout of an item class. */
    private void addLayoutMethods(ItemMethods names, TypeElement layoutClass,
                                  List<MemberModel> layoutMembers) {
        ClassName itemType = ClassName.get(layoutClass);
        MethodSpec.Builder write = MethodSpec.methodBuilder(names.write())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_WRITER, "writer")
            .addParameter(itemType, "value").addParameter(CODEC_CONTEXT, "ctx");
        MethodSpec.Builder read = MethodSpec.methodBuilder(names.read())
            .addModifiers(Modifier.PRIVATE).returns(itemType)
            .addParameter(BOOK_READER, "reader").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T target = new $T()", itemType, itemType);
        for (MemberModel member : layoutMembers) {
            if (member instanceof CollectionModel inner) {
                write.addCode(collections.setCount(inner));
            }
        }
        for (MemberModel member : layoutMembers) {
            switch (member) {
                case FieldModel field -> {
                    write.addStatement(fields.write(field));
                    read.addStatement("target.$N($L)", field.setter(), fields.read(field));
                }
                case CollectionModel inner -> {
                    write.addCode(collections.writeItems(inner));
                    read.addCode(collections.readItems(inner, false));
                }
            }
        }
        methods.add(write.build());
        methods.add(read.addStatement("return target").build());
    }
}
