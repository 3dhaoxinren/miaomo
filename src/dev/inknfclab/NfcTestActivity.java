package dev.inknfclab;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.*;
import android.nfc.tech.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import org.json.*;
import rikka.shizuku.Shizuku;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

public final class NfcTestActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final ExecutorService nfcWork=Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch=new AtomicInteger();
    private final AtomicBoolean tagSeen=new AtomicBoolean();
    private final IBinder qOwner=new Binder();
    private volatile boolean qSession;
    private int currentMode,currentToken,currentPowerMs;
    private PicturePanel.Frame currentFrame;
    private TextView deviceChip;
    private final android.widget.ScrollView[] tabs=new android.widget.ScrollView[3];
    private final Button[] tabButtons=new Button[3];
    private String previousQStatus="";
    private volatile SessionLog session;
    private volatile IBinder remote;
    private volatile IsoDep connection;
    private volatile boolean active, resumed, destroyed, changingRfCompatibility;
    private boolean binding, smokeRequested, smokeDone, stopping;
    private int diagnosticTestMode=-1;
    private PicturePanel pictures;
    private UpdatePanel updates;
    private Spinner transport;
    private TextView compatibilityStatus;
    private AlertDialog compatibilityNotice;
    private int marker;
    private NfcAdapter adapter;
    private TextView state, output, sessionLabel;
    private File pendingExport;
    private final StringBuilder screen=new StringBuilder();
        private final Shizuku.UserServiceArgs serviceArgs=new Shizuku.UserServiceArgs(new ComponentName("dev.inknfclab.test",DiagnosticService.class.getName()))
        .daemon(false).processNameSuffix("nfc_diagnostics").version(11).debuggable(true);
    private final ServiceConnection serviceConnection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding=false; remote=binder; show("Shizuku 诊断服务已连接（shell）"); refresh();
            if(smokeRequested && !smokeDone) runSmoke();
            runDiagnosticIntent();
        }
        @Override public void onServiceDisconnected(ComponentName name) { remote=null; binding=false; show("Shizuku 诊断服务断开"); refresh(); }
    };
    private final Shizuku.OnBinderReceivedListener received=() -> main.post(() -> {
        refresh();
        try { if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED || smokeRequested) connectShizuku(); } catch(Throwable e) { error("shizuku_received",e); }
    });
    private final Shizuku.OnBinderDeadListener dead=() -> main.post(() -> { remote=null; binding=false; show("Shizuku 已停止，系统日志采集不可用"); refresh(); });
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result) -> main.post(() -> {
        show(result==PackageManager.PERMISSION_GRANTED ? "Shizuku 已授权" : "Shizuku 未授权，仍可普通寻卡");
        if(result==PackageManager.PERMISSION_GRANTED) bind(); refresh();
    });
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            SessionLog current=session;
            if(active && current!=null && remote!=null) work.execute(() -> { try { drain(current,false); if(qSession) pollQ(current); } catch(Throwable e) { current.event("capture_error",Log.getStackTraceString(e)); } });
            if(!destroyed) main.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); adapter=NfcAdapter.getDefaultAdapter(this);
        smokeRequested=getIntent().getBooleanExtra("smoke_test",false);
        diagnosticTestMode=getIntent().getIntExtra("diagnostic_mode",-1);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setStatusBarColor(AppUi.BG);getWindow().setNavigationBarColor(AppUi.SURFACE);
        LinearLayout root=AppUi.column(this);root.setBackgroundColor(AppUi.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(AppUi.dp(this,22),AppUi.dp(this,10),AppUi.dp(this,22),AppUi.dp(this,8));
        LinearLayout brand=AppUi.column(this);brand.addView(AppUi.text(this,"喵墨",20,AppUi.INK,true));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        deviceChip=AppUi.text(this,"连接设备  ›",11,AppUi.MUTED,false);deviceChip.setPadding(AppUi.dp(this,12),AppUi.dp(this,8),AppUi.dp(this,12),AppUi.dp(this,8));deviceChip.setBackground(AppUi.shape(0x00000000,0,this));deviceChip.setOnClickListener(v->selectTab(1));header.addView(deviceChip);root.addView(header);
        FrameLayout host=new FrameLayout(this);root.addView(host,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout[] pages=new LinearLayout[3];for(int i=0;i<3;i++){tabs[i]=new ScrollView(this);tabs[i].setFillViewport(true);tabs[i].setClipToPadding(false);pages[i]=AppUi.column(this);pages[i].setPadding(AppUi.dp(this,20),AppUi.dp(this,10),AppUi.dp(this,20),AppUi.dp(this,18));tabs[i].addView(pages[i]);host.addView(tabs[i]);}
        pictures=new PicturePanel(this,(type,value)->{SessionLog current=session;if(current!=null)current.event(type,value);if(type.equals("image_prepared"))show("照片四色预览已就绪");else if(type.endsWith("error"))show(type+" / "+value);},()->begin(3),()->stopSession("user_stop"));pages[0].addView(pictures.view);
        AppUi.title(pages[1],"连接你的墨水屏","一次连接，随后即可从照片页推送。");
        LinearLayout device=AppUi.card(this);device.addView(AppUi.text(this,"华为 3.98 英寸四色屏",19,AppUi.INK,true));AppUi.gap(device,8);device.addView(AppUi.text(this,"552 × 768  ·  内置 NFC",13,AppUi.MUTED,false));AppUi.gap(device,20);state=AppUi.text(this,"",14,AppUi.INK,false);device.addView(state);AppUi.gap(device,20);device.addView(AppUi.button(this,"连接 NFC 服务",true,this::connectShizuku));AppUi.gap(device,8);device.addView(AppUi.button(this,"检测屏幕",false,()->begin(2)));pages[1].addView(device);AppUi.gap(pages[1],16);
        LinearLayout settings=AppUi.card(this);settings.addView(AppUi.text(this,"推送设置",17,AppUi.INK,true));AppUi.gap(settings,10);settings.addView(AppUi.text(this,"刷新上电等待",13,AppUi.INK,false));Spinner wait=new Spinner(this);wait.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1 秒","2 秒","3 秒","5 秒"}));int[] times={1000,2000,3000,5000};int at=3;for(int i=0;i<4;i++)if(times[i]==getSharedPreferences("refresh",0).getInt("power_wait_ms",5000))at=i;wait.setSelection(at);settings.addView(wait);wait.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int i,long id){getSharedPreferences("refresh",0).edit().putInt("power_wait_ms",times[i]).apply();}public void onNothingSelected(AdapterView<?> a){}});settings.addView(AppUi.text(this,"照片上传与刷新期间，请保持稳定供电和 NFC 贴合。",12,AppUi.MUTED,false));pages[1].addView(settings);AppUi.gap(pages[1],8);
        settings.addView(AppUi.text(this,"连接方式",13,AppUi.INK,false));
        transport=new Spinner(this);transport.setContentDescription("NFC 连接方式");transport.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动选择（推荐）","标准 NFC（跨品牌，无需 Shizuku）"}));
        transport.setSelection(getSharedPreferences("nfc_compat",0).getBoolean("standard_only",false)?1:0);settings.addView(transport);
        transport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int i,long id){boolean previous=getSharedPreferences("nfc_compat",0).getBoolean("standard_only",false);if(active||stopping){if((i==1)!=previous){transport.setSelection(previous?1:0);Toast.makeText(NfcTestActivity.this,"先停止 NFC 会话再切换连接方式",Toast.LENGTH_SHORT).show();}return;}getSharedPreferences("nfc_compat",0).edit().putBoolean("standard_only",i==1).apply();}public void onNothingSelected(AdapterView<?> a){}});
        settings.addView(AppUi.text(this,"自动模式先检查专用接口，不满足条件时使用标准 NFC。标准模式需要系统能发现屏幕，无法代发特殊唤醒短帧。",11,AppUi.MUTED,false));
        compatibilityStatus=AppUi.text(this,"兼容性尚未检测；接口可用不代表实屏已验证。",11,AppUi.MUTED,false);settings.addView(compatibilityStatus);
        settings.addView(AppUi.button(this,"检查手机兼容性",false,this::checkCompatibility));
        CheckBox qCompat=new CheckBox(this);qCompat.setText("允许无 RF 配置读回兼容（部分机型）");qCompat.setTextColor(AppUi.INK);qCompat.setTextSize(12);qCompat.setChecked(getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false));
        qCompat.setOnCheckedChangeListener((button,checked)->{
            if(changingRfCompatibility)return;
            if(active||stopping){changingRfCompatibility=true;qCompat.setChecked(!checked);changingRfCompatibility=false;Toast.makeText(this,"先停止 NFC 会话再改适配模式",Toast.LENGTH_SHORT).show();return;}
            if(!checked){getSharedPreferences("nfc_compat",0).edit().putBoolean("allow_unverified_rf_snapshot",false).apply();return;}
            qCompat.setEnabled(false);
            AlertDialog prompt=new AlertDialog.Builder(this).setTitle("启用无读回兼容模式？")
                .setMessage("仅在 Q-Poll 报‘getRfDiscoverConfigurations 不存在’时使用。启用后无法核验原 RF 配置，也无法确认射频状态已完整恢复；请先关闭其他 NFC 寻卡/刷卡页面，并确保不会同时运行别的 NFC 任务。若本机缺少 vendor NCI 或 ReaderMode 接口，仍会安全拒绝启动。")
                .setNegativeButton("取消",null)
                .setPositiveButton("我确认，仅在 NFC 空闲时使用",null)
                .create();
            prompt.setOnCancelListener(dialog->{if(!getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false)){changingRfCompatibility=true;qCompat.setChecked(false);changingRfCompatibility=false;}qCompat.setEnabled(true);});
            prompt.show();
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked->{
                getSharedPreferences("nfc_compat",0).edit().putBoolean("allow_unverified_rf_snapshot",true).commit();
                changingRfCompatibility=true;qCompat.setChecked(true);changingRfCompatibility=false;qCompat.setEnabled(true);prompt.dismiss();
            });
        });
        settings.addView(qCompat);settings.addView(AppUi.text(this,"仅针对缺少 RF 配置读取接口的 ROM；默认关闭，且不代表已验证该机型射频或面板兼容性。",11,AppUi.MUTED,false));AppUi.gap(pages[1],8);
        CheckBox stCompat=new CheckBox(this);stCompat.setText("ST54L 适配试验（小米 15 Pro 等）");stCompat.setTextColor(AppUi.INK);stCompat.setTextSize(12);
        stCompat.setChecked(getSharedPreferences("nfc_compat",0).getBoolean("allow_st54l_experiment",false));
        stCompat.setOnCheckedChangeListener((toggle,enabled)->{
            if(changingRfCompatibility)return;
            if(active||stopping){changingRfCompatibility=true;stCompat.setChecked(!enabled);changingRfCompatibility=false;Toast.makeText(this,"先停止 NFC 会话再改适配模式",Toast.LENGTH_SHORT).show();return;}
            if(!enabled){getSharedPreferences("nfc_compat",0).edit().putBoolean("allow_st54l_experiment",false).apply();return;}
            changingRfCompatibility=true;stCompat.setChecked(false);changingRfCompatibility=false;
            AlertDialog confirm=new AlertDialog.Builder(this).setTitle("ST54L 实验适配")
                .setMessage("仅在厂商诊断明确报告 ST54L 时使用。本路径先读取并保存原始短帧，再配置 7A 并读回校验；结束恢复原值。它不依赖缺失的 RF getter，也不发送 NXP QTag 命令。小米 15 Pro 尚未实机验证，可能受 ROM 权限、回调或标签派发限制；首次只点检测屏幕，保存日志。请确认没有其他 NFC 操作。")
                .setNegativeButton("取消",null).setPositiveButton("启用实验适配",null).create();
            confirm.show();confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked->{
                getSharedPreferences("nfc_compat",0).edit().putBoolean("allow_st54l_experiment",true).apply();
                changingRfCompatibility=true;stCompat.setChecked(true);changingRfCompatibility=false;confirm.dismiss();
            });
        });
        settings.addView(stCompat);settings.addView(AppUi.text(this,"按实际芯片选择独立 ST54L 路径；先读 D1，未确认寻卡前不要推图。",11,AppUi.MUTED,false));
        updates=new UpdatePanel(this,()->active);pages[1].addView(updates.view);AppUi.gap(pages[1],14);
        LinearLayout advanced=AppUi.column(this);advanced.setVisibility(View.GONE);pages[1].addView(AppUi.button(this,"高级诊断",false,()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));AppUi.gap(pages[1],10);pages[1].addView(advanced);button(advanced,"Q-Poll 寻卡（不发送屏幕命令）",()->begin(4));button(advanced,"普通 NFC 寻卡对照",()->begin(1));button(advanced,"读取厂商诊断信息",this::diagnostics);button(advanced,"仅采集 NFC 日志",()->begin(0));button(advanced,"生成验证用测试图",pictures::prepareDemo);AppUi.gap(pages[1],16);pages[1].addView(AppUi.text(this,"喵墨 "+AppVersion.name(this)+"  /  InkSight 图片处理\nShizuku 通过系统调试服务运行，无需 root。",11,AppUi.MUTED,false));
        AppUi.title(pages[2],"日志与记录","每次推送自动保存，遇到问题可导出调试。");
        LinearLayout logActions=AppUi.card(this);row(logActions,"分享日志",()->export(false),"保存日志",()->export(true));AppUi.gap(logActions,8);button(logActions,"历史会话",this::history);AppUi.gap(logActions,8);button(logActions,"停止并保存当前会话",()->stopSession("user_stop"));AppUi.gap(logActions,8);button(logActions,"添加复现标记",()->{SessionLog current=session;if(current!=null){String mark="MARK_"+(++marker);current.event("user_marker",mark);show("已添加标记 "+mark);}});pages[2].addView(logActions);AppUi.gap(pages[2],18);
        sessionLabel=AppUi.label(this,"");pages[2].addView(sessionLabel);AppUi.gap(pages[2],12);output=AppUi.text(this,"准备就绪\n",12,AppUi.INK,false);output.setTypeface(android.graphics.Typeface.MONOSPACE);output.setTextIsSelectable(true);LinearLayout console=AppUi.card(this);console.addView(output);pages[2].addView(console);AppUi.gap(pages[2],10);pages[2].addView(AppUi.text(this,"日志只存本机，导出的 ZIP 可能包含所选照片、屏幕标识和通信数据。",11,AppUi.MUTED,false));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(AppUi.dp(this,24),AppUi.dp(this,6),AppUi.dp(this,24),AppUi.dp(this,6));nav.setBackgroundColor(AppUi.SURFACE);
        String[] names={"照片","设备","日志"};for(int i=0;i<3;i++){final int index=i;tabButtons[i]=AppUi.button(this,names[i],false,()->selectTab(index));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,AppUi.dp(this,42),1);lp.setMargins(AppUi.dp(this,3),0,AppUi.dp(this,3),0);nav.addView(tabButtons[i],lp);}root.addView(nav);selectTab(0);
        try { session=new SessionLog(this); sessionLabel.setText("会话 "+session.id); } catch(Exception e) { show("会话创建失败 "+e); }
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t,e) -> { SessionLog s=session; if(s!=null) s.event("uncaught_exception",Log.getStackTraceString(e)); if(previous!=null) previous.uncaughtException(t,e); });
        Shizuku.addRequestPermissionResultListener(permission); Shizuku.addBinderDeadListener(dead); Shizuku.addBinderReceivedListenerSticky(received);
        main.post(poll); refresh();
    }
    private void selectTab(int index){for(int i=0;i<3;i++){tabs[i].setVisibility(i==index?View.VISIBLE:View.GONE);tabButtons[i].setBackground(AppUi.shape(0x00000000,0,this));tabButtons[i].setTextColor(i==index?AppUi.INK:AppUi.MUTED);tabButtons[i].setTypeface(android.graphics.Typeface.create("sans-serif",i==index?1:0));}}
    private void button(LinearLayout page,String title,Runnable action){page.addView(AppUi.button(this,title,false,action));AppUi.gap(page,7);}
    private void row(LinearLayout page,String first,Runnable a,String second,Runnable b){page.addView(AppUi.pair(this,AppUi.button(this,first,false,a),AppUi.button(this,second,false,b)));}
    private void show(String message) {
        main.post(() -> { if(destroyed) return; screen.append(message).append('\n'); if(screen.length()>18000) screen.delete(0,screen.length()-12000); output.setText(screen); });
    }
    private void error(String context,Throwable e) { SessionLog s=session; if(s!=null) s.event(context,Log.getStackTraceString(e)); show(context+": "+e); }
    private void refresh() {
        if(state==null || destroyed) return;
        String shizuku="未运行";
        try { if(Shizuku.pingBinder()) shizuku=remote!=null ? "已连接 / shell" : Shizuku.checkSelfPermission()==0 ? "已授权，待连接" : "待授权"; } catch(Throwable ignored) {}
        if(deviceChip!=null)deviceChip.setText(active?"● 操作中":remote!=null&&adapter!=null&&adapter.isEnabled()?"● 已连接":"连接设备  ›");
        state.setText("NFC："+(adapter==null ? "不支持" : adapter.isEnabled() ? "已开启" : "已关闭")+"\nShizuku："+shizuku+"\n采集："+(active ? "进行中" : "已停止"));
    }
    private void connectShizuku() {
        try {
            if(!Shizuku.pingBinder()) {
                show("请在 Shizuku 中启动服务，再回到本页连接。");
                Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api"); if(intent!=null) startActivity(intent); return;
            }
            if(Shizuku.getUid()!=2000) { show("此测试包只支持以 shell（非 root）方式启动的 Shizuku。"); return; }
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(101); else bind();
        } catch(Throwable e) { error("shizuku_connect_error",e); }
    }
    private void bind() {
        if(remote!=null || binding) return;
        try { binding=true; Shizuku.bindUserService(serviceArgs,serviceConnection); main.postDelayed(() -> { if(remote==null) { binding=false; show("诊断服务连接超时，可重试。"); refresh(); } },15000); }
        catch(Throwable e) { binding=false; error("shizuku_bind_error",e); }
    }
    private String call(int code) throws Exception {
        IBinder binder=remote; if(binder==null) throw new IOException("Shizuku 未连接");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try { data.writeInterfaceToken(DiagnosticService.DESCRIPTOR); if(!binder.transact(code,data,reply,0)) throw new IOException("Unknown service operation"); reply.readException(); String result=reply.readString(); if(result==null || result.startsWith("ERROR ")) throw new IOException(String.valueOf(result)); return result; }
        finally { data.recycle(); reply.recycle(); }
    }
    private String qStart() throws Exception {
        IBinder binder=remote;if(binder==null)throw new IOException("Shizuku 未连接");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            String serviceVersion=call(DiagnosticService.SERVICE_VERSION);
            if(!"11".equals(serviceVersion))throw new IOException("Shizuku 诊断服务版本不匹配，请重启 Shizuku，再回到喵墨连接 NFC 服务。");
            data.writeInterfaceToken(DiagnosticService.DESCRIPTOR);data.writeStrongBinder(qOwner);data.writeInt(getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false)?1:0);
            data.writeInt(getSharedPreferences("nfc_compat",0).getBoolean("allow_st54l_experiment",false)?1:0);
            if(!binder.transact(DiagnosticService.Q_START,data,reply,0))throw new IOException("Q-Poll 服务版本过旧");reply.readException();String result=reply.readString();if(result==null || result.startsWith("ERROR "))throw new IOException(result);return result;
        }
        finally {data.recycle();reply.recycle();}
    }
    private byte[] qExchange(SessionLog s,byte[] command,int token,long started) throws Exception {
        if(!active || epoch.get()!=token)throw new CancellationException("操作已停止");
        if(SystemClock.elapsedRealtime()-started>240000)throw new TimeoutException("操作超过4分钟");
        IBinder binder=remote;if(binder==null)throw new IOException("Shizuku 通道断开");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();long sent=SystemClock.elapsedRealtime();s.event("apdu_tx",Protocol.hex(command));
        try {data.writeInterfaceToken(DiagnosticService.DESCRIPTOR);data.writeByteArray(command);if(!binder.transact(DiagnosticService.Q_TRANSCEIVE,data,reply,0))throw new IOException("Q-Poll 传输不可用");reply.readException();if(reply.readInt()!=1)throw new IOException(reply.readString());byte[] response=reply.createByteArray();s.event("apdu_rx",Protocol.hex(response));s.event("transceive_ms",String.valueOf(SystemClock.elapsedRealtime()-sent));return response;}
        catch(Exception e){s.event("apdu_error",Log.getStackTraceString(e));throw e;}
        finally {data.recycle();reply.recycle();}
    }
    private void qTimeout(int timeout) {
        IBinder binder=remote;if(binder==null)throw new IllegalStateException("Shizuku 已断开");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {data.writeInterfaceToken(DiagnosticService.DESCRIPTOR);data.writeInt(timeout);if(!binder.transact(DiagnosticService.Q_TIMEOUT,data,reply,0))throw new IOException("timeout operation unsupported");reply.readException();String result=reply.readString();if(result==null || result.startsWith("ERROR "))throw new IOException(result);}
        catch(Exception e){throw new IllegalStateException(e);}finally{data.recycle();reply.recycle();}
    }
    private void pollQ(SessionLog s) throws Exception {
        if(!active || session!=s)return;
        JSONObject status=new JSONObject(call(DiagnosticService.Q_STATUS));String value=status.toString();
        if(!value.equals(previousQStatus)){s.event("q_poll_status",value);s.write("q-poll-state.json",status.toString(2));previousQStatus=value;
            if(status.optBoolean("q_discovery_accepted"))show("控制器已接受 Q-Poll，等待电子屏标签回调。");
        }
        if(!status.optBoolean("running")){main.post(() -> {if(active && session==s){show("Q-Poll 已停止："+status.optString("error","未收到标签"));stopSession("q_backend_stopped");}});return;}
        if(status.optBoolean("tag_ready") && tagSeen.compareAndSet(false,true)) {
            int token=currentToken,mode=currentMode;PicturePanel.Frame frame=currentFrame;
            nfcWork.execute(() -> readQTag(s,mode,frame,token));
            main.postDelayed(() -> {if(active && epoch.get()==token)stopSession("operation_deadline");},240000);
        }
    }
    private void readQTag(SessionLog s,int mode,PicturePanel.Frame frame,int token) {
        long started=SystemClock.elapsedRealtime();
        try {
            if(mode==4){s.event("apdu_skipped","Q-Poll tag callback received, no APDU requested");show("Q-Poll 已发现标签（未发送 APDU），可继续读取 D1。");return;}
            JSONObject info=new JSONObject(call(DiagnosticService.Q_CONNECT));s.event("tag_discovered",info.toString());show("Q-Poll 发现并连接标签\n"+info.toString(2));
            byte[] uid=decodeHex(info.getString("uid"));
            byte[] response=qExchange(s,Protocol.READ_INFO,token,started);
            String parsed=Protocol.deviceInfo(response,uid);s.event("D1_parsed",parsed);show(parsed);
            if(mode==3){
                Protocol.DeviceInfo device=Protocol.parseDeviceInfo(response,uid);
                if(!device.supportsImageTransfer() || frame==null)throw new IOException("D1 型号、四色压缩能力或 UID 不匹配，未写入图片");
                ImageTransfer.send(new ImageTransfer.TransferLink(){
                    private int timeout=3000;
                    public boolean isConnected(){return active && epoch.get()==token && remote!=null;}
                    public int getMaxTransceiveLength(){return info.optInt("max_transceive",0);}
                    public int getTimeout(){return timeout;}
                    public void setTimeout(int ms){qTimeout(ms);timeout=ms;}
                    public byte[] transceive(byte[] command)throws Exception{return qExchange(s,command,token,started);}
                },frame.image.packed,new ImageTransfer.Progress(){
                    public boolean cancelled(){return !active || epoch.get()!=token || destroyed;}
                    public void trace(String message){s.event("transfer_trace",message);}
                    public void update(String message,int done,int total){s.event("transfer_progress",message+" packets="+done+"/"+total);main.post(() -> {if(epoch.get()==token)pictures.transfer(message,done,total);});}
                },currentPowerMs);
                if(!active || epoch.get()!=token)throw new CancellationException("操作已停止，画面状态未确认");
                s.event("image_device_idle","DE=009000; visual confirmation pending");main.post(() -> pictures.result("图片已发送，设备报告空闲；请检查实屏画面。"));
            }
        }catch(Throwable e){s.event("q_tag_error",Log.getStackTraceString(e));show("Q-Poll 标签操作失败："+e);if(mode==3)main.post(() -> pictures.failed(e));}
        finally{main.post(() -> {if(active && epoch.get()==token)stopSession("q_tag_complete");});}
    }
    private static byte[] decodeHex(String value){byte[] bytes=new byte[value.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(value.substring(2*i,2*i+2),16);return bytes;}
    private void diagnostics() {
        if(remote==null) { show("先连接 Shizuku 后可读取厂商接口。"); return; }
        SessionLog s=session;
        if(s==null) return;
        work.execute(() -> { try { String value=call(DiagnosticService.INFO); s.write("vendor-info.json",value); s.event("vendor_info",value); show("设备只读诊断：\n"+value); } catch(Throwable e) { s.event("vendor_info_error",Log.getStackTraceString(e)); show("诊断失败 "+e); } });
    }
    private boolean standardOnly(){return getSharedPreferences("nfc_compat",0).getBoolean("standard_only",false);}
    private JSONObject compatibilityReport(boolean probeShell)throws Exception{
        JSONObject report=new JSONObject().put("app",AppVersion.name(this)).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
            .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("nfc_hardware",adapter!=null)
            .put("nfc_enabled",adapter!=null&&adapter.isEnabled()).put("standard_only",standardOnly()).put("shell_connected",remote!=null)
            .put("route",NfcCapabilities.STANDARD).put("vendor_ready",false)
            .put("support_status","not_checked")
            .put("reason","标准 NFC 不依赖手机品牌或 Shizuku。须先由系统发现并激活屏幕为 ISO-DEP，才能读取 D1 和推图；本模式不发送 7A 唤醒短帧。")
            .put("scope","只读接口检查；未寻卡、未发送 APDU，不代表实屏验证通过");
        if(probeShell&&remote!=null){
            String serviceVersion=call(DiagnosticService.SERVICE_VERSION);report.put("diagnostic_service_version",serviceVersion);
            if(!"11".equals(serviceVersion))return report.put("diagnostic_service_compatible",false).put("route",NfcCapabilities.STANDARD).put("vendor_ready",false).put("support_status","service_stale")
                .put("reason","Shizuku NFC 服务版本较旧（"+serviceVersion+"）；未调用厂商接口，将尝试标准 NFC。要使用专用寻卡，请重启 Shizuku 后重新连接。")
                .put("diagnostic_service_error","expected 11, received "+serviceVersion);
            report.put("diagnostic_service_compatible",true);
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();IBinder binder=remote;
            try{
                if(binder==null)throw new IOException("Shizuku 通道断开，请重试兼容性检测。");
                data.writeInterfaceToken(DiagnosticService.DESCRIPTOR);
                data.writeInt(getSharedPreferences("nfc_compat",0).getBoolean("allow_unverified_rf_snapshot",false)?1:0);
                data.writeInt(getSharedPreferences("nfc_compat",0).getBoolean("allow_st54l_experiment",false)?1:0);
                if(!binder.transact(DiagnosticService.CAPABILITIES,data,reply,0))throw new IOException("诊断服务不支持兼容性检测，请重启 Shizuku。");
                reply.readException();String value=reply.readString();if(value==null||value.startsWith("ERROR "))throw new IOException(value);
                JSONObject result=new JSONObject(value);for(Iterator<String> keys=result.keys();keys.hasNext();){String key=keys.next();report.put(key,result.get(key));}
            }finally{data.recycle();reply.recycle();}
        }
        if(adapter==null)report.put("route","unavailable").put("vendor_ready",false).put("reason","未检测到 NFC 硬件；仍可编辑和保存照片预览。");
        return report;
    }
    private void checkCompatibility(){
        if(active||stopping){Toast.makeText(this,"先停止 NFC 会话再检查兼容性",Toast.LENGTH_SHORT).show();return;}
        compatibilityStatus.setText("正在检查手机接口…");
        work.execute(()->{try{
            JSONObject report=compatibilityReport(remote!=null);SessionLog s=session;
            if(s!=null){recordCompatibility(s,report,"manual_check");collectFeedbackDiagnostics(s);}
            String message=Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n"+
                "NFC："+(report.optBoolean("nfc_hardware")?(report.optBoolean("nfc_enabled")?"已开启":"未开启"):"无硬件")+"\n"+
                "可尝试通道："+report.optString("route")+"\n\n"+report.optString("reason")+"\n\n这是只读检查，结果已记入本次日志；实际兼容需依次验证寻卡、D1 和刷新。";
            main.post(()->{if(destroyed||!resumed)return;compatibilityStatus.setText(report.optString("reason"));dismissCompatibilityNotice();compatibilityNotice=new AlertDialog.Builder(this).setTitle("手机兼容性").setMessage(message).setPositiveButton("知道了",null).setNeutralButton("导出适配反馈",(d,w)->export(false)).show();});
        }catch(Throwable e){SessionLog s=session;if(s!=null){s.event("compatibility_error",Log.getStackTraceString(e));try{recordCompatibility(s,compatibilityReport(false).put("support_status","probe_failed").put("probe_error",Log.getStackTraceString(e)),"manual_check_failed");}catch(Exception ignored){}}
            main.post(()->{if(!destroyed&&resumed){compatibilityStatus.setText("检测未完成："+e.getMessage());dismissCompatibilityNotice();compatibilityNotice=new AlertDialog.Builder(this).setTitle("兼容性检测未完成").setMessage("本次未能读取完整接口信息，不能据此判断手机不支持。请确认 Shizuku 已启动并授权，再重试；也可导出本次反馈日志。 ").setPositiveButton("知道了",null).setNeutralButton("导出适配反馈",(d,w)->export(false)).show();}});}});
    }
    private void dismissCompatibilityNotice(){if(compatibilityNotice!=null){compatibilityNotice.dismiss();compatibilityNotice=null;}}
    private void recordCompatibility(SessionLog s,JSONObject report,String stage)throws Exception{
        s.write("compatibility.json",report.toString(2));s.event("compatibility_report",report.toString());
        JSONObject permissions=new JSONObject().put("nfc_permission",checkSelfPermission(android.Manifest.permission.NFC)==PackageManager.PERMISSION_GRANTED)
            .put("shell_connected",remote!=null);
        try{boolean running=Shizuku.pingBinder();permissions.put("shizuku_running",running);if(running)permissions.put("shizuku_permission",Shizuku.checkSelfPermission()).put("shizuku_uid",Shizuku.getUid());}catch(Throwable unavailable){permissions.put("shizuku_check_error",unavailable.toString());}
        JSONObject device=new JSONObject().put("manufacturer",Build.MANUFACTURER).put("brand",Build.BRAND).put("model",Build.MODEL).put("device",Build.DEVICE)
            .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("display",Build.DISPLAY).put("fingerprint",Build.FINGERPRINT).put("security_patch",Build.VERSION.SECURITY_PATCH);
        JSONObject feedback=new JSONObject().put("schema_version",1).put("session",s.id).put("app",AppVersion.name(this)).put("stage",stage)
            .put("recorded_at_ms",System.currentTimeMillis()).put("device",device).put("permissions",permissions).put("compatibility",report)
            .put("evidence_scope","interface inspection and recorded session events; not proof of RF/Tag/D1/refresh success")
            .put("related_files",new JSONArray(Arrays.asList("environment.json","vendor-info.json","compatibility.json","events.jsonl","nfc-system.log","q-poll-state.json","q-poll-cleanup.json","nfc-before.txt","nfc-after.txt")));
        s.write("compatibility-feedback.json",feedback.toString(2));
    }
    private void collectFeedbackDiagnostics(SessionLog s){
        if(remote==null){s.event("feedback_diagnostics_unavailable","Shizuku shell not connected; interfaces have not been inspected");return;}
        try{s.write("vendor-info.json",call(DiagnosticService.INFO));}catch(Throwable failed){s.event("feedback_diagnostics_error",Log.getStackTraceString(failed));}
    }
    private void showInterfaceNotice(SessionLog s,JSONObject report,boolean usingStandard){
        String status=report.optString("support_status");
        if(!Arrays.asList("missing_interfaces","unadapted_controller","unidentified_controller","command_rejected").contains(status))return;
        final String title=status.equals("missing_interfaces")?"系统缺少专用 NFC 接口":status.equals("command_rejected")?"专用唤醒暂不可用":"检测到接口，暂未确认适配";
        JSONArray missing=report.optJSONArray("missing_interfaces");StringBuilder detail=new StringBuilder();
        if(missing!=null&&missing.length()>0){detail.append("本次未检测到：");for(int i=0;i<missing.length();i++)detail.append(i==0?"":"、").append(missing.optString(i));detail.append("。\n\n");}
        detail.append(usingStandard?"已尝试使用标准 NFC；如果系统无法发现这块屏，当前仍不能刷图。":"本次无法启动专用寻卡。");
        detail.append("\n\n").append(report.optString("reason")).append("\n\n已记录系统、芯片与接口信息。请导出适配反馈，并补充手机系统版本、屏幕型号和复现步骤，方便后续适配。");
        s.event("interface_notice",status);
        main.post(()->{if(destroyed||!resumed||session!=s)return;dismissCompatibilityNotice();compatibilityNotice=new AlertDialog.Builder(this).setTitle(title).setMessage(detail.toString()).setPositiveButton("知道了",null).setNeutralButton("导出适配反馈",(d,w)->export(false)).show();});
    }
    private JSONObject confirmedStReadRejection(SessionLog s,JSONObject report,Throwable failure,int mode){
        String detail=Log.getStackTraceString(failure);
        if(mode==4||!NfcCapabilities.ST.equals(report.optString("route"))||!"ST54L".equalsIgnoreCase(report.optString("chip"))
            ||!detail.contains("ST command 0x15 response rejected:"))return null;
        try{
            JSONObject state=new JSONObject(call(DiagnosticService.Q_STATUS));
            boolean clean=!state.optBoolean("running")&&!state.optBoolean("st_frames_configured")
                &&!state.optBoolean("q_discovery_sent")&&"not_required".equals(state.optString("restore"))
                &&"st54l_custom_frames".equals(state.optString("controller_profile"))
                &&state.optString("error").contains("ST command 0x15 response rejected:")
                &&detail.contains(state.optString("error"));
            if(clean)return state;
            s.event("st_fallback_blocked",state.toString());
        }catch(Throwable verify){s.event("st_fallback_verify_error",Log.getStackTraceString(verify));}
        return null;
    }
    private static String rejectedStStatus(JSONObject state){
        String error=state.optString("error");String marker="response rejected:";int at=error.indexOf(marker);
        if(at<0)return "unknown";String response=error.substring(at+marker.length()).trim();
        if(response.length()<4)return "unknown";String status=response.substring(2,4).toUpperCase(Locale.ROOT);
        return status.matches("[0-9A-F]{2}")?status:"unknown";
    }
    private void begin(int mode) {
        if(!resumed) return;
        if(active || stopping) { show("请先停止当前会话，等待保存完成后再开始。"); return; }
        PicturePanel.Frame frame=mode==3 ? pictures.frame() : null;
        if(mode==3 && frame==null) { show("请先选择图片，等待四色预览生成。"); return; }
        if(mode!=0 && (adapter==null || !adapter.isEnabled())) { show("请先开启系统 NFC。"); return; }
        boolean probeVendor=NfcCapabilities.probeVendor(mode,standardOnly(),remote!=null);
        if(mode==4&&remote==null){show("专用 Q-Poll 对照需要 Shizuku；检测屏幕和推图可使用标准 NFC。");connectShizuku();return;}
        try { session=new SessionLog(this); } catch(Exception e) { error("session_error",e); return; }
        SessionLog s=session; int token=epoch.incrementAndGet(); active=true; tagSeen.set(false); pictures.setBusy(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        qSession=false;currentMode=mode;currentToken=token;currentFrame=frame;previousQStatus="";
        currentPowerMs=pictures.powerWaitMs();if(mode==3)s.event("refresh_settings","power_wait_ms="+currentPowerMs);
        s.event("transport",probeVendor ? "auto_preflight" : "standard_reader");
        sessionLabel.setText("会话 "+s.id); s.event("mode",mode==0 ? "log_only" : mode==1 ? "reader_no_apdu" : mode==2 ? "reader_D1_00D1000000" : mode==4 ? "qpoll_no_apdu" : "image_push");
        show("新会话 "+s.id+" / "+(mode==0 ? "只采集日志" : mode==1 ? "普通寻卡：不发送 APDU" : mode==2 ? "寻卡并读取 D1：00D1000000" : mode==4 ? "Q-Poll 寻卡：不发送 APDU" : "实机推图：请将电子屏贴稳，保持本页前台"));
        if(mode==3) pictures.result("等待发现屏幕 · 保持贴合");
        work.execute(() -> {
            try {
                if(frame!=null) {
                    s.write("image-metadata.json",frame.metadata.toString(2)); s.writeBytes("image-packed.bin",frame.image.packed);
                    java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();
                    if(!frame.image.preview.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png)) throw new IOException("预览图保存失败");
                    s.writeBytes("image-preview.png",png.toByteArray());
                }
                if(remote==null) { s.event("system_capture_unavailable","Shizuku not connected"); show("仅记录应用日志：Shizuku 未连接。"); }
                else { s.event("capture_start",call(DiagnosticService.START)); String info=call(DiagnosticService.INFO); s.write("vendor-info.json",info); s.write("nfc-before.txt",call(DiagnosticService.SNAPSHOT)); }
            } catch(Throwable e) { s.event("capture_start_error",Log.getStackTraceString(e)); show("系统日志启动失败 "+e); }
            try{
                if(!active||epoch.get()!=token)return;
                JSONObject checked;
                try{checked=compatibilityReport(probeVendor);}
                catch(Exception preflight){
                    if(!probeVendor||mode==4)throw preflight;
                    s.event("vendor_preflight_error",Log.getStackTraceString(preflight));
                    checked=compatibilityReport(false).put("support_status","probe_failed").put("probe_error",Log.getStackTraceString(preflight)).put("reason","专用 NFC 能力检查失败："+preflight.getMessage()+" 将尝试标准 NFC；尚未启动厂商寻卡。");
                }
                final JSONObject report=checked;
                recordCompatibility(s,report,"preflight");
                boolean vendor=probeVendor&&report.optBoolean("vendor_ready");
                if(probeVendor)showInterfaceNotice(s,report,mode!=4&&!vendor);
                if(mode==4&&!vendor)throw new IOException(report.optString("reason"));
                if(vendor){
                    if(!active||epoch.get()!=token)return;qSession=true;
                    String qStatus;
                    try{qStatus=qStart();}
                    catch(Throwable vendorFailure){
                        JSONObject clean=confirmedStReadRejection(s,report,vendorFailure,mode);
                        if(clean==null)throw vendorFailure;
                        qSession=false;String code=rejectedStStatus(clean);
                        JSONObject standard=new JSONObject(report.toString()).put("route",NfcCapabilities.STANDARD).put("vendor_ready",false)
                            .put("fallback_from",NfcCapabilities.ST).put("st_rejected_status",code)
                            .put("support_status","command_rejected")
                            .put("reason","ST54L 读取轮询配置被控制器拒绝（"+code+"）；清理确认未修改配置。已改用标准 NFC；若未发现屏幕，此系统通道无法代发 7A 唤醒帧。");
                        s.event("st_vendor_read_rejected",Log.getStackTraceString(vendorFailure));
                        s.event("st_vendor_read_rejected_status",code);
                        s.write("st-fallback-cleanup.json",clean.toString(2));recordCompatibility(s,standard,"st_query_rejected");showInterfaceNotice(s,standard,true);
                        s.event("transport_fallback",standard.toString());s.event("transport_selected",NfcCapabilities.STANDARD);
                        show("ST54L 读取配置失败（"+code+"），确认未写入射频配置；改用标准 NFC。若屏幕未被系统激活，将无法继续读取 D1。");
                        main.post(()->{if(active&&epoch.get()==token){compatibilityStatus.setText(standard.optString("reason"));enableReader(s,mode,frame,token);}});
                        return;
                    }
                    s.event("q_start",qStatus);
                    if(!active||epoch.get()!=token){s.event("q_cancel_cleanup",call(DiagnosticService.Q_STOP));return;}
                    String selected=new JSONObject(qStatus).optString("controller_profile","unknown");s.event("transport_selected",selected);
                    boolean unverified=new JSONObject(qStatus).optString("restore").equals("not_verifiable");
                    if(unverified)s.event("rf_snapshot_unverified","manual opt-in enabled; verify NFC status after session");
                    show("NFC 寻卡已开始（"+selected+"），贴稳电子屏。第一次建议只读 D1。"+(unverified?" RF 配置无法读回，结束后请核对 NFC 状态。":""));
                }else{
                    s.event("transport_selected",NfcCapabilities.STANDARD);show("使用标准 NFC："+report.optString("reason"));
                    main.post(()->{if(active&&epoch.get()==token&&mode!=0){compatibilityStatus.setText(report.optString("reason"));enableReader(s,mode,frame,token);}});
                }
            }catch(Throwable e){s.event("transport_start_error",Log.getStackTraceString(e));try{JSONObject failed=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(s.dir,"compatibility.json").toPath()),"UTF-8")).put("start_error",Log.getStackTraceString(e));recordCompatibility(s,failed,"transport_start_failed");}catch(Exception logging){s.event("feedback_write_error",logging.toString());}show("NFC 通道开启失败："+e);main.post(()->{if(active&&epoch.get()==token){if(mode==3)pictures.result("NFC 通道未能开启，请在设备页检查手机兼容性，详见日志。");stopSession("transport_start_error");}});}
        });
        main.postDelayed(() -> { if(active && epoch.get()==token && (mode==0 || !tagSeen.get())) {
            s.event("deadline",mode==0 ? "600 seconds" : "60 seconds; tag_seen=false");
            show(mode==0 ? "采集达到 10 分钟" : "60 秒内未收到标签，已保存控制器与寻卡日志。检查线圈位置并导出本次日志。");
            if(mode==3) pictures.result(qSession?"未发现屏幕，尚未发送任何图像数据。请导出日志。":"标准 NFC 未发现屏幕。此屏可能需要手机系统未开放的专用唤醒功能；请检查兼容性并导出日志。"); stopSession("deadline");
        } },mode==0 ? 600000 : 60000);
        refresh();
    }
    private void enableReader(SessionLog s,int mode,PicturePanel.Frame frame,int token) {
            try {
                Bundle options=new Bundle(); options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,250);
                adapter.enableReaderMode(this, tag -> {
                    if(!active || epoch.get()!=token || !tagSeen.compareAndSet(false,true)) return;
                    nfcWork.execute(() -> readTag(s,tag,mode,frame,token));
                    main.postDelayed(() -> { if(active && epoch.get()==token) { if(mode==3) pictures.result("操作超过 4 分钟，已停止；画面状态未确认。"); stopSession("operation_deadline"); } },240000);
                },NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK|NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,options);
                s.event("reader_enabled","NFC_A, NFC_B, SKIP_NDEF_CHECK, NO_PLATFORM_SOUNDS");
            } catch(Throwable e) { error("reader_enable_error",e); stopSession("reader_enable_error"); return; }
    }
    private byte[] exchange(SessionLog s,IsoDep iso,byte[] command,int token,long started) throws Exception {
        if(!active || epoch.get()!=token) throw new CancellationException("操作已停止");
        if(SystemClock.elapsedRealtime()-started>240000) throw new TimeoutException("操作超过 4 分钟");
        s.event("apdu_tx",Protocol.hex(command)); long sent=SystemClock.elapsedRealtime();
        try { byte[] response=iso.transceive(command); s.event("apdu_rx",Protocol.hex(response)); s.event("transceive_ms",String.valueOf(SystemClock.elapsedRealtime()-sent)); return response; }
        catch(Exception e) { s.event("apdu_error","command="+Protocol.hex(command)+" elapsed_ms="+(SystemClock.elapsedRealtime()-sent)+" "+Log.getStackTraceString(e)); throw e; }
    }
    private void readTag(SessionLog s,Tag tag,int mode,PicturePanel.Frame frame,int token) {
        if(!active || epoch.get()!=token) { s.event("tag_cancelled","session no longer active"); return; }
        long start=SystemClock.elapsedRealtime(); IsoDep opened=null;
        try {
            JSONObject info=new JSONObject().put("uid",Protocol.hex(tag.getId())).put("tech",new JSONArray(Arrays.asList(tag.getTechList())));
            NfcA a=NfcA.get(tag); if(a!=null) info.put("atqa",Protocol.hex(a.getAtqa())).put("sak",a.getSak()&65535);
            s.event("tag_discovered",info.toString()); show("发现标签\n"+info.toString(2));
            if(mode==1) { s.event("apdu_skipped","ordinary reader mode"); return; }
            IsoDep iso=IsoDep.get(tag);
            if(iso==null) throw new IOException("标签没有 IsoDep，无法走标准 D1 / 推图接口");
            connection=iso; opened=iso;
            s.event("iso_dep",new JSONObject().put("historical",Protocol.hex(iso.getHistoricalBytes())).put("hi_layer",Protocol.hex(iso.getHiLayerResponse())).put("extended_apdu",iso.isExtendedLengthApduSupported()).put("max_transceive",iso.getMaxTransceiveLength()).toString());
            iso.setTimeout(3000); long connect=SystemClock.elapsedRealtime(); iso.connect(); s.event("connect_ms",String.valueOf(SystemClock.elapsedRealtime()-connect));
            if(!active || epoch.get()!=token) { s.event("D1_cancelled","session stopped before transceive"); return; }
            byte[] response=exchange(s,iso,Protocol.READ_INFO,token,start);
            try { String parsed=Protocol.deviceInfo(response,tag.getId()); s.event("D1_parsed",parsed); show(parsed); }
            catch(Exception e) { s.event("D1_parse_error",Log.getStackTraceString(e)); throw e; }
            if(mode==3) {
                Protocol.DeviceInfo device=Protocol.parseDeviceInfo(response,tag.getId());
                if(!device.supportsImageTransfer()) throw new IllegalStateException("D1 型号、四色压缩能力或 UID 不匹配，未开始写图");
                if(frame==null) throw new IllegalStateException("图片未准备完成");
                s.event("image_target_verified",frame.metadata.toString());
                ImageTransfer.send(new ImageTransfer.TransferLink() {
                    @Override public boolean isConnected() { return iso.isConnected(); }
                    @Override public int getMaxTransceiveLength() { return iso.getMaxTransceiveLength(); }
                    @Override public int getTimeout() { return iso.getTimeout(); }
                    @Override public void setTimeout(int ms) { iso.setTimeout(ms); }
                    @Override public byte[] transceive(byte[] command) throws Exception { return exchange(s,iso,command,token,start); }
                },frame.image.packed,new ImageTransfer.Progress() {
                    @Override public boolean cancelled() { return !active || epoch.get()!=token || destroyed; }
                    @Override public void trace(String message) { s.event("transfer_trace",message); }
                    @Override public void update(String message,int completed,int total) {
                        s.event("transfer_progress",message+" packets="+completed+"/"+total);
                        main.post(() -> { if(epoch.get()==token) pictures.transfer(message,completed,total); });
                    }
                },currentPowerMs);
                if(!active || epoch.get()!=token) throw new CancellationException("操作已停止，画面状态未确认");
                s.event("image_device_idle","DE=009000; visual confirmation pending");
                main.post(() -> pictures.result("图片已发送，设备报告空闲；请检查实屏画面。"));
                show("图片已发送，设备报告空闲；请检查实屏画面。");
            }
        } catch(Throwable e) { s.event("tag_error",Log.getStackTraceString(e)); show("标签操作未完成："+e); if(mode==3) main.post(() -> pictures.failed(e)); }
        finally {
            connection=null; if(opened!=null) try { opened.close(); } catch(IOException e) { s.event("close_error",e.toString()); }
            s.event("tag_operation_ms",String.valueOf(SystemClock.elapsedRealtime()-start));
            main.post(() -> { if(active && epoch.get()==token) stopSession("tag_complete"); });
        }
    }
    private void drain(SessionLog s,boolean all) throws Exception {
        for(int i=0;i<(all ? 32 : 1);i++) {
            JSONObject result=new JSONObject(call(DiagnosticService.DRAIN)); s.system(result.getString("text"));
            s.write("capture-state.json",result.toString(2));
            if(result.getInt("buffered_chars")==0) break;
        }
    }
    private void stopSession(String reason) {
        if(!active) return;
        if(reason.equals("user_stop")||reason.equals("activity_background"))pictures.result("已停止，画面状态未确认。可以在日志页查看记录。");
        active=false; stopping=true; epoch.incrementAndGet(); SessionLog s=session;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean wasQ=qSession;qSession=false;
        try { if(!wasQ && adapter!=null) adapter.disableReaderMode(this); } catch(Throwable e) { if(s!=null) s.event("reader_disable_error",e.toString()); }
        if(wasQ && remote!=null)new Thread(() -> {try{String result=call(DiagnosticService.Q_STOP);if(s!=null){s.write("q-poll-cleanup.json",result);s.event("q_cleanup",result);}}catch(Throwable e){if(s!=null)s.event("q_cleanup_error",Log.getStackTraceString(e));}},"q-stop").start();
        IsoDep iso=connection; if(iso!=null) new Thread(() -> { try { iso.close(); } catch(IOException ignored) {} },"close-iso").start();
        Future<?> transportFinished=nfcWork.submit(() -> {});
        if(s!=null) { s.event("stop_requested",reason); work.execute(() -> {
            try { transportFinished.get(65,TimeUnit.SECONDS); } catch(Exception e) { s.event("transport_stop_wait_error",e.toString()); }
            try { if(remote!=null) { drain(s,true); s.event("capture_stop",call(DiagnosticService.STOP)); drain(s,true); s.write("nfc-after.txt",call(DiagnosticService.SNAPSHOT)); } }
            catch(Throwable e) { s.event("capture_stop_error",Log.getStackTraceString(e)); }
            s.event("session_stopped",reason); show("已停止并保存会话 "+s.id);
            main.post(() -> { stopping=false; if(pictures!=null) pictures.setBusy(false); refresh(); });
        }); }
        refresh();
    }
    private Uri exportUri(File file) { return Uri.parse("content://dev.inknfclab.test.logs/"+file.getName()); }
    private void export(boolean save) {
        stopSession("export"); SessionLog s=session; if(s==null) return;
        work.execute(() -> {
            try { s.event("export_requested",save ? "save" : "share"); File file=s.export(this); main.post(() -> offerExport(file,save)); }
            catch(Throwable e) { error("export_error",e); }
        });
    }
    private void offerExport(File file,boolean save) {
        try {
            pendingExport=file;
            if(save) { Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,file.getName()); startActivityForResult(intent,201); }
            else { Uri uri=exportUri(file); Intent intent=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.setClipData(ClipData.newRawUri("NFC log",uri)); startActivity(Intent.createChooser(intent,"分享 NFC 调试日志")); }
        } catch(Throwable e) { error("export_ui_error",e); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(pictures.onResult(request,result,data)) return;
        if(request==201 && result==RESULT_OK && data!=null && data.getData()!=null && pendingExport!=null) {
            Uri uri=data.getData(); File file=pendingExport;
            work.execute(() -> { try(InputStream in=new FileInputStream(file); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IOException("无法打开目标文件"); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); show("日志已保存："+file.getName());
            } catch(Throwable e) { error("save_error",e); } });
        }
    }
    private void history() {
        stopSession("history");
        File parent=new File(getFilesDir(),"sessions"); File[] dirs=parent.listFiles(File::isDirectory);
        if(dirs==null || dirs.length==0) { show("暂无历史会话"); return; }
        Arrays.sort(dirs,(a,b) -> b.getName().compareTo(a.getName())); String[] names=new String[dirs.length]; for(int i=0;i<dirs.length;i++) names[i]=dirs[i].getName();
        new AlertDialog.Builder(this).setTitle("历史会话").setItems(names,(dialog,index) -> {
            File folder=dirs[index];
            new AlertDialog.Builder(this).setTitle(folder.getName()).setItems(new String[]{"分享 ZIP","保存 ZIP","删除该会话"},(d,choice) -> {
                if(choice==2) { new AlertDialog.Builder(this).setMessage("删除该会话及其导出 ZIP？").setPositiveButton("删除",(x,y) -> {
                    if(session!=null && session.dir.equals(folder)) { show("当前会话请开始新会话后再删除。"); return; }
                    work.execute(() -> { File[] entries=folder.listFiles(); if(entries!=null) for(File f:entries) f.delete(); folder.delete(); new File(getFilesDir(),"exports/ink-nfc-"+folder.getName()+".zip").delete(); show("已删除 "+folder.getName()); });
                }).setNegativeButton("取消",null).show(); return; }
                work.execute(() -> { try {
                    File target=new File(getFilesDir(),"exports/ink-nfc-"+folder.getName()+".zip"); target.getParentFile().mkdirs();
                    try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(target))) { File[] files=folder.listFiles(); if(files!=null) for(File f:files) if(f.isFile()) { out.putNextEntry(new ZipEntry(folder.getName()+"/"+f.getName())); try(InputStream in=new FileInputStream(f)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); } out.closeEntry(); } }
                    main.post(() -> offerExport(target,choice==1));
                } catch(Throwable e) { error("history_export_error",e); } });
            }).show();
        }).show();
    }
    private void runSmoke() {
        if(!resumed) { main.postDelayed(() -> { if(!destroyed && !smokeDone) runSmoke(); },300); return; }
        smokeDone=true; begin(0); SessionLog s=session;
        // Image preparation uses Android's real bitmap pipeline, without any NFC write.
        pictures.setBusy(false); pictures.prepareDemo(); pictures.setBusy(true);
        work.execute(() -> { s.event("smoke_marker","SMOKE_CAPTURE_"+s.id); });
        main.postDelayed(() -> {
            stopSession("smoke_complete");
            work.execute(() -> {
                try {
                    String info=new String(java.nio.file.Files.readAllBytes(new File(s.dir,"vendor-info.json").toPath()),"UTF-8");
                    String log=new String(java.nio.file.Files.readAllBytes(new File(s.dir,"nfc-system.log").toPath()),"UTF-8");
                    JSONObject results=new JSONObject().put("uid",new JSONObject(info).getInt("uid")).put("system_log_marker",log.contains("SMOKE_CAPTURE_"+s.id)).put("nfc_radio_tested",false);
                    PicturePanel.Frame frame=pictures.frame();
                    boolean imageReady=frame!=null && frame.image.packed.length==105984 && frame.image.preview.getWidth()==552 && frame.image.preview.getHeight()==768;
                    results.put("image_pipeline_ready",imageReady);
                    if(imageReady) {
                        int[] pixels=new int[ImageCodec.PIXEL_COUNT]; frame.image.preview.getPixels(pixels,0,552,0,0,552,768); int matched=0;
                        for(int y=0;y<768;y++) for(int x=0;x<552;x++) {
                            int at=(551-x)*192+(767-y)/4,shift=(3-(767-y)%4)*2;
                            if(pixels[y*552+x]==frame.image.palette[(frame.image.packed[at]>>>shift)&3]) matched++;
                        }
                        results.put("preview_payload_pixels_matched",matched).put("image_sha256",frame.metadata.getString("sha256"));
                        s.write("image-metadata.json",frame.metadata.toString(2)); s.writeBytes("image-packed.bin",frame.image.packed);
                        java.io.ByteArrayOutputStream fitted=new java.io.ByteArrayOutputStream();frame.image.originalPreview.compress(android.graphics.Bitmap.CompressFormat.PNG,100,fitted);s.writeBytes("image-fitted.png",fitted.toByteArray());
                        java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream(); frame.image.preview.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png); s.writeBytes("image-preview.png",png.toByteArray());
                    }
                    s.write("smoke-results.json",results.toString(2)); File zip=s.export(this); int entries=0;
                    try(ZipInputStream in=new ZipInputStream(getContentResolver().openInputStream(exportUri(zip)))) { while(in.getNextEntry()!=null) { entries++; byte[] b=new byte[8192]; while(in.read(b)!=-1) {} } }
                    boolean writeBlocked=false,pathBlocked=false;
                    try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(exportUri(zip),"w")) {} catch(FileNotFoundException e) { writeBlocked=true; }
                    try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse("content://dev.inknfclab.test.logs/../environment.json"),"r")) {} catch(FileNotFoundException e) { pathBlocked=true; }
                    results.put("provider_zip_entries",entries).put("provider_write_blocked",writeBlocked).put("provider_path_blocked",pathBlocked)
                        .put("pass",results.getInt("uid")==2000 && results.getBoolean("system_log_marker") && entries>=7 && writeBlocked && pathBlocked && imageReady && results.optInt("preview_payload_pixels_matched")==423936);
                    s.write("smoke-results.json",results.toString(2)); s.export(this);
                    Log.i("InkNfcTest",results.getBoolean("pass") ? "SMOKE_PASS "+zip.getName() : "SMOKE_FAIL "+results); show("无屏自检：\n"+results.toString(2));
                } catch(Throwable e) { s.event("smoke_error",Log.getStackTraceString(e)); show("无屏自检失败 "+e); }
            });
        },3500);
    }
    private void runDiagnosticIntent() {
        if(!resumed || remote==null || active || stopping)return;
        int mode=diagnosticTestMode;
        if(mode!=1 && mode!=2 && mode!=4)return;
        diagnosticTestMode=-1;begin(mode);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(intent.getBooleanExtra("smoke_test",false)) { smokeRequested=true; smokeDone=false; if(remote!=null) runSmoke(); else connectShizuku(); } diagnosticTestMode=intent.getIntExtra("diagnostic_mode",-1); if(diagnosticTestMode>=0){if(remote!=null)runDiagnosticIntent();else connectShizuku();} }
    @Override protected void onResume() { super.onResume(); resumed=true; refresh(); runDiagnosticIntent(); if(updates!=null)updates.foreground(true); }
    @Override protected void onPause() { resumed=false; dismissCompatibilityNotice(); if(updates!=null)updates.foreground(false); stopSession("activity_background"); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed=true; main.removeCallbacksAndMessages(null); stopSession("destroy");
        if(updates!=null)updates.close(); pictures.close(); nfcWork.shutdown();
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission);
        work.execute(() -> { try { if(remote!=null || binding) Shizuku.unbindUserService(serviceArgs,serviceConnection,true); } catch(Throwable ignored) {} remote=null; });
        work.shutdown(); super.onDestroy();
    }
}
