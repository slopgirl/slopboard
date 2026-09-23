/*
 * Copyright (C) 2026 slopgirl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Synthesizes the keypress sound styles as 16-bit mono PCM. Every style is a few noise bursts,
 * filters and damped sines; {@link Config} scales their frequencies (pitch), durations (length)
 * and filter cutoffs (tone). Seeded per style and key kind, so a config always sounds the same.
 */
public final class KeySoundSynth {
    public static final int RATE = 44100;

    public static final int KIND_KEY = 0;
    public static final int KIND_DELETE = 1;
    public static final int KIND_ENTER = 2;
    public static final int KIND_SPACE = 3;
    public static final int KIND_COUNT = 4;
    private static final String[] KIND_NAMES = { "key", "delete", "enter", "space" };
    // Enter and space sit a little louder than letters, like on a real keyboard.
    private static final float[] PEAKS = { 0.7f, 0.65f, 0.8f, 0.75f };
    // Each tone step scales the filter cutoffs by this much.
    private static final double TONE_STEP = 1.15;

    /** Everything the rendered sound depends on. Immutable. */
    public static final class Config {
        public final String mStyle;
        public final int mPitchSemitones;
        public final int mLengthPercent;
        public final int mTone;

        public Config(final String style, final int pitchSemitones, final int lengthPercent,
                final int tone) {
            mStyle = style;
            mPitchSemitones = pitchSemitones;
            mLengthPercent = lengthPercent;
            mTone = tone;
        }

        /** Distinct per config; used for cache file names. */
        public String getKey() {
            return mStyle + "_p" + mPitchSemitones + "_l" + mLengthPercent + "_t" + mTone;
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Config && getKey().equals(((Config) o).getKey());
        }

        @Override
        public int hashCode() {
            return getKey().hashCode();
        }
    }

    private final double mPitch;
    private final double mLength;
    private final double mTone;
    private final Random mRandom;

    private KeySoundSynth(final Config config, final int kind) {
        mPitch = Math.pow(2, config.mPitchSemitones / 12.0);
        mLength = config.mLengthPercent / 100.0;
        mTone = Math.pow(TONE_STEP, config.mTone);
        mRandom = new Random((config.mStyle + "/" + KIND_NAMES[kind]).hashCode());
    }

    public static boolean hasStyle(final String style) {
        switch (style) {
        case "soft":
        case "click":
        case "typewriter":
        case "mechanical":
        case "bubble":
            return true;
        default:
            return false;
        }
    }

    /** Renders one key kind of a style; null if the style isn't synthesized. */
    public static short[] render(final Config config, final int kind) {
        final KeySoundSynth synth = new KeySoundSynth(config, kind);
        final float[] samples;
        switch (config.mStyle) {
        case "soft": samples = synth.soft(kind); break;
        case "click": samples = synth.click(kind); break;
        case "typewriter": samples = synth.typewriter(kind); break;
        case "mechanical": samples = synth.mechanical(kind); break;
        case "bubble": samples = synth.bubble(kind); break;
        default: return null;
        }
        return finish(samples, PEAKS[kind]);
    }

    /** Renders one key kind and writes it as a WAV file. */
    public static void renderToWav(final Config config, final int kind, final File file)
            throws IOException {
        final short[] pcm = render(config, kind);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length * 2);
        writeAscii(out, "RIFF");
        writeInt(out, 36 + pcm.length * 2);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1); // PCM
        writeShort(out, 1); // mono
        writeInt(out, RATE);
        writeInt(out, RATE * 2); // byte rate
        writeShort(out, 2); // block align
        writeShort(out, 16); // bits per sample
        writeAscii(out, "data");
        writeInt(out, pcm.length * 2);
        for (final short s : pcm) {
            writeShort(out, s);
        }
        try (FileOutputStream fileOut = new FileOutputStream(file)) {
            out.writeTo(fileOut);
        }
    }

    // --- styles ---------------------------------------------------------------------------

    /** Muted, low thump, like tapping a phone screen with a fingertip. */
    private float[] soft(final int kind) {
        final double pitch = pick(kind, 190, 150, 130, 110);
        final float[] body = tone(pitch, 0.06, 0.012);
        final float[] thud = decay(lowpass(noise(0.03), 900), 0.004);
        return lowpass(mix(part(1, body, 0), part(0.5, thud, 0)), 2500);
    }

    /** Short, crisp, high click. */
    private float[] click(final int kind) {
        final double pitch = pick(kind, 3200, 2600, 2200, 2800);
        final float[] tick = decay(highpass(noise(0.02), 2000), 0.0015);
        final float[] ping = tone(pitch, 0.02, 0.002);
        return mix(part(1, tick, 0), part(0.6, ping, 0));
    }

    /** Typebar strike on a platen; enter is the carriage return and bell. */
    private float[] typewriter(final int kind) {
        switch (kind) {
        case KIND_KEY:
            return strike(1.0, 6000);
        case KIND_DELETE:
            return strike(0.7, 4500);
        case KIND_SPACE:
            // The space bar only advances the carriage: a duller double thunk.
            return mix(part(1, strike(1.2, 2500), 0), part(0.5, strike(0.8, 2000), 0.035));
        default:
            // Enter: ratchet zip of the carriage, then the bell.
            final double zipLength = 0.22 * mLength;
            final float[] ratchet = new float[samples(zipLength)];
            for (int i = 0; i < ratchet.length; i++) {
                // Teeth per second, speeding up.
                final double teethRate = (55 + 60.0 * i / ratchet.length) / mLength;
                final double t = (double) i / RATE;
                final double pulse = Math.exp(-((t * teethRate) % 1.0) * 18);
                ratchet[i] = (float) (pulse * (mRandom.nextDouble() * 2 - 1));
            }
            final float[] bell = mix(part(1, tone(2093, 0.7, 0.18), 0),
                    part(0.45, tone(4186, 0.7, 0.09), 0), part(0.2, tone(6280, 0.7, 0.05), 0));
            return mix(part(0.8, strike(1.1, 4000), 0), part(0.5, lowpass(ratchet, 3500), 0.02),
                    part(0.55, bell, 0.2));
        }
    }

    private float[] strike(final double weight, final double brightness) {
        final float[] impact = decay(lowpass(noise(0.08), brightness), 0.006 * weight);
        final float[] metal = mix(part(0.5, tone(1250, 0.08, 0.010), 0),
                part(0.35, tone(2730, 0.08, 0.007), 0), part(0.2, tone(4150, 0.08, 0.005), 0));
        final float[] platen = tone(210, 0.08, 0.015 * weight);
        return mix(part(1, impact, 0), part(0.6, metal, 0), part(0.7, platen, 0));
    }

    /** Clicky mechanical switch: a click at the actuation point, then the bottom-out clack. */
    private float[] mechanical(final int kind) {
        final double bodyPitch = pick(kind, 520, 470, 360, 300);
        final float[] actuate = mix(part(1, decay(highpass(noise(0.015), 3000), 0.001), 0),
                part(0.5, tone(4500, 0.015, 0.0012), 0));
        final float[] bottom = mix(part(1, decay(lowpass(noise(0.06), 5000), 0.004), 0),
                part(0.8, tone(bodyPitch, 0.06, 0.010), 0),
                part(0.3, tone(bodyPitch * 2.7, 0.06, 0.004), 0));
        if (kind == KIND_SPACE) {
            // Stabilizer rattle on the long key.
            final float[] rattle = decay(highpass(noise(0.05), 1500), 0.008);
            return mix(part(0.6, actuate, 0), part(1, bottom, 0.012), part(0.35, rattle, 0.03));
        }
        return mix(part(0.6, actuate, 0), part(1, bottom, 0.012));
    }

    /** Round, pitched pop. */
    private float[] bubble(final int kind) {
        final double f0 = pick(kind, 500, 700, 420, 330);
        final double f1 = pick(kind, 950, 380, 1100, 620);
        final float[] pop = sweep(f0, f1, 0.07, 0.018);
        if (kind == KIND_ENTER) {
            return mix(part(1, pop, 0), part(0.8, sweep(f1, f1 * 1.5, 0.08, 0.02), 0.06));
        }
        return pop;
    }

    // --- building blocks --------------------------------------------------------------------
    // Frequencies are scaled by the pitch, durations and time constants by the length, and
    // filter cutoffs by pitch and tone.

    private static double pick(final int kind, final double key, final double delete,
            final double enter, final double space) {
        switch (kind) {
        case KIND_DELETE: return delete;
        case KIND_ENTER: return enter;
        case KIND_SPACE: return space;
        default: return key;
        }
    }

    private int samples(final double seconds) {
        return Math.max(1, (int) (seconds * RATE));
    }

    private float[] noise(final double seconds) {
        final float[] out = new float[samples(seconds * mLength)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (mRandom.nextDouble() * 2 - 1);
        }
        return out;
    }

    private float[] decay(final float[] in, final double timeConstant) {
        final double tc = timeConstant * mLength * RATE;
        final float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (float) (in[i] * Math.exp(-i / tc));
        }
        return out;
    }

    private double cutoff(final double hz) {
        return Math.min(hz * mPitch * mTone, RATE * 0.45);
    }

    private float[] lowpass(final float[] in, final double cutoffHz) {
        final double a = 1 - Math.exp(-2 * Math.PI * cutoff(cutoffHz) / RATE);
        final float[] out = new float[in.length];
        double y = 0;
        for (int i = 0; i < in.length; i++) {
            y += a * (in[i] - y);
            out[i] = (float) y;
        }
        return out;
    }

    private float[] highpass(final float[] in, final double cutoffHz) {
        final float[] low = lowpass(in, cutoffHz);
        final float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] - low[i];
        }
        return out;
    }

    /** Damped sine: a struck resonance. */
    private float[] tone(final double freq, final double seconds, final double timeConstant) {
        final double f = Math.min(freq * mPitch, RATE * 0.45);
        final double tc = timeConstant * mLength * RATE;
        final float[] out = new float[samples(seconds * mLength)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (Math.sin(2 * Math.PI * f * i / RATE) * Math.exp(-i / tc));
        }
        return out;
    }

    /** Damped sine gliding exponentially from f0 to f1. */
    private float[] sweep(final double f0, final double f1, final double seconds,
            final double timeConstant) {
        final double from = Math.min(f0 * mPitch, RATE * 0.45);
        final double to = Math.min(f1 * mPitch, RATE * 0.45);
        final double tc = timeConstant * mLength * RATE;
        final float[] out = new float[samples(seconds * mLength)];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            final double f = from * Math.pow(to / from, (double) i / out.length);
            phase += 2 * Math.PI * f / RATE;
            out[i] = (float) (Math.sin(phase) * Math.exp(-i / tc));
        }
        return out;
    }

    private static final class Part {
        final double mGain;
        final float[] mSamples;
        final double mOffsetSeconds;

        Part(final double gain, final float[] samples, final double offsetSeconds) {
            mGain = gain;
            mSamples = samples;
            mOffsetSeconds = offsetSeconds;
        }
    }

    private Part part(final double gain, final float[] samples, final double offsetSeconds) {
        return new Part(gain, samples, offsetSeconds * mLength);
    }

    private static float[] mix(final Part... parts) {
        int length = 0;
        for (final Part part : parts) {
            length = Math.max(length, (int) (part.mOffsetSeconds * RATE) + part.mSamples.length);
        }
        final float[] out = new float[length];
        for (final Part part : parts) {
            final int start = (int) (part.mOffsetSeconds * RATE);
            for (int i = 0; i < part.mSamples.length; i++) {
                out[start + i] += (float) (part.mGain * part.mSamples[i]);
            }
        }
        return out;
    }

    /** Fade in, trim the inaudible tail, normalize to the peak and fade out. */
    private static short[] finish(final float[] in, final float peak) {
        final int attack = Math.max(1, (int) (0.0005 * RATE));
        float top = 0;
        for (int i = 0; i < in.length; i++) {
            in[i] *= Math.min(1f, (float) i / attack);
            top = Math.max(top, Math.abs(in[i]));
        }
        if (top == 0) {
            return new short[1];
        }
        int last = 0;
        for (int i = 0; i < in.length; i++) {
            if (Math.abs(in[i]) > top * 0.001f) {
                last = i;
            }
        }
        final float[] trimmed = Arrays.copyOf(in, last + 1);
        // At most a quarter of the sound, so very short sounds keep their level.
        final int fade = Math.max(1, Math.min(trimmed.length / 4, (int) (0.004 * RATE)));
        final short[] out = new short[trimmed.length];
        for (int i = 0; i < trimmed.length; i++) {
            final int fromEnd = trimmed.length - 1 - i;
            final float fadeGain = fromEnd < fade ? (float) fromEnd / fade : 1f;
            final float s = trimmed[i] * peak / top * fadeGain;
            out[i] = (short) Math.round(Math.max(-1f, Math.min(1f, s)) * 32767);
        }
        return out;
    }

    private static void writeAscii(final ByteArrayOutputStream out, final String s) {
        for (int i = 0; i < s.length(); i++) {
            out.write(s.charAt(i));
        }
    }

    private static void writeInt(final ByteArrayOutputStream out, final int v) {
        out.write(v);
        out.write(v >> 8);
        out.write(v >> 16);
        out.write(v >> 24);
    }

    private static void writeShort(final ByteArrayOutputStream out, final int v) {
        out.write(v);
        out.write(v >> 8);
    }
}
