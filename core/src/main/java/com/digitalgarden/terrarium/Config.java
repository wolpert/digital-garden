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

    /** The whole world, in tiles. Larger than the view: the camera scrolls over it. */
    public static final int WORLD_W = 480;
    public static final int WORLD_H = 270;
    /** The world's extent in logical pixels. */
    public static final int WORLD_PX_W = WORLD_W * TILE_SIZE;
    public static final int WORLD_PX_H = WORLD_H * TILE_SIZE;

    /** On-screen render resolution (480x270). Scaled up to the window via a FitViewport. */
    public static final int VIEW_W = 480;
    public static final int VIEW_H = 270;

    /** Camera pan speed in on-screen pixels/second (keyboard), constant across zoom. */
    public static final float PAN_SPEED = 260f;

    // --- Zoom (view pixels per world pixel) ---
    /** Zoomed all the way out fits the whole world (world and view share 16:9). */
    public static final float MIN_ZOOM = VIEW_W / (float) WORLD_PX_W;
    public static final float MAX_ZOOM = 6f;
    public static final float DEFAULT_ZOOM = 1f;
    /** Multiplicative zoom change per mouse-wheel notch. */
    public static final float ZOOM_STEP = 1.15f;

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

    // --- Springs / drains ---
    /** Water a spring emits per second (feeds rivers/lakes that outlast evaporation). */
    public static final float SPRING_RATE = 1.6f;
    /** Water a drain removes per second. */
    public static final float DRAIN_RATE = 2.0f;

    // --- Weather ---
    /** Cloud drift speed (cloud-noise units/second); wind direction wanders on its own. */
    public static final float WIND_SPEED = 0.12f;
    /** Spatial frequency of the cloud field (smaller = larger, softer clouds). */
    public static final float CLOUD_SCALE = 0.05f;
    /** Cloud-density remap window: below LOW is clear sky, above HIGH is solid cloud. */
    public static final float CLOUD_LOW = 0.42f;
    public static final float CLOUD_HIGH = 0.72f;
    /** Cloud density above which it rains (scaled by the current storm level). */
    public static final float RAIN_THRESHOLD = 0.55f;
    /** Water volume rained per second at full intensity. Balanced against evaporation. */
    public static final float RAIN_RATE = 0.02f;
    /** How strongly rain raises soil moisture per second. */
    public static final float RAIN_MOISTURE = 2.0f;
    /** Cloud shadow offset (tiles) and darkening strength. */
    public static final float CLOUD_SHADOW_DX = 2.5f;
    public static final float CLOUD_SHADOW_DY = 2.5f;
    public static final float SHADOW_STRENGTH = 0.30f;
    /** Opacity of the translucent white cloud bodies. */
    public static final float CLOUD_BODY_ALPHA = 0.40f;
    /** Rain streak fall speed (pixels/second) and diagonal slant. */
    public static final float RAIN_FALL = 55f;

    // --- Plants & growth ---
    /** Water depth that drowns/rots a growing plant. */
    public static final float FLOOD_KILL = 0.14f;
    /** Growth lost per second while a plant is too dry to advance. */
    public static final float DRY_DECAY = 0.06f;
    /** A plant dies once its growth decays below this. */
    public static final float DEATH_MARGIN = -0.35f;
    /** Chance per second a mature tree/flower seeds a suitable neighbor. */
    public static final float SPREAD_CHANCE = 0.03f;

    // --- Moisture-driven terrain transitions (chance per second) ---
    public static final float WET_MOISTURE = 0.60f;   // above this, dirt greens
    public static final float DRY_MOISTURE = 0.16f;   // below this, grass browns
    public static final float DIRT_TO_GRASS = 0.05f;
    public static final float GRASS_TO_DIRT = 0.03f;
    public static final float DIRT_TO_SAND = 0.02f;
}
