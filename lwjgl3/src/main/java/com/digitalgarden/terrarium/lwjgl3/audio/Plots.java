package com.digitalgarden.terrarium.lwjgl3.audio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Renders the two visual checks SOUND.md wants — a waveform and a spectrogram PNG — so we
 * can eyeball envelope shape, gusting, sweeps and clipping the same way we screenshot the
 * visuals. Pure Java2D, no external tools (no python/sox/ffmpeg needed).
 */
final class Plots {
    private Plots() {}

    /** Min/max-per-column waveform, with a center line and ±1.0 clip guides. */
    static void waveform(double[] x, File out) throws IOException {
        int w = 1200, h = 320;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x10141c));
        g.fillRect(0, 0, w, h);

        int mid = h / 2;
        // ±1.0 full-scale guides (red) and zero line (grey).
        g.setColor(new Color(0x40202a));
        g.drawLine(0, 1, w, 1);
        g.drawLine(0, h - 2, w, h - 2);
        g.setColor(new Color(0x2a3340));
        g.drawLine(0, mid, w, mid);

        g.setColor(new Color(0x6fd0a0));
        int n = x.length;
        for (int px = 0; px < w; px++) {
            int a = (int) ((long) px * n / w);
            int b = (int) ((long) (px + 1) * n / w);
            if (b <= a) b = Math.min(n, a + 1);
            double lo = 1, hi = -1;
            for (int i = a; i < b && i < n; i++) {
                if (x[i] < lo) lo = x[i];
                if (x[i] > hi) hi = x[i];
            }
            int y1 = (int) (mid - hi * (mid - 2));
            int y2 = (int) (mid - lo * (mid - 2));
            g.drawLine(px, y1, px, y2);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    /** STFT magnitude spectrogram: time →, frequency ↑ (0 at bottom), magma-ish colormap. */
    static void spectrogram(double[] x, int sampleRate, File out) throws IOException {
        final int frameSize = 1024;
        final int hop = 256;
        int bins = frameSize / 2;
        if (x.length < frameSize) {
            // Too short: emit a placeholder so the file always exists.
            BufferedImage tiny = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(tiny, "png", out);
            return;
        }
        int frames = 1 + (x.length - frameSize) / hop;

        // Compute magnitudes in dB, tracking the max for normalization.
        double[][] db = new double[frames][bins];
        double maxDb = -300;
        double[] buf = new double[frameSize];
        for (int t = 0; t < frames; t++) {
            System.arraycopy(x, t * hop, buf, 0, frameSize);
            double[] frame = buf.clone();
            Fft.hann(frame);
            double[] mag = Fft.magnitudes(frame);
            for (int f = 0; f < bins; f++) {
                double d = 20 * Math.log10(mag[f] + 1e-9);
                db[t][f] = d;
                if (d > maxDb) maxDb = d;
            }
        }

        final double floorDb = maxDb - 80; // 80 dB dynamic range
        int w = Math.min(1400, frames);
        int h = bins; // 512 px tall
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < w; px++) {
            int t = (int) ((long) px * frames / w);
            for (int f = 0; f < bins; f++) {
                double norm = (db[t][f] - floorDb) / (maxDb - floorDb);
                norm = Math.max(0, Math.min(1, norm));
                img.setRGB(px, h - 1 - f, magma(norm)); // low freq at bottom
            }
        }
        ImageIO.write(img, "png", out);
    }

    /** Cheap magma-like colormap: 0 = near-black, 1 = pale yellow. */
    private static int magma(double t) {
        double r = Math.min(1, 1.6 * t);
        double g = Math.max(0, 1.4 * t - 0.4);
        double b = Math.max(0, Math.min(1, 1.2 * t < 0.5 ? 1.2 * t + 0.15 : 1.5 - 1.6 * t));
        int ri = (int) (255 * r), gi = (int) (255 * g), bi = (int) (255 * b);
        return (ri << 16) | (gi << 8) | bi;
    }
}
