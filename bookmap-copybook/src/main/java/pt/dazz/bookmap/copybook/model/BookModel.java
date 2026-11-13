package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes the classes that the generator writes for one copybook. The model is the input
 * of the Java generator. Its JSON form is stable, so other tools can keep and compare it.
 *
 * @param formatVersion the version of the JSON format
 * @param sourceName the file name of the copybook, without a directory
 * @param classes the classes, in source order
 * @param enums the enums from level 88 items, in source order
 */
public record BookModel(int formatVersion, String sourceName, List<ClassModel> classes,
                        List<EnumModel> enums) {

    /** The current version of the JSON format. */
    public static final int FORMAT_VERSION = 1;

    /**
     * Creates a model.
     *
     * @param formatVersion the format version
     * @param sourceName the source name
     * @param classes the classes
     * @param enums the enums
     */
    public BookModel {
        classes = List.copyOf(classes);
        enums = List.copyOf(enums);
    }

    /**
     * Creates a model without enums.
     *
     * @param formatVersion the format version
     * @param sourceName the source name
     * @param classes the classes
     */
    public BookModel(int formatVersion, String sourceName, List<ClassModel> classes) {
        this(formatVersion, sourceName, classes, List.of());
    }
}
