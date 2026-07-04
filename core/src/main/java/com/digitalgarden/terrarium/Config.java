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

    // --- Fluid simulation ---
    /** Fraction of the equalizing flow applied per tick (< 1 keeps it from oscillating). */
    public static final float FLOW_DAMP = 0.5f;
    /** Water depth below which a tile is snapped to bone-dry. */
    public static final float FLUID_MIN = 0.0015f;
    /** Base water lost to evaporation per tick (scaled by terrain). Kept low so
     *  pooled water lingers for minutes; rain (a later milestone) replenishes it. */
    public static final float EVAP_BASE = 0.00006f;
    public static final float EVAP_SAND = 1.3f;   // bakes off fast
    public static final float EVAP_TREES = 0.4f;  // shaded, retains
    /** How damp land next to standing water becomes, and how fast it gets there. */
    public static final float SHORE_MOISTURE = 0.7f;
    public static final float MOISTURE_WET_RATE = 0.02f;
    public static final float MOISTURE_DRY_RATE = 0.002f;

    // --- Pour tool ---
    /** Water volume added per second at the center of the brush. */
    public static final float POUR_RATE = 2.5f;
    /** Brush radius in tiles. */
    public static final int POUR_RADIUS = 4;
}
