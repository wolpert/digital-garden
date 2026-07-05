package com.digitalgarden.terrarium.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;

/**
 * A small whole-world overview shown in the top-right corner while zoomed in,
 * with a rectangle marking the current camera view. It downsamples the world
 * into a tiny texture each frame (a few thousand samples — cheap) so it reflects
 * water, terrain changes and rocks live. Hidden when zoomed all the way out,
 * since the main view already shows everything.
 */
public class MiniMap implements Disposable {
    private static final int MM_W = 120;
    private static final int MM_H = Math.round(MM_W * (float) Config.WORLD_H / Config.WORLD_W);
    private static final int INSET = 4;

    private final Pixmap pixmap;
    private final Texture texture;
    private final int[] terrainRgba;
    private final int waterRgba;
    private final int rockRgba;

    public MiniMap() {
        pixmap = new Pixmap(MM_W, MM_H, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        texture = new Texture(pixmap);

        TerrainType[] types = TerrainType.values();
        terrainRgba = new int[types.length];
        for (int i = 0; i < types.length; i++) terrainRgba[i] = Color.rgba8888(types[i].color);
        waterRgba = Color.rgba8888(TerrainType.WATER.color);
        rockRgba = Color.rgba8888(new Color(0.5f, 0.5f, 0.53f, 1f));
    }

    /** Shown only while zoomed in past (near) the whole-world fit. */
    public static boolean visible(WorldCamera cam) {
        return cam.zoom() > Config.MIN_ZOOM * 1.05f;
    }

    /** True if a logical-space point is over the (visible) mini-map, so tools skip it. */
    public boolean overMiniMap(float lx, float ly, WorldCamera cam) {
        if (!visible(cam)) return false;
        float x = Config.VIEW_W - MM_W - INSET;
        float y = Config.VIEW_H - MM_H - INSET;
        return lx >= x - 2 && lx <= x + MM_W + 2 && ly >= y - 2 && ly <= y + MM_H + 2;
    }

    /** Jumps the camera so its view centers on the world point clicked on the mini-map. */
    public void jumpTo(float lx, float ly, WorldCamera cam) {
        if (!visible(cam)) return;
        float x = Config.VIEW_W - MM_W - INSET;
        float y = Config.VIEW_H - MM_H - INSET;
        float localX = clamp(lx - x, 0f, MM_W);           // from mini-map's left
        float localTop = clamp((y + MM_H) - ly, 0f, MM_H); // logical y-up -> top-origin
        cam.centerOn(localX / MM_W * Config.WORLD_PX_W,
                     localTop / MM_H * Config.WORLD_PX_H);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Draws the mini-map, unless the camera is zoomed out to (near) the whole world. */
    public void render(SpriteBatch batch, ShapeRenderer sr, Camera viewCam, World world, WorldCamera cam) {
        if (!visible(cam)) return;
        rebuild(world);

        float x = Config.VIEW_W - MM_W - INSET;
        float y = Config.VIEW_H - MM_H - INSET; // top-right, logical y-up

        Gdx.gl.glEnable(GL20.GL_BLEND);

        sr.setProjectionMatrix(viewCam.combined);
        sr.begin(ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.5f);
        sr.rect(x - 2, y - 2, MM_W + 4, MM_H + 4);
        sr.end();

        batch.setProjectionMatrix(viewCam.combined);
        batch.begin();
        batch.draw(texture, x, y, MM_W, MM_H, 0, 0, MM_W, MM_H, false, false);
        batch.end();

        // border + current-view rectangle
        float sx = MM_W / (float) Config.WORLD_PX_W;
        float sy = MM_H / (float) Config.WORLD_PX_H;
        float rx = cam.x() * sx;
        float ry = cam.y() * sy;
        float rw = (Config.VIEW_W / cam.zoom()) * sx;
        float rh = (Config.VIEW_H / cam.zoom()) * sy;

        sr.begin(ShapeType.Line);
        sr.setColor(1f, 1f, 1f, 0.6f);
        sr.rect(x, y, MM_W, MM_H);
        sr.setColor(1f, 1f, 0.35f, 0.95f);
        sr.rect(x + rx, y + MM_H - ry - rh, rw, rh); // world y-down -> logical y-up
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void rebuild(World world) {
        for (int my = 0; my < MM_H; my++) {
            int ty = my * world.h / MM_H;
            for (int mx = 0; mx < MM_W; mx++) {
                int tx = mx * world.w / MM_W;
                pixmap.drawPixel(mx, my, colorOf(world.at(tx, ty)));
            }
        }
        texture.draw(pixmap, 0, 0);
    }

    private int colorOf(Tile t) {
        if (t.water > Config.WATER_RENDER_THRESHOLD) return waterRgba;
        if (t.rock) return rockRgba;
        return terrainRgba[t.terrain.ordinal()];
    }

    @Override
    public void dispose() {
        texture.dispose();
        pixmap.dispose();
    }
}
