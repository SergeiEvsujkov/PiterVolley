# -*- coding: utf-8 -*-
"""Launcher icon generator for PiterVolley: volleyball on brand blue."""
import os
from PIL import Image, ImageDraw

OUT = r"C:\Users\evsuj\Documents\deepseek-harness\default-workspace\pv-app\app\src\main\res"
S = 1024
BG1 = (27, 79, 156)
BG2 = (12, 46, 99)
SEAM = (24, 71, 140)
SS = 3


def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def gradient(size):
    img = Image.new("RGB", (size, size), BG1)
    d = ImageDraw.Draw(img)
    for y in range(size):
        d.line([(0, y), (size, y)], fill=lerp(BG1, BG2, y / size))
    return img


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def bezier(p0, p1, p2, n=120):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y))
    return pts


def draw_ball(img, cx, cy, r, seam=SEAM):
    import math
    size = int(r * 2) + 4
    ball = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(ball)
    d.ellipse([2, 2, size - 3, size - 3], fill=(255, 255, 255, 255))

    seams = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ds = ImageDraw.Draw(seams)
    w = max(2, int(r * 0.085))
    col = seam + (255,)

    # три дуги, дающие классический рисунок волейбольного мяча
    for k in range(3):
        ang = math.radians(90 + 120 * k)
        ox = size / 2 + math.cos(ang) * size * 0.50
        oy = size / 2 + math.sin(ang) * size * 0.50
        rr = size * 0.60
        ds.ellipse([ox - rr, oy - rr, ox + rr, oy + rr], outline=col, width=w)

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([2, 2, size - 3, size - 3], fill=255)
    alpha = seams.getchannel("A").point(lambda v: 255 if v > 60 else 0)
    seams.putalpha(Image.composite(alpha, Image.new("L", (size, size), 0), mask))
    ball.alpha_composite(seams)

    img.alpha_composite(ball, (int(cx - r), int(cy - r)))


def legacy(size, mask_kind):
    big = S * SS
    base = gradient(big).convert("RGBA")
    draw_ball(base, big // 2, big // 2, int(big * 0.285))
    mask = rounded_mask(big, int(big * 0.22)) if mask_kind == "rounded" else circle_mask(big)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def foreground(size):
    big = S * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_ball(img, big // 2, big // 2, int(big * 0.225))
    return img.resize((size, size), Image.LANCZOS)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for dens, px in DENSITIES.items():
    folder = os.path.join(OUT, "mipmap-" + dens)
    os.makedirs(folder, exist_ok=True)
    legacy(px, "rounded").save(os.path.join(folder, "ic_launcher.png"))
    legacy(px, "circle").save(os.path.join(folder, "ic_launcher_round.png"))
    foreground(FG[dens]).save(os.path.join(folder, "ic_launcher_foreground.png"))
    print(dens, "ok")

legacy(512, "rounded").save(r"C:\Users\evsuj\Documents\deepseek-harness\default-workspace\pv-app\icon-preview.png")
print("done")
