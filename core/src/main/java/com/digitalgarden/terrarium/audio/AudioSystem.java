package com.digitalgarden.terrarium.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;
import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.unitgen.LineOut;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;
import com.digitalgarden.terrarium.sim.WeatherSystem;

/**
 * The game's procedural audio engine. Mirrors {@link com.digitalgarden.terrarium.fx.ParticleSystem}
 * in shape: constructed in {@code Terrarium.create()}, ticked once per frame, and disposed
 * with the app. It runs a JSyn {@link Synthesizer} whose engine thread is bridged into a
 * single libGDX {@link AudioDevice} (see {@link GdxAudioDeviceManager}) for a unified
 * desktop+Android output path with one master gain and mute.
 *
 * <p>Voices are built one at a time (SOUND.md) and summed here. Currently the wind and
 * rain ambient beds play, each with a game-controllable intensity that {@link #update}
 * maps from live state (the Wind dial, the storm level). More voices and one-shot SFX
 * get added as they're approved.
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
    private SoundLibrary.AmbientVoice wind;
    private SoundLibrary.AmbientVoice rain;
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

            // Build the ambient voices and sum each into both channels (JSyn adds signals
            // connected to the same port). Master gain + soft-clip live in the bridge.
            wind = SoundLibrary.buildWind(synth);
            rain = SoundLibrary.buildRain(synth);
            for (SoundLibrary.AmbientVoice v : new SoundLibrary.AmbientVoice[] { wind, rain }) {
                v.output.connect(0, lineOut.input, 0);
                v.output.connect(0, lineOut.input, 1);
            }

            available = true;
            startEngine();
            Gdx.app.log("AudioSystem", "audio online: " + Config.AUDIO_SAMPLE_RATE
                    + " Hz stereo, wind + rain (M to mute)");
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
     * Per-frame update from the render thread: map live game state to each voice's target
     * intensity. The audio thread ramps toward whatever we set here (click-free).
     */
    public void update(WeatherSystem weather, float dt) {
        if (!available) return;
        // Wind: the Wind dial (settings.windSpeed, baseline Config.WIND_SPEED, up to 3x) —
        // a faint breeze when calm, full when the dial is maxed.
        float windNorm = MathUtils.clamp(settings.windSpeed / (Config.WIND_SPEED * 3f), 0f, 1f);
        wind.setIntensity(0.35 + 0.65 * windNorm);
        // Rain: the current storm level (0 = clear → silent, 1 = full storm → full rain).
        rain.setIntensity(MathUtils.clamp(weather.stormLevel(), 0f, 1f));
    }

    /** Level to restore to when unmuting from the M key (if the slider is at Off). */
    private int preMuteLevel = Config.SOUND_LEVEL_DEFAULT;

    /** M-key mute toggle: drops the sound level to Off, or restores the last audible level.
     *  The bridge ramps the master gain to/from zero, so it's click-free. */
    public void toggleMute() {
        if (settings.soundLevel > 0) {
            preMuteLevel = settings.soundLevel;
            settings.setSoundLevel(0);
        } else {
            settings.setSoundLevel(preMuteLevel);
        }
        Gdx.app.log("AudioSystem", isMuted() ? "muted" : "unmuted (level " + settings.soundLevel + ")");
    }

    public boolean isMuted() {
        return settings.soundLevel == 0;
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
