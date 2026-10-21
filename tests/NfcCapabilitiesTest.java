package dev.inknfclab;

import android.os.IBinder;
import android.os.Bundle;
import java.util.List;

/** API fixtures exercise routing, not a particular phone or radio. */
public final class NfcCapabilitiesTest {
    interface IAppCallback {}
    interface Android12 {
        int getState();void setReaderMode(IBinder b,IAppCallback cb,int flags,Bundle options);
    }
    interface Nxp4 extends Android12 {
        int sendVendorNciMessage(int a,int b,int c,byte[] data);
    }
    interface Nxp5 {
        int getState();List<?> getRfDiscoverConfigurations();
        int sendVendorNciMessage(int a,int b,int c,byte[] data);
        void setReaderMode(IBinder b,IAppCallback cb,int flags,Bundle options,String pkg);
    }
    interface St4 extends Nxp4 {int pausePolling(long millis);void resumePolling();}
    interface WrongSnapshot extends Nxp4 {String getRfDiscoverConfigurations();}
    interface WrongVendor extends Android12 {byte[] sendVendorNciMessage(int a,int b,int c,byte[] data);}
    interface WrongReader {int getState();int sendVendorNciMessage(int a,int b,int c,byte[] data);void setReaderMode(Object b,Object cb,int flags,Bundle options);}
    static int checks;
    static void expect(Class<?> api,String chip,int feature,boolean rf,boolean st,boolean callback,String route)throws Exception{
        NfcCapabilities.Plan plan=NfcCapabilities.inspect(api,chip,feature,rf,st,callback);checks++;
        if(!route.equals(plan.route))throw new AssertionError(plan.route+": "+plan.reason);
        if(plan.reason==null||plan.reason.isEmpty())throw new AssertionError("missing explanation");
    }
    public static void main(String[] args)throws Exception{
        expect(Android12.class,"unknown",-1,false,false,false,NfcCapabilities.STANDARD);
        expect(Android12.class,"NXP",16,true,false,false,NfcCapabilities.STANDARD);
        expect(Nxp4.class,"NXP",16,false,false,false,NfcCapabilities.STANDARD);
        expect(Nxp4.class,"NXP",16,true,false,false,NfcCapabilities.NXP);
        expect(Nxp5.class,"NXP",16,false,false,false,NfcCapabilities.NXP);
        expect(Nxp5.class,"unknown",-1,true,true,true,NfcCapabilities.STANDARD);
        expect(St4.class,"ST54L",-1,false,false,true,NfcCapabilities.STANDARD);
        expect(St4.class,"ST54L",-1,false,true,true,NfcCapabilities.ST);
        expect(St4.class,"ST54L",-1,false,true,false,NfcCapabilities.STANDARD);
        expect(St4.class,"ST54J",16,true,true,true,NfcCapabilities.STANDARD);
        expect(Nxp4.class,"ST54L",16,true,true,true,NfcCapabilities.STANDARD);
        expect(WrongSnapshot.class,"NXP",16,false,false,false,NfcCapabilities.STANDARD);
        expect(WrongVendor.class,"NXP",16,true,false,false,NfcCapabilities.STANDARD);
        expect(WrongReader.class,"NXP",16,true,false,false,NfcCapabilities.STANDARD);
        if(NfcCapabilities.probeVendor(3,true,true)||NfcCapabilities.probeVendor(2,false,false)||NfcCapabilities.probeVendor(1,false,true))throw new AssertionError("standard route must not require shell");checks++;
        if(!NfcCapabilities.probeVendor(3,false,true)||!NfcCapabilities.probeVendor(4,true,true))throw new AssertionError("vendor preflight missing");checks++;
        System.out.println("PASS NfcCapabilitiesTest cases="+checks+": Android 12 four-argument API; five-argument API; standard without shell; NXP profile/opt-in; ST callback/restore prerequisites; unknown/wrong signatures fall back without vendor writes. API_FIXTURES_ONLY");
    }
}
