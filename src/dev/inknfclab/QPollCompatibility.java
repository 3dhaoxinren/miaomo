package dev.inknfclab;

import java.io.IOException;
import java.lang.reflect.Method;

final class QPollCompatibility {
    enum Mode { VERIFIED_RF_SNAPSHOT, USER_OPTED_IN_NO_RF_SNAPSHOT }

    static Mode inspect(Class<?> adapter,boolean allowUnverifiedSnapshot,int nxpFeatureVersion)throws IOException{
        if(!has(adapter,"getState","int"))throw new IOException("NFC 服务缺少 getState，无法确认 NFC 状态。");
        boolean snapshot=has(adapter,"getRfDiscoverConfigurations");
        if(!snapshot&&!allowUnverifiedSnapshot)throw new IOException("RF config readback is unavailable; explicitly opt in while NFC is idle. No Q-Poll command was sent.");
        if(nxpFeatureVersion<=0)throw new IOException("NXP_MW feature version is unavailable or unsupported; this QTag command is not verified on the active NFC stack. No Q-Poll command was sent.");
        if(!has(adapter,"sendVendorNciMessage","int","int","int","int","[B"))throw new IOException("此 ROM 未暴露兼容的 vendor NCI 发送接口；未发送任何 Q-Poll 命令。");
        if(!has(adapter,"setReaderMode","void","android.os.IBinder","@IAppCallback","int","android.os.Bundle","java.lang.String")
            &&!has(adapter,"setReaderMode","void","android.os.IBinder","@IAppCallback","int","android.os.Bundle"))throw new IOException("此 ROM 的 reader-mode 接口与喵墨不兼容；未发送任何 Q-Poll 命令。");
        if(snapshot)return Mode.VERIFIED_RF_SNAPSHOT;
        if(!allowUnverifiedSnapshot)throw new IOException("此 ROM 不支持 RF 配置只读核对。请先确认 NFC 当前空闲，再到设备页手动启用‘无 RF 读回兼容模式’。\n未发送 Q-Poll 命令。");
        return snapshot?Mode.VERIFIED_RF_SNAPSHOT:Mode.USER_OPTED_IN_NO_RF_SNAPSHOT;
    }

    private static boolean has(Class<?> type,String name){
        return has(type,name,null,new String[0]);
    }
    private static boolean has(Class<?> type,String name,String returnType,String... signature){
        for(Method method:type.getMethods())if(method.getName().equals(name)){
            if(returnType!=null&&!method.getReturnType().getName().equals(returnType))continue;
            Class<?>[] parameters=method.getParameterTypes();if(parameters.length!=signature.length)continue;
            boolean matches=true;for(int i=0;i<parameters.length;i++)if(signature[i].startsWith("@")?!parameters[i].getSimpleName().equals(signature[i].substring(1)):!parameters[i].getName().equals(signature[i])){matches=false;break;}
            if(matches)return true;
        }
        return false;
    }
    private QPollCompatibility(){}
}
