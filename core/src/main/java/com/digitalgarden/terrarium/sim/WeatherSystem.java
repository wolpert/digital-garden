package com.digitalgarden.terrarium.sim;

import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.util.Noise;

/**
 * Autonomous weather. A drifting noise field defines cloud cover; the wind
 * direction wanders slowly, carrying the clouds across the map. Dense cloud
 * cores rain — adding water volume (which the {@link FluidSystem} then flows into
 * ponds) and raising soil moisture — while an ebbing storm level gives clear
 * spells between showers. Renders as soft shadows, translucent cloud bodies and
 * animated rain; the density field is sampled bilinearly so clouds look soft
 * rather than tile-blocky.
 */
public class WeatherSystem {
    private final int W, H;
    private final float[] cloud; // density per tile, 0..1
    private final Noise cloudNoise;

    private float weatherTime;
    private float offX, offY;   // accumulated cloud drift
    private float windX, windY; // current wind (cloud-noise units/second)
    private float stormLevel;   // 0 = clear .. 1 = heavy rain

    public WeatherSystem(World world, long seed) {
        this.W = world.w;
        this.H = world.h;
        this.cloud = new float[W * H];
        this.cloudNoise = new Noise(seed);
    }

    /** Advances weather by one fixed sim tick and rains onto the world. */
    public void step(World world, float dt) {
        weatherTime += dt;

        // Wind direction wanders; drift the cloud field along it.
        float angle = 0.7f * (float) Math.sin(weatherTime * 0.05f)
                    + 0.4f * (float) Math.sin(weatherTime * 0.017f + 1.3f);
        windX = (float) Math.cos(angle) * Config.WIND_SPEED;
        windY = (float) Math.sin(angle) * Config.WIND_SPEED;
        offX += windX * dt;
        offY += windY * dt;

        // Storm intensity ebbs and flows, dipping to fully clear between showers.
        // Phase -PI/2 opens on a clear sky; the first shower rolls in after ~45s.
        float s = 0.5f + 0.5f * (float) Math.sin(weatherTime * 0.03f - 1.5708f);
        stormLevel = clamp01((s - 0.4f) / 0.6f);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float d = cloudNoise.fbm(x * Config.CLOUD_SCALE + offX,
                                         y * Config.CLOUD_SCALE + offY, 4, 0.5f, 2f);
                d = smoothstep(Config.CLOUD_LOW, Config.CLOUD_HIGH, d);
                cloud[y * W + x] = d;

                float over = d - Config.RAIN_THRESHOLD;
                if (over > 0f && stormLevel > 0f) {
                    Tile t = world.tiles[y * W + x];
                    if (!t.rock) {
                        float rain = over * stormLevel;
                        t.water += rain * Config.RAIN_RATE * dt;
                        t.moisture = Math.min(1f, t.moisture + rain * Config.RAIN_MOISTURE * dt);
                    }
                }
            }
        }
    }

    public float stormLevel() { return stormLevel; }
    public float windX() { return windX; }

    /** Bilinearly-sampled cloud density at fractional tile coords (soft edges). */
    public float cloudAt(float tx, float ty) {
        return bilinear(tx, ty);
    }

    /** Cloud shadow density: the cloud field sampled at an offset (sun angle). */
    public float shadowAt(float tx, float ty) {
        return bilinear(tx - Config.CLOUD_SHADOW_DX, ty - Config.CLOUD_SHADOW_DY);
    }

    /** Rain intensity 0..1 at fractional tile coords. */
    public float rainAt(float tx, float ty) {
        float over = bilinear(tx, ty) - Config.RAIN_THRESHOLD;
        if (over <= 0f || stormLevel <= 0f) return 0f;
        return (over / (1f - Config.RAIN_THRESHOLD)) * stormLevel;
    }

    private float bilinear(float tx, float ty) {
        if (tx < 0f) tx = 0f; else if (tx > W - 1) tx = W - 1;
        if (ty < 0f) ty = 0f; else if (ty > H - 1) ty = H - 1;
        int x0 = (int) tx, y0 = (int) ty;
        int x1 = Math.min(x0 + 1, W - 1), y1 = Math.min(y0 + 1, H - 1);
        float fx = tx - x0, fy = ty - y0;
        float a = cloud[y0 * W + x0], b = cloud[y0 * W + x1];
        float c = cloud[y1 * W + x0], e = cloud[y1 * W + x1];
        float top = a + (b - a) * fx;
        float bot = c + (e - c) * fx;
        return top + (bot - top) * fy;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }
}
