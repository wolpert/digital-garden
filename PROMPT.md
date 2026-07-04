# Prompt: "Terrarium" — a top-down landscape growing sandbox (libGDX)

Build a **libGDX game in Java** called **Terrarium**: a relaxing, real-time, top-down landscape simulator with no win/lose condition. The player observes and gently nudges a living landscape — watering, planting seeds, and dragging rocks around — while weather (clouds, rain) drifts across on its own and water flows and pools like a real fluid.

## Project setup
- Generate the project with **gdx-liftoff / gdx-setup**: Java, Gradle, modules for **Desktop (LWJGL3)** and **Android**. Share game logic in the `core` module; keep platform launchers thin.
- Target a fixed logical resolution (**480×270**, 16:9) rendered to a `FitViewport` so it scales cleanly to any window or phone screen. Design for both mouse (desktop) and touch (Android) — every interaction must work with a single pointer.
- Use a fixed-timestep simulation (e.g. 15–20 sim ticks/second) decoupled from render framerate. Keep grid size, tick rate, and all tuning constants (evaporation, flow rate, growth thresholds, etc.) in one config class.

## The board
- A **fine** grid of tiles — small tiles, ~2–3 logical pixels each. Default to a **160×90** grid (3px tiles) and make it trivial to change; the sim must stay smooth on a phone at this density. Each tile has a **terrain type**, a **moisture** value (0–1), a **water level** (fluid volume, see below), and optional **contents** (plant, rock).
- Terrain types and base colors:
  - **Grass** — light green
  - **Trees** — dark green
  - **Water** — blue (rendered from the fluid layer, not a fixed terrain)
  - **Dirt** — brown
  - **Sand** — yellow
- **Render at the pixel level, not as flat squares.** Give the board subtle internal texture so it reads as a rich landscape rather than a color grid: per-pixel value noise / dithering, slightly varied hues seeded by tile coordinates (stable, not flickering), darker edges where terrain types meet, and animated shimmer/caustics on water whose brightness scales with water depth. Draw into an offscreen pixel buffer (`Pixmap`/mesh of points / small framebuffer) so the aesthetic is deliberately low-res and crisp. Cozy, cohesive palette — tasteful pixel art, not neon.
- Generate the initial map procedurally (value/Perlin noise): an elevation heightmap drives everything — low elevation collects water, then sand near shorelines, dirt, grass, and clusters of trees on higher/wetter ground. Scatter a few rocks. Each land tile also stores an **elevation** used by the fluid sim.

## Fluid water (cellular-automata simulation)
Water is a **real flowing fluid**, not static terrain:
- Each tile stores a **water level** (volume). Water **flows downhill** by elevation and **spreads to equalize levels** with neighbors, so it **pools in low ground**, forms lakes and rivers, and settles to a flat surface.
- Use a stable cellular-automata / shallow-water approach (per-tick flux between neighbors, clamped so it can't oscillate or blow up). Conserve volume during flow; only evaporation and rain add/remove it.
- **Rocks and high elevation dam and divert flow** — the player can wall off or redirect water by moving rocks and by the terrain's shape.
- Water **evaporates** slowly (faster in sun/on sand, slower in shade/under trees). Standing water raises **moisture** in its own and adjacent land tiles; shorelines stay damp.
- A tile renders as **water** when its water level exceeds a small threshold; depth drives color (shallow = lighter, deep = darker) and the shimmer intensity. Very shallow film over sand/dirt looks wet rather than fully blue.

## Simulation rules (real-time, continuous)
- **Moisture** comes from nearby water and rain, diffuses gently to neighbors, and dries out over time (sun/dryness lowers it; sand dries fast, tree cover retains).
- **Seeds → plants:** a planted seed needs adequate moisture to sprout, then grows through stages over time (sprout → mature). Well-watered dirt/grass grows plants faster; too dry and a seed stalls or dies; submerged/flooded and it rots. Include a few seed types with different needs:
  - **Grass seed** — fast, likes moderate moisture.
  - **Tree seed** — slow, needs sustained moisture, matures into dark-green tree terrain.
  - **Desert/flower seed** — tolerates sand and low moisture.
  Mature plants can slowly spread to suitable neighboring tiles.
- **Terrain transitions** emerge from moisture: persistently wet dirt greens into grass; grass hosting a mature tree becomes tree terrain; long-dry grass browns back to dirt; dry dirt at a water's edge can turn to sand. Keep transitions gradual so the map visibly "grows."
- **Rocks** are solid: they block plant growth, and (above) dam/divert the fluid. The player can pick them up and drop them elsewhere.

## Weather (autonomous)
- **Clouds** drift as a translucent overlay in a wind direction that changes slowly, casting soft shadows on the tiles below.
- **Rain** falls from clouds onto the tiles beneath them, **adding water volume** (which then flows via the fluid sim) and raising moisture — animated rain streaks / pixel droplets. Rain comes and goes on its own; the world keeps changing even if the player does nothing, filling low ground into ponds over time.
- Optional subtle day/night ambient tint for atmosphere.

## Player interactions
A minimal on-screen tool palette (mouse or touch):
- **Water** — click/drag to pour water (adds fluid volume, with a soft radius) like a watering can; it then flows naturally.
- **Plant seed** — select a seed type, then click/drag to sow on valid terrain.
- **Move rock** — click a rock to pick it up, click/drag to place it; rocks snap to tiles and immediately affect flow.
- Show a brush cursor/highlight so the player sees where the action lands. Keep the UI unobtrusive — this is a calm toy.

## Feel & polish
- No score, no timer, no fail state — a **zen sandbox**. Success is a landscape that feels alive and reacts believably to the player and the weather.
- Satisfying feedback: ripples and a splash when water is poured or rain lands, tiny sprout pop when a seed sprouts, dust when a rock lands, flowing/settling animation as water finds its level.
- Clean, readable code: a `Tile`/grid model, a `FluidSystem` (water flow), a `SimulationSystem` (moisture, growth, terrain transitions), a `WeatherSystem` (clouds/rain), a `Renderer` (pixel-level tile drawing), and an `InputController` (tools). All tuning constants in one config class.

## Build order
Scaffold the gdx-liftoff project and get a colored, pixel-textured procedural map rendering on desktop. Then layer in, running the game after each milestone: **(1)** fluid water (pour + flow + pooling), **(2)** moisture + growth + terrain transitions, **(3)** weather (clouds + rain feeding the fluid), **(4)** tools + polish. Verify smoothness on the 160×90 grid before adding cost.
