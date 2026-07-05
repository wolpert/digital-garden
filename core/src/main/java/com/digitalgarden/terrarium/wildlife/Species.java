package com.digitalgarden.terrarium.wildlife;

import com.badlogic.gdx.graphics.Color;

/**
 * The kinds of wildlife and their traits. Rabbits and foxes form a predator/prey
 * loop over the grass; birds, fish and butterflies are ambient life kept at a
 * target population. Speeds are in world pixels/second; size is in world pixels.
 */
public enum Species {
    RABBIT(Habitat.LAND, new Color(0.72f, 0.57f, 0.40f, 1f), 2.4f, 24f, 90, 22),
    FOX(Habitat.LAND, new Color(0.83f, 0.42f, 0.17f, 1f), 3f, 31f, 16, 6),
    BIRD(Habitat.AIR, new Color(0.20f, 0.20f, 0.25f, 1f), 2f, 46f, 30, 18),
    FISH(Habitat.WATER, new Color(0.72f, 0.80f, 0.88f, 1f), 2f, 18f, 30, 14),
    BUTTERFLY(Habitat.AIR, new Color(0.96f, 0.76f, 0.30f, 1f), 1.4f, 16f, 30, 18);

    public enum Habitat { LAND, WATER, AIR }

    public final Habitat habitat;
    public final Color color;
    public final float size;
    public final float speed;
    public final int maxPop;
    public final int initial;

    Species(Habitat habitat, Color color, float size, float speed, int maxPop, int initial) {
        this.habitat = habitat;
        this.color = color;
        this.size = size;
        this.speed = speed;
        this.maxPop = maxPop;
        this.initial = initial;
    }

    /** Ambient (non-ecology) species are refilled toward their cap. */
    public boolean isAmbient() {
        return this == BIRD || this == FISH || this == BUTTERFLY;
    }
}
