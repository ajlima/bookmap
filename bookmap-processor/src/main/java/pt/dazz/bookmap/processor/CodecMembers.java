package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.EDITED_PICTURE;
import static pt.dazz.bookmap.processor.CodecTypes.EMPTY_SLOT;
import static pt.dazz.bookmap.processor.CodecTypes.FORMATS;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.NameAllocator;
import com.palantir.javapoet.TypeSpec;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.processor.EnumMethodsGenerator.EnumMethods;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.FieldModel.JavaKind;
import pt.dazz.bookmap.processor.model.ItemModel;
import pt.dazz.bookmap.processor.model.MemberModel;

/**
 * The names of a codec. Gives each generated member and method a unique name, and adds the
 * constant fields of each field and collection: the descriptor, the date or time formatter, the
 * converter instance, the edited picture, and the empty-slot marker.
 */
final class CodecMembers {

    /** The names of the generated members of one field. */
    record Names(String descriptor, String formatter, String converter,
                 EnumMethods enumMethods, String picture) {
    }

    /**
     * The names of the generated members and local variables of one collection.
     *
     * @param marker the name of the {@code EmptySlot} constant, or {@code null} for LOW-VALUES
     * @param indexed {@code true} if a message of an item names the index of the item: the
     *     collection is in an item class, or its item class has a collection
     */
    record CollectionNames(String descriptor, String list, String count, String marker,
                           boolean indexed) {
    }

    /** The names of the methods that write and read one item class. */
    record ItemMethods(String write, String read) {
    }

    private final NameAllocator allocator = new NameAllocator();
    private final Map<FieldModel, Names> names = new HashMap<>();
    private final Map<CollectionModel, CollectionNames> collections = new HashMap<>();
    /** The converter field of each converter class, by qualified class name. */
    private final Map<String, String> converters = new HashMap<>();
    /** The item methods of each item class, by qualified class name. */
    private final Map<String, ItemMethods> items = new HashMap<>();
    private final EnumMethodsGenerator enums = new EnumMethodsGenerator(allocator);

    CodecMembers() {
        // The local variables of the generated methods.
        List.of("value", "target", "data", "ctx", "writer", "reader", "item", "i", "start",
                "key", "selected", "copy", "e")
            .forEach(allocator::newName);
    }

    /** Gives a unique name. */
    String newName(String name) {
        return allocator.newName(name);
    }

    Names names(FieldModel field) {
        return names.get(field);
    }

    boolean hasNames(FieldModel field) {
        return names.containsKey(field);
    }

    CollectionNames names(CollectionModel collection) {
        return collections.get(collection);
    }

    boolean hasNames(CollectionModel collection) {
        return collections.containsKey(collection);
    }

    /** Gives the item methods of an item class, or null before they are made. */
    ItemMethods itemMethods(ItemModel item) {
        return items.get(item.type().getQualifiedName().toString());
    }

    void putItemMethods(ItemModel item, ItemMethods methods) {
        items.put(item.type().getQualifiedName().toString(), methods);
    }

    /** Gives the methods that read and write the enum types. */
    List<MethodSpec> enumMethods() {
        return enums.methods();
    }

    /** Adds the constant fields of a field. */
    void addField(TypeSpec.Builder builder, FieldModel field) {
        String constant = upperSnake(field.name());
        String descriptor = allocator.newName(constant);
        builder.addField(FieldSpec.builder(String.class, descriptor, Modifier.PRIVATE,
            Modifier.STATIC, Modifier.FINAL).initializer("$S", field.descriptor()).build());
        String formatter = null;
        String converter = null;
        EnumMethods enumMethods = null;
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
            case ENUM -> enumMethods = enums.methodsOf(field.enumModel());
            default -> {
                // Text and numbers need no other member.
            }
        }
        String picture = null;
        if (field.edited() != null) {
            picture = allocator.newName(constant + "_PICTURE");
            builder.addField(FieldSpec.builder(EDITED_PICTURE, picture, Modifier.PRIVATE,
                    Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.parse($S, $L)", EDITED_PICTURE, field.edited().text(),
                    field.edited().blankWhenZero()).build());
        }
        names.put(field, new Names(descriptor, formatter, converter, enumMethods, picture));
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

    /**
     * Adds the constant fields of a collection, and gives the names of its local variables.
     *
     * @param inner {@code true} for a collection in an item class
     */
    void addCollection(TypeSpec.Builder builder, CollectionModel collection, boolean inner) {
        String descriptor = allocator.newName(upperSnake(collection.name()));
        builder.addField(FieldSpec.builder(String.class, descriptor, Modifier.PRIVATE,
            Modifier.STATIC, Modifier.FINAL).initializer("$S", collection.descriptor()).build());
        String marker = null;
        if (collection.marked()) {
            marker = allocator.newName(upperSnake(collection.name()) + "_EMPTY");
            builder.addField(FieldSpec.builder(EMPTY_SLOT, marker, Modifier.PRIVATE,
                Modifier.STATIC, Modifier.FINAL).initializer(emptySlot(collection)).build());
        }
        collections.put(collection, new CollectionNames(descriptor,
            allocator.newName(collection.name()), allocator.newName(collection.name() + "Count"),
            marker, inner || hasInnerCollection(collection.item())));
    }

    /** Tells if an item class has a collection, in its own members or in a subtype. */
    private static boolean hasInnerCollection(ItemModel item) {
        List<MemberModel> members = new ArrayList<>(item.members());
        if (item.subTypes() != null) {
            item.subTypes().variants().forEach(variant -> members.addAll(variant.members()));
        }
        return members.stream().anyMatch(CollectionModel.class::isInstance);
    }

    /** Gives the expression that makes the empty-slot marker of a collection. */
    private static CodeBlock emptySlot(CollectionModel collection) {
        CodeBlock.Builder code = CodeBlock.builder().add("$T.$N()", EMPTY_SLOT,
            collection.emptyItem() == EmptyItem.SPACES ? "spaces" : "lowValues");
        FieldModel key = collection.emptyKey();
        if (key != null && key.kind() == JavaKind.STRING) {
            code.add(".withTextKey($L, $L)", collection.emptyKeyOffset(), key.byteLength());
        } else if (key != null) {
            code.add(".withNumberKey($L, $L)", collection.emptyKeyOffset(),
                CodecTypes.numberAttributes(key));
        }
        return code.build();
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
}
