package com.digitalgarden.terrarium.lwjgl3.audio;

/**
 * The objective, no-ears-needed checks SOUND.md asks the harness to run on every rendered
 * WAV before the user ever listens: clipping, clicks/discontinuities, level, DC offset, and
 * a coarse spectral profile. Hard problems (clipping, clicks, silence) fail the render;
 * softer observations (DC, spectral balance) are reported so we can eyeball them.
 */
final class SignalChecks {
    // Thresholds.
    private static final double CLIP_LEVEL = 0.99;   // |sample| at/above this = clipping
    private static final double CLICK_JUMP = 0.5;    // sample-to-sample jump = discontinuity
    private static final double SILENT_PEAK = 1e-4;  // below this peak = effectively silent
    private static final double DC_LIMIT = 0.02;     // |mean| above this = DC offset warning

    boolean pass = true;
    final StringBuilder report = new StringBuilder();

    static SignalChecks run(double[] x, int sampleRate) {
        SignalChecks c = new SignalChecks();
        int n = x.length;

        // Peak, RMS, DC, clipping, clicks in one pass.
        double peak = 0, sumSq = 0, sum = 0, maxJump = 0;
        int clipped = 0;
        int clickAt = -1;
        for (int i = 0; i < n; i++) {
            double s = x[i];
            double a = Math.abs(s);
            if (a > peak) peak = a;
            sumSq += s * s;
            sum += s;
            if (a >= CLIP_LEVEL) clipped++;
            if (i > 0) {
                double jump = Math.abs(s - x[i - 1]);
                if (jump > maxJump) maxJump = jump;
                if (jump > CLICK_JUMP && clickAt < 0) clickAt = i;
            }
        }
        double rms = Math.sqrt(sumSq / Math.max(1, n));
        double dc = sum / Math.max(1, n);

        c.line("duration", String.format("%.3f s (%d frames @ %d Hz)",
                n / (double) sampleRate, n, sampleRate));

        // Clipping.
        if (clipped > 0) {
            c.fail("clipping", clipped + " samples ≥ " + CLIP_LEVEL);
        } else {
            c.ok("clipping", "none (peak " + fmt(peak) + ")");
        }

        // Level.
        if (peak < SILENT_PEAK) {
            c.fail("level", "SILENT (peak " + fmt(peak) + ")");
        } else {
            c.ok("level", "peak " + fmt(peak) + ", rms " + fmt(rms)
                    + " (" + String.format("%.1f dBFS", 20 * Math.log10(rms + 1e-12)) + ")");
        }

        // Clicks / discontinuities.
        if (clickAt >= 0) {
            c.fail("clicks", "jump " + fmt(maxJump) + " at frame " + clickAt
                    + " (t=" + String.format("%.3f s", clickAt / (double) sampleRate) + ")");
        } else {
            c.ok("clicks", "none (max jump " + fmt(maxJump) + ")");
        }

        // DC offset (warning only).
        if (Math.abs(dc) > DC_LIMIT) {
            c.warn("dc-offset", fmt(dc) + " (> " + DC_LIMIT + ")");
        } else {
            c.ok("dc-offset", fmt(dc));
        }

        // Spectral profile (informational): dominant frequency + low/mid/high energy split.
        spectral(c, x, sampleRate);

        return c;
    }

    private static void spectral(SignalChecks c, double[] x, int sampleRate) {
        int n = Fft.floorPow2(Math.min(x.length, 1 << 15)); // up to 32k-point FFT
        if (n < 2) {
            c.warn("spectral", "too short to analyze");
            return;
        }
        // Take a frame from the middle (past any attack), Hann-windowed.
        int start = Math.max(0, (x.length - n) / 2);
        double[] frame = new double[n];
        System.arraycopy(x, start, frame, 0, n);
        Fft.hann(frame);
        double[] mag = Fft.magnitudes(frame);

        double binHz = sampleRate / (double) n;
        int peakBin = 1;
        double low = 0, mid = 0, high = 0;
        for (int i = 1; i < mag.length; i++) {
            if (mag[i] > mag[peakBin]) peakBin = i;
            double f = i * binHz;
            if (f < 250) low += mag[i];
            else if (f < 2000) mid += mag[i];
            else high += mag[i];
        }
        double total = low + mid + high + 1e-12;
        c.line("spectral", String.format(
                "dominant ≈ %.0f Hz | low %.0f%% mid %.0f%% high %.0f%%",
                peakBin * binHz, 100 * low / total, 100 * mid / total, 100 * high / total));
    }

    // --- report helpers ---

    private void ok(String name, String detail)   { line("PASS  " + name, detail); }
    private void warn(String name, String detail)  { line("WARN  " + name, detail); }
    private void fail(String name, String detail)  { pass = false; line("FAIL  " + name, detail); }

    private void line(String name, String detail) {
        report.append(String.format("  %-18s %s%n", name, detail));
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }
}
