package com.digitalgarden.terrarium.lwjgl3.audio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Minimal, format-robust RIFF/WAV <b>reader</b> for the audio render harness. Parses the
 * {@code fmt } and {@code data} chunks and decodes samples to normalized {@code [-1, 1]}
 * doubles, so the objective checks and plots run on exactly the samples that were written
 * to disk (i.e. exactly what the user will hear). Handles PCM integer (8/16/24/32-bit) and
 * IEEE float (32/64-bit) — whatever JSyn's {@code WaveRecorder} emits.
 */
final class WavFile {
    final int sampleRate;
    final int channels;
    /** Deinterleaved samples: {@code data[ch][frame]}, each in [-1, 1]. */
    final double[][] data;

    private WavFile(int sampleRate, int channels, double[][] data) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.data = data;
    }

    /** Frames per channel. */
    int frames() {
        return data.length == 0 ? 0 : data[0].length;
    }

    /** Downmix to a single mono channel (average of all channels). */
    double[] mono() {
        int n = frames();
        double[] m = new double[n];
        for (int ch = 0; ch < channels; ch++) {
            double[] c = data[ch];
            for (int i = 0; i < n; i++) {
                m[i] += c[i];
            }
        }
        if (channels > 1) {
            for (int i = 0; i < n; i++) {
                m[i] /= channels;
            }
        }
        return m;
    }

    static WavFile read(File file) throws IOException {
        byte[] b = Files.readAllBytes(file.toPath());
        if (b.length < 12 || !tag(b, 0).equals("RIFF") || !tag(b, 8).equals("WAVE")) {
            throw new IOException("Not a RIFF/WAVE file: " + file);
        }

        int audioFormat = 0, channels = 0, sampleRate = 0, bitsPerSample = 0;
        int dataOff = -1, dataLen = 0;

        int p = 12;
        while (p + 8 <= b.length) {
            String id = tag(b, p);
            int size = le32(b, p + 4);
            int body = p + 8;
            if (id.equals("fmt ")) {
                audioFormat = le16(b, body);
                channels = le16(b, body + 2);
                sampleRate = le32(b, body + 4);
                bitsPerSample = le16(b, body + 14);
            } else if (id.equals("data")) {
                dataOff = body;
                dataLen = Math.min(size, b.length - body);
            }
            p = body + size + (size & 1); // chunks are word-aligned
        }
        if (dataOff < 0 || channels == 0 || bitsPerSample == 0) {
            throw new IOException("Missing fmt/data chunk in " + file);
        }

        int bytesPerSample = bitsPerSample / 8;
        int frameSize = bytesPerSample * channels;
        int frames = dataLen / frameSize;
        double[][] out = new double[channels][frames];

        for (int f = 0; f < frames; f++) {
            int base = dataOff + f * frameSize;
            for (int ch = 0; ch < channels; ch++) {
                int off = base + ch * bytesPerSample;
                out[ch][f] = decode(b, off, bitsPerSample, audioFormat);
            }
        }
        return new WavFile(sampleRate, channels, out);
    }

    /** Decode one sample to [-1, 1]. audioFormat 1 = PCM int, 3 = IEEE float. */
    private static double decode(byte[] b, int off, int bits, int audioFormat) {
        if (audioFormat == 3) { // IEEE float
            if (bits == 32) {
                return Float.intBitsToFloat(le32(b, off));
            }
            return Double.longBitsToDouble(le64(b, off));
        }
        // PCM signed integer, little-endian
        switch (bits) {
            case 8: // 8-bit WAV PCM is unsigned
                return ((b[off] & 0xFF) - 128) / 128.0;
            case 16:
                return (short) le16(b, off) / 32768.0;
            case 24: {
                int v = (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | (b[off + 2] << 16);
                return v / 8388608.0;
            }
            case 32:
                return le32(b, off) / 2147483648.0;
            default:
                throw new IllegalArgumentException("Unsupported bit depth: " + bits);
        }
    }

    private static String tag(byte[] b, int off) {
        return new String(b, off, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long le64(byte[] b, int off) {
        return (le32(b, off) & 0xFFFFFFFFL) | ((long) le32(b, off + 4) << 32);
    }
}
