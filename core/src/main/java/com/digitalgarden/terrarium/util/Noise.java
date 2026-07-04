package com.digitalgarden.terrarium.util;

/**
 * Seeded value noise with fractional Brownian motion. Deterministic for a given
 * seed and coordinate, so world generation is reproducible and stable (no flicker).
 */
public class Noise {
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    /** Hashes an integer lattice point to a value in [0,1). */
    private float hash(int x, int y) {
        long h = seed + x * 374761393L + y * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Smooth value noise in [0,1]. */
    public float value(float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float v00 = hash(x0, y0);
        float v10 = hash(x0 + 1, y0);
        float v01 = hash(x0, y0 + 1);
        float v11 = hash(x0 + 1, y0 + 1);
        float u = fade(fx);
        float v = fade(fy);
        return lerp(lerp(v00, v10, u), lerp(v01, v11, u), v);
    }

    /** Fractal (multi-octave) noise in [0,1]. */
    public float fbm(float x, float y, int octaves, float gain, float lacunarity) {
        float sum = 0f, amp = 0.5f, freq = 1f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * value(x * freq, y * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }
}
