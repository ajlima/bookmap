package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.ctg.client.ECIReturnCodes;
import org.junit.jupiter.api.Test;

class EciReturnCodesTest {

    @Test
    void constantsMatchTheIbmApi() {
        assertThat(EciReturnCodes.NO_ERROR).isEqualTo(ECIReturnCodes.ECI_NO_ERROR);
        assertThat(EciReturnCodes.ERR_INVALID_DATA_LENGTH).isEqualTo(ECIReturnCodes.ECI_ERR_INVALID_DATA_LENGTH);
        assertThat(EciReturnCodes.ERR_NO_CICS).isEqualTo(ECIReturnCodes.ECI_ERR_NO_CICS);
        assertThat(EciReturnCodes.ERR_CICS_DIED).isEqualTo(ECIReturnCodes.ECI_ERR_CICS_DIED);
        assertThat(EciReturnCodes.ERR_REQUEST_TIMEOUT).isEqualTo(ECIReturnCodes.ECI_ERR_REQUEST_TIMEOUT);
        assertThat(EciReturnCodes.ERR_REQUEST_TIMEOUT).isEqualTo(ECIReturnCodes.ECI_ERR_NO_REPLY);
        assertThat(EciReturnCodes.ERR_RESPONSE_TIMEOUT).isEqualTo(ECIReturnCodes.ECI_ERR_RESPONSE_TIMEOUT);
        assertThat(EciReturnCodes.ERR_TRANSACTION_ABEND).isEqualTo(ECIReturnCodes.ECI_ERR_TRANSACTION_ABEND);
        assertThat(EciReturnCodes.ERR_SYSTEM_ERROR).isEqualTo(ECIReturnCodes.ECI_ERR_SYSTEM_ERROR);
        assertThat(EciReturnCodes.ERR_RESOURCE_SHORTAGE).isEqualTo(ECIReturnCodes.ECI_ERR_RESOURCE_SHORTAGE);
        assertThat(EciReturnCodes.ERR_NO_SESSIONS).isEqualTo(ECIReturnCodes.ECI_ERR_NO_SESSIONS);
        assertThat(EciReturnCodes.ERR_UNKNOWN_SERVER).isEqualTo(ECIReturnCodes.ECI_ERR_UNKNOWN_SERVER);
        assertThat(EciReturnCodes.ERR_NO_SYSTEMS).isEqualTo(ECIReturnCodes.ECI_ERR_NO_SYSTEMS);
        assertThat(EciReturnCodes.ERR_SECURITY_ERROR).isEqualTo(ECIReturnCodes.ECI_ERR_SECURITY_ERROR);
        assertThat(EciReturnCodes.ERR_MAX_SYSTEMS).isEqualTo(ECIReturnCodes.ECI_ERR_MAX_SYSTEMS);
        assertThat(EciReturnCodes.ERR_MAX_SESSIONS).isEqualTo(ECIReturnCodes.ECI_ERR_MAX_SESSIONS);
        assertThat(EciReturnCodes.ERR_ROLLEDBACK).isEqualTo(ECIReturnCodes.ECI_ERR_ROLLEDBACK);
    }

    @Test
    void namesTheKnownCodes() {
        assertThat(EciReturnCodes.name(0)).isEqualTo("ECI_NO_ERROR");
        assertThat(EciReturnCodes.name(-1)).isEqualTo("ECI_ERR_INVALID_DATA_LENGTH");
        assertThat(EciReturnCodes.name(-3)).isEqualTo("ECI_ERR_NO_CICS");
        assertThat(EciReturnCodes.name(-4)).isEqualTo("ECI_ERR_CICS_DIED");
        assertThat(EciReturnCodes.name(-5)).isEqualTo("ECI_ERR_REQUEST_TIMEOUT");
        assertThat(EciReturnCodes.name(-6)).isEqualTo("ECI_ERR_RESPONSE_TIMEOUT");
        assertThat(EciReturnCodes.name(-7)).isEqualTo("ECI_ERR_TRANSACTION_ABEND");
        assertThat(EciReturnCodes.name(-9)).isEqualTo("ECI_ERR_SYSTEM_ERROR");
        assertThat(EciReturnCodes.name(-16)).isEqualTo("ECI_ERR_RESOURCE_SHORTAGE");
        assertThat(EciReturnCodes.name(-17)).isEqualTo("ECI_ERR_NO_SESSIONS");
        assertThat(EciReturnCodes.name(-22)).isEqualTo("ECI_ERR_UNKNOWN_SERVER");
        assertThat(EciReturnCodes.name(-26)).isEqualTo("ECI_ERR_NO_SYSTEMS");
        assertThat(EciReturnCodes.name(-27)).isEqualTo("ECI_ERR_SECURITY_ERROR");
        assertThat(EciReturnCodes.name(-28)).isEqualTo("ECI_ERR_MAX_SYSTEMS");
        assertThat(EciReturnCodes.name(-29)).isEqualTo("ECI_ERR_MAX_SESSIONS");
        assertThat(EciReturnCodes.name(-30)).isEqualTo("ECI_ERR_ROLLEDBACK");
    }

    @Test
    void unknownCodeHasUnknownName() {
        assertThat(EciReturnCodes.name(-999)).isEqualTo("UNKNOWN");
    }
}
