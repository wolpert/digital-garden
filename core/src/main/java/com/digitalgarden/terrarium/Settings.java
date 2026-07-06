package com.digitalgarden.terrarium;

/**
 * Runtime-adjustable simulation parameters, surfaced by the dial panel. The
 * simulation systems read these live instead of the {@link Config} constants, so
 * turning a dial changes the world's behavior immediately. Values start at the
 * Config defaults ("1x").
 */
public class Settings {
    /** Weather. */
    public float rainRate = Config.RAIN_RATE;
    public float windSpeed = Config.WIND_SPEED;

    /** Fluid. */
    public float flowDamp = Config.FLOW_DAMP;
    public float evapBase = Config.EVAP_BASE;

    /** Growth. Multiplier on plant growth speed (1 = normal). */
    public float growthRate = 1f;

    /** Rot / die. */
    public float dryDecay = Config.DRY_DECAY;   // how fast dry plants wither
    public float floodKill = Config.FLOOD_KILL;  // water depth that rots a plant

    /** Audio. Master output level [0..1] and a global mute (toggled with M). Read live
     *  by the audio bridge, which ramps toward the effective gain to avoid clicks. Kept
     *  here (not just Config) so it can later get a dial, like the sim parameters. */
    public float masterVolume = Config.MASTER_VOLUME;
    public boolean audioMuted = false;
}
