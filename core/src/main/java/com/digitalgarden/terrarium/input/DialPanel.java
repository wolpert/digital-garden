package com.digitalgarden.terrarium.input;

import java.util.ArrayList;
import java.util.List;

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
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;

/**
 * A row of dials in the lower-right that tune the live {@link Settings}: rain,
 * wind, water flow, evaporation, growth, and the two death modes (wither/drought
 * and flood/rot). Each dial is expressed as a multiplier of the game's default
 * ("1.0x"); drag a dial up/down to turn it, and hover it for a tooltip showing
 * its name and current setting.
 */
public class DialPanel implements Disposable {
    private static final int INSET = 4, PAD = 5, R = 7, SPACING = 20;
    /** Vertical drag (logical px) that sweeps a dial across its full range. */
    private static final float DRAG_SPAN = 55f;

    @FunctionalInterface
    private interface Setter { void set(float v); }

    private static final class Dial {
        final String label;
        final float baseline;   // value at 1.0x
        final float maxMult;    // top of the range, in multiples of baseline
        final Setter setter;
        float mult = 1f;

        Dial(String label, float baseline, float maxMult, Setter setter) {
            this.label = label;
            this.baseline = baseline;
            this.maxMult = maxMult;
            this.setter = setter;
            apply();
        }

        void apply() { setter.set(baseline * mult); }
        void setMult(float m) { mult = MathUtils.clamp(m, 0f, maxMult); apply(); }
        float normalized() { return mult / maxMult; }
        String display() { return label + "  " + String.format("%.1fx", mult); }
    }

    private final com.badlogic.gdx.utils.viewport.Viewport viewport;
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 tmp = new Vector3();
    private final List<Dial> dials = new ArrayList<>();
    private final float panelX, panelY, panelW, panelH, cy;

    public DialPanel(com.badlogic.gdx.utils.viewport.Viewport viewport, Settings s) {
        this.viewport = viewport;
        dials.add(new Dial("Rain", Config.RAIN_RATE, 3f, v -> s.rainRate = v));
        dials.add(new Dial("Wind", Config.WIND_SPEED, 3f, v -> s.windSpeed = v));
        dials.add(new Dial("Water flow", Config.FLOW_DAMP, 1.6f, v -> s.flowDamp = v));
        dials.add(new Dial("Evaporation", Config.EVAP_BASE, 4f, v -> s.evapBase = v));
        dials.add(new Dial("Growth", 1f, 3f, v -> s.growthRate = v));
        dials.add(new Dial("Wither (drought)", Config.DRY_DECAY, 3f, v -> s.dryDecay = v));
        dials.add(new Dial("Flood (rot)", Config.FLOOD_KILL, 3f, v -> s.floodKill = v));

        panelW = PAD * 2 + (dials.size() - 1) * SPACING + 2 * R;
        panelH = PAD * 2 + 2 * R;
        panelX = Config.VIEW_W - INSET - panelW;
        panelY = INSET;
        cy = panelY + PAD + R;
    }

    private float dialX(int i) { return panelX + PAD + R + i * SPACING; }

    /** Dial under a logical-space point, or -1. */
    public int dialAt(float lx, float ly) {
        if (ly < panelY || ly > panelY + panelH) return -1;
        for (int i = 0; i < dials.size(); i++) {
            float dx = lx - dialX(i), dy = ly - cy;
            if (dx * dx + dy * dy <= (R + 2) * (R + 2)) return i;
        }
        return -1;
    }

    public boolean overPanel(float lx, float ly) {
        return lx >= panelX && lx <= panelX + panelW && ly >= panelY && ly <= panelY + panelH;
    }

    /** Turns dial {@code i} by a fraction of its full range (drag delta / span). */
    public void nudge(int i, float deltaFraction) {
        Dial d = dials.get(i);
        d.setMult(d.mult + deltaFraction * d.maxMult);
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, Camera uiCam) {
        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        int hover = dialAt(tmp.x, tmp.y);

        sr.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        sr.begin(ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.rect(panelX, panelY, panelW, panelH);
        for (int i = 0; i < dials.size(); i++) {
            sr.setColor(0.13f, 0.14f, 0.18f, 0.95f);
            sr.circle(dialX(i), cy, R, 18);
        }
        sr.end();

        sr.begin(ShapeType.Line);
        for (int i = 0; i < dials.size(); i++) {
            Dial d = dials.get(i);
            if (i == hover) sr.setColor(1f, 1f, 1f, 1f);
            else sr.setColor(0.65f, 0.7f, 0.78f, 0.9f);
            sr.circle(dialX(i), cy, R, 22);
            // needle: 0 -> 225deg (down-left), max -> -45deg (down-right), clockwise
            float a = (225f - d.normalized() * 270f) * MathUtils.degRad;
            sr.setColor(0.5f, 0.85f, 1f, 1f);
            sr.line(dialX(i), cy, dialX(i) + MathUtils.cos(a) * (R - 1), cy + MathUtils.sin(a) * (R - 1));
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        if (hover >= 0) drawTooltip(batch, uiCam, hover);
    }

    private void drawTooltip(SpriteBatch batch, Camera uiCam, int i) {
        tmp.set(dialX(i), panelY + panelH + 2f, 0f);
        viewport.project(tmp); // -> screen pixels, origin bottom-left (matches uiCam)

        String text = dials.get(i).display();
        layout.setText(font, text);
        float tx = tmp.x - layout.width / 2f;
        float ty = tmp.y + layout.height + 6f;

        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        font.setColor(0f, 0f, 0f, 0.7f);
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
