package dev.inknfclab;

/** InkSight image_processing.py + eink_dither.cpp; RGB inputs are already fitted. */
public strictfp final class InkSightQuantizer {
    public static final int[] COLORS={0xff000000,0xffffffff,0xffe8b000,0xffc80000};
    private static final int[][] STEPS={{1,0},{2,0},{-1,1},{0,1},{1,1},{0,2}};

    public static byte[] quantize(int[] pixels,int width,int height,boolean enhance) {
        validate(pixels,width,height);
        int[] prepared=enhance ? enhance(pixels,width,height) : pixels;
        double[] data=new double[pixels.length*3];
        for(int i=0;i<pixels.length;i++)for(int c=0;c<3;c++)data[i*3+c]=(prepared[i]>>>(16-8*c))&255;
        byte[] out=new byte[pixels.length];
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++) {
                int base=(y*width+x)*3,best=0;double distance=1e30;
                for(int color=0;color<4;color++) {
                    double dr=data[base]-((COLORS[color]>>>16)&255),dg=data[base+1]-((COLORS[color]>>>8)&255),db=data[base+2]-(COLORS[color]&255);
                    double d=dr*dr+dg*dg+db*db;
                    if(d<distance){distance=d;best=color;}
                }
                out[y*width+x]=(byte)best;
                double er=data[base]-((COLORS[best]>>>16)&255),eg=data[base+1]-((COLORS[best]>>>8)&255),eb=data[base+2]-(COLORS[best]&255);
                for(int[] step:STEPS) {
                    int nx=x+step[0],ny=y+step[1];if(nx<0||nx>=width||ny>=height)continue;
                    int at=(ny*width+nx)*3;
                    data[at]=clamp(data[at]+er/8);data[at+1]=clamp(data[at+1]+eg/8);data[at+2]=clamp(data[at+2]+eb/8);
                }
            }
        }
        return out;
    }

    /** Pillow autocontrast(cutoff=1), Contrast(1.12), Sharpness(1.25), USM(.8,80,3). */
    static int[] enhance(int[] pixels,int width,int height) {
        validate(pixels,width,height);
        int[][] lut=new int[3][256];
        for(int c=0;c<3;c++) {
            int[] histogram=new int[256];for(int p:pixels)histogram[(p>>>(16-8*c))&255]++;
            int cut=pixels.length/100;
            for(int i=0;i<256;i++){int n=Math.min(cut,histogram[i]);histogram[i]-=n;cut-=n;if(cut==0)break;}
            cut=pixels.length/100;
            for(int i=255;i>=0;i--){int n=Math.min(cut,histogram[i]);histogram[i]-=n;cut-=n;if(cut==0)break;}
            int lo=0,hi=255;while(lo<255&&histogram[lo]==0)lo++;while(hi>0&&histogram[hi]==0)hi--;
            double scale=hi>lo ? 255.0/(hi-lo) : 1,offset=hi>lo ? -lo*scale : 0;
            for(int i=0;i<256;i++)lut[c][i]=clip((int)(i*scale+offset));
        }
        int[] contrast=new int[pixels.length];long graySum=0;
        for(int i=0;i<pixels.length;i++) {
            int p=pixels[i],red=lut[0][(p>>>16)&255],green=lut[1][(p>>>8)&255],blue=lut[2][p&255];
            contrast[i]=0xff000000|(red<<16)|(green<<8)|blue;
            graySum+=(red*19595+green*38470+blue*7471+32768)>>16;
        }
        int mean=(int)((double)graySum/pixels.length+.5);
        for(int i=0;i<contrast.length;i++) {
            int result=0xff000000;
            for(int c=0;c<3;c++){int shift=16-8*c,v=(contrast[i]>>>shift)&255;result|=clip((int)(mean+1.12f*(v-mean)))<<shift;}
            contrast[i]=result;
        }
        int[] sharp=contrast.clone();
        float k=1f/13f,center=5f/13f;
        for(int y=1;y<height-1;y++) {
            checkCancelled();
            for(int x=1;x<width-1;x++) {
                int at=y*width+x,result=0xff000000;
                for(int c=0;c<3;c++) {
                    int shift=16-8*c;float smooth=.5f;
                    smooth+=channel(contrast[at+width-1],shift)*k+channel(contrast[at+width],shift)*k+channel(contrast[at+width+1],shift)*k;
                    smooth+=channel(contrast[at-1],shift)*k+channel(contrast[at],shift)*center+channel(contrast[at+1],shift)*k;
                    smooth+=channel(contrast[at-width-1],shift)*k+channel(contrast[at-width],shift)*k+channel(contrast[at-width+1],shift)*k;
                    int blurred=clip((int)smooth),value=channel(contrast[at],shift);
                    result|=clip((int)(blurred+1.25f*(value-blurred)))<<shift;
                }
                sharp[at]=result;
            }
        }
        int[] blur=sharp;
        // Pillow 10.1: fractional radius approximates Gaussian using 3 box passes per axis.
        float radius=.8f,sigma2=radius*radius/3;
        float length=(float)Math.sqrt(12.0*sigma2+1.0),integer=(float)Math.floor((length-1.0)/2.0);
        float a=(2*integer+1)*(integer*(integer+1)-3*sigma2);
        a/=6*(sigma2-(integer+1)*(integer+1));float boxRadius=integer+a;
        int whole=(int)boxRadius;
        long weight=(long)((1<<24)/(boxRadius*2+1)),far=((1<<24)-(whole*2+1)*weight)/2;
        for(int axis=0;axis<2;axis++)for(int pass=0;pass<3;pass++)blur=box(blur,width,height,axis,whole,weight,far);
        int[] out=new int[pixels.length];
        for(int i=0;i<out.length;i++) {
            int result=0xff000000;
            for(int c=0;c<3;c++){int shift=16-8*c,value=channel(sharp[i],shift),diff=value-channel(blur[i],shift);result|=clip(Math.abs(diff)>3 ? value+diff*80/100 : value)<<shift;}
            out[i]=result;
        }
        return out;
    }
    private static int[] box(int[] input,int w,int h,int axis,int radius,long weight,long far) {
        int[] out=new int[input.length];
        for(int y=0;y<h;y++) {
            checkCancelled();
            for(int x=0;x<w;x++) {
                int result=0xff000000;
                for(int c=0;c<3;c++) {
                    int shift=16-8*c;long total=0;
                    for(int d=-radius;d<=radius;d++)total+=sample(input,w,h,x,y,axis,d,shift)*weight;
                    total+=(sample(input,w,h,x,y,axis,-radius-1,shift)+sample(input,w,h,x,y,axis,radius+1,shift))*far;
                    result|=((int)((total+(1<<23))>>24))<<shift;
                }
                out[y*w+x]=result;
            }
        }
        return out;
    }
    private static int sample(int[] data,int w,int h,int x,int y,int axis,int d,int shift){return channel(data[Math.min(h-1,Math.max(0,y+(axis==1?d:0)))*w+Math.min(w-1,Math.max(0,x+(axis==0?d:0)))],shift);}
    private static int channel(int color,int shift){return (color>>>shift)&255;}
    private static double clamp(double v){return Math.max(0,Math.min(255,v));}
    private static int clip(int v){return Math.max(0,Math.min(255,v));}
    private static void validate(int[] p,int w,int h){if(p==null||w<1||h<1||(long)w*h!=p.length)throw new IllegalArgumentException("无效 InkSight 图像尺寸");}
    private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();}
    private InkSightQuantizer(){}
}
