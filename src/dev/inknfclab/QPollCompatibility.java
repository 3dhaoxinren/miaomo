package dev.inknfclab;

import java.io.IOException;
import java.lang.reflect.Method;

final class QPollCompatibility {
    enum Mode { VERIFIED_RF_SNAPSHOT, USER_OPTED_IN_NO_RF_SNAPSHOT }

    static Mode inspect(Class<?> adapter,boolean allowUnverifiedSnapshot,int nxpFeatureVersion)throws IOException{
        if(!has(adapter,"getState","int"))throw new IOException("NFC 服务缺少 getState，无法确认 NFC 状态。");
        boolean snapshot=hasSnapshot(adapter);
        if(nxpFeatureVersion<=0)throw new IOException("未确认 NXP_MW 协议能力；未发送任何 Q-Poll 命令。");
        if(!hasVendorSend(adapter))throw new IOException("此 ROM 未暴露兼容的 vendor NCI 发送接口；未发送任何 Q-Poll 命令。");
        if(!hasReader(adapter))throw new IOException("此 ROM 的 reader-mode 接口与喵墨不兼容；未发送任何 Q-Poll 命令。");
        if(!snapshot&&!allowUnverifiedSnapshot)throw new IOException("此 ROM 缺少 RF 配置读回接口。可在设备页明确启用‘允许无 RF 配置读回兼容’后重试；未发送 Q-Poll 命令。");
        return snapshot?Mode.VERIFIED_RF_SNAPSHOT:Mode.USER_OPTED_IN_NO_RF_SNAPSHOT;
    }

    static boolean hasSnapshot(Class<?> api){
        try{return java.util.List.class.isAssignableFrom(api.getMethod("getRfDiscoverConfigurations").getReturnType());}
        catch(NoSuchMethodException absent){return false;}
    }
    static boolean hasVendorSend(Class<?> api){return has(api,"sendVendorNciMessage","int","int","int","int","[B");}
    static boolean hasReader(Class<?> api){return has(api,"setReaderMode","void","android.os.IBinder","@IAppCallback","int","android.os.Bundle","java.lang.String")||has(api,"setReaderMode","void","android.os.IBinder","@IAppCallback","int","android.os.Bundle");}

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
