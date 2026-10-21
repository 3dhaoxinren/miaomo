package dev.inknfclab;

import java.util.Arrays;

public final class ImageQuantizer {
    public static final int PHOTO = 0;
    public static final int ARTWORK = 1;
    public static final int MONO = 2;
    public static final int[] COLORS = {0xff000000, 0xffffffff, 0xfff5cd00, 0xffc80000};
    private static final double[] LINEAR = new double[256];
    private static final double[][] PERCEPTUAL = new double[4][];

    static {
        for (int value = 0; value < 256; value++) {
            double channel = value / 255.0;
            LINEAR[value] = channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        for (int index = 0; index < COLORS.length; index++) {
            int color = COLORS[index];
            PERCEPTUAL[index] = perceptual((color >>> 16) & 255, (color >>> 8) & 255, color & 255);
        }
    }

    public static byte[] quantize(int[] pixels, int width, int height, int mode,
                                  int brightness, int contrast, double strength) {
        if (pixels == null || width < 1 || height < 1 || (long) width * height != pixels.length
                || mode < PHOTO || mode > MONO || brightness < -40 || brightness > 40
                || contrast < -30 || contrast > 50 || !Double.isFinite(strength) || strength < 0 || strength > 1)
            throw new IllegalArgumentException("无效的图像处理参数");
        byte[] codes = new byte[pixels.length];
        double[] current = new double[(width + 2) * 3];
        double[] next = new double[current.length];
        double gain = 1 + contrast / 100.0;
        for (int row = 0; row < height; row++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int direction = (row & 1) == 0 ? 1 : -1;
            for (int column = direction == 1 ? 0 : width - 1; column >= 0 && column < width; column += direction) {
                int pixel = pixels[row * width + column];
                double alpha = (pixel >>> 24) / 255.0;
                double red = clamp(((((pixel >>> 16) & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                double green = clamp(((((pixel >>> 8) & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                double blue = clamp((((pixel & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                boolean neutral = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) < 16;
                boolean monochrome = mode == MONO || neutral || (blue > red + 12 && blue > green * .65);
                int offset = (column + 1) * 3;
                if (monochrome) {
                    double luminance = red * .2126 + green * .7152 + blue * .0722;
                    red = green = blue = luminance;
                }
                red = clamp(red + current[offset]);
                green = clamp(green + current[offset + 1]);
                blue = clamp(blue + current[offset + 2]);
                int code = nearest(red, green, blue, monochrome);
                codes[row * width + column] = (byte) code;
                if (strength > 0) {
                    int color = COLORS[code];
                    double errorRed = red - ((color >>> 16) & 255);
                    double errorGreen = green - ((color >>> 8) & 255);
                    double errorBlue = blue - (color & 255);
                    diffuse(current, offset + direction * 3, errorRed, errorGreen, errorBlue, strength * 7 / 16);
                    diffuse(next, offset - direction * 3, errorRed, errorGreen, errorBlue, strength * 3 / 16);
                    diffuse(next, offset, errorRed, errorGreen, errorBlue, strength * 5 / 16);
                    diffuse(next, offset + direction * 3, errorRed, errorGreen, errorBlue, strength / 16);
                }
            }
            double[] swap = current;
            current = next;
            next = swap;
            Arrays.fill(next, 0);
        }
        return codes;
    }

    private static int nearest(double red, double green, double blue, boolean neutral) {
        if (neutral) return red * .2126 + green * .7152 + blue * .0722 >= 127.5 ? 1 : 0;
        double[] sample = perceptual((int) Math.round(red), (int) Math.round(green), (int) Math.round(blue));
        int selected = 0;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < COLORS.length; index++) {
            double lightness = sample[0] - PERCEPTUAL[index][0];
            double greenRed = sample[1] - PERCEPTUAL[index][1];
            double blueYellow = sample[2] - PERCEPTUAL[index][2];
            double distance = lightness * lightness + greenRed * greenRed + blueYellow * blueYellow;
            if (distance < best) { selected = index; best = distance; }
        }
        return selected;
    }

    private static double[] perceptual(int red, int green, int blue) {
        double linearRed = LINEAR[red], linearGreen = LINEAR[green], linearBlue = LINEAR[blue];
        double first = Math.cbrt(.4122214708 * linearRed + .5363325363 * linearGreen + .0514459929 * linearBlue);
        double second = Math.cbrt(.2119034982 * linearRed + .6806995451 * linearGreen + .1073969566 * linearBlue);
        double third = Math.cbrt(.0883024619 * linearRed + .2817188376 * linearGreen + .6299787005 * linearBlue);
        return new double[]{.2104542553 * first + .793617785 * second - .0040720468 * third,
                1.9779984951 * first - 2.428592205 * second + .4505937099 * third,
                .0259040371 * first + .7827717662 * second - .808675766 * third};
    }

    private static double clamp(double value) { return Math.max(0, Math.min(255, value)); }

    private static void diffuse(double[] row, int offset, double red, double green, double blue, double strength) {
        row[offset] += red * strength;
        row[offset + 1] += green * strength;
        row[offset + 2] += blue * strength;
    }

    private ImageQuantizer() {}
}
