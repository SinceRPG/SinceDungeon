package net.danh.sinceDungeon.utils;

/**
 * Pre-calculates sine and cosine values to eliminate JIT loop-churn and CPU overhead
 * during intense particle rendering iterations.
 */
public class MathCache {
    public static final double[] SIN = new double[360];
    public static final double[] COS = new double[360];

    static {
        for (int i = 0; i < 360; i++) {
            double rad = Math.toRadians(i);
            SIN[i] = Math.sin(rad);
            COS[i] = Math.cos(rad);
        }
    }
}