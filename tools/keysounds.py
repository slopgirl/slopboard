#!/usr/bin/env python3
"""Synthesize slopboard's keypress sound styles.

Writes app/src/main/res/raw/keysound_<style>_<kind>.wav (16-bit mono PCM) for every style
below and kind in key/delete/enter/space. Pure Python, no dependencies, seeded so the output is
reproducible. Run with `just sounds`, then rebuild the app.

To add a style: write a function taking the kind and returning a list of float samples in
[-1, 1], add it to STYLES, and add it to the keypress_sound_style arrays in
res/values/keypress-sound-styles.xml.
"""

import math
import os
import random
import struct
import wave

RATE = 44100
KINDS = ("key", "delete", "enter", "space")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


# --- building blocks -------------------------------------------------------------------------

def silence(seconds):
    return [0.0] * int(seconds * RATE)


def noise(seconds, rng):
    return [rng.uniform(-1.0, 1.0) for _ in range(int(seconds * RATE))]


def decay(samples, time_constant):
    """Exponential decay envelope; time_constant in seconds."""
    return [s * math.exp(-i / (time_constant * RATE)) for i, s in enumerate(samples)]


def attack(samples, seconds):
    """Linear fade-in, which avoids a click at the very first sample."""
    n = max(1, int(seconds * RATE))
    return [s * min(1.0, i / n) for i, s in enumerate(samples)]


def lowpass(samples, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for s in samples:
        y += a * (s - y)
        out.append(y)
    return out


def highpass(samples, cutoff):
    low = lowpass(samples, cutoff)
    return [s - l for s, l in zip(samples, low)]


def tone(freq, seconds, time_constant, phase=0.0):
    """Damped sine: a struck resonance."""
    n = int(seconds * RATE)
    return [math.sin(phase + 2 * math.pi * freq * i / RATE) * math.exp(-i / (time_constant * RATE))
            for i in range(n)]


def sweep(f0, f1, seconds, time_constant):
    """Damped sine gliding exponentially from f0 to f1."""
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        f = f0 * (f1 / f0) ** (i / n)
        phase += 2 * math.pi * f / RATE
        out.append(math.sin(phase) * math.exp(-i / (time_constant * RATE)))
    return out


def mix(*parts):
    """Sum (gain, samples, offset_seconds) parts."""
    length = max(int(off * RATE) + len(s) for _, s, off in parts)
    out = [0.0] * length
    for gain, samples, off in parts:
        start = int(off * RATE)
        for i, s in enumerate(samples):
            out[start + i] += gain * s
    return out


def normalize(samples, peak):
    top = max(abs(s) for s in samples) or 1.0
    return [s * peak / top for s in samples]


def fade_out(samples, seconds=0.004):
    n = min(len(samples), int(seconds * RATE))
    return samples[:-n] + [s * (1 - i / n) for i, s in enumerate(samples[-n:])]


# --- styles ----------------------------------------------------------------------------------
# Each returns raw samples; finish() normalizes them. Peaks differ per kind so that enter and
# space sit a little louder than letters, like on a real keyboard.

def soft(kind, rng):
    """Muted, low thump, like tapping a phone screen with a fingertip."""
    pitch = {"key": 190, "delete": 150, "enter": 130, "space": 110}[kind]
    body = tone(pitch, 0.06, 0.012)
    thud = decay(lowpass(noise(0.03, rng), 900), 0.004)
    return lowpass(mix((1.0, body, 0), (0.5, thud, 0)), 2500)


def click(kind, rng):
    """Short, crisp, high click."""
    pitch = {"key": 3200, "delete": 2600, "enter": 2200, "space": 2800}[kind]
    tick = decay(highpass(noise(0.02, rng), 2000), 0.0015)
    ping = tone(pitch, 0.02, 0.002)
    return mix((1.0, tick, 0), (0.6, ping, 0))


def typewriter(kind, rng):
    """Typebar strike on a platen; enter is the carriage return and bell."""
    def strike(weight, brightness):
        impact = decay(lowpass(noise(0.08, rng), brightness), 0.006 * weight)
        metal = mix((0.5, tone(1250, 0.08, 0.010), 0), (0.35, tone(2730, 0.08, 0.007), 0),
                    (0.2, tone(4150, 0.08, 0.005), 0))
        platen = tone(210, 0.08, 0.015 * weight)
        return mix((1.0, impact, 0), (0.6, metal, 0), (0.7, platen, 0))

    if kind == "key":
        return strike(1.0, 6000)
    if kind == "delete":
        return strike(0.7, 4500)
    if kind == "space":
        # The space bar only advances the carriage: a duller double thunk.
        return mix((1.0, strike(1.2, 2500), 0), (0.5, strike(0.8, 2000), 0.035))
    # enter: ratchet zip of the carriage, then the bell.
    zip_len = 0.22
    ratchet = []
    for i in range(int(zip_len * RATE)):
        rate = 55 + 60 * i / (zip_len * RATE)  # teeth per second, speeding up
        t = i / RATE
        pulse = math.exp(-((t * rate) % 1.0) * 18)
        ratchet.append(pulse * rng.uniform(-1, 1))
    ratchet = lowpass(ratchet, 3500)
    bell = mix((1.0, tone(2093, 0.7, 0.18), 0), (0.45, tone(4186, 0.7, 0.09), 0),
               (0.2, tone(6280, 0.7, 0.05), 0))
    return mix((0.8, strike(1.1, 4000), 0), (0.5, ratchet, 0.02), (0.55, bell, 0.2))


def mechanical(kind, rng):
    """Clicky mechanical switch: a click at the actuation point, then the bottom-out clack."""
    body_pitch = {"key": 520, "delete": 470, "enter": 360, "space": 300}[kind]
    actuate = mix((1.0, decay(highpass(noise(0.015, rng), 3000), 0.001), 0),
                  (0.5, tone(4500, 0.015, 0.0012), 0))
    bottom = mix((1.0, decay(lowpass(noise(0.06, rng), 5000), 0.004), 0),
                 (0.8, tone(body_pitch, 0.06, 0.010), 0),
                 (0.3, tone(body_pitch * 2.7, 0.06, 0.004), 0))
    parts = [(0.6, actuate, 0), (1.0, bottom, 0.012)]
    if kind == "space":
        # Stabilizer rattle on the long key.
        parts.append((0.35, decay(highpass(noise(0.05, rng), 1500), 0.008), 0.03))
    return mix(*parts)


def bubble(kind, rng):
    """Round, pitched pop."""
    f0, f1 = {"key": (500, 950), "delete": (700, 380), "enter": (420, 1100),
              "space": (330, 620)}[kind]
    pop = sweep(f0, f1, 0.07, 0.018)
    if kind == "enter":
        return mix((1.0, pop, 0), (0.8, sweep(f1, f1 * 1.5, 0.08, 0.02), 0.06))
    return pop


STYLES = {
    "soft": soft,
    "click": click,
    "typewriter": typewriter,
    "mechanical": mechanical,
    "bubble": bubble,
}

PEAK = {"key": 0.7, "delete": 0.65, "enter": 0.8, "space": 0.75}


def finish(samples, kind):
    samples = attack(samples, 0.0005)
    # Trim the inaudible tail so the files stay small.
    threshold = max(abs(s) for s in samples) * 0.001
    last = max(i for i, s in enumerate(samples) if abs(s) > threshold)
    return fade_out(normalize(samples[:last + 1], PEAK[kind]))


def write_wav(path, samples):
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767))
                               for s in samples))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for style, make in STYLES.items():
        for kind in KINDS:
            rng = random.Random(f"{style}/{kind}")
            path = os.path.join(OUT_DIR, f"keysound_{style}_{kind}.wav")
            write_wav(path, finish(make(kind, rng), kind))
            print(f"{os.path.relpath(path)}  {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
