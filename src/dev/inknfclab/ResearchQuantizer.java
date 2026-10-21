package dev.inknfclab;

import java.util.Arrays;

public final class ResearchQuantizer {
    private static final int[][] FS = {{1, 0, 7}, {-1, 1, 3}, {0, 1, 5}, {1, 1, 1}};
    private static final int[][] SIERRA = {{1, 0, 2}, {-1, 1, 1}, {0, 1, 1}};
    private static final int[][] STUCKI = {{1, 0, 8}, {2, 0, 4}, {-2, 1, 2}, {-1, 1, 4}, {0, 1, 8},
            {1, 1, 4}, {2, 1, 2}, {-2, 2, 1}, {-1, 2, 2}, {0, 2, 4}, {1, 2, 2}, {2, 2, 1}};
    private static final int[][] BAYER = {{0, 48, 12, 60, 3, 51, 15, 63}, {32, 16, 44, 28, 35, 19, 47, 31},
            {8, 56, 4, 52, 11, 59, 7, 55}, {40, 24, 36, 20, 43, 27, 39, 23},
            {2, 50, 14, 62, 1, 49, 13, 61}, {34, 18, 46, 30, 33, 17, 45, 29},
            {10, 58, 6, 54, 9, 57, 5, 53}, {42, 26, 38, 22, 41, 25, 37, 21}};
    private static final double[] LINEAR = new double[256];
    private static final double[][] PALETTE = new double[4][];

    static {
        for (int value = 0; value < 256; value++) {
            double channel = value / 255.0;
            LINEAR[value] = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
        }
        for (int index = 0; index < 4; index++) {
            int color = ImageQuantizer.COLORS[index];
            PALETTE[index] = lab((color >>> 16) & 255, (color >>> 8) & 255, color & 255);
        }
    }

    public static byte[] quantize(int[] pixels, int width, int height, int mode,
                                  int brightness, int contrast, double strength, int algorithm) {
        if (pixels == null || width < 1 || height < 1 || (long) width * height != pixels.length
                || mode < 0 || mode > 2 || brightness < -40 || brightness > 40 || contrast < -30 || contrast > 50
                || !Double.isFinite(strength) || strength < 0 || strength > 1
                || algorithm < AlgorithmCatalog.SMOOTH_FS || algorithm >= AlgorithmCatalog.COUNT)
            throw new IllegalArgumentException("无效的研究算法参数");
        int[][] kernel = algorithm == AlgorithmCatalog.SMOOTH_FS ? FS : algorithm == AlgorithmCatalog.SIERRA_LITE ? SIERRA : STUCKI;
        double divisor = algorithm == AlgorithmCatalog.SMOOTH_FS ? 16 : algorithm == AlgorithmCatalog.SIERRA_LITE ? 4 : 42;
        boolean ordered = algorithm == AlgorithmCatalog.BAYER;
        boolean diffusion = !ordered && algorithm != AlgorithmCatalog.CLEAN && strength > 0;
        double[][] errors = new double[3][(width + 4) * 3];
        byte[] codes = new byte[pixels.length];
        for (int row = 0; row < height; row++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int direction = (row & 1) == 0 ? 1 : -1;
            for (int column = direction == 1 ? 0 : width - 1; column >= 0 && column < width; column += direction) {
                int pixel = pixels[row * width + column];
                double alpha = (pixel >>> 24) / 255.0, gain = 1 + contrast / 100.0;
                double red = clamp(((((pixel >>> 16) & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                double green = clamp(((((pixel >>> 8) & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                double blue = clamp((((pixel & 255) * alpha + 255 * (1 - alpha)) - 128) * gain + 128 + brightness);
                double chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                double weight = mode == ImageQuantizer.MONO ? 0 : smooth(8, 48, chroma)
                        * (1 - smooth(0, 36, blue - red - 6) * smooth(-16, 24, blue - green * .65));
                double luminance = red * .2126 + green * .7152 + blue * .0722;
                int offset = (column + 2) * 3;
                red = clamp(luminance + (red - luminance) * weight + errors[0][offset]);
                green = clamp(luminance + (green - luminance) * weight + errors[0][offset + 1]);
                blue = clamp(luminance + (blue - luminance) * weight + errors[0][offset + 2]);
                int code = choose(red, green, blue, weight, ordered && strength > 0
                        ? .5 + ((BAYER[row & 7][column & 7] + .5) / 64 - .5) * strength : -1);
                codes[row * width + column] = (byte) code;
                if (diffusion) {
                    int color = ImageQuantizer.COLORS[code];
                    for (int[] step : kernel) {
                        int target = offset + step[0] * direction * 3;
                        double factor = strength * step[2] / divisor;
                        errors[step[1]][target] += (red - ((color >>> 16) & 255)) * factor;
                        errors[step[1]][target + 1] += (green - ((color >>> 8) & 255)) * factor;
                        errors[step[1]][target + 2] += (blue - (color & 255)) * factor;
                    }
                }
            }
            double[] first = errors[0];
            errors[0] = errors[1];
            errors[1] = errors[2];
            errors[2] = first;
            Arrays.fill(first, 0);
        }
        return codes;
    }

    private static int choose(double red, double green, double blue, double weight, double threshold) {
        if (weight < .000001) return (red * .2126 + green * .7152 + blue * .0722) / 255 >= (threshold < 0 ? .5 : threshold) ? 1 : 0;
        double[] sample = lab((int) Math.round(red), (int) Math.round(green), (int) Math.round(blue));
        int best = 0, second = 1;
        double bestDistance = Double.MAX_VALUE, secondDistance = Double.MAX_VALUE;
        for (int index = 0; index < 4; index++) {
            double lightness = sample[0] - PALETTE[index][0], first = sample[1] - PALETTE[index][1], last = sample[2] - PALETTE[index][2];
            double distance = lightness * lightness + first * first + last * last + (index >= 2 ? (1 - weight) * .25 : 0);
            if (distance < bestDistance) {
                secondDistance = bestDistance; second = best;
                bestDistance = distance; best = index;
            } else if (distance < secondDistance) { secondDistance = distance; second = index; }
        }
        if (threshold < 0 || bestDistance < .000000001) return best;
        int firstColor = ImageQuantizer.COLORS[best], secondColor = ImageQuantizer.COLORS[second];
        double firstRed = (firstColor >>> 16) & 255, firstGreen = (firstColor >>> 8) & 255, firstBlue = firstColor & 255;
        double deltaRed = ((secondColor >>> 16) & 255) - firstRed;
        double deltaGreen = ((secondColor >>> 8) & 255) - firstGreen;
        double deltaBlue = (secondColor & 255) - firstBlue;
        double projection = ((red - firstRed) * deltaRed * .2126 + (green - firstGreen) * deltaGreen * .7152
                + (blue - firstBlue) * deltaBlue * .0722)
                / (deltaRed * deltaRed * .2126 + deltaGreen * deltaGreen * .7152 + deltaBlue * deltaBlue * .0722);
        return projection > threshold ? second : best;
    }

    private static double[] lab(int red, int green, int blue) {
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
    private ResearchQuantizer() {}
}
