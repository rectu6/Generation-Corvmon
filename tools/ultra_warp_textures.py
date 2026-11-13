# Ultra Warp Ride tunnel textures (our own art, made from sums of waves):
# - tunnel_wall.png: the walls, a swirl of night blue, violet and cyan veins, tiling
#   both ways (every wave has whole periods across the picture), opaque;
# - tunnel_glow.png: the inner glowing layer, bright swirls on black (drawn additive);
# - streak.png: one light streak, bright in the middle, soft at the ends (additive);
# - glow.png: the light at the end of the tunnel, cores of the orbs (additive);
# - ring.png: the ring of light around the orbs (additive);
# - bolt.png: a strip of electric light, even along its length (additive);
# - crystal.png: facets of the dimensional debris (opaque, tinted violet);
# - vortex.png: the swirl of a gravitational anomaly and of the portals (additive);
# - petal.png: an energy petal of the exceptional portals' aura (additive);
# - sparkle.png: a four-pointed sparkle drawn into the portals (additive).
# Usage: python tools/ultra_warp_textures.py (writes into the mod's resources).
import math, os, random, struct, zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "resources",
                   "assets", "generations_core", "textures", "ultra_space")
os.makedirs(OUT, exist_ok=True)
TAU = math.tau


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def save(name, w, h, pixels):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for r, g, b, a in pixels[y * w:(y + 1) * w]:
            raw += bytes((int(clamp(r, 0, 255) + 0.5), int(clamp(g, 0, 255) + 0.5), int(clamp(b, 0, 255) + 0.5), int(clamp(a) * 255 + 0.5)))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(os.path.join(OUT, name), "wb").write(png)
    print(name, w, h)


def waves(rng, count, max_k, amplitude):
    """Waves with whole numbers of periods across the picture: the sum tiles."""
    out = []
    for _ in range(count):
        kx = rng.randint(-max_k, max_k)
        ky = rng.randint(1, max_k)
        out.append((kx, ky, amplitude * rng.uniform(0.4, 1.0), rng.uniform(0, TAU)))
    return out


def field(x, y, terms):
    return sum(a * math.sin(TAU * (kx * x + ky * y) + p) for kx, ky, a, p in terms)


def ramp(stops, t):
    t = clamp(t)
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(c0[j] + (c1[j] - c0[j]) * f for j in range(3))
    return stops[-1][1]


def tunnel_wall(size=512):
    rng = random.Random(7031)
    warp_x = waves(rng, 4, 3, 1.0)
    warp_y = waves(rng, 4, 3, 1.0)
    swirl = waves(rng, 6, 5, 0.7)
    stops = [
        (0.00, (8, 6, 30)),
        (0.35, (34, 16, 82)),
        (0.55, (62, 30, 132)),
        (0.75, (38, 74, 178)),
        (0.90, (120, 70, 196)),
        (1.00, (206, 120, 230)),
    ]
    pixels = []
    for py in range(size):
        y = py / size
        for px in range(size):
            x = px / size
            wx = field(x, y, warp_x)
            wy = field(x, y, warp_y)
            xx = x + 0.05 * wx
            yy = y + 0.05 * wy
            s = field(xx, yy, swirl)
            v = 0.5 + 0.5 * math.tanh(s * 0.9)
            r, g, b = ramp(stops, v)
            # Thin cyan veins along the swirl.
            vein = max(0.0, 1.0 - abs(math.sin(TAU * (2 * xx + 3 * yy) + 2.5 * wx))) ** 14
            vein2 = max(0.0, 1.0 - abs(math.sin(TAU * (-3 * xx + 2 * yy) + 2.0 * wy))) ** 20
            glow = vein * 0.9 + vein2 * 0.6
            r += 60 * glow
            g += 190 * glow
            b += 210 * glow
            pixels.append((r, g, b, 1.0))
    save("tunnel_wall.png", size, size, pixels)


def tunnel_glow(size=256):
    rng = random.Random(4412)
    warp = waves(rng, 4, 3, 1.0)
    mask = waves(rng, 4, 2, 1.0)
    pixels = []
    for py in range(size):
        y = py / size
        for px in range(size):
            x = px / size
            w = field(x, y, warp)
            band = max(0.0, math.sin(TAU * (x + 4 * y) + 1.8 * w)) ** 10
            band2 = max(0.0, math.sin(TAU * (-2 * x + 7 * y) + 1.4 * w + 1.0)) ** 16
            m = 0.5 + 0.5 * math.tanh(field(x, y, mask))
            v = clamp((band * 0.9 + band2 * 0.7) * (0.25 + 0.75 * m))
            c = 255 * v
            pixels.append((c, c, c, 1.0))
    save("tunnel_glow.png", size, size, pixels)


def streak(w=16, h=128):
    pixels = []
    for py in range(h):
        v = (py + 0.5) / h
        ends = math.sin(math.pi * v) ** 0.7
        for px in range(w):
            d = ((px + 0.5) / w - 0.5) * 2
            c = 255 * math.exp(-(d / 0.35) ** 2) * ends
            pixels.append((c, c, c, 1.0))
    save("streak.png", w, h, pixels)


def glow(size=128):
    pixels = []
    for py in range(size):
        for px in range(size):
            dx = (px + 0.5) / size * 2 - 1
            dy = (py + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            c = 255 * (max(0.0, 1 - r) ** 2.2 + 0.6 * max(0.0, 1 - r * 3) ** 2)
            pixels.append((c, c, c, 1.0))
    save("glow.png", size, size, pixels)


def ring(size=128):
    pixels = []
    for py in range(size):
        for px in range(size):
            dx = (px + 0.5) / size * 2 - 1
            dy = (py + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            c = 255 * (math.exp(-((r - 0.78) / 0.05) ** 2) + 0.35 * math.exp(-((r - 0.78) / 0.16) ** 2))
            pixels.append((c, c, c, 1.0))
    save("ring.png", size, size, pixels)


def bolt(w=16, h=16):
    pixels = []
    for py in range(h):
        for px in range(w):
            d = ((px + 0.5) / w - 0.5) * 2
            c = 255 * (math.exp(-(d / 0.22) ** 2) + 0.3 * math.exp(-(d / 0.6) ** 2))
            pixels.append((c, c, c, 1.0))
    save("bolt.png", w, h, pixels)


def crystal(size=64):
    rng = random.Random(911)
    veins = waves(rng, 3, 2, 1.0)
    pixels = []
    for py in range(size):
        y = py / size
        for px in range(size):
            x = px / size
            v = 0.5 + 0.5 * math.tanh(field(x, y, veins))
            edge = max(0.0, 1.0 - abs(math.sin(TAU * (x * 2 + y * 3)))) ** 18
            r = 70 + 110 * v + 120 * edge
            g = 40 + 60 * v + 160 * edge
            b = 140 + 100 * v + 115 * edge
            pixels.append((r, g, b, 1.0))
    save("crystal.png", size, size, pixels)


def vortex(size=128):
    pixels = []
    for py in range(size):
        for px in range(size):
            dx = (px + 0.5) / size * 2 - 1
            dy = (py + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            a = math.atan2(dy, dx)
            arms = max(0.0, math.sin(a * 3 + r * 9)) ** 3
            fall = max(0.0, 1 - r) ** 1.5
            c = 255 * clamp(fall * (0.25 + 0.75 * arms) + 0.5 * max(0.0, 1 - r * 4) ** 2)
            pixels.append((c, c, c, 1.0))
    save("vortex.png", size, size, pixels)


def petal(w=64, h=128):
    pixels = []
    for py in range(h):
        v = (py + 0.5) / h  # 0 at the tip, 1 at the base
        half = 0.5 * math.sin(math.pi * (v ** 0.8)) ** 0.9
        for px in range(w):
            d = abs((px + 0.5) / w - 0.5)
            inside = clamp((half - d) / 0.12)
            vein = math.exp(-(d / 0.04) ** 2) * 0.5
            c = 255 * clamp(inside * (0.55 + 0.45 * (1 - v)) + vein * inside)
            pixels.append((c, c, c, 1.0))
    save("petal.png", w, h, pixels)


def sparkle(size=32):
    pixels = []
    for py in range(size):
        for px in range(size):
            dx = abs((px + 0.5) / size * 2 - 1)
            dy = abs((py + 0.5) / size * 2 - 1)
            star = max(0.0, 1 - (dx * 6 + dy)) + max(0.0, 1 - (dy * 6 + dx))
            core = max(0.0, 1 - math.sqrt(dx * dx + dy * dy) * 2.5) ** 2
            c = 255 * clamp(star * 0.8 + core)
            pixels.append((c, c, c, 1.0))
    save("sparkle.png", size, size, pixels)


tunnel_wall()
tunnel_glow()
streak()
glow()
ring()
bolt()
crystal()
vortex()
petal()
sparkle()
