# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Terrarium** — a top-down, real-time landscape sandbox built with libGDX (Java 17,
Gradle wrapper 9.6.1, libGDX 1.14.2). No external art or audio assets; the world and
UI are drawn procedurally. There is no win/lose state — the player nudges a living,
procedurally generated world (water, weather, plants, wildlife) with brush tools.

## Commands

```bash
./gradlew :lwjgl3:run        # run the desktop app (LWJGL3)
./gradlew build              # full build of every module: compile + Android lint + tests + APKs (what CI runs)
./gradlew :lwjgl3:build      # core + desktop only (no Android SDK needed)
./gradlew :android:assembleDebug   # Android debug APK
```

- **There are no unit tests yet** — the `test` task is `NO-SOURCE`. When adding tests
  (JUnit under `core/src/test/java`), run a single one with
  `./gradlew :core:test --tests 'com.digitalgarden.terrarium.SomeTest'`.
- **Android needs the SDK** (compileSdk 36, AGP 9.2.1). Set `ANDROID_HOME` or create a
  root `local.properties` with `sdk.dir=/path/to/Android/Sdk` (git-ignored). CI installs
  platform 36 via `setup-android`. `:android:assembleDebug` fails the *lint*-free path;
  only the full `build` runs lint, so run `./gradlew build` before pushing.
- Verifying visually: there are no tests, so validate changes by launching the app
  (`:lwjgl3:run`) and/or by writing a throwaway headless `main` compiled against
  `core/build/classes/java/main` + the cached `gdx-<ver>.jar` — pure classes
  (`World`, the `sim`/`wildlife` systems, `Noise`) run without a GL context; anything
  touching `Texture`/`Pixmap`/`ShapeRenderer` needs the running app.

## Module layout

- **`core/`** — all game logic and rendering, shared by every platform. Everything of
  interest lives here under `com.digitalgarden.terrarium`.
- **`lwjgl3/`** and **`android/`** — thin launchers that just instantiate
  `Terrarium` (the `ApplicationAdapter`). Don't put logic here.

## Architecture (the big picture)

`Terrarium.java` owns every system and wires them together — it's the map of the whole
app. Two update cadences run each frame in `Terrarium.update(dt)`:

1. **Fixed-timestep grid simulation** (`Config.SIM_TICK`, ~18/s) in an accumulator loop,
   in this order: `WeatherSystem` (rain) → `SpringSystem` (sources) → `FluidSystem`
   (flow) → `GrowthSystem` (plants + terrain transitions). Order matters: water is
   *added* (rain, springs) before it *flows*, then life reacts to the result.
2. **Per-frame continuous updates** with real `dt`: `CameraController`, `InputController`,
   `WildlifeSystem`, `ParticleSystem`. These are entity/UI systems that want smooth
   motion, not lockstep ticks.

Rendering order in `Terrarium.render()`: `PixelRenderer` paints the terrain →
`WildlifeSystem.draw` → `ParticleSystem.draw` → `MiniMap` → `DialPanel` → `Hud`.

### The world model
`World` is a flat `Tile[]` grid (`WORLD_W × WORLD_H` tiles, larger than the screen).
A `Tile` is plain mutable data: `terrain`, `elevation`, `moisture`, `water`, `rock`,
`spring`, `plantType`, `growth`. Systems mutate tiles in place; there is no ECS. `World`
also does procedural generation from seeded value noise (`util/Noise`).

- **`Config`** holds every tuning constant and all geometry (`TILE_SIZE`, `WORLD_W/H`,
  `VIEW_W/H`, rates, thresholds). Change feel here.
- **`Settings`** holds the subset of parameters that are adjustable *at runtime*. The
  sim systems read `Settings` (not the `Config` constants) for those, and `DialPanel`
  writes them — that's how the on-screen dials work. Adding a dial = a field in
  `Settings` + a `Dial` entry in `DialPanel` + the system reading it.

### Coordinate conventions (READ THIS — it has caused bugs twice)
- **World-y increases downward.** The pixmap's row 0 is the *top* of the screen (the
  terrain texture is drawn with `flipY=false`). So on screen, "up" = **decreasing** y /
  **negative** velocity, and gravity is **positive** vy. (This inverted the Y-axis input
  and the particle gravity in earlier bugs.)
- Tiles are `Config.TILE_SIZE` (3) logical pixels. `VIEW_W/H` (480×270) is the fixed
  logical canvas scaled to the window by a `FitViewport`.
- **`WorldCamera`** is the scroll+zoom into the larger world: an offset in world pixels
  plus a zoom (view-px per world-px). Never convert screen↔world by hand — use its
  `viewToWorldX/Y`, `worldToViewX/Y`, `centerOn`. `PixelRenderer`, `InputController`,
  `Hud` brush, `MiniMap`, wildlife and particle rendering all go through it.
- **Anchoring:** terrain dither / tile hue / water shimmer are *world*-anchored (so they
  don't swim when panning); rain streaks are *screen*-anchored (they fall down the
  screen). Keep new visual noise consistent with that intent.

### Rendering & UI
- `PixelRenderer` samples only the visible camera window and writes it per-pixel into an
  offscreen `Pixmap` uploaded to a `Texture` each frame. It layers terrain color +
  variation/dither + edges + plants + spring/drain markers + elevation shading + weather
  (cloud shadow/body, rain) per pixel. Cost is fixed at `VIEW_W×VIEW_H` regardless of
  world size.
- UI overlays (`Hud`, `DialPanel`, `MiniMap`) draw shapes in the logical view space via
  a shared `ShapeRenderer`, and draw tooltip **text** in screen space with a
  `BitmapFont` and a separate `OrthographicCamera` (`uiCam`). Note: libGDX
  `Camera.project()` returns coordinates with a **bottom-left origin** (y-up), which
  already matches `uiCam` — do not flip it again (that misplaced a tooltip once).
- Enable `GL_BLEND` around any translucent ShapeRenderer pass (particles, HUD panels).

### Input & tools
`Tool` (enum) defines the palette. Adding a tool = an enum entry (+ label/swatch) and a
`case` in `InputController.update`'s switch; brush-radius tools also get a circle in
`Hud`. `InputController` tracks **capture flags** so a press that lands on the palette,
mini-map, or a dial adjusts UI instead of acting on the world; a right-drag or second
finger means the `CameraController` is panning, so tool actions are suppressed.

## Conventions & workflow

- Match the surrounding Java style; systems are constructed in `Terrarium.create()` and
  disposed in `Terrarium.dispose()` if they hold GL/native resources.
- `main` is **branch-protected**: changes land via a PR that passes the **Build & test**
  check. Weekly Dependabot opens dependency PRs; non-major bumps auto-merge when green,
  majors wait for a human (`.github/README-dependabot.md`). Do **not** attempt to merge
  a PR you authored — that's the user's call.
- End commit messages with the `Co-Authored-By: Claude ...` trailer; end PR bodies with
  the Claude Code generation line.
