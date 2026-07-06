package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;

/**
 * The configuration menu: a gear button in the top-left corner that toggles a small
 * settings window. The only setting so far is <b>Sound</b> — a slider with four discrete
 * levels (0 Off · 1 Soft · 2 Medium · 3 Loud, left→right) bound to {@link Settings#soundLevel}
 * (which persists across runs). Drawn like the other overlays: shapes in the logical 480x270
 * space, text in screen space so the font stays crisp at any window size.
 */
public class ConfigMenu implements Disposable {
    // Gear button (top-left).
    private static final int INSET = 3, BTN = 16, GAP = 3;
    private static final float BTN_X = INSET;
    private static final float BTN_Y = Config.VIEW_H - INSET - BTN;

    // Popup window, dropping down from under the button.
    private static final float WIN_X = INSET, WIN_W = 176, WIN_H = 64;
    private static final float WIN_TOP = BTN_Y - GAP;
    private static final float WIN_BOTTOM = WIN_TOP - WIN_H;

    // Sound slider geometry (logical space).
    private static final float TRACK_X = WIN_X + 14;
    private static final float TRACK_W = WIN_W - 28;
    private static final float SLIDER_Y = WIN_TOP - 44;
    private static final float KNOB_R = 4.5f;

    private final Viewport viewport;
    private final Settings settings;
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 tmp = new Vector3();
    private boolean open;

    public ConfigMenu(Viewport viewport, Settings settings) {
        this.viewport = viewport;
        this.settings = settings;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        open = !open;
    }

    /** True if the logical point is on the gear button. */
    public boolean buttonAt(float lx, float ly) {
        return lx >= BTN_X && lx <= BTN_X + BTN && ly >= BTN_Y && ly <= BTN_Y + BTN;
    }

    /** True if the point is inside the open window (so the press is UI, not a world action). */
    public boolean overWindow(float lx, float ly) {
        return open && lx >= WIN_X && lx <= WIN_X + WIN_W && ly >= WIN_BOTTOM && ly <= WIN_TOP;
    }

    /** True if the point is on/near the sound slider track. */
    public boolean overSlider(float lx, float ly) {
        return open && lx >= TRACK_X - 8 && lx <= TRACK_X + TRACK_W + 8
                && Math.abs(ly - SLIDER_Y) <= 9;
    }

    /** The discrete level (0..3) nearest the given x on the track. */
    public int levelForX(float lx) {
        float f = (lx - TRACK_X) / TRACK_W; // 0..1
        return MathUtils.clamp(Math.round(f * (Config.SOUND_LEVELS - 1)), 0, Config.SOUND_LEVELS - 1);
    }

    /** Sets the sound level from a pointer x on the track (used on press and drag). */
    public void setLevelFromX(float lx) {
        settings.setSoundLevel(levelForX(lx));
    }

    private static float knobX(int level) {
        return TRACK_X + level / (float) (Config.SOUND_LEVELS - 1) * TRACK_W;
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, Camera uiCam) {
        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        boolean hoverBtn = buttonAt(tmp.x, tmp.y);

        sr.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        // --- filled pass ---
        sr.begin(ShapeType.Filled);
        // gear button background
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.rect(BTN_X, BTN_Y, BTN, BTN);
        // gear hub
        float gx = BTN_X + BTN / 2f, gy = BTN_Y + BTN / 2f;
        sr.setColor(hoverBtn ? 1f : 0.8f, hoverBtn ? 1f : 0.82f, hoverBtn ? 1f : 0.88f, 1f);
        sr.circle(gx, gy, 4.2f, 20);
        sr.setColor(0.13f, 0.14f, 0.18f, 1f);
        sr.circle(gx, gy, 1.8f, 14);

        if (open) {
            sr.setColor(0f, 0f, 0f, 0.55f);
            sr.rect(WIN_X, WIN_BOTTOM, WIN_W, WIN_H);
            // slider track
            sr.setColor(0.30f, 0.33f, 0.38f, 1f);
            sr.rect(TRACK_X, SLIDER_Y - 1f, TRACK_W, 2f);
            // filled portion up to the current level
            float kx = knobX(settings.soundLevel);
            sr.setColor(0.35f, 0.72f, 1f, 1f);
            sr.rect(TRACK_X, SLIDER_Y - 1f, kx - TRACK_X, 2f);
            // knob
            sr.setColor(0.6f, 0.86f, 1f, 1f);
            sr.circle(kx, SLIDER_Y, KNOB_R, 18);
        }
        sr.end();

        // --- line pass ---
        sr.begin(ShapeType.Line);
        // gear teeth (short radial spokes)
        sr.setColor(hoverBtn ? 1f : 0.8f, hoverBtn ? 1f : 0.82f, hoverBtn ? 1f : 0.88f, 1f);
        for (int k = 0; k < 6; k++) {
            float a = k * MathUtils.PI / 3f;
            float c = MathUtils.cos(a), s = MathUtils.sin(a);
            sr.line(gx + c * 4.2f, gy + s * 4.2f, gx + c * 6.5f, gy + s * 6.5f);
        }
        // button outline
        sr.setColor(hoverBtn ? 1f : 0f, hoverBtn ? 1f : 0f, hoverBtn ? 1f : 0f, hoverBtn ? 1f : 0.6f);
        sr.rect(BTN_X, BTN_Y, BTN, BTN);

        if (open) {
            // window outline
            sr.setColor(0.65f, 0.7f, 0.78f, 0.9f);
            sr.rect(WIN_X, WIN_BOTTOM, WIN_W, WIN_H);
            // title separator
            sr.setColor(0.4f, 0.44f, 0.5f, 0.7f);
            sr.line(WIN_X + 6, WIN_TOP - 18, WIN_X + WIN_W - 6, WIN_TOP - 18);
            // tick marks at each level
            sr.setColor(0.6f, 0.65f, 0.72f, 0.9f);
            for (int l = 0; l < Config.SOUND_LEVELS; l++) {
                float x = knobX(l);
                sr.line(x, SLIDER_Y - 4f, x, SLIDER_Y + 4f);
            }
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- text (screen space) ---
        if (open) {
            batch.setProjectionMatrix(uiCam.combined);
            batch.begin();
            drawText(batch, "Configuration", WIN_X + 8, WIN_TOP - 4, false);
            drawText(batch, "Sound", WIN_X + 8, WIN_TOP - 24, false);
            drawText(batch, Config.SOUND_LEVEL_NAMES[settings.soundLevel],
                    WIN_X + WIN_W - 8, WIN_TOP - 24, true);
            drawText(batch, "0", TRACK_X, SLIDER_Y - 8, false);
            drawText(batch, "3", TRACK_X + TRACK_W, SLIDER_Y - 8, true);
            batch.end();
        }
    }

    /** Draws a line of text with its top at logical (lx, topY); right-aligned if {@code right}. */
    private void drawText(SpriteBatch batch, String text, float lx, float topY, boolean right) {
        tmp.set(lx, topY, 0f);
        viewport.project(tmp); // -> screen pixels, origin bottom-left (matches uiCam)
        layout.setText(font, text);
        float tx = right ? tmp.x - layout.width : tmp.x;
        font.setColor(0f, 0f, 0f, 0.7f);
        font.draw(batch, text, tx + 1f, tmp.y - 1f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, tx, tmp.y);
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
