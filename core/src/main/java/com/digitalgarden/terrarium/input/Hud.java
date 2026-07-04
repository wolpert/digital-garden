package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tool;

/**
 * The minimal on-screen tool palette (bottom-left) plus the brush cursor. Owns
 * the selected tool. Buttons are colored swatches; the selected one gets a white
 * border. The brush shows where the current tool will act: a soft radius for the
 * watering can, or a single-tile outline for seeds and rocks.
 */
public class Hud {
    private static final int INSET = 3, PAD = 3, SIZE = 16, GAP = 3;
    private static final Tool[] TOOLS = Tool.values();

    private final Viewport viewport;
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

    public void render(ShapeRenderer sr, boolean carrying) {
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
        // button swatches
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
        // a rock riding the cursor while carried
        if (carrying) {
            sr.setColor(0.50f, 0.50f, 0.53f, 0.9f);
            float gx = (float) Math.floor(lx / Config.TILE_SIZE) * Config.TILE_SIZE;
            float gy = (float) Math.floor(ly / Config.TILE_SIZE) * Config.TILE_SIZE;
            sr.rect(gx - Config.TILE_SIZE, gy - Config.TILE_SIZE,
                    Config.TILE_SIZE * 3f, Config.TILE_SIZE * 3f);
        }
        sr.end();

        sr.begin(ShapeType.Line);
        // button borders (selected = white, rest = dark)
        for (int i = 0; i < TOOLS.length; i++) {
            float x = INSET + PAD + i * (SIZE + GAP);
            float y = INSET + PAD;
            if (i == selected) sr.setColor(1f, 1f, 1f, 1f);
            else sr.setColor(0f, 0f, 0f, 0.6f);
            sr.rect(x, y, SIZE, SIZE);
        }
        // brush cursor over the board
        boolean overBoard = lx >= 0 && lx < Config.VIEW_W && ly >= 0 && ly < Config.VIEW_H;
        boolean overPanel = lx <= INSET + panelW && ly <= INSET + PAD * 2 + SIZE;
        if (overBoard && !overPanel) {
            if (tool == Tool.WATER) {
                sr.setColor(0.7f, 0.85f, 1f, 0.9f);
                sr.circle(lx, ly, Config.POUR_RADIUS * Config.TILE_SIZE);
            } else {
                float gx = (float) Math.floor(lx / Config.TILE_SIZE) * Config.TILE_SIZE;
                float gy = (float) Math.floor(ly / Config.TILE_SIZE) * Config.TILE_SIZE;
                sr.setColor(tool.swatch.r, tool.swatch.g, tool.swatch.b, 1f);
                sr.rect(gx, gy, Config.TILE_SIZE, Config.TILE_SIZE);
            }
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
