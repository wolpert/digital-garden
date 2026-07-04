package com.digitalgarden.terrarium;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.input.InputController;
import com.digitalgarden.terrarium.render.PixelRenderer;
import com.digitalgarden.terrarium.sim.FluidSystem;
import com.digitalgarden.terrarium.sim.WeatherSystem;

/**
 * Terrarium — a top-down, real-time landscape sandbox.
 *
 * <p>Milestone 1 (this scaffold): a procedurally generated, pixel-textured map
 * rendered to a fixed 480x270 logical canvas and scaled to the window.
 * Later milestones layer in: (1) fluid water, (2) moisture + growth + terrain
 * transitions, (3) weather, (4) player tools. See PROMPT.md.
 */
public class Terrarium extends ApplicationAdapter {
    private SpriteBatch batch;
    private Viewport viewport;
    private PixelRenderer renderer;
    private World world;
    private FluidSystem fluid;
    private WeatherSystem weather;
    private InputController input;
    private float time;
    private float simAccumulator;

    @Override
    public void create() {
        batch = new SpriteBatch();
        viewport = new FitViewport(Config.VIEW_W, Config.VIEW_H);
        world = new World(Config.SEED);
        fluid = new FluidSystem(world);
        weather = new WeatherSystem(world, Config.SEED * 13 + 5);
        input = new InputController(world, viewport);
        renderer = new PixelRenderer();
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        time += dt;
        update(dt);
        renderer.render(world, weather, time);

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
    }

    /** Pours (continuous) then advances the fluid on a fixed timestep. */
    private void update(float dt) {
        input.update(dt);
        simAccumulator += dt;
        // cap iterations so a hitch can't spiral into a catch-up death loop
        int steps = 0;
        while (simAccumulator >= Config.SIM_TICK && steps < 5) {
            weather.step(world, Config.SIM_TICK); // rain lands...
            fluid.step(world);                    // ...then flows and pools
            simAccumulator -= Config.SIM_TICK;
            steps++;
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        renderer.dispose();
    }
}
