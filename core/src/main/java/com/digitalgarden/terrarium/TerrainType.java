package com.digitalgarden.terrarium;

import com.badlogic.gdx.graphics.Color;

/** The landscape palette. Each terrain type carries its base color. */
public enum TerrainType {
    GRASS(0x7EC850), // light green
    TREES(0x2E783C), // dark green
    DIRT (0x8C603C), // brown
    SAND (0xDECA80), // yellow
    WATER(0x3678C8); // blue (base tint for deep water)

    public final Color color;

    TerrainType(int rgb) {
        // Color(int) reads RGBA8888, so shift the RGB up and set full alpha.
        this.color = new Color((rgb << 8) | 0xFF);
    }
}
