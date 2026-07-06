package com.digitalgarden.terrarium.sim;

import java.util.Random;

import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Plant;
import com.digitalgarden.terrarium.Settings;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.fx.ParticleSystem;

/**
 * Turns moisture into life. Each tick it advances planted seeds through their
 * growth stages (stalling or killing them when too dry, rotting them when
 * flooded), matures grass and trees into their terrain, lets mature plants
 * spread to suitable neighbors, and nudges terrain itself: persistently wet dirt
 * greens into grass, long-dry grass browns back to dirt, and dry dirt at a
 * water's edge turns to sand. Transitions are stochastic (low per-second odds)
 * so the landscape shifts gradually rather than snapping.
 */
public class GrowthSystem {
    private final Random rng;
    private final Settings settings;
    private final ParticleSystem particles;

    public GrowthSystem(long seed, Settings settings, ParticleSystem particles) {
        this.rng = new Random(seed);
        this.settings = settings;
        this.particles = particles;
    }

    public void step(World world, float dt) {
        for (int y = 0; y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                Tile t = world.at(x, y);
                if (t.plantType != 0) {
                    growPlant(world, x, y, t, dt);
                }
                if (!t.rock && t.water <= Config.WATER_RENDER_THRESHOLD) {
                    transitionTerrain(world, x, y, t, dt);
                }
            }
        }
    }

    private void growPlant(World world, int x, int y, Tile t, float dt) {
        Plant p = Plant.byId(t.plantType);
        if (p == null) { t.plantType = 0; return; }

        if (t.rock || t.water > settings.floodKill) { // uprooted or drowned
            kill(t);
            return;
        }

        float q = p.quality(t.moisture);
        if (q <= 0f) {
            // too dry: wither, and eventually die
            t.growth -= settings.dryDecay * dt;
            if (t.growth < Config.DEATH_MARGIN) kill(t);
            return;
        }

        if (t.growth < 1f) {
            t.growth += p.rate * settings.growthRate * q * dt;
            if (t.growth >= 1f) {
                t.growth = 1f;
                mature(t, p);
                particles.sprout((x + 0.5f) * Config.TILE_SIZE, (y + 0.5f) * Config.TILE_SIZE);
            }
        } else {
            trySpread(world, x, y, p);
        }
    }

    /** Grass/tree become terrain (and stop being a "plant"); flowers stay to bloom. */
    private void mature(Tile t, Plant p) {
        if (p.matureTerrain != null) {
            t.terrain = p.matureTerrain;
            t.plantType = 0;
        }
    }

    private void trySpread(World world, int x, int y, Plant p) {
        // Only trees (as TREES terrain, handled below) and flowers spread here.
        if (p != Plant.FLOWER) return;
        if (rng.nextFloat() < Config.SPREAD_CHANCE * Config.SIM_TICK) {
            sowNeighbor(world, x, y, Plant.FLOWER, TerrainType.GRASS);
        }
    }

    private void transitionTerrain(World world, int x, int y, Tile t, float dt) {
        float m = t.moisture;

        // Mature trees seed nearby land.
        if (t.terrain == TerrainType.TREES
                && rng.nextFloat() < Config.SPREAD_CHANCE * dt) {
            sowNeighbor(world, x, y, Plant.TREE, null);
            return;
        }

        if (t.plantType != 0) return; // don't reshape terrain a seed is using

        switch (t.terrain) {
            case DIRT:
                if (m > Config.WET_MOISTURE && chance(Config.DIRT_TO_GRASS, dt)) {
                    t.terrain = TerrainType.GRASS;
                } else if (m < Config.DRY_MOISTURE && nearWater(world, x, y)
                        && chance(Config.DIRT_TO_SAND, dt)) {
                    t.terrain = TerrainType.SAND;
                }
                break;
            case GRASS:
                if (m < Config.DRY_MOISTURE && chance(Config.GRASS_TO_DIRT, dt)) {
                    t.terrain = TerrainType.DIRT;
                }
                break;
            case SAND:
                if (m > Config.WET_MOISTURE && chance(Config.DIRT_TO_GRASS, dt)) {
                    t.terrain = TerrainType.DIRT; // wet sand builds up into soil
                }
                break;
            default:
                break;
        }
    }

    /** Sows {@code seed} on a random suitable neighbor (empty, dry-ish land). */
    private void sowNeighbor(World world, int x, int y, Plant seed, TerrainType requireTerrain) {
        int dir = rng.nextInt(4);
        int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
        int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
        if (!world.inBounds(nx, ny)) return;
        Tile n = world.at(nx, ny);
        if (n.rock || n.plantType != 0 || n.water > Config.WATER_RENDER_THRESHOLD) return;
        if (n.terrain == TerrainType.WATER) return;
        if (requireTerrain != null && n.terrain != requireTerrain) return;
        if (seed.quality(n.moisture) <= 0f) return; // won't take here
        n.plantType = seed.id();
        n.growth = 0f;
    }

    private boolean chance(float perSecond, float dt) {
        return rng.nextFloat() < perSecond * dt;
    }

    private static void kill(Tile t) {
        t.plantType = 0;
        t.growth = 0f;
    }

    private static boolean nearWater(World world, int x, int y) {
        return wet(world, x - 1, y) || wet(world, x + 1, y)
                || wet(world, x, y - 1) || wet(world, x, y + 1);
    }

    private static boolean wet(World world, int x, int y) {
        return world.inBounds(x, y) && world.at(x, y).water > Config.WATER_RENDER_THRESHOLD;
    }
}
