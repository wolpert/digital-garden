# SOUND.md — Procedural audio plan

Plan for adding **procedurally generated sound** to Terrarium. Written to survive a
context reset: it captures the decisions, the integration technique, the sound design,
and — importantly — **how Claude can help validate the sound without being able to hear
it**. Read `CLAUDE.md` first for the overall architecture; this file is audio-specific.

## Goal / focus

A subtle, **reactive ambient soundscape** that mirrors the simulation, plus light event
SFX. This is a zen sandbox — sound should be **calm and non-intrusive**, never a loop
that grates. It reacts to the same live state the visuals do (weather, water, wildlife),
with a **master volume + mute** the player controls. Default master volume should be low.

Nothing here ships audio *files* — the sound is synthesized at runtime, matching the
"everything is procedural" ethos of the project.

## Why JSyn (decision + rationale)

We evaluated the JVM audio options against three hard constraints: **must be MIT-license
compatible**, **must run on desktop AND Android**, and **must integrate with libGDX**.

**Chosen: [JSyn](https://github.com/philburk/jsyn)** — a modular software synthesizer
(unit generators: oscillators, filters, envelopes, reverb/effects, FM).

- **License: Apache 2.0** — permissive, compatible with this project's MIT license.
- **Maintained** — active releases by the original author (latest **v17.2.0, Jun 2025**;
  v17.1.0 Apr 2025). Not abandoned.
- **Cross-platform incl. Android** — JSyn does its own output via **JavaSound on desktop
  and Android's native `AudioTrack`** (it has an `android` module; v17.2.0 notes mention
  AudioTrack improvements). This sidesteps the `javax.sound`-doesn't-exist-on-Android
  problem that disqualifies the alternatives.
- **Modular** — gives us reverb/effects/filters for free instead of hand-rolling DSP.

**Rejected alternatives:**
- **Beads / Minim** — desktop-only (JavaSound I/O), no Android.
- **TarsosDSP** — GPLv3, would impose copyleft on our MIT app. Avoid.
- **Hand-rolled DSP** — viable (~150 lines: pink noise + one-pole filter + envelopes +
  chirp osc), zero deps, but no reverb/effects and more to maintain. JSyn wins for the
  modular synth + effects; keep hand-rolled as the fallback if JSyn integration fights us.

**Dependency (verify current version at implementation time):** JSyn via JitPack —
`com.github.philburk:jsyn:v17.2.0`, and add `maven { url = uri('https://jitpack.io') }`
to the `repositories` block in the root `build.gradle`. The old Clojars artifact
`com.jsyn:jsyn:20170815` is stale — don't use it. Confirm the **Android** artifact/setup
JSyn needs before wiring the android module.

## Integration technique

**Chosen: bridge JSyn's engine into libGDX's `AudioDevice` (unified output path).**

Run the JSyn `Synthesizer` in **non-real-time / pull mode**, read generated sample frames,
and `writeSamples(...)` them into `Gdx.audio.newAudioDevice(44100, /*stereo*/ true)` on a
**dedicated daemon thread**. This gives one audio lifecycle, one master volume/mute, and
one place to handle desktop+Android uniformly through libGDX.

- **Fallback:** let JSyn own its output (its `AudioDeviceManager` picks JavaSound on
  desktop / AudioTrack on Android). Less glue, but a parallel audio path outside libGDX.
  Fine for fast prototyping.

**Where it lives — mirror the `ParticleSystem` pattern** (see how particles are wired):
- New `core/src/main/java/com/digitalgarden/terrarium/audio/AudioSystem.java`.
- Constructed in `Terrarium.create()`; `dispose()` stops the thread and disposes the
  device; hook `Terrarium.pause()`/`resume()` to stop/restart audio on Android.
- `update(world, weather, settings)` called once per frame from `Terrarium.update` sets
  **ambient target parameters** (write `volatile`/`Atomic*` fields; the audio thread reads
  them and **ramps** toward them to avoid zipper noise).
- `emit*(...)` one-shot methods called from the **same event hooks the particles use**:
  - `InputController.pour(...)` → splash; `InputController.dropRock(...)` → thud.
  - `GrowthSystem` maturity (where `particles.sprout(...)` is called) → sprout blip.
  - `WildlifeSystem` → optional critter chirps.
  SFX triggers go through a **lock-free queue** the audio thread drains.
- **Master gain + mute** in `Settings` (so it could later get a dial); a mute key (e.g. `M`).

**Threading rules:** `writeSamples` blocks — never touch it from the render thread. Game
thread only writes params / pushes triggers; audio thread does all synthesis. Ramp all
gains. Soft-clip the master sum; keep headroom.

## Sound design (the voices)

**Ambient bed — continuous, parameter-driven:**
| Voice | Synthesis sketch | Driven by |
|-------|------------------|-----------|
| Wind | pink/low-passed noise; LFO on cutoff + gain → gusts | `settings.windSpeed` |
| Rain | brighter band-passed noise; sparse droplet transients when heavy | `weather.stormLevel()` |
| Water | soft bubbling (filtered noise + slow pitch-mod blips) | on-screen / near-camera water amount |
| Birds | chirp phrases (pitch-swept osc + AR env), sparser when stormy | clear weather (low storm) |

**Event SFX — one-shots, panned toward where they happened on screen:**
- Pour water → short filtered-noise splash.
- Rock drop → low sine/triangle thud + tiny click.
- Plant matures → quick upward pitch blip.
- (optional) tool/critter blips.

Everything **subtle** and **ramped**; a light reverb send ties it together.

## Parameter mapping (game → audio)

Read live each frame, exactly like the dials read `Settings`:
- `weather.stormLevel()` → rain gain (↑), bird activity (↓).
- `settings.windSpeed` → wind gain + gust rate.
- fraction of visible water tiles → water bubbling gain.
- overall calm (clear + little wind) → more birdsong.

## How to validate the sound (Claude can't hear — this is the workflow)

Claude has **no audio playback** in its environment, so quality is validated by a mix of
**objective analysis Claude runs** + **the human listening**. Design every audio change to
be renderable **offline and deterministically** so it can be inspected and A/B'd.

**1. Headless render harness.** Add a dev entry point (a `main`, e.g.
`lwjgl3/.../AudioRenderMain`, or a Gradle task) that renders **deterministic WAV files**
(fixed RNG seed, JSyn **non-real-time** mode → `WaveRecorder`/`WaveFileWriter`, or render
`AudioSystem` offline) into `build/audio/`:
- ambient presets: `calm`, `windy`, `light-rain`, `storm`, `dawn-birds` (each a few seconds).
- each SFX: `splash`, `thud`, `sprout`, `chirp` (a few pitch/pan variants).

**2. Objective checks Claude runs on each WAV (no ears needed).** Flag problems automatically:
- **Clipping** — samples at/above ~0.99 (should be none; keep headroom).
- **Clicks/discontinuities** — max `|sample[i] - sample[i-1]|` above a threshold.
- **Level** — peak + RMS/loudness in a sane range; not silent, not blasting.
- **DC offset** — mean near 0.
- **Spectral sanity** — FFT energy by band: wind/rain weighted low-mid, chirps sweep
  upward, no harsh high-frequency spikes; storm brighter than calm.

**3. Visual check.** Generate **waveform + spectrogram PNGs** (small python/numpy or
sox/ffmpeg script) that Claude can open and view to confirm envelope shape, gusting,
chirp sweeps, and absence of clipping — the same screenshot-inspection loop used for the
visuals.

**4. Human listen + tune (the real quality gate).** The user plays the WAVs (and runs the
app) and gives qualitative notes; Claude maps notes → DSP parameters and re-renders:

| Symptom the user reports | Likely knob to turn |
|---|---|
| Harsh / hissy | lower filter cutoff; reduce high-band gain; more low-pass |
| Too quiet / too loud | voice gain / master gain |
| Clicky / pops | lengthen envelope attack/release; ramp gains; fix buffer seams |
| Not "gusty" enough | wind LFO rate/depth |
| Rain not intense in storms | steepen `stormLevel → rain gain` curve; more droplets |
| Muddy / boomy | high-pass the low end; reduce reverb; trim water/thud lows |
| Robotic birds | randomize chirp pitch/timing; vary phrase length |

**5. In-app check.** `./gradlew :lwjgl3:run` to hear it react to real weather; later,
test on an Android device (AudioTrack latency + audio focus).

## Build order (incremental slices)

1. **Pipeline proof** — add JSyn dep + JitPack repo; `AudioSystem` skeleton + AudioDevice
   bridge thread + master gain/mute; a single **test tone**. Confirm it plays on **desktop
   and Android**. Don't build voices until output works on both.
2. **Render harness + checks** — offline WAV render + the objective checks + waveform/
   spectrogram PNGs. This is the validation backbone; build it early.
3. **Ambient bed** — wind → rain → water, tied to `weather`/`settings`. Render presets, run
   checks, user listens, tune.
4. **Event SFX** — via the existing emit hooks; pan by screen position.
5. **Birds + polish** — chirp grammar, light reverb glue; master volume/mute in `Settings`
   (maybe a dial); Android device pass.

## Gotchas / constraints (don't relearn these the hard way)

- `writeSamples` **blocks** → daemon thread only; `dispose()` must stop it and dispose the
  device; stop audio on `Terrarium.pause()` (Android backgrounding).
- **No zipper noise**: ramp every gain change. **No clicks**: window/envelope every voice,
  keep buffer seams continuous.
- **Headroom**: sum voices then soft-clip; start conservative.
- **Determinism** in the render harness (fixed seed) so re-renders are comparable A/B.
- **Android**: verify JSyn's android setup; higher latency; respect audio focus.
- **Keep it subtle** — default master low; this is a calm game, not an arcade.

## Starter prompt (hand this to a fresh Claude)

> Add procedural audio to Terrarium using **JSyn** (Apache-2.0; JitPack
> `com.github.philburk:jsyn`, add the jitpack repo). Build slice 1 only: an `AudioSystem`
> in `core/.../audio/` (mirroring `ParticleSystem`) that opens `Gdx.audio.newAudioDevice`
> on a daemon thread, runs a JSyn synth in pull mode bridged into it, plays a quiet test
> tone, and has master gain + mute; wire it into `Terrarium` create/update/dispose/pause.
> Then add the **offline render harness** that writes deterministic WAVs to `build/audio/`
> plus objective checks (clip/click/level/DC/spectral) and waveform+spectrogram PNGs, so we
> can validate sound without playback. Confirm it builds and runs on desktop; note the
> Android steps. See SOUND.md for the full plan, voices, parameter mapping, and the
> validation loop.
