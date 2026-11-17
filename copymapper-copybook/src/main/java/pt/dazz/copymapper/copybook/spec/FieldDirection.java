package pt.dazz.copymapper.copybook.spec;

/** Says in which direction a field moves between the request and the response. */
public enum FieldDirection {
    /** The field is in the request only. */
    INPUT,
    /** The field is in the response only. */
    OUTPUT,
    /** The field is in the request and in the response. */
    INPUT_OUTPUT,
    /** The field is not in the JSON. */
    IGNORE,
    /** The field is derived by the runtime. */
    DERIVED;

    /** Tells if the field is in the request. @return true for INPUT and INPUT_OUTPUT */
    public boolean inRequest() {
        return this == INPUT || this == INPUT_OUTPUT;
    }

    /** Tells if the field is in the response. @return true for OUTPUT and INPUT_OUTPUT */
    public boolean inResponse() {
        return this == OUTPUT || this == INPUT_OUTPUT;
    }
}
