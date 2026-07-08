package com.digitalgarden.terrarium.audio;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;
import com.jsyn.unitgen.FilterHighPass;
import com.jsyn.unitgen.FilterLowPass;
import com.jsyn.unitgen.FilterOnePole;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.ImpulseOscillator;
import com.jsyn.unitgen.LinearRamp;
import com.jsyn.unitgen.Maximum;
import com.jsyn.unitgen.Minimum;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.MultiplyAdd;
import com.jsyn.unitgen.PinkNoise;
import com.jsyn.unitgen.RedNoise;
import com.jsyn.unitgen.SineOscillator;
import com.jsyn.unitgen.WhiteNoise;
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
    /** Ambient rain bed: band-limited bright noise, steadier than wind. */
    public static final String RAIN = "rain";
    /** Event SFX: a short filtered-noise splash when water is poured. */
    public static final String SPLASH = "splash";

    /** Every sound name the harness/UI can ask for, in build order. */
    public static final String[] ALL = { TEST_TONE, WIND, RAIN };

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
            case RAIN:
                return buildRain(synth).output;
            case SPLASH:
                return buildSplash(synth);
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
    private static final double AMBIENT_INTENSITY_RAMP = 0.3; // s to glide to a new intensity

    /**
     * A live ambient voice: its {@link #output} port plus a game-controllable
     * {@link #setIntensity} (0..1-ish) that scales the whole bed, glided smoothly on the
     * audio thread so a state change (Wind dial, storm level) doesn't click. At intensity
     * 1.0 the output is identical to the approved isolated render.
     */
    public static final class AmbientVoice {
        public final UnitOutputPort output;
        private final LinearRamp intensity;

        AmbientVoice(UnitOutputPort output, LinearRamp intensity) {
            this.output = output;
            this.intensity = intensity;
        }

        /** Sets the target intensity; the audio thread ramps toward it. */
        public void setIntensity(double v) {
            intensity.input.set(v);
        }
    }

    /** Wraps a dry voice output in a click-free, game-controllable intensity stage. Defaults
     *  to intensity 1.0 so the offline harness renders the exact approved timbre. */
    private static AmbientVoice wrapIntensity(Synthesizer synth, UnitOutputPort dry) {
        LinearRamp intensity = new LinearRamp();
        Multiply out = new Multiply();
        synth.add(intensity); synth.add(out);
        intensity.time.set(AMBIENT_INTENSITY_RAMP);
        intensity.current.set(1.0);
        intensity.input.set(1.0);
        dry.connect(out.inputA);
        intensity.output.connect(out.inputB);
        return new AmbientVoice(out.output, intensity);
    }

    /** Builds the wind patch and returns a controllable handle. */
    public static AmbientVoice buildWind(Synthesizer synth) {
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

        // Overall level, then the game-controllable intensity stage.
        gusted.output.connect(level.inputA);
        level.inputB.set(WIND_LEVEL);
        return wrapIntensity(synth, level.output);
    }

    // --- rain (v2) -----------------------------------------------------------
    // Rain = a soft, dark "sheet" bed + a layer of DROPLETS (the patter), which is what
    // makes it read as rain instead of wind/hiss.
    //   Bed: pink noise band-limited low (dark, quiet) with gentle fluctuation.
    //   Droplets: a "dust" of sparse, randomly-timed impulses — white noise thresholded
    //     just below its peak, so only rare spikes get through — fed into a resonant
    //     state-variable filter so each spike rings as a brief pitched tick. The tick
    //     pitch wanders (slow RedNoise on the filter frequency) so no two drops match.
    private static final double RAIN_HP_HZ      = 300;   // bed high-pass: cut low rumble
    private static final double RAIN_LP_HZ      = 1600;  // bed low-pass: a low wash, not a mid hiss
    private static final double RAIN_Q          = 0.7;   // gentle, non-resonant bed
    private static final double RAIN_FLUX_RATE  = 0.5;   // bed fluctuation LFO (Hz)
    private static final double RAIN_FLUX_BASE  = 0.85;  // bed mean level
    private static final double RAIN_FLUX_SWING = 0.12;  // ± bed fluctuation
    private static final double RAIN_BED_LEVEL  = 0.45;  // bed output scale (soft backdrop)

    private static final double DROP_THRESHOLD  = 0.9994; // white-noise level a spike must exceed
    private static final double DROP_SPIKE_GAIN = 3000;   // huge, so any over-threshold sample...
    private static final double DROP_IMPULSE_CAP = 1.0;   // ...clamps to a consistent unit impulse
    private static final double DROP_RESONANCE  = 0.94;   // high = a tonal, ringing "plink" (not a click)
    private static final double DROP_FREQ_BASE  = 1500;   // plink pitch centre (Hz)
    private static final double DROP_FREQ_SWING = 700;    // ± plink-pitch wander (800..2200 Hz)
    private static final double DROP_FREQ_RATE  = 8;      // how fast the plink pitch varies (Hz)
    private static final double DROP_LP_HZ      = 2200;   // strip the staticky high spray off each impact
    private static final double DROP_LEVEL      = 3.0;    // droplet layer scale (drops sit on top)
    private static final double RAIN_LEVEL      = 2.0;    // overall (bed + drops) scale

    public static AmbientVoice buildRain(Synthesizer synth) {
        // --- bed ---
        PinkNoise pink = new PinkNoise();
        FilterHighPass hp = new FilterHighPass();
        FilterLowPass lp = new FilterLowPass();
        RedNoise fluxLfo = new RedNoise();
        MultiplyAdd fluxMod = new MultiplyAdd();
        Multiply fluxed = new Multiply();
        Multiply bed = new Multiply();
        synth.add(pink); synth.add(hp); synth.add(lp);
        synth.add(fluxLfo); synth.add(fluxMod); synth.add(fluxed); synth.add(bed);

        pink.amplitude.set(1.0);
        hp.frequency.set(RAIN_HP_HZ); hp.Q.set(RAIN_Q);
        lp.frequency.set(RAIN_LP_HZ); lp.Q.set(RAIN_Q);
        pink.output.connect(hp.input);
        hp.output.connect(lp.input);
        fluxLfo.frequency.set(RAIN_FLUX_RATE);
        fluxLfo.amplitude.set(1.0);
        fluxMod.inputB.set(RAIN_FLUX_SWING);
        fluxMod.inputC.set(RAIN_FLUX_BASE);
        fluxLfo.output.connect(fluxMod.inputA);
        lp.output.connect(fluxed.inputA);
        fluxMod.output.connect(fluxed.inputB);
        fluxed.output.connect(bed.inputA);
        bed.inputB.set(RAIN_BED_LEVEL);

        // --- droplets ---
        WhiteNoise trig = new WhiteNoise();
        Add sub = new Add();           // trig - threshold
        Maximum rect = new Maximum();   // max(0, ...) -> sparse tiny positive spikes
        Multiply spike = new Multiply(); // blow them up...
        Minimum clamp = new Minimum();   // ...then clamp to a consistent unit impulse
        RedNoise pitchLfo = new RedNoise();
        MultiplyAdd pitchMod = new MultiplyAdd();
        FilterStateVariable ring = new FilterStateVariable();
        FilterLowPass dropLp = new FilterLowPass();
        Multiply drops = new Multiply();
        synth.add(trig); synth.add(sub); synth.add(rect); synth.add(spike); synth.add(clamp);
        synth.add(pitchLfo); synth.add(pitchMod); synth.add(ring); synth.add(dropLp); synth.add(drops);

        trig.amplitude.set(1.0);
        sub.inputB.set(-DROP_THRESHOLD);
        trig.output.connect(sub.inputA);
        rect.inputB.set(0.0);
        sub.output.connect(rect.inputA);
        spike.inputB.set(DROP_SPIKE_GAIN);
        rect.output.connect(spike.inputA);
        clamp.inputB.set(DROP_IMPULSE_CAP);
        spike.output.connect(clamp.inputA);
        // wandering tick pitch
        pitchLfo.frequency.set(DROP_FREQ_RATE);
        pitchLfo.amplitude.set(1.0);
        pitchMod.inputB.set(DROP_FREQ_SWING);
        pitchMod.inputC.set(DROP_FREQ_BASE);
        pitchLfo.output.connect(pitchMod.inputA);
        pitchMod.output.connect(ring.frequency);
        ring.resonance.set(DROP_RESONANCE);
        ring.amplitude.set(1.0);
        clamp.output.connect(ring.input);
        dropLp.frequency.set(DROP_LP_HZ);
        dropLp.Q.set(0.7);
        ring.bandPass.connect(dropLp.input);
        dropLp.output.connect(drops.inputA);
        drops.inputB.set(DROP_LEVEL);

        // --- mix bed + droplets, then overall level ---
        Add mix = new Add();
        Multiply level = new Multiply();
        synth.add(mix); synth.add(level);
        bed.output.connect(mix.inputA);
        drops.output.connect(mix.inputB);
        mix.output.connect(level.inputA);
        level.inputB.set(RAIN_LEVEL);
        return wrapIntensity(synth, level.output);
    }

    // --- pour splash (v1) ----------------------------------------------------
    // A short "pshh": a band-passed white-noise burst, bright at the impact then dulling, with
    // a quick decay. It's an event one-shot; for the isolation render it self-triggers on a
    // slow ImpulseOscillator so we hear several. The live game will fire the same patch from
    // pour events instead (that trigger wiring comes after the timbre is approved).
    // Reference-matched (a real splash SFX, see SOUND.md): each splash is a sharp broadband
    // IMPACT + a ~0.6 s decaying FIZZ tail (the water settling — droplets/bubbles). Broadband,
    // NOT tonal (a pitched bloop reads as percussion). The long fizzy tail is what says "water".
    private static final double SPLASH_DEMO_RATE  = 1.2;    // demo: splashes/sec (live uses events)
    private static final double IMPACT_DECAY      = 0.9996; // ~55 ms bright hit (applied as -b1)
    private static final double IMPACT_ATTACK     = 0.90;   // ~2 ms onset — sharp but not a click
    private static final double IMPACT_HP         = 700;    // impact band...
    private static final double IMPACT_LP         = 5000;   // ...tamed on top (not sizzly)
    private static final double IMPACT_LEVEL      = 0.7;
    private static final double TAIL_DECAY        = 0.99996; // ~560 ms settling tail
    private static final double TAIL_HP           = 400;    // tail band
    private static final double TAIL_LP           = 4500;
    private static final double FIZZ_RATE         = 35;     // fast wobble = the fizz/bubbling
    private static final double FIZZ_BASE         = 0.55;   // mean fizz gain
    private static final double FIZZ_SWING        = 0.45;   // ± fizz depth (0.10..1.00)
    private static final double TAIL_LEVEL        = 0.6;
    private static final double SPLASH_LEVEL      = 3.0;    // overall output scale

    /** Builds a pour splash driven by a slow periodic trigger (for isolation rendering). */
    public static UnitOutputPort buildSplash(Synthesizer synth) {
        ImpulseOscillator trig = new ImpulseOscillator(); // DEMO trigger; live uses pour events
        synth.add(trig);
        trig.frequency.set(SPLASH_DEMO_RATE);
        trig.amplitude.set(1.0);
        return splashVoice(synth, trig.output);
    }

    /** The splash patch itself, gated by a {@code trigger} impulse (reused live + in the demo). */
    private static UnitOutputPort splashVoice(Synthesizer synth, UnitOutputPort trigger) {
        // --- impact envelope (fast, soft-onset) and tail envelope (slow settle) ---
        FilterOnePole impactEnv = new FilterOnePole();
        FilterOnePole impactAtk = new FilterOnePole();
        Minimum impactClamp = new Minimum();
        FilterOnePole tailEnv = new FilterOnePole();
        Minimum tailClamp = new Minimum();
        synth.add(impactEnv); synth.add(impactAtk); synth.add(impactClamp);
        synth.add(tailEnv); synth.add(tailClamp);
        impactEnv.a0.set(1.0); impactEnv.b1.set(-IMPACT_DECAY);
        trigger.connect(impactEnv.input);
        impactAtk.a0.set(1.0 - IMPACT_ATTACK); impactAtk.b1.set(-IMPACT_ATTACK);
        impactEnv.output.connect(impactAtk.input);
        impactClamp.inputB.set(1.0);
        impactAtk.output.connect(impactClamp.inputA);
        tailEnv.a0.set(1.0); tailEnv.b1.set(-TAIL_DECAY);
        trigger.connect(tailEnv.input);
        tailClamp.inputB.set(1.0);
        tailEnv.output.connect(tailClamp.inputA);

        // --- IMPACT: a broadband burst (pink noise = flatter/less sizzly than white) ---
        PinkNoise impactN = new PinkNoise();
        FilterHighPass impactHp = new FilterHighPass();
        FilterLowPass impactLp = new FilterLowPass();
        Multiply impact = new Multiply();
        Multiply impactGain = new Multiply();
        synth.add(impactN); synth.add(impactHp); synth.add(impactLp); synth.add(impact); synth.add(impactGain);
        impactN.amplitude.set(1.0);
        impactHp.frequency.set(IMPACT_HP); impactHp.Q.set(0.7);
        impactLp.frequency.set(IMPACT_LP); impactLp.Q.set(0.7);
        impactN.output.connect(impactHp.input);
        impactHp.output.connect(impactLp.input);
        impactLp.output.connect(impact.inputA);
        impactClamp.output.connect(impact.inputB);
        impact.output.connect(impactGain.inputA);
        impactGain.inputB.set(IMPACT_LEVEL);

        // --- TAIL: band-limited noise, fast fizz modulation, slow decay = water settling ---
        PinkNoise tailN = new PinkNoise();
        FilterHighPass tailHp = new FilterHighPass();
        FilterLowPass tailLp = new FilterLowPass();
        RedNoise fizzLfo = new RedNoise();
        MultiplyAdd fizzMod = new MultiplyAdd(); // fizz gain = swing*lfo + base
        Multiply fizzed = new Multiply();
        Multiply tail = new Multiply();
        Multiply tailGain = new Multiply();
        synth.add(tailN); synth.add(tailHp); synth.add(tailLp);
        synth.add(fizzLfo); synth.add(fizzMod); synth.add(fizzed); synth.add(tail); synth.add(tailGain);
        tailN.amplitude.set(1.0);
        tailHp.frequency.set(TAIL_HP); tailHp.Q.set(0.7);
        tailLp.frequency.set(TAIL_LP); tailLp.Q.set(0.7);
        tailN.output.connect(tailHp.input);
        tailHp.output.connect(tailLp.input);
        fizzLfo.frequency.set(FIZZ_RATE); fizzLfo.amplitude.set(1.0);
        fizzMod.inputB.set(FIZZ_SWING); fizzMod.inputC.set(FIZZ_BASE);
        fizzLfo.output.connect(fizzMod.inputA);
        tailLp.output.connect(fizzed.inputA);
        fizzMod.output.connect(fizzed.inputB);
        fizzed.output.connect(tail.inputA);
        tailClamp.output.connect(tail.inputB);
        tail.output.connect(tailGain.inputA);
        tailGain.inputB.set(TAIL_LEVEL);

        // --- mix impact + tail, overall level ---
        Multiply level = new Multiply();
        synth.add(level);
        impactGain.output.connect(level.inputA);
        tailGain.output.connect(level.inputA); // sums with the impact
        level.inputB.set(SPLASH_LEVEL);
        return level.output;
    }
}
