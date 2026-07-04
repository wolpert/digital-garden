package com.digitalgarden.terrarium;

import com.digitalgarden.terrarium.util.Noise;

/**
 * The tile grid plus procedural generation. An elevation heightmap drives
 * everything: low ground collects water, shorelines become sand, and higher,
 * wetter ground grows grass and trees.
 */
public class World {
    public final int w = Config.GRID_W;
    public final int h = Config.GRID_H;
    public final Tile[] tiles = new Tile[w * h];

    public World(long seed) {
        generate(seed);
    }

    public Tile at(int x, int y) {
        return tiles[y * w + x];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    private void generate(long seed) {
        Noise elev = new Noise(seed);
        Noise moist = new Noise(seed * 31 + 7);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = new Tile();
                float nx = (float) x / w;
                float ny = (float) y / h;

                float e = clamp01(elev.fbm(nx * 4f, ny * 4f, 5, 0.5f, 2f));
                float m = clamp01(moist.fbm(nx * 3f + 100f, ny * 3f + 100f, 4, 0.55f, 2f));
                t.elevation = e;
                t.moisture = m;

                if (e < Config.SEA_LEVEL) {
                    // Low ground: standing water. Store its depth for the fluid sim.
                    t.water = Config.SEA_LEVEL - e;
                    t.terrain = (e > Config.SEA_LEVEL - 0.05f) ? TerrainType.SAND : TerrainType.DIRT;
                    t.moisture = 1f;
                } else if (e < Config.SEA_LEVEL + 0.04f) {
                    t.terrain = TerrainType.SAND; // beach ring around water
                    t.moisture = Math.max(m, 0.5f);
                } else if (m > 0.62f && e > 0.5f) {
                    t.terrain = TerrainType.TREES;
                } else if (m > 0.42f) {
                    t.terrain = TerrainType.GRASS;
                } else {
                    t.terrain = TerrainType.DIRT;
                }

                tiles[y * w + x] = t;
            }
        }

        scatterRocks(new Noise(seed * 7 + 3));
    }

    private void scatterRocks(Noise n) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = at(x, y);
                if (t.water > Config.WATER_RENDER_THRESHOLD) continue;
                if (n.value(x * 0.9f + 0.5f, y * 0.9f + 0.5f) > 0.93f) {
                    t.rock = true;
                }
            }
        }
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
