package com.digitalgarden.terrarium;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

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

    /** Audio. Discrete sound level 0..3 (off/soft/medium/loud), set by the config slider
     *  and read live by the audio bridge (which ramps toward {@link #soundVolume()} to
     *  avoid clicks). Persisted across runs via libGDX {@link Preferences}; defaults to
     *  {@link Config#SOUND_LEVEL_DEFAULT} the first time, when no saved value exists. */
    private static final String PREFS = "terrarium";
    private static final String KEY_SOUND_LEVEL = "soundLevel";

    public int soundLevel = loadSoundLevel();

    /** Master gain for the current level. */
    public float soundVolume() {
        return Config.SOUND_VOLUMES[soundLevel];
    }

    /** Sets and persists the sound level, clamped to the valid range. No-op if unchanged. */
    public void setSoundLevel(int level) {
        int l = Math.max(0, Math.min(Config.SOUND_LEVELS - 1, level));
        if (l == soundLevel) return;
        soundLevel = l;
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putInteger(KEY_SOUND_LEVEL, l);
            p.flush();
        } catch (Throwable ignored) {
            // No preferences backend (e.g. headless) — keep the in-memory value.
        }
    }

    private static int loadSoundLevel() {
        try {
            return Gdx.app.getPreferences(PREFS).getInteger(KEY_SOUND_LEVEL, Config.SOUND_LEVEL_DEFAULT);
        } catch (Throwable ignored) {
            return Config.SOUND_LEVEL_DEFAULT;
        }
    }
}
