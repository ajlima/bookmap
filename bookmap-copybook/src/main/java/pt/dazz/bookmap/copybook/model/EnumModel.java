package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes one generated enum: the level 88 items of one elementary item.
 *
 * @param name the simple enum name
 * @param cobolName the COBOL name of the elementary item
 * @param constants the constants, in source order
 */
public record EnumModel(String name, String cobolName, List<EnumConstant> constants) {

    /**
     * Creates an enum model.
     *
     * @param name the name
     * @param cobolName the COBOL name
     * @param constants the constants
     */
    public EnumModel {
        constants = List.copyOf(constants);
    }
}
