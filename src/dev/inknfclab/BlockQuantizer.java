package dev.inknfclab;

/** Quantizes cell averages and expands palette codes without interpolating colors. */
final class BlockQuantizer {
    static byte[] quantize(int[] pixels,int width,int height,int mode,int brightness,int contrast,double strength,int algorithm,int cell) {
        if(pixels==null || width<1 || height<1 || (long)width*height!=pixels.length || (cell!=1 && cell!=2 && cell!=4))
            throw new IllegalArgumentException("无效的抖动颗粒参数");
        if(cell==1)return AlgorithmCatalog.quantize(pixels,width,height,mode,brightness,contrast,strength,algorithm);
        int cols=(width+cell-1)/cell,rows=(height+cell-1)/cell;
        int[] small=new int[cols*rows];
        for(int by=0;by<rows;by++)for(int bx=0;bx<cols;bx++) {
            long red=0,green=0,blue=0;int count=0;
            for(int y=by*cell;y<Math.min(height,(by+1)*cell);y++)for(int x=bx*cell;x<Math.min(width,(bx+1)*cell);x++) {
                int p=pixels[y*width+x];red+=(p>>>16)&255;green+=(p>>>8)&255;blue+=p&255;count++;
            }
            small[by*cols+bx]=0xFF000000|((int)((red+count/2)/count)<<16)|((int)((green+count/2)/count)<<8)|(int)((blue+count/2)/count);
        }
        byte[] codes=AlgorithmCatalog.quantize(small,cols,rows,mode,brightness,contrast,strength,algorithm);
        byte[] expanded=new byte[pixels.length];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)expanded[y*width+x]=codes[(y/cell)*cols+x/cell];
        return expanded;
    }
    private BlockQuantizer(){}
}
