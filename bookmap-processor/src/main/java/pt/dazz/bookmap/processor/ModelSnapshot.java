package pt.dazz.bookmap.processor;

import java.util.List;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.OperationModel;
import pt.dazz.bookmap.processor.model.SubTypesModel;

/**
 * Gives a copy of an {@link OperationModel} that contains only strings and primitive values.
 * Tests use it to examine the model after the compilation.
 *
 * @param operationName the logical operation name
 * @param charsetName the charset name, or {@code ""}
 * @param continuable {@code true} if the class implements {@code Continuable}
 * @param validatable {@code true} if the class implements {@code Validatable}
 * @param members the members, sorted by order
 * @param discriminator the name of the discriminator field, or {@code ""}
 * @param variants the subtypes
 */
record ModelSnapshot(String operationName, String charsetName, boolean continuable,
    boolean validatable, List<Member> members, String discriminator, List<Variant> variants) {

    /**
     * Gives a copy of one subtype.
     *
     * @param type the simple name of the subtype
     * @param value the normalized discriminator value
     * @param members the member names of the full layout, sorted by order
     */
    record Variant(String type, String value, List<String> members) {
    }

    /**
     * Gives a copy of one member. A collection has no length, scale, sign, usage, pattern, Java
     * kind, or converter. A field has no count field, no maximum, and no item fields.
     *
     * @param owner the simple name of the declaring class
     * @param name the field name
     * @param order the order
     * @param direction the direction
     * @param length the length
     * @param scale the scale
     * @param signed the sign setting
     * @param usage the usage name, or {@code ""}
     * @param pattern the pattern, or {@code ""}
     * @param byteLength the number of bytes of a field, or of one entry of a collection
     * @param javaKind the Java kind name, or {@code ""}
     * @param getter the getter name
     * @param setter the setter name
     * @param converter the qualified converter class name, or {@code ""}
     * @param countField the name of the count field, or {@code ""}
     * @param maxOccurs the maximum number of entries, or 0
     * @param fixed {@code true} for a fixed collection
     * @param itemFields the fields of one entry
     * @param edited the edited picture, with {@code BLANK WHEN ZERO}, or {@code ""}
     */
    record Member(String owner, String name, int order, String direction, int length, int scale,
        boolean signed, String usage, String pattern, int byteLength, String javaKind,
        String getter, String setter, String converter, String countField, int maxOccurs,
        boolean fixed, List<Member> itemFields, String edited) {
    }

    /**
     * Makes a snapshot of a model.
     *
     * @param model the model
     * @return the snapshot
     */
    static ModelSnapshot of(OperationModel model) {
        SubTypesModel subTypes = model.subTypes();
        return new ModelSnapshot(model.operationName(), model.charsetName(), model.continuable(),
            model.validatable(), model.members().stream().map(ModelSnapshot::member).toList(),
            subTypes == null ? "" : subTypes.discriminator().name(),
            subTypes == null ? List.of() : subTypes.variants().stream()
                .map(variant -> new Variant(variant.type().getSimpleName().toString(),
                    variant.value(), variant.members().stream().map(MemberModel::name).toList()))
                .toList());
    }

    private static Member member(MemberModel member) {
        return switch (member) {
            case FieldModel field -> field(field);
            case CollectionModel collection -> new Member(collection.ownerSimpleName(),
                collection.name(), collection.order(), collection.direction().name(), 0, 0, false,
                "", "", collection.item().byteLength(), "", collection.getter(),
                collection.setter(), "",
                collection.countField() == null ? "" : collection.countField().name(),
                collection.maxOccurs(), collection.fixed(),
                collection.item().fields().stream().map(ModelSnapshot::field).toList(), "");
        };
    }

    private static Member field(FieldModel field) {
        return new Member(field.ownerSimpleName(), field.name(), field.order(),
            field.direction().name(), field.length(), field.scale(), field.signed(),
            field.usage().name(), field.pattern(), field.byteLength(), field.kind().name(),
            field.getter(), field.setter(),
            field.converter() == null ? "" : field.converter().getQualifiedName().toString(), "", 0,
            false, List.of(), field.edited() == null ? "" : field.edited().toString());
    }
}
