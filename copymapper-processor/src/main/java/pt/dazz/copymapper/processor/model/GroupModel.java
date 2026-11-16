package pt.dazz.copymapper.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;
import pt.dazz.copymapper.annotation.Direction;

/**
 * Describes one group without {@code OCCURS} ({@code @BookGroup}). A group is always a member
 * of an {@link AreaModel}.
 *
 * @param ownerSimpleName the simple name of the class that declares the group
 * @param name the Java field name
 * @param order the position in the book
 * @param direction the message in which the group appears
 * @param type the group class
 * @param members the members of the group class, sorted by order
 * @param byteLength the number of bytes of the group
 * @param getter the name of the getter method
 * @param setter the name of the setter method
 */
public record GroupModel(String ownerSimpleName, String name, int order, Direction direction,
    TypeElement type, List<MemberModel> members, int byteLength, String getter, String setter)
    implements MemberModel {

    @Override
    public int areaLength() {
        return byteLength;
    }
}
