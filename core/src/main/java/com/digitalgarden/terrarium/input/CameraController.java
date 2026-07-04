package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.input.GestureDetector.GestureListener;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.render.WorldCamera;

/**
 * Pans and zooms the {@link WorldCamera}.
 * <ul>
 *   <li>Pan: WASD / arrow keys, right-mouse drag, or a second finger (touch).</li>
 *   <li>Zoom: mouse wheel (toward the cursor) or a two-finger pinch (toward the
 *       pinch center).</li>
 * </ul>
 * Scroll and pinch arrive as events, so this installs an input processor for
 * them; panning and tools still poll input, which is unaffected.
 */
public class CameraController implements GestureListener {
    private final Viewport viewport;
    private final WorldCamera camera;
    private final Vector3 tmp = new Vector3();

    private boolean dragging;
    private float lastX, lastY;

    private boolean pinching;
    private float pinchStartZoom;

    public CameraController(Viewport viewport, WorldCamera camera) {
        this.viewport = viewport;
        this.camera = camera;

        InputAdapter wheel = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // wheel up (amountY < 0) zooms in, toward the cursor
                float factor = amountY < 0 ? Config.ZOOM_STEP : 1f / Config.ZOOM_STEP;
                zoomAtScreen(factor, Gdx.input.getX(), Gdx.input.getY());
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(new GestureDetector(this), wheel));
    }

    public void update(float dt) {
        keyboardPan(dt);
        dragPan();
    }

    private void keyboardPan(float dt) {
        // pan speed is in on-screen pixels; convert to world pixels via zoom
        float d = Config.PAN_SPEED * dt / camera.zoom();
        float dx = 0f, dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= d;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += d;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy -= d;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy += d;
        if (dx != 0f || dy != 0f) camera.pan(dx, dy);
    }

    private void dragPan() {
        boolean active = Gdx.input.isButtonPressed(Input.Buttons.RIGHT) || Gdx.input.isTouched(1);
        if (!active) {
            dragging = false;
            return;
        }
        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        float lx = tmp.x, ly = tmp.y;
        if (dragging) {
            // drag the world with the pointer; convert the on-screen delta to world px
            float z = camera.zoom();
            camera.pan(-(lx - lastX) / z, (ly - lastY) / z);
        }
        lastX = lx;
        lastY = ly;
        dragging = true;
    }

    /** Zooms around a point given in raw screen pixels (mouse/finger). */
    private void zoomAtScreen(float factor, float screenX, float screenY) {
        tmp.set(screenX, screenY, 0f);
        viewport.unproject(tmp); // -> logical view coords (y-up)
        float focusVx = tmp.x;
        float focusVy = Config.VIEW_H - tmp.y; // to top-origin view pixel
        camera.zoomBy(factor, focusVx, focusVy);
    }

    // --- GestureListener: only pinch-zoom is used ---

    @Override
    public boolean pinch(Vector2 initial1, Vector2 initial2, Vector2 p1, Vector2 p2) {
        if (!pinching) {
            pinching = true;
            pinchStartZoom = camera.zoom();
        }
        float target = pinchStartZoom * (p1.dst(p2) / initial1.dst(initial2));
        float midX = (p1.x + p2.x) * 0.5f, midY = (p1.y + p2.y) * 0.5f;
        tmp.set(midX, midY, 0f);
        viewport.unproject(tmp);
        camera.zoomTo(target, tmp.x, Config.VIEW_H - tmp.y);
        return true;
    }

    @Override
    public void pinchStop() {
        pinching = false;
    }

    @Override public boolean zoom(float initialDistance, float distance) { return false; }
    @Override public boolean touchDown(float x, float y, int pointer, int button) { return false; }
    @Override public boolean tap(float x, float y, int count, int button) { return false; }
    @Override public boolean longPress(float x, float y) { return false; }
    @Override public boolean fling(float vx, float vy, int button) { return false; }
    @Override public boolean pan(float x, float y, float dx, float dy) { return false; }
    @Override public boolean panStop(float x, float y, int pointer, int button) { return false; }
}
