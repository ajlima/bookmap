// Test stub of the IBM CICS Transaction Gateway API. The signatures and the constant values come
// from the public CICS TG V9.3 Javadoc. This file is not published and contains no IBM code.
// Do not change a signature or a constant without a check against the IBM Javadoc.
package com.ibm.ctg.client;

/** Stub of an ECI request. The methods that are not in the IBM API are for tests only. */
@SuppressWarnings({"checkstyle:MemberName", "checkstyle:VisibilityModifier"})
public class ECIRequest extends GatewayRequest implements ECIReturnCodes {

    public static final int ECI_SYNC = 1;
    public static final int ECI_NO_EXTEND = 0;

    public int Call_Type;
    public String Server;
    public String Userid;
    public String Password;
    public String Program;
    public String Transid;
    public byte[] Commarea;
    public int Commarea_Length;
    public int Extend_Mode;
    public int Luw_Token;
    public String Abend_Code;

    private short eciTimeout;
    private int outboundLength = -1;
    private int inboundLength = -1;
    private int rc;

    public ECIRequest(int iCallType, String strServer, String strUserid, String strPassword,
                      String strProgram, String strTransid, byte[] abytCommarea,
                      int iCommareaLength, int iExtendMode, int iLuwToken) {
        Call_Type = iCallType;
        Server = strServer;
        Userid = strUserid;
        Password = strPassword;
        Program = strProgram;
        Transid = strTransid;
        Commarea = abytCommarea;
        Commarea_Length = iCommareaLength;
        Extend_Mode = iExtendMode;
        Luw_Token = iLuwToken;
    }

    public void setECITimeout(short sTimeout) {
        eciTimeout = sTimeout;
    }

    public void setCommareaOutboundLength(int iOut) {
        outboundLength = iOut;
    }

    public void setCommareaInboundLength(int iIn) {
        inboundLength = iIn;
    }

    public int getRc() {
        return rc;
    }

    /** Test only: the ECI timeout that the adapter set. */
    public short eciTimeout() {
        return eciTimeout;
    }

    /** Test only: the outbound COMMAREA length that the adapter set, or -1. */
    public int outboundLength() {
        return outboundLength;
    }

    /** Test only: the inbound COMMAREA length that the adapter set, or -1. */
    public int inboundLength() {
        return inboundLength;
    }

    /** Test only: sets the reply of the gateway. */
    public void reply(int returnCode, String abendCode) {
        rc = returnCode;
        Abend_Code = abendCode;
    }
}
