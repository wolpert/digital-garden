package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Plant;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.Tool;
import com.badlogic.gdx.math.MathUtils;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.fx.ParticleSystem;
import com.digitalgarden.terrarium.render.MiniMap;
import com.digitalgarden.terrarium.render.WorldCamera;
import com.digitalgarden.terrarium.sim.SpringSystem;

/**
 * Turns pointer input into tool actions, dispatching on the {@link Hud}'s
 * selected tool: the watering can pours continuously, seed tools sow along a
 * drag, and the rock tool picks a rock up on press and drops it on release.
 * A press that lands on the palette selects a tool instead of touching the world.
 */
public class InputController {
    private final World world;
    private final Viewport viewport;
    private final Hud hud;
    private final WorldCamera camera;
    private final MiniMap miniMap;
    private final DialPanel dials;
    private final SpringSystem springs;
    private final ParticleSystem particles;
    private final Vector3 tmp = new Vector3();

    private boolean wasTouched;
    private boolean hudCapture;      // this press began on a UI element (palette/mini-map/dial)
    private boolean miniMapCapture;  // ...specifically on the mini-map (drag to scrub)
    private int dialCapture = -1;    // ...specifically on a dial (drag to turn)
    private float lastDialY;
    private boolean carrying;        // holding a picked-up rock
    private int carryFromX, carryFromY;

    public InputController(World world, Viewport viewport, Hud hud, WorldCamera camera,
                           MiniMap miniMap, DialPanel dials, SpringSystem springs, ParticleSystem particles) {
        this.world = world;
        this.viewport = viewport;
        this.hud = hud;
        this.camera = camera;
        this.miniMap = miniMap;
        this.dials = dials;
        this.springs = springs;
        this.particles = particles;
    }

    public boolean isCarrying() {
        return carrying;
    }

    public void update(float dt) {
        // A right-drag (desktop) or a second finger (touch) means the user is
        // panning the camera, not using a tool — ignore the primary pointer then.
        boolean panning = Gdx.input.isButtonPressed(Input.Buttons.RIGHT) || Gdx.input.isTouched(1);
        boolean touched = Gdx.input.isTouched() && !panning;
        boolean justDown = touched && !wasTouched;
        boolean justUp = !touched && wasTouched;

        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        float lx = tmp.x, ly = tmp.y;
        // view pixel (top-origin) -> world pixel via the camera (scroll + zoom) -> tile
        int wx = (int) camera.viewToWorldX(lx);
        int wy = (int) camera.viewToWorldY(Config.VIEW_H - ly);
        int cx = wx / Config.TILE_SIZE;
        int cy = wy / Config.TILE_SIZE;

        if (justDown) {
            int btn = hud.buttonAt(lx, ly);
            if (btn >= 0) hud.select(btn);
            int dial = (btn < 0) ? dials.dialAt(lx, ly) : -1;
            dialCapture = dial;
            if (dial >= 0) lastDialY = ly;
            // a press on the palette / mini-map / dials is UI, not a world action
            miniMapCapture = btn < 0 && dial < 0 && miniMap.overMiniMap(lx, ly, camera);
            hudCapture = btn >= 0 || dial >= 0 || miniMapCapture;
        }

        // hold/drag on the mini-map jumps the camera to that spot
        if (miniMapCapture && touched) {
            miniMap.jumpTo(lx, ly, camera);
        }

        // hold/drag on a dial turns it (drag up = more)
        if (dialCapture >= 0 && touched) {
            dials.nudge(dialCapture, (ly - lastDialY) / 55f);
            lastDialY = ly;
        }

        Tool tool = hud.selected();

        if (!hudCapture) {
            switch (tool) {
                case WATER:
                    if (touched) pour(cx, cy, dt);
                    break;
                case SEED_GRASS:
                case SEED_TREE:
                case SEED_FLOWER:
                    if (touched) sow(cx, cy, tool.seed);
                    break;
                case ROCK:
                    if (justDown) pickRock(cx, cy);
                    if (justUp) dropRock(cx, cy);
                    break;
                case SPRING:
                    if (justDown) springs.toggle(cx, cy, 1);
                    break;
                case DRAIN:
                    if (justDown) springs.toggle(cx, cy, 2);
                    break;
                case RAISE:
                    if (touched) sculpt(cx, cy, dt, +1f);
                    break;
                case LOWER:
                    if (touched) sculpt(cx, cy, dt, -1f);
                    break;
                default:
                    break;
            }
        }

        if (justUp) {
            hudCapture = false;
            miniMapCapture = false;
            dialCapture = -1;
        }
        wasTouched = touched;
    }

    private void pour(int cx, int cy, float dt) {
        int r = Config.POUR_RADIUS;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (!world.inBounds(x, y)) continue;
                Tile t = world.at(x, y);
                if (t.rock) continue;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > r) continue;
                t.water += Config.POUR_RATE * (1f - dist / r) * dt;
            }
        }
        if (MathUtils.random() < 0.4f) {
            particles.splash((cx + 0.5f) * Config.TILE_SIZE, (cy + 0.5f) * Config.TILE_SIZE, 2);
        }
    }

    private void sculpt(int cx, int cy, float dt, float dir) {
        int r = Config.SCULPT_RADIUS;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (!world.inBounds(x, y)) continue;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > r) continue;
                Tile t = world.at(x, y);
                float e = t.elevation + dir * Config.SCULPT_RATE * (1f - dist / r) * dt;
                t.elevation = e < 0f ? 0f : (e > 1f ? 1f : e);
            }
        }
    }

    private void sow(int cx, int cy, Plant seed) {
        if (!world.inBounds(cx, cy)) return;
        Tile t = world.at(cx, cy);
        if (t.rock || t.plantType != 0) return;
        if (t.water > Config.WATER_RENDER_THRESHOLD) return;
        if (t.terrain == TerrainType.WATER || t.terrain == TerrainType.TREES) return;
        t.plantType = seed.id();
        t.growth = 0f;
    }

    private void pickRock(int cx, int cy) {
        if (carrying || !world.inBounds(cx, cy)) return;
        Tile t = world.at(cx, cy);
        if (!t.rock) return;
        t.rock = false;
        carrying = true;
        carryFromX = cx;
        carryFromY = cy;
    }

    private void dropRock(int cx, int cy) {
        if (!carrying) return;
        carrying = false;
        if (world.inBounds(cx, cy)) {
            Tile t = world.at(cx, cy);
            boolean valid = !t.rock && t.water <= Config.WATER_RENDER_THRESHOLD
                    && t.terrain != TerrainType.WATER;
            if (valid) {
                t.rock = true;
                t.plantType = 0; // a dropped rock crushes any seedling
                t.growth = 0f;
                t.spring = 0;    // ...and caps a spring/drain (SpringSystem prunes it)
                particles.dust((cx + 0.5f) * Config.TILE_SIZE, (cy + 0.5f) * Config.TILE_SIZE);
                return;
            }
        }
        world.at(carryFromX, carryFromY).rock = true; // invalid target: put it back
    }
}
