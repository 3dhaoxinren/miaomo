package dev.inknfclab;

import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Shell-side ST54L frame configuration using public runtime Binder method signatures. */
final class StVendorSession implements StPollingTransaction.Link,StPollingTransaction.Store {
    private final Object adapter;
    private final Class<?> api;
    private final QPollBackend.Listener log;
    private final String identity;
    private final File snapshot=new File("/data/local/tmp/miaomo-st54l-frames.json");
    private final ArrayBlockingQueue<byte[]> replies=new ArrayBlockingQueue<>(4);
    private final StPollingTransaction transaction;
    private Object callback;
    private Class<?> callbackType;
    private volatile int expected=-1;
    private boolean registered,paused;
    private Method pause,resume;
    StVendorSession(Object adapter,Class<?> api,String chip,String firmware,QPollBackend.Listener log){
        this.adapter=adapter;this.api=api;this.log=log;identity=chip+"/"+firmware;transaction=new StPollingTransaction(this,this);
    }
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{
        try{return api.getMethod(name,types).invoke(adapter,args);}
        catch(InvocationTargetException e){Throwable cause=e.getTargetException();if(cause instanceof Exception)throw (Exception)cause;throw e;}
    }
    void initialize()throws Exception{
        callbackType=Class.forName("android.nfc.INfcVendorNciCallback");Class<?> stub=Class.forName("android.nfc.INfcVendorNciCallback$Stub");
        Method responseMethod=null;
        for(Method method:callbackType.getMethods())if(method.getName().equals("onVendorResponseReceived")||method.getName().equals("onVendorNciResponse")){
            if(Arrays.equals(method.getParameterTypes(),new Class<?>[]{int.class,int.class,byte[].class}))responseMethod=method;
        }
        if(responseMethod==null)throw new IOException("此 ROM 未暴露兼容的 ST vendor NCI response callback；尚未配置短帧。");
        Field field=stub.getDeclaredField("TRANSACTION_"+responseMethod.getName());field.setAccessible(true);final int responseCode=field.getInt(null);
        try{pause=api.getMethod("pausePolling",long.class);}catch(NoSuchMethodException old){pause=api.getMethod("pausePolling",int.class);}
        resume=api.getMethod("resumePolling");
        for(Method method:new Method[]{pause,resume})if(method.getReturnType()!=void.class&&method.getReturnType()!=int.class)throw new IOException("Unsupported polling-pause API result type: "+method.toString());
        api.getMethod("sendVendorNciMessage",int.class,int.class,int.class,byte[].class);
        Method register=api.getMethod("registerVendorExtensionCallback",callbackType);api.getMethod("unregisterVendorExtensionCallback",callbackType);
        Binder binder=new Binder(){
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
                if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString("android.nfc.INfcVendorNciCallback");return true;}
                if(code!=responseCode)return super.onTransact(code,data,reply,flags);
                data.enforceInterface("android.nfc.INfcVendorNciCallback");int gid=data.readInt(),oid=data.readInt();byte[] value=data.createByteArray();
                if(gid==15&&oid==1&&expected>=0&&value!=null&&value.length>=2&&(value[0]&255)==expected)replies.offer(value.clone());
                if(reply!=null)reply.writeNoException();return true;
            }
        };
        callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
            if(method.getName().equals("asBinder"))return binder;
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return proxy==args[0];
            if(method.getName().equals("toString"))return "MiaomoSt54LCallback";return null;
        });
        registered=true;try{register.invoke(adapter,callback);}catch(Exception failed){try{close();}catch(Exception cleanup){failed.addSuppressed(cleanup);}throw failed;}
        log.event("st_vendor_callback","registered; configuration not yet changed");
    }
    byte[] start()throws Exception{return transaction.start();}
    boolean restore()throws Exception{return transaction.restore();}
    boolean needsRecovery(){return transaction.needsRecovery();}
    byte[] baseline(){return transaction.baseline();}
    @Override public void pause()throws Exception{
        if(paused)return;
        Object argument=pause.getParameterTypes()[0]==long.class?(Object)Long.valueOf(30000):Integer.valueOf(30000);
        Object result;
        try{result=pause.invoke(adapter,argument);}catch(InvocationTargetException e){throw new IOException("ST pausePolling failed",e.getTargetException());}
        if(result instanceof Number&&((Number)result).intValue()!=1)throw new IOException("ST pausePolling returned "+result+"; no frame setter was issued");
        paused=true;log.event("st_polling_pause",String.valueOf(result));
    }
    @Override public void resume()throws Exception{
        if(!paused)return;
        Object result=resume.invoke(adapter);
        if(result instanceof Number&&((Number)result).intValue()!=1)throw new IOException("ST resumePolling returned "+result);
        paused=false;log.event("st_polling_resume","requested");
    }
    @Override public byte[] exchange(byte[] command)throws Exception{
        replies.clear();expected=command[0]&255;
        try{
            log.event("st_vendor_tx","GID=0F OID=01 "+Protocol.hex(command));
            Object result=invoke("sendVendorNciMessage",new Class<?>[]{int.class,int.class,int.class,byte[].class},1,15,1,command);
            if(!(result instanceof Number)||((Number)result).intValue()!=0)throw new IOException("ST vendor NCI API rejected command: "+result);
            byte[] payload=replies.poll(2500,TimeUnit.MILLISECONDS);if(payload==null)throw new IOException("ST vendor response timeout; command acceptance unconfirmed");
            log.event("st_vendor_rx",Protocol.hex(payload));return payload;
        }finally{expected=-1;}
    }
    @Override public byte[] load()throws Exception{
        if(!snapshot.exists())return null;
        if(snapshot.length()>4096)throw new IOException("ST recovery snapshot too large; left unchanged");
        JSONObject saved=new JSONObject(new String(Files.readAllBytes(snapshot.toPath()),StandardCharsets.UTF_8));
        if(!identity.equals(saved.getString("identity")))throw new IOException("ST recovery snapshot belongs to another controller/firmware; left unchanged");
        byte[] frames=android.util.Base64.decode(saved.getString("frames"),android.util.Base64.NO_WRAP);Protocol.validatePolling(frames);
        log.event("st_recovery_pending","owned snapshot found; will restore before another configuration");return frames;
    }
    @Override public void save(byte[] frames)throws Exception{
        if(snapshot.exists())throw new IOException("Existing ST recovery snapshot must be restored first");
        File temporary=new File(snapshot.getPath()+".new");
        byte[] data=new JSONObject().put("identity",identity).put("frames",android.util.Base64.encodeToString(frames,android.util.Base64.NO_WRAP)).toString().getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream output=new FileOutputStream(temporary)){android.system.Os.chmod(temporary.getPath(),0600);output.write(data);output.getFD().sync();}
        Files.move(temporary.toPath(),snapshot.toPath(),StandardCopyOption.ATOMIC_MOVE);log.event("st_recovery_saved","baseline persisted before setter");
    }
    @Override public void clear()throws Exception{if(!snapshot.delete()&&snapshot.exists())throw new IOException("ST baseline restored but snapshot could not be removed");}
    void close()throws Exception{
        if(transaction.needsRecovery())throw new IOException("ST polling restore not verified; retain callback and saved baseline");
        resume();
        if(registered){invoke("unregisterVendorExtensionCallback",new Class<?>[]{callbackType},callback);registered=false;}
    }
}
