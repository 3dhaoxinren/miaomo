package dev.inknfclab;
import java.util.Arrays;

public final class BlockQuantizerTest {
    private static void require(boolean value){if(!value)throw new AssertionError();}
    private static int edges(byte[] codes,int w,int h){int n=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;if(x>0&&codes[i]!=codes[i-1])n++;if(y>0&&codes[i]!=codes[i-w])n++;}return n;}
    public static void main(String[] args){
        int w=64,h=64;int[] gray=new int[w*h];Arrays.fill(gray,0xff808080);
        byte[] fine=AlgorithmCatalog.quantize(gray,w,h,ImageQuantizer.PHOTO,0,0,.85,AlgorithmCatalog.CLASSIC);
        require(Arrays.equals(fine,BlockQuantizer.quantize(gray,w,h,ImageQuantizer.PHOTO,0,0,.85,AlgorithmCatalog.CLASSIC,1)));
        byte[] coarse=BlockQuantizer.quantize(gray,w,h,ImageQuantizer.PHOTO,0,0,.6,AlgorithmCatalog.CLASSIC,2);
        require(edges(coarse,w,h)<edges(fine,w,h)*.8);
        for(int y=0;y<h;y+=2)for(int x=0;x<w;x+=2){int i=y*w+x;require(coarse[i]==coarse[i+1]&&coarse[i]==coarse[i+w]&&coarse[i]==coarse[i+w+1]);}
        require(Arrays.stream(gray).allMatch(p->p==0xff808080));
        int[] odd=new int[15];Arrays.fill(odd,0xffffffff);odd[14]=0xff000000;
        for(int cell:new int[]{1,2,4}){byte[] q=BlockQuantizer.quantize(odd,3,5,ImageQuantizer.PHOTO,0,0,.6,AlgorithmCatalog.CLASSIC,cell);require(q.length==15);for(byte code:q)require(code>=0&&code<4);}
        int[] black=new int[15];Arrays.fill(black,0xff000000);
        for(byte b:BlockQuantizer.quantize(black,3,5,ImageQuantizer.PHOTO,0,0,.6,AlgorithmCatalog.CLASSIC,4))require(b==0);
        try{BlockQuantizer.quantize(gray,w,h,ImageQuantizer.PHOTO,0,0,.6,AlgorithmCatalog.CLASSIC,3);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS BlockQuantizerTest: legacy cell=1 byte equality; 2x2 cells; odd edges; palette; fine_edges="+edges(fine,w,h)+" coarse_edges="+edges(coarse,w,h)+". IMAGE_STRUCTURE_ONLY, NOT_POWER_MEASUREMENT");
    }
}
