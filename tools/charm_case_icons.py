"""Textures of the charm case (build 54).

- gui/charm_case_button.png: the inventory's button, a closed case with a gold clasp
  (16x32: normal on top, hovered below).
- item/charm_case/empty_<name>.png: the empty slots' silhouettes, like the vanilla armor
  slots: the item itself in grey, faded into the slot (a flat outline is unreadable for
  charms this thin at 16 px).

Run from Generations-Core: python tools/charm_case_icons.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glimmering_charm import read_png, write_png  # noqa: E402

ROOT = os.path.join('common', 'src', 'main')
ASSETS = os.path.join(ROOT, 'resources', 'assets', 'generations_core', 'textures')
GENERATED = os.path.join(ROOT, 'generated', 'resources', 'assets', 'generations_core', 'textures')

# Slot silhouettes: name -> source texture (under textures/).
SILHOUETTES = {
    'shiny_charm': 'item/player_items/shiny_charm',
    'exp_charm': 'item/player_items/exp_charm',
    'catching_charm': 'item/player_items/catching_charm',
    'mark_charm': 'item/player_items/mark_charm',
    'oval_charm': 'item/player_items/oval_charm',
    'glimmering_charm': 'item/player_items/glimmering_charm',
    'mega_charm': 'item/player_items/mega_charm',
    'old_charm': 'item/unimplemented/old_charm',
    'mega': 'item/player_items/mega_bracelet',
    'dynamax': 'item/player_items/dynamax_band',
    'tera': 'item/player_items/tera_orb',
    'z_ring': 'item/player_items/z_ring',
}

DARK = 0x45
LIGHT = 0x82
CLEAR = (0, 0, 0, 0)

CASE = [
    '..............W.',
    '.............WYW',
    '..............W.',
    '..OOOOOOOOOOOO..',
    '.OhhhhhhhhhhhsO.',
    '.OhLLLLLLLLLLsO.',
    '.OhLLLLggLLLLsO.',
    '.OddddgGGgddddO.',
    '.OhBBBgGGgBBBsO.',
    '.OhBBBBggBBBBsO.',
    '.OhBBBBBBBBBBsO.',
    '.OhBBBBBBBBBBsO.',
    '.OSSSSSSSSSSSSO.',
    '..OOOOOOOOOOOO..',
    '................',
    '................',
]

PALETTE = {
    '.': CLEAR,
    'O': (0x3b, 0x1d, 0x24, 255),
    'h': (0xd8, 0x70, 0x6a, 255),
    'L': (0xb8, 0x43, 0x3f, 255),
    'B': (0xa8, 0x3b, 0x3a, 255),
    's': (0x7d, 0x2a, 0x2e, 255),
    'S': (0x6a, 0x22, 0x28, 255),
    'd': (0x5e, 0x1f, 0x25, 255),
    'g': (0xb9, 0x8a, 0x22, 255),
    'G': (0xff, 0xd8, 0x4a, 255),
    'W': (0xff, 0xff, 0xff, 255),
    'Y': (0xff, 0xf6, 0xb0, 255),
}


def source(name):
    for base in (ASSETS, GENERATED):
        path = os.path.join(base, name + '.png')
        if os.path.exists(path):
            return path
    raise FileNotFoundError(name)


def lighter(colour, amount=0.22):
    if colour[3] == 0 or colour == PALETTE['O']:
        return colour
    return tuple(min(255, int(c + (255 - c) * amount)) for c in colour[:3]) + (colour[3],)


def button():
    normal = [[PALETTE[c] for c in row] for row in CASE]
    hovered = [[lighter(p) for p in row] for row in normal]
    return normal + hovered


def silhouette(path):
    """The item in grey, faded into the slot's grey (#8b8b8b): its darkest lines DARK, its
    lightest parts LIGHT (contrast evened out, so a dark item is not a blot). Opaque, so no
    blending is needed (Slot.getNoItemIcon)."""
    w, h, pixels = read_png(path)
    lum = lambda p: 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]
    values = [lum(p) for row in pixels for p in row if p[3] >= 128]
    low, high = min(values), max(values)
    out = []
    for row in pixels:
        line = []
        for p in row:
            if p[3] < 128:
                line.append(CLEAR)
                continue
            t = (lum(p) - low) / (high - low) if high > low else 0.5
            grey = int(DARK + (LIGHT - DARK) * t)
            line.append((grey, grey, grey, 255))
        out.append(line)
    return out


def main():
    gui = os.path.join(ASSETS, 'gui')
    os.makedirs(gui, exist_ok=True)
    write_png(os.path.join(gui, 'charm_case_button.png'), button())
    slots = os.path.join(ASSETS, 'item', 'charm_case')
    os.makedirs(slots, exist_ok=True)
    for name, texture in SILHOUETTES.items():
        write_png(os.path.join(slots, 'empty_' + name + '.png'), silhouette(source(texture)))
    print('ok')


if __name__ == '__main__':
    main()
