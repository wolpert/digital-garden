package com.digitalgarden.terrarium.lwjgl3.audio;

/**
 * Tiny in-place iterative radix-2 Cooley–Tukey FFT — enough for the harness's spectral
 * checks and spectrogram. No external DSP dependency, so the render harness stays pure
 * Java. Length must be a power of two.
 */
final class Fft {
    private Fft() {}

    /** Largest power of two &le; n. */
    static int floorPow2(int n) {
        int p = 1;
        while (p * 2 <= n) {
            p *= 2;
        }
        return p;
    }

    /** In-place FFT. re/im have equal, power-of-two length. */
    static void transform(double[] re, double[] im) {
        int n = re.length;
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("length must be a power of two: " + n);
        }
        // Bit-reversal permutation.
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        // Butterflies.
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1, curIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double vRe = re[b] * curRe - im[b] * curIm;
                    double vIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - vRe;
                    im[b] = im[a] - vIm;
                    re[a] += vRe;
                    im[a] += vIm;
                    double nRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                }
            }
        }
    }

    /** Magnitude spectrum of a real windowed frame; returns the first n/2 bins. */
    static double[] magnitudes(double[] frame) {
        int n = frame.length;
        double[] re = frame.clone();
        double[] im = new double[n];
        transform(re, im);
        double[] mag = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            mag[i] = Math.hypot(re[i], im[i]);
        }
        return mag;
    }

    /** In-place Hann window. */
    static void hann(double[] frame) {
        int n = frame.length;
        for (int i = 0; i < n; i++) {
            frame[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
        }
    }
}
