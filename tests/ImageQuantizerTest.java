package dev.inknfclab;

import java.util.Arrays;

public final class ImageQuantizerTest {
    public static void main(String[] args) {
        int width = 64, height = 48;
        int[] pixels = new int[width * height];
        for (int color = 0; color < 4; color++) {
            Arrays.fill(pixels, ImageQuantizer.COLORS[color]);
            byte[] codes = ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85);
            for (byte code : codes) require(code == color);
        }
        for (int index = 0; index < pixels.length; index++) {
            int gray = index % 256;
            pixels[index] = 0xff000000 | gray << 16 | gray << 8 | gray;
        }
        byte[] neutral = ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85);
        for (byte code : neutral) require(code == 0 || code == 1);
        require(Arrays.equals(neutral, ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85)));
        Arrays.fill(pixels, 0x00000000);
        for (byte code : ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85)) require(code == 1);
        Arrays.fill(pixels, 0xff808080);
        byte[] midtone = ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, 1);
        long white = whiteCount(midtone);
        require(white > pixels.length * .4 && white < pixels.length * .6);
        require(whiteCount(ImageQuantizer.quantize(pixels, width, height, 0, 30, 0, 1)) > white);
        require(whiteCount(ImageQuantizer.quantize(pixels, width, height, 0, -30, 0, 1)) < white);
        for (byte code : ImageQuantizer.quantize(pixels, width, height, 1, 0, 0, 0)) require(code == 1);
        Arrays.fill(pixels, 0xffc80000);
        for (byte code : ImageQuantizer.quantize(pixels, width, height, 2, 0, 0, 1)) require(code <= 1);
        for (int cool : new int[]{0xff0000ff, 0xff00ffff}) {
            Arrays.fill(pixels, cool);
            for (byte code : ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, .85)) require(code <= 1);
        }
        reject(() -> ImageQuantizer.quantize(pixels, 1, 1, 0, 0, 0, 1));
        reject(() -> ImageQuantizer.quantize(pixels, width, height, 0, 0, 0, Double.NaN));
        reject(() -> ImageQuantizer.quantize(pixels, width, height, 3, 0, 0, 1));
        reject(() -> ImageQuantizer.quantize(pixels, width, height, 0, 41, 0, 1));
        System.out.println("Image quantizer tests passed");
    }

    private static long whiteCount(byte[] codes) {
        long count = 0;
        for (byte code : codes) if (code == 1) count++;
        return count;
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid image setting rejection");
    }
}
