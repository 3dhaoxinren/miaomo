package dev.inknfclab;

import java.util.Arrays;
import java.util.Collections;

public final class UpdateVersionTest {
    private static int checks;
    private static void require(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void order(String first,String second,int expected){
        UpdateVersion a=UpdateVersion.parse(first),b=UpdateVersion.parse(second);
        require(a!=null&&b!=null);require(Integer.signum(a.compareTo(b))==expected);require(Integer.signum(b.compareTo(a))==-expected);
    }
    private static GitHubUpdates.Release release(String tag,boolean pre,boolean draft){return new GitHubUpdates.Release(tag,tag,"",null,0,pre,draft);}
    public static void main(String[] args){
        order("v0.5.10-test","0.5.9-test",1);
        order("v0.6.0-test","0.5.99",1);
        order("0.5.3","0.5.3-test",1);
        order("0.5.3-test.10","0.5.3-test.2",1);
        order("0.5.3-test","0.5.3-test.1",-1);
        order("0.5.3-1","0.5.3-alpha",-1);
        order("v0.5.3-test+build1","0.5.3-test+build2",0);
        order("1.0.0-alpha","1.0.0-alpha.1",-1);
        for(String bad:new String[]{"main","latest","v0.5","01.2.3","0.5.3-test.01","0.5.3-","0.5.3/test","2147483648.0.0","0.5.3\n",null})require(UpdateVersion.parse(bad)==null);
        String tag="v0.5.3-test",name="miaomo-0.5.3.apk",url=GitHubUpdates.REPOSITORY+"/releases/download/"+tag+"/"+name;
        require(GitHubUpdates.safeApkUrl(url,tag,name));
        for(String bad:new String[]{url.replace("https:","http:"),url.replace("github.com/","github.com.evil.test/"),url.replace("github.com/","evil.test@github.com/"),url.replace("miaomo/releases","other/releases"),url.replace(tag,"v9.0.0"),url+"?redirect=1",url+"#fake",url.replace("github.com/","github.com:443/"),"javascript:alert(1)",null})require(!GitHubUpdates.safeApkUrl(bad,tag,name));
        require(!GitHubUpdates.safeApkUrl(url,tag,"other.apk"));
        GitHubUpdates.Release old=release("v0.5.2-test",true,false),current=release("v0.5.3-test",true,false),newer=release("v0.5.4-test",true,false),draft=release("v9.0.0",false,true),invalid=release("nightly",false,false),stable=release("v0.5.3",false,false);
        require(GitHubUpdates.select(Arrays.asList(old,current,draft,invalid),"0.5.3-test",true).updateAvailable==false);
        require(GitHubUpdates.select(Arrays.asList(newer,old,stable),"0.5.3-test",true).latest==newer);
        require(GitHubUpdates.select(Arrays.asList(newer,old,stable),"0.5.2",false).latest==stable);
        require(GitHubUpdates.select(Arrays.asList(old,current),"0.5.3-test",false).latest==null);
        require(GitHubUpdates.select(Collections.emptyList(),"0.5.3-test",true).latest==null);
        require(GitHubUpdates.select(Arrays.asList(stable),"0.5.3-test",true).updateAvailable);
        require(!GitHubUpdates.select(Arrays.asList(old),"0.5.3-test",true).updateAvailable);
        require(!GitHubUpdates.select(Arrays.asList(current),"0.5.3-test",true).updateAvailable);
        require(GitHubUpdates.select(Arrays.asList(newer),"0.5.3-test",true).updateAvailable);
        System.out.println("PASS UpdateVersionTest checks="+checks+": numeric/SemVer order; draft/channel selection; same/older skipped; official HTTPS asset links only");
    }
}
