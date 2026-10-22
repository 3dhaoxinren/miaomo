package dev.inknfclab;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34)
public class ControllerProbeTest {
    public static final class StWithoutFirmwareGetter {
        public String getChipId(){return "ST54L";}
    }
    public static final class DirectNxp {
        int writes;
        public String getChipId(){return "NXP";}
        public String getFwVersion(){return "test-fixture";}
        public int getFeatureVersion(String name){assertEquals("NXP_MW",name);return 16;}
        public void setConfiguration(){writes++;}
    }
    @Test public void firmwareGetterIsOptionalAndXiaomiServiceIsNotRequired(){
        NfcControllerProbe.Info info=NfcControllerProbe.inspect(new StWithoutFirmwareGetter(),StWithoutFirmwareGetter.class);
        assertEquals("ST54L",info.chip);assertEquals("unknown",info.firmware);assertEquals(-1,info.nxpFeature);assertEquals("INfcAdapter",info.source);
    }
    @Test public void directProfileGettersAreReadOnly(){
        DirectNxp target=new DirectNxp();NfcControllerProbe.Info info=NfcControllerProbe.inspect(target,DirectNxp.class);
        assertEquals(16,info.nxpFeature);assertEquals("NXP",info.chip);assertEquals("INfcAdapter",info.source);assertEquals(0,target.writes);
    }
    @Test public void absentVendorIdentificationIsAnUnknownProfile(){
        NfcControllerProbe.Info info=NfcControllerProbe.inspect(new Object(),Object.class);
        assertEquals("unknown",info.chip);assertEquals(-1,info.nxpFeature);assertEquals("unavailable",info.source);
    }
    @Test public void interfaceInventoryPreservesSignaturesWithoutInvokingThem()throws Exception{
        DirectNxp target=new DirectNxp();org.json.JSONObject inventory=NfcControllerProbe.interfaceInventory(target.getClass());
        assertEquals(DirectNxp.class.getName(),inventory.getString("class"));assertFalse(inventory.getBoolean("truncated"));
        assertTrue(inventory.getJSONArray("signatures").toString().contains("getFeatureVersion(java.lang.String)"));assertEquals(0,target.writes);
    }
}
