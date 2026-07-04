package com.digitalgarden.terrarium;

/**
 * A single cell of the world. Terrain, moisture and the fluid water level all
 * live here; later milestones (growth, weather, fluid flow) mutate these fields.
 */
public class Tile {
    /** Surface terrain type. */
    public TerrainType terrain = TerrainType.DIRT;
    /** Static heightmap value 0..1 that drives water flow and generation. */
    public float elevation;
    /** Soil moisture 0..1. */
    public float moisture;
    /** Fluid water volume (depth units). 0 = dry. Simulated by the FluidSystem later. */
    public float water;
    /** A solid rock the player can move; blocks growth and dams flow. */
    public boolean rock;

    // --- Plant state (used by the growth milestone) ---
    /** 0 = no plant; otherwise a seed-type id. */
    public int plantType;
    /** Growth progress 0..1 for whatever plant occupies this tile. */
    public float growth;
}
