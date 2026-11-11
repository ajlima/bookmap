package pt.dazz.bookmap.convert;

/**
 * Marker that means "no custom converter". Do not use it as a real converter.
 */
public final class NoConverter implements FieldConverter<Object> {

    /**
     * Creates the marker. The annotation default refers to the class only.
     */
    public NoConverter() {
        // Marker class: no state.
    }

    /**
     * Throws always. This class is a marker only.
     *
     * @param bytes not used
     * @param context not used
     * @return nothing
     * @throws UnsupportedOperationException always
     */
    @Override
    public Object decode(byte[] bytes, FieldContext context) {
        throw new UnsupportedOperationException("NoConverter is a marker only");
    }

    /**
     * Throws always. This class is a marker only.
     *
     * @param value not used
     * @param context not used
     * @return nothing
     * @throws UnsupportedOperationException always
     */
    @Override
    public byte[] encode(Object value, FieldContext context) {
        throw new UnsupportedOperationException("NoConverter is a marker only");
    }
}
