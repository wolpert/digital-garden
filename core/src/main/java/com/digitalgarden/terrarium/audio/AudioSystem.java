package com.digitalgarden.terrarium.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.LineOut;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;

/**
 * The game's procedural audio engine. Mirrors {@link com.digitalgarden.terrarium.fx.ParticleSystem}
 * in shape: constructed in {@code Terrarium.create()}, ticked once per frame, and disposed
 * with the app. It runs a JSyn {@link Synthesizer} whose engine thread is bridged into a
 * single libGDX {@link AudioDevice} (see {@link GdxAudioDeviceManager}) for a unified
 * desktop+Android output path with one master gain and mute.
 *
 * <p>This is the <b>Slice 1 infra</b> from SOUND.md: skeleton + output bridge + master
 * gain/mute + a quiet test tone to prove sound reaches the speakers on both platforms.
 * The actual voices (wind, rain, …) come later, built one at a time; when they exist,
 * {@link #update(float)} will push their live target parameters to the audio thread and
 * event hooks will fire one-shot SFX. For now those are intentionally stubs.
 *
 * <p>Threading: the render thread only ever writes {@link Settings} and calls the lifecycle
 * methods here; all synthesis and the blocking {@code writeSamples} happen on JSyn's engine
 * thread. Audio init failures (e.g. no output device) are swallowed so the game still runs.
 */
public final class AudioSystem {
    private final Settings settings;

    private AudioDevice device;
    private Synthesizer synth;
    private LineOut lineOut;
    private boolean available;

    public AudioSystem(Settings settings) {
        this.settings = settings;
        try {
            // Stereo libGDX device; isMono = false.
            device = Gdx.audio.newAudioDevice(Config.AUDIO_SAMPLE_RATE, false);
            AudioDeviceManager manager = new GdxAudioDeviceManager(device, settings);
            synth = JSyn.createSynthesizer(manager);

            lineOut = new LineOut();
            synth.add(lineOut);

            // Build the (only, for now) voice and fan its mono output to both channels.
            UnitOutputPort voice = SoundLibrary.build(synth, SoundLibrary.TEST_TONE, Config.SEED);
            voice.connect(0, lineOut.input, 0);
            voice.connect(0, lineOut.input, 1);

            available = true;
            startEngine();
            Gdx.app.log("AudioSystem", "audio online: " + Config.AUDIO_SAMPLE_RATE
                    + " Hz stereo, playing test tone (M to mute)");
        } catch (Throwable t) {
            // No audio device, unsupported platform, etc. — run silently rather than crash.
            available = false;
            Gdx.app.error("AudioSystem", "audio unavailable; continuing without sound", t);
        }
    }

    /** Starts the JSyn engine + output. No real input/output hardware — 0 input channels,
     *  2 output channels routed through our libGDX bridge manager. */
    private void startEngine() {
        // (frameRate, inputDeviceID, numInputChannels, outputDeviceID, numOutputChannels)
        synth.start(Config.AUDIO_SAMPLE_RATE,
                AudioDeviceManager.USE_DEFAULT_DEVICE, 0,
                AudioDeviceManager.USE_DEFAULT_DEVICE, 2);
        lineOut.start();
    }

    /**
     * Per-frame update from the render thread. Once ambient voices exist this will map live
     * game state (weather, wind, visible water) to their target parameters — the audio
     * thread ramps toward whatever we set here. Nothing to drive yet with only a test tone.
     */
    public void update(float dt) {
        // Reserved for ambient parameter mapping; see SOUND.md "Parameter mapping".
    }

    /** Flips global mute. The bridge ramps the master gain to/from zero, so it's click-free. */
    public void toggleMute() {
        settings.audioMuted = !settings.audioMuted;
        Gdx.app.log("AudioSystem", settings.audioMuted ? "muted" : "unmuted");
    }

    public boolean isMuted() {
        return settings.audioMuted;
    }

    /** Stop audio when the app is backgrounded (Android lifecycle). Safe to call repeatedly. */
    public void pause() {
        if (available && synth != null && synth.isRunning()) {
            synth.stop();
        }
    }

    /** Resume audio after the app returns to the foreground. */
    public void resume() {
        if (available && synth != null && !synth.isRunning()) {
            startEngine();
        }
    }

    public void dispose() {
        if (synth != null) {
            synth.stop();
        }
        if (device != null) {
            device.dispose();
        }
    }
}
