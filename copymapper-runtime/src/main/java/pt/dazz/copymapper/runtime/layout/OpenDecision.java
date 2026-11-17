package pt.dazz.copymapper.runtime.layout;

import pt.dazz.copymapper.copybook.spec.SourceLocation;

/**
 * A problem of a mapping spec that its author must decide before the runtime can use it.
 *
 * @param kind the kind
 * @param path the path of the item
 * @param location the place of the item in the source, or null
 * @param message the message
 */
public record OpenDecision(OpenDecisionKind kind, String path, SourceLocation location,
        String message) {

    /**
     * Writes the decision as {@code source:line:column: path: message}. Without a location, the
     * text starts with the path.
     *
     * @return the text
     */
    public String format() {
        String place = location == null ? ""
                : location.source() + ":" + location.line() + ":" + location.column() + ": ";
        return place + path + ": " + message;
    }
}
