package pt.dazz.copymapper.processor;

import static pt.dazz.copymapper.processor.CodecTypes.MAPPING_EXCEPTION;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pt.dazz.copymapper.annotation.EmptyItem;
import pt.dazz.copymapper.processor.CodecMembers.CollectionNames;
import pt.dazz.copymapper.processor.CodecMembers.ItemMethods;
import pt.dazz.copymapper.processor.HelperMethods.Helper;
import pt.dazz.copymapper.processor.model.CollectionModel;
import pt.dazz.copymapper.processor.model.FieldModel;
import pt.dazz.copymapper.processor.model.FieldModel.JavaKind;

/**
 * Generates the code that writes and reads one collection. The encode code sets the count field
 * to the list size. The write code calls the item method for each item. A fixed collection
 * fills the unused slots. The read code gives a new list.
 */
final class CollectionCode {

    private final CodecMembers members;
    private final HelperMethods helpers;

    CollectionCode(CodecMembers members, HelperMethods helpers) {
        this.members = members;
        this.helpers = helpers;
    }

    /** Gives {@code List<Item>} for a collection. */
    static TypeName listOf(CollectionModel collection) {
        return ParameterizedTypeName.get(ClassName.get(List.class),
            ClassName.get(collection.item().type()));
    }

    /**
     * Gives the code that reads the list of {@code value}, checks its size, and sets the count
     * field to the size.
     */
    CodeBlock setCount(CollectionModel collection) {
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

    /** Gives the code that writes the items. It follows the code of {@link #setCount}. */
    CodeBlock writeItems(CollectionModel collection) {
        if (collection.fixed()) {
            return writeFixedItems(collection);
        }
        CollectionNames n = members.names(collection);
        // The count is 0 for a null list.
        return CodeBlock.builder()
            .beginControlFlow("for (int i = 0; i < $N; i++)", n.count())
            .add(nullItemCheck(collection, n))
            .add(onItem(n, CodeBlock.of("$N(writer, item, ctx)",
                itemMethodsOf(collection).write())))
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
            .addStatement("throw new $T($N + $S + i + $S)", MAPPING_EXCEPTION, n.descriptor(),
                ": item ", " is null")
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
     * Gives the code that reads the items and sets the list of {@code target}.
     *
     * @param append {@code true} to add the items to a new list with the existing items
     * @param partial {@code true} if the data can end on a slot boundary inside the fixed area
     */
    CodeBlock readItems(CollectionModel collection, boolean append, boolean partial) {
        return readList(collection, append, partial).toBuilder()
            .addStatement("target.$N($N)", collection.setter(), members.names(collection).list())
            .build();
    }

    /**
     * Gives the code that reads the items into the local list of the collection. Without
     * {@code append} and without a count field, the code does not use {@code target}.
     *
     * @param append {@code true} to add the items to a new list with the existing items
     * @param partial {@code true} if the data can end on a slot boundary inside the fixed area
     */
    CodeBlock readList(CollectionModel collection, boolean append, boolean partial) {
        CollectionNames n = members.names(collection);
        int itemLength = collection.item().byteLength();
        CodeBlock.Builder code = CodeBlock.builder();
        // The slots that a read can use: the present slots of a partial response, else all.
        CodeBlock slots = CodeBlock.of("$L", collection.maxOccurs());
        if (partial) {
            code.addStatement("int $N = reader.presentSlots($N, $L, $L)", n.present(),
                n.descriptor(), itemLength, collection.maxOccurs());
            slots = CodeBlock.of("$N", n.present());
        }
        CodeBlock marker = n.marker() == null ? CodeBlock.of("")
            : CodeBlock.of(", $N", n.marker());
        CodeBlock size = collection.countField() == null
            ? CodeBlock.of("reader.countUsedSlots($N, $L, $L$L)", n.descriptor(), itemLength,
                slots, marker)
            : helpers.call(Helper.COLLECTION_SIZE,
                CodeBlock.of("target.$N()", collection.countField().getter()),
                CodeBlock.of("$N", n.descriptor()), CodeBlock.of("$L", collection.maxOccurs()));
        code.addStatement("int $N = $L", n.count(), size);
        if (partial && collection.countField() != null) {
            code.beginControlFlow("if ($N > $N)", n.count(), n.present())
                .addStatement("throw new $T($N + $S + $N + $S + $N + $S)", MAPPING_EXCEPTION,
                    n.descriptor(), ": count ", n.count(),
                    " is more than the number of slots in the response (", n.present(), ")")
                .endControlFlow();
        }
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
                slots, n.count(), itemLength);
        }
        return code.build();
    }

    /**
     * Gives a statement on one item. A {@code MappingException} of the statement gets the
     * descriptor of the collection and the index {@code i} of the item at the start of its
     * message. The exception object and its class do not change.
     */
    private static CodeBlock onItem(CollectionNames n, CodeBlock statement) {
        return CodeBlock.builder()
            .beginControlFlow("try")
            .addStatement(statement)
            .nextControlFlow("catch ($T e)", MAPPING_EXCEPTION)
            .addStatement("throw e.atItem($N, i)", n.descriptor())
            .endControlFlow()
            .build();
    }

    private ItemMethods itemMethodsOf(CollectionModel collection) {
        return members.itemMethods(collection.item());
    }
}
