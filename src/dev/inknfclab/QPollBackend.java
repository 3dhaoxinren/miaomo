package dev.inknfclab;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.os.*;
import org.json.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/** Shell UID 2000 owns reader, callback and IsoDep; no root or NFC config files. */
final class QPollBackend {
    interface Listener { void event(String name,String value); }
    private final Listener listener;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private Object adapter,callback;
    private Class<?> adapterType;
    private StVendorSession stSession;
    private String controllerProfile="unselected";
    private boolean stConfigured,stRestoreFailed;
    private Binder readerToken;
    private IBinder owner;
    private IBinder.DeathRecipient death;
    private volatile IsoDep iso;
    private volatile Tag tag;
    private volatile boolean running,qTouched,readerTouched,tagTaken;
    private volatile boolean qSent,qAccepted;
    private volatile boolean rfSnapshotAvailable;
    private volatile String error="",before="",after="",restore="not_started";
    private volatile long generation;
    private volatile int timeout=3000,maxLength;
    private int discoveredCode,lostCode=-1;

    QPollBackend(Listener listener) { this.listener=listener; }
    private void event(String type,String text) { listener.event(type,text); }
    private Object invoke(String name,Class<?>[] types,Object... args) throws Exception {
        try { return adapterType.getMethod(name,types).invoke(adapter,args); }
        catch(InvocationTargetException e) {
            Throwable cause=e.getCause(); if(cause instanceof Exception) throw (Exception)cause; throw e;
        }
    }
    private String rfConfig() throws Exception {
        Object value=invoke("getRfDiscoverConfigurations",new Class<?>[0]);
        if(!(value instanceof List<?>)) throw new IOException("RF discovery getter did not return a list");
        ArrayList<String> rows=new ArrayList<>();
        for(Object item:(List<?>)value) {
            int mode=((Number)item.getClass().getMethod("getTechnologyMode").invoke(item)).intValue()&255;
            int frequency=((Number)item.getClass().getMethod("getFrequency").invoke(item)).intValue();
            rows.add(String.format(Locale.ROOT,"%02X:%d",mode,frequency));
        }
        Collections.sort(rows);return rows.toString();
    }

    private Object[] controllerInfo() throws Exception {
        NfcControllerProbe.Info info=NfcControllerProbe.inspect(adapter,adapterType);
        event("controller_profile",info.json().toString());
        return new Object[]{info.chip,info.firmware,info.nxpFeature};
    }
    private Method readerMethod() throws Exception {
        Class<?> callbackType=Class.forName("android.nfc.IAppCallback");
        try{return adapterType.getMethod("setReaderMode",IBinder.class,callbackType,int.class,Bundle.class,String.class);}
        catch(NoSuchMethodException older){return adapterType.getMethod("setReaderMode",IBinder.class,callbackType,int.class,Bundle.class);}
    }
    private int qtag(int enabled) throws Exception {
        return ((Number)invoke("sendVendorNciMessage",new Class<?>[]{int.class,int.class,int.class,byte[].class},1,15,112,new byte[]{0x31,(byte)enabled})).intValue();
    }
    private void reader(int flags) throws Exception {
        Bundle extras=new Bundle();extras.putInt("presence",5000);
        Method method=readerMethod();
        Object[] args=method.getParameterTypes().length==5?new Object[]{readerToken,callback,flags,extras,"com.android.shell"}:new Object[]{readerToken,callback,flags,extras};
        try{method.invoke(adapter,args);}catch(InvocationTargetException e){Throwable failure=e.getTargetException();if(failure instanceof Exception)throw (Exception)failure;throw e;}
    }
    synchronized String start(IBinder caller,boolean allowUnverifiedSnapshot,boolean allowSt54l) throws Exception {
        if(running || qTouched || readerTouched || stSession!=null || stRestoreFailed) throw new IllegalStateException("Q-Poll session already active; stop it first");
        if(caller==null)throw new IllegalArgumentException("Caller lifecycle binder is required");
        if(android.os.Process.myUid()!=2000) throw new SecurityException("Q-Poll requires Shizuku shell UID 2000");
        controllerProfile="unselected";stConfigured=false;tag=null;iso=null;tagTaken=false;qSent=false;qAccepted=false;rfSnapshotAvailable=false;before="";after="";error="";restore="pending";
        Object service=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"nfc");
        if(service==null) throw new IOException("NFC 服务不可用");
        adapterType=Class.forName("android.nfc.INfcAdapter");
        adapter=Class.forName("android.nfc.INfcAdapter$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
        if(((Number)invoke("getState",new Class<?>[0])).intValue()!=3) throw new IOException("请先打开系统 NFC 并解锁手机");
        Object[] controller=controllerInfo();
        NfcControllerProfile.Kind profile=NfcControllerProfile.select((String)controller[0],(Integer)controller[2],allowSt54l);
        readerMethod(); // Resolve the real 4/5 argument signature before any vendor write.
        if(profile==NfcControllerProfile.Kind.ST54L_FRAMES){
            controllerProfile="st54l_custom_frames";
            before="st_pending";restore="pending";
        }else{
            controllerProfile="nxp_qtag";
            QPollCompatibility.Mode compatibility=QPollCompatibility.inspect(adapterType,allowUnverifiedSnapshot,(Integer)controller[2]);
            rfSnapshotAvailable=compatibility==QPollCompatibility.Mode.VERIFIED_RF_SNAPSHOT;
            if(rfSnapshotAvailable){
                before=rfConfig();event("rf_before",before);
                if(before.contains("71:"))throw new IOException("已有 Q-Poll 寻卡会话，未接管或改动它");
            }else{before="unavailable";after="unavailable";restore="not_verifiable";event("rf_snapshot_unavailable","explicit opt-in; NFC-idle acknowledged");}
        }
        readerToken=new Binder();
        Class<?> cbType=Class.forName("android.nfc.IAppCallback"),stub=Class.forName("android.nfc.IAppCallback$Stub");
        Field field=stub.getDeclaredField("TRANSACTION_onTagDiscovered");field.setAccessible(true);discoveredCode=field.getInt(null);
        try { field=stub.getDeclaredField("TRANSACTION_onTagLost");field.setAccessible(true);lostCode=field.getInt(null); } catch(NoSuchFieldException ignored) { }
        final long run=++generation;
        Binder callbackBinder=new Binder() {
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
                if(code==INTERFACE_TRANSACTION) { if(reply!=null) reply.writeString("android.nfc.IAppCallback"); return true; }
                if(code!=discoveredCode && code!=lostCode) return super.onTransact(code,data,reply,flags);
                data.enforceInterface("android.nfc.IAppCallback");
                Tag found=data.readInt()==0 ? null : Tag.CREATOR.createFromParcel(data);
                if(code==discoveredCode && found!=null && running && generation==run && !tagTaken) {
                    tag=found;event("tag_callback",Protocol.hex(found.getId())+" "+Arrays.toString(found.getTechList()));
                } else if(code==lostCode) event("tag_lost","callback");
                if(reply!=null) reply.writeNoException(); return true;
            }
        };
        callback=Proxy.newProxyInstance(cbType.getClassLoader(),new Class<?>[]{cbType},(obj,method,args) -> {
            if(method.getName().equals("asBinder"))return callbackBinder;
            if(method.getName().equals("toString"))return "InkNfcShellCallback";
            if(method.getName().equals("hashCode"))return System.identityHashCode(obj);
            if(method.getName().equals("equals"))return obj==args[0];return null;
        });
        owner=caller;death=() -> { new Thread(() -> stop("app_binder_died"),"q-cleanup").start(); };
        owner.linkToDeath(death,0);
        running=true;
        try {
            if(profile==NfcControllerProfile.Kind.ST54L_FRAMES){
                stSession=new StVendorSession(adapter,adapterType,(String)controller[0],(String)controller[1],listener);
                stSession.initialize();
                byte[] original=stSession.start();stConfigured=true;before="st_frames:"+Protocol.hex(original);after="st_frames:"+Protocol.hex(Protocol.polling7A());
                event("st_frames_verified",after+"; this confirms controller configuration, not tag activation or screen refresh");
            }else{
                qTouched=true;int result=qtag(1);event("qtag_on_api",String.valueOf(result));
                if(result!=0)throw new IOException("系统拒绝 QTag，API="+result);
            }
            readerTouched=true;reader(0x81);event("reader_on","shell UID 2000, flags=0x81");
            timer.postDelayed(() -> { if(generation==run && running && tag==null) stop("60s_no_tag"); },60000);
            timer.postDelayed(() -> { if(generation==run && running) stop("300s_hard_deadline"); },300000);
            return status();
        } catch(Exception e) { error=e.toString();stop("start_error");throw e; }
    }
    void observe(String line) {
        if(!running)return;
        int marker=line.indexOf("> ");if(marker<0)return;
        String packet=line.substring(marker+2).trim().toUpperCase(Locale.ROOT);
        if(line.contains("NxpNciX") && packet.startsWith("2103")) {
            event("rf_tx",packet);
            if(NciDiscovery.hasQPoll(packet)) {qSent=true;qAccepted=false;event("q_discovery_sent","71");}
        }
        if(line.contains("NxpNciR") && packet.startsWith("410301") && packet.length()>=8 && qSent) {
            qAccepted=packet.substring(6,8).equals("00");event("q_discovery_response",packet);
            if(!qAccepted)error="Q-Poll 被控制器拒绝："+packet;
        }
        if(line.contains("NxpNciR") && packet.startsWith("4F7001") && packet.length()>=8 && !packet.substring(6,8).equals("00")) {
            error="QTag 控制器拒绝："+packet;event("qtag_rejected",error);
            new Thread(() -> stop("controller_rejected"),"q-reject-cleanup").start();
        }
        if(line.contains("NxpNciR") && (packet.startsWith("6103") || packet.startsWith("6105")))event("rf_rx",packet);
    }
    String status() {
        try {return new JSONObject().put("controller_profile",controllerProfile).put("st_frames_configured",stConfigured).put("running",running).put("tag_ready",tag!=null).put("q_discovery_sent",qSent).put("q_discovery_accepted",qAccepted).put("error",error).put("rf_before",before).put("rf_after",after).put("restore",restore).toString();}
        catch(JSONException e) {return "{}";}
    }
    String connect() throws Exception {
        if(!running || tag==null)throw new IOException("尚未收到标签回调");
        Tag found=tag;tagTaken=true;
        IsoDep channel=IsoDep.get(found);if(channel==null)throw new IOException("标签没有 IsoDep");
        iso=channel;channel.setTimeout(timeout);long start=SystemClock.elapsedRealtime();channel.connect();
        if(!running){channel.close();throw new IOException("会话已停止");}
        maxLength=channel.getMaxTransceiveLength();
        JSONObject info=new JSONObject().put("uid",Protocol.hex(found.getId())).put("tech",new JSONArray(Arrays.asList(found.getTechList())))
            .put("historical",Protocol.hex(channel.getHistoricalBytes())).put("hi_layer",Protocol.hex(channel.getHiLayerResponse()))
            .put("max_transceive",maxLength).put("extended_apdu",channel.isExtendedLengthApduSupported()).put("connect_ms",SystemClock.elapsedRealtime()-start);
        NfcA a=NfcA.get(found);if(a!=null)info.put("atqa",Protocol.hex(a.getAtqa())).put("sak",a.getSak()&65535);
        event("iso_connected",info.toString());return info.toString();
    }
    byte[] transceive(byte[] command) throws Exception {
        IsoDep opened=iso;if(!running || opened==null || !opened.isConnected())throw new IOException("Q-Poll 通道未连接或已取消");
        if(command==null || command.length==0 || command.length>maxLength)throw new IOException("APDU 长度无效");
        return opened.transceive(command);
    }
    void timeout(int milliseconds) {
        if(milliseconds<100 || milliseconds>60000)throw new IllegalArgumentException("timeout");
        timeout=milliseconds;IsoDep opened=iso;if(opened!=null)opened.setTimeout(milliseconds);
    }
    synchronized String stop(String why) {
        running=false;++generation;
        IsoDep opened=iso;iso=null;if(opened!=null)try {opened.close();}catch(Exception e){event("close_error",e.toString());}
        if(readerTouched) {
            try {reader(0);readerTouched=false;event("reader_off",why);}catch(Exception e){error=e.toString();event("cleanup_error","reader "+e);}
        }
        if(qTouched) {
            try {int code=qtag(0);event("qtag_off_api",String.valueOf(code));if(code==0)qTouched=false;else error="QTag off API="+code;}
            catch(Exception e){error=e.toString();event("cleanup_error","qtag "+e);}
        }
        if(stSession!=null){
            try{
                boolean owned=stSession.needsRecovery()||stConfigured;
                stSession.restore();stConfigured=false;restore=owned?"st_frames_matched":"not_required";
                byte[] original=stSession.baseline();if(original!=null)after="st_frames:"+Protocol.hex(original);
                stSession.close();stSession=null;stRestoreFailed=false;event("st_frames_restored",restore);
            }catch(Exception failed){stRestoreFailed=true;restore="st_restore_failed";error=failed.toString();event("cleanup_error","ST snapshot retained; do not start another session: "+failed);}
        }
        if(owner!=null && death!=null) {owner.unlinkToDeath(death,0);owner=null;death=null;}
        if(controllerProfile.equals("nxp_qtag") && adapter!=null && rfSnapshotAvailable && !before.isEmpty()) {
            try {
                after=rfConfig();
                // Reader release is asynchronous in NfcService; verify its readback.
                for(int attempt=0;attempt<6 && !after.equals(before);attempt++) {
                    SystemClock.sleep(150);after=rfConfig();
                }
                restore=after.equals(before) && !qTouched && !readerTouched ? "matched" : "not_matched";event("rf_restore",status());
            }
            catch(Exception e){restore="read_failed";event("cleanup_error","rf readback "+e);}
        } else if(controllerProfile.equals("nxp_qtag") && adapter!=null && "unavailable".equals(before)) {
            after="unavailable";restore="not_verifiable";
            event("rf_restore_unavailable","reader-mode and QTag stop were attempted; RF configuration could not be read back");
        } else if(!controllerProfile.equals("st54l_custom_frames") && adapter!=null && !qTouched && !readerTouched) {
            after="not_changed";restore="not_required";
        }
        return status();
    }
}
