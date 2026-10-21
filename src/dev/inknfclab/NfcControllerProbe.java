package dev.inknfclab;

import android.os.IBinder;
import dalvik.system.PathClassLoader;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/** Optional, read-only controller identification; absence of Xiaomi services is ordinary. */
final class NfcControllerProbe {
    static final class Info {
        String chip="unknown",firmware="unknown",source="unavailable";
        int nxpFeature=-1;
        final ArrayList<String> notes=new ArrayList<>();
        JSONObject json()throws Exception{return new JSONObject().put("chip",chip).put("firmware",firmware).put("nxp_feature",nxpFeature).put("source",source).put("notes",new org.json.JSONArray(notes));}
    }
    static Object service(String name)throws Exception{return Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);}
    static Object adapter()throws Exception{
        IBinder binder=(IBinder)service("nfc");
        if(binder==null)throw new java.io.IOException("系统 NFC 服务不可用");
        return Class.forName("android.nfc.INfcAdapter$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    static ClassLoader xiaomiLoader(){
        ArrayList<String> jars=new ArrayList<>();
        for(String p:Arrays.asList("/system_ext/framework/com.xiaomi.nfc.jar","/system/framework/com.xiaomi.nfc.jar","/product/framework/com.xiaomi.nfc.jar"))if(new File(p).isFile())jars.add(p);
        return jars.isEmpty()?ClassLoader.getSystemClassLoader():new PathClassLoader(String.join(File.pathSeparator,jars),ClassLoader.getSystemClassLoader());
    }
    private static Object get(Class<?> api,Object target,String name,Class<?>[] types,Object... args)throws Exception{return api.getMethod(name,types).invoke(target,args);}
    private static void read(Info info,Class<?> api,Object target,String source){
        try{Object v=get(api,target,"getChipId",new Class<?>[0]);if(v!=null&&!v.toString().trim().isEmpty()){info.chip=v.toString();info.source=source;}}catch(Exception e){info.notes.add(source+" getChipId: "+e.getClass().getSimpleName());}
        try{Object v=get(api,target,"getFwVersion",new Class<?>[0]);if(v!=null)info.firmware=v.toString();}catch(Exception e){info.notes.add(source+" getFwVersion: "+e.getClass().getSimpleName());}
        try{Object v=get(api,target,"getFeatureVersion",new Class<?>[]{String.class},"NXP_MW");if(v instanceof Number){info.nxpFeature=((Number)v).intValue();if(info.source.equals("unavailable"))info.source=source;}}catch(Exception e){info.notes.add(source+" NXP_MW: "+e.getClass().getSimpleName());}
    }
    static Info inspect(Object adapter,Class<?> api){
        Info info=new Info();
        // Direct runtime getters can identify a non-Xiaomi implementation without a brand whitelist.
        read(info,api,adapter,"INfcAdapter");
        try{
            IBinder binder=(IBinder)service("mi_nfc");
            if(binder!=null){
                ClassLoader loader=xiaomiLoader();Class<?> miApi=loader.loadClass("com.xiaomi.nfc.IMiNfcAdapter");
                Object mi=loader.loadClass("com.xiaomi.nfc.IMiNfcAdapter$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
                // Keep identity and feature evidence from the same service, rather than mixing two stacks.
                Info miInfo=new Info();read(miInfo,miApi,mi,"mi_nfc");
                if(!info.chip.equals("unknown")&&!miInfo.chip.equals("unknown")&&!info.chip.trim().equalsIgnoreCase(miInfo.chip.trim())){
                    Info conflict=new Info();conflict.source="conflicting_runtime_getters";conflict.notes.add("Controller getters disagree; only standard NFC will be attempted");return conflict;
                }
                if(!miInfo.source.equals("unavailable")&&(info.chip.equals("unknown")||!miInfo.chip.equals("unknown")))return miInfo;
                info.notes.addAll(miInfo.notes);
            }else info.notes.add("mi_nfc absent; standard NFC remains available");
        }catch(Exception e){info.notes.add("mi_nfc: "+e.getClass().getSimpleName());}
        return info;
    }
    static boolean stCallbackAvailable(Class<?> adapter){
        try{
            Class<?> api=Class.forName("android.nfc.INfcVendorNciCallback"),stub=Class.forName("android.nfc.INfcVendorNciCallback$Stub");
            for(Method m:api.getMethods())if((m.getName().equals("onVendorResponseReceived")||m.getName().equals("onVendorNciResponse"))&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{int.class,int.class,byte[].class})){
                stub.getDeclaredField("TRANSACTION_"+m.getName());
                adapter.getMethod("registerVendorExtensionCallback",api);adapter.getMethod("unregisterVendorExtensionCallback",api);return true;
            }
        }catch(Exception unavailable){}
        return false;
    }
    static JSONObject capabilities(boolean allowRf,boolean allowSt)throws Exception{
        Object adapter=adapter();Class<?> api=Class.forName("android.nfc.INfcAdapter");Info info=inspect(adapter,api);
        boolean stCallback=stCallbackAvailable(api);
        NfcCapabilities.Plan plan=NfcCapabilities.inspect(api,info.chip,info.nxpFeature,allowRf,allowSt,stCallback);
        return info.json().put("route",plan.route).put("reason",plan.reason).put("vendor_ready",plan.vendor())
            .put("scope","read-only interface inspection; not a radio or refresh test")
            .put("raw_vendor_api",QPollCompatibility.hasVendorSend(api)).put("reader_api",QPollCompatibility.hasReader(api))
            .put("rf_getter",QPollCompatibility.hasSnapshot(api)).put("st_callback",stCallback);
    }
    private NfcControllerProbe(){}
}
