package com.digitalgarden.terrarium.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.render.WorldCamera;
import com.digitalgarden.terrarium.sim.WeatherSystem;

/**
 * Short-lived visual particles for feedback and atmosphere: splashes when water
 * is poured or rain lands, expanding ripples on rained-on water, dust when a rock
 * drops, a green pop when a plant matures, and drifting pollen over grass and
 * flowers. Purely cosmetic — capped and culled, spawned both by events (via the
 * emit methods) and ambiently from the visible world.
 */
public class ParticleSystem {
    private enum Kind { SPLASH, RIPPLE, DUST, SPROUT, POLLEN }

    private static final int CAP = 500;
    private static final float TS = Config.TILE_SIZE;

    private static final class P {
        Kind kind;
        float x, y, vx, vy, age, life, size, r, g, b;
    }

    private final List<P> live = new ArrayList<>();
    private final List<P> pool = new ArrayList<>();
    private final Random rng = new Random(1234);

    // --- event emitters (world-pixel coordinates) ---

    public void splash(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            P p = obtain(Kind.SPLASH, x, y);
            if (p == null) return;
            p.vx = (rng.nextFloat() - 0.5f) * 40f;
            p.vy = 30f + rng.nextFloat() * 40f;
            p.life = 0.35f + rng.nextFloat() * 0.2f;
            p.size = 1.4f;
            p.r = 0.75f; p.g = 0.88f; p.b = 1f;
        }
    }

    public void ripple(float x, float y) {
        P p = obtain(Kind.RIPPLE, x, y);
        if (p == null) return;
        p.life = 0.7f;
        p.size = 6f; // max radius
        p.r = 0.85f; p.g = 0.93f; p.b = 1f;
    }

    public void dust(float x, float y) {
        for (int i = 0; i < 10; i++) {
            P p = obtain(Kind.DUST, x, y);
            if (p == null) return;
            p.vx = (rng.nextFloat() - 0.5f) * 55f;
            p.vy = 15f + rng.nextFloat() * 30f;
            p.life = 0.4f + rng.nextFloat() * 0.3f;
            p.size = 1.6f;
            float t = 0.55f + rng.nextFloat() * 0.15f;
            p.r = t; p.g = t * 0.8f; p.b = t * 0.6f;
        }
    }

    public void sprout(float x, float y) {
        for (int i = 0; i < 6; i++) {
            P p = obtain(Kind.SPROUT, x, y);
            if (p == null) return;
            p.vx = (rng.nextFloat() - 0.5f) * 30f;
            p.vy = 25f + rng.nextFloat() * 25f;
            p.life = 0.4f + rng.nextFloat() * 0.2f;
            p.size = 1.4f;
            p.r = 0.5f; p.g = 0.85f; p.b = 0.35f;
        }
    }

    // --- per-frame ---

    public void update(World world, WeatherSystem weather, WorldCamera cam, float dt) {
        for (int i = live.size() - 1; i >= 0; i--) {
            P p = live.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                pool.add(live.remove(i));
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.kind == Kind.SPLASH || p.kind == Kind.DUST || p.kind == Kind.SPROUT) {
                p.vy -= 90f * dt; // gravity (world-y is down, so upward is +)
            }
        }
        spawnAmbient(world, weather, cam, dt);
    }

    /** Spawns rain ripples/splashes and pollen from what's currently on screen. */
    private void spawnAmbient(World world, WeatherSystem weather, WorldCamera cam, float dt) {
        float invZoom = 1f / cam.zoom();
        int rainTries = weather.stormLevel() > 0f ? 6 : 0;
        for (int i = 0; i < rainTries; i++) {
            float wx = cam.x() + rng.nextFloat() * Config.VIEW_W * invZoom;
            float wy = cam.y() + rng.nextFloat() * Config.VIEW_H * invZoom;
            Tile t = tileAt(world, wx, wy);
            if (t == null) continue;
            if (t.water > Config.WATER_RENDER_THRESHOLD && weather.rainAt(wx / TS, wy / TS) > 0.1f
                    && rng.nextFloat() < 0.5f) {
                ripple(wx, wy);
            }
        }
        // pollen drifting over grass / flowers
        for (int i = 0; i < 2; i++) {
            if (rng.nextFloat() > 3f * dt) continue;
            float wx = cam.x() + rng.nextFloat() * Config.VIEW_W * invZoom;
            float wy = cam.y() + rng.nextFloat() * Config.VIEW_H * invZoom;
            Tile t = tileAt(world, wx, wy);
            if (t == null || t.water > Config.WATER_RENDER_THRESHOLD) continue;
            if (t.terrain == TerrainType.GRASS || t.plantType == 3) {
                P p = obtain(Kind.POLLEN, wx, wy);
                if (p == null) return;
                p.vx = (rng.nextFloat() - 0.5f) * 8f;
                p.vy = 4f + rng.nextFloat() * 6f;
                p.life = 2.5f + rng.nextFloat() * 2f;
                p.size = 1f;
                p.r = 0.98f; p.g = 0.95f; p.b = 0.55f;
            }
        }
    }

    public void draw(ShapeRenderer sr, com.badlogic.gdx.graphics.Camera viewCam, WorldCamera cam) {
        if (live.isEmpty()) return;
        float zoom = cam.zoom();
        sr.setProjectionMatrix(viewCam.combined);
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        sr.begin(ShapeType.Filled);
        for (P p : live) {
            if (p.kind == Kind.RIPPLE) continue;
            float vx = cam.worldToViewX(p.x), vyTop = cam.worldToViewY(p.y);
            if (vx < -8 || vx > Config.VIEW_W + 8 || vyTop < -8 || vyTop > Config.VIEW_H + 8) continue;
            float fade = 1f - p.age / p.life;
            sr.setColor(p.r, p.g, p.b, fade);
            float s = Math.max(1f, p.size * zoom);
            sr.rect(vx - s * 0.5f, Config.VIEW_H - vyTop - s * 0.5f, s, s);
        }
        sr.end();

        sr.begin(ShapeType.Line);
        for (P p : live) {
            if (p.kind != Kind.RIPPLE) continue;
            float vx = cam.worldToViewX(p.x), vyTop = cam.worldToViewY(p.y);
            if (vx < -8 || vx > Config.VIEW_W + 8 || vyTop < -8 || vyTop > Config.VIEW_H + 8) continue;
            float t = p.age / p.life;
            sr.setColor(p.r, p.g, p.b, 1f - t);
            sr.circle(vx, Config.VIEW_H - vyTop, p.size * t * zoom);
        }
        sr.end();

        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    // --- helpers ---

    private P obtain(Kind kind, float x, float y) {
        if (live.size() >= CAP) return null;
        P p = pool.isEmpty() ? new P() : pool.remove(pool.size() - 1);
        p.kind = kind;
        p.x = x; p.y = y;
        p.vx = 0; p.vy = 0; p.age = 0;
        live.add(p);
        return p;
    }

    private static Tile tileAt(World world, float wx, float wy) {
        int tx = (int) (wx / TS), ty = (int) (wy / TS);
        return world.inBounds(tx, ty) ? world.at(tx, ty) : null;
    }
}
