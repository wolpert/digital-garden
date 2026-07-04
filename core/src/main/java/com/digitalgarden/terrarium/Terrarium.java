package com.digitalgarden.terrarium;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.render.PixelRenderer;

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
    private float time;

    @Override
    public void create() {
        batch = new SpriteBatch();
        viewport = new FitViewport(Config.VIEW_W, Config.VIEW_H);
        world = new World(Config.SEED);
        renderer = new PixelRenderer();
    }

    @Override
    public void render() {
        time += Gdx.graphics.getDeltaTime();
        renderer.render(world, time);

        ScreenUtils.clear(0.05f, 0.06f, 0.09f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Texture tex = renderer.getTexture();
        // flipY so pixmap row 0 (top) draws at the top of the screen
        batch.draw(tex, 0f, 0f, Config.VIEW_W, Config.VIEW_H,
                0, 0, tex.getWidth(), tex.getHeight(), false, true);
        batch.end();
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
