package dev.inknfclab;
import java.nio.file.*;
import java.util.*;

/** Expected bytes come from the running router's Pillow and native InkSight library. */
public final class InkSightQuantizerTest {
    static void equal(byte[] actual,byte[] expected,String label){
        if(actual.length!=expected.length)throw new AssertionError(label+" length");
        int different=0,first=-1;for(int i=0;i<actual.length;i++)if(actual[i]!=expected[i]){different++;if(first<0)first=i;}
        if(different>0)throw new AssertionError(label+" mismatches="+different+" first="+first+" actual="+(actual[first]&255)+" expected="+(expected[first]&255));
    }
    static byte[] rgb(int[] colors){byte[] b=new byte[colors.length*3];for(int i=0;i<colors.length;i++)for(int c=0;c<3;c++)b[i*3+c]=(byte)(colors[i]>>>(16-8*c));return b;}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);int checks=0;long pixels=0;
        for(String line:Files.readAllLines(root.resolve("cases.txt"))){
            String[] fields=line.split(" ");String name=fields[0];int w=Integer.parseInt(fields[1]),h=Integer.parseInt(fields[2]);
            byte[] raw=Files.readAllBytes(root.resolve(name+".rgb"));int[] source=new int[w*h];
            for(int i=0;i<source.length;i++)source[i]=0xff000000|((raw[i*3]&255)<<16)|((raw[i*3+1]&255)<<8)|(raw[i*3+2]&255);
            int[] pristine=source.clone();
            equal(InkSightQuantizer.quantize(source,w,h,false),Files.readAllBytes(root.resolve(name+".plain.codes")),name+" plain");checks++;
            equal(rgb(InkSightQuantizer.enhance(source,w,h)),Files.readAllBytes(root.resolve(name+".enhanced.rgb")),name+" enhance");checks++;
            equal(InkSightQuantizer.quantize(source,w,h,true),Files.readAllBytes(root.resolve(name+".enhanced.codes")),name+" enhanced codes");checks++;
            if(w==552 && h==768){
                byte[] codes=Files.readAllBytes(root.resolve(name+".enhanced.codes")),packed=ImageCodec.pack(codes);
                if(packed.length!=105984)throw new AssertionError("NFC packed size");
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                    int at=(551-x)*192+(767-y)/4,shift=(3-(767-y)%4)*2;
                    if(((packed[at]>>>shift)&3)!=codes[y*w+x])throw new AssertionError("preview/payload code mismatch");
                }
                checks++;System.out.println("PASS InkSight NFC packing: 423936 pixels, 105984 bytes");
            }
            if(!Arrays.equals(source,pristine))throw new AssertionError("input modified");
            pixels+=w*(long)h;System.out.println("PASS InkSight "+name+" "+w+"x"+h);
        }
        for(String line:Files.readAllLines(root.resolve("fits.txt"))){
            String[] f=line.split(" ");String name=f[0];int w=Integer.parseInt(f[2]),h=Integer.parseInt(f[3]),tw=Integer.parseInt(f[4]),th=Integer.parseInt(f[5]);
            byte[] raw=Files.readAllBytes(root.resolve(f[1]+".rgb"));int[] source=new int[w*h];
            for(int i=0;i<source.length;i++)source[i]=0xff000000|((raw[i*3]&255)<<16)|((raw[i*3+1]&255)<<8)|(raw[i*3+2]&255);
            int[] fitted=InkSightFitter.fit(source,w,h,tw,th,f[6].equals("cover"));
            equal(rgb(fitted),Files.readAllBytes(root.resolve(name+".fitted.rgb")),name+" Lanczos fit");checks++;
            equal(InkSightQuantizer.quantize(fitted,tw,th,true),Files.readAllBytes(root.resolve(name+".fitted.codes")),name+" full pipeline");checks++;
            pixels+=(long)tw*th;System.out.println("PASS InkSight fit "+name);
        }
        System.out.println("PASS InkSight reference: checks="+checks+" pixels="+pixels+"; exact enhancement RGB and palette indices on same fitted RGB input");
    }
}
