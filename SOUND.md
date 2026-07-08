# SOUND.md — Procedural audio plan

Plan for adding **procedurally generated sound** to Terrarium. Written to survive a
context reset: it captures the decisions, the integration technique, the sound design,
and — importantly — **the collaborative loop we use to create each sound together**:
Claude makes a change and renders it, the user listens, and we adjust until it's right —
**one sound at a time**. Read `CLAUDE.md` first for the overall architecture; this file is
audio-specific.

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

## How we build the sounds — together, one at a time

**Core working method: we co-create each sound in a tight Claude↔user loop, and we do it
for every sound independently.** Pick ONE sound, iterate until the user is happy with it,
**lock it**, then move to the next. Do **not** batch-build all the voices and tune them
later — a single voice judged in isolation is far easier to get right, and mixing several
half-baked sounds muddies the feedback.

Claude has **no audio playback** of its own, so the user's ears are the quality gate;
Claude's job is to make the change, render it, catch objective problems, and translate the
user's plain-language feedback into parameter changes.

### The iteration loop (repeat per sound until approved)

1. **Focus one sound.** State which single sound we're working on (e.g. "wind"). Mute /
   exclude the others so nothing else colors the judgment.
2. **Claude changes it** — implement or tweak just that voice's JSyn patch + parameters.
3. **Claude renders it in isolation** to a deterministic WAV in `build/audio/<sound>.wav`
   (fixed RNG seed, JSyn non-real-time mode), generates a **waveform + spectrogram PNG**,
   and runs the **objective checks** (below). Auto-fail on clipping/clicks before the user
   ever listens.
4. **Claude reports back**, concisely: the WAV path to play, a one-line note on *what
   changed and why*, the check results, and (if useful) what to listen for.
5. **User listens** and replies in plain language — "too harsh", "not gusty enough",
   "clicks at the start", "make it shorter", "brighter in a storm", or "that's it, lock it".
6. **Claude translates → params** (see symptom→knob table) and re-renders. Go to 3.
7. On **"approve"**, record the final parameters as the locked settings for that sound and
   move to the next one. Keep a short changelog per sound so we can A/B and revert.

Only after the individual sounds are each approved do we do a **mix/balance pass** —
combine them, set relative levels + reverb, wire to live game state, and listen to the
whole bed together (that pass is its own iteration loop).

### The tooling that supports the loop

- **Render harness** — a dev entry point (a `main`, e.g. `lwjgl3/.../AudioRenderMain`, or a
  Gradle task) that renders a **single named sound in isolation** to a deterministic WAV
  (fixed seed → JSyn non-real-time `WaveRecorder`/`WaveFileWriter`). Must be able to render
  just the sound under focus, not the whole mix. Ambient voices render across a few state
  values (e.g. wind at low/med/high) so we can hear the reaction; SFX render a few variants.
- **Objective checks Claude runs on each WAV (no ears needed):**
  - **Clipping** — any sample ≥ ~0.99 (should be none; keep headroom).
  - **Clicks/discontinuities** — max `|sample[i] - sample[i-1]|` over a threshold.
  - **Level** — peak + RMS in a sane range; not silent, not blasting.
  - **DC offset** — mean near 0.
  - **Spectral sanity** — FFT energy by band (wind/rain low-mid weighted, chirps sweep up,
    no harsh highs; storm brighter than calm).
- **Visual check** — waveform + spectrogram PNGs (small python/numpy or sox/ffmpeg script)
  that Claude opens and views to confirm envelope shape, gusting, sweeps, no clipping — the
  same screenshot-inspection loop used for the visuals.

### Symptom → knob (Claude's translation aid for step 6)

| User says | Likely knob to turn |
|---|---|
| Harsh / hissy | lower filter cutoff; reduce high-band gain; more low-pass |
| Too quiet / too loud | that voice's gain |
| Clicky / pops | lengthen envelope attack/release; ramp gains; fix buffer seams |
| Not "gusty" enough | wind LFO rate/depth |
| Rain not intense in storms | steepen `stormLevel → rain gain` curve; more droplets |
| Muddy / boomy | high-pass the low end; reduce reverb; trim lows |
| Robotic / repetitive birds | randomize chirp pitch/timing; vary phrase length |

### In-app check (after a sound is approved)

`./gradlew :lwjgl3:run` to hear the approved sound react to real weather in context; later,
test on an Android device (AudioTrack latency + audio focus).

## Build order

Two infrastructure slices first, then **one sound at a time**, each taken through the
iteration loop to approval before the next is started.

1. **Pipeline proof (infra)** — ✅ **DONE (desktop-verified).** JSyn dep + JitPack repo;
   `AudioSystem` skeleton + AudioDevice bridge + master gain/mute; a single **test tone**.
   Confirmed on **desktop**; Android steps noted below (not yet run on a device).
2. **Render + validation harness (infra)** — ✅ **DONE.** Renders a *single named sound in
   isolation* to a deterministic WAV, plus objective checks + waveform/spectrogram PNGs.
3. **Each sound, independently** — implement → render in isolation → **iterate with the
   user until approved** → lock → next. Suggested order (each its own loop):
   wind → rain → water → pour splash → rock thud → sprout blip → birds.
4. **Mix & balance pass** — only once the individual sounds are approved: combine them, set
   relative levels + reverb, wire to live game state (`weather`/`settings`/water amount),
   and iterate on the whole bed together.
5. **Android device pass** — verify on a real device (latency, audio focus, lifecycle).

## Infra as built (what slices 1 & 2 actually shipped)

The two infra slices are implemented and desktop-verified. Details future-you needs:

**Files.**
- `core/.../audio/AudioSystem.java` — live engine; mirrors `ParticleSystem`. Built in
  `Terrarium.create()`, ticked from `Terrarium.update` via `audio.update(dt)`, and hooked
  into `dispose()` / `pause()` / `resume()`. Master gain + mute live in `Settings`
  (`masterVolume`, `audioMuted`); **`M`** toggles mute (handled in `Terrarium.update`).
- `core/.../audio/GdxAudioDeviceManager.java` — the bridge. A custom JSyn
  `AudioDeviceManager` whose output stream applies **ramped** master gain + a soft-clip
  limiter and forwards frames to `Gdx.audio.newAudioDevice(44100, stereo)`. libGDX's
  blocking `writeSamples` paces JSyn's engine thread, so **that engine thread is the audio
  thread** — the render thread only writes `Settings`. (We did *not* build a separate
  pull-mode daemon; the blocking-bridge achieves the same one-output-path goal more simply.)
- `core/.../audio/SoundLibrary.java` — shared voice catalogue; the same patch feeds the
  live engine and the offline harness. Only `test-tone` (a quiet 220 Hz sine) exists yet.
  Add a real voice = one `case` here.
- `lwjgl3/.../audio/AudioRenderMain.java` (+ `WavFile`, `Fft`, `SignalChecks`, `Plots`) —
  the offline harness. Pure Java, **no python/sox/ffmpeg** and **no audio hardware**: JSyn
  in `setRealTime(false)` with 0 in/out channels, `WaveRecorder` taps the graph, then a
  self-written WAV reader + radix-2 FFT drive the checks and Java2D PNGs.

**Audio-tuning constants** are in `Config` (`AUDIO_SAMPLE_RATE`, `MASTER_VOLUME` = 0.35 low
default, `AUDIO_GAIN_RAMP`, `TEST_TONE_*`).

**Run the harness:** `./gradlew :lwjgl3:renderAudio` (all sounds) or
`--args="wind"` (one). Outputs `build/audio/<sound>.wav` + `.waveform.png` +
`.spectrogram.png`; prints the checks; **exits non-zero if any hard check fails**
(clipping / clicks / silence) so it can gate a build. Verified: the test tone renders to
219 Hz-dominant, peak 0.20, no clip/clicks, DC 0.

**Android — not yet run, but the design already accounts for it (steps to verify):**
- **No JSyn Android artifact is needed.** We replaced JSyn's device layer with
  `GdxAudioDeviceManager`, so JSyn's JavaSound/AudioTrack code never loads; **libGDX** owns
  Android output (AudioTrack under `Gdx.audio.newAudioDevice`). JSyn core is pure Java with
  no natives, so nothing goes in `android/libs` (the `natives` config there is libGDX-only).
- `jsyn` reaches `:android` transitively — `core` declares it as **`api`** — so no change to
  `android/build.gradle` is required.
- Lifecycle is wired: `Terrarium.pause()`→`audio.pause()` stops the synth (so the blocking
  `writeSamples` can't wedge a backgrounded app); `resume()` restarts it.
- **To verify on device:** `./gradlew :android:assembleDebug` (needs the SDK), install, and
  listen for the test tone; watch `logcat` for `AudioSystem: audio online`. Check audio
  focus (pause on call/other app) and AudioTrack latency.
- **Caveat:** if a release build later turns on R8/shrinking, add keep rules for `com.jsyn.**`
  (debug builds don't shrink, so this doesn't affect the assembleDebug verification).

## Sound changelog (locked voices)

Per-voice record so we can A/B and revert. Params live in `SoundLibrary`.

- **wind — v1, APPROVED & wired live.** Pink noise → `FilterLowPass`, driven by two slow
  non-periodic `RedNoise` LFOs (one wanders the cutoff = the whoosh, one swells the gain =
  gusts). Locked params (`SoundLibrary.WIND_*`): cutoff 550 Hz ±300 @ 0.35 Hz, Q 0.6, gust
  0.60 ±0.40 @ 0.22 Hz, level 2.0. Isolated render peaks ~0.5–0.7 (stochastic), low/mid
  weighted, no harsh highs. Live: `AudioSystem` maps the Wind dial (`settings.windSpeed`,
  baseline `Config.WIND_SPEED`, up to 3×) to a `WindVoice.setIntensity` of 0.35→1.0,
  glided by a `LinearRamp` so dial moves don't click; intensity 1.0 == the approved render.

- **rain — v6, APPROVED & wired live.** A soft dark "sheet" bed (pink noise, high-pass 300
  / low-pass 1600 = a low wash, not a mid hiss) plus DROPLETS = the patter. Droplets are a
  "dust" of sparse random impulses (white noise thresholded at 0.9994, then gain+clamped to
  a consistent unit impulse) exciting a high-resonance (0.94) state-variable band-pass whose
  pitch wanders (RedNoise, ~800–2200 Hz), low-passed at 2.2 kHz so each is a tonal *plink*,
  not a click. Locked params: `SoundLibrary.RAIN_*` / `DROP_*`; bed level 0.45, drop level
  3.0, overall 2.0. Getting here took: bed too bright/wind-like → darken + add droplets;
  drops → sparser & normalized-impulse for punch; "sounds like static" → high resonance +
  low-pass the drops into tonal plinks + drop the bed to a low wash. Live: `AudioSystem`
  maps `weather.stormLevel()` (0 clear → silent, 1 → full) to the rain voice's intensity.
  Marked "good for now — revisit later if needed."

- **water — SHELVED (unsolved), on branch `add-water-voice`.** ~15 iterations across 5+
  architectures never produced a convincing *forest river*. What we learned, so the next
  attempt doesn't repeat it:
  - **The FLOW body works.** A continuous bright band-limited pink-noise rush, amplitude-
    modulated by a high floor + gentle *smoothed* swells, reference-matched on spectrum
    (centroid ~5 kHz, ~0% below 250 Hz) — the user approved this as "better." Kept as the
    `water-flow` channel (`SoundLibrary.buildWaterFlow`).
  - **The BUBBLE detail is the wall.** Every attempt to add lively bubbling/splashes fell
    into one of two failure modes, and there was no audible sweet spot in between (judged by
    the user; the model has no ears):
      - *tonal* (pure sine up-chirp "bloop", clean resonant ping) → **"sci-fi robot"**
      - *noisy* (sharp / varied / noise-burst-excited resonator bursts, dense or sparse) → **"static"**
    Real water bubbling is an organic in-between (Minnaert bubble physics: a decaying sine
    with an exponentially *rising* pitch, radius-randomized), which blind parameter-tuning
    couldn't hit.
  - **Reference-matching tooling exists** (built for this, kept on the branch): download a
    reference with `yt-dlp`, then `./gradlew :lwjgl3:analyzeAudio --args="ref.wav mine.wav"`
    (`AudioAnalyzeMain`) prints octave-band spectrum, spectral centroid, crest factor, and
    PNGs — so a sound can be tuned to a *measured* target, not a mental model. This got the
    flow's spectrum right; it can't fix a timbre the ear rejects.
  - **Channel architecture** is in place: `water` = `water-flow` + `water-bubble`, each
    renderable in isolation (`SoundLibrary.WATER_FLOW` / `WATER_BUBBLE`). Water is NOT wired
    into the live `AudioSystem` (it never shipped).
  - **To revisit:** either implement a real physical bubble model, or drop a short *recorded*
    bubble sample into the `water-bubble` channel (the architecture already supports it) — not
    more blind noise/resonator tuning. Ship flow-only if a quick ambient is wanted.

**Next:** water is shelved (above). Continue with the **event SFX** — pour splash → rock
thud → sprout blip — then **birds**, via the per-sound loop.

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
> `com.github.philburk:jsyn`, add the jitpack repo). Build the two infra slices only for
> now: (1) an `AudioSystem` in `core/.../audio/` (mirroring `ParticleSystem`) that opens
> `Gdx.audio.newAudioDevice` on a daemon thread, runs a JSyn synth in pull mode bridged
> into it, plays a quiet test tone, and has master gain + mute, wired into `Terrarium`
> create/update/dispose/pause; and (2) an **offline render harness** that renders a single
> named sound in isolation to a deterministic WAV in `build/audio/`, with objective checks
> (clip/click/level/DC/spectral) and waveform+spectrogram PNGs. Confirm it builds/runs on
> desktop; note the Android steps. **Then stop and we build the actual sounds together,
> one at a time** — starting with wind — using the per-sound iteration loop in SOUND.md
> (Claude renders it in isolation, I listen, we adjust until I approve, then move on).
