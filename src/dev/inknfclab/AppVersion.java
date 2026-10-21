package dev.inknfclab;

import android.content.Context;

final class AppVersion {
    static String name(Context context){
        try{
            String value=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;
            return value==null||value.isEmpty()?"未知版本":value;
        }catch(Exception missing){return "未知版本";}
    }
    private AppVersion(){}
}
