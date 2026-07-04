package com.digitalgarden.terrarium.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.sim.WeatherSystem;

/**
 * Draws the world at the pixel level into an offscreen {@link Pixmap} and uploads
 * it to a {@link Texture} each frame. Rather than flat tiles it adds stable
 * per-tile hue variation, per-pixel dithering, darkened terrain borders and
 * animated water shimmer, so the board reads as a textured landscape.
 */
public class PixelRenderer implements Disposable {
    private static final int W = Config.VIEW_W;
    private static final int H = Config.VIEW_H;
    private static final int TS = Config.TILE_SIZE;

    private final Pixmap pixmap;
    private final Texture texture;

    public PixelRenderer() {
        pixmap = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        texture = new Texture(pixmap);
    }

    public Texture getTexture() {
        return texture;
    }

    /** Repaints the whole board. {@code time} (seconds) animates water and rain. */
    public void render(World world, WeatherSystem weather, float time) {
        float invTs = 1f / TS;
        for (int py = 0; py < H; py++) {
            int ty = py / TS;
            float fy = py * invTs;
            for (int px = 0; px < W; px++) {
                int tx = px / TS;
                float fx = px * invTs;
                pixmap.drawPixel(px, py, pixelColor(world, weather, tx, ty, px, py, fx, fy, time));
            }
        }
        texture.draw(pixmap, 0, 0);
    }

    private int pixelColor(World world, WeatherSystem weather, int tx, int ty,
                           int px, int py, float fx, float fy, float time) {
        Tile t = world.at(tx, ty);
        float r, g, b;

        if (t.water > Config.WATER_RENDER_THRESHOLD) {
            float depth = Math.min(t.water, Config.WATER_DEEP) / Config.WATER_DEEP; // 0..1
            Color deep = TerrainType.WATER.color;
            // shallow water tends toward a lighter, greener blue
            r = lerp(0.42f, deep.r, depth);
            g = lerp(0.66f, deep.g, depth);
            b = lerp(0.85f, deep.b, depth);
            // animated caustic shimmer, stronger in shallows
            float shimmer = (float) Math.sin(px * 0.6f + py * 0.35f + time * 2.0f)
                          * (float) Math.sin(px * 0.2f - py * 0.5f - time * 1.3f);
            float caustic = 0.06f * shimmer * (0.4f + 0.6f * (1f - depth));
            r += caustic;
            g += caustic;
            b += caustic * 1.2f;
        } else if (t.rock) {
            r = 0.50f; g = 0.50f; b = 0.53f;
            float d = (hash01(px * 3 + 1, py * 3 + 5) - 0.5f) * 0.10f;
            r += d; g += d; b += d;
        } else {
            Color base = t.terrain.color;
            r = base.r; g = base.g; b = base.b;
            // stable per-tile value variation (seeded by tile coords, so it never flickers)
            float tv = (hash01(tx + 7, ty + 13) - 0.5f) * 0.10f;
            r += tv; g += tv; b += tv;
            // per-pixel dither for texture
            float d = (hash01(px * 3 + 1, py * 3 + 5) - 0.5f) * 0.06f;
            r += d; g += d; b += d;
            // wet soil reads darker
            if (t.terrain == TerrainType.DIRT || t.terrain == TerrainType.SAND) {
                float wet = t.moisture * 0.12f;
                r -= wet; g -= wet; b -= wet * 0.6f;
            }
        }

        // subtle darkening on pixels sitting against a different surface
        int lx = px % TS, ly = py % TS;
        if ((lx == 0 && edge(world, t, tx - 1, ty))
                || (lx == TS - 1 && edge(world, t, tx + 1, ty))
                || (ly == 0 && edge(world, t, tx, ty - 1))
                || (ly == TS - 1 && edge(world, t, tx, ty + 1))) {
            r *= 0.85f; g *= 0.85f; b *= 0.85f;
        }

        // --- weather overlay ---
        // overcast: a subtle global dimming while it storms
        float ambient = 1f - weather.stormLevel() * 0.12f;
        r *= ambient; g *= ambient; b *= ambient;

        // soft cloud shadow trailing the cloud (offset by sun angle)
        float shadow = weather.shadowAt(fx, fy) * Config.SHADOW_STRENGTH;
        if (shadow > 0f) {
            float sf = 1f - shadow;
            r *= sf; g *= sf; b *= sf;
        }

        // translucent white cloud body — only the denser cores read as cloud
        float body = weather.cloudAt(fx, fy);
        body *= body; // emphasize cores, fade thin edges
        float ba = body * Config.CLOUD_BODY_ALPHA;
        if (ba > 0f) {
            r = r * (1f - ba) + 0.93f * ba;
            g = g * (1f - ba) + 0.95f * ba;
            b = b * (1f - ba) + 1.00f * ba;
        }

        // animated rain streaks under raining cloud
        float rain = weather.rainAt(fx, fy);
        if (rain > 0.04f) {
            int ry = py - (int) (time * Config.RAIN_FALL); // scrolls downward over time
            int rx = px + (py >> 2);                        // fixed diagonal slant
            if (hash01(rx * 7, Math.floorDiv(ry, 3)) > 1f - 0.06f * rain
                    && Math.floorMod(ry, 3) < 2) {
                r = lerp(r, 0.82f, 0.55f);
                g = lerp(g, 0.90f, 0.55f);
                b = lerp(b, 1.00f, 0.55f);
            }
        }

        return pack(r, g, b);
    }

    private static boolean edge(World world, Tile t, int nx, int ny) {
        return world.inBounds(nx, ny) && surface(t) != surface(world.at(nx, ny));
    }

    /** Collapses a tile to a "what does it look like" id for border detection. */
    private static int surface(Tile t) {
        if (t.water > Config.WATER_RENDER_THRESHOLD) return 100;
        if (t.rock) return 200;
        return t.terrain.ordinal();
    }

    // --- small helpers ---

    private static float hash01(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int pack(float r, float g, float b) {
        return (channel(r) << 24) | (channel(g) << 16) | (channel(b) << 8) | 0xFF;
    }

    private static int channel(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    @Override
    public void dispose() {
        texture.dispose();
        pixmap.dispose();
    }
}
