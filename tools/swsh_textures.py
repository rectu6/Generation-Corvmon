# HD battle menu textures (4x the GUI size), drawn with signed distance fields:
# every shape is anti-aliased without supersampling. Our own art, Sword/Shield style.
import math, struct, zlib, os

# Usage: python tools/swsh_textures.py (writes into the mod's resources).

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "resources", "assets", "generations_core", "textures", "gui", "battle")
os.makedirs(OUT, exist_ok=True)


def save(name, w, h, pixels):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((clamp8(r), clamp8(g), clamp8(b), clamp8(a)))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(os.path.join(OUT, name), "wb").write(png)


def clamp8(v):
    return max(0, min(255, int(round(v))))


def cov(d):
    """Pixel coverage from a signed distance in pixels."""
    return max(0.0, min(1.0, 0.5 - d))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def over(dst, src):
    """Alpha compositing of src over dst, colours 0-255 with alpha 0-1."""
    sa = src[3]
    da = dst[3]
    out_a = sa + da * (1 - sa)
    if out_a <= 0:
        return (0, 0, 0, 0)
    rgb = tuple((src[i] * sa + dst[i] * da * (1 - sa)) / out_a for i in range(3))
    return rgb + (out_a,)


# ---- distance functions ------------------------------------------------------

def sd_round_rect(px, py, cx, cy, hw, hh, r):
    qx = abs(px - cx) - hw + r
    qy = abs(py - cy) - hh + r
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r


def sd_circle(px, py, cx, cy, r):
    return math.hypot(px - cx, py - cy) - r


def sd_segment(px, py, ax, ay, bx, by, r):
    pax, pay, bax, bay = px - ax, py - ay, bx - ax, by - ay
    h = max(0.0, min(1.0, (pax * bax + pay * bay) / (bax * bax + bay * bay)))
    return math.hypot(pax - bax * h, pay - bay * h) - r


def sd_polygon(px, py, pts):
    d = (px - pts[0][0]) ** 2 + (py - pts[0][1]) ** 2
    s = 1.0
    n = len(pts)
    j = n - 1
    for i in range(n):
        ex, ey = pts[j][0] - pts[i][0], pts[j][1] - pts[i][1]
        wx, wy = px - pts[i][0], py - pts[i][1]
        h = max(0.0, min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)))
        bx, by = wx - ex * h, wy - ey * h
        d = min(d, bx * bx + by * by)
        c1 = py >= pts[i][1]
        c2 = py < pts[j][1]
        c3 = ex * wy > ey * wx
        if (c1 and c2 and c3) or (not c1 and not c2 and not c3):
            s = -s
        j = i
    return s * math.sqrt(d)


# ---- pills -------------------------------------------------------------------

def pill(name, w, h, top, bottom, border, border_width, highlight=None):
    pixels = []
    r = h / 2 - 2
    for y in range(h):
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            d = sd_round_rect(px, py, w / 2, h / 2, w / 2 - 2, h / 2 - 2, r)
            a = cov(d)
            if a <= 0:
                pixels.append((0, 0, 0, 0))
                continue
            fill = mix(top, bottom, y / h)
            # A soft light band on the upper half.
            if highlight and y < h * 0.45:
                fill = mix(fill, highlight, 0.18 * (1 - y / (h * 0.45)))
            t = max(0.0, min(1.0, d + border_width + 0.5))
            colour = mix(fill, border, t)
            pixels.append(colour + (255 * a,))
    save(name, w, h, pixels)


# Action button (116 x 20 in the GUI).
pill("swsh_pill.png", 464, 80, (250, 251, 252), (222, 227, 233), (150, 158, 170), 3, (255, 255, 255))
pill("swsh_pill_selected.png", 464, 80, (40, 40, 46), (14, 14, 18), (240, 246, 255), 5, (90, 90, 100))
# Move button (140 x 22), grey so that it can be tinted with the type colour.
pill("swsh_move.png", 560, 88, (255, 255, 255), (190, 190, 190), (255, 255, 255), 4, (255, 255, 255))


def outline(name, w, h, colour, width):
    pixels = []
    r = h / 2 - 2
    for y in range(h):
        for x in range(w):
            d = sd_round_rect(x + 0.5, y + 0.5, w / 2, h / 2, w / 2 - 2, h / 2 - 2, r)
            ring = abs(d + width / 2) - width / 2
            # Glow fading outwards.
            glow = max(0.0, 1 - max(d, 0) / 6) * 0.35 if d > 0 else 0
            a = max(cov(ring), glow)
            pixels.append(colour + (255 * a,))
    save(name, w, h, pixels)


outline("swsh_move_selected.png", 560, 88, (255, 255, 255), 6)


# ---- icons -------------------------------------------------------------------

def icon(name, colour, glyph, size=64):
    dark = tuple(c * 0.72 for c in colour)
    pixels = []
    c = size / 2
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            d = sd_circle(px, py, c, c, c - 2)
            base = (0, 0, 0, 0)
            if cov(d) > 0:
                fill = mix(colour, dark, py / size)
                t = max(0.0, min(1.0, d + 3.5))
                base = mix(fill, (255, 255, 255), t * 0.85) + (cov(d),)
            g = glyph(px, py)
            if g is not None:
                base = over(base, (255, 255, 255, cov(g)))
            pixels.append(base[:3] + (255 * base[3],))
    save(name, size, size, pixels)


def star(px, py):
    pts = []
    for i in range(16):
        a = math.pi * 2 * i / 16 - math.pi / 2
        rad = 19 if i % 2 == 0 else 8.5
        pts.append((32 + math.cos(a) * rad, 32 + math.sin(a) * rad))
    return sd_polygon(px, py, pts)


def poke_ball(px, py):
    ring = abs(sd_circle(px, py, 32, 32, 16)) - 2.6
    band = sd_round_rect(px, py, 32, 32, 16, 2.4, 0)
    centre = abs(sd_circle(px, py, 32, 32, 5.5)) - 2.4
    hole = sd_circle(px, py, 32, 32, 3.0)
    band = max(band, -sd_circle(px, py, 32, 32, 7.5))
    shape = min(ring, band, centre)
    top_fill = max(sd_circle(px, py, 32, 32, 14), py - 31)
    top_fill = max(top_fill, -sd_circle(px, py, 32, 32, 8.5))
    return max(min(shape, top_fill), -hole) if hole < 0 else min(shape, top_fill)


def bag(px, py):
    body = sd_round_rect(px, py, 32, 38, 16, 10, 5)
    handle = abs(sd_circle(px, py, 32, 29, 10)) - 2.4
    handle = max(handle, py - 29)
    # The flap: a thin line across the bag.
    flap = sd_segment(px, py, 17, 34, 47, 34, 1.1)
    return max(min(body, handle), -flap)


def run(px, py):
    a = min(sd_segment(px, py, 19, 21, 29, 32, 3), sd_segment(px, py, 29, 32, 19, 43, 3))
    b = min(sd_segment(px, py, 31, 21, 41, 32, 3), sd_segment(px, py, 41, 32, 31, 43, 3))
    return min(a, b)


def flag(px, py):
    pole = sd_segment(px, py, 22, 17, 22, 47, 2.2)
    cloth = sd_polygon(px, py, [(24, 17), (45, 22), (24, 31)])
    return min(pole, cloth)


icon("swsh_icon_fight.png", (232, 48, 74), star)
icon("swsh_icon_party.png", (52, 176, 92), poke_ball)
icon("swsh_icon_bag.png", (238, 160, 32), bag)
icon("swsh_icon_run.png", (60, 120, 220), run)
icon("swsh_icon_forfeit.png", (122, 122, 134), flag)


# ---- cursor --------------------------------------------------------------------

def cursor():
    size = 48
    pixels = []
    pts = [(8, 7), (41, 24), (8, 41)]
    for y in range(size):
        for x in range(size):
            d = sd_polygon(x + 0.5, y + 0.5, pts) + 2
            outer = cov(d - 3.5)
            inner = cov(d)
            colour = mix((20, 20, 26), (255, 255, 255), inner)
            pixels.append(colour + (255 * max(outer, inner),))
    save("swsh_cursor.png", size, size, pixels)


cursor()
print("ok", sorted(os.listdir(OUT)))
