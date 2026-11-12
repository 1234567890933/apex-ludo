"""Apex Ludo 1.2 asset pass: HD board textures + two extra music beds.

Everything is generated procedurally - no third-party art or audio is used.
Run from the repository root:  python3 tools/gen_assets2.py
"""
import math, os, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = "app/src/main/res"
SR = 44100


# ----------------------------------------------------------------- textures
def fbm(shape, octaves=5, seed=1):
    rng = np.random.default_rng(seed)
    out = np.zeros(shape)
    amp = 1.0
    total = 0.0
    for o in range(octaves):
        size = 2 ** (o + 2)
        base = rng.random((size, size))
        img = Image.fromarray((base * 255).astype(np.uint8)).resize(
            (shape[1], shape[0]), Image.BICUBIC)
        out += np.asarray(img, dtype=np.float64) / 255.0 * amp
        total += amp
        amp *= 0.55
    return out / total


def tex_wood(size=1024):
    y, x = np.mgrid[0:size, 0:size] / size
    rings = np.sin((y * 46 + fbm((size, size), 4, 3) * 6.0) * math.pi) * 0.5 + 0.5
    grain = fbm((size, size), 6, 7)
    v = (rings * 0.55 + grain * 0.45)
    r = 88 + v * 62
    g = 52 + v * 44
    b = 30 + v * 28
    img = np.dstack([r, g, b]).astype(np.uint8)
    return Image.fromarray(img).filter(ImageFilter.GaussianBlur(0.6))


def tex_marble(size=1024):
    base = fbm((size, size), 6, 11)
    veins = np.abs(np.sin((base * 14 + fbm((size, size), 4, 13) * 5) * math.pi)) ** 0.35
    v = 1.0 - veins
    r = 224 - v * 70
    g = 228 - v * 68
    b = 236 - v * 62
    img = np.dstack([r, g, b]).astype(np.uint8)
    return Image.fromarray(img).filter(ImageFilter.GaussianBlur(0.8))


def tex_neon(size=1024):
    img = Image.new("RGB", (size, size), (10, 6, 20))
    d = ImageDraw.Draw(img)
    step = size // 16
    for i in range(0, size + 1, step):
        d.line([(i, 0), (i, size)], fill=(44, 16, 76), width=2)
        d.line([(0, i), (size, i)], fill=(44, 16, 76), width=2)
    glow = Image.new("RGB", (size, size), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i in range(0, size + 1, step * 4):
        gd.line([(i, 0), (i, size)], fill=(120, 30, 190), width=6)
        gd.line([(0, i), (size, i)], fill=(120, 30, 190), width=6)
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    return Image.blend(img, glow, 0.55)


def tex_paper(size=1024):
    fib = fbm((size, size), 6, 17)
    fib2 = fbm((size, size), 4, 19)
    v = fib * 0.7 + fib2 * 0.3
    r = 244 - v * 22
    g = 240 - v * 24
    b = 230 - v * 30
    img = np.dstack([r, g, b]).astype(np.uint8)
    return Image.fromarray(img).filter(ImageFilter.GaussianBlur(0.4))


# ----------------------------------------------------------------- audio
def note(freq, dur, amp=0.5, harm=4, attack=0.01, release=0.35):
    n = int(SR * dur)
    t = np.arange(n) / SR
    ph = 2 * np.pi * freq * t
    sig = np.zeros(n)
    for k in range(1, harm + 1):
        sig += np.sin(ph * k) / (k ** 1.5)
    env = np.ones(n)
    a = max(1, int(SR * attack)); r = max(1, int(SR * release))
    env[:a] = np.linspace(0, 1, a)
    env[-r:] *= np.linspace(1, 0, r)
    return sig / (np.max(np.abs(sig)) or 1) * env * amp


def pad_chord(freqs, dur, amp=0.28):
    n = int(SR * dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        detune = 1.0 + (i - 1) * 0.0016
        out += np.sin(2 * np.pi * f * detune * t + i) * 0.5
        out += np.sin(2 * np.pi * f * 2.003 * t) * 0.10
    out *= 0.5 + 0.5 * np.sin(2 * np.pi * 0.09 * t)
    a = int(SR * 0.6); r = int(SR * 0.6)
    env = np.ones(n); env[:a] = np.linspace(0, 1, a); env[-r:] *= np.linspace(1, 0, r)
    return out / (np.max(np.abs(out)) or 1) * env * amp


def kick(dur=0.22, amp=0.5):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = 120 * np.exp(-t * 18) + 45
    sig = np.sin(2 * np.pi * np.cumsum(f) / SR)
    return sig * np.exp(-t * 14) * amp


def hat(dur=0.06, amp=0.14, seed=3):
    n = int(SR * dur)
    x = np.random.default_rng(seed).normal(0, 1, n)
    x = np.diff(np.concatenate([[0], x]))
    return x / (np.max(np.abs(x)) or 1) * np.exp(-np.arange(n) / SR * 70) * amp


def stereo(sig, width=0.5, seed=5):
    """Cheap stereo widening: slightly delayed, phase-inverted copy on the right."""
    delay = int(SR * 0.012 * width)
    left = sig
    right = np.concatenate([np.zeros(delay), sig])[:len(sig)]
    return np.stack([left * 0.95, right * 0.95], axis=1)


def save_wav(name, data, amp=1.0):
    data = np.asarray(data, dtype=np.float64)
    peak = np.max(np.abs(data)) or 1.0
    data = data / peak * 0.88 * amp
    pcm = (data * 32767).astype("<i2")
    p = os.path.join(ROOT, "raw", name + ".wav")
    with wave.open(p, "wb") as w:
        w.setnchannels(2 if data.ndim == 2 else 1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    return os.path.getsize(p)


def track_menu():
    """Calm 64 s loop: four chords, arpeggio, soft kick every bar."""
    bpm = 78
    bar = 60 / bpm * 4
    chords = [(174.61, 220.00, 261.63, 329.63),
              (146.83, 185.00, 220.00, 293.66),
              (130.81, 164.81, 196.00, 261.63),
              (155.56, 196.00, 233.08, 293.66)]
    total = int(SR * bar * 4)
    out = np.zeros(total)
    for c, ch in enumerate(chords):
        start = int(SR * bar * c)
        seg = pad_chord(ch, bar * 1.05, 0.30)
        out[start:start + len(seg)] += seg[:max(0, total - start)]
        # arpeggio
        for k in range(8):
            f = ch[k % len(ch)] * (2 if k % 3 == 0 else 1)
            st = start + int(SR * bar / 8 * k)
            nt = note(f, 0.55, 0.10, 3, 0.005, 0.4)
            out[st:st + len(nt)] += nt[:max(0, total - st)]
        # gentle kick on the bar
        kk = kick(0.3, 0.30)
        out[start:start + len(kk)] += kk[:max(0, total - start)]
    return stereo(out, 0.6)


def track_game():
    """Driving 72 s loop: minor progression, bass, hats."""
    bpm = 104
    beat = 60 / bpm
    bar = beat * 4
    chords = [(110.00, 130.81, 164.81, 220.00),
              (98.00, 123.47, 146.83, 196.00),
              (87.31, 110.00, 130.81, 174.61),
              (98.00, 116.54, 146.83, 196.00)]
    bars = 8
    total = int(SR * bar * bars)
    out = np.zeros(total)
    for b in range(bars):
        ch = chords[b % len(chords)]
        start = int(SR * bar * b)
        seg = pad_chord(ch, bar * 1.02, 0.22)
        out[start:start + len(seg)] += seg[:max(0, total - start)]
        for k in range(4):
            st = start + int(SR * beat * k)
            kk = kick(0.24, 0.42)
            out[st:st + len(kk)] += kk[:max(0, total - st)]
        for k in range(8):
            st = start + int(SR * beat / 2 * k)
            hh = hat(0.05, 0.10 if k % 2 else 0.16, seed=11 + k)
            out[st:st + len(hh)] += hh[:max(0, total - st)]
        for k in range(4):
            f = ch[0] / 2
            st = start + int(SR * beat * k)
            nt = note(f, beat * 0.9, 0.22, 2, 0.006, 0.2)
            out[st:st + len(nt)] += nt[:max(0, total - st)]
        for k in range(4):
            f = ch[(k + 2) % len(ch)] * 2
            st = start + int(SR * beat * (k + 0.5))
            nt = note(f, 0.4, 0.09, 3, 0.004, 0.3)
            out[st:st + len(nt)] += nt[:max(0, total - st)]
    return stereo(out, 0.8)


if __name__ == "__main__":
    os.makedirs(os.path.join(ROOT, "raw"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "drawable-nodpi"), exist_ok=True)

    textures = {"tex_wood": tex_wood(), "tex_marble": tex_marble(),
                "tex_neon": tex_neon(), "tex_paper": tex_paper()}
    for name, img in textures.items():
        p = os.path.join(ROOT, "drawable-nodpi", name + ".png")
        img.save(p, optimize=True)
        print(name, img.size, round(os.path.getsize(p) / 1024 / 1024, 2), "MB")

    print("bgm_menu", round(save_wav("bgm_menu", track_menu()) / 1024 / 1024, 2), "MB")
    print("bgm_game", round(save_wav("bgm_game", track_game()) / 1024 / 1024, 2), "MB")
