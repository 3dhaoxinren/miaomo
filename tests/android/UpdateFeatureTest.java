package dev.inknfclab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class UpdateFeatureTest {
    interface IAppCallback {}
    interface QSnapshot {
        int getState();java.util.List<?> getRfDiscoverConfigurations();int sendVendorNciMessage(int a,int b,int c,byte[] d);
        void setReaderMode(android.os.IBinder token,IAppCallback callback,int flags,android.os.Bundle extras,String packageName);
    }
    interface QNoSnapshot {
        int getState();int sendVendorNciMessage(int a,int b,int c,byte[] d);
        void setReaderMode(android.os.IBinder token,IAppCallback callback,int flags,android.os.Bundle extras,String packageName);
    }
    interface QNoRaw {
        int getState();java.util.List<?> getRfDiscoverConfigurations();
        void setReaderMode(android.os.IBinder token,IAppCallback callback,int flags,android.os.Bundle extras,String packageName);
    }
    ActivityController<Activity> controller;
    Activity activity;
    UpdatePanel panel;
    @Before public void setup()throws Exception{
        Activity app=Robolectric.buildActivity(Activity.class).get();
        PackageInfo info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
        info.versionName="0.5.8-test";info.versionCode=19;Shadows.shadowOf(app.getPackageManager()).installPackage(info);
        controller=Robolectric.buildActivity(Activity.class);activity=controller.get();activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);controller.setup();
        activity.getSharedPreferences("app_updates",0).edit().clear().commit();
    }
    @After public void close(){if(panel!=null)panel.close();if(controller!=null)controller.pause().stop().destroy();}
    static JSONObject release(String tag,boolean draft,boolean pre,boolean apk)throws Exception{
        UpdateVersion v=UpdateVersion.parse(tag);String name="miaomo-"+v.core()+".apk";
        JSONObject object=new JSONObject().put("tag_name",tag).put("name","喵墨 "+tag).put("body","更新测试说明").put("draft",draft).put("prerelease",pre).put("html_url","https://evil.test/spoof");
        JSONArray assets=new JSONArray();if(apk)assets.put(new JSONObject().put("name",name).put("size",600000).put("browser_download_url",GitHubUpdates.REPOSITORY+"/releases/download/"+tag+"/"+name));
        return object.put("assets",assets);
    }
    static String body(String tag)throws Exception{return new JSONArray().put(release(tag,false,true,true)).toString();}
    UpdatePanel panel(GitHubUpdates.Fetcher fetcher,AtomicBoolean busy){
        panel=new UpdatePanel(activity,busy::get,fetcher);activity.setContentView(panel.view);panel.foreground(true);return panel;
    }
    static Button button(View view,String text){
        if(view instanceof Button&&text.equals(((Button)view).getText().toString()))return (Button)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=button(group.getChildAt(i),text);if(b!=null)return b;}}
        return null;
    }
    static boolean contains(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;}
        return false;
    }
    static CheckBox checkbox(View view,String text){
        if(view instanceof CheckBox&&text.equals(((CheckBox)view).getText().toString()))return (CheckBox)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){CheckBox found=checkbox(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    static void await(Button check)throws Exception{
        for(int i=0;i<300&&!check.isEnabled();i++){Thread.sleep(10);ShadowLooper.idleMainLooper();}
        ShadowLooper.idleMainLooper();assertTrue("request finished",check.isEnabled());
    }
    @Test public void manualFindsNewerAndOpensOfficialAsset()throws Exception{
        String data=body("v0.5.9-test");AtomicInteger requests=new AtomicInteger();
        panel(()->{requests.incrementAndGet();return data;},new AtomicBoolean());
        assertEquals(0,requests.get());Button check=button(panel.view,"检查更新");assertNotNull(check);check.performClick();await(check);
        assertEquals(1,requests.get());AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(dialog);
        assertTrue(contains(dialog.getWindow().getDecorView(),"发现新版本 v0.5.9-test"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(GitHubUpdates.REPOSITORY+"/releases/download/v0.5.9-test/miaomo-0.5.9.apk",intent.getDataString());
    }
    @Test public void sameOrOlderDoesNotOfferDowngrade()throws Exception{
        String data=body("v0.5.2-test");panel(()->data,new AtomicBoolean());Button check=button(panel.view,"检查更新");check.performClick();await(check);
        AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(contains(dialog.getWindow().getDecorView(),"无需更新"));assertNotNull(button(panel.view,"检查更新"));
    }
    @Test public void failureIsVisibleAndRetryEnabled()throws Exception{
        panel(()->{throw new IOException("GitHub 暂时限制请求");},new AtomicBoolean());Button check=button(panel.view,"检查更新");check.performClick();await(check);
        assertTrue(contains(panel.view,"检查失败"));assertTrue(contains(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"GitHub 暂时限制请求"));
    }
    @Test public void backgroundCompletionDoesNotOpenDialog()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);String data=body("v0.5.9-test");
        panel(()->{started.countDown();release.await(2,TimeUnit.SECONDS);return data;},new AtomicBoolean());Button check=button(panel.view,"检查更新");check.performClick();assertTrue(started.await(1,TimeUnit.SECONDS));panel.foreground(false);release.countDown();await(check);
        assertNull(ShadowDialog.getLatestDialog());assertNotNull(button(panel.view,"查看新版本"));
    }
    @Test public void runningNfcBlocksCheckAndBrowser()throws Exception{
        AtomicInteger requests=new AtomicInteger();panel(()->{requests.incrementAndGet();return "[]";},new AtomicBoolean(true));button(panel.view,"检查更新").performClick();button(panel.view,"GitHub 发布页").performClick();ShadowLooper.idleMainLooper();
        assertEquals(0,requests.get());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void automaticIsOptInAndThrottled()throws Exception{
        AtomicInteger requests=new AtomicInteger();String data=body("v0.5.8-test");panel(()->{requests.incrementAndGet();return data;},new AtomicBoolean());assertEquals(0,requests.get());
        activity.getSharedPreferences("app_updates",0).edit().putBoolean("automatic",true).commit();panel.foreground(true);Button check=button(panel.view,"检查更新");await(check);assertEquals(1,requests.get());panel.foreground(false);panel.foreground(true);ShadowLooper.idleMainLooper();assertEquals(1,requests.get());
        assertFalse(UpdatePanel.due(false,100,0));assertFalse(UpdatePanel.due(true,1000,999));assertTrue(UpdatePanel.due(true,UpdatePanel.CHECK_INTERVAL+100,100));assertTrue(UpdatePanel.due(true,99,100));
    }
    @Test public void closingSuppressesLateCallbacks()throws Exception{
        CountDownLatch started=new CountDownLatch(1);panel(()->{started.countDown();Thread.sleep(3000);return "[]";},new AtomicBoolean());button(panel.view,"检查更新").performClick();assertTrue(started.await(1,TimeUnit.SECONDS));panel.close();Thread.sleep(50);ShadowLooper.idleMainLooper();assertNull(ShadowDialog.getLatestDialog());
    }
    @Test public void parsesRealJsonAndRejectsUnsafeAsset()throws Exception{
        JSONObject bad=release("v0.5.9-test",false,true,true);bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://evil.test/miaomo.apk");
        GitHubUpdates.Result unsafe=GitHubUpdates.check("0.5.8-test",true,()->new JSONArray().put(bad).toString());
        assertTrue(unsafe.updateAvailable);assertNull("unsafe download must be rejected",unsafe.latest.downloadUrl);
        JSONObject next=release("v0.5.10-test",false,true,true);
        JSONArray list=new JSONArray().put(release("v99.0.0",true,false,true)).put(bad).put(next).put(release("v0.5.2-test",false,true,true));
        GitHubUpdates.Result result=GitHubUpdates.check("0.5.8-test",true,list::toString);assertTrue(result.updateAvailable);assertEquals("v0.5.10-test",result.latest.tag);assertNotNull(result.latest.downloadUrl);assertEquals(GitHubUpdates.REPOSITORY+"/releases/tag/v0.5.10-test",result.latest.page());
        GitHubUpdates.Result stable=GitHubUpdates.check("0.5.2",false,list::toString);assertNull(stable.latest);
        assertTrue(GitHubUpdates.parse("[]").isEmpty());assertTrue(GitHubUpdates.parse("[null,{}]").isEmpty());
        try{GitHubUpdates.parse("{bad json}");fail();}catch(org.json.JSONException expected){}
    }
    @Test public void activityContainsUpdateEntry()throws Exception{
        ActivityController<NfcTestActivity> app=Robolectric.buildActivity(NfcTestActivity.class);app.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar);app.setup();
        assertNotNull(button(app.get().getWindow().getDecorView(),"检查更新"));assertTrue(contains(app.get().getWindow().getDecorView(),"当前版本 0.5.8-test"));app.pause().stop().destroy();
    }
    @Test public void appShowsExplicitOptInForUnverifiedRfCompatibility()throws Exception{
        ActivityController<NfcTestActivity> app=Robolectric.buildActivity(NfcTestActivity.class);app.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar);app.setup();
        String label="允许无 RF 配置读回兼容（部分机型）";CheckBox compat=checkbox(app.get().getWindow().getDecorView(),label);assertNotNull(compat);assertFalse(compat.isChecked());
        assertTrue(contains(app.get().getWindow().getDecorView(),"默认关闭"));compat.performClick();
        AlertDialog confirmation=(AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(confirmation);assertTrue(contains(confirmation.getWindow().getDecorView(),"无法核验原 RF 配置"));
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue("explicit confirmation keeps the opt-in enabled",compat.isChecked());assertTrue("compatibility choice is persisted",app.get().getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false));
        app.pause().stop().destroy();
    }
    @Test public void st54lOptInIsSeparateAndDefaultsOff()throws Exception{
        ActivityController<NfcTestActivity> app=Robolectric.buildActivity(NfcTestActivity.class);app.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar);app.setup();
        CheckBox st=checkbox(app.get().getWindow().getDecorView(),"ST54L 适配试验（小米 15 Pro 等）");assertNotNull(st);assertFalse(st.isChecked());st.performClick();assertFalse(st.isChecked());
        AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(contains(dialog.getWindow().getDecorView(),"不发送 NXP QTag 命令"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(st.isChecked());assertTrue(app.get().getSharedPreferences("nfc_compat",0).getBoolean("allow_st54l_experiment",false));
        assertFalse(app.get().getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false));app.pause().stop().destroy();
    }
    @Test public void qpollCompatibilityGateRequiresNxpProfileAndExactCapabilities()throws Exception{
        assertEquals(QPollCompatibility.Mode.VERIFIED_RF_SNAPSHOT,QPollCompatibility.inspect(QSnapshot.class,false,16));
        try{QPollCompatibility.inspect(QNoSnapshot.class,false,16);fail("RF snapshot must require explicit opt-in");}catch(IOException expected){}
        assertEquals(QPollCompatibility.Mode.USER_OPTED_IN_NO_RF_SNAPSHOT,QPollCompatibility.inspect(QNoSnapshot.class,true,16));
        try{QPollCompatibility.inspect(QNoSnapshot.class,true,0);fail("unknown/ST profile must not receive the NXP QTag command");}catch(IOException expected){assertTrue(expected.getMessage().contains("NXP_MW"));}
        try{QPollCompatibility.inspect(QNoRaw.class,true,16);fail("missing raw vendor NCI must fail before Q-Poll");}catch(IOException expected){assertTrue(expected.getMessage().contains("vendor NCI"));}
    }
}
