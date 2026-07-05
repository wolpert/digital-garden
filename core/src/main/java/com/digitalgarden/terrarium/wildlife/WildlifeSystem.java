package com.digitalgarden.terrarium.wildlife;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.TerrainType;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;
import com.digitalgarden.terrarium.render.WorldCamera;
import com.digitalgarden.terrarium.wildlife.Species.Habitat;

/**
 * A lightweight ecosystem of moving critters. Rabbits graze grass (over-grazing
 * bares it to dirt), drink from and breed near water, and flee foxes; foxes hunt
 * rabbits; all die of starvation or old age. Birds, fish and butterflies are
 * ambient — kept near a target count, drawn to trees/water/flowers. Everything
 * reacts to the live world: droughts starve the grass and the herbivores with it.
 */
public class WildlifeSystem {
    private static final float TS = Config.TILE_SIZE;

    // energy / lifecycle (per second unless noted)
    private static final float DRAIN_RABBIT = 0.022f, DRAIN_FOX = 0.014f;
    private static final float GRAZE_GAIN = 0.5f, FOX_MEAL = 0.6f;
    private static final float MAX_AGE_RABBIT = 130f, MAX_AGE_FOX = 170f;
    private static final float OVERGRAZE_CHANCE = 0.25f; // per second while grazing
    private static final float BREED_RABBIT = 0.20f, BREED_FOX = 0.09f;
    private static final float AMBIENT_SPAWN = 0.4f;

    // senses (world pixels)
    private static final float FOX_SENSE = 70f, FOX_EAT = 5f, RABBIT_FLEE = 45f;
    private static final float FLOWER_SENSE_TILES = 7;

    private final World world;
    private final Random rng;
    private final List<Critter> critters = new ArrayList<>();
    private final List<Critter> babies = new ArrayList<>();

    public WildlifeSystem(World world, long seed) {
        this.world = world;
        this.rng = new Random(seed);
        for (Species s : Species.values()) {
            for (int i = 0; i < s.initial; i++) spawn(s);
        }
    }

    public List<Critter> critters() {
        return critters;
    }

    // --- update ---

    public void update(float dt) {
        if (dt > 0.05f) dt = 0.05f; // clamp after a hitch
        babies.clear();

        for (Critter c : critters) {
            c.age += dt;
            c.breedCooldown -= dt;
            switch (c.species) {
                case RABBIT: rabbit(c, dt); break;
                case FOX: fox(c, dt); break;
                case BIRD: fly(c, dt, true); break;
                case BUTTERFLY: butterfly(c, dt); break;
                case FISH: fish(c, dt); break;
                default: break;
            }
        }

        critters.removeIf(c -> !c.alive);
        critters.addAll(babies);
        replenish(dt);
    }

    private void rabbit(Critter c, float dt) {
        c.energy -= DRAIN_RABBIT * dt;

        Critter fox = nearest(Species.FOX, c.x, c.y, RABBIT_FLEE);
        if (fox != null) {
            c.heading = MathUtils.atan2(c.y - fox.y, c.x - fox.x); // run away
            moveLand(c, c.species.speed * 1.8f, dt);
        } else {
            Tile here = tileAt(c.x, c.y);
            if (here != null && here.terrain == TerrainType.GRASS && here.plantType == 0 && c.energy < 0.95f) {
                c.energy = Math.min(1f, c.energy + GRAZE_GAIN * dt);
                if (rng.nextFloat() < OVERGRAZE_CHANCE * dt) here.terrain = TerrainType.DIRT; // grazed bare
            }
            wander(c, dt);
            moveLand(c, c.species.speed, dt);
        }

        if (c.energy > 0.8f && c.age > 8f && c.breedCooldown <= 0f
                && count(Species.RABBIT) < Species.RABBIT.maxPop
                && nearWater(c.x, c.y, 5) && rng.nextFloat() < BREED_RABBIT * dt) {
            breed(c, 0.4f, 8f);
        }
        if (c.energy <= 0f || c.age > MAX_AGE_RABBIT) c.alive = false;
    }

    private void fox(Critter c, float dt) {
        c.energy -= DRAIN_FOX * dt;

        Critter prey = nearest(Species.RABBIT, c.x, c.y, FOX_SENSE);
        if (prey != null) {
            c.heading = MathUtils.atan2(prey.y - c.y, prey.x - c.x);
            moveLand(c, c.species.speed, dt);
            if (dist2(c, prey) < FOX_EAT * FOX_EAT) {
                prey.alive = false;
                c.energy = Math.min(1f, c.energy + FOX_MEAL);
            }
        } else {
            wander(c, dt);
            moveLand(c, c.species.speed * 0.7f, dt);
        }

        if (c.energy > 0.85f && c.age > 15f && c.breedCooldown <= 0f
                && count(Species.FOX) < Species.FOX.maxPop && rng.nextFloat() < BREED_FOX * dt) {
            breed(c, 0.5f, 15f);
        }
        if (c.energy <= 0f || c.age > MAX_AGE_FOX) c.alive = false;
    }

    private void butterfly(Critter c, float dt) {
        int tx = (int) (c.x / TS), ty = (int) (c.y / TS);
        int fx = -1, fy = -1;
        outer:
        for (int r = 1; r <= FLOWER_SENSE_TILES; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = tx + dx, y = ty + dy;
                    if (world.inBounds(x, y) && world.at(x, y).plantType == 3) { fx = x; fy = y; break outer; }
                }
            }
        }
        if (fx >= 0) c.heading = MathUtils.atan2((fy + 0.5f) * TS - c.y, (fx + 0.5f) * TS - c.x);
        else wander(c, dt);
        // flutter: jitter the heading a lot
        c.heading += (rng.nextFloat() - 0.5f) * 3f * dt * 10f;
        fly(c, dt, false);
    }

    private void fish(Critter c, float dt) {
        if (!waterAt(c.x, c.y)) { c.alive = false; return; } // its pond dried up
        wander(c, dt);
        float nx = c.x + MathUtils.cos(c.heading) * c.species.speed * dt;
        float ny = c.y + MathUtils.sin(c.heading) * c.species.speed * dt;
        if (waterAt(nx, ny)) { c.x = nx; c.y = ny; }
        else c.heading += MathUtils.PI * 0.5f + rng.nextFloat() * MathUtils.PI;
    }

    /** Free flight with edge bounce; {@code flock} nudges birds toward each other. */
    private void fly(Critter c, float dt, boolean flock) {
        wander(c, dt);
        if (flock) {
            Critter mate = nearest(Species.BIRD, c.x, c.y, 60f);
            if (mate != null && mate != c) {
                float to = MathUtils.atan2(mate.y - c.y, mate.x - c.x);
                c.heading = MathUtils.lerpAngle(c.heading, to, 0.02f);
            }
        }
        float nx = c.x + MathUtils.cos(c.heading) * c.species.speed * dt;
        float ny = c.y + MathUtils.sin(c.heading) * c.species.speed * dt;
        if (nx < 0 || nx > Config.WORLD_PX_W) c.heading = MathUtils.PI - c.heading;
        if (ny < 0 || ny > Config.WORLD_PX_H) c.heading = -c.heading;
        c.x = MathUtils.clamp(nx, 0f, Config.WORLD_PX_W);
        c.y = MathUtils.clamp(ny, 0f, Config.WORLD_PX_H);
    }

    private void wander(Critter c, float dt) {
        if (rng.nextFloat() < 1.5f * dt) c.heading += (rng.nextFloat() - 0.5f) * 1.5f;
    }

    private void moveLand(Critter c, float speed, float dt) {
        float nx = c.x + MathUtils.cos(c.heading) * speed * dt;
        float ny = c.y + MathUtils.sin(c.heading) * speed * dt;
        if (passableLand(nx, ny)) { c.x = nx; c.y = ny; }
        else c.heading += MathUtils.PI * 0.5f + rng.nextFloat() * MathUtils.PI; // turn away
    }

    private void breed(Critter parent, float cost, float cooldown) {
        parent.energy -= cost;
        parent.breedCooldown = cooldown;
        Critter baby = new Critter(parent.species,
                parent.x + rng.nextFloat() * 6 - 3, parent.y + rng.nextFloat() * 6 - 3,
                rng.nextFloat() * MathUtils.PI2, rng.nextFloat() * MathUtils.PI2);
        baby.energy = 0.6f;
        babies.add(baby);
    }

    /** Keep ambient species topped up, and reseed rabbits/foxes if they die out. */
    private void replenish(float dt) {
        for (Species s : Species.values()) {
            if (s.isAmbient() && count(s) < s.maxPop && rng.nextFloat() < AMBIENT_SPAWN * dt) spawn(s);
        }
        if (count(Species.RABBIT) == 0 && rng.nextFloat() < 0.1f * dt) { spawn(Species.RABBIT); spawn(Species.RABBIT); }
        if (count(Species.FOX) == 0 && count(Species.RABBIT) > 25 && rng.nextFloat() < 0.05f * dt) spawn(Species.FOX);
    }

    // --- spawning & queries ---

    private void spawn(Species s) {
        for (int attempt = 0; attempt < 40; attempt++) {
            float x = rng.nextFloat() * Config.WORLD_PX_W;
            float y = rng.nextFloat() * Config.WORLD_PX_H;
            boolean ok = s.habitat == Habitat.AIR
                    || (s.habitat == Habitat.WATER ? waterAt(x, y) : passableLand(x, y));
            if (ok) {
                Critter c = new Critter(s, x, y, rng.nextFloat() * MathUtils.PI2, rng.nextFloat() * MathUtils.PI2);
                if (s == Species.BUTTERFLY) {
                    // random cheerful tint
                    switch (rng.nextInt(4)) {
                        case 0: c.r = 0.95f; c.g = 0.35f; c.b = 0.4f; break;
                        case 1: c.r = 0.98f; c.g = 0.82f; c.b = 0.3f; break;
                        case 2: c.r = 0.8f; c.g = 0.5f; c.b = 0.95f; break;
                        default: c.r = 0.98f; c.g = 0.98f; c.b = 0.98f;
                    }
                }
                critters.add(c);
                return;
            }
        }
    }

    private Critter nearest(Species s, float x, float y, float maxDist) {
        Critter best = null;
        float bestD2 = maxDist * maxDist;
        for (Critter c : critters) {
            if (c.species != s || !c.alive) continue;
            float d2 = (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y);
            if (d2 < bestD2) { bestD2 = d2; best = c; }
        }
        return best;
    }

    private int count(Species s) {
        int n = 0;
        for (Critter c : critters) if (c.species == s && c.alive) n++;
        return n;
    }

    private static float dist2(Critter a, Critter b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    private Tile tileAt(float wx, float wy) {
        int tx = (int) (wx / TS), ty = (int) (wy / TS);
        return world.inBounds(tx, ty) ? world.at(tx, ty) : null;
    }

    private boolean waterAt(float wx, float wy) {
        Tile t = tileAt(wx, wy);
        return t != null && t.water > Config.WATER_RENDER_THRESHOLD;
    }

    private boolean passableLand(float wx, float wy) {
        Tile t = tileAt(wx, wy);
        return t != null && !t.rock && t.water <= Config.WATER_RENDER_THRESHOLD;
    }

    private boolean nearWater(float wx, float wy, int rTiles) {
        int tx = (int) (wx / TS), ty = (int) (wy / TS);
        for (int dy = -rTiles; dy <= rTiles; dy++)
            for (int dx = -rTiles; dx <= rTiles; dx++)
                if (world.inBounds(tx + dx, ty + dy) && world.at(tx + dx, ty + dy).water > Config.WATER_RENDER_THRESHOLD)
                    return true;
        return false;
    }

    // --- render ---

    /** Draws visible critters as small shapes, projected to the view camera. */
    public void draw(ShapeRenderer sr, com.badlogic.gdx.graphics.Camera viewCam, WorldCamera cam, float time) {
        sr.setProjectionMatrix(viewCam.combined);
        float zoom = cam.zoom();
        sr.begin(ShapeType.Filled);
        for (Critter c : critters) {
            float vx = cam.worldToViewX(c.x);
            float vyTop = cam.worldToViewY(c.y);
            if (vx < -6 || vx > Config.VIEW_W + 6 || vyTop < -6 || vyTop > Config.VIEW_H + 6) continue;
            float ly = Config.VIEW_H - vyTop;
            float s = Math.max(1f, c.species.size * zoom);

            float bob = 0f;
            if (c.species == Species.RABBIT || c.species == Species.FOX)
                bob = Math.abs(MathUtils.sin(time * 9f + c.phase)) * 0.7f * zoom;
            else if (c.species == Species.BUTTERFLY)
                bob = MathUtils.sin(time * 22f + c.phase) * 0.9f * zoom;
            else if (c.species == Species.BIRD)
                bob = MathUtils.sin(time * 6f + c.phase) * 0.6f * zoom;

            sr.setColor(c.r, c.g, c.b, 1f);
            sr.rect(vx - s * 0.5f, ly - s * 0.5f + bob, s, s);
        }
        sr.end();
    }
}
