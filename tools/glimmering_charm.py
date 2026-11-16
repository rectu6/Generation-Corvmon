"""Draws the Glimmering Charm's texture (32 x 32), in the style of the other charms.

    python tools/glimmering_charm.py <Generations-Core>

The cord is the Shiny Charm's (its loop and its tail, recoloured in violet), the pendant
a faceted crystal like a Tera jewel; the dark outline is added around everything, as
on the other charms. Pure Python (no PIL on this PC). Writes
common/src/main/resources/assets/generations_core/textures/item/player_items/glimmering_charm.png.
"""
import os
import struct
import sys
import zlib

OUTLINE = (0x20, 0x20, 0x20, 255)
CORD_DARK = (0x7a, 0x52, 0xd6, 255)
CORD_LIGHT = (0xa8, 0x84, 0xf6, 255)
TOP_LEFT = (0xff, 0xb8, 0xf4, 255)
TOP_RIGHT = (0xd2, 0x8a, 0xff, 255)
BOTTOM_LEFT = (0x8c, 0xe6, 0xff, 255)
BOTTOM_RIGHT = (0x6c, 0x7e, 0xff, 255)
RIDGE = (0xf4, 0xdc, 0xff, 255)
SHINE = (0xff, 0xff, 0xff, 255)
GLINT = (0xff, 0xfb, 0xd0, 255)


def read_png(path):
    data = open(path, 'rb').read()
    i, idat, plte, trns = 8, b'', None, None
    while i < len(data):
        length = struct.unpack('>I', data[i:i + 4])[0]
        kind, chunk = data[i + 4:i + 8], data[i + 8:i + 8 + length]
        i += 12 + length
        if kind == b'IHDR':
            w, h, depth, colour = struct.unpack('>IIBB', chunk[:10])
        elif kind == b'IDAT':
            idat += chunk
        elif kind == b'PLTE':
            plte = chunk
        elif kind == b'tRNS':
            trns = chunk
    channels = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[colour]
    bpp = max(1, channels * depth // 8)
    stride = (w * channels * depth + 7) // 8
    raw = zlib.decompress(idat)
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        kind = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    pixels = []
    for y in range(h):
        row = []
        for x in range(w):
            if colour == 6:
                row.append(tuple(rows[y][x * 4:x * 4 + 4]))
            elif colour == 2:
                row.append(tuple(rows[y][x * 3:x * 3 + 3]) + (255,))
            else:
                per = 8 // depth
                k = rows[y][x] if depth == 8 else (rows[y][x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1)
                alpha = trns[k] if trns and k < len(trns) else 255
                row.append(tuple(plte[k * 3:k * 3 + 3]) + (alpha,))
        pixels.append(row)
    return w, h, pixels


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    items = os.path.join(root, 'common', 'src', 'main', 'resources', 'assets', 'generations_core', 'textures', 'item')
    _, _, shiny = read_png(os.path.join(items, 'unimplemented', 'shiny_charm.png'))
    blue_dark, blue_light = (0x52, 0x7b, 0xe6), (0x5a, 0x9c, 0xf6)
    size = 32
    fill = [[None] * size for _ in range(size)]

    # The cord: the Shiny Charm's loop (rows 5-10) and tail (rows 22-26, left part),
    # outline included, the blues turned violet.
    base = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            px = shiny[y][x]
            if px[3] == 0:
                continue
            if 5 <= y <= 10 or (22 <= y <= 26 and x <= 15):
                if px[:3] == blue_dark:
                    base[y][x] = CORD_DARK
                elif px[:3] == blue_light:
                    base[y][x] = CORD_LIGHT
                elif px[:3] == OUTLINE[:3]:
                    base[y][x] = OUTLINE
    # Links from the loop and the tail to the crystal.
    for x, y in ((22, 11), (21, 12)):
        fill[y][x] = CORD_DARK
    for x, y in ((14, 21), (13, 22)):
        fill[y][x] = CORD_LIGHT

    # The crystal: a diamond with four facets, a ridge and a shine.
    cx, cy, rx, ry = 17, 16, 5.0, 5.5
    for y in range(size):
        for x in range(size):
            if abs(x - cx) / rx + abs(y - cy) / ry > 1.0:
                continue
            if y < cy:
                colour = TOP_LEFT if x < cx else TOP_RIGHT
            elif y > cy:
                colour = BOTTOM_LEFT if x < cx else BOTTOM_RIGHT
            else:
                colour = RIDGE
            if x == cx and y != cy:
                colour = RIDGE
            fill[y][x] = colour
    for x, y in ((15, 13), (16, 12), (15, 14)):
        fill[y][x] = SHINE

    # The dark outline around the crystal and the links (4 neighbours), as on the other charms.
    out = [row[:] for row in base]
    for y in range(size):
        for x in range(size):
            if fill[y][x] is not None:
                out[y][x] = fill[y][x]
    for y in range(size):
        for x in range(size):
            if fill[y][x] is None and out[y][x][3] == 0 and any(
                    0 <= x + dx < size and 0 <= y + dy < size and fill[y + dy][x + dx] is not None
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = OUTLINE
    # A glint beside the crystal, without outline.
    for x, y in ((25, 18), (24, 19), (25, 19), (26, 19), (25, 20)):
        if out[y][x][3] == 0:
            out[y][x] = GLINT if (x, y) == (25, 19) else (0xff, 0xf0, 0xa0, 200)

    target = os.path.join(items, 'player_items', 'glimmering_charm.png')
    write_png(target, out)
    print('written', target)


if __name__ == '__main__':
    main()
