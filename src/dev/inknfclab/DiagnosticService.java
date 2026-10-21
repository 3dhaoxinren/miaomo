package dev.inknfclab;
import android.content.Context;
import android.os.*;
import dalvik.system.PathClassLoader;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Shell diagnostics and bounded Q-Poll/IsoDep sessions requested by this app. */
public final class DiagnosticService extends Binder {
    static final String DESCRIPTOR = "dev.inknfclab.test.Diagnostics.v1";
    static final int INFO=1, START=2, DRAIN=3, STOP=4, SNAPSHOT=5;
    static final int Q_START=6, Q_STATUS=7, Q_CONNECT=8, Q_TRANSCEIVE=9, Q_TIMEOUT=10, Q_STOP=11;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private java.lang.Process logcat;
    private int buffered;
    private long dropped, generation;
    private final QPollBackend qpoll=new QPollBackend((type,value) -> append("QPollBackend "+type+" "+value+"\n"));
    public DiagnosticService() { attachInterface(null, DESCRIPTOR); }
    public DiagnosticService(Context context) { this(); }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        if (code == 16777115) { stop(); new Thread(() -> { try { Thread.sleep(150); } catch(Exception ignored) {} System.exit(0); }).start(); if(reply != null) reply.writeNoException(); return true; }
        data.enforceInterface(DESCRIPTOR);
        if (android.os.Process.myUid() != 2000) throw new SecurityException("Only Shizuku shell UID 2000 supported");
        if(code==Q_TRANSCEIVE) {
            try { byte[] response=qpoll.transceive(data.createByteArray()); reply.writeNoException();reply.writeInt(1);reply.writeByteArray(response); }
            catch(Throwable e) {reply.writeNoException();reply.writeInt(0);reply.writeString(android.util.Log.getStackTraceString(e));}
            return true;
        }
        String result;
        try {
            switch(code) {
                case INFO: result=info(); break;
                case START: result=start(); break;
                case DRAIN: result=drain(); break;
                case STOP: result=stop(); break;
                case SNAPSHOT: result=command(new String[]{"/system/bin/dumpsys","nfc"}, 10000); break;
                case Q_START: result=qpoll.start(data.readStrongBinder());break;
                case Q_STATUS: result=qpoll.status();break;
                case Q_CONNECT: result=qpoll.connect();break;
                case Q_TIMEOUT: qpoll.timeout(data.readInt());result="TIMEOUT_SET";break;
                case Q_STOP: result=qpoll.stop("app_stop");break;
                default: return super.onTransact(code,data,reply,flags);
            }
        } catch(Throwable e) { result="ERROR " + android.util.Log.getStackTraceString(e); }
        reply.writeNoException(); reply.writeString(result); return true;
    }
    private static Object service(String name) throws Exception {
        return Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
    }
    private static void getter(JSONObject o, Class<?> api, Object obj, String name, Class<?>[] types, Object... args) {
        try {
            Object value = api.getMethod(name,types).invoke(obj,args);
            if (value instanceof List<?>) {
                JSONArray array = new JSONArray();
                for (Object item : (List<?>)value) {
                    JSONObject entry=new JSONObject();
                    for (String method : new String[]{"getTechnologyMode","getFrequency"})
                        try { entry.put(method,item.getClass().getMethod(method).invoke(item)); } catch(Exception e) { entry.put(method,e.toString()); }
                    array.put(entry);
                }
                value=array;
            }
            o.put(name,value==null ? JSONObject.NULL : value);
        } catch(Throwable e) { try { o.put(name+"_error",e instanceof InvocationTargetException ? ((InvocationTargetException)e).getTargetException().toString() : e.toString()); } catch(Exception ignored) {} }
    }
    private String info() throws Exception {
        JSONObject out=new JSONObject().put("uid",android.os.Process.myUid()).put("scope","read-only getters");
        try {
            IBinder binder=(IBinder)service("mi_nfc");
            out.put("mi_descriptor",binder==null ? "null" : binder.getInterfaceDescriptor());
            if(binder!=null) {
                ClassLoader loader=new PathClassLoader("/system_ext/framework/com.xiaomi.nfc.jar",ClassLoader.getSystemClassLoader());
                Class<?> api=loader.loadClass("com.xiaomi.nfc.IMiNfcAdapter");
                Object obj=loader.loadClass("com.xiaomi.nfc.IMiNfcAdapter$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
                for(String name:new String[]{"getVersion","getChipId","getFwVersion","getCustomRFConfig"}) getter(out,api,obj,name,new Class<?>[0]);
                getter(out,api,obj,"getDiscoveryTech",new Class<?>[]{int.class},3);
                getter(out,api,obj,"getFeatureVersion",new Class<?>[]{String.class},"NXP_MW");
            }
        } catch(Throwable e) { out.put("mi_error",e.toString()); }
        try {
            IBinder binder=(IBinder)service("nfc");
            Class<?> api=Class.forName("android.nfc.INfcAdapter");
            Object obj=Class.forName("android.nfc.INfcAdapter$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
            getter(out,api,obj,"getState",new Class<?>[0]);
            getter(out,api,obj,"getRfDiscoverConfigurations",new Class<?>[0]);
        } catch(Throwable e) { out.put("nfc_error",e.toString()); }
        out.put("typeQ_property", command(new String[]{"/system/bin/getprop","nfc.polling.typeQ.enable"},2000));
        return out.toString(2);
    }
    private String start() throws IOException {
        stop(); lines.clear(); buffered=0; dropped=0;
        ArrayList<String> args=new ArrayList<>(Arrays.asList("/system/bin/logcat","-v","threadtime","-T","1"));
        for(String tag:new String[]{"NfcService","NfcTag","NfcDispatcher","NfcPhoneCase","NfcRFConfig","NfcAdaptation","NxpNci","NxpNciX","NxpNciR","NxpNciHal","NxpExtns","NxpTml","NxpHal","libnfc_nci","NFC","InkNfcTest"}) args.add(tag+":V");
        args.add("*:S");
        final java.lang.Process current=new ProcessBuilder(args).redirectErrorStream(true).start(); logcat=current;
        final long token=++generation;
        new Thread(() -> {
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(current.getInputStream()))) {
                String line; while((line=reader.readLine())!=null) {
                    synchronized(this) {
                        if(generation!=token) break;
                        if(line.length()>16384) { line=line.substring(0,16384)+" [TRUNCATED]"; dropped++; }
                        while(buffered+line.length()+1>1024*1024 && !lines.isEmpty()) { buffered-=lines.removeFirst().length(); dropped++; }
                        lines.addLast(line+"\n"); buffered+=line.length()+1;
                    }
                    qpoll.observe(line);
                }
            } catch(IOException e) { synchronized(this) { if(generation==token) { String m="CAPTURE_ERROR "+e+"\n"; lines.addLast(m); buffered+=m.length(); } } }
        },"nfc-log-reader").start();
        new Thread(() -> { try { Thread.sleep(600000); } catch(InterruptedException ignored) {} boolean expired; synchronized(this){expired=generation==token;} if(expired)stop(); },"capture-deadline").start();
        return "STARTED uid=2000 max_duration_ms=600000";
    }
    private synchronized String drain() throws Exception {
        StringBuilder out=new StringBuilder();
        while(!lines.isEmpty() && out.length()+lines.peekFirst().length()<48000) { String line=lines.removeFirst(); buffered-=line.length(); out.append(line); }
        return new JSONObject().put("text",out.toString()).put("dropped_lines",dropped).put("buffered_chars",buffered)
            .put("running",logcat!=null && logcat.isAlive()).toString();
    }
    private String stop() {
        qpoll.stop("capture_stop");
        synchronized(this){++generation;
        if(logcat!=null) { logcat.destroy(); logcat=null; }}
        return "STOPPED";
    }
    private synchronized void append(String text) {
        while(buffered+text.length()>1024*1024 && !lines.isEmpty()){buffered-=lines.removeFirst().length();dropped++;}
        lines.addLast(text);buffered+=text.length();
        android.util.Log.i("InkNfcTest",text.trim());
    }
    private static String command(String[] args, int timeout) throws Exception {
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(() -> {
            try(InputStream in=p.getInputStream()) { byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1) synchronized(out) { if(out.size()<180000) out.write(b,0,Math.min(n,180000-out.size())); } } catch(IOException ignored) {}
        }); reader.start();
        boolean done=p.waitFor(timeout,TimeUnit.MILLISECONDS); if(!done) p.destroy(); reader.join(1000);
        synchronized(out) { return "EXIT="+(done ? p.exitValue() : "TIMEOUT")+"\n"+out.toString("UTF-8")+(out.size()>=180000 ? "\n[TRUNCATED]" : ""); }
    }
}
