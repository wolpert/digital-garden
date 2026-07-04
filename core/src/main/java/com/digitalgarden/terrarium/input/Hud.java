package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tool;
import com.digitalgarden.terrarium.render.WorldCamera;

/**
 * The minimal on-screen tool palette (bottom-left), the brush cursor, and a
 * hover tooltip naming the tool under the pointer. Owns the selected tool.
 * Buttons and brush are drawn in the logical 480x270 space; the tooltip text is
 * drawn in screen space (a separate camera) so the font stays crisp at any
 * window size.
 */
public class Hud implements Disposable {
    private static final int INSET = 3, PAD = 3, SIZE = 16, GAP = 3;
    private static final Tool[] TOOLS = Tool.values();
    private static final float PANEL_TOP = INSET + PAD * 2 + SIZE;

    private final Viewport viewport;
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 tmp = new Vector3();
    private int selected = 0;

    public Hud(Viewport viewport) {
        this.viewport = viewport;
    }

    public Tool selected() {
        return TOOLS[selected];
    }

    public void select(int i) {
        if (i >= 0 && i < TOOLS.length) selected = i;
    }

    /** Palette button under logical point (lx,ly), or -1 if none. */
    public int buttonAt(float lx, float ly) {
        float y = INSET + PAD;
        if (ly < y || ly > y + SIZE) return -1;
        for (int i = 0; i < TOOLS.length; i++) {
            float x = INSET + PAD + i * (SIZE + GAP);
            if (lx >= x && lx <= x + SIZE) return i;
        }
        return -1;
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, Camera uiCam,
                       WorldCamera cam, boolean carrying) {
        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        float lx = tmp.x, ly = tmp.y;
        Tool tool = selected();

        sr.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        float panelW = PAD * 2 + TOOLS.length * SIZE + (TOOLS.length - 1) * GAP;

        sr.begin(ShapeType.Filled);
        // translucent panel behind the buttons
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.rect(INSET, INSET, panelW, PAD * 2 + SIZE);
        for (int i = 0; i < TOOLS.length; i++) {
            float x = INSET + PAD + i * (SIZE + GAP);
            float y = INSET + PAD;
            sr.setColor(TOOLS[i].swatch);
            sr.rect(x, y, SIZE, SIZE);
            if (TOOLS[i] == Tool.SEED_FLOWER) { // a little bloom on the swatch
                sr.setColor(1f, 0.95f, 0.4f, 1f);
                sr.rect(x + SIZE / 2f - 1.5f, y + SIZE / 2f - 1.5f, 3f, 3f);
            }
        }
        if (carrying) { // a rock riding the cursor
            sr.setColor(0.50f, 0.50f, 0.53f, 0.9f);
            float gx = (float) Math.floor(lx / Config.TILE_SIZE) * Config.TILE_SIZE;
            float gy = (float) Math.floor(ly / Config.TILE_SIZE) * Config.TILE_SIZE;
            sr.rect(gx - Config.TILE_SIZE, gy - Config.TILE_SIZE,
                    Config.TILE_SIZE * 3f, Config.TILE_SIZE * 3f);
        }
        sr.end();

        sr.begin(ShapeType.Line);
        for (int i = 0; i < TOOLS.length; i++) {
            float x = INSET + PAD + i * (SIZE + GAP);
            float y = INSET + PAD;
            if (i == selected) sr.setColor(1f, 1f, 1f, 1f);
            else sr.setColor(0f, 0f, 0f, 0.6f);
            sr.rect(x, y, SIZE, SIZE);
        }
        boolean overBoard = lx >= 0 && lx < Config.VIEW_W && ly >= 0 && ly < Config.VIEW_H;
        boolean overPanel = lx <= INSET + panelW && ly <= PANEL_TOP;
        if (overBoard && !overPanel) {
            if (tool == Tool.WATER) {
                sr.setColor(0.7f, 0.85f, 1f, 0.9f);
                sr.circle(lx, ly, Config.POUR_RADIUS * Config.TILE_SIZE);
            } else {
                drawTileBrush(sr, cam, lx, ly, tool);
            }
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- tooltip (screen space) ---
        int hover = buttonAt(lx, ly);
        if (hover >= 0) {
            drawTooltip(batch, uiCam, hover);
        }
    }

    /** Outlines the world tile under the cursor, accounting for the camera scroll. */
    private void drawTileBrush(ShapeRenderer sr, WorldCamera cam, float lx, float ly, Tool tool) {
        int ts = Config.TILE_SIZE;
        int wx = cam.pxX() + (int) Math.floor(lx);
        int wy = cam.pxY() + (int) Math.floor(Config.VIEW_H - ly);
        int tileX = wx / ts, tileY = wy / ts;
        float viewX = tileX * ts - cam.pxX();
        float viewYTop = tileY * ts - cam.pxY();       // top-origin
        float logicalY = Config.VIEW_H - (viewYTop + ts); // to y-up logical
        sr.setColor(tool.swatch.r, tool.swatch.g, tool.swatch.b, 1f);
        sr.rect(viewX, logicalY, ts, ts);
    }

    private void drawTooltip(SpriteBatch batch, Camera uiCam, int button) {
        // anchor above the hovered button, in logical space, then project to screen
        float bx = INSET + PAD + button * (SIZE + GAP) + SIZE / 2f;
        tmp.set(bx, PANEL_TOP + 2f, 0f);
        viewport.project(tmp); // -> screen pixels, origin bottom-left (y-up), matching uiCam

        String text = TOOLS[button].label;
        layout.setText(font, text);
        float tx = tmp.x - layout.width / 2f;
        float ty = tmp.y + layout.height + 6f;

        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        font.setColor(0f, 0f, 0f, 0.7f); // shadow for readability
        font.draw(batch, text, tx + 1f, ty - 1f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, tx, ty);
        batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
