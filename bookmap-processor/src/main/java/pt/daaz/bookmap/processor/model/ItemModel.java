package pt.daaz.bookmap.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Describes the layout of one entry of a collection.
 *
 * @param type the item class
 * @param fields the fields of the item, sorted by order
 * @param byteLength the number of bytes of one entry
 */
public record ItemModel(TypeElement type, List<FieldModel> fields, int byteLength) {
}
