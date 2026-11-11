"""Generates Apex Ludo's launcher icons and all synthesized audio (no third-party assets)."""
import math, os, struct, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = "app/src/main/res"
SR = 22050

# ---------------------------------------------------------------- icons
def icon(size):
    s = size * 4  # supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = int(s * 0.22)
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=(11, 14, 23, 255))
    # four colour quadrants
    pad = int(s * 0.13)
    gap = int(s * 0.035)
    cell = (s - 2 * pad - gap) // 2
    cols = [(232, 62, 74), (58, 198, 124), (245, 190, 60), (72, 138, 245)]
    for i, c in enumerate(cols):
        x = pad + (i % 2) * (cell + gap)
        y = pad + (i // 2) * (cell + gap)
        d.rounded_rectangle([x, y, x + cell, y + cell], radius=int(cell * 0.28), fill=c + (255,))
    # centre dice
    cs = int(s * 0.30)
    cx = (s - cs) // 2
    d.rounded_rectangle([cx, cx, cx + cs, cx + cs], radius=int(cs * 0.24),
                        fill=(245, 248, 255, 255))
    pr = int(cs * 0.09)
    for (px, py) in [(0.28, 0.28), (0.72, 0.28), (0.5, 0.5), (0.28, 0.72), (0.72, 0.72)]:
        x = cx + int(cs * px); y = cx + int(cs * py)
        d.ellipse([x - pr, y - pr, x + pr, y + pr], fill=(20, 26, 40, 255))
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    return img.resize((size, size), Image.LANCZOS)

for folder, size in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                     ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
    p = os.path.join(ROOT, folder)
    os.makedirs(p, exist_ok=True)
    icon(size).save(os.path.join(p, "ic_launcher.png"))

# ---------------------------------------------------------------- audio helpers
def env(n, a=0.004, d=0.12, sustain=0.0):
    at = max(1, int(SR * a)); dt = max(1, int(SR * d))
    e = np.ones(n) * sustain
    e[:at] = np.linspace(0, 1, at)
    tail = min(dt, n - at) if n > at else 0
    if tail > 0:
        e[at:at + tail] *= np.linspace(1, 0, tail)
    if at + tail < n:
        e[at + tail:] = 0
    return e

def tone(freq, dur, amp=0.5, harm=3, attack=0.004, decay=None, sweep=None):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = freq if sweep is None else np.linspace(freq, sweep, n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    sig = np.zeros(n)
    for k in range(1, harm + 1):
        sig += np.sin(ph * k) / (k ** 1.4)
    sig /= max(1e-6, np.max(np.abs(sig)))
    return sig * env(n, attack, decay or dur * 0.9) * amp

def noise(dur, amp=0.4, lowpass=0.35, attack=0.001, decay=None):
    n = int(SR * dur)
    x = np.random.default_rng(7).normal(0, 1, n)
    # simple one-pole lowpass
    y = np.zeros(n); prev = 0.0
    for i in range(n):
        prev = prev + lowpass * (x[i] - prev)
        y[i] = prev
    y /= max(1e-6, np.max(np.abs(y)))
    return y * env(n, attack, decay or dur * 0.8) * amp

def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out

def silence(dur):
    return np.zeros(int(SR * dur))

def save(name, sig, amp=1.0):
    sig = np.asarray(sig, dtype=np.float64)
    peak = np.max(np.abs(sig)) or 1.0
    sig = sig / peak * 0.85 * amp
    data = (sig * 32767).astype("<i2")
    p = os.path.join(ROOT, "raw", name + ".wav")
    with wave.open(p, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(data.tobytes())
    return os.path.getsize(p)

sizes = {}
sizes["sfx_tap"] = save("sfx_tap", tone(1650, 0.055, 0.6, harm=2, decay=0.05))
sizes["sfx_open"] = save("sfx_open", mix(tone(520, 0.16, 0.4, harm=3, sweep=1150),
                                         noise(0.14, 0.12, 0.5)))
sizes["sfx_close"] = save("sfx_close", mix(tone(900, 0.14, 0.35, harm=3, sweep=430),
                                           noise(0.1, 0.1, 0.4)))
sizes["sfx_move"] = save("sfx_move", mix(tone(760, 0.07, 0.35, harm=2, decay=0.06),
                                         noise(0.05, 0.08, 0.6)))
sizes["sfx_dice_roll"] = save("sfx_dice_roll", mix(
    noise(0.42, 0.55, 0.55),
    tone(180, 0.4, 0.15, harm=2, decay=0.35)))
sizes["sfx_dice_land"] = save("sfx_dice_land", mix(
    tone(150, 0.22, 0.75, harm=3, decay=0.2),
    noise(0.12, 0.4, 0.35),
    tone(2200, 0.05, 0.25, harm=1, decay=0.04)))
sizes["sfx_capture"] = save("sfx_capture", mix(
    noise(0.55, 0.5, 0.25),
    tone(420, 0.5, 0.5, harm=4, sweep=90, decay=0.45),
    tone(120, 0.4, 0.6, harm=2, decay=0.35)))
sizes["sfx_home"] = save("sfx_home", mix(
    tone(523.25, 0.5, 0.4, harm=3), tone(659.25, 0.5, 0.35, harm=3),
    tone(783.99, 0.6, 0.35, harm=3), tone(1046.5, 0.45, 0.25, harm=2)))
sizes["sfx_tick"] = save("sfx_tick", tone(1200, 0.035, 0.4, harm=1, decay=0.03))
sizes["sfx_six"] = save("sfx_six", mix(
    tone(880, 0.12, 0.4, harm=2), silence(0.04), tone(1320, 0.18, 0.4, harm=2)))
sizes["sfx_win"] = save("sfx_win", mix(
    tone(523.25, 0.18, 0.4, harm=3), silence(0.02),
    tone(659.25, 0.18, 0.4, harm=3), silence(0.02),
    tone(783.99, 0.18, 0.4, harm=3), silence(0.02),
    tone(1046.5, 0.5, 0.5, harm=4),
    tone(261.63, 0.9, 0.35, harm=2)))

# BGM: soft evolving pad, 12 s seamless-ish loop
def bgm():
    dur = 12.0
    n = int(SR * dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    chords = [(130.81, 196.00, 261.63), (146.83, 220.00, 293.66),
              (174.61, 261.63, 349.23), (130.81, 196.00, 261.63)]
    seg = n // len(chords)
    for i, ch in enumerate(chords):
        a, b = i * seg, (i + 1) * seg if i < len(chords) - 1 else n
        tt = np.arange(b - a) / SR
        local = np.zeros(b - a)
        for j, f in enumerate(ch):
            local += np.sin(2 * np.pi * f * tt + j) * 0.33
            local += np.sin(2 * np.pi * f * 2.002 * tt) * 0.08
        # slow tremolo + soft edges
        local *= 0.55 + 0.45 * np.sin(2 * np.pi * 0.12 * tt)
        fade = int(SR * 0.35)
        local[:fade] *= np.linspace(0, 1, fade)
        local[-fade:] *= np.linspace(1, 0, fade)
        out[a:b] += local
    return out * 0.22

sizes["bgm_loop"] = save("bgm_loop", bgm())

# ---------------------------------------------------------------- verify
print("audio:", {k: round(v / 1024, 1) for k, v in sizes.items()})
print("total KB:", round(sum(sizes.values()) / 1024, 1))
for folder in ["mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"]:
    f = os.path.join(ROOT, folder, "ic_launcher.png")
    print(folder, Image.open(f).size, round(os.path.getsize(f) / 1024, 1), "KB")
