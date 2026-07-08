package com.digitalgarden.terrarium.lwjgl3.audio;

import java.io.File;

/**
 * Analyzes arbitrary WAV files (e.g. a reference recording alongside our synth renders) with
 * the same tools the render harness uses — objective checks + a finer octave-band spectral
 * fingerprint + waveform/spectrogram PNGs — so a procedural sound can be tuned to <em>match</em>
 * a measured target instead of a mental model. Used to chase the "forest river" reference.
 *
 * <pre>./gradlew :lwjgl3:analyzeAudio --args="path/to/a.wav path/to/b.wav"</pre>
 */
public final class AudioAnalyzeMain {
    // Octave-ish band edges (Hz) for the spectral fingerprint.
    private static final double[] EDGES = { 0, 125, 250, 500, 1000, 2000, 4000, 8000, 22050 };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: analyzeAudio <wav> [<wav> ...]");
            System.exit(2);
        }
        for (String path : args) {
            File wav = new File(path);
            System.out.println("\n=== " + wav.getName() + " ===");
            WavFile w = WavFile.read(wav);
            double[] mono = w.mono();

            SignalChecks checks = SignalChecks.run(mono, w.sampleRate);
            System.out.print(checks.report);

            octaveBands(mono, w.sampleRate);

            String base = wav.getName().replaceFirst("\\.[^.]+$", "");
            File dir = wav.getParentFile();
            Plots.waveform(mono, new File(dir, base + ".waveform.png"));
            Plots.spectrogram(mono, w.sampleRate, new File(dir, base + ".spectrogram.png"));
            System.out.println("  (wrote " + base + ".waveform.png / .spectrogram.png)");
        }
    }

    /** Prints the fraction of spectral energy in each octave-ish band + the spectral centroid. */
    private static void octaveBands(double[] x, int sampleRate) {
        int n = Fft.floorPow2(Math.min(x.length, 1 << 16));
        // Average magnitude across several Hann-windowed frames for a stable profile.
        int frames = Math.max(1, (x.length - n) / n);
        double[] mag = new double[n / 2];
        for (int f = 0; f < frames; f++) {
            double[] frame = new double[n];
            System.arraycopy(x, f * n, frame, 0, n);
            Fft.hann(frame);
            double[] m = Fft.magnitudes(frame);
            for (int i = 0; i < mag.length; i++) mag[i] += m[i];
        }
        double binHz = sampleRate / (double) n;
        double[] band = new double[EDGES.length - 1];
        double total = 0, centroidNum = 0, centroidDen = 0;
        for (int i = 1; i < mag.length; i++) {
            double freq = i * binHz;
            double e = mag[i];
            total += e;
            centroidNum += freq * e;
            centroidDen += e;
            for (int b = 0; b < band.length; b++) {
                if (freq >= EDGES[b] && freq < EDGES[b + 1]) { band[b] += e; break; }
            }
        }
        StringBuilder sb = new StringBuilder("  octaves           ");
        for (int b = 0; b < band.length; b++) {
            sb.append(String.format("%d-%d:%.0f%%  ",
                    (int) EDGES[b], (int) EDGES[b + 1], 100 * band[b] / (total + 1e-12)));
        }
        System.out.println(sb.toString().trim());
        System.out.printf("  centroid          %.0f Hz%n", centroidNum / (centroidDen + 1e-12));
    }

    private AudioAnalyzeMain() {}
}
