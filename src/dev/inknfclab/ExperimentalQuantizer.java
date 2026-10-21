package dev.inknfclab;

import java.util.Arrays;

public final class ExperimentalQuantizer {
    private static final double[] LINEAR = new double[256];
    private static final double[][] PALETTE = new double[4][];

    static {
        for (int value = 0; value < 256; value++) {
            double channel = value / 255.0;
            LINEAR[value] = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
        }
        for (int index = 0; index < 4; index++) {
            int color = ImageQuantizer.COLORS[index];
            PALETTE[index] = perceptual((color >>> 16) & 255, (color >>> 8) & 255, color & 255);
        }
    }

    public static byte[] quantize(int[] pixels, int width, int height, int mode,
                                  int brightness, int contrast, double strength) {
        if (pixels == null || width < 1 || height < 1 || (long) width * height != pixels.length
                || mode < 0 || mode > 2 || brightness < -40 || brightness > 40 || contrast < -30 || contrast > 50
                || !Double.isFinite(strength) || strength < 0 || strength > 1)
            throw new IllegalArgumentException("无效的实验图像参数");
        byte[] codes = new byte[pixels.length];
        double[] current = new double[(width + 4) * 3];
        double[] next = new double[current.length];
        double[] following = new double[current.length];
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
                double chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                double coolness = smooth(0, 36, blue - red - 6) * smooth(-16, 24, blue - green * .65);
                double colorWeight = mode == ImageQuantizer.MONO ? 0 : smooth(8, 48, chroma) * (1 - coolness);
                double luminance = red * .2126 + green * .7152 + blue * .0722;
                int offset = (column + 2) * 3;
                red = clamp(luminance + (red - luminance) * colorWeight + current[offset]);
                green = clamp(luminance + (green - luminance) * colorWeight + current[offset + 1]);
                blue = clamp(luminance + (blue - luminance) * colorWeight + current[offset + 2]);
                int code = nearest(red, green, blue, colorWeight);
                codes[row * width + column] = (byte) code;
                if (strength > 0) {
                    int color = ImageQuantizer.COLORS[code];
                    double errorRed = (red - ((color >>> 16) & 255)) * strength / 8;
                    double errorGreen = (green - ((color >>> 8) & 255)) * strength / 8;
                    double errorBlue = (blue - (color & 255)) * strength / 8;
                    diffuse(current, offset + direction * 3, errorRed, errorGreen, errorBlue);
                    diffuse(current, offset + direction * 6, errorRed, errorGreen, errorBlue);
                    diffuse(next, offset - direction * 3, errorRed, errorGreen, errorBlue);
                    diffuse(next, offset, errorRed, errorGreen, errorBlue);
                    diffuse(next, offset + direction * 3, errorRed, errorGreen, errorBlue);
                    diffuse(following, offset, errorRed, errorGreen, errorBlue);
                }
            }
            double[] swap = current;
            current = next;
            next = following;
            following = swap;
            Arrays.fill(following, 0);
        }
        return codes;
    }

    private static int nearest(double red, double green, double blue, double colorWeight) {
        if (colorWeight < .000001) return red * .2126 + green * .7152 + blue * .0722 >= 127.5 ? 1 : 0;
        double[] sample = perceptual((int) Math.round(red), (int) Math.round(green), (int) Math.round(blue));
        int selected = 0;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < 4; index++) {
            double lightness = sample[0] - PALETTE[index][0];
            double firstChroma = sample[1] - PALETTE[index][1];
            double secondChroma = sample[2] - PALETTE[index][2];
            double distance = lightness * lightness + firstChroma * firstChroma + secondChroma * secondChroma
                    + (index >= 2 ? (1 - colorWeight) * .25 : 0);
            if (distance < best) { best = distance; selected = index; }
        }
        return selected;
    }

    private static double[] perceptual(int red, int green, int blue) {
        double first = Math.cbrt(.4122214708 * LINEAR[red] + .5363325363 * LINEAR[green] + .0514459929 * LINEAR[blue]);
        double second = Math.cbrt(.2119034982 * LINEAR[red] + .6806995451 * LINEAR[green] + .1073969566 * LINEAR[blue]);
        double third = Math.cbrt(.0883024619 * LINEAR[red] + .2817188376 * LINEAR[green] + .6299787005 * LINEAR[blue]);
        return new double[]{.2104542553 * first + .793617785 * second - .0040720468 * third,
                1.9779984951 * first - 2.428592205 * second + .4505937099 * third,
                .0259040371 * first + .7827717662 * second - .808675766 * third};
    }

    private static double smooth(double low, double high, double value) {
        double position = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return position * position * (3 - 2 * position);
    }

    private static double clamp(double value) { return Math.max(0, Math.min(255, value)); }

    private static void diffuse(double[] row, int offset, double red, double green, double blue) {
        row[offset] += red;
        row[offset + 1] += green;
        row[offset + 2] += blue;
    }

    private ExperimentalQuantizer() {}
}
