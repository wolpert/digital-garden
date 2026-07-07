package com.digitalgarden.terrarium.audio;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.FilterLowPass;
import com.jsyn.unitgen.LinearRamp;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.MultiplyAdd;
import com.jsyn.unitgen.PinkNoise;
import com.jsyn.unitgen.RedNoise;
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
    /** Ambient wind bed: low-passed pink noise with slow random gusts. */
    public static final String WIND = "wind";

    /** Every sound name the harness/UI can ask for, in build order. */
    public static final String[] ALL = { TEST_TONE, WIND };

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
            case WIND:
                return buildWind(synth).output;
            default:
                throw new IllegalArgumentException("Unknown sound: " + name);
        }
    }

    // --- wind (v1) -----------------------------------------------------------
    // Airy bed = pink noise (1/f, the natural colour of wind) through a low-pass,
    // with two slow, non-periodic RedNoise LFOs so it never sounds looped:
    //   • one wanders the filter cutoff  → the "whoosh" moving through the air
    //   • one swells the overall gain     → gusts rising and falling
    // All params are here so we can tweak them one at a time in the SOUND.md loop.
    private static final double WIND_CUTOFF_HZ   = 550;  // base low-pass cutoff
    private static final double WIND_CUTOFF_SWING = 300; // ± cutoff wander (250..850 Hz)
    private static final double WIND_CUTOFF_RATE = 0.35; // cutoff LFO speed (Hz)
    private static final double WIND_Q           = 0.6;  // gentle — no resonant whistle yet
    private static final double WIND_GUST_RATE   = 0.22; // gust LFO speed (Hz)
    private static final double WIND_GUST_BASE   = 0.60; // mean gust gain
    private static final double WIND_GUST_SWING  = 0.40; // ± gust depth (0.20..1.00)
    private static final double WIND_LEVEL       = 2.0;  // overall output scale
    private static final double WIND_INTENSITY_RAMP = 0.3; // s to glide to a new intensity

    /**
     * A live wind voice: its {@link #output} port plus a game-controllable
     * {@link #setIntensity} (0..1-ish) that scales the whole bed, glided smoothly on the
     * audio thread so turning the Wind dial doesn't click. At intensity 1.0 the output is
     * identical to the approved isolated render.
     */
    public static final class WindVoice {
        public final UnitOutputPort output;
        private final LinearRamp intensity;

        WindVoice(UnitOutputPort output, LinearRamp intensity) {
            this.output = output;
            this.intensity = intensity;
        }

        /** Sets the target intensity; the audio thread ramps toward it. */
        public void setIntensity(double v) {
            intensity.input.set(v);
        }
    }

    /** Builds the wind patch and returns a controllable handle. */
    public static WindVoice buildWind(Synthesizer synth) {
        PinkNoise pink = new PinkNoise();
        FilterLowPass lpf = new FilterLowPass();
        RedNoise cutoffLfo = new RedNoise();
        MultiplyAdd cutoffMod = new MultiplyAdd(); // cutoff = swing*lfo + base
        RedNoise gustLfo = new RedNoise();
        MultiplyAdd gustMod = new MultiplyAdd();   // gain = swing*lfo + base
        Multiply gusted = new Multiply();          // filtered * gustGain
        Multiply level = new Multiply();           // * overall level
        synth.add(pink); synth.add(lpf);
        synth.add(cutoffLfo); synth.add(cutoffMod);
        synth.add(gustLfo); synth.add(gustMod);
        synth.add(gusted); synth.add(level);

        pink.amplitude.set(1.0);
        lpf.Q.set(WIND_Q);

        // Cutoff wander: RedNoise (∈[-1,1]) -> swing*lfo + base -> filter frequency.
        cutoffLfo.frequency.set(WIND_CUTOFF_RATE);
        cutoffLfo.amplitude.set(1.0);
        cutoffMod.inputB.set(WIND_CUTOFF_SWING);
        cutoffMod.inputC.set(WIND_CUTOFF_HZ);
        cutoffLfo.output.connect(cutoffMod.inputA);
        pink.output.connect(lpf.input);
        cutoffMod.output.connect(lpf.frequency);

        // Gusts: RedNoise -> swing*lfo + base -> multiply the filtered signal.
        gustLfo.frequency.set(WIND_GUST_RATE);
        gustLfo.amplitude.set(1.0);
        gustMod.inputB.set(WIND_GUST_SWING);
        gustMod.inputC.set(WIND_GUST_BASE);
        gustLfo.output.connect(gustMod.inputA);
        lpf.output.connect(gusted.inputA);
        gustMod.output.connect(gusted.inputB);

        // Overall level.
        gusted.output.connect(level.inputA);
        level.inputB.set(WIND_LEVEL);

        // Game-controllable intensity, glided to avoid clicks. Defaults to 1.0 so the
        // offline harness renders the exact approved timbre.
        LinearRamp intensity = new LinearRamp();
        Multiply out = new Multiply();
        synth.add(intensity); synth.add(out);
        intensity.time.set(WIND_INTENSITY_RAMP);
        intensity.current.set(1.0);
        intensity.input.set(1.0);
        level.output.connect(out.inputA);
        intensity.output.connect(out.inputB);
        return new WindVoice(out.output, intensity);
    }
}
