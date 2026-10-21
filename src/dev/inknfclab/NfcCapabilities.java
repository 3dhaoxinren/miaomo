package dev.inknfclab;

import java.io.IOException;
import java.lang.reflect.Method;

/** Read-only planning. A usable API is not proof that a handset can wake the screen. */
final class NfcCapabilities {
    static final String STANDARD="standard_reader",NXP="nxp_qtag",ST="st54l_custom_frames";
    static final class Plan {
        final String route,reason;
        Plan(String route,String reason){this.route=route;this.reason=reason;}
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
            return new Plan(STANDARD,failure.getMessage()+" 将使用标准 NFC；需要系统先激活为 ISO-DEP，标准模式不能代发 7A 唤醒短帧。");
        }
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
