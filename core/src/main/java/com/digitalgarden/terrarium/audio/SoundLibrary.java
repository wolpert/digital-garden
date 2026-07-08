package com.digitalgarden.terrarium.audio;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;
import com.jsyn.unitgen.FilterHighPass;
import com.jsyn.unitgen.FilterLowPass;
import com.jsyn.unitgen.FilterOnePole;
import com.jsyn.unitgen.FilterStateVariable;
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
    /** Ambient water: a smooth flow channel + a bubbling channel, mixed. */
    public static final String WATER = "water";
    /** Water sub-channels, renderable in isolation for tuning (not in {@link #ALL}). */
    public static final String WATER_FLOW = "water-flow";
    public static final String WATER_BUBBLE = "water-bubble";

    /** Every sound name the harness/UI can ask for, in build order. */
    public static final String[] ALL = { TEST_TONE, WIND, RAIN, WATER };

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
            case WATER:
                return buildWater(synth).output;
            case WATER_FLOW:
                return buildWaterFlow(synth);
            case WATER_BUBBLE:
                return buildWaterBubble(synth);
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

    // --- water: FLOW channel + BUBBLE channel, mixed -------------------------
    // Water is two independent channels, each renderable in isolation for tuning
    // ("water-flow" / "water-bubble") and summed by buildWater with its own level.
    //
    // FLOW (the smooth body, approved as "better"): reference-matched forest river — a
    // continuous, bright, band-limited noise rush amplitude-modulated by (a high floor + a
    // few gentle SMOOTHED swells). Matches the reference spectrum (centroid ~5 kHz, ~0% below
    // 250 Hz). Rejected flow dead-ends (don't repeat — see SOUND.md): sine-chirp drops ->
    // robot; resonant/continuous noise bloops -> wind/tent; sharp/varied noise-burst splashes
    // -> static.
    private static final double WATER_HP        = 300;   // cut lows to ~0% (kills the wind rumble)
    private static final double WATER_LP        = 8000;  // bright, but not sizzly/static up top
    private static final double WATER_FLOOR     = 0.45;  // the continuous smooth flow (a real bed)
    private static final double SPLASH_SPIKE_GAIN = 3000; // gain before the unit-impulse clamp
    private static final double SPLASH_ATTACK_POLE = 0.9985; // ~8 ms swell (not a sharp click)
    private static final double FLOW_CHANNEL_LEVEL = 0.7;
    private static final double[][] SPLASH_TRAINS = {
        { 0.9990, 0.9990 }, { 0.9992, 0.9992 }, { 0.9988, 0.9993 },
    };

    // BUBBLE channel (v1): sparse, pitch-varied "bloops". Each = a resonator excited by a short
    // NOISE burst (organic body, so not a pure-sine robot) whose pitch chirps UP as it rings
    // (the drop-into-a-pool signature), low-passed to stay round. Judged ALONE via "water-bubble".
    private static final double BUB_THRESHOLD   = 0.9990; // trigger rate (higher = sparser)
    private static final double BUB_SPIKE_GAIN  = 3000;
    private static final double BUB_BURST_DECAY = 0.985;  // ~1 ms noise burst that excites the ring
    private static final double BUB_RING_RES    = 0.90;   // resonance of the bloop (ring length)
    private static final double BUB_CHIRP_DECAY = 0.9990; // how fast the pitch-rise settles (~45 ms)
    private static final double BUB_TOP         = 1100;   // top pitch of the up-chirp (Hz)
    private static final double BUB_CHIRP       = 400;    // rise depth (< TOP-SWING so freq stays +)
    private static final double BUB_TOP_SWING   = 300;    // ± per-bloop pitch variation (800..1400)
    private static final double BUB_TOP_RATE    = 6;      // pitch-variation LFO (Hz)
    private static final double BUB_LP          = 3500;   // round off the top
    private static final double BUBBLE_CHANNEL_LEVEL = 0.8;

    /** The full water voice: flow + bubble channels, summed. */
    public static AmbientVoice buildWater(Synthesizer synth) {
        UnitOutputPort flow = buildWaterFlow(synth);
        UnitOutputPort bubble = buildWaterBubble(synth);
        Multiply mix = new Multiply();
        synth.add(mix);
        flow.connect(mix.inputA);
        bubble.connect(mix.inputA); // sums with the flow at one input port
        mix.inputB.set(1.0);
        return wrapIntensity(synth, mix.output);
    }

    /** FLOW channel: the smooth, bright, flowing river body. */
    public static UnitOutputPort buildWaterFlow(Synthesizer synth) {
        PinkNoise noise = new PinkNoise();
        FilterHighPass hp = new FilterHighPass();
        FilterLowPass lp = new FilterLowPass();
        synth.add(noise); synth.add(hp); synth.add(lp);
        noise.amplitude.set(1.0);
        hp.frequency.set(WATER_HP); hp.Q.set(0.7);
        lp.frequency.set(WATER_LP); lp.Q.set(0.7);
        noise.output.connect(hp.input);
        hp.output.connect(lp.input);

        Add envFloor = new Add();
        Minimum envClamp = new Minimum();
        synth.add(envFloor); synth.add(envClamp);
        for (double[] t : SPLASH_TRAINS) {
            splashTrain(synth, envFloor.inputA, t[0], t[1]);
        }
        envFloor.inputB.set(WATER_FLOOR);
        envClamp.inputB.set(1.0);
        envFloor.output.connect(envClamp.inputA);

        Multiply splash = new Multiply();
        Multiply level = new Multiply();
        synth.add(splash); synth.add(level);
        lp.output.connect(splash.inputA);
        envClamp.output.connect(splash.inputB);
        splash.output.connect(level.inputA);
        level.inputB.set(FLOW_CHANNEL_LEVEL);
        return level.output;
    }

    /** BUBBLE channel: sparse, pitch-varied bloops — a noise-burst-excited resonator whose
     *  pitch chirps up as it rings. */
    public static UnitOutputPort buildWaterBubble(Synthesizer synth) {
        // sparse random unit-impulse triggers ("dust")
        WhiteNoise trig = new WhiteNoise();
        Add sub = new Add();
        Maximum rect = new Maximum();
        Multiply spike = new Multiply();
        Minimum clamp = new Minimum();
        synth.add(trig); synth.add(sub); synth.add(rect); synth.add(spike); synth.add(clamp);
        trig.amplitude.set(1.0);
        sub.inputB.set(-BUB_THRESHOLD);
        trig.output.connect(sub.inputA);
        rect.inputB.set(0.0);
        sub.output.connect(rect.inputA);
        spike.inputB.set(BUB_SPIKE_GAIN);
        rect.output.connect(spike.inputA);
        clamp.inputB.set(1.0);
        spike.output.connect(clamp.inputA);

        // a short noise burst per trigger = the organic excitation for each bloop
        WhiteNoise burstN = new WhiteNoise();
        FilterOnePole burstEnv = new FilterOnePole();
        Multiply burst = new Multiply();
        synth.add(burstN); synth.add(burstEnv); synth.add(burst);
        burstN.amplitude.set(1.0);
        burstEnv.a0.set(1.0); burstEnv.b1.set(-BUB_BURST_DECAY);
        clamp.output.connect(burstEnv.input);
        burstN.output.connect(burst.inputA);
        burstEnv.output.connect(burst.inputB);

        // chirp envelope drives the rising pitch of the ring (clamped <=1 so freq stays positive)
        FilterOnePole chirpEnv = new FilterOnePole();
        Minimum chirpClamp = new Minimum();
        synth.add(chirpEnv); synth.add(chirpClamp);
        chirpEnv.a0.set(1.0); chirpEnv.b1.set(-BUB_CHIRP_DECAY);
        clamp.output.connect(chirpEnv.input);
        chirpClamp.inputB.set(1.0);
        chirpEnv.output.connect(chirpClamp.inputA);

        // per-bloop top pitch varies; freq = top - chirp*chirpEnv (low at onset -> rises to top)
        RedNoise topLfo = new RedNoise();
        MultiplyAdd topVar = new MultiplyAdd();
        MultiplyAdd freqMod = new MultiplyAdd();
        FilterStateVariable ring = new FilterStateVariable();
        FilterLowPass blipLp = new FilterLowPass();
        Multiply level = new Multiply();
        synth.add(topLfo); synth.add(topVar); synth.add(freqMod);
        synth.add(ring); synth.add(blipLp); synth.add(level);
        topLfo.frequency.set(BUB_TOP_RATE); topLfo.amplitude.set(1.0);
        topVar.inputB.set(BUB_TOP_SWING); topVar.inputC.set(BUB_TOP);
        topLfo.output.connect(topVar.inputA);
        freqMod.inputB.set(-BUB_CHIRP);
        chirpClamp.output.connect(freqMod.inputA);
        topVar.output.connect(freqMod.inputC);
        freqMod.output.connect(ring.frequency);
        ring.resonance.set(BUB_RING_RES); ring.amplitude.set(1.0);
        burst.output.connect(ring.input); // excite the resonance with the noise burst
        ring.bandPass.connect(blipLp.input);
        blipLp.frequency.set(BUB_LP); blipLp.Q.set(0.7);
        blipLp.output.connect(level.inputA);
        level.inputB.set(BUBBLE_CHANNEL_LEVEL);
        return level.output;
    }

    /** One train of gentle random swells (flow channel): unit-impulse "dust" -> a one-pole
     *  decay, with a slow low-pass onset so it swells (not clicks). Sums into {@code dest}. */
    private static void splashTrain(Synthesizer synth, UnitInputPort dest,
                                    double threshold, double decay) {
        WhiteNoise trig = new WhiteNoise();
        Add sub = new Add();
        Maximum rect = new Maximum();
        Multiply spike = new Multiply();
        Minimum clamp = new Minimum();
        FilterOnePole env = new FilterOnePole();   // exponential decay
        FilterOnePole swell = new FilterOnePole();  // smooth the onset into a swell (no click)
        synth.add(trig); synth.add(sub); synth.add(rect); synth.add(spike); synth.add(clamp);
        synth.add(env); synth.add(swell);
        trig.amplitude.set(1.0);
        sub.inputB.set(-threshold);
        trig.output.connect(sub.inputA);
        rect.inputB.set(0.0);
        sub.output.connect(rect.inputA);
        spike.inputB.set(SPLASH_SPIKE_GAIN);
        rect.output.connect(spike.inputA);
        clamp.inputB.set(1.0);
        spike.output.connect(clamp.inputA);
        env.a0.set(1.0); env.b1.set(-decay); // negative b1 => smooth exponential decay
        clamp.output.connect(env.input);
        swell.a0.set(1.0 - SPLASH_ATTACK_POLE); swell.b1.set(-SPLASH_ATTACK_POLE);
        env.output.connect(swell.input);
        swell.output.connect(dest);
    }
}
