package dev.inknfclab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric release ordering, including SemVer prereleases; never compare tags as strings. */
final class UpdateVersion implements Comparable<UpdateVersion> {
    private static final Pattern FORMAT=Pattern.compile("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    final int major,minor,patch;
    final String prerelease;
    private UpdateVersion(int a,int b,int c,String pre){major=a;minor=b;patch=c;prerelease=pre;}
    static UpdateVersion parse(String text){
        if(text==null||text.length()>160)return null;
        Matcher m=FORMAT.matcher(text);if(!m.matches())return null;
        String pre=m.group(4);
        if(pre!=null)for(String part:pre.split("\\."))if(numeric(part)&&part.length()>1&&part.charAt(0)=='0')return null;
        try{return new UpdateVersion(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),pre);}
        catch(NumberFormatException invalid){return null;}
    }
    String core(){return major+"."+minor+"."+patch;}
    boolean isPrerelease(){return prerelease!=null;}
    private static boolean numeric(String s){for(int i=0;i<s.length();i++)if(s.charAt(i)<'0'||s.charAt(i)>'9')return false;return true;}
    @Override public int compareTo(UpdateVersion other){
        int c=Integer.compare(major,other.major);if(c!=0)return c;
        c=Integer.compare(minor,other.minor);if(c!=0)return c;
        c=Integer.compare(patch,other.patch);if(c!=0)return c;
        if(prerelease==null)return other.prerelease==null?0:1;
        if(other.prerelease==null)return -1;
        String[] a=prerelease.split("\\."),b=other.prerelease.split("\\.");
        for(int i=0;i<Math.min(a.length,b.length);i++){
            if(a[i].equals(b[i]))continue;
            boolean an=numeric(a[i]),bn=numeric(b[i]);
            if(an&&bn){c=Integer.compare(a[i].length(),b[i].length());return c!=0?c:a[i].compareTo(b[i]);}
            if(an!=bn)return an?-1:1;
            return a[i].compareTo(b[i]);
        }
        return Integer.compare(a.length,b.length);
    }
}
