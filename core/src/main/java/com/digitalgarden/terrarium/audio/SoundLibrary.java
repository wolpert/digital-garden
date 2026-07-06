package com.digitalgarden.terrarium.audio;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.SineOscillator;
import com.digitalgarden.terrarium.Config;

/**
 * The catalogue of procedural sounds, built as JSyn unit-generator patches. One place
 * defines each voice so the <em>live</em> {@link AudioSystem} and the <em>offline</em>
 * render harness produce byte-identical audio — the harness lets us judge a sound in
 * isolation, then the same patch plays in the game (see SOUND.md).
 *
 * <p>Each entry adds its units to the supplied {@link Synthesizer} and returns the mono
 * output port to route (the caller fans it out to stereo / a reverb send). Voices take a
 * {@code seed} so any stochastic voice can be made deterministic for A/B re-renders;
 * the current infra voice (a pure test tone) ignores it.
 *
 * <p>Right now only the {@link #TEST_TONE} exists — it exists to prove the output path.
 * Real voices (wind, rain, water, splashes…) get added here one at a time, each taken
 * through the per-sound iteration loop before the next is started.
 */
public final class SoundLibrary {
    private SoundLibrary() {}

    /** Infra proof-of-life voice: a quiet steady sine. */
    public static final String TEST_TONE = "test-tone";

    /** Every sound name the harness/UI can ask for, in build order. */
    public static final String[] ALL = { TEST_TONE };

    /**
     * Builds the named voice into {@code synth} and returns its output port.
     *
     * @param synth the synthesizer to add units to (already created, not necessarily started)
     * @param name  one of the constants above
     * @param seed  RNG seed for stochastic voices (ignored by deterministic ones)
     * @return the voice's mono output port, ready to connect downstream
     * @throws IllegalArgumentException if {@code name} is unknown
     */
    public static UnitOutputPort build(Synthesizer synth, String name, long seed) {
        switch (name) {
            case TEST_TONE: {
                SineOscillator osc = new SineOscillator(Config.TEST_TONE_HZ, Config.TEST_TONE_AMP);
                synth.add(osc);
                return osc.output;
            }
            default:
                throw new IllegalArgumentException("Unknown sound: " + name);
        }
    }
}
