package dev.inknfclab;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Goldens are produced independently from the fixed reference profile. */
public final class EinkWarmQuantizerTest {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void close(double actual,double expected,String label){require(Math.abs(actual-expected)<=1e-9,label+" actual="+actual+" expected="+expected);}
    private static void equal(byte[] actual,byte[] expected,String label) {
        require(actual.length==expected.length,label+" length");
        int count=0,first=-1;
        for(int i=0;i<actual.length;i++)if(actual[i]!=expected[i]){count++;if(first<0)first=i;}
        require(count==0,label+" mismatches="+count+" first="+first+(first<0?"":" actual="+(actual[first]&255)+" expected="+(expected[first]&255)));
    }
    private static void rejects(int[] pixels,int width,int height) {
        try{EinkWarmQuantizer.quantize(pixels,width,height);throw new AssertionError("accepted dimensions "+width+"x"+height);}
        catch(IllegalArgumentException expected){}
    }
    private static void contracts() {
        int[] palette={0xff000000,0xffffffff,0xffe8b000,0xffc80000,0xffffff00,0xffff0000};
        int[] untouched=palette.clone();
        equal(EinkWarmQuantizer.quantize(palette,3,2),new byte[]{0,1,2,3,2,3},"whole-palette identity");
        require(Arrays.equals(palette,untouched),"palette input mutated");
        rejects(null,1,1);rejects(new int[0],0,1);rejects(new int[]{0},-1,1);
        rejects(new int[]{0},2,1);rejects(new int[]{0},Integer.MAX_VALUE,Integer.MAX_VALUE);
        double[] hues={0,60,85,110,180,300,325,350,359};
        double[] affinities={1,1,.5,0,0,0,.5,1,1};
        for(int i=0;i<hues.length;i++)close(EinkWarmQuantizer.affinity(hues[i]),affinities[i],"affinity "+hues[i]);
        for(int gray:new int[]{0,1,64,128,200,254,255}) {
            double[] mapped={gray,gray,gray};EinkWarmQuantizer.mapNatural(mapped,0);
            for(double channel:mapped)close(channel,gray,"neutral warm-map "+gray);
        }
        equal(EinkWarmQuantizer.quantizePrepared(new double[]{160,160,160},1,1),new byte[]{3},"std gray160");
        equal(EinkWarmQuantizer.quantizePrepared(new double[]{180,180,180},1,1),new byte[]{2},"std gray180");
        double[] scan={80,80,80,140,140,140,200,100,20,40,120,200,220,180,140,120,120,120,250,220,80,170,60,40,40,40,40,230,230,230,180,140,100,90,130,170};
        double[] original=scan.clone();
        equal(EinkWarmQuantizer.quantizePrepared(scan,4,3),new byte[]{0,0,3,0,1,0,2,3,0,1,3,0},"serpentine std probe");
        require(Arrays.equals(scan,original),"working RGB input mutated");
        double[] gray=new double[16*16*3];Arrays.fill(gray,128);
        int[] count=new int[4];for(byte code:EinkWarmQuantizer.quantizePrepared(gray,16,16))count[code]++;
        require(Arrays.equals(count,new int[]{179,64,5,8}),"calibrated neutral diffusion "+Arrays.toString(count));
        Thread.currentThread().interrupt();
        try{EinkWarmQuantizer.quantize(palette,3,2);throw new AssertionError("interruption ignored");}
        catch(CancellationException expected){require(Thread.currentThread().isInterrupted(),"interrupt flag cleared");}
        finally{Thread.interrupted();}
    }
    private static long goldens(Path root)throws Exception {
        long total=0;int cases=0;
        for(String line:Files.readAllLines(root.resolve("cases.tsv"))) {
            if(line.trim().isEmpty()||line.startsWith("#"))continue;
            String[] fields=line.split("\t");
            if(fields[0].equals("id"))continue;
            require(fields.length>=5,"invalid fixture index: "+line);
            int width=Integer.parseInt(fields[1]),height=Integer.parseInt(fields[2]);
            byte[] rgb=Files.readAllBytes(root.resolve(fields[3])),expected=Files.readAllBytes(root.resolve(fields[4]));
            require((long)width*height*3==rgb.length,fields[0]+" RGB length");
            int[] input=new int[width*height];
            for(int i=0;i<input.length;i++)input[i]=0xff000000|((rgb[i*3]&255)<<16)|((rgb[i*3+1]&255)<<8)|(rgb[i*3+2]&255);
            int[] before=input.clone();long start=System.nanoTime();
            byte[] actual=EinkWarmQuantizer.quantize(input,width,height);
            double elapsed=(System.nanoTime()-start)/1e6;
            equal(actual,expected,fields[0]);
            equal(EinkWarmQuantizer.quantize(input,width,height),actual,fields[0]+" deterministic");
            require(Arrays.equals(input,before),fields[0]+" source modified");
            for(byte code:actual)require(code>=0&&code<4,fields[0]+" invalid palette code");
            total+=input.length;cases++;
            System.out.printf(Locale.ROOT,"PASS einkwarm %s %dx%d %.2fms%n",fields[0],width,height,elapsed);
        }
        require(cases>0,"no independent golden fixtures");
        System.out.println("PASS einkwarm independent goldens: cases="+cases+" pixels="+total);
        return total;
    }
    public static void main(String[] args)throws Exception {
        contracts();
        if(args.length>0)goldens(Paths.get(args[0]));
        System.out.println("PASS einkwarm contracts: palette identity, dimensions, HSL neutrality, calibration, serpentine diffusion, immutable input and interruption");
    }
}
