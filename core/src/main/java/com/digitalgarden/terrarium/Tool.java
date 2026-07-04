package com.digitalgarden.terrarium;

import com.badlogic.gdx.graphics.Color;

/**
 * A selectable action in the tool palette. WATER pours; the three seed tools sow
 * their {@link Plant}; ROCK picks up and drops rocks. The {@code swatch} color is
 * what the HUD button shows.
 */
public enum Tool {
    WATER(null, "Water", new Color(0.32f, 0.55f, 0.85f, 1f)),
    SEED_GRASS(Plant.GRASS, "Grass seed", new Color(0.49f, 0.78f, 0.31f, 1f)),
    SEED_TREE(Plant.TREE, "Tree seed", new Color(0.18f, 0.47f, 0.24f, 1f)),
    SEED_FLOWER(Plant.FLOWER, "Flower seed", new Color(0.86f, 0.35f, 0.62f, 1f)),
    ROCK(null, "Move rock", new Color(0.50f, 0.50f, 0.53f, 1f));

    /** The seed this tool plants, or null for non-planting tools. */
    public final Plant seed;
    /** Human-readable name shown in the tooltip. */
    public final String label;
    public final Color swatch;

    Tool(Plant seed, String label, Color swatch) {
        this.seed = seed;
        this.label = label;
        this.swatch = swatch;
    }
}
