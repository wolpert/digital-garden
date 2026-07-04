package com.digitalgarden.terrarium;

/**
 * Central tuning for Terrarium. Every magic number the simulation and renderer
 * care about lives here so the whole feel of the world can be adjusted in one place.
 */
public final class Config {
    private Config() {}

    // --- Board geometry ---
    /** Logical pixels per tile. Small tiles = fine, chunky pixel look. */
    public static final int TILE_SIZE = 3;
    public static final int GRID_W = 160;
    public static final int GRID_H = 90;
    /** Logical render resolution (480x270). Scaled up to the window via a FitViewport. */
    public static final int VIEW_W = GRID_W * TILE_SIZE;
    public static final int VIEW_H = GRID_H * TILE_SIZE;

    // --- Simulation ---
    /** Seconds per fixed simulation tick (~18 ticks/second). */
    public static final float SIM_TICK = 1f / 18f;
    public static final long SEED = 20260704L;

    // --- Terrain generation ---
    /** Heightmap value below which tiles collect water at world-gen. */
    public static final float SEA_LEVEL = 0.34f;
    /** Water depth (in fluid units) above which a tile renders as water. */
    public static final float WATER_RENDER_THRESHOLD = 0.03f;
    /** Depth that maps to the deepest water shade. */
    public static final float WATER_DEEP = 0.5f;
}
