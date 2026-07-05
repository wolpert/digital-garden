package com.digitalgarden.terrarium.render;

import com.digitalgarden.terrarium.Config;

/**
 * The view into the (larger) world: a scroll offset plus a zoom level. The
 * offset is the world-pixel coordinate of the view's top-left corner; zoom is
 * view pixels per world pixel (1 = native, &gt;1 zoomed in, &lt;1 zoomed out).
 * Both are kept as floats for smooth motion and clamped so the view never leaves
 * the world. Provides the view&lt;-&gt;world conversions the renderer, input and HUD
 * all share.
 */
public class WorldCamera {
    private float x, y, zoom;

    public WorldCamera() {
        zoom = Config.DEFAULT_ZOOM;
        x = (Config.WORLD_PX_W - visibleW()) * 0.5f;
        y = (Config.WORLD_PX_H - visibleH()) * 0.5f;
        clamp();
    }

    public float x() { return x; }
    public float y() { return y; }
    public float zoom() { return zoom; }

    /** Pans by a world-pixel delta. */
    public void pan(float dxWorld, float dyWorld) {
        x += dxWorld;
        y += dyWorld;
        clamp();
    }

    /** Centers the view on a world-pixel point (e.g. a mini-map click). */
    public void centerOn(float worldPxX, float worldPxY) {
        x = worldPxX - visibleW() * 0.5f;
        y = worldPxY - visibleH() * 0.5f;
        clamp();
    }

    /** Multiplies the zoom, keeping the world point under (focusVx,focusVy) fixed. */
    public void zoomBy(float factor, float focusVx, float focusVy) {
        zoomTo(zoom * factor, focusVx, focusVy);
    }

    /** Sets an absolute zoom, keeping the world point under (focusVx,focusVy) fixed. */
    public void zoomTo(float target, float focusVx, float focusVy) {
        float nz = clampZoom(target);
        if (nz == zoom) return;
        float worldFocusX = x + focusVx / zoom;
        float worldFocusY = y + focusVy / zoom;
        zoom = nz;
        x = worldFocusX - focusVx / zoom;
        y = worldFocusY - focusVy / zoom;
        clamp();
    }

    // view pixel (top-origin) <-> world pixel
    public float viewToWorldX(float viewX) { return x + viewX / zoom; }
    public float viewToWorldY(float viewYTop) { return y + viewYTop / zoom; }
    public float worldToViewX(float worldPx) { return (worldPx - x) * zoom; }
    public float worldToViewY(float worldPy) { return (worldPy - y) * zoom; }

    private float visibleW() { return Config.VIEW_W / zoom; }
    private float visibleH() { return Config.VIEW_H / zoom; }

    private void clamp() {
        x = clamp(x, 0f, Config.WORLD_PX_W - visibleW());
        y = clamp(y, 0f, Config.WORLD_PX_H - visibleH());
    }

    private static float clampZoom(float z) {
        return z < Config.MIN_ZOOM ? Config.MIN_ZOOM : (z > Config.MAX_ZOOM ? Config.MAX_ZOOM : z);
    }

    private static float clamp(float v, float lo, float hi) {
        if (hi < lo) return lo; // world smaller than view (shouldn't happen at MIN_ZOOM)
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
