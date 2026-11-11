package pt.dazz.bookmap.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Describes the full layout of one operation class. With subtypes, {@code members} is the
 * layout of the base class.
 *
 * @param type the operation class
 * @param operationName the logical operation name
 * @param charsetName the charset name from {@code @BookDefaults}, or {@code ""} if it is not set
 * @param members the fields and collections, sorted by order
 * @param continuable {@code true} if the class implements {@code Continuable}
 * @param validatable {@code true} if the class implements {@code Validatable}
 * @param subTypes the subtypes, or {@code null} if the class has no {@code @BookSubTypes}
 */
public record OperationModel(TypeElement type, String operationName, String charsetName,
    List<MemberModel> members, boolean continuable, boolean validatable,
    SubTypesModel subTypes) {
}
