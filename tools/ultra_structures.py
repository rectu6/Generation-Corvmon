"""
Ultra Space structures (phase 4.3.1): writes, into common/src/main/resources/data/generations_core,
- structure/<world>/...nbt             the templates (Minecraft's structure format, 1.21.1),
                                       drawn here block by block from the plans of
                                       tools/ultra_structures_<world>.py
- worldgen/template_pool/<world>/...   the jigsaw pools (which pieces may come at a connector)
- worldgen/structure/<world>/...       the structures (minecraft:jigsaw)
- worldgen/structure_set/<world>/...   where they are tried (generations_core:ultra_spread:
                                       Minecraft's random spread, its numbers in the server's
                                       ultra_structures.json)
- tags/worldgen/biome/has_structure/<world>/...  the biomes of each structure

Minecraft assembles and places them itself (jigsaw), chunk by chunk, from the seed.
No template is made in game: everything is reproducible from these scripts.

    python tools/ultra_structures.py [preview folder]

With a folder, a PNG of each template is drawn there (top view, plan at y 2, two side
views), to look at the plans without the game.

A template: x east, y up, z south. Layer 0 is in the ground (Minecraft puts a jigsaw
piece's layer 1 on the first free block: its layer 0 replaces the top of the ground),
so floors are at y 0 and rooms start at y 1. A cell left unset keeps the terrain.
"""
import gzip
import json
import os
import random
import shutil
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'data', 'generations_core')
NS = 'generations_core'
DATA_VERSION = 3955  # Minecraft 1.21.1
AIR = 'minecraft:air'

DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPPOSITE = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}


# ---------------------------------------------------------------- NBT (written by hand: no library needed)

def _str(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag(v):
    if isinstance(v, bool):
        raise TypeError('no booleans in these files')
    if isinstance(v, int):
        return 3
    if isinstance(v, str):
        return 8
    if isinstance(v, dict):
        return 10
    if isinstance(v, list):
        return 9
    raise TypeError(type(v))


def _payload(v):
    if isinstance(v, int):
        return struct.pack('>i', v)
    if isinstance(v, str):
        return _str(v)
    if isinstance(v, dict):
        return b''.join(bytes([_tag(x)]) + _str(k) + _payload(x) for k, x in v.items()) + b'\x00'
    kind = _tag(v[0]) if v else 0
    return bytes([kind]) + struct.pack('>i', len(v)) + b''.join(_payload(x) for x in v)


def nbt_bytes(root):
    return gzip.compress(b'\x0a' + _str('') + _payload(root), mtime=0)


# ---------------------------------------------------------------- blocks

def norm(spec):
    """'stone', 'g:crate' (Generations), 'quartz_stairs[facing=east]' -> the full name, properties sorted."""
    props = {}
    name = spec
    if '[' in spec:
        name, rest = spec[:-1].split('[', 1)
        props = dict(p.split('=') for p in rest.split(','))
    if name.startswith('g:'):
        name = NS + ':' + name[2:]
    elif ':' not in name:
        name = 'minecraft:' + name
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


def split(block):
    if '[' not in block:
        return block, {}
    name, rest = block[:-1].split('[', 1)
    return name, dict(p.split('=') for p in rest.split(','))


def with_props(block, **props):
    name, old = split(norm(block))
    old.update({k: str(v) for k, v in props.items()})
    return name + '[' + ','.join(f'{k}={v}' for k, v in sorted(old.items())) + ']'


class Mix:
    """A block drawn at random each time: Mix(('white_concrete', 5), ('moss_block', 1))."""

    def __init__(self, *pairs):
        self.blocks = [norm(b) for b, _ in pairs]
        self.weights = [w for _, w in pairs]

    def pick(self, rng):
        return rng.choices(self.blocks, self.weights)[0]


# Blocks that join their neighbours: their sides are worked out when the template is written
# (Minecraft does not update the shapes of a jigsaw piece's blocks).
def _joins(name):
    short = name.split(':')[1]
    if short.endswith('_pane') or short == 'iron_bars' or short.endswith('glass_pane'):
        return 'pane'
    if short.endswith('_fence'):
        return 'fence'
    if short.endswith('_wall') and 'sign' not in short:
        return 'wall'
    return None


NOT_FULL = ('air', 'stairs', 'slab', 'pane', 'bars', 'fence', 'wall', 'ladder', 'torch', 'lantern', 'carpet', 'rod', 'chain', 'door',
            'button', 'lever', 'pot', 'plate', 'jigsaw', 'candle', 'rail', 'vine', 'grass', 'fern', 'bush', 'water', 'lectern', 'stand',
            'cauldron', 'detector', 'sapling', 'azalea', 'dripleaf', 'head', 'banner', 'bed', 'pickle', 'light', 'cobweb', 'sign', 'anvil',
            'grindstone', 'bell', 'hopper', 'campfire', 'petals', 'roots', 'amethyst_cluster', 'comparator', 'repeater', 'tripwire')


def is_full(block):
    short = split(block)[0].split(':')[1]
    if short in ('lightning_lantern', 'crystal_light', 'sea_lantern', 'redstone_lamp', 'glowstone', 'shroomlight', 'moss_block', 'rooted_dirt',
                 'packed_ice', 'smooth_stone', 'polished_andesite', 'polished_deepslate', 'daylight_detector_base'):
        return True
    return not any(k in short for k in NOT_FULL)


# ---------------------------------------------------------------- a template

class Template:
    def __init__(self, name, sx, sy, sz):
        self.name = name
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.nbt = {}
        self.rng = random.Random(name)

    # -- single blocks
    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def set(self, x, y, z, block):
        if not self.inside(x, y, z):
            raise ValueError(f'{self.name}: ({x}, {y}, {z}) is outside {self.size}')
        if block is None:
            self.blocks.pop((x, y, z), None)
            return
        self.blocks[(x, y, z)] = block.pick(self.rng) if isinstance(block, Mix) else norm(block)
        self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    # -- volumes (all bounds inclusive)
    def fill(self, x0, y0, z0, x1, y1, z1, block, only=None, chance=1.0):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if only is not None and self.blocks.get((x, y, z)) not in only:
                        continue
                    if chance < 1.0 and self.rng.random() >= chance:
                        continue
                    self.set(x, y, z, block)

    def walls(self, x0, y0, z0, x1, y1, z1, block):
        self.fill(x0, y0, z0, x1, y1, z0, block)
        self.fill(x0, y0, z1, x1, y1, z1, block)
        self.fill(x0, y0, z0, x0, y1, z1, block)
        self.fill(x1, y0, z0, x1, y1, z1, block)

    def room(self, x0, z0, x1, z1, height, wall, floor, roof):
        """A closed box: floor at y 0, air from 1 to height, roof at height + 1."""
        self.fill(x0, 0, z0, x1, 0, z1, floor)
        self.fill(x0, 1, z0, x1, height, z1, AIR)
        self.walls(x0, 1, z0, x1, height, z1, wall)
        self.fill(x0, height + 1, z0, x1, height + 1, z1, roof)

    def disc(self, cx, y, cz, r, block, ring=False, only=None):
        """A filled circle (or its outline) in the horizontal plane; r may be x.5."""
        n = int(r) + 1
        for dx in range(-n, n + 1):
            for dz in range(-n, n + 1):
                d = (dx * dx + dz * dz) ** 0.5
                if d > r + 0.01 or (ring and d < r - 1.0):
                    continue
                if not self.inside(cx + dx, y, cz + dz):
                    continue
                if only is not None and self.blocks.get((cx + dx, y, cz + dz)) not in only:
                    continue
                self.set(cx + dx, y, cz + dz, block)

    def dome(self, cx, y, cz, r, block, inner=AIR):
        """A hemisphere shell standing on y, its inside emptied."""
        n = int(r) + 1
        for dx in range(-n, n + 1):
            for dy in range(0, n + 1):
                for dz in range(-n, n + 1):
                    d = (dx * dx + dy * dy + dz * dz) ** 0.5
                    if d > r + 0.01 or not self.inside(cx + dx, y + dy, cz + dz):
                        continue
                    self.set(cx + dx, y + dy, cz + dz, block if d >= r - 1.0 else inner)

    def vault(self, x0, x1, y, z0, z1, rise, block, inner=AIR):
        """A barrel vault along z over [x0, x1], springing at y and rising `rise` in the middle."""
        cx = (x0 + x1) / 2.0
        half = (x1 - x0) / 2.0
        for x in range(x0, x1 + 1):
            t = (x - cx) / half if half else 0.0
            top = y + int(round(rise * max(0.0, 1.0 - t * t) ** 0.5))
            for z in range(z0, z1 + 1):
                for yy in range(y, top + 1):
                    self.set(x, yy, z, block if yy == top else inner)

    def scatter(self, x0, y0, z0, x1, y1, z1, block, count, on_floor=True):
        """`count` blocks at random places of the box, standing on something when on_floor."""
        done = 0
        for _ in range(count * 30):
            if done >= count:
                break
            x, y, z = self.rng.randint(x0, x1), self.rng.randint(y0, y1), self.rng.randint(z0, z1)
            here = self.blocks.get((x, y, z))
            below = self.blocks.get((x, y - 1, z))
            if here not in (None, AIR) or (on_floor and (below in (None, AIR) or not is_full(below))):
                continue
            self.set(x, y, z, block)
            done += 1

    # -- pieces of building
    def stairs(self, x, y, z, block, facing, half='bottom'):
        self.set(x, y, z, with_props(block, facing=facing, half=half, shape='straight', waterlogged='false'))

    def ladder(self, x, y0, y1, z, facing):
        """facing: the side the ladder looks at (the wall is behind it)."""
        for y in range(y0, y1 + 1):
            self.set(x, y, z, f'ladder[facing={facing},waterlogged=false]')

    def side_cells(self, side, along, depth=0):
        """The (x, z) of a place along one of the template's sides, `depth` cells inside."""
        sx, _, sz = self.size
        if side == 'north':
            return along, depth
        if side == 'south':
            return along, sz - 1 - depth
        if side == 'west':
            return depth, along
        return sx - 1 - depth, along

    def jigsaw(self, x, y, z, facing, name, target, pool, final=AIR, joint='aligned'):
        """facing: a side (north, south, west, east), or up / down (a floor stacked on another: both pieces keep the
        same turn, their norths together)."""
        self.set(x, y, z, f'jigsaw[orientation={facing}_north]' if facing in ('up', 'down') else f'jigsaw[orientation={facing}_up]')
        self.nbt[(x, y, z)] = {
            'id': 'minecraft:jigsaw', 'name': _id(name), 'target': _id(target), 'pool': _id(pool) if pool != 'minecraft:empty' else pool,
            'final_state': _final(final), 'joint': joint, 'placement_priority': 0, 'selection_priority': 0,
        }

    def door(self, side, along, pool, name='door', target='tube_end', frame=None, y=1, width=3, height=3):
        """An opening in one of the template's sides (centred on `along`), with its connector at the bottom middle."""
        half = width // 2
        for a in range(along - half - 1, along + half + 2):
            for yy in range(y - 1 + 1, y + height + 1):
                x, z = self.side_cells(side, a)
                edge = a < along - half or a > along + half or yy == y + height
                if edge:
                    if frame and self.inside(x, yy, z):
                        self.set(x, yy, z, frame)
                else:
                    self.set(x, yy, z, AIR)
        x, z = self.side_cells(side, along)
        self.jigsaw(x, y, z, side, name, target, pool)

    def windows(self, side, y0, y1, start, end, step, glass, width=1):
        for a in range(start, end + 1, step):
            for w in range(width):
                if a + w > end:
                    continue
                x, z = self.side_cells(side, a + w)
                for yy in range(y0, y1 + 1):
                    self.set(x, yy, z, glass)

    # -- writing
    def _joined(self):
        """The blocks, panes / fences / walls joined to their neighbours."""
        out = dict(self.blocks)
        for (x, y, z), block in self.blocks.items():
            name, props = split(block)
            kind = _joins(name)
            if not kind:
                continue
            sides = {}
            for side, (dx, dz) in DIRS.items():
                other = self.blocks.get((x + dx, y, z + dz))
                joined = False
                if other and other != AIR:
                    other_kind = _joins(split(other)[0])
                    joined = is_full(other) or other_kind == kind or (kind == 'pane' and other_kind == 'pane')
                sides[side] = joined
            if kind == 'wall':
                above = self.blocks.get((x, y + 1, z))
                straight = (sides['north'] and sides['south'] and not sides['east'] and not sides['west']) or \
                           (sides['east'] and sides['west'] and not sides['north'] and not sides['south'])
                props.update({k: 'low' if v else 'none' for k, v in sides.items()})
                props['up'] = 'false' if straight and above in (None, AIR) else 'true'
                props['waterlogged'] = 'false'
            else:
                props.update({k: 'true' if v else 'false' for k, v in sides.items()})
                props['waterlogged'] = 'false'
            out[(x, y, z)] = name + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'
        return out

    def nbt_root(self):
        blocks = self._joined()
        palette, index, entries = [], {}, []
        # Full blocks first, the ones that lean on them after.
        order = sorted(blocks, key=lambda p: (0 if is_full(blocks[p]) else 1, p[1], p[0], p[2]))
        for pos in order:
            block = blocks[pos]
            if block not in index:
                index[block] = len(palette)
                name, props = split(block)
                entry = {'Name': name}
                if props:
                    entry['Properties'] = dict(sorted(props.items()))
                palette.append(entry)
            entry = {'pos': [pos[0], pos[1], pos[2]], 'state': index[block]}
            if pos in self.nbt:
                entry['nbt'] = self.nbt[pos]
            entries.append(entry)
        return {'DataVersion': DATA_VERSION, 'size': list(self.size), 'palette': palette, 'blocks': entries, 'entities': []}

    def check(self):
        """Connectors on the outside, facing out."""
        sx, sy, sz = self.size
        for (x, y, z) in self.nbt:
            name, props = split(self.blocks[(x, y, z)])
            facing = props['orientation'].split('_')[0]
            dx, dz = DIRS.get(facing, (0, 0))
            if facing in DIRS and self.inside(x + dx, y, z + dz):
                raise ValueError(f'{self.name}: the connector at ({x}, {y}, {z}) faces {facing} but is not on that side')


def _id(name):
    return name if ':' in name else f'{NS}:{name}'


def _final(block):
    """A block as a jigsaw's final_state writes it: name[props]."""
    return norm(block)


# ---------------------------------------------------------------- everything of the worlds

class Pack:
    def __init__(self):
        self.templates = {}
        self.pools = {}
        self.structures = {}
        self.sets = {}
        self.tags = {}
        self.processors = {}

    def template(self, path, sx, sy, sz):
        t = Template(path, sx, sy, sz)
        self.templates[path] = t
        return t

    def pool(self, name, elements, fallback='minecraft:empty', processors='minecraft:empty'):
        """elements: [(template path, or None for nothing, weight)]."""
        out = []
        for path, weight in elements:
            if path is None:
                out.append({'weight': weight, 'element': {'element_type': 'minecraft:empty_pool_element'}})
                continue
            if path not in self.templates:
                raise ValueError(f'pool {name}: no template {path}')
            out.append({'weight': weight, 'element': {'element_type': 'minecraft:single_pool_element', 'location': _id(path),
                                                       'projection': 'rigid', 'processors': processors}})
        self.pools[name] = {'fallback': _id(fallback) if fallback != 'minecraft:empty' else fallback, 'elements': out}

    def jigsaw(self, name, start_pool, biomes, size, max_distance, adaptation='beard_thin', step='surface_structures', start_height=0,
               heightmap='WORLD_SURFACE_WG', start_jigsaw=None):
        tag = f'has_structure/{name}'
        self.tags[tag] = [_id(b) for b in biomes]
        s = {
            'type': 'minecraft:jigsaw', 'biomes': f'#{NS}:{tag}', 'step': step, 'spawn_overrides': {}, 'terrain_adaptation': adaptation,
            'start_pool': _id(start_pool), 'size': size, 'start_height': {'absolute': start_height}, 'max_distance_from_center': max_distance,
            'use_expansion_hack': False,
        }
        if heightmap:
            s['project_start_to_heightmap'] = heightmap
        if start_jigsaw:
            s['start_jigsaw_name'] = _id(start_jigsaw)
        self.structures[name] = s

    def cavern(self, name, start_pool, biomes, size, max_distance, min_y, max_y, clearance=8, reach=10, open_sides=5,
               adaptation='beard_thin', step='underground_structures'):
        """A jigsaw started in a cavern (generations_core:ultra_cavern, UltraCavernStructure): a floor between min_y and max_y with
        `clearance` blocks of air over it, and `open_sides` of the eight columns `reach` blocks away open at the same level."""
        tag = f'has_structure/{name}'
        self.tags[tag] = [_id(b) for b in biomes]
        self.structures[name] = {
            'type': f'{NS}:ultra_cavern', 'biomes': f'#{NS}:{tag}', 'step': step, 'spawn_overrides': {}, 'terrain_adaptation': adaptation,
            'start_pool': _id(start_pool), 'size': size, 'max_distance_from_center': max_distance, 'min_y': min_y, 'max_y': max_y,
            'clearance': clearance, 'reach': reach, 'open_sides': open_sides,
        }

    def structure_set(self, name, structures, spacing, separation, salt, exclusion=None):
        """structures: [(structure, weight)]; exclusion: (other set, chunks) kept away from."""
        placement = {'type': f'{NS}:ultra_spread', 'config': name, 'salt': salt, 'spacing': spacing, 'separation': separation}
        if exclusion:
            placement['exclusion_zone'] = {'other_set': _id(exclusion[0]), 'chunk_count': exclusion[1]}
        for s, _ in structures:
            if s not in self.structures:
                raise ValueError(f'set {name}: no structure {s}')
        self.sets[name] = {'structures': [{'structure': _id(s), 'weight': w} for s, w in structures], 'placement': placement}


def write_json(rel, value):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write('\n')


def write(pack, worlds):
    for sub in ('structure', 'worldgen/template_pool', 'worldgen/structure', 'worldgen/structure_set', 'tags/worldgen/biome/has_structure'):
        for w in worlds:
            shutil.rmtree(os.path.join(DATA, sub, w), ignore_errors=True)
    for path, t in pack.templates.items():
        t.check()
        file = os.path.join(DATA, 'structure', path + '.nbt')
        os.makedirs(os.path.dirname(file), exist_ok=True)
        with open(file, 'wb') as f:
            f.write(nbt_bytes(t.nbt_root()))
    # Each connector must find, in its pool, a piece that has the connector it asks for.
    names = {}
    for path, t in pack.templates.items():
        names[path] = {nbt['name'] for nbt in t.nbt.values()}
    for path, t in pack.templates.items():
        for nbt in t.nbt.values():
            pool = nbt['pool']
            if pool == 'minecraft:empty':
                continue
            key = pool.split(':', 1)[1]
            if key not in pack.pools:
                raise ValueError(f'{path}: a connector names the pool {pool}, which does not exist')
            members = [e['element']['location'].split(':', 1)[1] for e in pack.pools[key]['elements'] if 'location' in e['element']]
            if members and not any(nbt['target'] in names[m] for m in members):
                raise ValueError(f'{path}: no piece of {pool} has a connector named {nbt["target"]}')
    for name, pool in pack.pools.items():
        write_json(f'worldgen/template_pool/{name}.json', pool)
    for name, s in pack.structures.items():
        write_json(f'worldgen/structure/{name}.json', s)
    for name, s in pack.sets.items():
        write_json(f'worldgen/structure_set/{name}.json', s)
    for name, biomes in pack.tags.items():
        write_json(f'tags/worldgen/biome/{name}.json', {'replace': False, 'values': biomes})


# ---------------------------------------------------------------- previews (PNG written by hand, no library)

COLOURS = [('white_marble', 0xECECEC), ('light_gray_marble', 0x9A9A9A), ('gray_marble', 0x505050), ('black_marble', 0x242424),
           ('house_floor_4', 0xC9CEDD), ('subway_floor', 0x8C8C8C), ('volcanic', 0x7A3A32), ('crate', 0xC8A078), ('lightning_lantern', 0xFFF4B0),
           ('crystal_light', 0x7FE0E8), ('glass', 0x9FD6F2), ('quartz', 0xE8E4DA), ('concrete_powder', 0x3A3A3A), ('yellow_concrete', 0xF0C020),
           ('black_concrete', 0x151515), ('gray_concrete', 0x5A5E62), ('white_concrete', 0xDADFE0), ('iron_bars', 0x707880), ('chain', 0x505860),
           ('ladder', 0x9A7848), ('water', 0x3060D0), ('moss', 0x5A8A30), ('dirt', 0x7A5838), ('podzol', 0x6A4A28), ('jigsaw', 0xD040D0),
           ('deepslate', 0x3C3C42), ('andesite', 0x8A8A8A), ('smooth_stone', 0xA0A0A0), ('gravel', 0x80787A), ('detector', 0x304878),
           ('end_rod', 0xFFFFFF), ('carpet', 0xD8D8D8), ('leaves', 0x3F7A2A), ('azalea', 0x5F8A3A), ('barrel', 0x8A6030), ('blackstone', 0x2A2428),
           ('lectern', 0xB08850), ('cauldron', 0x404448), ('brewing', 0x807060), ('terracotta', 0xA05A40), ('cobweb', 0xD0D0D0),
           ('glowing_charge', 0xF0E060), ('charge_stone_brick', 0x6A6440), ('charge', 0x7A7450), ('basalt', 0x4A4A50), ('froglight', 0xF0D890),
           ('shingles', 0xA03030), ('calcite', 0xF2F2EE), ('bamboo', 0xC8B050), ('ultra_dark', 0x3A2A2A), ('cave_rock', 0x40405A),
           ('sea_lantern', 0xC0F0F0), ('concrete', 0x8A8A8A), ('bricks', 0x904838), ('stone', 0x909090)]


def colour(block):
    short = split(block)[0].split(':')[1]
    for key, c in COLOURS:
        if key in short:
            return c
    h = zlib.crc32(short.encode())
    return 0x404040 | (h & 0xBFBFBF)


def _shade(c, f):
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    return tuple(max(0, min(255, int(v * f))) for v in (r, g, b))


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


def preview(t, path, k=None):
    """Top view | plan at y 2 || view from the south | view from the east."""
    sx, sy, sz = t.size
    k = k or max(3, min(10, 480 // max(sx, sz, sy)))
    sky, void = (216, 232, 240), (46, 50, 58)
    gap = 6
    width = (sx + gap // 2 + sx) * k + gap
    height = (sz + sy) * k + gap * 2
    px = [[void] * (max(width, (sx + sz) * k + gap * 2)) for _ in range(height)]

    def put(x0, y0, c):
        for j in range(k):
            row = px[y0 + j]
            for i in range(k):
                row[x0 + i] = c
    solid = {p: b for p, b in t.blocks.items() if b != AIR}
    for x in range(sx):
        for z in range(sz):
            top = max((y for y in range(sy) if (x, y, z) in solid), default=None)
            if top is not None:
                put(x * k, z * k, _shade(colour(solid[(x, top, z)]), 0.65 + 0.35 * (top + 1) / sy))
            if (x, 2, z) in t.blocks:
                b = t.blocks[(x, 2, z)]
                put((sx + gap // 2) * k + x * k, z * k, (235, 235, 225) if b == AIR else _shade(colour(b), 1.0))
            elif (x, 1, z) in solid:
                put((sx + gap // 2) * k + x * k, z * k, _shade(colour(solid[(x, 1, z)]), 0.8))
    oy = sz * k + gap
    for x in range(sx):
        for y in range(sy):
            hit = next((solid[(x, y, z)] for z in range(sz - 1, -1, -1) if (x, y, z) in solid), None)
            put(x * k, oy + (sy - 1 - y) * k, _shade(colour(hit), 1.0) if hit else sky)
    ox = sx * k + gap
    for z in range(sz):
        for y in range(sy):
            hit = next((solid[(x, y, z)] for x in range(sx - 1, -1, -1) if (x, y, z) in solid), None)
            put(ox + z * k, oy + (sy - 1 - y) * k, _shade(colour(hit), 0.9) if hit else sky)
    png(path, px)


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import ultra_structures_crater
    import ultra_structures_jungle
    import ultra_structures_desert
    import ultra_structures_central
    import ultra_structures_forest
    import ultra_structures_abyss
    import ultra_structures_skyscraper
    pack = Pack()
    worlds = []
    for module in (ultra_structures_crater, ultra_structures_jungle, ultra_structures_desert, ultra_structures_central, ultra_structures_forest,
                   ultra_structures_abyss, ultra_structures_skyscraper):
        worlds.append(module.WORLD)
        module.build(sys.modules[__name__], pack)
    write(pack, worlds)
    if len(sys.argv) > 1:
        for path, t in pack.templates.items():
            preview(t, os.path.join(sys.argv[1], path.replace('/', '_') + '.png'))
        # The blocks the templates use, for the checks made outside the game.
        used = sorted({split(b)[0] for t in pack.templates.values() for b in t.blocks.values()})
        with open(os.path.join(sys.argv[1], 'blocks.txt'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(used) + '\n')
    blocks = sum(len(t.blocks) for t in pack.templates.values())
    print(f'{len(pack.templates)} templates ({blocks} blocks), {len(pack.pools)} pools, {len(pack.structures)} structures, {len(pack.sets)} sets')


if __name__ == '__main__':
    main()
