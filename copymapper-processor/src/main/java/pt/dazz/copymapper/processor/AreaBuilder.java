package pt.dazz.copymapper.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import pt.dazz.copymapper.annotation.Direction;
import pt.dazz.copymapper.processor.model.AreaModel;
import pt.dazz.copymapper.processor.model.CollectionModel;
import pt.dazz.copymapper.processor.model.FieldModel;
import pt.dazz.copymapper.processor.model.GroupModel;
import pt.dazz.copymapper.processor.model.MemberModel;

/**
 * Puts the members of a {@code REDEFINES} area into one {@link AreaModel}, and checks the
 * rules of an area. An area is a redefined member and the members that name it in
 * {@code redefines} (the views).
 */
final class AreaBuilder {

    private final Problems problems;

    AreaBuilder(Problems problems) {
        this.problems = problems;
    }

    /**
     * Gives the names of the members of the areas of a class: the views and the members that
     * a view names.
     *
     * @param annotated the annotated members of the class
     * @return the names
     */
    static Set<String> names(List<AnnotatedMember> annotated) {
        Set<String> names = new HashSet<>();
        for (AnnotatedMember member : annotated) {
            if (!member.redefines().isEmpty()) {
                names.add(member.name());
                names.add(member.redefines());
            }
        }
        return names;
    }

    /**
     * Tells if a layout has an area with a member of a name.
     *
     * @param layout the members of a layout
     * @param name the member name
     * @return {@code true} if an area of the layout has the member
     */
    static boolean inArea(List<? extends MemberModel> layout, String name) {
        return layout.stream().anyMatch(member -> member instanceof AreaModel area
            && area.members().stream().anyMatch(inner -> inner.name().equals(name)));
    }

    /**
     * Puts the members of each area of one class into an {@link AreaModel}.
     *
     * @param owner the class that the generated code uses for the accessors
     * @param annotated the annotated members of the class
     * @param members the resolved members, sorted by order
     * @param top {@code true} for a member of an operation class or a subtype, which has a
     *     direction
     * @param continuable {@code true} if the class implements {@code Continuable}
     * @param unresolved {@code true} if javac could not resolve an annotated member
     * @return the members, with one {@link AreaModel} for each area
     */
    List<MemberModel> fold(TypeElement owner, List<AnnotatedMember> annotated,
                           List<MemberModel> members, boolean top, boolean continuable,
                           boolean unresolved) {
        Map<String, AnnotatedMember> byName = new HashMap<>();
        annotated.forEach(member -> byName.put(member.name(), member));
        Set<String> names = names(annotated);
        Set<String> resolved = new HashSet<>();
        members.forEach(member -> resolved.add(member.name()));
        List<List<MemberModel>> slots = new ArrayList<>();
        for (MemberModel member : members) {
            AnnotatedMember declared = byName.get(member.name());
            if (declared.redefines().isEmpty()) {
                if (member instanceof GroupModel && !names.contains(member.name())) {
                    problems.error(declared, "a @Group must be in a REDEFINES area");
                }
                slots.add(new ArrayList<>(List.of(member)));
            } else {
                addView(owner, declared, member, byName, resolved, slots, top, unresolved);
            }
        }
        List<MemberModel> result = new ArrayList<>();
        for (List<MemberModel> slot : slots) {
            if (slot.size() == 1) {
                result.add(slot.get(0));
            } else {
                checkArea(slot, byName, top && continuable);
                result.add(new AreaModel(owner, List.copyOf(slot)));
            }
        }
        return result;
    }

    /** Adds a view to the slot of the member that it redefines, or reports why not. */
    private void addView(TypeElement owner, AnnotatedMember view, MemberModel member,
                         Map<String, AnnotatedMember> byName, Set<String> resolved,
                         List<List<MemberModel>> slots, boolean top, boolean unresolved) {
        String name = view.redefines();
        AnnotatedMember target = byName.get(name);
        String prefix = "redefines member '" + name + "' ";
        if (target == null) {
            if (!unresolved) {
                problems.error(view, prefix + "does not exist in " + owner.getSimpleName());
            }
            return;
        }
        if (!target.redefines().isEmpty()) {
            problems.error(view, prefix + "is a view. Name the redefined member '"
                + target.redefines() + "'");
            return;
        }
        if (target.order() >= view.order()) {
            problems.error(view, prefix + "must come before the view");
            return;
        }
        if (!resolved.contains(name)) {
            // The redefined member has its own errors.
            return;
        }
        List<MemberModel> last = slots.get(slots.size() - 1);
        MemberModel redefined = last.get(0);
        if (!redefined.name().equals(name)) {
            problems.error(view, "a view must come right after the member it redefines: "
                + redefined.descriptor() + " is between them");
            return;
        }
        if (top && view.values().containsKey("direction")) {
            problems.error(view, "a view must not set direction. It uses the direction of "
                + redefined.descriptor());
        }
        last.add(withDirection(member, redefined.direction()));
    }

    /**
     * Checks the members of one area. The Continuable rule repeats the error of the redefined
     * member: the report shows a message only one time.
     */
    private void checkArea(List<MemberModel> slot, Map<String, AnnotatedMember> byName,
                           boolean continuable) {
        for (MemberModel member : slot) {
            AnnotatedMember declared = byName.get(member.name());
            if (member instanceof FieldModel field && field.type().getKind().isPrimitive()) {
                problems.error(declared, "a member of a REDEFINES area must not have a primitive"
                    + " type. Use Integer, Long, or BigDecimal");
            } else if (member instanceof CollectionModel collection) {
                checkCollection(collection, declared, continuable);
            }
        }
    }

    private void checkCollection(CollectionModel collection, AnnotatedMember declared,
                                 boolean continuable) {
        if (!collection.fixed() || collection.countField() != null) {
            problems.error(declared, "a collection in a REDEFINES area must have fixed = true"
                + " and no dependsOn");
        }
        if (collection.partial()) {
            problems.error(declared, "partial is not permitted in a REDEFINES area");
        }
        if (continuable && collection.direction() == Direction.INPUT_OUTPUT) {
            problems.error(declared,
                "an INPUT_OUTPUT collection is not permitted in a Continuable class");
        }
    }

    /**
     * Gives a copy of a field, a collection, or a group with another direction.
     *
     * @param member a field, a collection, or a group
     * @param direction the new direction
     * @return the copy
     */
    static MemberModel withDirection(MemberModel member, Direction direction) {
        if (member instanceof FieldModel f) {
            return withDirection(f, direction);
        }
        if (member instanceof CollectionModel c) {
            return new CollectionModel(c.ownerSimpleName(), c.name(), c.order(), direction,
                c.countField(), c.maxOccurs(), c.fixed(), c.item(), c.getter(), c.setter(),
                c.emptyItem(), c.emptyKey(), c.emptyKeyOffset(), c.partial());
        }
        GroupModel g = (GroupModel) member;
        return new GroupModel(g.ownerSimpleName(), g.name(), g.order(), direction, g.type(),
            g.members(), g.byteLength(), g.getter(), g.setter());
    }

    /**
     * Gives a copy of a field with another direction.
     *
     * @param f the field
     * @param direction the new direction
     * @return the copy
     */
    static FieldModel withDirection(FieldModel f, Direction direction) {
        return new FieldModel(f.ownerSimpleName(), f.name(), f.order(), f.length(), direction,
            f.usage(), f.scale(), f.signed(), f.sign(), f.pattern(), f.kind(), f.type(),
            f.getter(), f.setter(), f.converter(), f.byteLength(), f.enumModel(), f.edited());
    }
}
