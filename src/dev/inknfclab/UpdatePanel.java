package dev.inknfclab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

final class UpdatePanel {
    static final long CHECK_INTERVAL=24L*60*60*1000;
    final LinearLayout view;
    private final Activity activity;
    private final BooleanSupplier busy;
    private final GitHubUpdates.Fetcher fetcher;
    private final SharedPreferences preferences;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"github-update");t.setDaemon(true);return t;});
    private final Button check;
    private final TextView status;
    private final String current;
    private final boolean includePrereleases;
    private boolean running,foreground,closed;
    private GitHubUpdates.Release available;
    private AlertDialog dialog;

    UpdatePanel(Activity activity,BooleanSupplier busy){this(activity,busy,GitHubUpdates::fetch);}
    UpdatePanel(Activity activity,BooleanSupplier busy,GitHubUpdates.Fetcher fetcher){
        this.activity=activity;this.busy=busy;this.fetcher=fetcher;
        current=AppVersion.name(activity);UpdateVersion parsed=UpdateVersion.parse(current);includePrereleases=parsed!=null&&parsed.isPrerelease();
        preferences=activity.getSharedPreferences("app_updates",0);
        view=AppUi.card(activity);view.addView(AppUi.text(activity,"应用更新",15,AppUi.INK,true));AppUi.gap(view,5);
        view.addView(AppUi.text(activity,"当前版本 "+current+"\n"+(includePrereleases?"接收测试版和正式版更新":"接收正式版更新"),11,AppUi.MUTED,false));AppUi.gap(view,10);
        check=AppUi.button(activity,"检查更新",true,()->{if(available!=null)showRelease(available);else check(true);});
        view.addView(AppUi.pair(activity,check,AppUi.button(activity,"GitHub 发布页",false,()->open(GitHubUpdates.RELEASES))));AppUi.gap(view,9);
        status=AppUi.text(activity,"手动检查 GitHub 是否发布新版本。",11,AppUi.MUTED,false);view.addView(status);AppUi.gap(view,8);
        Switch auto=new Switch(activity);auto.setText("启动时检查更新（每天最多一次）");auto.setTextSize(12);auto.setTextColor(AppUi.INK);auto.setChecked(preferences.getBoolean("automatic",false));
        auto.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean("automatic",checked).apply();if(checked)maybeCheck();});view.addView(auto);
    }
    static boolean due(boolean enabled,long now,long last){return enabled&&(last<=0||now<last||now-last>=CHECK_INTERVAL);}
    void foreground(boolean value){
        foreground=value;
        if(value)maybeCheck();else if(dialog!=null){dialog.dismiss();dialog=null;}
    }
    private void maybeCheck(){
        if(!closed&&foreground&&!running&&!busy.getAsBoolean()&&due(preferences.getBoolean("automatic",false),System.currentTimeMillis(),preferences.getLong("last_attempt",0)))check(false);
    }
    void check(boolean manual){
        if(closed||running)return;
        if(busy.getAsBoolean()){if(manual)Toast.makeText(activity,"请先结束 NFC 操作，再检查更新。",Toast.LENGTH_SHORT).show();return;}
        running=true;check.setEnabled(false);status.setText("正在检查 GitHub 发布版本…");preferences.edit().putLong("last_attempt",System.currentTimeMillis()).apply();
        worker.execute(()->{
            GitHubUpdates.Result result=null;String failure=null;
            try{result=GitHubUpdates.check(current,includePrereleases,fetcher);}
            catch(UnknownHostException|SocketTimeoutException offline){failure="无法连接 GitHub，请检查网络后重试。";}
            catch(Exception error){failure=error instanceof java.io.IOException&&error.getMessage()!=null?error.getMessage():"无法解析 GitHub 发布信息，请稍后重试。";}
            final GitHubUpdates.Result found=result;final String message=failure;
            main.post(()->{
                if(closed)return;running=false;check.setEnabled(true);
                if(message!=null){status.setText("检查失败 · "+message);if(manual&&foreground&&!busy.getAsBoolean())message("检查更新失败",message);return;}
                if(found.latest==null){status.setText("暂未找到适用的发布版本。");if(manual&&foreground&&!busy.getAsBoolean())message("暂无适用版本","可打开 GitHub 发布页查看。");return;}
                available=found.updateAvailable?found.latest:null;
                check.setText(available==null?"检查更新":"查看新版本");
                status.setText(available==null?"已检查 · 当前无需更新（最新发布 "+found.latest.tag+"）":"发现新版本 "+available.tag);
                if(available!=null&&foreground&&!busy.getAsBoolean()&&(manual||activity.hasWindowFocus()))showRelease(available);
                else if(available==null&&manual&&foreground&&!busy.getAsBoolean())message("无需更新","当前版本："+current+"\nGitHub 最新适用版本："+found.latest.tag);
            });
        });
    }
    private void message(String title,String text){
        if(dialog!=null)dialog.dismiss();
        dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("知道了",null).setNeutralButton("GitHub 发布页",(d,w)->open(GitHubUpdates.RELEASES)).show();
    }
    private void showRelease(GitHubUpdates.Release release){
        if(closed||!foreground)return;
        if(busy.getAsBoolean()){Toast.makeText(activity,"请先结束 NFC 操作，再查看更新。",Toast.LENGTH_SHORT).show();return;}
        if(dialog!=null)dialog.dismiss();
        String details="当前 "+current+" → "+release.tag+"\n"+(release.prerelease?"测试版":"正式版");
        if(release.downloadUrl!=null&&release.size>0)details+=" · "+Formatter.formatFileSize(activity,release.size);
        if(!release.title.isEmpty())details+="\n\n"+release.title;
        details+="\n\n"+(release.notes.isEmpty()?"本次发布未填写更新说明。":release.notes);
        details+=release.downloadUrl==null?"\n\n此版本暂未提供可下载的 App 安装包，可查看版本详情。":"\n\n下载将在浏览器中打开，由你确认安装。";
        TextView text=AppUi.text(activity,details,13,AppUi.INK,false);int pad=AppUi.dp(activity,22);text.setPadding(pad,pad,pad,pad);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(activity);scroll.addView(text);
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("发现新版本 "+release.tag).setView(scroll).setNegativeButton("稍后",null);
        if(release.downloadUrl!=null)builder.setPositiveButton("下载 APK",(d,w)->open(release.downloadUrl)).setNeutralButton("版本详情",(d,w)->open(release.page()));
        else builder.setPositiveButton("版本详情",(d,w)->open(release.page()));
        dialog=builder.show();
    }
    private void open(String url){
        if(busy.getAsBoolean()){Toast.makeText(activity,"请先结束 NFC 操作，再打开更新页面。",Toast.LENGTH_SHORT).show();return;}
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(Exception unavailable){Toast.makeText(activity,"未找到可打开链接的浏览器。",Toast.LENGTH_SHORT).show();}
    }
    void close(){closed=true;foreground=false;main.removeCallbacksAndMessages(null);worker.shutdownNow();if(dialog!=null){dialog.dismiss();dialog=null;}}
}
