package pt.dazz.bookmap.ctg;

/**
 * The reply of an ECI call.
 *
 * @param returnCode the ECI return code. {@code 0} means no error.
 * @param abendCode the abend code of the CICS transaction, or {@code null}
 * @param commarea the COMMAREA after the call
 */
public record EciReply(int returnCode, String abendCode, byte[] commarea) {
}
