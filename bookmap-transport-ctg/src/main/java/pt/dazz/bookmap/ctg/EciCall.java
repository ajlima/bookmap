package pt.dazz.bookmap.ctg;

import java.util.Objects;

/**
 * One synchronous ECI call with a COMMAREA.
 *
 * @param server the name of the CICS server in the CTG configuration, or {@code null} for the
 *     default server
 * @param userid the CICS user ID, or {@code null}
 * @param password the CICS password, or {@code null}
 * @param program the name of the CICS program
 * @param transaction the name of the CICS transaction, or {@code null} for the mirror transaction
 * @param commarea the request bytes. They can be shorter than {@code commareaLength}.
 * @param commareaLength the size of the COMMAREA that CICS gets, in bytes
 * @param timeoutSeconds the ECI timeout in seconds, from 0 to 32767. {@code 0} means no ECI
 *     timeout.
 */
public record EciCall(String server, String userid, String password, String program,
                      String transaction, byte[] commarea, int commareaLength,
                      int timeoutSeconds) {

    /**
     * Creates a call and checks the values.
     *
     * @throws NullPointerException if the program or the COMMAREA is {@code null}
     * @throws IllegalArgumentException if the request bytes are longer than the COMMAREA length,
     *     or if the timeout is not from 0 to 32767
     */
    public EciCall {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(commarea, "commarea");
        if (commarea.length > commareaLength) {
            throw new IllegalArgumentException("The request has " + commarea.length
                + " bytes, but commareaLength is " + commareaLength);
        }
        if (timeoutSeconds < 0 || timeoutSeconds > Short.MAX_VALUE) {
            throw new IllegalArgumentException("timeoutSeconds must be from 0 to "
                + Short.MAX_VALUE + ": " + timeoutSeconds);
        }
    }

    /**
     * Gives a text for logs. The text does not contain the password or the request bytes.
     *
     * @return the text
     */
    @Override
    public String toString() {
        return "EciCall[server=" + server + ", userid=" + userid
            + ", password=" + (password == null ? "null" : "***")
            + ", program=" + program + ", transaction=" + transaction
            + ", commareaLength=" + commareaLength + ", timeoutSeconds=" + timeoutSeconds + "]";
    }
}
