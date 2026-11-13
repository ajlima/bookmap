package pt.dazz.bookmap.ctg;

import java.util.Objects;

/**
 * The CICS destination of one operation: a program and an optional transaction.
 *
 * @param program the name of the CICS program, 1 to 8 characters
 * @param transaction the name of the CICS transaction, 1 to 4 characters, or {@code null}. If it
 *     is {@code null}, the transport uses its default transaction.
 */
public record CtgRoute(String program, String transaction) {

    /**
     * Creates a route and checks the values.
     *
     * @throws NullPointerException if the program is {@code null}
     * @throws IllegalArgumentException if a name is blank or too long
     */
    public CtgRoute {
        Objects.requireNonNull(program, "program");
        checkName("program", program, 8);
        if (transaction != null) {
            checkName("transaction", transaction, 4);
        }
    }

    static void checkName(String kind, String value, int maxLength) {
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("The " + kind + " name must have 1 to " + maxLength
                + " characters and must not be blank: '" + value + "'");
        }
    }
}
