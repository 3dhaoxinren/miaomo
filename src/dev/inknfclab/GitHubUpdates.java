package dev.inknfclab;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Public GitHub release metadata only. No photo, log, device ID, or API key is sent. */
final class GitHubUpdates {
    static final String REPOSITORY="https://github.com/3dhaoxinren/miaomo";
    static final String RELEASES=REPOSITORY+"/releases";
    static final String API="https://api.github.com/repos/3dhaoxinren/miaomo/releases?per_page=30";
    static final int MAX_BYTES=2*1024*1024;
    interface Fetcher { String fetch() throws Exception; }
    static final class Release {
        final String tag,title,notes,downloadUrl;
        final UpdateVersion version;
        final boolean prerelease,draft;
        final long size;
        Release(String tag,String title,String notes,String download,long size,boolean prerelease,boolean draft){
            this.tag=tag;this.title=title;this.notes=notes;this.downloadUrl=download;this.size=size;
            this.version=UpdateVersion.parse(tag);this.prerelease=prerelease||(version!=null&&version.isPrerelease());this.draft=draft;
        }
        String page(){return REPOSITORY+"/releases/tag/"+tag;}
    }
    static final class Result {
        final Release latest;
        final boolean updateAvailable;
        Result(Release latest,boolean available){this.latest=latest;updateAvailable=available;}
    }
    static Result check(String installed,boolean includePrereleases,Fetcher fetcher) throws Exception{
        if(UpdateVersion.parse(installed)==null)throw new IOException("无法读取当前版本，请到 GitHub 查看发布信息。");
        return select(parse(fetcher.fetch()),installed,includePrereleases);
    }
    static Result select(List<Release> releases,String installed,boolean includePrereleases){
        UpdateVersion current=UpdateVersion.parse(installed);if(current==null)throw new IllegalArgumentException("Invalid installed version");
        Release latest=null;
        for(Release release:releases){
            if(release==null||release.version==null||release.draft||(!includePrereleases&&release.prerelease))continue;
            if(latest==null||release.version.compareTo(latest.version)>0)latest=release;
        }
        return new Result(latest,latest!=null&&latest.version.compareTo(current)>0);
    }
    static List<Release> parse(String body) throws Exception{
        if(body==null||body.length()>MAX_BYTES)throw new IOException("GitHub 响应过大，请到发布页查看。");
        JSONArray array=new JSONArray(body);List<Release> releases=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject item=array.optJSONObject(i);if(item==null)continue;
            String tag=item.optString("tag_name","");UpdateVersion version=UpdateVersion.parse(tag);if(version==null)continue;
            String expected="miaomo-"+version.core()+".apk",download=null;long size=0;
            JSONArray assets=item.optJSONArray("assets");
            if(assets!=null)for(int a=0;a<assets.length();a++){
                JSONObject asset=assets.optJSONObject(a);if(asset==null||!expected.equals(asset.optString("name")))continue;
                String candidate=asset.optString("browser_download_url","");
                if(safeApkUrl(candidate,tag,expected)){download=candidate;size=Math.max(0,asset.optLong("size",0));break;}
            }
            releases.add(new Release(tag,clip(item.optString("name",tag),160),clip(item.optString("body",""),4000),download,size,item.optBoolean("prerelease",false),item.optBoolean("draft",false)));
        }
        return releases;
    }
    private static String clip(String value,int length){return value.length()>length?value.substring(0,length)+"…":value;}
    static boolean safeApkUrl(String value,String tag,String name){
        if(name==null||UpdateVersion.parse(tag)==null||!name.equals("miaomo-"+UpdateVersion.parse(tag).core()+".apk"))return false;
        try{
            URI uri=new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())&&"github.com".equalsIgnoreCase(uri.getHost())
                &&uri.getPort()==-1&&uri.getUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null
                &&("/3dhaoxinren/miaomo/releases/download/"+tag+"/"+name).equals(uri.getPath());
        }catch(Exception invalid){return false;}
    }
    static String fetch() throws IOException{
        HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        connection.setRequestProperty("User-Agent","Miaomo-Android-Update");
        try{
            int status=connection.getResponseCode();
            if(status==403||status==429)throw new IOException("GitHub 暂时限制请求，请稍后重试，或打开发布页。");
            if(status!=200)throw new IOException("GitHub 请求失败（HTTP "+status+"），请稍后重试。");
            long deadline=System.nanoTime()+20000000000L;
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("检查已取消");
                    if(System.nanoTime()>deadline)throw new IOException("GitHub 响应超时，请稍后重试。");
                    if(output.size()+count>MAX_BYTES)throw new IOException("GitHub 响应过大，请到发布页查看。");
                    output.write(buffer,0,count);
                }
                return new String(output.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{connection.disconnect();}
    }
    private GitHubUpdates(){}
}
