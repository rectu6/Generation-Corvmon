"""
Ultra Crater (Celesteela): its structures, for tools/ultra_structures.py.

The great one: the abandoned lunar base, a jigsaw of modules joined by tube corridors
(a core with a control deck, laboratories, quarters, a greenhouse, an observation dome,
a hangar, a reactor, junctions, wrecked modules; stair tubes take it to other levels;
airlocks open on landing pads, antenna masts and solar arrays).
The small ones: a mini station on legs, an abandoned hangar, an observation post, a
research shelter, each in two variants.

White and grey marble hulls, the subway's metal floors, volcanic stone, crates, glowing
lanterns: Generations and vanilla blocks, nothing precious (no iron or copper block).
Connectors: a module's door is named `door` and asks the corridor pool for a `tube_end`;
a tube's end is named `tube_end` and asks the module pool for a `door`. Doors are 3 wide
and 3 high, their connector at the bottom middle; floors at y 0, so that everything
meets on one level (stair tubes climb 4).
"""

WORLD = 'ultra_crater'
P = WORLD + '/'

AIR = 'minecraft:air'
TRIM = 'g:light_gray_marble'
DARK = 'g:gray_marble'
FLOOR = 'g:subway_floor_plain'
PLATE = 'g:subway_floor_cross'
TILES = 'g:house_floor_4'
GLASS = 'light_blue_stained_glass'
LIGHT = 'g:lightning_lantern'
GLOW = 'g:crystal_light'
ROCK = 'g:smooth_volcanic_stone'
BRICK = 'g:volcanic_stone_bricks'
CRATE = 'g:crate'
BARS = 'iron_bars'
COUNTER = 'polished_deepslate_slab[type=top,waterlogged=false]'
SHELF = 'smooth_quartz_slab[type=top,waterlogged=false]'
SEAT = 'quartz_stairs'

CORRIDORS = P + 'base/corridors'
MODULES = P + 'base/modules'
ENTRANCE = P + 'base/entrance'
EXTERIOR = P + 'base/exterior'


def build(u, pack):
    hull = 'g:white_marble'
    dusty = u.Mix(('g:white_marble', 5), (TRIM, 3), ('g:white_poke_brick', 1))
    rubble = u.Mix(('gravel', 3), ('g:volcanic_cobblestone', 2), (TRIM, 2), ('black_concrete_powder', 1))

    def module(path, sx, sz, h, floor=FLOOR, wall=hull, lights=True):
        """A closed module over the whole template: floor at y 0, room up to h, roof at h + 1."""
        t = pack.template(P + path, sx, h + 2, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall, floor, wall)
        t.walls(0, 1, 0, sx - 1, 1, sz - 1, TRIM)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, DARK)
        if lights:
            for x in range(3, sx - 2, 4):
                for z in range(3, sz - 2, 4):
                    t.set(x, h + 1, z, LIGHT)
        return t

    def crates(t, x0, z0, x1, z1, count, top=2):
        """A heap of crates in a corner: columns of 1 to `top`."""
        for _ in range(count):
            x, z = t.rng.randint(x0, x1), t.rng.randint(z0, z1)
            if t.get(x, 1, z) != AIR:
                continue
            for y in range(1, 1 + t.rng.randint(1, top)):
                t.set(x, y, z, CRATE if t.rng.random() < 0.8 else 'barrel[facing=up,open=false]')

    def breach(t, cx, cy, cz, r, floor=None):
        """A hole torn in a module: everything within r of the point goes, rubble on what floor is left."""
        sx, sy, sz = t.size
        for x in range(sx):
            for y in range(sy):
                for z in range(sz):
                    d = ((x - cx) ** 2 + (y - cy) ** 2 * 1.4 + (z - cz) ** 2) ** 0.5 + t.rng.random() * 1.2
                    if d > r or (x, y, z) in t.nbt:
                        continue
                    if y == 0:
                        t.set(x, y, z, floor or rubble)
                    else:
                        t.set(x, y, z, AIR)

    # A small rover: wheels, a body, a cabin (x0, y0, z0: its lowest corner; 3 x 3 x 5).
    def rover(t, x0, y0, z0):
        for z in (z0, z0 + 3):
            t.set(x0, y0, z, 'polished_blackstone')
            t.set(x0 + 2, y0, z, 'polished_blackstone')
        t.fill(x0, y0 + 1, z0, x0 + 2, y0 + 1, z0 + 4, 'white_concrete')
        t.fill(x0, y0 + 2, z0 + 3, x0 + 2, y0 + 2, z0 + 4, 'tinted_glass')
        t.set(x0 + 1, y0 + 2, z0, 'daylight_detector[inverted=false,power=0]')
        t.set(x0 + 1, y0 + 2, z0 + 1, CRATE)

    # ------------------------------------------------------------------ the core

    t = pack.template(P + 'base/core', 21, 19, 21)
    c = 10

    def in_core(x, z):
        dx, dz = abs(x - c), abs(z - c)
        return max(dx, dz) <= 10 and dx + dz <= 15

    def core_wall(x, z):
        return in_core(x, z) and not all(in_core(x + a, z + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    for x in range(21):
        for z in range(21):
            if not in_core(x, z):
                continue
            wall = core_wall(x, z)
            t.set(x, 0, z, DARK if wall else TILES)
            for y in range(1, 10):
                t.set(x, y, z, (TRIM if y in (1, 5) else hull) if wall else AIR)
            if not wall:
                t.set(x, 5, z, FLOOR)
            t.set(x, 10, z, DARK if wall else hull)
            # The control deck's band of windows, and a few portholes below.
            straight = abs(x - c) <= 4 or abs(z - c) <= 4
            if wall and straight and abs(x - c) != 0 and abs(z - c) != 0:
                t.set(x, 7, z, GLASS)
                t.set(x, 8, z, GLASS)
            if wall and (abs(x - c) == 4 or abs(z - c) == 4) and straight:
                t.set(x, 2, z, GLASS)
                t.set(x, 3, z, GLASS)
    # The central column, glowing rings at both levels.
    t.fill(9, 0, 9, 11, 10, 11, DARK)
    for y in (3, 8):
        t.walls(9, y, 9, 11, y, 11, GLOW)
    # Stairs up to the deck, along the south side, with a rail round the well.
    for i in range(5):
        t.stairs(5 + i, 1 + i, 15, 'quartz_stairs', 'east')
        for y in range(1, 1 + i):
            t.set(5 + i, y, 15, TRIM)
    t.fill(5, 5, 15, 8, 5, 15, AIR)
    for x in range(5, 9):
        t.set(x, 6, 14, BARS)
        t.set(x, 6, 16, BARS)
    t.set(4, 6, 15, BARS)
    t.set(4, 6, 14, BARS)
    t.set(4, 6, 16, BARS)
    # Ground floor: lights in the deck's floor, crates, benches round the column.
    for x, z in ((5, 5), (15, 5), (15, 15), (10, 4), (4, 10), (16, 10), (10, 16)):
        t.set(x, 5, z, LIGHT)
        t.set(x, 10, z, LIGHT)
    crates(t, 13, 12, 16, 15, 7)
    crates(t, 4, 4, 6, 6, 4)
    for x in (8, 12):
        t.stairs(x, 1, 10, SEAT, 'west' if x == 8 else 'east')
    # The deck: consoles under the north and east windows, seats, a chart table.
    for a in range(7, 14):
        t.set(a, 6, 1, COUNTER)
        t.set(19, 6, a, COUNTER)
    for a in (8, 10, 12):
        t.stairs(a, 6, 3, SEAT, 'south')
        t.set(a, 7, 1, 'lectern[facing=south,has_book=false,powered=false]' if a == 10 else AIR)
        t.stairs(17, 6, a, SEAT, 'west')
    t.fill(13, 6, 13, 14, 6, 14, SHELF)
    # The roof: a mast, a radome.
    for y in range(11, 18):
        t.set(c, y, c, BARS)
    t.set(c, 18, c, 'end_rod[facing=up]')
    t.dome(5, 11, 6, 2.5, 'g:white_marble', inner='g:white_marble')
    t.fill(13, 11, 13, 15, 11, 15, 'daylight_detector[inverted=false,power=0]')
    for side in ('north', 'east', 'south'):
        t.door(side, c, CORRIDORS, frame=DARK)
    t.door('west', c, ENTRANCE, frame=DARK)

    # ------------------------------------------------------------------ tubes (along z, 5 wide, 5 high)

    def tube(path, length, wall=hull, roof=hull, windows=False, width=5):
        o = (width - 5) // 2
        t = pack.template(P + path, width, 5, length)
        t.fill(o, 0, 0, o + 4, 0, length - 1, FLOOR)
        t.fill(o, 0, 0, o, 0, length - 1, DARK)
        t.fill(o + 4, 0, 0, o + 4, 0, length - 1, DARK)
        t.fill(o + 1, 1, 0, o + 3, 3, length - 1, AIR)
        t.fill(o, 1, 0, o, 3, length - 1, wall)
        t.fill(o + 4, 1, 0, o + 4, 3, length - 1, wall)
        t.fill(o, 4, 0, o + 4, 4, length - 1, roof)
        for z in range(2, length - 1, 4):
            t.set(o + 2, 4, z, LIGHT)
            if windows:
                t.set(o, 2, z, GLASS)
                t.set(o + 4, 2, z, GLASS)
        # A rib at each end.
        for z in (0, length - 1):
            t.fill(o, 1, z, o, 3, z, TRIM)
            t.fill(o + 4, 1, z, o + 4, 3, z, TRIM)
            t.fill(o, 4, z, o + 4, 4, z, TRIM)
        t.jigsaw(o + 2, 1, 0, 'north', 'tube_end', 'door', MODULES)
        t.jigsaw(o + 2, 1, length - 1, 'south', 'tube_end', 'door', MODULES)
        return t

    tube('base/tube_short', 7)
    tube('base/tube_long', 13, windows=True)
    t = tube('base/tube_glass', 9, wall=GLASS, roof=GLASS)
    for z in range(0, 9, 4):
        t.fill(0, 1, z, 0, 3, z, TRIM)
        t.fill(4, 1, z, 4, 3, z, TRIM)
        t.fill(0, 4, z, 4, 4, z, TRIM)
    t.set(2, 4, 4, LIGHT)

    # A tube broken in the middle: roof and one wall fallen, rubble inside and out.
    t = tube('base/tube_broken', 11, wall=dusty, roof=dusty, width=9)
    for z in range(3, 8):
        for x in range(2, 9):
            for y in range(1, 5):
                if x >= 5 or y == 4:
                    if t.rng.random() < 0.85:
                        t.set(x, y, z, AIR if 3 <= x <= 5 else None)
    t.fill(6, 1, 2, 8, 1, 8, AIR)
    t.scatter(3, 1, 3, 8, 1, 7, rubble, 12, on_floor=False)
    t.scatter(6, 0, 2, 8, 0, 8, rubble, 14, on_floor=False)
    t.set(4, 4, 2, AIR)
    t.set(4, 4, 6, None)

    # A stair tube: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'base/tube_stairs', 5, 9, 11)
    t.fill(0, 0, 0, 4, 0, 10, DARK)
    t.fill(1, 0, 0, 3, 0, 2, FLOOR)
    t.fill(1, 1, 0, 3, 7, 10, AIR)
    t.fill(0, 1, 0, 0, 7, 10, hull)
    t.fill(4, 1, 0, 4, 7, 10, hull)
    t.fill(0, 8, 0, 4, 8, 10, hull)
    for i in range(4):
        z = 3 + i
        for x in range(1, 4):
            t.stairs(x, 1 + i, z, 'smooth_quartz_stairs', 'south')
            for y in range(1, 1 + i):
                t.set(x, y, z, TRIM)
    t.fill(1, 1, 7, 3, 3, 10, TRIM)
    t.fill(1, 4, 7, 3, 4, 10, FLOOR)
    t.fill(0, 0, 7, 4, 3, 10, DARK, only=[None])
    t.fill(1, 4, 0, 3, 7, 0, hull)          # above the low door
    t.fill(1, 1, 10, 3, 4, 10, DARK)        # under the high door
    for z in (2, 5, 8):
        t.set(0, 5, z, GLASS)
        t.set(4, 5, z, GLASS)
        t.set(2, 8, z, LIGHT)
    t.jigsaw(2, 1, 0, 'north', 'tube_end', 'door', MODULES)
    t.jigsaw(2, 5, 10, 'south', 'tube_end', 'door', MODULES)

    # An airlock to the outside: a vestibule, an outer frame, a step; what lies beyond is drawn from `exterior`.
    t = pack.template(P + 'base/airlock', 5, 5, 5)
    t.fill(0, 0, 0, 4, 0, 4, DARK)
    t.fill(1, 0, 0, 3, 0, 4, PLATE)
    t.fill(1, 1, 0, 3, 3, 4, AIR)
    t.fill(0, 1, 0, 0, 3, 4, hull)
    t.fill(4, 1, 0, 4, 3, 4, hull)
    t.fill(0, 4, 0, 4, 4, 4, hull)
    for z in (0, 4):
        t.fill(0, 1, z, 0, 3, z, DARK)
        t.fill(4, 1, z, 4, 3, z, DARK)
        t.fill(0, 4, z, 4, 4, z, DARK)
    t.set(2, 4, 2, LIGHT)
    t.set(0, 2, 2, 'yellow_concrete')
    t.set(4, 2, 2, 'yellow_concrete')
    t.jigsaw(2, 1, 0, 'north', 'tube_end', 'door', MODULES)
    t.jigsaw(2, 0, 4, 'south', 'exit', 'pad', EXTERIOR, final=PLATE)

    # Closed ends: a window where a door or a tube leads nowhere.
    for name, connector, target, pool in (('base/cap_door', 'tube_end', 'door', MODULES), ('base/cap_tube', 'door', 'tube_end', CORRIDORS)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, DARK)
        t.fill(1, 1, 0, 3, 3, 0, GLASS)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=GLASS)

    # ------------------------------------------------------------------ modules

    # Laboratory.
    t = module('base/lab', 13, 13, 4, floor=TILES)
    t.windows('east', 2, 3, 3, 9, 3, GLASS, width=2)
    t.windows('west', 2, 3, 3, 9, 3, GLASS, width=2)
    for x in range(1, 12):
        t.set(x, 1, 11, COUNTER)
    for x, what in ((2, 'brewing_stand'), (4, 'flower_pot'), (6, 'brewing_stand'), (8, 'cauldron'), (10, 'flower_pot')):
        t.set(x, 2 if what != 'cauldron' else 1, 11, what)
    for cx in (3, 9):
        # A specimen tank: glass round a glowing heart.
        t.walls(cx - 1, 1, 5, cx + 1, 3, 7, GLASS)
        t.set(cx, 1, 6, GLOW)
        t.set(cx, 3, 6, GLOW)
        t.fill(cx - 1, 4, 5, cx + 1, 4, 7, SHELF)
    t.fill(5, 1, 8, 7, 1, 8, SHELF)
    t.set(6, 2, 8, 'lectern[facing=north,has_book=false,powered=false]')
    t.stairs(6, 1, 9, SEAT, 'north')
    t.door('north', 6, CORRIDORS, frame=DARK)
    t.fill(5, 1, 11, 7, 2, 11, AIR)
    t.door('south', 6, CORRIDORS, frame=DARK)

    # Crew quarters: bunks along the walls, a table.
    t = module('base/quarters', 11, 15, 4)
    for z in (2, 6, 10):
        for x in (1, 9):
            for y in (1, 3):
                t.fill(x, y, z, x, y, z + 1, SHELF)
                t.fill(x, y + 1, z, x, y + 1, z + 1, 'white_carpet' if y == 1 else 'light_gray_carpet')
            t.set(2 if x == 1 else 8, 1, z + 2, CRATE)
    t.fill(5, 1, 6, 5, 1, 8, COUNTER)
    for z in (6, 8):
        t.stairs(4, 1, z, SEAT, 'east')
        t.stairs(6, 1, z, SEAT, 'west')
    t.door('north', 5, CORRIDORS, frame=DARK)
    t.door('south', 5, CORRIDORS, frame=DARK)

    # Storage: heaps of crates.
    t = module('base/storage', 13, 11, 4, floor=PLATE)
    crates(t, 1, 5, 5, 9, 16, top=3)
    crates(t, 8, 2, 11, 9, 18, top=3)
    for z in range(2, 5):
        t.set(1, 2, z, SHELF)
        t.set(1, 3, z, SHELF)
    t.door('north', 6, CORRIDORS, frame=DARK)
    t.fill(6, 1, 1, 7, 3, 9, AIR)
    t.door('south', 6, CORRIDORS, frame=DARK)

    # Greenhouse: beds under a glass vault.
    t = pack.template(P + 'base/greenhouse', 11, 9, 17)
    t.fill(0, 0, 0, 10, 0, 16, FLOOR)
    t.fill(0, 1, 0, 10, 4, 16, AIR)
    t.walls(0, 1, 0, 10, 2, 16, hull)
    t.walls(0, 3, 0, 10, 4, 16, GLASS)
    t.vault(0, 10, 5, 0, 16, 3, GLASS)
    for z in (0, 16):
        for x in range(11):
            top = max(y for y in range(5, 9) if t.get(x, y, z) is not None)
            t.fill(x, 5, z, x, top, z, GLASS)
        t.fill(0, 1, z, 0, 4, z, DARK)
        t.fill(10, 1, z, 10, 4, z, DARK)
    soil = u.Mix(('moss_block', 3), ('rooted_dirt', 2), ('podzol', 2))
    plants = u.Mix(('fern', 4), ('azalea', 2), ('flowering_azalea', 1), ('dead_bush', 2), ('air', 4))
    for x0 in (2, 6):
        t.fill(x0, 1, 2, x0 + 2, 1, 14, soil)
        t.walls(x0 - 1, 1, 1, x0 + 3, 1, 15, TRIM)
        t.fill(x0, 2, 2, x0 + 2, 2, 14, plants)
    t.fill(5, 1, 1, 5, 1, 15, AIR)
    for z in (4, 8, 12):
        t.set(5, 0, z, GLOW)
    t.door('north', 5, CORRIDORS, frame=DARK)
    t.door('south', 5, CORRIDORS, frame=DARK)

    # Observation dome: a glass hemisphere, a telescope.
    t = pack.template(P + 'base/dome', 15, 11, 15)
    t.disc(7, 0, 7, 7, TILES)
    t.disc(7, 0, 7, 7, DARK, ring=True)
    for y in (1, 2):
        t.disc(7, y, 7, 7, AIR)
        t.disc(7, y, 7, 7, hull if y == 2 else TRIM, ring=True)
    t.dome(7, 3, 7, 7, GLASS)
    t.fill(7, 1, 8, 7, 2, 8, 'polished_deepslate')
    for i in range(4):
        t.set(7, 3 + i, 8 - i, DARK)
    t.set(7, 7, 4, 'tinted_glass')
    t.stairs(7, 1, 10, SEAT, 'north')
    for x in (3, 11):
        t.set(x, 1, 7, COUNTER)
        t.set(x, 0, 9, GLOW)
    t.door('north', 7, CORRIDORS, frame=DARK)
    t.fill(6, 1, 1, 8, 3, 1, AIR)

    # Hangar: a vault open to the south, a landing circle, a rover.
    t = pack.template(P + 'base/hangar', 25, 12, 21)
    t.fill(0, 0, 0, 24, 0, 20, PLATE)
    t.vault(0, 24, 6, 0, 20, 5, hull)
    t.fill(1, 1, 0, 23, 5, 20, AIR)
    t.fill(0, 1, 0, 0, 5, 20, hull)
    t.fill(24, 1, 0, 24, 5, 20, hull)
    t.fill(0, 1, 0, 0, 1, 20, TRIM)
    t.fill(24, 1, 0, 24, 1, 20, TRIM)
    for z in (0, 20):
        for x in range(25):
            top = max(y for y in range(6, 12) if t.get(x, y, z) is not None)
            t.fill(x, 1, z, x, top, z, hull if z == 0 else DARK)
    t.fill(5, 1, 20, 19, 7, 20, AIR)                       # the great door
    for x in range(5, 20):
        t.set(x, 0, 18, 'yellow_concrete' if x % 2 else 'black_concrete')
    t.disc(12, 0, 9, 5, 'yellow_concrete', ring=True)
    t.fill(11, 0, 7, 11, 0, 11, 'white_concrete')
    t.fill(13, 0, 7, 13, 0, 11, 'white_concrete')
    t.set(12, 0, 9, 'white_concrete')
    for z in range(2, 19, 4):
        t.set(12, 11, z, LIGHT)
        t.set(4, 9, z, LIGHT)
        t.set(20, 9, z, LIGHT)
    crates(t, 1, 1, 4, 6, 14, top=3)
    crates(t, 20, 12, 23, 18, 10, top=2)
    rover(t, 18, 1, 3)
    for z in range(10, 16):
        t.set(1, 1, z, COUNTER)
    t.set(1, 2, 11, 'grindstone[face=floor,facing=east]')
    t.set(1, 2, 14, 'cauldron')
    t.door('north', 12, CORRIDORS, frame=DARK)

    # Reactor: a glowing core in glass.
    t = module('base/reactor', 13, 13, 6, floor=PLATE, lights=False)
    t.disc(6, 0, 6, 3.5, 'yellow_concrete', ring=True)
    for y in range(1, 7):
        t.disc(6, y, 6, 2, GLASS, ring=True)
        t.set(6, y, 6, GLOW if y % 2 else LIGHT)
    t.disc(6, 7, 6, 2, DARK)
    for x, z in ((1, 6), (11, 6), (3, 11), (9, 11)):
        t.set(x, 1, z, COUNTER)
        t.set(x, 7, z, LIGHT)
    for a in (2, 10):
        t.fill(a, 1, 2, a, 5, 2, 'polished_deepslate_wall')
        t.fill(a, 1, 10, a, 5, 10, 'polished_deepslate_wall')
    t.windows('east', 3, 4, 5, 7, 1, GLASS)
    t.windows('west', 3, 4, 5, 7, 1, GLASS)
    t.door('north', 6, CORRIDORS, frame=DARK)
    t.door('south', 6, CORRIDORS, frame=DARK)

    # Junction: four doors, a planter.
    t = module('base/junction', 11, 11, 4)
    t.fill(4, 1, 4, 6, 1, 6, TRIM)
    t.fill(4, 2, 4, 6, 2, 6, u.Mix(('fern', 2), ('azalea', 1), ('air', 1)))
    t.set(5, 1, 5, 'moss_block')
    t.set(5, 2, 5, 'flowering_azalea')
    t.set(5, 5, 5, LIGHT)
    for side in ('north', 'east', 'south', 'west'):
        t.door(side, 5, CORRIDORS, frame=DARK)

    # Wrecked module: a corner torn off.
    t = module('base/wreck', 13, 13, 4, wall=dusty, lights=False)
    crates(t, 1, 1, 5, 4, 8)
    t.door('north', 6, CORRIDORS, frame=DARK)
    breach(t, 11, 3, 11, 7.5)
    t.scatter(1, 1, 1, 11, 1, 11, rubble, 14)
    t.scatter(1, 1, 1, 11, 3, 11, 'cobweb', 4, on_floor=False)

    # ------------------------------------------------------------------ outside the airlocks

    # Landing pad.
    t = pack.template(P + 'base/pad', 15, 4, 15)
    t.fill(0, 0, 0, 14, 0, 14, ROCK)
    t.walls(0, 0, 0, 14, 0, 14, BRICK)
    t.disc(7, 0, 7, 5, 'yellow_concrete', ring=True)
    t.fill(5, 0, 5, 5, 0, 9, 'white_concrete')
    t.fill(9, 0, 5, 9, 0, 9, 'white_concrete')
    t.fill(5, 0, 7, 9, 0, 7, 'white_concrete')
    t.fill(0, 1, 0, 14, 3, 14, AIR)
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        t.set(x, 1, z, 'polished_deepslate_wall')
        t.set(x, 2, z, LIGHT)
    crates(t, 11, 10, 13, 13, 4)
    t.fill(6, 0, 0, 8, 0, 0, PLATE)
    t.jigsaw(7, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=PLATE)

    # Antenna mast.
    t = pack.template(P + 'base/antenna', 7, 24, 7)
    t.fill(0, 0, 0, 6, 0, 6, ROCK)
    t.fill(0, 1, 0, 6, 3, 6, AIR)
    t.fill(2, 0, 2, 4, 1, 4, DARK)
    for y in range(2, 20):
        t.walls(2, y, 2, 4, y, 4, BARS)
        t.set(3, y, 3, AIR)
        if y % 5 == 0:
            t.fill(2, y, 2, 4, y, 4, TRIM)
    t.fill(1, 17, 3, 5, 17, 3, BARS)
    t.fill(3, 17, 1, 3, 17, 5, BARS)
    for y in range(20, 23):
        t.set(3, y, 3, BARS)
    t.set(3, 23, 3, 'end_rod[facing=up]')
    t.set(3, 19, 3, LIGHT)
    t.fill(3, 0, 0, 3, 0, 1, PLATE)
    t.jigsaw(3, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=PLATE)

    # Solar array.
    t = pack.template(P + 'base/solar', 13, 4, 11)
    t.fill(0, 0, 0, 12, 0, 10, ROCK)
    t.fill(6, 0, 0, 6, 0, 10, PLATE)
    t.fill(0, 1, 0, 12, 3, 10, AIR)
    for z in (2, 5, 8):
        for x in list(range(1, 5)) + list(range(8, 12)):
            t.set(x, 1, z, 'polished_deepslate_wall')
            t.set(x, 2, z, 'daylight_detector[inverted=false,power=0]')
    t.set(6, 1, 10, 'polished_deepslate_wall')
    t.set(6, 2, 10, LIGHT)
    t.jigsaw(6, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=PLATE)

    pack.pool(CORRIDORS, [(P + 'base/tube_short', 3), (P + 'base/tube_long', 3), (P + 'base/tube_glass', 2), (P + 'base/tube_stairs', 2),
                          (P + 'base/tube_broken', 1), (P + 'base/airlock', 2)], fallback=P + 'base/door_caps')
    pack.pool(P + 'base/door_caps', [(P + 'base/cap_door', 2), (P + 'base/airlock', 1)])
    pack.pool(ENTRANCE, [(P + 'base/airlock', 1)], fallback=P + 'base/door_caps')
    pack.pool(MODULES, [(P + 'base/lab', 3), (P + 'base/quarters', 2), (P + 'base/storage', 2), (P + 'base/greenhouse', 2), (P + 'base/dome', 2),
                        (P + 'base/hangar', 2), (P + 'base/reactor', 1), (P + 'base/junction', 3), (P + 'base/wreck', 2)], fallback=P + 'base/tube_caps')
    pack.pool(P + 'base/tube_caps', [(P + 'base/cap_tube', 1)])
    pack.pool(EXTERIOR, [(P + 'base/pad', 3), (P + 'base/antenna', 2), (P + 'base/solar', 2), (None, 2)])
    pack.pool(P + 'base/start', [(P + 'base/core', 1)])
    pack.jigsaw(P + 'lunar_base', P + 'base/start', [P + 'crater_plains', P + 'giant_craters', P + 'steam_fields'], size=7, max_distance=100)

    # ------------------------------------------------------------------ the small ones

    # Mini station: a capsule on legs, reached by a ladder under it.
    def station(path, wrecked):
        t = pack.template(P + path, 11, 14, 11)
        base = 0 if wrecked else 3
        if not wrecked:
            for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
                t.fill(x, 0, z, x, 2, z, DARK)
            t.fill(5, 0, 5, 5, 2, 5, DARK)
            t.ladder(5, 1, 3, 6, 'south')
            t.fill(1, 1, 1, 9, 2, 9, AIR, only=[None])
        wall = dusty if wrecked else hull
        t.fill(1, base, 1, 9, base, 9, FLOOR)
        t.fill(1, base + 1, 1, 9, base + 3, 9, AIR)
        t.walls(1, base + 1, 1, 9, base + 3, 9, wall)
        t.fill(1, base + 4, 1, 9, base + 4, 9, wall)
        for x, z in ((1, 1), (9, 1), (1, 9), (9, 9)):
            t.fill(x, base, z, x, base + 4, z, DARK)
        for a in (4, 5, 6):
            t.set(a, base + 2, 1, GLASS)
            t.set(9, base + 2, a, GLASS)
            t.set(1, base + 2, a, GLASS)
        t.set(5, base + 4, 5, LIGHT)
        t.fill(2, base + 1, 2, 4, base + 1, 2, COUNTER)
        t.set(3, base + 2, 2, 'lectern[facing=south,has_book=false,powered=false]')
        t.stairs(3, base + 1, 3, SEAT, 'north')
        t.fill(8, base + 1, 6, 8, base + 1, 7, SHELF)
        t.fill(8, base + 2, 6, 8, base + 2, 7, 'white_carpet')
        t.set(8, base + 1, 3, CRATE)
        t.set(7, base + 1, 2, CRATE)
        if wrecked:
            for x, z in ((0, 3), (10, 7), (4, 10)):
                t.fill(x, 0, z, x, 1, z, DARK)
            t.fill(5, 1, 9, 5, 2, 9, AIR)                 # a doorway torn in the side
            breach(t, 2, 3, 8, 4.5, floor=rubble)
            t.scatter(2, 1, 2, 8, 1, 8, rubble, 8)
        else:
            t.set(5, 3, 6, AIR)                           # the hatch
            for y in range(8, 12):
                t.set(7, y, 3, BARS)
            t.set(7, 12, 3, 'end_rod[facing=up]')
            t.fill(2, 8, 6, 4, 8, 8, 'daylight_detector[inverted=false,power=0]')
        return t

    station('sites/station', False)
    station('sites/station_wrecked', True)

    # Abandoned hangar.
    def hangar(path, collapsed):
        t = pack.template(P + path, 17, 10, 21)
        t.fill(0, 0, 0, 16, 0, 20, PLATE)
        t.vault(0, 16, 5, 0, 20, 4, dusty)
        t.fill(1, 1, 0, 15, 4, 20, AIR)
        t.fill(0, 1, 0, 0, 4, 20, dusty)
        t.fill(16, 1, 0, 16, 4, 20, dusty)
        for z in (0, 20):
            for x in range(17):
                top = max(y for y in range(5, 10) if t.get(x, y, z) is not None)
                t.fill(x, 1, z, x, top, z, dusty if z == 0 else DARK)
        t.fill(4, 1, 20, 12, 6, 20, AIR)
        t.fill(8, 1, 0, 8, 2, 0, AIR)                     # a back door
        for x in range(4, 13):
            t.set(x, 0, 18, 'yellow_concrete' if x % 2 else 'black_concrete')
        crates(t, 1, 1, 4, 8, 14, top=3)
        crates(t, 11, 2, 15, 6, 8, top=2)
        for z in range(11, 16):
            t.set(15, 1, z, COUNTER)
        t.set(15, 2, 13, 'grindstone[face=floor,facing=west]')
        t.set(8, 8, 5, LIGHT)
        if collapsed:
            for x in range(3, 15):
                for z in range(8, 17):
                    for y in range(5, 10):
                        if t.get(x, y, z) not in (None, AIR) and t.rng.random() < 0.8:
                            t.set(x, y, z, AIR)
            t.scatter(3, 1, 8, 14, 1, 16, rubble, 26)
            t.scatter(3, 2, 8, 14, 2, 16, rubble, 8)
        else:
            rover(t, 6, 1, 10)
            t.set(8, 8, 13, LIGHT)
        return t

    hangar('sites/hangar', False)
    hangar('sites/hangar_collapsed', True)

    # Observation post: a platform on four legs, a telescope, a dish.
    def post(path, fallen):
        t = pack.template(P + path, 9, 15, 9)
        for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
            t.set(x, 0, z, DARK)
            t.fill(x, 1, z, x, 7, z, BARS)
        t.fill(4, 0, 4, 4, 7, 4, DARK)
        t.ladder(4, 1, 8, 5, 'south')
        t.fill(0, 8, 0, 8, 8, 8, FLOOR)
        t.walls(0, 8, 0, 8, 8, 8, DARK)
        t.set(4, 8, 5, 'ladder[facing=south,waterlogged=false]')
        t.walls(0, 9, 0, 8, 9, 8, BARS)
        t.fill(1, 9, 1, 7, 11, 7, AIR)
        # The telescope, looking north-east.
        t.fill(2, 9, 2, 2, 10, 2, 'polished_deepslate')
        for i in range(3):
            t.set(2 + i, 11 + i, 2, DARK)
        t.set(5, 14, 2, 'tinted_glass')
        t.set(6, 9, 6, COUNTER)
        t.set(7, 9, 7, LIGHT)
        if fallen:
            # The dish has fallen to the foot of the tower.
            t.fill(6, 0, 3, 8, 0, 5, ROCK)
            t.set(7, 1, 4, 'smooth_quartz')
            for x, z in ((6, 4), (8, 4), (7, 3), (7, 5)):
                t.set(x, 1, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
            t.set(0, 9, 3, AIR)
            t.set(0, 9, 4, AIR)
        else:
            t.fill(6, 9, 2, 6, 11, 2, BARS)
            t.set(6, 12, 2, 'smooth_quartz')
            for x, z in ((5, 2), (7, 2), (6, 1), (6, 3)):
                t.set(x, 13, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
        return t

    post('sites/post', False)
    post('sites/post_fallen', True)

    # Research shelter: a low bunker of volcanic stone, one or two rooms and a store.
    def shelter(path, wing):
        sx = 17 if wing else 13
        t = pack.template(P + path, sx, 6, 11)
        t.room(0, 0, 12, 10, 3, BRICK, TILES, ROCK)
        t.fill(1, 4, 1, 11, 4, 9, ROCK)
        t.fill(2, 5, 2, 10, 5, 8, ROCK)
        t.fill(6, 1, 1, 6, 3, 9, hull)                    # the wall between the two rooms
        t.fill(6, 1, 5, 6, 2, 5, AIR)
        t.fill(3, 1, 10, 3, 2, 10, AIR)                   # the way in
        t.set(2, 2, 10, DARK)
        t.set(4, 2, 10, DARK)
        for x in (2, 9):
            t.set(x, 2, 0, GLASS)
        t.set(12 if not wing else 9, 2, 0, GLASS)
        t.set(3, 4, 5, LIGHT)
        t.set(9, 4, 5, LIGHT)
        # First room: a bench and crates; second: a bunk and a desk.
        t.fill(1, 1, 1, 4, 1, 1, COUNTER)
        t.set(2, 2, 1, 'brewing_stand')
        t.set(4, 2, 1, 'flower_pot')
        crates(t, 1, 6, 2, 8, 4)
        t.fill(11, 1, 2, 11, 1, 3, SHELF)
        t.fill(11, 2, 2, 11, 2, 3, 'white_carpet')
        t.fill(8, 1, 9, 10, 1, 9, COUNTER)
        t.set(9, 2, 9, 'lectern[facing=north,has_book=false,powered=false]')
        t.stairs(9, 1, 8, SEAT, 'south')
        if wing:
            t.room(12, 2, 16, 8, 3, BRICK, PLATE, ROCK)
            t.fill(12, 1, 5, 12, 2, 5, AIR)
            crates(t, 13, 3, 15, 7, 10, top=2)
            t.set(14, 4, 5, LIGHT)
        return t

    shelter('sites/shelter', False)
    shelter('sites/shelter_wing', True)

    everywhere = [P + b for b in ('crater_plains', 'giant_craters', 'spire_forest', 'steam_fields', 'yellow_plateaus')]
    for name, variants, biomes in (
            ('station', ('station', 'station_wrecked'), everywhere),
            ('hangar', ('hangar', 'hangar_collapsed'), [P + 'crater_plains', P + 'giant_craters', P + 'steam_fields']),
            ('post', ('post', 'post_fallen'), [P + 'crater_plains', P + 'spire_forest', P + 'yellow_plateaus']),
            ('shelter', ('shelter', 'shelter_wing'), everywhere)):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32)

    # The base: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from the base.
    pack.structure_set(P + 'landmark', [(P + 'lunar_base', 1)], spacing=36, separation=14, salt=746120931)
    pack.structure_set(P + 'sites', [(P + 'station', 3), (P + 'hangar', 2), (P + 'post', 3), (P + 'shelter', 3)],
                       spacing=11, separation=5, salt=1930461187, exclusion=(P + 'landmark', 8))
