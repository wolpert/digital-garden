package com.digitalgarden.terrarium;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.input.CameraController;
import com.digitalgarden.terrarium.input.ConfigMenu;
import com.digitalgarden.terrarium.input.DialPanel;
import com.digitalgarden.terrarium.input.Hud;
import com.digitalgarden.terrarium.input.InputController;
import com.digitalgarden.terrarium.render.MiniMap;
import com.digitalgarden.terrarium.render.PixelRenderer;
import com.digitalgarden.terrarium.render.WorldCamera;
import com.digitalgarden.terrarium.sim.FluidSystem;
import com.digitalgarden.terrarium.sim.GrowthSystem;
import com.digitalgarden.terrarium.sim.SpringSystem;
import com.digitalgarden.terrarium.sim.WeatherSystem;
import com.digitalgarden.terrarium.fx.ParticleSystem;
import com.digitalgarden.terrarium.audio.AudioSystem;
import com.digitalgarden.terrarium.wildlife.WildlifeSystem;

/**
 * Terrarium — a top-down, real-time landscape sandbox.
 *
 * <p>A procedurally generated, pixel-textured world larger than the screen; the
 * camera scrolls a 480x270 window over it. Each fixed tick runs weather (clouds +
 * rain), the fluid water sim, then plant growth and moisture-driven terrain
 * transitions. The player nudges it through the tool palette: pour water, sow
 * seeds, and move rocks; pan with WASD/arrows, right-drag, or two fingers.
 * See PROMPT.md.
 */
public class Terrarium extends ApplicationAdapter {
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Viewport viewport;
    private OrthographicCamera uiCam;
    private PixelRenderer renderer;
    private MiniMap miniMap;
    private World world;
    private Settings settings;
    private FluidSystem fluid;
    private SpringSystem springs;
    private WeatherSystem weather;
    private GrowthSystem growth;
    private WildlifeSystem wildlife;
    private ParticleSystem particles;
    private AudioSystem audio;
    private WorldCamera camera;
    private CameraController cameraController;
    private Hud hud;
    private DialPanel dials;
    private ConfigMenu config;
    private InputController input;
    private float time;
    private float simAccumulator;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        viewport = new FitViewport(Config.VIEW_W, Config.VIEW_H);
        uiCam = new OrthographicCamera();
        uiCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        world = new World(Config.SEED);
        settings = new Settings();
        particles = new ParticleSystem();
        fluid = new FluidSystem(world, settings);
        springs = new SpringSystem(world);
        weather = new WeatherSystem(world, Config.SEED * 13 + 5, settings);
        growth = new GrowthSystem(Config.SEED * 7 + 1, settings, particles);
        wildlife = new WildlifeSystem(world, Config.SEED * 17 + 3);
        audio = new AudioSystem(settings);
        camera = new WorldCamera();
        cameraController = new CameraController(viewport, camera);
        hud = new Hud(viewport);
        dials = new DialPanel(viewport, settings);
        config = new ConfigMenu(viewport, settings);
        renderer = new PixelRenderer();
        miniMap = new MiniMap();
        input = new InputController(world, viewport, hud, camera, miniMap, dials, config, springs, particles);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        time += dt;
        update(dt);
        renderer.render(world, weather, time, camera);

        ScreenUtils.clear(0.05f, 0.06f, 0.09f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Texture tex = renderer.getTexture();
        // No flip: pixmap row 0 draws at the top of the screen. This matches the
        // InputController's screen->tile mapping (so a top click pours at the top)
        // and makes rain streaks fall downward (they scroll along pixmap +py).
        batch.draw(tex, 0f, 0f, Config.VIEW_W, Config.VIEW_H,
                0, 0, tex.getWidth(), tex.getHeight(), false, false);
        batch.end();

        wildlife.draw(shapes, viewport.getCamera(), camera, time);
        particles.draw(shapes, viewport.getCamera(), camera);

        miniMap.render(batch, shapes, viewport.getCamera(), world, camera);
        dials.render(shapes, batch, uiCam);
        hud.render(shapes, batch, uiCam, camera, input.isCarrying());
        config.render(shapes, batch, uiCam); // last: the popup window draws on top
    }

    /** Handles input and camera, then advances the sim on a fixed timestep. */
    private void update(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            audio.toggleMute();
        }
        cameraController.update(dt);
        input.update(dt);
        simAccumulator += dt;
        // cap iterations so a hitch can't spiral into a catch-up death loop
        int steps = 0;
        while (simAccumulator >= Config.SIM_TICK && steps < 5) {
            weather.step(world, Config.SIM_TICK); // rain lands...
            springs.apply(Config.SIM_TICK);       // ...springs well up...
            fluid.step(world);                    // ...then it all flows and pools...
            growth.step(world, Config.SIM_TICK);  // ...then plants & terrain react
            simAccumulator -= Config.SIM_TICK;
            steps++;
        }
        wildlife.update(dt); // continuous entities, smooth per-frame motion
        particles.update(world, weather, camera, dt);
        audio.update(weather, dt); // maps live state (wind dial, storm level) to ambient audio
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiCam.setToOrtho(false, width, height);
    }

    @Override
    public void pause() {
        // Android backgrounding: stop the audio thread (writeSamples would otherwise block).
        if (audio != null) audio.pause();
    }

    @Override
    public void resume() {
        if (audio != null) audio.resume();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        renderer.dispose();
        miniMap.dispose();
        dials.dispose();
        hud.dispose();
        config.dispose();
        if (audio != null) audio.dispose();
    }
}
