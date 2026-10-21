package dev.inknfclab;

import android.os.Bundle;
import android.os.IBinder;
import java.io.IOException;

public final class QPollCompatibilityTest {
    interface IAppCallback {}
    interface SnapshotApi {
        int getState();
        java.util.List<?> getRfDiscoverConfigurations();
        int sendVendorNciMessage(int gid,int oid,int length,byte[] payload);
        void setReaderMode(IBinder token,IAppCallback callback,int flags,Bundle extras,String packageName);
    }
    interface NoSnapshotApi {
        int getState();
        int sendVendorNciMessage(int gid,int oid,int length,byte[] payload);
        void setReaderMode(IBinder token,IAppCallback callback,int flags,Bundle extras,String packageName);
    }
    interface NoRawSendApi {
        int getState();
        java.util.List<?> getRfDiscoverConfigurations();
        void setReaderMode(IBinder token,IAppCallback callback,int flags,Bundle extras,String packageName);
    }
    interface NoReaderApi {
        int getState();
        java.util.List<?> getRfDiscoverConfigurations();
        int sendVendorNciMessage(int gid,int oid,int length,byte[] payload);
    }
    interface WrongReaderModeSignature {
        int getState();
        int sendVendorNciMessage(int a,int b,int c,byte[] d);
        void setReaderMode(IBinder a,Object b,int c,Bundle d);
    }
    private static void require(boolean value){if(!value)throw new AssertionError();}
    private static void rejects(Class<?> api,boolean allow,int nxpFeature,String text){
        try{QPollCompatibility.inspect(api,allow,nxpFeature);}
        catch(IOException expected){require(expected.getMessage().contains(text));return;}
        throw new AssertionError("Expected refusal for "+api.getName());
    }
    public static void main(String[] args)throws Exception{
        require(QPollCompatibility.inspect(SnapshotApi.class,false,16)==QPollCompatibility.Mode.VERIFIED_RF_SNAPSHOT);
        rejects(NoSnapshotApi.class,false,16,"缺少 RF");
        require(QPollCompatibility.inspect(NoSnapshotApi.class,true,16)==QPollCompatibility.Mode.USER_OPTED_IN_NO_RF_SNAPSHOT);
        rejects(NoSnapshotApi.class,true,-1,"NXP_MW");
        rejects(NoRawSendApi.class,true,16,"vendor NCI");
        rejects(NoReaderApi.class,true,16,"reader-mode");
        rejects(WrongReaderModeSignature.class,true,16,"reader-mode");
        System.out.println("PASS QPollCompatibilityTest checks=7: positive NXP profile, opt-in RF readback fallback and exact Binder contracts");
    }
}
