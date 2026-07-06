package com.digitalgarden.terrarium.sim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Tile;
import com.digitalgarden.terrarium.World;

/**
 * Player-placed water sources and sinks. A <b>spring</b> adds water to its tile
 * every tick; the {@link FluidSystem} then carries it downhill, so a spring on
 * high ground becomes a persistent river/lake that outlasts evaporation. A
 * <b>drain</b> removes water. Sources are tracked in a small list so emission
 * is cheap regardless of world size.
 */
public class SpringSystem {
    private final World world;
    private final List<int[]> sources = new ArrayList<>();

    public SpringSystem(World world) {
        this.world = world;
    }

    /** Places or removes a source of {@code type} (1 spring, 2 drain) at a tile. */
    public void toggle(int x, int y, int type) {
        if (!world.inBounds(x, y)) return;
        Tile t = world.at(x, y);
        if (t.rock) return; // can't tap a rock
        if (t.spring == type) {          // same type -> remove
            t.spring = 0;
            removeAt(x, y);
        } else {
            if (t.spring == 0) sources.add(new int[]{x, y});
            t.spring = type;             // add, or switch spring<->drain in place
        }
    }

    /** Emits from springs and pulls from drains for one tick. */
    public void apply(float dt) {
        for (Iterator<int[]> it = sources.iterator(); it.hasNext();) {
            int[] s = it.next();
            Tile t = world.at(s[0], s[1]);
            if (t.spring == 1) {
                t.water += Config.SPRING_RATE * dt;
            } else if (t.spring == 2) {
                t.water = Math.max(0f, t.water - Config.DRAIN_RATE * dt);
            } else {
                it.remove(); // cleared elsewhere (e.g. a rock dropped on it)
            }
        }
    }

    private void removeAt(int x, int y) {
        for (Iterator<int[]> it = sources.iterator(); it.hasNext();) {
            int[] s = it.next();
            if (s[0] == x && s[1] == y) { it.remove(); return; }
        }
    }
}
