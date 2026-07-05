package com.digitalgarden.terrarium.wildlife;

/** One animal: a position in world pixels, a heading, and a little state. */
public class Critter {
    public final Species species;
    public float x, y;        // world pixels
    public float heading;     // radians
    public float energy = 0.7f;
    public float age;
    public float breedCooldown;
    public final float phase; // animation offset
    public float r, g, b;     // render tint (defaults to species color)
    public boolean alive = true;

    public Critter(Species species, float x, float y, float heading, float phase) {
        this.species = species;
        this.x = x;
        this.y = y;
        this.heading = heading;
        this.phase = phase;
        this.r = species.color.r;
        this.g = species.color.g;
        this.b = species.color.b;
    }
}
