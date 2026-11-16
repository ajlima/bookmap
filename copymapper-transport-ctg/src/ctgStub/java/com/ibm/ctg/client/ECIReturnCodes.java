// Test stub of the IBM CICS Transaction Gateway API. The signatures and the constant values come
// from the public CICS TG V9.3 Javadoc. This file is not published and contains no IBM code.
// Do not change a signature or a constant without a check against the IBM Javadoc.
package com.ibm.ctg.client;

/** Stub of the ECI return codes. */
public interface ECIReturnCodes {
    int ECI_NO_ERROR = 0;
    int ECI_ERR_INVALID_DATA_LENGTH = -1;
    int ECI_ERR_NO_CICS = -3;
    int ECI_ERR_CICS_DIED = -4;
    int ECI_ERR_REQUEST_TIMEOUT = -5;
    int ECI_ERR_NO_REPLY = -5;
    int ECI_ERR_RESPONSE_TIMEOUT = -6;
    int ECI_ERR_TRANSACTION_ABEND = -7;
    int ECI_ERR_SYSTEM_ERROR = -9;
    int ECI_ERR_RESOURCE_SHORTAGE = -16;
    int ECI_ERR_NO_SESSIONS = -17;
    int ECI_ERR_UNKNOWN_SERVER = -22;
    int ECI_ERR_NO_SYSTEMS = -26;
    int ECI_ERR_SECURITY_ERROR = -27;
    int ECI_ERR_MAX_SYSTEMS = -28;
    int ECI_ERR_MAX_SESSIONS = -29;
    int ECI_ERR_ROLLEDBACK = -30;
}
