package dev.inknfclab;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Photograph composition and warm quantization; NFC palette indices remain K/W/Y/R. */
public final class ImageCodec {
    public static final int WIDTH=552,HEIGHT=768,PIXEL_COUNT=WIDTH*HEIGHT,PACKED_LENGTH=105984,BLOCK_SIZE=2000,CHUNK_SIZE=250;
    public static final class PreparedImage {
        public final Bitmap preview,originalPreview;
        public final byte[] packed;
        public final int[] palette=InkSightQuantizer.COLORS.clone();
        private PreparedImage(Bitmap preview,Bitmap fitted,byte[] packed){this.preview=preview;originalPreview=fitted;this.packed=packed;}
    }
    public static final class Packet {
        public final byte[] apdu;public final int block,subpacket;
        private Packet(byte[] apdu,int block,int subpacket){this.apdu=apdu;this.block=block;this.subpacket=subpacket;}
    }
    public static PreparedImage prepare(Bitmap source,boolean cover,boolean ignored){PhotoEdit e=new PhotoEdit();e.cover=cover;return prepareEdited(source,e);}
    private static int[] fittedPixels(Bitmap source,PhotoEdit edit){
        if(source==null||source.getWidth()<1||source.getHeight()<1)throw new IllegalArgumentException("无效图片");
        edit=edit.copy();edit.normalize();Bitmap oriented=source;
        if(edit.rotation!=0||edit.mirror){Matrix m=new Matrix();if(edit.mirror)m.postScale(-1,1);m.postRotate(edit.rotation);oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,false);}
        int[] original=new int[oriented.getWidth()*oriented.getHeight()];oriented.getPixels(original,0,oriented.getWidth(),0,0,oriented.getWidth(),oriented.getHeight());
        int[] logical;
        try{logical=InkSightFitter.fit(original,oriented.getWidth(),oriented.getHeight(),edit.width(),edit.height(),edit.cover,edit.zoom,edit.offsetX,edit.offsetY,true);}finally{if(oriented!=source)oriented.recycle();}
        return logical;
    }
    static boolean hasDecorations(PhotoEdit edit){return edit.frameStyle!=0||!edit.texts.isEmpty();}
    private static void decorate(int[] logical,PhotoEdit edit){
        if(hasDecorations(edit)){
            Bitmap composed=Bitmap.createBitmap(edit.width(),edit.height(),Bitmap.Config.ARGB_8888);
            composed.setPixels(logical,0,edit.width(),0,0,edit.width(),edit.height());
            PhotoDecorations.draw(new android.graphics.Canvas(composed),edit,edit.width(),edit.height(),-1,false);
            composed.getPixels(logical,0,edit.width(),0,0,edit.width(),edit.height());composed.recycle();
        }
    }
    static Bitmap composeEdited(Bitmap source,PhotoEdit edit){
        edit=edit.copy();edit.normalize();int[] logical=fittedPixels(source,edit);decorate(logical,edit);
        return Bitmap.createBitmap(logical,edit.width(),edit.height(),Bitmap.Config.ARGB_8888);
    }
    private static int[] physicalPixels(int[] logical,boolean landscape){
        if(!landscape)return logical;int[] physical=new int[PIXEL_COUNT];
        for(int y=0;y<552;y++)for(int x=0;x<768;x++)physical[x*552+(551-y)]=logical[y*768+x];
        return physical;
    }
    private static int[] logicalPixels(int[] physical,boolean landscape){
        if(!landscape)return physical;int[] logical=new int[PIXEL_COUNT];
        for(int y=0;y<552;y++)for(int x=0;x<768;x++)logical[y*768+x]=physical[x*552+(551-y)];
        return logical;
    }
    private static int nearestColor(int color){
        int best=0;long distance=Long.MAX_VALUE;
        for(int code=0;code<4;code++){
            int target=InkSightQuantizer.COLORS[code],red=((color>>>16)&255)-((target>>>16)&255),green=((color>>>8)&255)-((target>>>8)&255),blue=(color&255)-(target&255);
            long candidate=red*red+green*green+blue*blue;
            if(candidate<distance){distance=candidate;best=code;}
        }
        return best;
    }
    static PreparedImage prepareEdited(Bitmap source,PhotoEdit edit){
        edit=edit.copy();edit.normalize();int[] logical=fittedPixels(source,edit);
        int[] fitted=physicalPixels(logical,edit.landscape);
        byte[] codes=PhotoEdit.CLASSIC.equals(edit.colorProfile)&&!EinkWarmQuantizer.isPaletteImage(fitted,WIDTH,HEIGHT)
                ? InkSightQuantizer.quantize(fitted,WIDTH,HEIGHT,true) : EinkWarmQuantizer.quantize(fitted,WIDTH,HEIGHT);int[] colors=new int[PIXEL_COUNT];
        for(int i=0;i<colors.length;i++)colors[i]=InkSightQuantizer.COLORS[codes[i]&3];
        if(hasDecorations(edit)){
            decorate(logical,edit);fitted=physicalPixels(logical,edit.landscape);
            int[] decorated=logicalPixels(colors,edit.landscape);decorate(decorated,edit);colors=physicalPixels(decorated,edit.landscape);
            for(int y=0;y<HEIGHT;y++){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                for(int x=0;x<WIDTH;x++){int at=y*WIDTH+x,code=nearestColor(colors[at]);codes[at]=(byte)code;colors[at]=InkSightQuantizer.COLORS[code];}
            }
        }
        Bitmap normalized=Bitmap.createBitmap(fitted,WIDTH,HEIGHT,Bitmap.Config.ARGB_8888),preview=Bitmap.createBitmap(colors,WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        return new PreparedImage(preview,normalized,pack(codes));
    }
    public static byte[] pack(byte[] codes) {
        if (codes == null || codes.length != PIXEL_COUNT) throw new IllegalArgumentException("像素码长度必须为423936字节");
        byte[] result = new byte[PACKED_LENGTH];
        int output = 0;
        for (int x = WIDTH - 1; x >= 0; x--) {
            for (int y = HEIGHT - 1; y >= 0; y -= 4) {
                result[output++] = (byte) (((codes[(y) * WIDTH + x] & 3) << 6)
                        | ((codes[(y - 1) * WIDTH + x] & 3) << 4)
                        | ((codes[(y - 2) * WIDTH + x] & 3) << 2)
                        | (codes[(y - 3) * WIDTH + x] & 3));
            }
        }
        return result;
    }

    public static byte[] lzoLiteralBlock(byte[] input) {
        if (input == null || input.length < 1 || input.length > BLOCK_SIZE)
            throw new IllegalArgumentException("LZO块长度超出范围");
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length + 16);
        if (input.length <= 238) {
            output.write(input.length + 17);
        } else {
            output.write(0);
            int remainder = input.length - 18;
            while (remainder > 255) {
                output.write(0);
                remainder -= 255;
            }
            output.write(remainder);
        }
        output.write(input, 0, input.length);
        output.write(0x11);
        output.write(0);
        output.write(0);
        return output.toByteArray();
    }

    public static List<Packet> packets(byte[] packed) {
        return packets(packed, CHUNK_SIZE);
    }

    public static List<Packet> packets(byte[] packed, int chunkSize) {
        if (packed == null || packed.length != PACKED_LENGTH) throw new IllegalArgumentException("点阵长度必须为105984字节");
        if (chunkSize < 1 || chunkSize > CHUNK_SIZE) throw new IllegalArgumentException("ISO-DEP分片长度超出范围");
        List<Packet> result = new ArrayList<>();
        for (int block = 0, start = 0; start < packed.length; block++, start += BLOCK_SIZE) {
            int length = Math.min(BLOCK_SIZE, packed.length - start);
            byte[] raw = java.util.Arrays.copyOfRange(packed, start, start + length);
            byte[] compressed = lzoLiteralBlock(raw);
            int count = (compressed.length + chunkSize - 1) / chunkSize;
            if (count > 256) throw new IllegalArgumentException("此设备分片过小，超过单块256包上限，拒绝传输");
            for (int index = 0, offset = 0; offset < compressed.length; index++, offset += chunkSize) {
                int partLength = Math.min(chunkSize, compressed.length - offset);
                byte[] apdu = new byte[7 + partLength];
                apdu[0] = (byte) 0xf0;
                apdu[1] = (byte) 0xd3;
                apdu[2] = 0;
                apdu[3] = (byte) (index == count - 1 ? 1 : 0);
                apdu[4] = (byte) (partLength + 2);
                apdu[5] = (byte) block;
                apdu[6] = (byte) index;
                System.arraycopy(compressed, offset, apdu, 7, partLength);
                result.add(new Packet(apdu, block, index));
            }
        }
        return result;
    }

    private ImageCodec() {}
}
