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
    void supplyTestFrame()throws Exception{
        PicturePanel panel=(PicturePanel)get("pictures");
        Constructor<ImageCodec.PreparedImage> make=ImageCodec.PreparedImage.class.getDeclaredConstructor(Bitmap.class,Bitmap.class,byte[].class);make.setAccessible(true);
        Bitmap preview=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        field(PicturePanel.class,"frame").set(panel,new PicturePanel.Frame(make.newInstance(preview,preview,new byte[ImageCodec.PACKED_LENGTH]),new JSONObject()));
    }
    @After public void close()throws Exception{if(controller!=null){if(!paused)controller.pause();settle();controller.stop().destroy();}}
    @Test public void autoWithoutShizukuStartsStandardD1Reader()throws Exception{
        assertNull(get("remote"));invoke("begin",new Class<?>[]{int.class},2);settle();
        assertTrue(nfc.isInReaderMode());assertEquals(Boolean.FALSE,get("qSession"));assertEquals(Boolean.TRUE,get("active"));
        SessionLog s=(SessionLog)get("session");JSONObject report=new JSONObject(new String(Files.readAllBytes(new File(s.dir,"compatibility.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("standard_reader",report.getString("route"));assertFalse(report.getBoolean("shell_connected"));assertFalse(report.getBoolean("vendor_ready"));
    }
    @Test public void photoPushCanArmStandardReaderWithoutShell()throws Exception{
        supplyTestFrame();
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
        final String version;int vendorStarts,preflights;String capabilityFixture;boolean denyPreflight,vendorReady,stQueryRejected,stConfigurationUncertain;
        FakeShell(String version){this.version=version;}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
            data.enforceInterface(DiagnosticService.DESCRIPTOR);String result="{}";
            if(code==DiagnosticService.SERVICE_VERSION)result=version;
            if(code==DiagnosticService.Q_START){vendorStarts++;result=stQueryRejected?"ERROR java.io.IOException: ST command 0x15 response rejected: 150B":"ERROR simulated vendor start failed";}
            if(code==DiagnosticService.Q_STATUS)result=stQueryRejected
                ?"{\"controller_profile\":\"st54l_custom_frames\",\"running\":false,\"st_frames_configured\":"+stConfigurationUncertain+",\"q_discovery_sent\":false,\"restore\":\""+(stConfigurationUncertain?"st_restore_failed":"not_required")+"\",\"error\":\"ST command 0x15 response rejected: 150B\"}"
                :"{}";
            if(code==DiagnosticService.CAPABILITIES){
                preflights++;
                if(denyPreflight){reply.writeException(new SecurityException("simulated read-only permission denied"));return true;}
                result=vendorReady?(stQueryRejected?"{\"route\":\"st54l_custom_frames\",\"chip\":\"ST54L\",\"vendor_ready\":true,\"reason\":\"ST54L fixture\"}":"{\"route\":\"nxp_qtag\",\"vendor_ready\":true,\"reason\":\"fixture vendor API present\"}"):"{\"route\":\"standard_reader\",\"vendor_ready\":false,\"reason\":\"专用接口不可用，使用标准 NFC\"}";
                if(capabilityFixture!=null)result=capabilityFixture;
            }
            if(code==DiagnosticService.DRAIN)result="{\"text\":\"\",\"dropped_lines\":0,\"buffered_chars\":0,\"running\":false}";
            reply.writeNoException();reply.writeString(result);return true;
        }
    }
    @Test public void unknownVendorPreflightFallsBackWithoutVendorStart()throws Exception{
        FakeShell shell=new FakeShell("11");field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);
    }
    @Test public void staleShellServiceFallsBackBeforeVendorTransportStarts()throws Exception{
        FakeShell shell=new FakeShell("10");field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);assertEquals(Boolean.FALSE,get("qSession"));assertTrue(((TextView)get("compatibilityStatus")).getText().toString().contains("版本较旧"));
    }
    @Test public void deniedReadOnlyPreflightCanStillUseStandardReader()throws Exception{
        FakeShell shell=new FakeShell("11");shell.denyPreflight=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);
    }
    @Test public void vendorStartFailureNeverSwitchesToStandardWhileCleanupIsPending()throws Exception{
        FakeShell shell=new FakeShell("11");shell.vendorReady=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertFalse(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertFalse((Boolean)get("active"));
    }
    @Test public void rejectedReadOnlyStQueryFallsBackOnlyAfterCleanRollbackState()throws Exception{
        FakeShell shell=new FakeShell("11");shell.vendorReady=true;shell.stQueryRejected=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertEquals(Boolean.FALSE,get("qSession"));
        SessionLog log=(SessionLog)get("session");String events=new String(java.nio.file.Files.readAllBytes(new java.io.File(log.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("failure detail retained",events.contains("st_vendor_read_rejected"));
        assertTrue("exact 0B status retained",events.contains("st_vendor_read_rejected_status"));
        assertTrue("standard reader selected",events.contains("transport_selected"));
    }
    @Test public void rejectedStQueryDoesNotFallbackWhenCleanupCannotConfirmPristineState()throws Exception{
        FakeShell shell=new FakeShell("11");shell.vendorReady=true;shell.stQueryRejected=true;shell.stConfigurationUncertain=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertFalse(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertFalse((Boolean)get("active"));
        SessionLog log=(SessionLog)get("session");String events=new String(java.nio.file.Files.readAllBytes(new java.io.File(log.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(events.contains("st_fallback_blocked"));assertFalse(events.contains("transport_fallback"));
    }
    @Test public void photoPushFallsBackAfterRejectedStReadWithoutUploadingUntilTag()throws Exception{
        supplyTestFrame();FakeShell shell=new FakeShell("11");shell.vendorReady=true;shell.stQueryRejected=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},3);settle();assertTrue(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertEquals(Boolean.FALSE,get("qSession"));
        SessionLog log=(SessionLog)get("session");String events=new String(java.nio.file.Files.readAllBytes(new java.io.File(log.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(events.contains("transport_fallback"));assertFalse(events.contains("apdu_tx"));assertFalse(events.contains("image_transfer_started"));
    }
    @Test public void fallbackKeepsTheControllerStatusInItsOwnEvent()throws Exception{
        FakeShell shell=new FakeShell("11");shell.vendorReady=true;shell.stQueryRejected=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertTrue(nfc.isInReaderMode());
        SessionLog log=(SessionLog)get("session");String events=new String(java.nio.file.Files.readAllBytes(new java.io.File(log.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        boolean status=false,fallback=false;
        for(String line:events.split("\\n")){
            JSONObject item=new JSONObject(line);String type=item.optString("type"),detail=item.optString("detail");
            if(type.equals("st_vendor_read_rejected_status")&&detail.equals("0B"))status=true;
            if(type.equals("transport_fallback")&&new JSONObject(detail).optString("fallback_from").equals("st54l_custom_frames"))fallback=true;
        }
        assertTrue(status);assertTrue(fallback);
    }
    @Test public void explicitQPollDiagnosticKeepsRejectedStStatusVisible()throws Exception{
        FakeShell shell=new FakeShell("11");shell.vendorReady=true;shell.stQueryRejected=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},4);settle();assertFalse(nfc.isInReaderMode());assertEquals(1,shell.vendorStarts);assertFalse((Boolean)get("active"));
        SessionLog log=(SessionLog)get("session");String events=new String(java.nio.file.Files.readAllBytes(new java.io.File(log.dir,"events.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(events.contains("transport_fallback"));assertTrue(events.contains("transport_start_error"));
    }
    FakeShell interfaceShell(String status,String chip)throws Exception{
        FakeShell shell=new FakeShell("11");shell.capabilityFixture=new JSONObject().put("route","standard_reader").put("vendor_ready",false)
            .put("support_status",status).put("chip",chip).put("firmware","fixture-fw").put("source","INfcAdapter")
            .put("raw_vendor_api",!status.equals("missing_interfaces")).put("reader_api",true)
            .put("missing_interfaces",new org.json.JSONArray(status.equals("missing_interfaces")?"[\"厂商 NFC 指令接口\"]":"[]"))
            .put("interface_inventory",new JSONObject().put("class","fixture.INfcAdapter").put("signatures",new org.json.JSONArray().put("void setReaderMode(IBinder,IAppCallback,int,Bundle)")))
            .put("reason",status.equals("missing_interfaces")?"未检测到厂商 NFC 指令接口":"控制器接口已发现，当前尚未确认专用适配").toString();
        field(NfcTestActivity.class,"remote").set(app,shell);return shell;
    }
    JSONObject feedback()throws Exception{
        SessionLog s=(SessionLog)get("session");return new JSONObject(new String(Files.readAllBytes(new File(s.dir,"compatibility-feedback.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
    }
    static boolean contains(android.view.View v,String text){
        if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;
        if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),text))return true;}return false;
    }
    @Test public void missingInterfaceShowsNoticeAndExportsUsableFeedbackZip()throws Exception{
        FakeShell shell=interfaceShell("missing_interfaces","NXP");invoke("begin",new Class<?>[]{int.class},2);settle();
        AlertDialog dialog=(AlertDialog)get("compatibilityNotice");assertNotNull(dialog);assertTrue(dialog.isShowing());
        assertTrue(contains(dialog.getWindow().getDecorView(),"系统缺少专用 NFC 接口"));assertTrue(contains(dialog.getWindow().getDecorView(),"厂商 NFC 指令接口"));
        assertTrue(nfc.isInReaderMode());assertEquals(0,shell.vendorStarts);
        JSONObject data=feedback();assertEquals(1,data.getInt("schema_version"));assertEquals("preflight",data.getString("stage"));
        assertTrue(data.getJSONObject("device").has("fingerprint"));assertTrue(data.getJSONObject("permissions").getBoolean("shell_connected"));
        assertEquals("missing_interfaces",data.getJSONObject("compatibility").getString("support_status"));assertTrue(data.getJSONObject("compatibility").getJSONObject("interface_inventory").getJSONArray("signatures").getString(0).contains("setReaderMode"));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();settle();
        File zip=(File)get("pendingExport");assertNotNull(zip);assertFalse(nfc.isInReaderMode());
        try(java.util.zip.ZipFile archive=new java.util.zip.ZipFile(zip)){
            assertTrue(archive.stream().anyMatch(e->e.getName().endsWith("/compatibility-feedback.json")));
            assertTrue(archive.stream().anyMatch(e->e.getName().endsWith("/environment.json")));
            assertTrue(archive.stream().anyMatch(e->e.getName().endsWith("/events.jsonl")));
        }
    }
    @Test public void exposedButUnadaptedControllerHasDifferentNotice()throws Exception{
        FakeShell shell=interfaceShell("unadapted_controller","ST54T");invoke("begin",new Class<?>[]{int.class},2);settle();
        AlertDialog dialog=(AlertDialog)get("compatibilityNotice");assertNotNull(dialog);assertTrue(contains(dialog.getWindow().getDecorView(),"检测到接口，暂未确认适配"));
        assertFalse(contains(dialog.getWindow().getDecorView(),"系统缺少专用 NFC 接口"));assertEquals(0,shell.vendorStarts);assertTrue(nfc.isInReaderMode());
        assertEquals("ST54T",feedback().getJSONObject("compatibility").getString("chip"));
    }
    @Test public void noShellMeansNotCheckedAndDoesNotClaimMissingInterfaces()throws Exception{
        invoke("begin",new Class<?>[]{int.class},2);settle();assertNull(get("compatibilityNotice"));
        assertEquals("not_checked",feedback().getJSONObject("compatibility").getString("support_status"));
    }
    @Test public void deniedProbeIsRecordedAsFailureNotUnsupported()throws Exception{
        FakeShell shell=new FakeShell("11");shell.denyPreflight=true;field(NfcTestActivity.class,"remote").set(app,shell);
        invoke("begin",new Class<?>[]{int.class},2);settle();assertNull(get("compatibilityNotice"));assertEquals(0,shell.vendorStarts);
        JSONObject report=feedback().getJSONObject("compatibility");assertEquals("probe_failed",report.getString("support_status"));assertTrue(report.getString("probe_error").contains("SecurityException"));
    }
    @Test public void disabledExperimentIsNotReportedAsMissingInterface()throws Exception{
        interfaceShell("experiment_disabled","ST54L");invoke("begin",new Class<?>[]{int.class},2);settle();assertNull(get("compatibilityNotice"));
        assertEquals("experiment_disabled",feedback().getJSONObject("compatibility").getString("support_status"));
    }
    @Test public void explicitStandardModeDoesNotProbeOrShowVendorNotice()throws Exception{
        FakeShell shell=interfaceShell("missing_interfaces","NXP");app.getSharedPreferences("nfc_compat",0).edit().putBoolean("standard_only",true).commit();
        invoke("begin",new Class<?>[]{int.class},2);settle();assertNull(get("compatibilityNotice"));assertEquals(0,shell.preflights);assertEquals(0,shell.vendorStarts);assertTrue(nfc.isInReaderMode());
    }
    @Test public void manualCompatibilityIncludesFeedbackActionWithoutStartingReader()throws Exception{
        interfaceShell("unadapted_controller","ST54T");invoke("checkCompatibility",new Class<?>[0]);settle();
        AlertDialog dialog=(AlertDialog)get("compatibilityNotice");assertNotNull(dialog);assertEquals("导出适配反馈",dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        assertEquals("manual_check",feedback().getString("stage"));assertFalse(nfc.isInReaderMode());
        SessionLog s=(SessionLog)get("session");assertTrue(new File(s.dir,"vendor-info.json").isFile());
    }
    @Test public void noticeIsDismissedWhenPageLeavesForeground()throws Exception{
        interfaceShell("missing_interfaces","NXP");invoke("begin",new Class<?>[]{int.class},2);settle();AlertDialog dialog=(AlertDialog)get("compatibilityNotice");assertNotNull(dialog);
        controller.pause();paused=true;settle();assertFalse(dialog.isShowing());assertNull(get("compatibilityNotice"));
    }
}
