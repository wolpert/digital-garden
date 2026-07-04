package com.digitalgarden.terrarium.render;

import com.digitalgarden.terrarium.Config;

/**
 * The scroll position of the view into the (larger) world, as the world-pixel
 * coordinate of the view's top-left corner. Kept as floats for smooth panning
 * and clamped so the view never leaves the world.
 */
public class WorldCamera {
    private float x, y;

    public WorldCamera() {
        // start centered on the world
        x = (Config.WORLD_PX_W - Config.VIEW_W) * 0.5f;
        y = (Config.WORLD_PX_H - Config.VIEW_H) * 0.5f;
        clamp();
    }

    public void pan(float dx, float dy) {
        x += dx;
        y += dy;
        clamp();
    }

    private void clamp() {
        x = clamp(x, 0f, Config.WORLD_PX_W - Config.VIEW_W);
        y = clamp(y, 0f, Config.WORLD_PX_H - Config.VIEW_H);
    }

    /** Integer world-pixel offsets used for pixel-exact sampling. */
    public int pxX() { return (int) x; }
    public int pxY() { return (int) y; }

    private static float clamp(float v, float lo, float hi) {
        if (hi < lo) return lo; // world smaller than view (shouldn't happen)
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
