package pt.dazz.copymapper.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;
import pt.dazz.copymapper.annotation.Direction;

/**
 * Describes one {@code REDEFINES} area: the redefined member and its views. All members start
 * at the same byte. The name, the order, the direction, and the declaring class are the ones of
 * the redefined member.
 *
 * @param owner the class that the generated code uses for the accessors
 * @param members the redefined member, then the views, sorted by order. Each member is a
 *     {@link FieldModel}, a {@link CollectionModel}, or a {@link GroupModel}
 */
public record AreaModel(TypeElement owner, List<MemberModel> members) implements MemberModel {

    /**
     * Gives the redefined member.
     *
     * @return the first member
     */
    public MemberModel redefined() {
        return members.get(0);
    }

    @Override
    public String name() {
        return redefined().name();
    }

    @Override
    public int order() {
        return redefined().order();
    }

    @Override
    public Direction direction() {
        return redefined().direction();
    }

    @Override
    public String ownerSimpleName() {
        return redefined().ownerSimpleName();
    }

    /** Gives the length of the longest member. */
    @Override
    public int areaLength() {
        return members.stream().mapToInt(MemberModel::areaLength).max().orElseThrow();
    }
}
