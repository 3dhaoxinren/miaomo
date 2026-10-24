package dev.inknfclab;

import android.graphics.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NfcFilterIntegrationTest {
    private static Bitmap photo(boolean landscape) {
        int width=landscape?768:552,height=landscape?552:768;
        int[] pixels=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=Color.rgb(70+x*100/width,130+y*100/height,190+x*50/width);
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
    }
    private static PhotoEdit decorated(boolean landscape,int color) {
        PhotoEdit edit=new PhotoEdit();edit.landscape=landscape;edit.frameStyle=1;edit.frameColor=0xffe8b000;edit.frameWidth=.06;
        PhotoEdit.TextLayer text=new PhotoEdit.TextLayer();text.text="暖调 Aa";text.color=color;text.size=.15;text.x=.43;text.y=.37;text.rotation=-11;edit.texts.add(text);
        return edit;
    }
    private static int[] pixels(Bitmap image) {
        int[] result=new int[image.getWidth()*image.getHeight()];image.getPixels(result,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return result;
    }
    private static Bitmap rotated(Bitmap image,int degrees) {
        Matrix matrix=new Matrix();matrix.postRotate(degrees);return Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,false);
    }
    private static Bitmap logical(Bitmap physical,boolean landscape){return landscape?rotated(physical,-90):physical.copy(Bitmap.Config.ARGB_8888,false);}
    private static void same(Bitmap expected,Bitmap actual) {
        assertEquals(expected.getWidth(),actual.getWidth());assertEquals(expected.getHeight(),actual.getHeight());assertArrayEquals(pixels(expected),pixels(actual));
    }
    private static boolean palette(int color){for(int p:InkSightQuantizer.COLORS)if(color==p)return true;return false;}
    private static void packedMatches(ImageCodec.PreparedImage image) {
        int[] colors=pixels(image.preview);assertEquals(105984,image.packed.length);
        for(int y=0;y<768;y++)for(int x=0;x<552;x++) {
            int at=(551-x)*192+(767-y)/4,shift=(3-(767-y)%4)*2,code=(image.packed[at]>>>shift)&3;
            assertEquals("preview/payload at "+x+","+y,colors[y*552+x],image.palette[code]);
        }
    }
    @Test public void photoIsQuantizedBeforeRedYellowTextAndFramesInBothOrientations() {
        for(String profile:new String[]{PhotoEdit.CLASSIC,PhotoEdit.EINKWARM})for(boolean landscape:new boolean[]{false,true})for(int ink:new int[]{0xffc80000,0xffe8b000}) {
            Bitmap source=photo(landscape);PhotoEdit edit=decorated(landscape,ink);edit.colorProfile=profile;PhotoEdit base=edit.copy();base.texts.clear();base.frameStyle=0;
            ImageCodec.PreparedImage result=ImageCodec.prepareEdited(source,edit),plain=ImageCodec.prepareEdited(source,base);
            Bitmap overlay=Bitmap.createBitmap(edit.width(),edit.height(),Bitmap.Config.ARGB_8888);PhotoDecorations.draw(new Canvas(overlay),edit,edit.width(),edit.height(),-1,false);
            Bitmap actual=logical(result.preview,landscape),before=logical(plain.preview,landscape),original=logical(result.originalPreview,landscape),composed=ImageCodec.composeEdited(source,edit);
            try {
                same(composed,original);
                int[] mask=pixels(overlay),output=pixels(actual),photo=pixels(before);int solidInk=0,untouched=0;
                for(int i=0;i<output.length;i++) {
                    assertTrue("output outside device palette",palette(output[i]));
                    if((mask[i]>>>24)==255&&palette(mask[i]))assertEquals("solid decoration changed",mask[i],output[i]);
                    if(mask[i]==ink)solidInk++;
                    if((mask[i]>>>24)==0){assertEquals("photo changed outside decoration",photo[i],output[i]);untouched++;}
                }
                assertTrue("glyph/frame fixture empty",solidInk>1000);assertTrue(untouched>200000);packedMatches(result);
            }finally {
                source.recycle();overlay.recycle();actual.recycle();before.recycle();original.recycle();composed.recycle();result.preview.recycle();result.originalPreview.recycle();plain.preview.recycle();plain.originalPreview.recycle();
            }
        }
    }

    @Test public void selectableClassicMatchesOriginalQuantizerAndBothProfilesKeepPacking() {
        Bitmap source=photo(false);PhotoEdit edit=new PhotoEdit();edit.colorProfile=PhotoEdit.CLASSIC;ImageCodec.PreparedImage classic=ImageCodec.prepareEdited(source,edit);
        edit.colorProfile=PhotoEdit.EINKWARM;ImageCodec.PreparedImage warm=ImageCodec.prepareEdited(source,edit);
        try {
            assertArrayEquals(ImageCodec.pack(InkSightQuantizer.quantize(pixels(source),552,768,true)),classic.packed);
            assertFalse(Arrays.equals(classic.packed,warm.packed));packedMatches(classic);packedMatches(warm);
        }finally{classic.preview.recycle();classic.originalPreview.recycle();warm.preview.recycle();warm.originalPreview.recycle();source.recycle();}
    }

    @Test public void wholePaletteSourcesUseNearestBeforeResizeAndRetainEditedCodes() {
        int[] chart={0xff000000,0xffffffff,0xffe8b000,0xffc80000,0xffc80000,0xffe8b000,0xffffffff,0xff000000};
        int[] fitted=InkSightFitter.fit(chart,4,2,12,6,true,1,0,0,true);
        for(int y=0;y<6;y++)for(int x=0;x<12;x++)assertEquals(chart[(y/3)*4+x/3],fitted[y*12+x]);
        int nonPalette=0;for(int color:InkSightFitter.fit(chart,4,2,12,6,true))if(!palette(color))nonPalette++;
        assertTrue("fixture does not distinguish interpolation",nonPalette>0);
        int[] mixed=chart.clone();mixed[0]=0xffc90000;
        assertArrayEquals(InkSightFitter.fit(mixed,4,2,12,6,true),InkSightFitter.fit(mixed,4,2,12,6,true,1,0,0,true));
        int[] alpha=InkSightFitter.fit(new int[]{0x00000000,0xff000000},2,1,6,3,true,1,0,0,true);
        for(int y=0;y<3;y++)for(int x=0;x<6;x++)assertEquals(x<3?0xffffffff:0xff000000,alpha[y*6+x]);
        Bitmap source=Bitmap.createBitmap(chart,4,2,Bitmap.Config.ARGB_8888);PhotoEdit edit=new PhotoEdit();edit.landscape=true;edit.cover=false;
        ImageCodec.PreparedImage initial=ImageCodec.prepareEdited(source,edit);Bitmap upright=logical(initial.preview,true);
        try {
            int[] output=pixels(upright);
            for(int x=0;x<768;x++)assertEquals(0xffffffff,output[x]);
            for(int x=0;x<4;x++){assertEquals(chart[x],output[84*768+x*192+96]);assertEquals(chart[4+x],output[467*768+x*192+96]);}
        }finally{upright.recycle();initial.preview.recycle();initial.originalPreview.recycle();}
        edit.cover=true;edit.zoom=2;edit.offsetX=1;edit.offsetY=-1;edit.rotation=90;edit.mirror=true;
        ImageCodec.PreparedImage edited=ImageCodec.prepareEdited(source,edit);Bitmap expected=ImageCodec.composeEdited(source,edit),actual=logical(edited.preview,true);
        try {
            same(expected,actual);for(int color:pixels(actual))assertTrue(palette(color));packedMatches(edited);assertArrayEquals(chart,pixels(source));edit.colorProfile=PhotoEdit.CLASSIC;ImageCodec.PreparedImage classic=ImageCodec.prepareEdited(source,edit);try{assertArrayEquals(edited.packed,classic.packed);}finally{classic.preview.recycle();classic.originalPreview.recycle();}
        }finally{expected.recycle();actual.recycle();edited.preview.recycle();edited.originalPreview.recycle();source.recycle();}
    }
}
