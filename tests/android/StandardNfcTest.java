package dev.inknfclab;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.nfc.NfcAdapter;
import android.os.Binder;
import android.os.Parcel;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

/** Emulated framework integration: does not simulate successful tag activation or RF power. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class StandardNfcTest {
    ActivityController<NfcTestActivity> controller;NfcTestActivity app;ShadowNfcAdapter nfc;
    boolean paused;
    @Before public void setup()throws Exception{
        ShadowNfcAdapter.setNfcHardwareExists(true);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC,true);
        controller=Robolectric.buildActivity(NfcTestActivity.class);app=controller.get();app.setTheme(android.R.style.Theme_Material_Light_NoActionBar);controller.setup();
        app.getSharedPreferences("nfc_compat",0).edit().clear().commit();
        nfc=Shadows.shadowOf(NfcAdapter.getDefaultAdapter(app));nfc.setEnabled(true);ShadowLooper.idleMainLooper();
    }
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    Object get(String name)throws Exception{return field(NfcTestActivity.class,name).get(app);}
    void invoke(String name,Class<?>[] signature,Object... args)throws Exception{Method m=NfcTestActivity.class.getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(app,args);}
    void settle()throws Exception{
        ((ExecutorService)get("work")).submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
        ((ExecutorService)get("work")).submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
    }
    @After public void close()throws Exception{if(controller!=null){if(!paused)controller.pause();settle();controller.stop().destroy();}}
    @Test public void autoWithoutShizukuStartsStandardD1Reader()throws Exception{
        assertNull(get("remote"));invoke("begin",new Class<?>[]{int.class},2);settle();
        assertTrue(nfc.isInReaderMode());assertEquals(Boolean.FALSE,get("qSession"));assertEquals(Boolean.TRUE,get("active"));
        SessionLog s=(SessionLog)get("session");JSONObject report=new JSONObject(new String(Files.readAllBytes(new File(s.dir,"compatibility.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("standard_reader",report.getString("route"));assertFalse(report.getBoolean("shell_connected"));assertFalse(report.getBoolean("vendor_ready"));
    }
    @Test public void photoPushCanArmStandardReaderWithoutShell()throws Exception{
        PicturePanel p=(PicturePanel)get("pictures");
        Constructor<ImageCodec.PreparedImage> make=ImageCodec.PreparedImage.class.getDeclaredConstructor(Bitmap.class,Bitmap.class,byte[].class);make.setAccessible(true);
        Bitmap preview=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        field(PicturePanel.class,"frame").set(p,new PicturePanel.Frame(make.newInstance(preview,preview,new byte[ImageCodec.PACKED_LENGTH]),new JSONObject()));
        invoke("begin",new Class<?>[]{int.class},3);settle();assertTrue(nfc.isInReaderMode());assertFalse((Boolean)get("qSession"));
        SessionLog s=(SessionLog)get("session");String logs=new String(Files.readAllBytes(new File(s.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(logs.contains("standard_reader"));assertFalse("No tag means no APDU or upload",logs.contains("apdu_tx"));
    }
    @Test public void selectionPersistsAndCannotChangeDuringReaderSession()throws Exception{
        Spinner selection=(Spinner)get("transport");selection.setSelection(1);ShadowLooper.idleMainLooper();
        assertTrue(app.getSharedPreferences("nfc_compat",0).getBoolean("standard_only",false));
        invoke("begin",new Class<?>[]{int.class},2);settle();selection.setSelection(0);ShadowLooper.idleMainLooper();
        assertEquals(1,selection.getSelectedItemPosition());assertTrue(app.getSharedPreferences("nfc_compat",0).getBoolean("standard_only",false));
    }
    @Test public void compatibilityCheckIsReadOnlyAndRecorded()throws Exception{
        invoke("checkCompatibility",new Class<?>[0]);settle();assertFalse(nfc.isInReaderMode());assertFalse((Boolean)get("active"));
        SessionLog s=(SessionLog)get("session");JSONObject report=new JSONObject(new String(Files.readAllBytes(new File(s.dir,"compatibility.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(android.os.Build.VERSION.SDK_INT,report.getInt("sdk"));assertTrue(report.getString("scope").contains("未寻卡"));
        assertNotNull(ShadowDialog.getLatestDialog());
    }
    @Test public void leavingPageReleasesStandardReader()throws Exception{
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());controller.pause();paused=true;settle();
        assertFalse(nfc.isInReaderMode());assertFalse((Boolean)get("active"));
    }
    static final class FakeShell extends Binder {
        final String version;int vendorStarts;boolean denyPreflight,vendorReady;
        FakeShell(String version){this.version=version;}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
            data.enforceInterface(DiagnosticService.DESCRIPTOR);String result="{}";
            if(code==DiagnosticService.SERVICE_VERSION)result=version;
            if(code==DiagnosticService.Q_START){vendorStarts++;result="ERROR simulated vendor start failed";}
            if(code==DiagnosticService.CAPABILITIES){
                if(denyPreflight){reply.writeException(new SecurityException("simulated read-only permission denied"));return true;}
                result=vendorReady?"{\"route\":\"nxp_qtag\",\"vendor_ready\":true,\"reason\":\"fixture vendor API present\"}":"{\"route\":\"standard_reader\",\"vendor_ready\":false,\"reason\":\"专用接口不可用，使用标准 NFC\"}";
            }
            if(code==DiagnosticService.DRAIN)result="{\"text\":\"\",\"dropped_lines\":0,\"buffered_chars\":0,\"running\":false}";
            reply.writeNoException();reply.writeString(result);return true;
        }
    }
    @Test public void unknownVendorPreflightFallsBackWithoutVendorStart()throws Exception{
        FakeShell shell=new FakeShell("9");field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);
    }
    @Test public void staleShellServiceFallsBackBeforeVendorTransportStarts()throws Exception{
        FakeShell shell=new FakeShell("8");field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);assertEquals(Boolean.FALSE,get("qSession"));assertTrue(((TextView)get("compatibilityStatus")).getText().toString().contains("版本较旧"));
    }
    @Test public void deniedReadOnlyPreflightCanStillUseStandardReader()throws Exception{
        FakeShell shell=new FakeShell("9");shell.denyPreflight=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);
    }
    @Test public void vendorStartFailureNeverSwitchesToStandardWhileCleanupIsPending()throws Exception{
        FakeShell shell=new FakeShell("9");shell.vendorReady=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertFalse(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertFalse((Boolean)get("active"));
    }
}
