package com.digitalgarden.terrarium.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.render.WorldCamera;

/**
 * Pans the {@link WorldCamera}. Desktop: WASD / arrow keys, or drag with the
 * right mouse button. Touch: drag with a second finger (a single finger is
 * reserved for tools). Panning is in logical view pixels, which map 1:1 to world
 * pixels, so a drag moves the world exactly under the pointer.
 */
public class CameraController {
    private final Viewport viewport;
    private final WorldCamera camera;
    private final Vector3 tmp = new Vector3();

    private boolean dragging;
    private float lastX, lastY;

    public CameraController(Viewport viewport, WorldCamera camera) {
        this.viewport = viewport;
        this.camera = camera;
    }

    public void update(float dt) {
        keyboardPan(dt);
        dragPan();
    }

    private void keyboardPan(float dt) {
        float d = Config.PAN_SPEED * dt;
        float dx = 0f, dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= d;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += d;
        // screen-up should reveal higher rows: world-y decreases
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy -= d;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy += d;
        if (dx != 0f || dy != 0f) camera.pan(dx, dy);
    }

    private void dragPan() {
        boolean right = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        boolean twoFinger = Gdx.input.isTouched(1);
        boolean active = right || twoFinger;
        if (!active) {
            dragging = false;
            return;
        }

        // use the primary pointer position (finger 0 / mouse) in logical coords
        tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(tmp);
        float lx = tmp.x, ly = tmp.y;

        if (dragging) {
            // drag the world with the pointer: content follows, camera moves opposite.
            // logical x maps to world +x; logical y is y-up, world-y is y-down.
            camera.pan(-(lx - lastX), (ly - lastY));
        }
        lastX = lx;
        lastY = ly;
        dragging = true;
    }
}
