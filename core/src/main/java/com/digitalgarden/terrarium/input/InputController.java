package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Plant;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.Tool;
import com.digitalgarden.terrarium.World;

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
    private final Vector3 tmp = new Vector3();

    private boolean wasTouched;
    private boolean hudCapture;   // this press began on the palette
    private boolean carrying;     // holding a picked-up rock
    private int carryFromX, carryFromY;

    public InputController(World world, Viewport viewport, Hud hud) {
        this.world = world;
        this.viewport = viewport;
        this.hud = hud;
    }

    public boolean isCarrying() {
        return carrying;
    }

    public void update(float dt) {
        boolean touched = Gdx.input.isTouched();
        boolean justDown = touched && !wasTouched;
        boolean justUp = !touched && wasTouched;

        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        float lx = tmp.x, ly = tmp.y;
        int cx = (int) Math.floor(lx / Config.TILE_SIZE);
        // screen-top maps to tile row 0 (renderer draws row 0 at the top)
        int cy = (int) Math.floor((Config.VIEW_H - ly) / Config.TILE_SIZE);

        if (justDown) {
            int btn = hud.buttonAt(lx, ly);
            hudCapture = btn >= 0;
            if (hudCapture) hud.select(btn);
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
                default:
                    break;
            }
        }

        if (justUp) hudCapture = false;
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
                return;
            }
        }
        world.at(carryFromX, carryFromY).rock = true; // invalid target: put it back
    }
}
