"""
Raid den 3D model: converts the den model (Pixelmon-style binary .bmd, compiled from
an SMD) into the mesh read by RaidDenModel.kt, and writes the glow texture of the pit.

    python tools/raid_den_model.py

Input:  tools/models/raid_den.bmd (and its texture, already at
        assets/generations_core/textures/block/raid_den/den_model.png)
Output: assets/generations_core/raid_den/raid_den_mesh.json
        assets/generations_core/textures/block/raid_den/glow.png

The .bmd layout (big-endian, as Java's DataOutputStream writes it):
    byte    version
    short   node count, then per node: short id, short parent, UTF-16 name, 0
    short   frame count, then per frame: short node count, then per node:
            short id, 6 floats (position, rotation)
    short   material count, then per material: UTF-16 name, 0, short triangle count,
            then per triangle: byte flags, and 3 vertices of
            short bone, 8 floats (position, normal, u, v), byte link count
            (+ link count x (short bone, float weight))
The model is Z-up in model units; Minecraft is Y-up: (x, y, z) -> (x, z, -y).
"""
import json
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, 'tools', 'models', 'raid_den.bmd')
ASSETS = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', 'generations_core')
MESH = os.path.join(ASSETS, 'raid_den', 'raid_den_mesh.json')
GLOW = os.path.join(ASSETS, 'textures', 'block', 'raid_den', 'glow.png')


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack(fmt, self.data[self.pos:self.pos + size])
        self.pos += size
        return value if len(value) > 1 else value[0]

    def text(self):
        chars = []
        while True:
            c = self.take('>H')
            if c == 0:
                return ''.join(chars)
            chars.append(chr(c))


def read_bmd(path):
    r = Reader(open(path, 'rb').read())
    r.take('>B')
    for _ in range(r.take('>H')):
        r.take('>hh')
        r.text()
    for _ in range(r.take('>H')):
        for _ in range(r.take('>H')):
            r.take('>h6f')
    triangles = []
    for _ in range(r.take('>H')):
        r.text()
        for _ in range(r.take('>H')):
            r.take('>B')
            triangle = []
            for _ in range(3):
                r.take('>h')
                triangle.append(r.take('>8f'))
                for _ in range(r.take('>B')):
                    r.take('>hf')
            triangles.append(triangle)
    assert r.pos == len(r.data), 'unread bytes: the .bmd layout differs'
    return triangles


def write_png(path, w, h, rgba):
    raw = b''.join(b'\x00' + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def glow_texture(size=64):
    """White, bright in the middle, fading to the edges of the square."""
    pixels = bytearray()
    for y in range(size):
        for x in range(size):
            dx = abs((x + 0.5) / size * 2 - 1)
            dy = abs((y + 0.5) / size * 2 - 1)
            edge = max(dx, dy)
            alpha = max(0.0, 1 - edge) ** 0.8 * 0.6 + max(0.0, 1 - math.hypot(dx, dy)) ** 2 * 0.4
            pixels += bytes((255, 255, 255, min(255, int(alpha * 255))))
    return size, pixels


def main():
    triangles = read_bmd(SOURCE)
    out = []
    pit = 0
    for triangle in triangles:
        # The pit: the thin slab at the bottom, textured with the black corner.
        is_pit = all(v[2] < 0.1 and v[6] > 0.85 and v[7] < 0.2 for v in triangle)
        pit += is_pit
        vertices = []
        for x, y, z, nx, ny, nz, u, v in triangle:
            vertices += [round(x, 5), round(z, 5), round(-y, 5), round(nx, 5), round(nz, 5), round(-ny, 5), round(u, 5), round(1 - v, 5)]
        out.append({'pit': is_pit, 'v': vertices})
    os.makedirs(os.path.dirname(MESH), exist_ok=True)
    with open(MESH, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'source': 'tools/models/raid_den.bmd', 'triangles': out}, f, separators=(',', ':'))
    size, pixels = glow_texture()
    write_png(GLOW, size, size, pixels)
    print(f'{len(out)} triangles ({pit} in the pit) -> {MESH}')
    print(f'glow -> {GLOW}')


if __name__ == '__main__':
    main()
