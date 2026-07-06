package com.digitalgarden.terrarium.audio;

import com.badlogic.gdx.audio.AudioDevice;
import com.jsyn.devices.AudioDeviceInputStream;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.devices.AudioDeviceOutputStream;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Settings;

/**
 * Bridges JSyn's synthesis engine to a libGDX {@link AudioDevice}, so the whole game has
 * <em>one</em> audio output path (desktop JavaSound / Android AudioTrack both handled by
 * libGDX) with one master gain and mute. We hand this manager to
 * {@code JSyn.createSynthesizer(...)}; when the synth starts with output channels, its
 * engine thread pulls generated frames and pushes them through
 * {@link Stream#write(double[], int, int)} here.
 *
 * <p>The key trick: libGDX's {@code AudioDevice.writeSamples} <b>blocks</b> until the
 * buffer drains, which paces JSyn's engine thread to real time — so JSyn's engine thread
 * <i>is</i> the dedicated daemon audio thread SOUND.md calls for. The render thread never
 * touches this; it only writes {@link Settings} fields that the bridge reads.
 *
 * <p>Only output is supported (no capture). Master gain is applied here, ramped per sample
 * toward the {@link Settings} target to avoid zipper noise, and the sum is soft-clipped.
 */
public final class GdxAudioDeviceManager implements AudioDeviceManager {
    private static final int OUTPUT_DEVICE_ID = 0;

    private final AudioDevice device;
    private final Settings settings;

    public GdxAudioDeviceManager(AudioDevice device, Settings settings) {
        this.device = device;
        this.settings = settings;
    }

    // --- The output stream JSyn writes generated frames into ---

    private final class Stream implements AudioDeviceOutputStream {
        private final int channels;
        private float[] buf = new float[0];
        /** Smoothed master gain; ramps toward the Settings target to avoid clicks. */
        private float gain;

        Stream(int channels) {
            this.channels = channels;
        }

        @Override
        public void write(double[] data, int start, int count) {
            if (buf.length < count) {
                buf = new float[count];
            }
            final float target = settings.audioMuted ? 0f : settings.masterVolume;
            for (int i = 0; i < count; i++) {
                gain += (target - gain) * Config.AUDIO_GAIN_RAMP;
                buf[i] = softClip(data[start + i] * gain);
            }
            // Blocks until consumed — this is what paces the JSyn engine thread.
            device.writeSamples(buf, 0, count);
        }

        @Override
        public void write(double value) {
            write(new double[] { value }, 0, 1);
        }

        @Override
        public void write(double[] buffer) {
            write(buffer, 0, buffer.length);
        }

        @Override
        public void start() {
            gain = 0f; // fade up from silence on (re)start, never a click
        }

        @Override
        public void stop() {
        }

        @Override
        public void close() {
            // The libGDX device is owned and disposed by AudioSystem, not here.
        }

        @Override
        public double getLatency() {
            return (double) device.getLatency() / Config.AUDIO_SAMPLE_RATE;
        }
    }

    /**
     * Soft clip / limiter on the master sum. Transparent below the knee, then saturates
     * smoothly so a hot moment compresses instead of clipping hard. Keeps headroom.
     */
    static float softClip(double x) {
        final double knee = 0.8;
        double a = Math.abs(x);
        if (a <= knee) {
            return (float) x;
        }
        double range = 1.0 - knee;            // 0.2 of headroom above the knee
        double over = (a - knee) / range;     // >= 0
        double shaped = knee + range * Math.tanh(over);
        return (float) (Math.signum(x) * shaped);
    }

    // --- AudioDeviceManager: only the output path is meaningful ---

    @Override
    public AudioDeviceOutputStream createOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
        return new Stream(samplesPerFrame);
    }

    @Override
    public AudioDeviceInputStream createInputStream(int deviceID, int frameRate, int samplesPerFrame) {
        throw new UnsupportedOperationException("GdxAudioDeviceManager is output-only");
    }

    @Override
    public String getName() {
        return "libGDX AudioDevice bridge";
    }

    @Override
    public int getDeviceCount() {
        return 1;
    }

    @Override
    public String getDeviceName(int deviceID) {
        return "libGDX AudioDevice";
    }

    @Override
    public int getDefaultInputDeviceID() {
        return OUTPUT_DEVICE_ID;
    }

    @Override
    public int getDefaultOutputDeviceID() {
        return OUTPUT_DEVICE_ID;
    }

    @Override
    public int getMaxInputChannels(int deviceID) {
        return 0;
    }

    @Override
    public int getMaxOutputChannels(int deviceID) {
        return 2;
    }

    @Override
    public double getDefaultLowInputLatency(int deviceID) {
        return 0.0;
    }

    @Override
    public double getDefaultHighInputLatency(int deviceID) {
        return 0.0;
    }

    @Override
    public double getDefaultLowOutputLatency(int deviceID) {
        return 0.02;
    }

    @Override
    public double getDefaultHighOutputLatency(int deviceID) {
        return 0.05;
    }

    @Override
    public int setSuggestedInputLatency(double latency) {
        return 0;
    }

    @Override
    public int setSuggestedOutputLatency(double latency) {
        return 0;
    }
}
