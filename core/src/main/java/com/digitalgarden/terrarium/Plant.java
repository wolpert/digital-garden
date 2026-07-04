package com.digitalgarden.terrarium;

/**
 * The seed/plant kinds and their growth needs. A tile stores a plant as its
 * {@link Tile#plantType} (0 = none, otherwise {@code ordinal + 1}) and a
 * {@link Tile#growth} of 0..1. Grass and trees convert their tile's terrain when
 * they mature; flowers just bloom in place.
 */
public enum Plant {
    /** Fast, likes moderate moisture; matures into grass terrain. */
    GRASS(0.25f, 0.40f, 0.80f, 0.15f, TerrainType.GRASS),
    /** Slow, needs sustained moisture; matures into a tree. */
    TREE(0.45f, 0.60f, 0.95f, 0.045f, TerrainType.TREES),
    /** Hardy, tolerates dry sand; blooms without changing terrain. */
    FLOWER(0.12f, 0.20f, 0.60f, 0.10f, null);

    /** Below this moisture the seed can't advance and slowly dies. */
    public final float minMoisture;
    /** Moisture band where growth is fastest. */
    public final float idealLow, idealHigh;
    /** Growth gained per second at ideal moisture. */
    public final float rate;
    /** Terrain this plant becomes when mature, or null to leave terrain as-is. */
    public final TerrainType matureTerrain;

    Plant(float minMoisture, float idealLow, float idealHigh, float rate, TerrainType matureTerrain) {
        this.minMoisture = minMoisture;
        this.idealLow = idealLow;
        this.idealHigh = idealHigh;
        this.rate = rate;
        this.matureTerrain = matureTerrain;
    }

    /** Id stored on a tile (1-based; 0 means no plant). */
    public int id() {
        return ordinal() + 1;
    }

    public static Plant byId(int id) {
        return (id <= 0 || id > values().length) ? null : values()[id - 1];
    }

    /** 0..1 growth quality for the given moisture (0 = can't grow). */
    public float quality(float m) {
        if (m < minMoisture) return 0f;
        if (m < idealLow) return (m - minMoisture) / (idealLow - minMoisture);
        if (m <= idealHigh) return 1f;
        // waterlogged: still grows, but slower the wetter it gets
        return Math.max(0.2f, 1f - (m - idealHigh) / (1f - idealHigh) * 0.8f);
    }
}
