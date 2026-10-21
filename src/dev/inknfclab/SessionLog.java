package dev.inknfclab;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

final class SessionLog {
    final File dir;
    final String id;
    private long eventBytes, systemBytes;
    private boolean eventLimit, systemLimit;
    SessionLog(Context context) throws Exception {
        id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        dir = new File(context.getFilesDir(), "sessions/" + id);
        if (!dir.mkdirs()) throw new IOException("无法创建会话目录");
        JSONObject env = new JSONObject();
        env.put("session", id).put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("device", Build.DEVICE).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("fingerprint", Build.FINGERPRINT).put("app", "0.5.2-test").put("elapsed_ms", SystemClock.elapsedRealtime());
        write("environment.json", env.toString(2));
        write("README.txt", "喵墨 0.5.2\n日志仅存本机，导出内容可能包含标签 UID、完整 APDU、图片预览、点阵数据和 NFC 系统信息。\n普通寻卡不发送 APDU；D1 模式只读取信息；推图模式经 D1 校验后执行认证、D3 上传、D4 刷新及 DE 查询。\nNXP Q-Poll 通过 Shizuku 开启并在结束后关闭；API 成功、0x71发现配置、标签和D1分别记录。普通寻卡保留作对照，未发现标签不能据此判断屏幕损坏。\n单会话事件/系统日志各限制 4 MiB，采集最长 10 分钟。后台自动停止，历史会话保留。\n");
        event("session_created", id);
    }
    synchronized void event(String type, String detail) {
        try {
            if (eventLimit) return;
            JSONObject o = new JSONObject().put("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).format(new Date()))
                .put("elapsed_ms", SystemClock.elapsedRealtime()).put("type", type).put("detail", detail);
            byte[] bytes = (o.toString() + "\n").getBytes(StandardCharsets.UTF_8);
            if (eventBytes + bytes.length > 4 * 1024 * 1024) {
                eventLimit = true; write("event-limit.txt", "事件日志达到 4 MiB，后续事件未写入。"); return;
            }
            try (FileOutputStream out = new FileOutputStream(new File(dir, "events.jsonl"), true)) { out.write(bytes); }
            eventBytes += bytes.length;
            Log.i("InkNfcTest", type + " " + detail);
        } catch (Exception e) { Log.e("InkNfcTest", "write event", e); }
    }
    synchronized void system(String value) throws IOException {
        if (systemLimit || value.isEmpty()) return;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int count = (int)Math.min(bytes.length, 4 * 1024 * 1024 - systemBytes);
        try (FileOutputStream out = new FileOutputStream(new File(dir, "nfc-system.log"), true)) { out.write(bytes, 0, count); }
        systemBytes += count;
        if (systemBytes >= 4 * 1024 * 1024) { systemLimit = true; write("system-limit.txt", "系统日志达到 4 MiB，后续系统日志未写入。"); }
    }
    synchronized void write(String name, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
    synchronized void writeBytes(String name, byte[] bytes) throws IOException {
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))) { out.write(bytes); }
    }
    synchronized File export(Context context) throws IOException {
        File exports = new File(context.getFilesDir(), "exports"); exports.mkdirs();
        File file = new File(exports, "ink-nfc-" + id + ".zip");
        File tmp = new File(exports, file.getName() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmp))) {
            File[] files = dir.listFiles();
            if (files != null) for (File item : files) if (item.isFile()) {
                out.putNextEntry(new ZipEntry(id + "/" + item.getName()));
                try (InputStream in = new FileInputStream(item)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b,0,n); }
                out.closeEntry();
            }
        }
        if (!tmp.renameTo(file)) throw new IOException("日志 ZIP 写入失败");
        return file;
    }
}
