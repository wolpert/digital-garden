package com.digitalgarden.terrarium.sim;

import java.util.Arrays;

import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;

/**
 * Shallow-water cellular automaton. Each tile carries a water depth; the
 * "hydraulic head" of a tile is its terrain {@code elevation + water}. Water
 * flows only from higher head to lower head, moving a damped, capped fraction of
 * what would equalize the pair, so it pools in low ground, forms lakes and
 * rivers, and settles to a flat surface without oscillating or gaining volume.
 *
 * <p>Rocks and terrain shape dam and divert flow (a rock is an impermeable wall:
 * it neither holds water nor accepts inflow). Only evaporation and pouring/rain
 * change the total volume; the flow step conserves it exactly.
 */
public class FluidSystem {
    private final float[] delta;
    private final Settings settings;

    public FluidSystem(World world, Settings settings) {
        delta = new float[world.tiles.length];
        this.settings = settings;
    }

    /** Advances the fluid by one fixed sim tick. */
    public void step(World world) {
        flow(world);
        evaporateAndDampen(world);
    }

    private void flow(World world) {
        final int W = world.w, H = world.h;
        Arrays.fill(delta, 0f);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                Tile ti = world.tiles[i];
                if (ti.rock) continue;
                float wi = ti.water;
                if (wi <= Config.FLUID_MIN) continue;

                float hi = ti.elevation + wi;
                // Head drop to each neighbor (0 if the neighbor is higher, out of
                // bounds, or a rock). All read from the same pre-tick snapshot.
                float dL = drop(world, hi, x - 1, y);
                float dR = drop(world, hi, x + 1, y);
                float dU = drop(world, hi, x, y - 1);
                float dD = drop(world, hi, x, y + 1);
                float sum = dL + dR + dU + dD;
                if (sum <= 0f) continue;

                // Moving drop/2 to a neighbor would equalize that pair, so the
                // total "equalizing" outflow is sum*0.5. Damp it, then cap so we
                // never move more water than the tile actually holds.
                float proposed = sum * 0.5f;
                float scale = settings.flowDamp;
                if (proposed * scale > wi) scale = wi / proposed;
                scale *= 0.5f; // drop/2 -> per-neighbor factor

                if (dL > 0f) move(i, i - 1, dL * scale);
                if (dR > 0f) move(i, i + 1, dR * scale);
                if (dU > 0f) move(i, i - W, dU * scale);
                if (dD > 0f) move(i, i + W, dD * scale);
            }
        }

        for (int i = 0; i < delta.length; i++) {
            Tile t = world.tiles[i];
            t.water += delta[i];
            if (t.water < 0f) t.water = 0f;
        }
    }

    private void move(int from, int to, float amount) {
        delta[from] -= amount;
        delta[to] += amount;
    }

    private static float drop(World world, float hi, int nx, int ny) {
        if (!world.inBounds(nx, ny)) return 0f;
        Tile tj = world.at(nx, ny);
        if (tj.rock) return 0f;
        float d = hi - (tj.elevation + tj.water);
        return d > 0f ? d : 0f;
    }

    private void evaporateAndDampen(World world) {
        for (int y = 0; y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                Tile t = world.at(x, y);
                if (t.rock) continue;

                if (t.water > Config.FLUID_MIN) {
                    float factor = t.terrain == TerrainType.SAND ? Config.EVAP_SAND
                            : t.terrain == TerrainType.TREES ? Config.EVAP_TREES : 1f;
                    t.water -= settings.evapBase * factor;
                    if (t.water < Config.FLUID_MIN) t.water = 0f;
                    t.moisture = 1f; // submerged ground is saturated
                } else {
                    // Dry land eases toward damp near water, otherwise slowly dries.
                    boolean nearWater = neighborHasWater(world, x, y);
                    float target = nearWater ? Config.SHORE_MOISTURE : 0f;
                    float rate = nearWater ? Config.MOISTURE_WET_RATE : Config.MOISTURE_DRY_RATE;
                    t.moisture += (target - t.moisture) * rate;
                }
            }
        }
    }

    private static boolean neighborHasWater(World world, int x, int y) {
        return hasWater(world, x - 1, y) || hasWater(world, x + 1, y)
                || hasWater(world, x, y - 1) || hasWater(world, x, y + 1);
    }

    private static boolean hasWater(World world, int x, int y) {
        return world.inBounds(x, y) && world.at(x, y).water > Config.WATER_RENDER_THRESHOLD;
    }
}
