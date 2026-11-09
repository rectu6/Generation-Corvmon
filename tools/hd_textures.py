# HD interface textures (raids, battle HUD): drawn at 4x their GUI size with
# signed distance fields, so every shape is anti-aliased. Our own art.
# Usage: python tools/hd_textures.py (writes into the mod's resources).
import math, os, struct, zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "resources",
                   "assets", "generations_core", "textures", "gui", "hd")
os.makedirs(OUT, exist_ok=True)


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def save(name, w, h, pixels):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        row = pixels[y * w:(y + 1) * w]
        for r, g, b, a in row:
            raw += bytes((int(clamp(r, 0, 255) + 0.5), int(clamp(g, 0, 255) + 0.5), int(clamp(b, 0, 255) + 0.5), int(clamp(a) * 255 + 0.5)))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(os.path.join(OUT, name), "wb").write(png)
    print(name, w, h)


def cov(d):
    return clamp(0.5 - d)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def over(dst, src):
    sa, da = src[3], dst[3]
    oa = sa + da * (1 - sa)
    if oa <= 0:
        return (0.0, 0.0, 0.0, 0.0)
    return tuple((src[i] * sa + dst[i] * da * (1 - sa)) / oa for i in range(3)) + (oa,)


def sd_round_rect(px, py, cx, cy, hw, hh, r):
    qx = abs(px - cx) - hw + r
    qy = abs(py - cy) - hh + r
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r


def sd_circle(px, py, cx, cy, r):
    return math.hypot(px - cx, py - cy) - r


def sd_segment(px, py, ax, ay, bx, by, r):
    pax, pay, bax, bay = px - ax, py - ay, bx - ax, by - ay
    h = clamp((pax * bax + pay * bay) / (bax * bax + bay * bay))
    return math.hypot(pax - bax * h, pay - bay * h) - r


def sd_polygon(px, py, pts):
    d = (px - pts[0][0]) ** 2 + (py - pts[0][1]) ** 2
    s = 1.0
    j = len(pts) - 1
    for i in range(len(pts)):
        ex, ey = pts[j][0] - pts[i][0], pts[j][1] - pts[i][1]
        wx, wy = px - pts[i][0], py - pts[i][1]
        h = clamp((wx * ex + wy * ey) / (ex * ex + ey * ey))
        bx, by = wx - ex * h, wy - ey * h
        d = min(d, bx * bx + by * by)
        c1, c2, c3 = py >= pts[i][1], py < pts[j][1], ex * wy > ey * wx
        if (c1 and c2 and c3) or (not c1 and not c2 and not c3):
            s = -s
        j = i
    return s * math.sqrt(d)


def star_points(cx, cy, outer, inner, n=5, rot=-math.pi / 2):
    pts = []
    for i in range(n * 2):
        a = rot + math.pi * i / n
        r = outer if i % 2 == 0 else inner
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


# ---- big backgrounds -------------------------------------------------------------

def background(name, gw, gh, top_split, bottom_split):
    """Red panel on the left, light on the right, split by a smooth diagonal."""
    s = 4
    w, h = gw * s, gh * s
    radius = 16 * s
    border = 2.2 * s
    red_top, red_bottom = (236, 52, 78), (168, 18, 44)
    light_top, light_bottom = (247, 248, 250), (226, 230, 236)
    pixels = []
    # Diagonal from (top_split * w, 0) to (bottom_split * w, h).
    ax, ay, bx, by = top_split * w, 0.0, bottom_split * w, float(h)
    nx, ny = by - ay, -(bx - ax)
    nl = math.hypot(nx, ny)
    nx, ny = nx / nl, ny / nl
    for y in range(h):
        t = y / h
        red = mix(red_top, red_bottom, t)
        light = mix(light_top, light_bottom, t)
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            d = sd_round_rect(px, py, w / 2, h / 2, w / 2 - 1, h / 2 - 1, radius)
            a = cov(d)
            if a <= 0:
                pixels.append((0, 0, 0, 0))
                continue
            side = (px - ax) * nx + (py - ay) * ny  # < 0 on the left
            # Faint Dynamax stripes on the red side.
            stripe = 0.5 + 0.5 * math.sin((px + py * 0.6) / (9 * s))
            left = mix(red, (255, 120, 140), 0.07 * stripe)
            colour = mix(left, light, cov(-side))
            # White line along the diagonal.
            line = cov(abs(side) - 1.6 * s)
            colour = mix(colour, (255, 255, 255), line * 0.9)
            # Border.
            colour = mix(colour, (96, 14, 30), clamp(d + border + 0.5))
            pixels.append(colour + (a,))
    save(name, w, h, pixels)


# ---- 9-slice panels (96 x 96, corners of 32 px = 8 GUI px) -------------------------

def panel(name, fill, border_colour, border_width=3, alpha=1.0, border_alpha=1.0):
    n = 96
    r = 30
    pixels = []
    for y in range(n):
        for x in range(n):
            d = sd_round_rect(x + 0.5, y + 0.5, n / 2, n / 2, n / 2 - 1, n / 2 - 1, r)
            a = cov(d)
            if a <= 0:
                pixels.append((0, 0, 0, 0))
                continue
            t = clamp(d + border_width + 0.5)
            colour = mix(fill, border_colour, t)
            al = alpha + (border_alpha - alpha) * t
            pixels.append(colour + (a * al,))
    save(name, n, n, pixels)


# ---- bars (64 x 16, rounded ends of 8 px) --------------------------------------------

def bar(name, top, bottom):
    w, h = 64, 16
    pixels = []
    for y in range(h):
        for x in range(w):
            d = sd_round_rect(x + 0.5, y + 0.5, w / 2, h / 2, w / 2 - 0.5, h / 2 - 0.5, h / 2 - 0.5)
            colour = mix(top, bottom, y / h)
            pixels.append(colour + (cov(d),))
    save(name, w, h, pixels)


# ---- icons ---------------------------------------------------------------------

def shape(name, size, fn):
    pixels = []
    for y in range(size):
        for x in range(size):
            pixels.append(fn(x + 0.5, y + 0.5))
    save(name, size, size, pixels)


def star_icon(px, py):
    d = sd_polygon(px, py, star_points(32, 34, 29, 12.5))
    # Rounded star: soften with a small offset.
    d -= 1.0
    shade = mix((255, 255, 255), (232, 232, 232), clamp((py - 8) / 50))
    base = (60, 40, 10, cov(d - 2.5) * 0.55)
    return over(base, shade + (cov(d),))


def glow(px, py):
    r = math.hypot(px - 64, py - 64) / 64
    a = clamp(1 - r) ** 2
    return (255, 255, 255, a)


def badge(colour, glyph):
    dark = tuple(c * 0.7 for c in colour)

    def fn(px, py):
        d = sd_circle(px, py, 32, 32, 30)
        base = (0, 0, 0, 0)
        if cov(d) > 0:
            fill = mix(colour, dark, py / 64)
            base = mix(fill, (255, 255, 255), cov(d + 3) * 0 + clamp(d + 3.5) * 0.8) + (cov(d),)
        g = glyph(px, py)
        if g is not None:
            base = over(base, (255, 255, 255, cov(g)))
        return base
    return fn


def sun(px, py):
    core = sd_circle(px, py, 32, 32, 10)
    rays = 99
    for i in range(8):
        a = math.pi * 2 * i / 8
        rays = min(rays, sd_segment(px, py, 32 + math.cos(a) * 15, 32 + math.sin(a) * 15, 32 + math.cos(a) * 22, 32 + math.sin(a) * 22, 2.4))
    return min(core, rays)


def rain(px, py):
    def drop(cx, cy, s):
        circle = sd_circle(px, py, cx, cy + 4 * s, 6 * s)
        tip = sd_polygon(px, py, [(cx, cy - 8 * s), (cx + 5.6 * s, cy + 2.5 * s), (cx - 5.6 * s, cy + 2.5 * s)])
        return min(circle, tip)
    return min(drop(24, 28, 1.0), drop(40, 36, 0.85))


def sand(px, py):
    lines = min(sd_segment(px, py, 15, 22, 44, 22, 2.4), sd_segment(px, py, 20, 32, 49, 32, 2.4), sd_segment(px, py, 13, 42, 38, 42, 2.4))
    grains = min(sd_circle(px, py, 48, 22, 2.6), sd_circle(px, py, 44, 42, 2.6))
    return min(lines, grains)


def hail(px, py):
    d = 99
    for i in range(3):
        a = math.pi * i / 3
        d = min(d, sd_segment(px, py, 32 - math.cos(a) * 18, 32 - math.sin(a) * 18, 32 + math.cos(a) * 18, 32 + math.sin(a) * 18, 2.4))
    for i in range(6):
        a = math.pi * i / 3
        tx, ty = 32 + math.cos(a) * 12, 32 + math.sin(a) * 12
        for side in (-0.6, 0.6):
            d = min(d, sd_segment(px, py, tx, ty, tx + math.cos(a + side) * 6, ty + math.sin(a + side) * 6, 1.8))
    return d


def wind(px, py):
    a = abs(sd_circle(px, py, 30, 32, 14)) - 2.4
    a = max(a, -(py - 30)) if px < 30 else a
    b = sd_segment(px, py, 14, 24, 40, 24, 2.4)
    c = sd_segment(px, py, 20, 40, 46, 40, 2.4)
    return min(b, c, sd_segment(px, py, 26, 32, 50, 32, 2.4))


def bolt(px, py):
    return sd_polygon(px, py, [(36, 12), (20, 35), (31, 35), (26, 52), (44, 27), (33, 27), (38, 12)])


def leaf(px, py):
    a = sd_circle(px, py, 24, 40, 18)
    b = sd_circle(px, py, 40, 24, 18)
    body = max(a, b)
    vein = sd_segment(px, py, 18, 46, 42, 22, 1.4)
    stem = sd_segment(px, py, 14, 50, 20, 44, 2.2)
    return min(max(body, -vein), stem)


def cloud(px, py):
    return min(sd_circle(px, py, 24, 36, 9), sd_circle(px, py, 34, 29, 11), sd_circle(px, py, 44, 37, 8),
               sd_round_rect(px, py, 33, 40, 14, 5, 4))


def eye(px, py):
    outer = max(sd_circle(px, py, 32, 44, 22), sd_circle(px, py, 32, 20, 22))
    ring = abs(outer) - 2.2
    pupil = sd_circle(px, py, 32, 32, 5.5)
    return min(ring, pupil)


def gender(colour, male):
    def fn(px, py):
        if male:
            ring = abs(sd_circle(px, py, 21, 28, 10)) - 3.2
            arrow = min(sd_segment(px, py, 28, 21, 40, 9, 3.2), sd_segment(px, py, 31, 9, 40, 9, 3.2), sd_segment(px, py, 40, 9, 40, 18, 3.2))
            d = min(ring, arrow)
        else:
            ring = abs(sd_circle(px, py, 24, 18, 10)) - 3.2
            cross = min(sd_segment(px, py, 24, 28, 24, 42, 3.2), sd_segment(px, py, 17, 36, 31, 36, 3.2))
            d = min(ring, cross)
        outline = (255, 255, 255, cov(d - 2.4))
        return over(outline, colour + (cov(d),))
    return fn


def ball_top(px, py):
    """Upper half of a Poké Ball, white: tinted with the ball's colour."""
    d = max(sd_circle(px, py, 64, 64, 58), py - 64)
    shade = mix((255, 255, 255), (205, 205, 205), clamp((py - 6) / 58))
    highlight = cov(sd_circle(px, py, 44, 30, 12)) * 0.35
    return mix(shade, (255, 255, 255), highlight) + (cov(d),)


def ball_base(px, py):
    """Lower half, black band, button and outline, drawn over the tinted top."""
    circle = sd_circle(px, py, 64, 64, 58)
    out = (0.0, 0.0, 0.0, 0.0)
    bottom = max(circle, 64 - py)
    out = over(out, mix((250, 250, 250), (200, 204, 210), clamp((py - 64) / 58)) + (cov(bottom),))
    band = max(circle, abs(py - 64) - 6)
    out = over(out, (24, 24, 28, cov(band)))
    ring = sd_circle(px, py, 64, 64, 17)
    out = over(out, (24, 24, 28, cov(ring)))
    button = sd_circle(px, py, 64, 64, 11)
    out = over(out, mix((255, 255, 255), (215, 215, 220), clamp((py - 53) / 22)) + (cov(button),))
    edge = abs(circle) - 3
    out = over(out, (24, 24, 28, cov(edge)))
    return out


def sparkle(px, py):
    d = sd_polygon(px, py, star_points(16, 16, 15, 3.5, n=4, rot=0))
    return (255, 255, 255, cov(d))


def ring(px, py):
    """Portrait frame: dark disc with a white rim."""
    d = sd_circle(px, py, 32, 32, 30)
    fill = mix((58, 58, 72), (28, 28, 36), py / 64)
    colour = mix(fill, (255, 255, 255), cov(d + 3.5))
    return colour + (cov(d),)


if __name__ == "__main__":
    background("lobby_bg.png", 440, 248, 0.55, 0.43)
    background("result_bg.png", 380, 230, 0.48, 0.38)
    panel("panel_light.png", (250, 251, 253), (196, 202, 212))
    panel("panel_dark.png", (18, 16, 26), (255, 255, 255), 3, 0.82, 0.22)
    panel("panel_red.png", (214, 34, 60), (255, 150, 170), 3)
    panel("panel_outline.png", (255, 255, 255), (255, 255, 255), 4, 0.0, 1.0)
    bar("bar.png", (255, 255, 255), (214, 214, 214))
    shape("star.png", 64, star_icon)
    shape("glow.png", 128, glow)
    shape("sparkle.png", 32, sparkle)
    shape("portrait_ring.png", 64, ring)
    shape("gender_male.png", 48, gender((64, 136, 238), True))
    shape("gender_female.png", 48, gender((238, 84, 136), False))
    shape("weather_sun.png", 64, badge((244, 150, 30), sun))
    shape("weather_harsh_sun.png", 64, badge((220, 60, 30), sun))
    shape("weather_rain.png", 64, badge((52, 120, 220), rain))
    shape("weather_heavy_rain.png", 64, badge((30, 60, 150), rain))
    shape("weather_sand.png", 64, badge((196, 156, 84), sand))
    shape("weather_hail.png", 64, badge((96, 180, 230), hail))
    shape("weather_wind.png", 64, badge((60, 170, 160), wind))
    shape("terrain_electric.png", 64, badge((240, 196, 30), bolt))
    shape("terrain_grassy.png", 64, badge((70, 170, 70), leaf))
    shape("terrain_misty.png", 64, badge((230, 120, 190), cloud))
    shape("terrain_psychic.png", 64, badge((170, 70, 200), eye))
    shape("ball_top.png", 128, ball_top)
    shape("ball_base.png", 128, ball_base)
