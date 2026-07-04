package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;

/**
 * Translates pointer input into world actions. Milestone 2 ships a single tool —
 * the watering can: holding the pointer pours water into a soft radius, which the
 * {@link com.digitalgarden.terrarium.sim.FluidSystem} then makes flow and pool.
 * This is the seam the full tool palette (seed, rock) plugs into later.
 */
public class InputController {
    private final World world;
    private final Viewport viewport;
    private final Vector3 tmp = new Vector3();

    public InputController(World world, Viewport viewport) {
        this.world = world;
        this.viewport = viewport;
    }

    public void update(float dt) {
        if (!Gdx.input.isTouched()) return;

        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp); // -> logical coords, y-up (0 at bottom)
        int cx = (int) Math.floor(tmp.x / Config.TILE_SIZE);
        // the board texture is drawn flipped, so screen-top is the last pixmap row
        int cy = (int) Math.floor((Config.VIEW_H - tmp.y) / Config.TILE_SIZE);
        pour(cx, cy, dt);
    }

    private void pour(int cx, int cy, float dt) {
        int r = Config.POUR_RADIUS;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (!world.inBounds(x, y)) continue;
                Tile t = world.at(x, y);
                if (t.rock) continue; // water can't be poured onto solid rock
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > r) continue;
                t.water += Config.POUR_RATE * (1f - dist / r) * dt;
            }
        }
    }
}
