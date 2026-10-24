package dev.inknfclab;

/** Fixed natural photograph profile; emitted indices use the device's K/W/Y/R order. */
public strictfp final class EinkWarmQuantizer {
    public static final String PROFILE="einkwarm-natural-std-v1";
    private static final double[] DECODE=new double[256];
    private static final double[][] TARGETS;
    static {
        for(int i=0;i<DECODE.length;i++)DECODE[i]=255*Math.pow(i/255.0,1.75);
        double calibrated=DECODE[230];
        TARGETS=new double[][]{{0,0,0},{255,255,255},{255,calibrated,0},{calibrated,0,0}};
    }

    public static byte[] quantize(int[] pixels,int width,int height) {
        validate(pixels,width,height);
        byte[] exact=paletteImage(pixels,width,height);
        return exact!=null ? exact : diffuse(prepare(pixels,width,height),width,height);
    }

    private static byte[] paletteImage(int[] pixels,int width,int height) {
        byte[] codes=new byte[pixels.length];
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++) {
                int at=y*width+x,code=paletteCode(pixels[at]);
                if(code<0)return null;
                codes[at]=(byte)code;
            }
        }
        return codes;
    }
    static boolean isPaletteImage(int[] pixels,int width,int height) {
        validate(pixels,width,height);
        for(int y=0;y<height;y++) {
            checkCancelled();for(int x=0;x<width;x++)if(paletteCode(pixels[y*width+x])<0)return false;
        }
        return true;
    }
    private static int paletteCode(int pixel) {
        int color=pixel&0xffffff;
        return color==0?0:color==0xffffff?1:color==0xe8b000||color==0xffff00?2:color==0xc80000||color==0xff0000?3:-1;
    }

    static double[] prepare(int[] pixels,int width,int height) {
        validate(pixels,width,height);
        double[] data=new double[pixels.length*3];
        int[] histogram=new int[256];
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++) {
                int at=y*width+x,p=pixels[at],base=at*3;
                double red=(p>>>16)&255,green=(p>>>8)&255,blue=p&255;
                data[base]=red;data[base+1]=green;data[base+2]=blue;
                histogram[(int)Math.floor(.2126*red+.7152*green+.0722*blue+.5)]++;
            }
        }
        int low=percentile(histogram,.005*pixels.length),high=percentile(histogram,.995*pixels.length);
        if(high-low>=8) {
            double scale=228.0/(high-low);
            for(int y=0;y<height;y++) {
                checkCancelled();
                int end=(y+1)*width*3;
                for(int i=y*width*3;i<end;i++)data[i]=clip((data[i]-low)*scale+18);
            }
        }
        sharpen(data,width,height);
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++)mapNatural(data,(y*width+x)*3);
        }
        return data;
    }

    private static int percentile(int[] histogram,double threshold) {
        long count=0;
        for(int i=0;i<histogram.length;i++){count+=histogram[i];if(count>=threshold)return i;}
        return 255;
    }

    private static void sharpen(double[] data,int width,int height) {
        double[] horizontal=new double[data.length];
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++) {
                int at=(y*width+x)*3,left=(y*width+Math.max(0,x-1))*3,right=(y*width+Math.min(width-1,x+1))*3;
                for(int c=0;c<3;c++)horizontal[at+c]=(data[left+c]+2*data[at+c]+data[right+c])/4;
            }
        }
        for(int y=0;y<height;y++) {
            checkCancelled();
            for(int x=0;x<width;x++) {
                int at=(y*width+x)*3,above=(Math.max(0,y-1)*width+x)*3,below=(Math.min(height-1,y+1)*width+x)*3;
                for(int c=0;c<3;c++) {
                    double blurred=(horizontal[above+c]+2*horizontal[at+c]+horizontal[below+c])/4;
                    data[at+c]=clip(data[at+c]+.30*(data[at+c]-blurred));
                }
            }
        }
    }

    static double affinity(double hue) {
        if(hue>300)return smooth(unit((hue-300)/50));
        if(hue<110)return 1-smooth(unit((hue-60)/50));
        return 0;
    }

    static void mapNatural(double[] data,int at) {
        double red=data[at]/255,green=data[at+1]/255,blue=data[at+2]/255;
        double maximum=Math.max(red,Math.max(green,blue)),minimum=Math.min(red,Math.min(green,blue));
        double range=maximum-minimum,lightness=(maximum+minimum)/2,hue=0,saturation=0;
        if(range>1e-12) {
            double denominator=lightness<=.5?maximum+minimum:2-maximum-minimum;
            saturation=unit(range/Math.max(denominator,1e-12));
            double rDistance=(maximum-red)/range,gDistance=(maximum-green)/range,bDistance=(maximum-blue)/range;
            double sector=maximum==red?bDistance-gDistance:maximum==green?2+rDistance-bDistance:4+gDistance-rDistance;
            hue=360*wrap(sector/6,1);
        }
        double warmth=affinity(hue),gain=unit(.60*warmth);
        saturation=unit(Math.pow(saturation,1-.75*gain)*(1-unit(.25*(1-warmth))));
        double cool=Math.max(-60,Math.min(0,-.25*(1-warmth)*(hue-60)));
        double toward=wrap(30-hue+540,360)-180;
        double pull=Math.max(-18,Math.min(18,.12*toward));
        double turn=wrap(hue+cool+warmth*pull,360)/360;
        double upper=lightness<.5?lightness*(1+saturation):lightness+saturation-lightness*saturation;
        double lower=2*lightness-upper;
        data[at]=clip(255*hueChannel(lower,upper,turn+1.0/3));
        data[at+1]=clip(255*hueChannel(lower,upper,turn));
        data[at+2]=clip(255*hueChannel(lower,upper,turn-1.0/3));
    }

    private static double hueChannel(double lower,double upper,double turn) {
        turn=wrap(turn,1);
        if(turn<1.0/6)return lower+(upper-lower)*6*turn;
        if(turn<.5)return upper;
        if(turn<2.0/3)return lower+(upper-lower)*(2.0/3-turn)*6;
        return lower;
    }

    static byte[] quantizePrepared(double[] rgb,int width,int height) {
        if(rgb==null||width<1||height<1||(long)width*height*3!=rgb.length)throw new IllegalArgumentException("无效暖调图像尺寸");
        return diffuse(rgb.clone(),width,height);
    }

    private static byte[] diffuse(double[] data,int width,int height) {
        for(int y=0;y<height;y++) {
            checkCancelled();
            int end=(y+1)*width*3;
            for(int i=y*width*3;i<end;i++)data[i]=DECODE[(int)Math.floor(clip(data[i])+.5)];
        }
        byte[] codes=new byte[width*height];
        for(int y=0;y<height;y++) {
            checkCancelled();
            int direction=(y&1)==0?1:-1;
            for(int x=direction==1?0:width-1;x>=0&&x<width;x+=direction) {
                int at=(y*width+x)*3,best=0;double nearest=Double.POSITIVE_INFINITY;
                for(int k=0;k<TARGETS.length;k++) {
                    double dr=data[at]-TARGETS[k][0],dg=data[at+1]-TARGETS[k][1],db=data[at+2]-TARGETS[k][2];
                    double distance=dr*dr+dg*dg+db*db;
                    if(distance<nearest){nearest=distance;best=k;}
                }
                codes[y*width+x]=(byte)best;
                for(int c=0;c<3;c++) {
                    double error=.85*(data[at+c]-TARGETS[best][c]);
                    if(x+direction>=0&&x+direction<width)data[at+direction*3+c]+=error*(7.0/16);
                    if(y+1<height) {
                        int next=at+width*3;
                        if(x-direction>=0&&x-direction<width)data[next-direction*3+c]+=error*(3.0/16);
                        data[next+c]+=error*(5.0/16);
                        if(x+direction>=0&&x+direction<width)data[next+direction*3+c]+=error*(1.0/16);
                    }
                }
            }
        }
        return codes;
    }

    private static void validate(int[] pixels,int width,int height) {
        if(pixels==null||width<1||height<1||(long)width*height!=pixels.length||pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("无效暖调图像尺寸");
        checkCancelled();
    }
    private static double clip(double value){return Math.max(0,Math.min(255,value));}
    private static double unit(double value){return Math.max(0,Math.min(1,value));}
    private static double smooth(double value){return value*value*(3-2*value);}
    private static double wrap(double value,double period){return value-period*Math.floor(value/period);}
    private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();}
    private EinkWarmQuantizer(){}
}
