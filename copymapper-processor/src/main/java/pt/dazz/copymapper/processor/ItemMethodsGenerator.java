package pt.dazz.copymapper.processor;

import static pt.dazz.copymapper.processor.CodecTypes.BOOK_READER;
import static pt.dazz.copymapper.processor.CodecTypes.BOOK_WRITER;
import static pt.dazz.copymapper.processor.CodecTypes.CODEC_CONTEXT;
import static pt.dazz.copymapper.processor.CodecTypes.MAPPING_EXCEPTION;

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
import pt.dazz.copymapper.processor.CodecMembers.ItemMethods;
import pt.dazz.copymapper.processor.model.AreaModel;
import pt.dazz.copymapper.processor.model.CollectionModel;
import pt.dazz.copymapper.processor.model.FieldModel;
import pt.dazz.copymapper.processor.model.GroupModel;
import pt.dazz.copymapper.processor.model.ItemModel;
import pt.dazz.copymapper.processor.model.MemberModel;

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

    /** Adds the members of a collection and of its item class. */
    void addCollection(TypeSpec.Builder builder, CollectionModel collection) {
        members.addCollection(builder, collection);
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
            // The members of a base class can already be in another layout.
            layout.members().forEach(member -> addMember(builder, member));
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
            write.addCode(writeMember(member));
            read.addCode(readMember(member, false, false));
        }
        methods.add(write.build());
        methods.add(read.addStatement("return target").build());
    }

    // ---- members of any kind ----

    /**
     * Adds the constants and the methods of a member, if they are not there yet: a field, a
     * collection and its item class, a group and its group class, or an area and its members.
     */
    void addMember(TypeSpec.Builder builder, MemberModel member) {
        if (member instanceof FieldModel field) {
            if (!members.hasNames(field)) {
                members.addField(builder, field);
            }
        } else if (member instanceof CollectionModel collection) {
            if (!members.hasNames(collection)) {
                addCollection(builder, collection);
            }
        } else if (member instanceof GroupModel group) {
            addGroup(builder, group);
        } else {
            addArea(builder, (AreaModel) member);
        }
    }

    /**
     * Gives the code that writes a member of {@code value}. For a collection, the code of
     * {@link CollectionCode#setCount} comes before it.
     */
    CodeBlock writeMember(MemberModel member) {
        if (member instanceof FieldModel field) {
            return CodeBlock.builder().addStatement(fields.write(field)).build();
        }
        if (member instanceof CollectionModel collection) {
            return collections.writeItems(collection);
        }
        if (member instanceof GroupModel group) {
            return CodeBlock.builder().addStatement("$N(writer, value.$N(), ctx)",
                members.groupMethods(group).write(), group.getter()).build();
        }
        return CodeBlock.builder().addStatement("$N(writer, value, ctx)",
            members.areaNames((AreaModel) member).methods().write()).build();
    }

    /**
     * Gives the code that reads a field, a collection, or an area into {@code target}.
     *
     * @param append {@code true} to add the items of a collection to the existing items
     * @param partial {@code true} for a collection that can end the response
     */
    CodeBlock readMember(MemberModel member, boolean append, boolean partial) {
        if (member instanceof FieldModel field) {
            return CodeBlock.builder()
                .addStatement("target.$N($L)", field.setter(), fields.read(field)).build();
        }
        if (member instanceof CollectionModel collection) {
            return collections.readItems(collection, append, partial);
        }
        return CodeBlock.builder().addStatement("$N(reader, target, ctx)",
            members.areaNames((AreaModel) member).methods().read()).build();
    }

    // ---- groups ----

    /** Adds the members of a group class and makes its write and read methods. */
    private void addGroup(TypeSpec.Builder builder, GroupModel group) {
        if (members.groupMethods(group) != null) {
            return;
        }
        group.members().forEach(member -> addMember(builder, member));
        String suffix = String.join("_", ClassName.get(group.type()).simpleNames());
        ItemMethods names = new ItemMethods(members.newName("writeGroup_" + suffix),
            members.newName("readGroup_" + suffix));
        members.putGroupMethods(group, names);
        addLayoutMethods(names, group.type(), group.members());
    }

    // ---- areas ----

    /**
     * Adds the members of an area and makes its write and read methods. The write method
     * writes the first member that is not null, then spaces to the end of the area. The read
     * method reads each member from the start of the area. A member with a value that is not
     * valid for its type is null.
     */
    private void addArea(TypeSpec.Builder builder, AreaModel area) {
        if (members.areaNames(area) != null) {
            return;
        }
        area.members().forEach(member -> addMember(builder, member));
        String suffix = area.ownerSimpleName() + "_" + area.name();
        ItemMethods names = new ItemMethods(members.newName("writeArea_" + suffix),
            members.newName("readArea_" + suffix));
        String descriptor = members.addAreaDescriptor(builder, area);
        members.putAreaNames(area, new CodecMembers.AreaNames(descriptor, names));
        ClassName owner = ClassName.get(area.owner());
        MethodSpec.Builder write = MethodSpec.methodBuilder(names.write())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_WRITER, "writer")
            .addParameter(owner, "value").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("int areaStart = writer.position()");
        boolean first = true;
        for (MemberModel member : area.members()) {
            if (first) {
                write.beginControlFlow("if (value.$N() != null)", getter(member));
            } else {
                write.nextControlFlow("else if (value.$N() != null)", getter(member));
            }
            first = false;
            if (member instanceof CollectionModel collection) {
                write.addCode(collections.setCount(collection));
            }
            write.addCode(writeMember(member));
        }
        methods.add(write.endControlFlow()
            .addStatement("writer.writeFiller($L - (writer.position() - areaStart))",
                area.areaLength())
            .build());
        MethodSpec.Builder read = MethodSpec.methodBuilder(names.read())
            .addModifiers(Modifier.PRIVATE).addParameter(BOOK_READER, "outer")
            .addParameter(owner, "target").addParameter(CODEC_CONTEXT, "ctx")
            .addStatement("$T area = outer.area($N, $L)", BOOK_READER, descriptor,
                area.areaLength());
        for (MemberModel member : area.members()) {
            read.addStatement("target.$N($T.orNull(() -> $L))", setter(member), BOOK_READER,
                readValue(builder, member));
        }
        methods.add(read.build());
    }

    /** Gives the expression that reads a member of an area from the start of the area. */
    private CodeBlock readValue(TypeSpec.Builder builder, MemberModel member) {
        if (member instanceof FieldModel field) {
            return CodeBlock.builder().add("{\n").indent()
                .add("$T reader = area.peek(0);\n", BOOK_READER)
                .add("return $L;\n", fields.read(field))
                .unindent().add("}").build();
        }
        if (member instanceof GroupModel group) {
            return CodeBlock.of("$N(area.peek(0), ctx)", members.groupMethods(group).read());
        }
        return CodeBlock.of("$N(area.peek(0), ctx)",
            listReader((CollectionModel) member));
    }

    /** Makes the method that reads the list of a collection in an area. */
    private String listReader(CollectionModel collection) {
        String name = members.newName("readList_" + collection.name());
        methods.add(MethodSpec.methodBuilder(name).addModifiers(Modifier.PRIVATE)
            .returns(CollectionCode.listOf(collection))
            .addParameter(BOOK_READER, "reader").addParameter(CODEC_CONTEXT, "ctx")
            .addCode(collections.readList(collection, false, false))
            .addStatement("return $N", members.names(collection).list())
            .build());
        return name;
    }

    /** Gives the getter of a field, a collection, or a group. */
    static String getter(MemberModel member) {
        if (member instanceof FieldModel field) {
            return field.getter();
        }
        if (member instanceof CollectionModel collection) {
            return collection.getter();
        }
        return ((GroupModel) member).getter();
    }

    /** Gives the setter of a field, a collection, or a group. */
    static String setter(MemberModel member) {
        if (member instanceof FieldModel field) {
            return field.setter();
        }
        if (member instanceof CollectionModel collection) {
            return collection.setter();
        }
        return ((GroupModel) member).setter();
    }
}
