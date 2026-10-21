package dev.inknfclab;

import java.io.IOException;
import java.util.Locale;

final class NfcControllerProfile {
    enum Kind { NXP_QTAG, ST54L_FRAMES }
    static Kind select(String chip,int nxpFeature,boolean stOptIn)throws IOException{
        String id=chip==null?"":chip.trim().toUpperCase(Locale.ROOT);
        if(id.equals("ST54L")){
            if(!stOptIn)throw new IOException("检测到 ST54L。请在设备页启用‘ST54L 适配试验’，先测试检测屏幕。尚未在小米 15 Pro 实机验证。");
            return Kind.ST54L_FRAMES;
        }
        if(id.startsWith("ST"))throw new IOException("未适配的 ST 控制器 "+id+"；未发送任何 vendor 配置命令。");
        if(nxpFeature>0)return Kind.NXP_QTAG;
        throw new IOException("未识别的 NFC 协议能力，chip="+id+", NXP_MW="+nxpFeature+"；未发送任何 vendor 配置命令。");
    }
    private NfcControllerProfile(){}
}
