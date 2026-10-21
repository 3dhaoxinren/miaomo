package dev.inknfclab;
import java.util.Arrays;
public final class PhotoEditTest {
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        PhotoEdit e=new PhotoEdit();e.rotation=-90;e.zoom=9;e.offsetX=-2;e.offsetY=Double.NaN;e.normalize();require(e.rotation==270&&e.zoom==4&&e.offsetX==-1&&e.offsetY==0);
        PhotoEdit saved=e.copy();e.reset();require(e.rotation==0&&e.zoom==1&&e.cover&&!e.landscape&&!e.mirror);require(saved.rotation==270);
        int w=100,h=100;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff000000|(x*255/99)<<16|(y*255/99)<<8;
        int[] defaults=InkSightFitter.fit(pixels,w,h,80,60,true);
        require(Arrays.equals(defaults,InkSightFitter.fit(pixels,w,h,80,60,true,1,0,0)));
        int[] left=InkSightFitter.fit(pixels,w,h,80,60,true,2,1,0),right=InkSightFitter.fit(pixels,w,h,80,60,true,2,-1,0);
        require(((left[30*80+40]>>>16)&255)<((right[30*80+40]>>>16)&255));
        int[] top=InkSightFitter.fit(pixels,w,h,80,60,true,2,0,1),bottom=InkSightFitter.fit(pixels,w,h,80,60,true,2,0,-1);
        require(((top[30*80+40]>>>8)&255)<((bottom[30*80+40]>>>8)&255));
        int[] contain=InkSightFitter.fit(new int[]{0xff000000,0xff000000},2,1,4,6,false);require(contain[0]==0xffffffff&&contain[2*4]==0xff000000&&contain[5*4]==0xffffffff);
        e.landscape=true;require(e.width()==768&&e.height()==552);
        System.out.println("PASS PhotoEditTest: state copy/reset/clamps; contain/cover; zoom and normalized pan direction; landscape geometry");
    }
}
