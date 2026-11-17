package pt.dazz.copymapper.copybook.spec;

import java.util.Objects;

/**
 * Describes how a table is read.
 *
 * @param mode the mode
 * @param path the path of the counter or key field; mandatory for COUNT and KEY, else null
 */
public record TableSpec(TableMode mode, String path) {

    /** Checks that the path matches the mode. */
    public TableSpec {
        Objects.requireNonNull(mode, "mode");
        boolean needsPath = mode == TableMode.COUNT || mode == TableMode.KEY;
        if (needsPath != (path != null)) {
            throw new IllegalArgumentException(needsPath
                    ? "table mode " + mode + " needs a path"
                    : "table mode " + mode + " must not have a path");
        }
    }
}
