package dev.inknfclab;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

/** Read-only planning. A usable API is not proof that a handset can wake the screen. */
final class NfcCapabilities {
    static final String STANDARD="standard_reader",NXP="nxp_qtag",ST="st54l_custom_frames";
    static final class Plan {
        final String route,reason,status;
        final String[] missing;
        Plan(String route,String reason){this(route,reason,"ready",new String[0]);}
        Plan(String route,String reason,String status,String[] missing){this.route=route;this.reason=reason;this.status=status;this.missing=missing;}
        boolean vendor(){return !STANDARD.equals(route);}
    }
    static Plan inspect(Class<?> adapter,String chip,int nxpFeature,boolean allowRf,boolean allowSt,boolean stCallback){
        try{
            NfcControllerProfile.Kind kind=NfcControllerProfile.select(chip,nxpFeature,allowSt);
            if(kind==NfcControllerProfile.Kind.NXP_QTAG){
                QPollCompatibility.Mode mode=QPollCompatibility.inspect(adapter,allowRf,nxpFeature);
                return new Plan(NXP,mode==QPollCompatibility.Mode.VERIFIED_RF_SNAPSHOT?"具备 NXP 专用寻卡接口；尚需验证标签、D1 和实屏刷新。":"NXP 无读回试验已启用；结束后无法核验 RF 配置完全恢复。");
            }
            if(!QPollCompatibility.hasReader(adapter))throw new IOException("缺少兼容的 ReaderMode 接口。");
            if(!QPollCompatibility.hasVendorSend(adapter))throw new IOException("缺少 vendor NCI 发送接口。");
            if(!stCallback||(!pollingControl(adapter,"pausePolling",long.class)&&!pollingControl(adapter,"pausePolling",int.class))||!pollingControl(adapter,"resumePolling"))
                throw new IOException("缺少 ST54L 回调或暂停/恢复轮询接口。");
            return new Plan(ST,"具备 ST54L 实验接口；运行时仍需权限检查、快照与读回验证。");
        }catch(IOException failure){
            String id=chip==null?"":chip.trim().toUpperCase(Locale.ROOT);
            String[] missing=missingInterfaces(adapter,id,nxpFeature,allowRf,stCallback);
            String status=id.equals("ST54L")&&!allowSt?"experiment_disabled":missing.length>0?"missing_interfaces":id.startsWith("ST")?"unadapted_controller":"unidentified_controller";
            return new Plan(STANDARD,failure.getMessage()+" 将使用标准 NFC；需要系统先激活为 ISO-DEP，标准模式不能代发 7A 唤醒短帧。",status,missing);
        }
    }
    private static String[] missingInterfaces(Class<?> api,String chip,int nxpFeature,boolean allowRf,boolean stCallback){
        ArrayList<String> missing=new ArrayList<>();
        if(!QPollCompatibility.hasVendorSend(api))missing.add("厂商 NFC 指令接口");
        if(!QPollCompatibility.hasReader(api))missing.add("专用读卡接口");
        if(chip.equals("ST54L")){
            if(!stCallback)missing.add("ST54L 响应回调接口");
            if((!pollingControl(api,"pausePolling",long.class)&&!pollingControl(api,"pausePolling",int.class))||!pollingControl(api,"resumePolling"))missing.add("ST54L 暂停/恢复轮询接口");
        }else if(!chip.startsWith("ST")&&nxpFeature>0){
            try{if(api.getMethod("getState").getReturnType()!=int.class)missing.add("NFC 状态读取接口");}catch(NoSuchMethodException e){missing.add("NFC 状态读取接口");}
            if(!QPollCompatibility.hasSnapshot(api)&&!allowRf)missing.add("RF 配置读回接口");
        }
        return missing.toArray(new String[0]);
    }
    private static boolean pollingControl(Class<?> api,String name,Class<?>... arguments){
        try{Method m=api.getMethod(name,arguments);return m.getReturnType()==void.class||m.getReturnType()==int.class;}
        catch(NoSuchMethodException absent){return false;}
    }
    static boolean probeVendor(int mode,boolean standardOnly,boolean shellConnected){
        return shellConnected&&(mode==4||!standardOnly&&(mode==2||mode==3));
    }
    private NfcCapabilities(){}
}
