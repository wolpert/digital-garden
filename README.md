# Terrarium

A relaxing, top-down **landscape growing sandbox** built with [libGDX](https://libgdx.com/).
There's no score and no fail state — you tend a living, procedurally generated
world: pour water and watch it flow into ponds, plant seeds and watch them grow
(or wither), drag rocks around to dam the flow, while clouds drift over and rain
comes and goes on its own.

The world is rendered at the pixel level — colors *are* the landscape:

| Color | Terrain |
|-------|---------|
| Light green | Grass |
| Dark green | Trees |
| Blue | Water |
| Brown | Dirt |
| Yellow | Sand |

## Controls

**Tools** — pick one from the palette in the bottom-left (hover a button for its name):

| Tool | Action |
|------|--------|
| **Water** | Hold / drag to pour water into a soft radius; it then flows downhill and pools. |
| **Grass / Tree / Flower seed** | Click or drag to sow seeds on land. |
| **Move rock** | Press on a rock to pick it up, release to drop it (rocks dam and divert water). |
| **Spring / Drain** | Click to place a spring (wells up water — a spring on high ground carves a river/lake downhill) or a drain (removes water). Click again to remove. |
| **Raise / Lower** | Hold to sculpt the terrain up into hills or down into valleys, rerouting where water flows. Relief is shaded so you can see the shape. |

**Camera**

| Input | Desktop | Touch |
|-------|---------|-------|
| **Pan** | WASD / arrow keys, or right-mouse drag | Drag with two fingers |
| **Zoom** | Mouse wheel (toward the cursor) | Pinch |

While zoomed in, a **mini-map** in the top-right shows the whole world with a
rectangle marking your current view. Click or drag on it to jump the camera
there.

Little **particle effects** add life and feedback: splashes when you pour or rain
lands, expanding ripples on rained-on water, dust when a rock drops, a green pop
when a plant matures, and pollen drifting over grass and flowers.

The world is alive with **wildlife**: rabbits graze the grass (and over-graze it
to dirt), drink and breed near water, and flee foxes that hunt them — a little
predator/prey loop — while birds flock overhead, fish swim in the ponds, and
butterflies drift toward flowers. Droughts thin the grass and the animals with it.

A row of **dials** in the lower-right tunes the simulation live — rain, wind,
water flow, evaporation, growth, and the two death modes (wither from drought,
rot from flooding). Drag a dial up/down to turn it (each is a multiplier of the
default, `1.0x`); hover it to see its current setting.

## Running it

Requires a JDK (17+). The bundled Gradle wrapper handles everything else.

**Desktop:**

```bash
./gradlew :lwjgl3:run
```

**Android** (needs the Android SDK). Point Gradle at it via a `local.properties`
file in the project root containing `sdk.dir=/path/to/Android/Sdk`, or by setting
`ANDROID_HOME`. Then:

```bash
./gradlew :android:assembleDebug   # build a debug APK
./gradlew :android:installDebug    # install to a connected device/emulator
```

See [`android/README.md`](android/README.md) for details.

## How it works

The world is **480×270 tiles**, larger than the screen; the camera scrolls and
zooms a window over it. Each fixed simulation tick (~18/sec, decoupled from the
render framerate) runs, in order — **weather** (rain), **springs** (player-placed
sources), **fluid** (flow), **growth** — while **wildlife** and **particles**
update every frame for smooth motion. The core simulation:

1. **Weather** — a drifting noise-based cloud field carried by a wandering wind.
   Dense clouds rain, adding water and moisture; storms ebb through clear spells.
2. **Fluid** — a shallow-water cellular automaton. Water flows only downhill
   (by terrain elevation + depth), conserving volume, so it pools in low ground
   and settles flat. Rocks are impermeable walls; springs and drains add/remove
   water; sculpting the elevation reroutes the flow. Water slowly evaporates.
3. **Growth** — seeds germinate and grow when moisture is right, stall and die
   when too dry, and rot when flooded. Grass and trees mature into their terrain;
   flowers bloom. Moisture also nudges terrain itself: wet dirt greens into grass,
   dry grass browns to dirt, dry shoreline dirt turns to sand.

The **renderer** paints the visible window per-pixel into an offscreen buffer,
adding per-tile hue variation, dithering, terrain-edge shading, elevation relief,
animated water shimmer, cloud shadows and rain streaks.

### Project layout

```
core/     All game logic, shared by every platform:
  Config.java            every tuning constant in one place
  Settings.java          runtime-adjustable params (driven by the dials)
  World / Tile / Plant   the grid model and world generation
  sim/                   Fluid, Weather, Growth, Spring systems
  wildlife/              WildlifeSystem + critters
  fx/                    ParticleSystem
  render/                PixelRenderer, WorldCamera, MiniMap
  input/                 InputController (tools), CameraController, Hud, DialPanel
  Terrarium.java         the ApplicationAdapter wiring it all together
lwjgl3/   Desktop launcher (LWJGL3)
android/  Android launcher
```

Want to change the feel of the world? Almost everything — grid size, tick rate,
sea level, evaporation, growth rates, rain intensity, zoom limits — lives in
[`Config.java`](core/src/main/java/com/digitalgarden/terrarium/Config.java).

## Tech

Java 17 · libGDX 1.14.2 · Gradle (wrapper, 9.6.1). No external art or audio
assets — the landscape and UI are drawn procedurally.

## License

[MIT](LICENSE) © Ned Wolpert
