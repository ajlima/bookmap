package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes one generated class.
 *
 * @param name the simple class name
 * @param kind the role of the class
 * @param superclass the simple name of the superclass, or {@code null}
 * @param subTypes the subtypes of a base class, or {@code null}
 * @param members the members, in order
 * @param continuable the handler class of {@code Continuable}, or {@code null}
 * @param validatable the handler class of {@code Validatable}, or {@code null}
 */
public record ClassModel(String name, ClassKind kind, String superclass, SubTypes subTypes,
                         List<MemberModel> members, String continuable, String validatable) {

    /**
     * Creates a class model.
     *
     * @param name the name
     * @param kind the kind
     * @param superclass the superclass
     * @param subTypes the subtypes
     * @param members the members
     * @param continuable the {@code Continuable} handler
     * @param validatable the {@code Validatable} handler
     */
    public ClassModel {
        members = List.copyOf(members);
    }

    /**
     * Creates a class model without {@code Continuable} and {@code Validatable} handlers.
     *
     * @param name the name
     * @param kind the kind
     * @param superclass the superclass
     * @param subTypes the subtypes
     * @param members the members
     */
    public ClassModel(String name, ClassKind kind, String superclass, SubTypes subTypes,
                      List<MemberModel> members) {
        this(name, kind, superclass, subTypes, members, null, null);
    }
}
