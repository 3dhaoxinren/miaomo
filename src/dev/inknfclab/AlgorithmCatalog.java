package dev.inknfclab;

public final class AlgorithmCatalog {
    public static final int CLASSIC = 0;
    public static final int ATKINSON = 1;
    public static final int SMOOTH_FS = 2;
    public static final int SIERRA_LITE = 3;
    public static final int STUCKI = 4;
    public static final int BAYER = 5;
    public static final int CLEAN = 6;
    public static final int INKSIGHT = 7;
    public static final int COUNT = 8;
    private static final String[] NAMES = {"经典 0.4", "实验 v1 · Atkinson", "柔和 FS", "Sierra Lite", "Stucki", "Bayer 8×8", "纯色无抖动", "InkSight 网页同款"};
    private static final String[] DETAILS = {"经典 0.4 · 保留实屏验证效果（默认）", "实验 v1 · 平滑冷色 / 部分误差扩散",
            "柔和 FS · 平滑冷色 / 蛇形扩散", "Sierra Lite · 轻量扩散 / 照片候选", "Stucki · 宽邻域扩散 / 渐变候选",
            "Bayer 8×8 · 有序网点 / 稳定纹理", "纯色无抖动 · 海报、文字与线稿候选", "InkSight · 照片增强 / RGB Atkinson"};

    public static String name(int algorithm) { validate(algorithm); return NAMES[algorithm]; }
    public static String[] choices() { return DETAILS.clone(); }
    public static void validate(int algorithm) {
        if (algorithm < 0 || algorithm >= COUNT) throw new IllegalArgumentException("未知图像算法");
    }

    public static byte[] quantize(int[] pixels, int width, int height, int mode,
                                  int brightness, int contrast, double strength, int algorithm) {
        validate(algorithm);
        if(algorithm==INKSIGHT)return InkSightQuantizer.quantize(pixels,width,height,true);
        if (algorithm == CLASSIC) return ImageQuantizer.quantize(pixels, width, height, mode, brightness, contrast, strength);
        if (algorithm == ATKINSON) return ExperimentalQuantizer.quantize(pixels, width, height, mode, brightness, contrast, strength);
        return ResearchQuantizer.quantize(pixels, width, height, mode, brightness, contrast, strength, algorithm);
    }

    private AlgorithmCatalog() {}
}
