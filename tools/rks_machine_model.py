"""
RKS Machine 3D model (RKS-PLAN.md § 3.3, decision D8): writes the mesh read by
RksMachineModel.kt. Every part is built here from primitives (boxes, cylinders, cones, tori,
tubes along curves, sloped slabs), mapped onto the texture atlas painted by tools/RksArt.java.

    python tools/rks_machine_model.py

Output: common/src/main/resources/assets/generations_core/rks/rks_machine_mesh.json

The model's frame (blocks): origin at the bottom centre of the containment chamber, x to the
right as seen from the front, y up, z toward the front (the console, the player). The machine
spans x -1.5..1.5, y 0..4, z -1.5..1.5:

    z = +1   console (x -1.5..1.5, one block high, its screens up to 1.5)
    z =  0   power (x -1, two high)   chamber (x 0, four high)   analyser (x +1, two high)
    z = -1   power                    reactor (x 0, four high)   analyser

Each group: its module (console, chamber, reactor, power, analyzer, or link: only once
assembled), its material (metal: lit, opaque; glow: full bright, opaque; add: full bright,
added; glass: translucent, drawn after the Pokémon in the tank), its variant (intact, damaged,
both), its animation (ring0..2, core, arm, scan, screen, lights, none) with a pivot and an axis,
and its detail level (1: skipped in LOW quality and far away).
One vertex: x, y, z, normal x, y, z, u, v (u, v in the atlas, 0 to 1).
"""
import json
import math
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', 'generations_core', 'rks', 'rks_machine_mesh.json')

ATLAS = 512.0
# Atlas regions in pixels (x0, y0, x1, y1); tools/RksArt.java paints the same ones.
REGIONS = {
    'metal_dark': (0, 0, 128, 128),
    'metal_light': (128, 0, 256, 128),
    'panel': (256, 0, 384, 128),
    'grille': (384, 0, 512, 128),
    'cable': (0, 128, 128, 160),
    'hazard': (128, 128, 256, 160),
    'pipe': (0, 160, 128, 192),
    'trim': (128, 160, 256, 192),
    'screen_frame': (256, 128, 384, 192),
    'glass': (0, 192, 64, 320),
    'liquid': (64, 192, 192, 320),
    'screen': (192, 192, 320, 256),
    'screen_red': (320, 192, 448, 256),
    'light': (448, 192, 512, 256),
    'halo': (0, 320, 128, 448),
    'core': (128, 320, 256, 448),
    'dark': (256, 256, 384, 320),
    'gauge': (384, 256, 448, 320),
    'buttons': (448, 256, 512, 320),
    'cracks': (256, 320, 384, 448),
    'holo': (384, 320, 512, 448),
    'screen_dna': (192, 256, 256, 320),
    'vial': (384, 448, 448, 512),
}


def uv(region, fu, fv):
    x0, y0, x1, y1 = REGIONS[region]
    # Half a pixel in: no bleeding from the next region.
    u = (x0 + 0.5 + (x1 - x0 - 1.0) * min(max(fu, 0.0), 1.0)) / ATLAS
    v = (y0 + 0.5 + (y1 - y0 - 1.0) * min(max(fv, 0.0), 1.0)) / ATLAS
    return (u, v)


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def norm(a):
    length = math.sqrt(dot(a, a))
    return (a[0] / length, a[1] / length, a[2] / length) if length > 1e-9 else (0.0, 1.0, 0.0)


class Group:
    def __init__(self, name, module, material, variant='both', anim='none', pivot=(0.0, 0.0, 0.0), axis=(0.0, 1.0, 0.0), detail=0):
        self.name = name
        self.module = module
        self.material = material
        self.variant = variant
        self.anim = anim
        self.pivot = pivot
        self.axis = axis
        self.detail = detail
        self.v = []

    def vertex(self, p, n, t):
        self.v += [p[0], p[1], p[2], n[0], n[1], n[2], t[0], t[1]]

    def tri(self, a, b, c, ta, tb, tc, na=None, nb=None, nc=None):
        if na is None:
            n = norm(cross(sub(b, a), sub(c, a)))
            na = nb = nc = n
        self.vertex(a, na, ta)
        self.vertex(b, nb, tb)
        self.vertex(c, nc, tc)

    def quad(self, a, b, c, d, ta, tb, tc, td, normals=None):
        """Corners counter-clockwise seen from the front (outside)."""
        if normals is None:
            self.tri(a, b, c, ta, tb, tc)
            self.tri(a, c, d, ta, tc, td)
        else:
            na, nb, nc, nd = normals
            self.tri(a, b, c, ta, tb, tc, na, nb, nc)
            self.tri(a, c, d, ta, tc, td, na, nc, nd)

    @property
    def triangles(self):
        return len(self.v) // 24


GROUPS = []


def group(*args, **kwargs):
    g = Group(*args, **kwargs)
    GROUPS.append(g)
    return g


# ---------------------------------------------------------------- primitives

def face(g, a, b, c, d, region, w, h, density=1.0):
    """A quad mapped onto a part of a region proportional to its size (w, h in blocks)."""
    fu = min(1.0, w * density)
    fv = min(1.0, h * density)
    g.quad(a, b, c, d, uv(region, 0, fv), uv(region, fu, fv), uv(region, fu, 0), uv(region, 0, 0))


def box(g, x0, y0, z0, x1, y1, z1, region, density=1.0, top=None, faces='nsewud'):
    """An axis-aligned box. top: another region for the top face."""
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    if 's' in faces:  # front (+z)
        face(g, (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), region, dx, dy, density)
    if 'n' in faces:  # back (-z)
        face(g, (x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), region, dx, dy, density)
    if 'e' in faces:  # +x
        face(g, (x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), region, dz, dy, density)
    if 'w' in faces:  # -x
        face(g, (x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), region, dz, dy, density)
    if 'u' in faces:
        face(g, (x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), top or region, dx, dz, density)
    if 'd' in faces:
        face(g, (x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), region, dx, dz, density)


def hexa(g, c, region, density=1.0):
    """A hexahedron from its 8 corners: bottom (x0z0, x1z0, x1z1, x0z1), then the top ones the same way."""
    b0, b1, b2, b3, t0, t1, t2, t3 = c
    def span(p, q):
        return math.sqrt(dot(sub(p, q), sub(p, q)))
    face(g, b3, b2, t2, t3, region, span(b3, b2), span(b2, t2), density)   # front (z1)
    face(g, b1, b0, t0, t1, region, span(b1, b0), span(b0, t0), density)   # back (z0)
    face(g, b2, b1, t1, t2, region, span(b2, b1), span(b1, t1), density)   # +x
    face(g, b0, b3, t3, t0, region, span(b0, b3), span(b3, t3), density)   # -x
    face(g, t3, t2, t1, t0, region, span(t3, t2), span(t2, t1), density)   # top
    face(g, b0, b1, b2, b3, region, span(b0, b1), span(b1, b2), density)   # bottom


def ring_points(cx, cz, r, seg, a0=0.0):
    return [(cx + r * math.sin(a0 + 2 * math.pi * i / seg), cz + r * math.cos(a0 + 2 * math.pi * i / seg)) for i in range(seg + 1)]


def frustum(g, cx, cz, y0, y1, r0, r1, seg, region, v_repeat=1.0, u_repeat=1.0, inside=False):
    """The side of a cone frustum (a cylinder when r0 == r1), smooth normals, facing out (or in)."""
    slope = (r0 - r1) / max(y1 - y0, 1e-6)
    for i in range(seg):
        a = 2 * math.pi * i / seg
        b = 2 * math.pi * (i + 1) / seg
        pa0 = (cx + r0 * math.sin(a), y0, cz + r0 * math.cos(a))
        pb0 = (cx + r0 * math.sin(b), y0, cz + r0 * math.cos(b))
        pa1 = (cx + r1 * math.sin(a), y1, cz + r1 * math.cos(a))
        pb1 = (cx + r1 * math.sin(b), y1, cz + r1 * math.cos(b))
        na = norm((math.sin(a), slope, math.cos(a)))
        nb = norm((math.sin(b), slope, math.cos(b)))
        ua = (i / seg * u_repeat) % 1.0
        ub = ua + u_repeat / seg
        if ub > 1.0:
            ua, ub = 0.0, u_repeat / seg
        if inside:
            na = mul(na, -1)
            nb = mul(nb, -1)
            g.quad(pb0, pa0, pa1, pb1, uv(region, ub, v_repeat), uv(region, ua, v_repeat), uv(region, ua, 0), uv(region, ub, 0), (nb, na, na, nb))
        else:
            g.quad(pa0, pb0, pb1, pa1, uv(region, ua, v_repeat), uv(region, ub, v_repeat), uv(region, ub, 0), uv(region, ua, 0), (na, nb, nb, na))


def cylinder(g, cx, cz, y0, y1, r, seg, region, v_repeat=1.0, u_repeat=1.0, caps=True, cap_region=None):
    frustum(g, cx, cz, y0, y1, r, r, seg, region, v_repeat, u_repeat)
    if caps:
        disc(g, cx, y1, cz, r, seg, cap_region or region, up=True)
        disc(g, cx, y0, cz, r, seg, cap_region or region, up=False)


def disc(g, cx, y, cz, r, seg, region, up=True, r_in=0.0):
    n = (0.0, 1.0 if up else -1.0, 0.0)
    for i in range(seg):
        a = 2 * math.pi * i / seg
        b = 2 * math.pi * (i + 1) / seg
        pa = (cx + r * math.sin(a), y, cz + r * math.cos(a))
        pb = (cx + r * math.sin(b), y, cz + r * math.cos(b))
        ta = uv(region, 0.5 + 0.5 * math.sin(a), 0.5 + 0.5 * math.cos(a))
        tb = uv(region, 0.5 + 0.5 * math.sin(b), 0.5 + 0.5 * math.cos(b))
        if r_in > 0:
            qa = (cx + r_in * math.sin(a), y, cz + r_in * math.cos(a))
            qb = (cx + r_in * math.sin(b), y, cz + r_in * math.cos(b))
            sa = uv(region, 0.5 + 0.5 * r_in / r * math.sin(a), 0.5 + 0.5 * r_in / r * math.cos(a))
            sb = uv(region, 0.5 + 0.5 * r_in / r * math.sin(b), 0.5 + 0.5 * r_in / r * math.cos(b))
            if up:
                g.quad(qa, qb, pb, pa, sa, sb, tb, ta, (n, n, n, n))
            else:
                g.quad(qb, qa, pa, pb, sb, sa, ta, tb, (n, n, n, n))
        else:
            c = (cx, y, cz)
            tc = uv(region, 0.5, 0.5)
            if up:
                g.tri(c, pb, pa, tc, tb, ta, n, n, n)
            else:
                g.tri(c, pa, pb, tc, ta, tb, n, n, n)


def torus(g, c, major, minor, seg, sides, region, axis='y', u_repeat=1.0):
    """A torus around an axis through c."""
    def point(theta, phi, rr):
        ring = major + rr * math.cos(phi)
        h = rr * math.sin(phi)
        if axis == 'y':
            return (c[0] + ring * math.sin(theta), c[1] + h, c[2] + ring * math.cos(theta))
        if axis == 'x':
            return (c[0] + h, c[1] + ring * math.sin(theta), c[2] + ring * math.cos(theta))
        return (c[0] + ring * math.sin(theta), c[1] + ring * math.cos(theta), c[2] + h)
    for i in range(seg):
        t0 = 2 * math.pi * i / seg
        t1 = 2 * math.pi * (i + 1) / seg
        for j in range(sides):
            p0 = 2 * math.pi * j / sides
            p1 = 2 * math.pi * (j + 1) / sides
            a, b, cc, d = point(t0, p0, minor), point(t1, p0, minor), point(t1, p1, minor), point(t0, p1, minor)
            na, nb, nc, nd = (norm(sub(point(t, p, minor), point(t, p, 0.0))) for t, p in ((t0, p0), (t1, p0), (t1, p1), (t0, p1)))
            u0 = (i / seg * u_repeat) % 1.0
            u1 = u0 + u_repeat / seg
            g.quad(a, b, cc, d, uv(region, u0, j / sides), uv(region, u1, j / sides), uv(region, u1, (j + 1) / sides), uv(region, u0, (j + 1) / sides), (na, nb, nc, nd))


def bezier(p0, p1, p2, p3, n):
    out = []
    for i in range(n + 1):
        t = i / n
        k = 1 - t
        out.append(tuple(k * k * k * p0[j] + 3 * k * k * t * p1[j] + 3 * k * t * t * p2[j] + t * t * t * p3[j] for j in range(3)))
    return out


def tube(g, points, r, sides, region, v_per_block=2.0):
    """A tube along a polyline (parallel transport frames), from its first point to its last: drawing a
    first part of its triangles grows it from the start (the assembly's cables)."""
    frames = []
    t0 = norm(sub(points[1], points[0]))
    up = (0.0, 1.0, 0.0) if abs(t0[1]) < 0.9 else (1.0, 0.0, 0.0)
    n0 = norm(cross(t0, up))
    for i, p in enumerate(points):
        if i == 0:
            t = t0
        elif i == len(points) - 1:
            t = norm(sub(points[i], points[i - 1]))
        else:
            t = norm(sub(points[i + 1], points[i - 1]))
        n0 = norm(sub(n0, mul(t, dot(n0, t))))
        b = cross(t, n0)
        frames.append((p, n0, b))
    length = 0.0
    for i in range(len(points) - 1):
        a, na, ba = frames[i]
        b, nb, bb = frames[i + 1]
        seg_len = math.sqrt(dot(sub(b, a), sub(b, a)))
        va = (length * v_per_block) % 1.0
        vb = va + seg_len * v_per_block
        if vb > 1.0:
            va, vb = 0.0, min(1.0, seg_len * v_per_block)
        length += seg_len
        for j in range(sides):
            p0 = 2 * math.pi * j / sides
            p1 = 2 * math.pi * (j + 1) / sides
            def ring(f, phi):
                pp, n, bb2 = f
                d = add(mul(n, math.cos(phi)), mul(bb2, math.sin(phi)))
                return add(pp, mul(d, r)), d
            a0, da0 = ring(frames[i], p0)
            a1, da1 = ring(frames[i], p1)
            b0, db0 = ring(frames[i + 1], p0)
            b1, db1 = ring(frames[i + 1], p1)
            g.quad(a0, b0, b1, a1, uv(region, va, j / sides), uv(region, vb, j / sides), uv(region, vb, (j + 1) / sides), uv(region, va, (j + 1) / sides), (da0, db0, db1, da1))


def panel_quad(g, corners, region, normal_flip=False):
    a, b, c, d = corners
    g.quad(a, b, c, d, uv(region, 0, 1), uv(region, 1, 1), uv(region, 1, 0), uv(region, 0, 0))


# ---------------------------------------------------------------- the chamber (x 0, z 0, four high)

R_GLASS = 0.72
GLASS_Y0 = 0.55
GLASS_Y1 = 3.45


def chamber():
    seg = 32
    m = group('chamber_body', 'chamber', 'metal')
    cylinder(m, 0, 0, 0.0, 0.12, 0.84, seg, 'metal_dark', caps=False)
    frustum(m, 0, 0, 0.12, 0.18, 0.84, 0.78, seg, 'trim')
    frustum(m, 0, 0, 0.18, 0.40, 0.78, 0.78, seg, 'panel', v_repeat=0.25, u_repeat=3.0)
    frustum(m, 0, 0, 0.44, 0.50, 0.78, 0.78, seg, 'panel', v_repeat=0.1, u_repeat=3.0)
    frustum(m, 0, 0, 0.50, 0.55, 0.78, 0.74, seg, 'trim')
    disc(m, 0, 0.55, 0, 0.74, seg, 'metal_dark', up=True, r_in=0.66)
    # The top: clamp, cap, its cone and a hatch.
    frustum(m, 0, 0, 3.45, 3.50, 0.74, 0.80, seg, 'trim')
    frustum(m, 0, 0, 3.50, 3.80, 0.80, 0.80, seg, 'panel', v_repeat=0.35, u_repeat=3.0)
    frustum(m, 0, 0, 3.80, 3.95, 0.80, 0.58, seg, 'metal_dark')
    cylinder(m, 0, 0, 3.95, 4.0, 0.58, seg, 'metal_light', caps=False)
    disc(m, 0, 4.0, 0, 0.58, seg, 'metal_dark', up=True)
    disc(m, 0, 3.45, 0, 0.74, seg, 'metal_dark', up=False, r_in=0.66)
    # Clamps on the glass (the bevelled edges) and the middle band.
    for y in (0.55, 3.41):
        frustum(m, 0, 0, y, y + 0.04, 0.745, 0.745, seg, 'trim', v_repeat=0.2)
    # Struts.
    s = group('chamber_struts', 'chamber', 'metal')
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        x, z = 0.75 * math.sin(a), 0.75 * math.cos(a)
        box(s, x - 0.035, GLASS_Y0, z - 0.035, x + 0.035, GLASS_Y1, z + 0.035, 'metal_light', density=0.5)
        box(s, x - 0.05, 1.98, z - 0.05, x + 0.05, 2.06, z + 0.05, 'trim')
    # Injectors hanging from the cap and rising from the base.
    inj = group('chamber_injectors', 'chamber', 'metal', detail=1)
    tips = group('chamber_injector_tips', 'chamber', 'glow', anim='lights')
    for k in range(6):
        a = 2 * math.pi * k / 6 + math.pi / 6
        x, z = 0.46 * math.sin(a), 0.46 * math.cos(a)
        cylinder(inj, x, z, 3.12, GLASS_Y1, 0.045, 8, 'metal_light')
        frustum(inj, x, z, 3.06, 3.12, 0.02, 0.045, 8, 'metal_dark')
        cylinder(tips, x, z, 3.04, 3.07, 0.022, 6, 'light')
    for k in range(4):
        a = 2 * math.pi * k / 4
        x, z = 0.52 * math.sin(a), 0.52 * math.cos(a)
        cylinder(inj, x, z, GLASS_Y0, 0.74, 0.05, 8, 'metal_light')
        cylinder(tips, x, z, 0.74, 0.77, 0.03, 6, 'light')
    # Light bands.
    lights = group('chamber_lights', 'chamber', 'glow', anim='lights')
    frustum(lights, 0, 0, 0.40, 0.44, 0.785, 0.785, seg, 'light')
    frustum(lights, 0, 0, 3.62, 3.66, 0.805, 0.805, seg, 'light')
    floor = group('chamber_floor_glow', 'chamber', 'glow', anim='lights')
    disc(floor, 0, 0.552, 0, 0.66, seg, 'halo', up=True)
    # The glass, a double wall (intact), and its jagged remains (damaged).
    outer = group('chamber_glass_outer', 'chamber', 'glass', variant='intact')
    frustum(outer, 0, 0, GLASS_Y0, GLASS_Y1, R_GLASS, R_GLASS, 40, 'glass', v_repeat=1.0, u_repeat=2.0)
    inner = group('chamber_glass_inner', 'chamber', 'glass', variant='intact')
    frustum(inner, 0, 0, GLASS_Y0, GLASS_Y1, 0.685, 0.685, 40, 'glass', v_repeat=1.0, u_repeat=2.0, inside=True)
    broken = group('chamber_glass_broken', 'chamber', 'glass', variant='damaged')
    rng = random.Random(1996)
    seg_b = 40
    low = [0.08 + 0.75 * rng.random() ** 1.6 for _ in range(seg_b)]
    high = [0.05 + 0.5 * rng.random() ** 1.8 for _ in range(seg_b)]
    for i in range(seg_b):
        a = 2 * math.pi * i / seg_b
        b = 2 * math.pi * (i + 1) / seg_b
        mid = (a + b) / 2
        for r, inside in ((R_GLASS, False), (0.685, True)):
            pa0 = (r * math.sin(a), GLASS_Y0, r * math.cos(a))
            pb0 = (r * math.sin(b), GLASS_Y0, r * math.cos(b))
            spike = max(low[i], low[(i + 1) % seg_b]) + 0.25 * rng.random()
            pa1 = (r * math.sin(a), GLASS_Y0 + low[i], r * math.cos(a))
            pb1 = (r * math.sin(b), GLASS_Y0 + low[(i + 1) % seg_b], r * math.cos(b))
            pm = (r * math.sin(mid), GLASS_Y0 + spike, r * math.cos(mid))
            n = mul(norm((math.sin(mid), 0, math.cos(mid))), -1 if inside else 1)
            ta, tb, tm = uv('glass', 0.0, 1.0), uv('glass', 0.2, 1.0), uv('glass', 0.1, 0.7)
            broken.tri(pa0, pb0, pb1, ta, tb, tb, n, n, n)
            broken.tri(pa0, pb1, pa1, ta, tb, ta, n, n, n)
            broken.tri(pa1, pb1, pm, ta, tb, tm, n, n, n)
            qa0 = (r * math.sin(a), GLASS_Y1, r * math.cos(a))
            qb0 = (r * math.sin(b), GLASS_Y1, r * math.cos(b))
            qa1 = (r * math.sin(a), GLASS_Y1 - high[i], r * math.cos(a))
            qb1 = (r * math.sin(b), GLASS_Y1 - high[(i + 1) % seg_b], r * math.cos(b))
            qm = (r * math.sin(mid), GLASS_Y1 - max(high[i], high[(i + 1) % seg_b]) - 0.18 * rng.random(), r * math.cos(mid))
            broken.tri(qa0, qa1, qb1, ta, ta, tb, n, n, n)
            broken.tri(qa0, qb1, qb0, ta, tb, tb, n, n, n)
            broken.tri(qa1, qm, qb1, ta, tm, tb, n, n, n)
    # Shards on the base, the console and the floor; cables hanging from the cap.
    shards = group('chamber_shards', 'chamber', 'glass', variant='damaged')
    for i in range(70):
        a = rng.random() * 2 * math.pi
        if i < 30:
            r, y = 0.2 + 0.5 * rng.random(), 0.556
        elif i < 55:
            r, y = 0.95 + 0.45 * rng.random(), 0.0
            a = (rng.random() - 0.5) * 1.6  # in front, on the console and the floor
            if abs(r * math.cos(a)) > 0.5:
                y = 0.962 if r * math.cos(a) < 1.42 else 0.0
        else:
            r, y = 0.8 + 0.6 * rng.random(), 0.0
        cx, cz = r * math.sin(a), r * math.cos(a)
        s1 = 0.03 + 0.09 * rng.random()
        rot = rng.random() * 2 * math.pi
        p = [(cx + s1 * math.cos(rot + k * 2.2 + rng.random() * 0.6), y + 0.004 + 0.01 * rng.random(), cz + s1 * math.sin(rot + k * 2.2 + rng.random() * 0.6)) for k in range(3)]
        shards.tri(p[0], p[1], p[2], uv('glass', 0.1, 0.2), uv('glass', 0.4, 0.3), uv('glass', 0.2, 0.6), (0, 1, 0), (0, 1, 0), (0, 1, 0))
        shards.tri(p[0], p[2], p[1], uv('glass', 0.1, 0.2), uv('glass', 0.2, 0.6), uv('glass', 0.4, 0.3), (0, -1, 0), (0, -1, 0), (0, -1, 0))
    hanging = group('chamber_hanging_cables', 'chamber', 'metal', variant='damaged')
    for k, (a, drop) in enumerate(((0.6, 1.1), (2.4, 0.7), (4.1, 1.4))):
        x, z = 0.42 * math.sin(a), 0.42 * math.cos(a)
        tube(hanging, bezier((x, 3.44, z), (x * 1.1, 3.1, z * 1.1), (x * 1.25, 3.44 - drop + 0.2, z * 1.25), (x * 1.3, 3.44 - drop, z * 1.3), 8), 0.025, 6, 'cable')
    sparks = group('chamber_broken_tips', 'chamber', 'glow', variant='damaged', anim='lights')
    for k, (a, drop) in enumerate(((0.6, 1.1), (2.4, 0.7), (4.1, 1.4))):
        x, z = 0.42 * math.sin(a), 0.42 * math.cos(a)
        cylinder(sparks, x * 1.3, z * 1.3, 3.44 - drop - 0.03, 3.44 - drop, 0.03, 6, 'light')


# ---------------------------------------------------------------- the reactor (x 0, z -1, four high)

def reactor():
    zc = -1.0
    m = group('reactor_frame', 'reactor', 'metal')
    box(m, -0.46, 0.0, zc - 0.46, 0.46, 0.34, zc + 0.46, 'metal_dark', top='panel')
    box(m, -0.40, 0.34, zc - 0.40, 0.40, 0.42, zc + 0.40, 'trim')
    box(m, -0.46, 3.62, zc - 0.46, 0.46, 3.96, zc + 0.46, 'metal_dark')
    box(m, -0.40, 3.54, zc - 0.40, 0.40, 3.62, zc + 0.40, 'trim')
    box(m, -0.30, 3.96, zc - 0.30, 0.30, 4.0, zc + 0.30, 'grille')
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = 0.37 * sx, zc + 0.37 * sz
            box(m, x - 0.05, 0.42, z - 0.05, x + 0.05, 3.54, z + 0.05, 'metal_light', density=0.4)
    collars = group('reactor_collars', 'reactor', 'metal', detail=1)
    for y in (0.62, 3.34):
        cylinder(collars, 0, zc, y - 0.06, y + 0.06, 0.2, 16, 'trim')
    torus(collars, (0, 1.2, zc), 0.17, 0.025, 20, 6, 'metal_light')
    torus(collars, (0, 2.8, zc), 0.17, 0.025, 20, 6, 'metal_light')
    core = group('reactor_core', 'reactor', 'glow', anim='core')
    cylinder(core, 0, zc, 0.42, 3.54, 0.12, 16, 'core', v_repeat=1.0, caps=False)
    sphere_core = group('reactor_heart', 'reactor', 'glow', anim='lights')
    cylinder(sphere_core, 0, zc, 1.92, 2.08, 0.16, 16, 'light')
    # Three nested rings (a gimbal) around the heart, each on its own axis.
    for i, (major, axis) in enumerate(((0.42, 'y'), (0.34, 'x'), (0.26, 'z'))):
        ring = group(f'reactor_ring{i}', 'reactor', 'metal', anim=f'ring{i}', pivot=(0.0, 2.0, zc), axis={'y': (0, 1, 0), 'x': (1, 0, 0), 'z': (0, 0, 1)}[axis])
        torus(ring, (0, 2.0, zc), major, 0.04, 32, 6, 'metal_light', axis=axis, u_repeat=2.0)
        nodes = group(f'reactor_ring{i}_nodes', 'reactor', 'glow', anim=f'ring{i}', pivot=(0.0, 2.0, zc), axis=ring.axis)
        for k in range(4):
            t = math.pi / 4 + k * math.pi / 2
            if axis == 'y':
                p = (major * math.sin(t), 2.0, zc + major * math.cos(t))
            elif axis == 'x':
                p = (0.0, 2.0 + major * math.sin(t), zc + major * math.cos(t))
            else:
                p = (major * math.sin(t), 2.0 + major * math.cos(t), zc)
            box(nodes, p[0] - 0.035, p[1] - 0.035, p[2] - 0.035, p[0] + 0.035, p[1] + 0.035, p[2] + 0.035, 'light')
    vents = group('reactor_vents', 'reactor', 'metal', detail=1)
    box(vents, -0.3, 0.06, zc - 0.465, 0.3, 0.28, zc - 0.46, 'grille', faces='n')
    box(vents, -0.3, 3.66, zc - 0.465, 0.3, 3.9, zc - 0.46, 'grille', faces='n')


# ---------------------------------------------------------------- the power module (x -1, z 0 and -1, two high)

def power():
    m = group('power_cabinet', 'power', 'metal')
    box(m, -1.45, 0.0, -1.42, -0.80, 1.78, 0.42, 'panel', density=0.8)
    box(m, -1.48, 1.78, -1.45, -0.77, 1.88, 0.45, 'metal_dark')
    box(m, -1.47, 0.0, -1.44, -0.78, 0.08, 0.44, 'metal_dark')
    hz = group('power_hazard', 'power', 'metal', detail=1)
    box(hz, -1.455, 0.08, 0.42, -0.795, 0.16, 0.425, 'hazard', faces='s')
    vents = group('power_vents', 'power', 'metal', detail=1)
    box(vents, -1.455, 0.25, -1.3, -1.45, 1.55, 0.3, 'grille', faces='w')
    gauges = group('power_gauges', 'power', 'metal', detail=1)
    for y in (1.05, 1.38):
        for x in (-1.3, -0.97):
            box(gauges, x - 0.12, y, 0.42, x + 0.12, y + 0.24, 0.43, 'gauge', faces='s')
    strip = group('power_lights', 'power', 'glow', anim='lights')
    box(strip, -1.40, 0.25, 0.425, -1.36, 0.95, 0.43, 'light', faces='s')
    box(strip, -0.89, 0.25, 0.425, -0.85, 0.95, 0.43, 'light', faces='s')
    # Three energy cells on the roof: glass capsules with a glowing core.
    caps = group('power_cell_caps', 'power', 'metal')
    cores = group('power_cell_cores', 'power', 'glow', anim='core')
    glass = group('power_cell_glass', 'power', 'glass')
    for z in (0.12, -0.5, -1.12):
        x = -1.12
        cylinder(caps, x, z, 1.88, 1.98, 0.15, 16, 'metal_light')
        cylinder(caps, x, z, 2.46, 2.56, 0.15, 16, 'metal_light')
        frustum(caps, x, z, 2.56, 2.62, 0.15, 0.07, 16, 'metal_dark')
        cylinder(cores, x, z, 1.98, 2.46, 0.065, 12, 'core', caps=False)
        frustum(glass, x, z, 1.98, 2.46, 0.12, 0.12, 20, 'glass')
    cable = group('power_cables', 'power', 'metal', detail=1)
    tube(cable, bezier((-0.82, 0.25, -0.2), (-0.7, 0.25, -0.25), (-0.72, 0.3, -0.35), (-0.79, 0.3, -0.45), 6), 0.04, 6, 'cable')


# ---------------------------------------------------------------- the genetic analyser (x +1, z 0 and -1, two high)

def analyzer():
    m = group('analyzer_cabinet', 'analyzer', 'metal')
    box(m, 0.80, 0.0, -1.42, 1.45, 0.98, 0.42, 'panel', density=0.8)
    box(m, 0.77, 0.98, -1.45, 1.48, 1.06, 0.45, 'metal_dark')
    box(m, 0.78, 0.0, -1.44, 1.47, 0.08, 0.44, 'metal_dark')
    bed = group('analyzer_bed', 'analyzer', 'metal')
    box(bed, 0.86, 1.06, -1.32, 1.36, 1.12, 0.30, 'metal_light', top='dark')
    # The gantry and its scanner head (it slides along the bed).
    gantry = group('analyzer_gantry', 'analyzer', 'metal')
    for z in (-1.36, 0.34):
        box(gantry, 1.32, 1.06, z - 0.04, 1.42, 1.92, z + 0.04, 'metal_light')
    box(gantry, 1.30, 1.88, -1.40, 1.44, 1.98, 0.38, 'metal_dark')
    arm = group('analyzer_arm', 'analyzer', 'metal', anim='arm')
    box(arm, 0.98, 1.86, -0.08, 1.38, 1.94, 0.08, 'metal_light')
    box(arm, 1.02, 1.62, -0.10, 1.22, 1.86, 0.10, 'metal_dark')
    lens = group('analyzer_lens', 'analyzer', 'glow', anim='arm')
    box(lens, 1.05, 1.60, -0.07, 1.19, 1.62, 0.07, 'light', faces='d')
    scan = group('analyzer_scan', 'analyzer', 'glow', anim='scan')
    box(scan, 0.88, 1.121, -0.02, 1.34, 1.125, 0.02, 'light', faces='u')
    # Sample vials on a rack.
    rack = group('analyzer_rack', 'analyzer', 'metal', detail=1)
    box(rack, 0.84, 1.06, 0.31, 1.40, 1.10, 0.43, 'metal_dark')
    vials = group('analyzer_vials', 'analyzer', 'glow', detail=1)
    for k in range(5):
        x = 0.90 + k * 0.11
        cylinder(vials, x, 0.37, 1.10, 1.24, 0.03, 8, 'vial')
    screens = group('analyzer_screens', 'analyzer', 'glow', anim='screen')
    box(screens, 0.92, 0.42, 0.42, 1.36, 0.86, 0.43, 'screen_dna', faces='s')
    frame = group('analyzer_frame', 'analyzer', 'metal', detail=1)
    box(frame, 0.89, 0.39, 0.42, 1.39, 0.42, 0.44, 'screen_frame')
    box(frame, 0.89, 0.86, 0.42, 1.39, 0.89, 0.44, 'screen_frame')
    vents = group('analyzer_vents', 'analyzer', 'metal', detail=1)
    box(vents, 1.45, 0.2, -1.3, 1.455, 0.8, 0.3, 'grille', faces='e')
    strip = group('analyzer_lights', 'analyzer', 'glow', anim='lights')
    box(strip, 0.85, 0.12, 0.425, 1.40, 0.15, 0.43, 'light', faces='s')


# ---------------------------------------------------------------- the console (z +1, x -1.5..1.5, one high)

def console():
    m = group('console_desk', 'console', 'metal')
    box(m, -1.45, 0.08, 0.66, 1.45, 0.70, 1.38, 'panel', density=0.8)
    box(m, -1.47, 0.0, 0.64, 1.47, 0.08, 1.33, 'metal_dark')
    hz = group('console_hazard', 'console', 'metal', detail=1)
    box(hz, -1.45, 0.08, 1.38, 1.45, 0.14, 1.385, 'hazard', faces='s')
    # The sloped control panel.
    slab = group('console_panel', 'console', 'metal')
    hexa(slab, [(-1.46, 0.70, 0.66), (1.46, 0.70, 0.66), (1.46, 0.70, 1.42), (-1.46, 0.70, 1.42),
                (-1.46, 0.95, 0.66), (1.46, 0.95, 0.66), (1.46, 0.76, 1.42), (-1.46, 0.76, 1.42)], 'metal_dark', density=0.7)
    # Buttons and sliders on the panel (its surface rises 0.19 over 0.76 toward the back).
    buttons = group('console_buttons', 'console', 'glow', anim='lights', detail=1)
    def on_panel(x, z, w, d, h, region, fu0, fv0, fu1, fv1):
        y = 0.76 + (1.42 - z) / 0.76 * 0.19
        a, b, c, dd = (x - w, y + h, z + d), (x + w, y + h, z + d), (x + w, y + h, z - d), (x - w, y + h, z - d)
        buttons.quad(a, b, c, dd, uv(region, fu0, fv1), uv(region, fu1, fv1), uv(region, fu1, fv0), uv(region, fu0, fv0))
    for i in range(8):
        for j in range(2):
            on_panel(-1.25 + i * 0.09, 1.25 - j * 0.1, 0.03, 0.03, 0.012, 'buttons', (i % 4) * 0.25, j * 0.5, (i % 4) * 0.25 + 0.25, j * 0.5 + 0.5)
    for i in range(8):
        on_panel(0.62 + i * 0.09, 1.2, 0.025, 0.06, 0.012, 'buttons', ((i + 2) % 4) * 0.25, 0.0, ((i + 2) % 4) * 0.25 + 0.25, 0.5)
    keys = group('console_keyboard', 'console', 'metal', detail=1)
    for i in range(12):
        x = -0.48 + i * 0.087
        y = 0.76 + (1.42 - 1.22) / 0.76 * 0.19
        box(keys, x, y, 1.16, x + 0.07, y + 0.022, 1.27, 'metal_light')
    # Screens on stands at the back of the desk, tilted a little.
    frames = group('console_screen_frames', 'console', 'metal')
    screens = group('console_screens', 'console', 'glow', anim='screen')
    for (x0, x1, y0, y1) in ((-0.48, 0.48, 1.02, 1.52), (-1.38, -0.62, 1.0, 1.4), (0.62, 1.38, 1.0, 1.4)):
        tilt = 0.08
        z_back = 0.74
        box(frames, (x0 + x1) / 2 - 0.04, 0.95, z_back - 0.03, (x0 + x1) / 2 + 0.04, y0 + 0.05, z_back + 0.03, 'metal_dark')
        # The frame: a thin slab leaning back.
        c = [(x0 - 0.03, y0 - 0.03, z_back - 0.03), (x1 + 0.03, y0 - 0.03, z_back - 0.03), (x1 + 0.03, y0 - 0.03, z_back + 0.03), (x0 - 0.03, y0 - 0.03, z_back + 0.03),
             (x0 - 0.03, y1 + 0.03, z_back - 0.03 - tilt), (x1 + 0.03, y1 + 0.03, z_back - 0.03 - tilt), (x1 + 0.03, y1 + 0.03, z_back + 0.03 - tilt), (x0 - 0.03, y1 + 0.03, z_back + 0.03 - tilt)]
        hexa(frames, c, 'screen_frame')
        zf = z_back + 0.032
        screens.quad((x0, y0, zf), (x1, y0, zf), (x1, y1, zf - tilt), (x0, y1, zf - tilt), uv('screen', 0, 1), uv('screen', 1, 1), uv('screen', 1, 0), uv('screen', 0, 0))
    lights = group('console_lights', 'console', 'glow', anim='lights')
    box(lights, -1.42, 0.62, 1.38, 1.42, 0.65, 1.385, 'light', faces='s')
    holo = group('console_projector', 'console', 'metal', detail=1)
    cylinder(holo, 0.0, 0.92, 0.95, 0.99, 0.08, 12, 'metal_light')


# ---------------------------------------------------------------- the links (only once assembled)

def links():
    plate = group('link_floor', 'link', 'metal')
    box(plate, -1.5, 0.0, -1.5, 1.5, 0.025, 1.5, 'metal_dark', density=0.3, faces='u')
    trim = group('link_floor_trim', 'link', 'glow', anim='lights', detail=1)
    for z in (-1.48, 1.48):
        box(trim, -1.48, 0.025, z - 0.01, 1.48, 0.03, z + 0.01, 'light', faces='u')
    cables = group('link_cables', 'link', 'metal')
    # From each energy cell up and over to the chamber's cap.
    for z in (0.12, -0.5, -1.12):
        tube(cables, bezier((-1.12, 2.62, z), (-1.1, 3.3, z), (-0.95, 3.75, z * 0.4), (-0.80, 3.68, z * 0.25), 14), 0.04, 8, 'cable')
    # From the reactor's top to the cap: two arches.
    for x in (-0.18, 0.18):
        tube(cables, bezier((x, 3.98, -1.0), (x, 4.35, -0.9), (x, 4.32, -0.45), (x * 0.8, 3.98, -0.3), 14), 0.05, 8, 'cable')
    # From the analyser's gantry down to the chamber's base.
    tube(cables, bezier((1.32, 1.95, -0.5), (1.1, 2.4, -0.45), (0.95, 0.9, -0.3), (0.80, 0.30, -0.25), 14), 0.04, 8, 'cable')
    # From the console under the desk to the base.
    pipes = group('link_pipes', 'link', 'metal', detail=1)
    for x in (-0.35, 0.35):
        tube(pipes, bezier((x, 0.2, 0.66), (x, 0.2, 0.6), (x * 0.9, 0.2, 0.72), (x * 0.85, 0.2, 0.78), 4), 0.045, 6, 'pipe')
    for x in (-0.25, 0.25):
        tube(pipes, bezier((x, 0.15, -0.55), (x, 0.15, -0.65), (x, 0.12, -0.7), (x, 0.1, -0.75), 4), 0.045, 6, 'pipe')


def main():
    chamber()
    reactor()
    power()
    analyzer()
    console()
    links()
    out = {'version': 1, 'groups': []}
    total = 0
    for g in GROUPS:
        if not g.v:
            continue
        total += g.triangles
        rounded = []
        for i, value in enumerate(g.v):
            k = i % 8
            rounded.append(round(value, 4 if k < 3 else (3 if k < 6 else 5)))
        out['groups'].append({
            'name': g.name, 'module': g.module, 'material': g.material, 'variant': g.variant, 'anim': g.anim,
            'pivot': list(g.pivot), 'axis': list(g.axis), 'detail': g.detail, 'v': rounded,
        })
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(out, f, separators=(',', ':'))
    # Bounds per module, a check of the layout.
    for module in ('console', 'chamber', 'reactor', 'power', 'analyzer', 'link'):
        xs, ys, zs, tris = [], [], [], 0
        for g in GROUPS:
            if g.module != module:
                continue
            tris += g.triangles
            xs += g.v[0::8]
            ys += g.v[1::8]
            zs += g.v[2::8]
        print('%-9s %5d triangles  x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f' % (module, tris, min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    print('%d groups, %d triangles, %d bytes -> %s' % (len(out['groups']), total, os.path.getsize(OUT), OUT))


if __name__ == '__main__':
    main()
