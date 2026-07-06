package com.digitalgarden.terrarium.lwjgl3.audio;

import java.io.File;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.util.WaveRecorder;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.audio.SoundLibrary;

/**
 * Offline audio render + validation harness — <b>Slice 2 infra</b> from SOUND.md. Renders a
 * single named sound <i>in isolation</i> to a deterministic WAV under {@code build/audio/},
 * then runs the objective checks and writes waveform + spectrogram PNGs beside it. This is
 * what makes the per-sound iteration loop possible: change a voice, render it here, listen to
 * the WAV, look at the PNGs, and read the checks — all without touching the live game.
 *
 * <p>Determinism: JSyn runs in non-real-time mode ({@code setRealTime(false)}) with a fixed
 * seed and no audio hardware (0 input/output channels — {@code WaveRecorder} taps the graph
 * directly), so re-renders are byte-comparable for A/B'ing tweaks.
 *
 * <pre>
 *   ./gradlew :lwjgl3:renderAudio                 # render every sound in SoundLibrary.ALL
 *   ./gradlew :lwjgl3:renderAudio --args="wind"   # render just one (once it exists)
 * </pre>
 *
 * Exit code is non-zero if any sound fails a hard check (clipping / clicks / silence), so it
 * can gate a build.
 */
public final class AudioRenderMain {
    /** Seconds of audio to render per sound. */
    private static final double DURATION = 2.0;

    public static void main(String[] args) throws Exception {
        String[] sounds = args.length > 0 ? args : SoundLibrary.ALL;
        File outDir = new File("build/audio");
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("Could not create " + outDir.getAbsolutePath());
            System.exit(2);
        }

        System.out.println("Rendering " + sounds.length + " sound(s) to "
                + outDir.getAbsolutePath());
        boolean allPass = true;

        for (String name : sounds) {
            System.out.println("\n=== " + name + " ===");
            File wav = new File(outDir, name + ".wav");
            try {
                render(name, wav);
            } catch (IllegalArgumentException e) {
                System.out.println("  SKIP  " + e.getMessage());
                allPass = false;
                continue;
            }

            WavFile w = WavFile.read(wav);
            double[] mono = w.mono();

            File waveformPng = new File(outDir, name + ".waveform.png");
            File spectroPng = new File(outDir, name + ".spectrogram.png");
            Plots.waveform(mono, waveformPng);
            Plots.spectrogram(mono, w.sampleRate, spectroPng);

            SignalChecks checks = SignalChecks.run(mono, w.sampleRate);
            System.out.print(checks.report);
            System.out.println("  wav:        " + wav.getPath());
            System.out.println("  waveform:   " + waveformPng.getPath());
            System.out.println("  spectrogram:" + spectroPng.getPath());
            System.out.println("  => " + (checks.pass ? "PASS" : "FAIL"));
            allPass &= checks.pass;
        }

        System.out.println("\n" + (allPass ? "All renders passed." : "Some renders FAILED."));
        System.exit(allPass ? 0 : 1);
    }

    /** Renders one named sound to a stereo WAV using JSyn offline (non-real-time). */
    private static void render(String name, File wav) throws Exception {
        Synthesizer synth = JSyn.createSynthesizer();
        synth.setRealTime(false);

        UnitOutputPort voice = SoundLibrary.build(synth, name, Config.SEED);

        WaveRecorder recorder = new WaveRecorder(synth, wav, 2); // stereo
        voice.connect(0, recorder.getInput(), 0);
        voice.connect(0, recorder.getInput(), 1);

        // No hardware: 0 input, 0 output channels. WaveRecorder captures from the graph.
        synth.start(Config.AUDIO_SAMPLE_RATE,
                AudioDeviceManager.USE_DEFAULT_DEVICE, 0,
                AudioDeviceManager.USE_DEFAULT_DEVICE, 0);
        recorder.start();
        synth.sleepFor(DURATION);
        recorder.stop();
        synth.stop();
        recorder.close();
    }

    private AudioRenderMain() {}
}
