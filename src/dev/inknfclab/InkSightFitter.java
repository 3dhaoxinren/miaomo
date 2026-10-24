package dev.inknfclab;

/** Centered contain/cover with Pillow-compatible Lanczos and 22-bit coefficients. */
public strictfp final class InkSightFitter {
    static int[] fit(int[] pixels,int width,int height,int targetWidth,int targetHeight,boolean cover) {
        return fit(pixels,width,height,targetWidth,targetHeight,cover,1,0,0);
    }
    static int[] fit(int[] pixels,int width,int height,int targetWidth,int targetHeight,boolean cover,double zoom,double offsetX,double offsetY) {
        return fit(pixels,width,height,targetWidth,targetHeight,cover,zoom,offsetX,offsetY,false);
    }
    static int[] fit(int[] pixels,int width,int height,int targetWidth,int targetHeight,boolean cover,double zoom,double offsetX,double offsetY,boolean preservePalette) {
        if(pixels==null||width<1||height<1||(long)width*height!=pixels.length||targetWidth<1||targetHeight<1)throw new IllegalArgumentException("无效裁切尺寸");
        double scale=cover ? Math.max((double)targetWidth/width,(double)targetHeight/height) : Math.min((double)targetWidth/width,(double)targetHeight/height);
        scale*=PhotoEdit.clamp(zoom,1,4);
        int resizedWidth=Math.max(1,(int)Math.rint(width*scale)),resizedHeight=Math.max(1,(int)Math.rint(height*scale));
        if((long)resizedWidth*resizedHeight>16000000L)throw new IllegalArgumentException("图片比例过于狭长，请先裁切后再选择");
        // Flatten alpha onto white; camera/gallery JPEG inputs are already opaque.
        int[] opaque=new int[pixels.length];
        for(int i=0;i<pixels.length;i++) {
            int color=pixels[i],alpha=color>>>24,result=0xff000000;
            for(int c=0;c<3;c++){int shift=16-8*c,value=(color>>>shift)&255;result|=((value*alpha+255*(255-alpha)+127)/255)<<shift;}
            opaque[i]=result;
        }
        int[] scaled=preservePalette&&EinkWarmQuantizer.isPaletteImage(opaque,width,height)
                ? resizeNearest(opaque,width,height,resizedWidth,resizedHeight) : resize(opaque,width,height,resizedWidth,resizedHeight);
        int[] result=new int[targetWidth*targetHeight];java.util.Arrays.fill(result,0xffffffff);
        int ox=Math.floorDiv(targetWidth-resizedWidth,2)+(int)Math.round(PhotoEdit.clamp(offsetX,-1,1)*Math.max(0,(resizedWidth-targetWidth)/2.0));
        int oy=Math.floorDiv(targetHeight-resizedHeight,2)+(int)Math.round(PhotoEdit.clamp(offsetY,-1,1)*Math.max(0,(resizedHeight-targetHeight)/2.0));
        for(int y=Math.max(0,oy);y<Math.min(targetHeight,oy+resizedHeight);y++)
            for(int x=Math.max(0,ox);x<Math.min(targetWidth,ox+resizedWidth);x++)result[y*targetWidth+x]=scaled[(y-oy)*resizedWidth+x-ox];
        return result;
    }
    private static int[] resizeNearest(int[] source,int width,int height,int targetWidth,int targetHeight) {
        int[] output=new int[targetWidth*targetHeight];
        for(int y=0;y<targetHeight;y++) {
            checkCancelled();int sy=Math.min(height-1,(int)((y+.5)*height/targetHeight));
            for(int x=0;x<targetWidth;x++){
                int sx=Math.min(width-1,(int)((x+.5)*width/targetWidth));output[y*targetWidth+x]=source[sy*width+sx];
            }
        }
        return output;
    }
    private static int[] resize(int[] source,int w,int h,int tw,int th) {
        int[] horizontal=source;
        if(tw!=w){
            Coefficients[] coefficients=coefficients(w,tw);horizontal=new int[tw*h];
            for(int y=0;y<h;y++) {
                checkCancelled();
                for(int x=0;x<tw;x++)horizontal[y*tw+x]=apply(source,y*w,1,coefficients[x]);
            }
        }
        if(th==h)return horizontal;
        int[] result=new int[tw*th];Coefficients[] coefficients=coefficients(h,th);
        for(int y=0;y<th;y++) {
            checkCancelled();for(int x=0;x<tw;x++)result[y*tw+x]=apply(horizontal,x,tw,coefficients[y]);
        }
        return result;
    }
    private static int apply(int[] source,int offset,int stride,Coefficients weights) {
        int color=0xff000000;
        for(int c=0;c<3;c++){
            int sum=1<<21,shift=16-8*c;
            for(int i=0;i<weights.values.length;i++)sum+=((source[offset+(weights.start+i)*stride]>>>shift)&255)*weights.values[i];
            color|=Math.max(0,Math.min(255,sum>>22))<<shift;
        }
        return color;
    }
    private static Coefficients[] coefficients(int input,int output) {
        double scale=(double)input/output,filterScale=Math.max(1,scale),support=3*filterScale;
        Coefficients[] all=new Coefficients[output];
        for(int i=0;i<output;i++){
            double center=(i+.5)*scale;int first=Math.max(0,(int)(center-support+.5)),end=Math.min(input,(int)(center+support+.5));
            double[] values=new double[end-first];double sum=0;
            for(int j=0;j<values.length;j++){double x=(j+first-center+.5)/filterScale;values[j]=x>=-3&&x<3 ? sinc(x)*sinc(x/3) : 0;sum+=values[j];}
            int[] normalized=new int[values.length];
            for(int j=0;j<values.length;j++){double k=sum!=0 ? values[j]/sum : values[j];normalized[j]=(int)(k*(1<<22)+(k<0 ? -.5 : .5));}
            all[i]=new Coefficients(first,normalized);
        }
        return all;
    }
    private static double sinc(double x){if(x==0)return 1;double pi=x*Math.PI;return Math.sin(pi)/pi;}
    private static final class Coefficients{final int start;final int[] values;Coefficients(int start,int[] values){this.start=start;this.values=values;}}
    private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();}
    private InkSightFitter(){}
}
