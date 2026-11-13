package pt.dazz.bookmap.ctg;

/**
 * The ECI return codes that the transport translates. The values are equal to the values of
 * {@code com.ibm.ctg.client.ECIReturnCodes} in CICS TG V9.3.
 */
final class EciReturnCodes {

    static final int NO_ERROR = 0;
    static final int ERR_INVALID_DATA_LENGTH = -1;
    static final int ERR_NO_CICS = -3;
    static final int ERR_CICS_DIED = -4;
    /** Also {@code ECI_ERR_NO_REPLY}. */
    static final int ERR_REQUEST_TIMEOUT = -5;
    static final int ERR_RESPONSE_TIMEOUT = -6;
    static final int ERR_TRANSACTION_ABEND = -7;
    static final int ERR_SYSTEM_ERROR = -9;
    static final int ERR_RESOURCE_SHORTAGE = -16;
    static final int ERR_NO_SESSIONS = -17;
    static final int ERR_UNKNOWN_SERVER = -22;
    static final int ERR_NO_SYSTEMS = -26;
    static final int ERR_SECURITY_ERROR = -27;
    static final int ERR_MAX_SYSTEMS = -28;
    static final int ERR_MAX_SESSIONS = -29;
    static final int ERR_ROLLEDBACK = -30;

    private EciReturnCodes() {
    }

    /**
     * Gives the name of a return code.
     *
     * @param code the return code
     * @return the name of the IBM constant, or {@code UNKNOWN}
     */
    static String name(int code) {
        return switch (code) {
            case NO_ERROR -> "ECI_NO_ERROR";
            case ERR_INVALID_DATA_LENGTH -> "ECI_ERR_INVALID_DATA_LENGTH";
            case ERR_NO_CICS -> "ECI_ERR_NO_CICS";
            case ERR_CICS_DIED -> "ECI_ERR_CICS_DIED";
            case ERR_REQUEST_TIMEOUT -> "ECI_ERR_REQUEST_TIMEOUT";
            case ERR_RESPONSE_TIMEOUT -> "ECI_ERR_RESPONSE_TIMEOUT";
            case ERR_TRANSACTION_ABEND -> "ECI_ERR_TRANSACTION_ABEND";
            case ERR_SYSTEM_ERROR -> "ECI_ERR_SYSTEM_ERROR";
            case ERR_RESOURCE_SHORTAGE -> "ECI_ERR_RESOURCE_SHORTAGE";
            case ERR_NO_SESSIONS -> "ECI_ERR_NO_SESSIONS";
            case ERR_UNKNOWN_SERVER -> "ECI_ERR_UNKNOWN_SERVER";
            case ERR_NO_SYSTEMS -> "ECI_ERR_NO_SYSTEMS";
            case ERR_SECURITY_ERROR -> "ECI_ERR_SECURITY_ERROR";
            case ERR_MAX_SYSTEMS -> "ECI_ERR_MAX_SYSTEMS";
            case ERR_MAX_SESSIONS -> "ECI_ERR_MAX_SESSIONS";
            case ERR_ROLLEDBACK -> "ECI_ERR_ROLLEDBACK";
            default -> "UNKNOWN";
        };
    }
}
