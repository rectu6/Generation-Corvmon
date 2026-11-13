"""
Ultra Deep Sea (Nihilego's caverns): its structures, for tools/ultra_structures.py.

Underground: every structure here is started in a cavern by generations_core:ultra_cavern (a floor
with air over it, open round it, above the drowned depths), never in solid rock; the jigsaw's
beard fills under it and clears what rock its pieces cut.

The great one: the crystal laboratory, a jigsaw of halls joined by tubes. Its core is a domed hall
round a giant crystal held in a ring of glass over a basin; an airlock opens it on the cavern, where
a crystal harvest, a drilling rig or an observation deck stand. Tubes: windowed, all glass, an open
bridge, stairs, a flooded one. Halls: specimen tanks, an analysis lab, a server room, quarters, a
generator, a fungal greenhouse, a store, a hall flooded through a breach, a hall caved in,
junctions.
The small ones: a small lab, an analysis station, a drilling post, an observation platform, each in
two variants.

White and grey marble hulls, deepslate tiles, glass of the cave's colours (cyan, light blue,
purple, packed ice: the Abyss's own crystals, no crystal block nor amethyst), lightning lanterns and
crystal lights, sea pickles glowing in the tanks (no sea lantern nor froglight: the scenery is no
mine). Same connectors as the other worlds: a hall's door is named `door` and asks the tube pool
for a `tube_end`; a tube's end is named `tube_end` and asks the hall pool for a `door`. Doors 3
wide and 3 high, floors at y 0.
"""

WORLD = 'ultra_abyss'
P = WORLD + '/'

AIR = 'minecraft:air'
WHITE = 'g:white_marble'
GREY = 'g:light_gray_marble'
TILES = 'deepslate_tiles'
DARK = 'polished_deepslate'
FLOOR = 'g:subway_floor_plain'
PLATE = 'g:subway_floor_cross'
ROCK = 'g:cave_rock'
ROCK_FLOOR = 'g:gray_cave_rock_floor'
LIGHT = 'g:lightning_lantern'
GLOW = 'g:crystal_light'

GLASS = 'cyan_stained_glass'
BLUE = 'light_blue_stained_glass'
VIOLET = 'purple_stained_glass'
PANE = 'cyan_stained_glass_pane'
BARS = 'iron_bars'
CHAIN = 'chain[axis=y,waterlogged=false]'
ROD = 'end_rod[facing=up]'
BENCH = 'deepslate_tile_slab[type=top,waterlogged=false]'
SLAB = 'deepslate_tile_slab[type=bottom,waterlogged=false]'
STEP = 'deepslate_tile_stairs'
RAIL = 'deepslate_tile_wall'
WATER = 'water[level=0]'
PICKLE = 'sea_pickle[pickles=4,waterlogged=true]'
CRATE = 'g:crate'
BARREL = 'barrel[facing=up,open=false]'
LANTERN = 'lantern[hanging=true,waterlogged=false]'

TUBES = P + 'lab/tubes'
HALLS = P + 'lab/halls'
AIRLOCK = P + 'lab/airlock'
OUTSIDE = P + 'lab/outside'


def build(u, pack):
    hull = u.Mix((WHITE, 6), (GREY, 3), ('g:white_poke_brick', 1))
    worn = u.Mix((WHITE, 3), (GREY, 3), (ROCK, 2), ('cobbled_deepslate', 1))
    metal = u.Mix((FLOOR, 5), (PLATE, 1))
    crystal = u.Mix((GLASS, 3), (BLUE, 3), (VIOLET, 2), ('packed_ice', 2))
    glass_names = set(crystal.blocks)
    rubble = u.Mix((ROCK, 3), ('cobbled_deepslate', 2), ('tuff', 1), ('gravel', 1))
    fungus = u.Mix(('moss_block', 3), ('mycelium[snowy=false]', 2), ('podzol[snowy=false]', 1))

    # ------------------------------------------------------------------ helpers

    def spike(t, x, y, z, h, core=True, down=False):
        """A spike of the cave's crystals, widened at its base, a crystal light inside (down: hanging)."""
        d = -1 if down else 1
        for i in range(h):
            if t.inside(x, y + d * i, z):
                t.set(x, y + d * i, z, crystal)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for i in range(max(1, h // 3)):
                if t.inside(x + dx, y + d * i, z + dz) and t.get(x + dx, y + d * i, z + dz) in (None, AIR):
                    t.set(x + dx, y + d * i, z + dz, crystal)
        if core and t.inside(x, y + d * (h // 3), z):
            t.set(x, y + d * (h // 3), z, GLOW)

    def tank(t, x, z, h, front, side='x'):
        """A tank in a wall: water from y 1 to h over a glowing sea pickle, glass in front (at `front`), trims round it."""
        t.set(x, 1, z, PICKLE)
        t.fill(x, 2, z, x, h, z, WATER)
        if side == 'x':
            t.fill(front, 1, z, front, h, z, BLUE)
            for zz in (z - 1, z + 1):
                t.fill(x, 1, zz, front, h, zz, TILES)
        else:
            t.fill(x, 1, front, x, h, front, BLUE)
            for xx in (x - 1, x + 1):
                t.fill(xx, 1, z, xx, h, front, TILES)
        t.set(x, h + 1, z, TILES)

    def doors(t, sides, pool=TUBES):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=TILES)

    def hall(path, sx, sz, h, floor=None, wall=None):
        """A closed hall over the whole template: floor at y 0, room up to h, roof at h + 1; tiled corners and
        a tiled band at y 1, lightning lanterns in the roof."""
        t = pack.template(P + path, sx, h + 2, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall or hull, floor or metal, wall or hull)
        t.walls(0, 1, 0, sx - 1, 1, sz - 1, TILES)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, DARK)
        for x in range(3, sx - 2, 4):
            for z in range(3, sz - 2, 4):
                t.set(x, h + 1, z, LIGHT)
        return t

    # ------------------------------------------------------------------ the crystal hall (start)

    t = pack.template(P + 'lab/core', 25, 21, 25)
    c = 12
    t.room(0, 0, 24, 24, 10, hull, metal, hull)
    t.walls(0, 1, 0, 24, 1, 24, TILES)
    t.walls(0, 6, 0, 24, 6, 24, TILES)
    for a in (0, 6, 12, 18, 24):
        for x, z in ((a, 0), (a, 24), (0, a), (24, a)):
            t.fill(x, 0, z, x, 11, z, DARK)
    for side in ('north', 'south', 'east', 'west'):
        t.windows(side, 3, 4, 2, 22, 6, PANE, width=3)
    # The dome over the middle, ribs of tiles, a light at its top; lanterns in the flat roof round it.
    t.dome(c, 11, c, 9.5, u.Mix((WHITE, 3), (GLASS, 1)))
    t.disc(c, 11, c, 9.5, TILES, ring=True)
    t.disc(c, 11, c, 8.5, AIR)
    for x in range(25):
        for y in range(12, 21):
            for z in range(25):
                if (x == c or z == c) and t.get(x, y, z) not in (None, AIR):
                    t.set(x, y, z, TILES)
    t.set(c, 20, c, LIGHT)
    for x, z in ((3, 3), (21, 3), (3, 21), (21, 21)):
        t.set(x, 11, z, LIGHT)
    # The giant crystal in its ring of glass, over a basin; a crystal light glowing in it.
    t.disc(c, 0, c, 5.5, TILES)
    t.disc(c, 0, c, 4.5, WATER)
    t.disc(c, 0, c, 1.5, DARK)
    t.disc(c, 1, c, 4.5, BLUE, ring=True)
    t.disc(c, 2, c, 4.5, BLUE, ring=True)
    t.disc(c, 3, c, 4.5, TILES, ring=True)
    spike(t, c, 1, c, 13)
    spike(t, c + 2, 1, c - 1, 5, core=False)
    spike(t, c - 2, 1, c + 1, 4, core=False)
    t.set(c, 8, c, GLOW)
    # From the dome a crown of instruments hangs over it.
    t.fill(c, 15, c, c, 19, c, CHAIN)
    t.disc(c, 15, c, 2.5, BARS, ring=True)
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        t.set(c + dx, 14, c + dz, 'end_rod[facing=down]')
    # Consoles round the ring, facing it; four pillars with lights.
    for a0, a1 in ((8, 10), (14, 16)):
        for x0, z0, x1, z1 in ((a0, 4, a1, 4), (a0, 20, a1, 20), (4, a0, 4, a1), (20, a0, 20, a1)):
            t.fill(x0, 1, z0, x1, 1, z1, BENCH)
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if (x + z) % 2 == 0:
                        t.set(x, 2, z, 'black_stained_glass')
    for x, z in ((6, 6), (18, 6), (6, 18), (18, 18)):
        t.fill(x, 1, z, x, 10, z, TILES)
        t.set(x, 5, z, GLOW)
    t.fill(1, 1, 1, 3, 2, 2, CRATE)
    t.set(22, 1, 2, 'water_cauldron[level=3]')
    t.set(23, 1, 2, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    t.door('north', c, AIRLOCK, frame=TILES)
    for side in ('east', 'west', 'south'):
        t.door(side, c, TUBES, frame=TILES)

    # ------------------------------------------------------------------ the airlock, from the hall to the cavern

    t = pack.template(P + 'lab/airlock', 9, 7, 11)
    t.fill(0, 0, 0, 8, 0, 10, metal)
    t.fill(0, 1, 0, 8, 5, 10, hull)
    t.fill(1, 1, 1, 7, 4, 9, AIR)
    t.walls(0, 1, 0, 8, 1, 10, TILES)
    for z in (0, 4, 10):
        t.fill(3, 1, z, 5, 3, z, AIR)
    t.fill(1, 1, 4, 2, 4, 4, TILES)
    t.fill(6, 1, 4, 7, 4, 4, TILES)
    t.fill(3, 4, 4, 5, 4, 4, TILES)
    t.fill(3, 1, 5, 5, 3, 5, AIR)
    for x in (1, 7):
        t.set(x, 2, 7, 'lever[face=wall,facing=east,powered=false]' if x == 1 else 'lever[face=wall,facing=west,powered=false]')
    t.set(4, 5, 2, LIGHT)
    t.set(4, 5, 7, LIGHT)
    t.fill(1, 1, 8, 2, 2, 9, CRATE)
    t.windows('east', 2, 3, 6, 8, 2, PANE)
    t.windows('west', 2, 3, 6, 8, 2, PANE)
    t.jigsaw(4, 1, 0, 'north', 'tube_end', 'door', HALLS)
    t.jigsaw(4, 0, 10, 'south', 'exit', 'yard', OUTSIDE, final=FLOOR)

    # ------------------------------------------------------------------ out in the cavern

    # A crystal harvest: a platform among clusters of crystals, a crane, crates of samples.
    t = pack.template(P + 'lab/harvest', 15, 14, 15)
    t.fill(0, 0, 0, 14, 0, 14, ROCK_FLOOR)
    t.fill(0, 1, 0, 14, 13, 14, AIR)
    t.fill(5, 0, 0, 9, 0, 8, metal)
    t.walls(5, 1, 1, 9, 1, 8, RAIL)
    t.fill(6, 1, 1, 8, 1, 1, AIR)
    for x, z, h in ((2, 3, 9), (12, 4, 11), (3, 12, 7), (11, 11, 10), (7, 12, 5), (1, 8, 4), (13, 8, 5)):
        spike(t, x, 1, z, h)
    for x, z in ((6, 7), (8, 7)):
        t.fill(x, 1, z, x, 2, z, CRATE)
    t.fill(7, 1, 5, 7, 8, 5, DARK)
    t.fill(7, 8, 6, 7, 8, 10, 'polished_basalt[axis=z]')
    t.fill(7, 5, 10, 7, 7, 10, CHAIN)
    t.set(7, 9, 5, LIGHT)
    t.jigsaw(7, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)

    # A drilling rig: a tower of iron bars over a bore, the drill on its chain, pipes, a generator.
    t = pack.template(P + 'lab/drill_rig', 13, 18, 13)
    t.fill(0, 0, 0, 12, 0, 12, ROCK_FLOOR)
    t.fill(0, 1, 0, 12, 17, 12, AIR)
    t.fill(3, 0, 3, 9, 0, 9, metal)
    for y in range(2, 14, 3):
        t.walls(4, y, 4, 8, y, 8, BARS)
    for x, z in ((4, 4), (8, 4), (4, 8), (8, 8)):
        t.fill(x, 1, z, x, 14, z, DARK)
    t.fill(4, 15, 4, 8, 15, 8, TILES)
    t.fill(6, 1, 6, 6, 14, 6, AIR)
    t.fill(6, 8, 6, 6, 14, 6, CHAIN)
    t.fill(6, 3, 6, 6, 7, 6, 'polished_basalt[axis=y]')
    t.set(6, 2, 6, 'pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]')
    t.set(6, 0, 6, WATER)
    t.set(6, 1, 6, AIR)
    for x, z in ((5, 6), (7, 6), (6, 5), (6, 7)):
        t.set(x, 0, z, DARK)
    t.set(6, 16, 6, LIGHT)
    t.fill(1, 1, 9, 2, 2, 11, CRATE)
    t.fill(10, 1, 2, 11, 3, 3, TILES)
    t.set(10, 2, 2, GLOW)
    t.fill(9, 1, 6, 11, 1, 6, 'polished_basalt[axis=x]')
    for a in (5, 6, 7):
        for x, z in ((a, 8), (4, a), (8, a)):
            t.set(x, 1, z, RAIL)
    t.jigsaw(6, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)
    t.fill(5, 0, 0, 7, 0, 3, metal)

    # An observation deck: a platform raised on legs over the cavern, its rail, a searchlight.
    t = pack.template(P + 'lab/deck', 11, 11, 13)
    t.fill(0, 0, 0, 10, 0, 12, ROCK_FLOOR)
    t.fill(0, 1, 0, 10, 10, 12, AIR)
    t.fill(4, 0, 0, 6, 0, 2, metal)
    for i in range(4):
        for x in range(4, 7):
            t.stairs(x, 1 + i, 3 + i, STEP, 'south')
            if i:
                t.fill(x, 1, 3 + i, x, i, 3 + i, TILES)
    t.fill(1, 4, 7, 9, 4, 12, metal)
    for x, z in ((1, 7), (9, 7), (1, 12), (9, 12)):
        t.fill(x, 1, z, x, 3, z, DARK)
    t.walls(1, 5, 7, 9, 5, 12, BARS)
    t.fill(4, 5, 7, 6, 5, 7, AIR)
    t.set(5, 5, 11, DARK)
    t.set(5, 6, 11, LIGHT)
    t.set(5, 7, 11, 'daylight_detector[inverted=false,power=0]')
    t.set(2, 5, 10, 'lectern[facing=east,has_book=false,powered=false]')
    t.jigsaw(5, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)

    # ------------------------------------------------------------------ tubes (along z)

    def ends(t, x, y_out, length):
        t.jigsaw(x, 1, 0, 'north', 'tube_end', 'door', HALLS)
        t.jigsaw(x, y_out, length - 1, 'south', 'tube_end', 'door', HALLS)

    def tube(path, length, sides=PANE):
        """A tube 3 wide inside (x 2 to 4), 3 high, windows on both sides, lightning lanterns in the roof."""
        t = pack.template(P + path, 7, 6, length)
        t.fill(1, 0, 0, 5, 0, length - 1, metal)
        t.fill(1, 1, 0, 5, 4, length - 1, hull)
        t.fill(2, 1, 0, 4, 3, length - 1, AIR)
        t.fill(1, 1, 0, 1, 1, length - 1, TILES)
        t.fill(5, 1, 0, 5, 1, length - 1, TILES)
        for z in range(1, length - 1):
            if z % 3:
                t.set(1, 2, z, sides)
                t.set(5, 2, z, sides)
        for z in range(2, length - 1, 4):
            t.set(3, 4, z, LIGHT)
        for z in (0, length - 1):
            t.fill(1, 1, z, 1, 3, z, TILES)
            t.fill(5, 1, z, 5, 3, z, TILES)
            t.fill(1, 4, z, 5, 4, z, TILES)
        ends(t, 3, 1, length)
        return t

    tube('lab/tube', 9)

    # All glass: the cavern seen on both sides and through the roof.
    t = tube('lab/tube_glass', 11)
    for z in range(1, 10):
        for y in (2, 3):
            t.set(1, y, z, GLASS)
            t.set(5, y, z, GLASS)
        t.fill(2, 4, z, 4, 4, z, BLUE)
    for z in (3, 7):
        t.fill(1, 2, z, 1, 4, z, TILES)
        t.fill(5, 2, z, 5, 4, z, TILES)
        t.fill(2, 4, z, 4, 4, z, TILES)
        t.set(3, 4, z, LIGHT)

    # An open bridge: plates between rails, lights on posts.
    t = pack.template(P + 'lab/bridge', 7, 6, 13)
    t.fill(1, 0, 0, 5, 0, 12, PLATE)
    t.fill(1, 1, 0, 5, 5, 12, AIR)
    t.fill(1, 1, 1, 1, 1, 11, BARS)
    t.fill(5, 1, 1, 5, 1, 11, BARS)
    for z in (0, 12):
        t.fill(1, 1, z, 1, 3, z, DARK)
        t.fill(5, 1, z, 5, 3, z, DARK)
        t.fill(1, 4, z, 5, 4, z, TILES)
    for z in (4, 8):
        for x in (1, 5):
            t.set(x, 1, z, DARK)
            t.set(x, 2, z, RAIL)
            t.set(x, 3, z, LIGHT)
    ends(t, 3, 1, 13)

    # Stairs: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'lab/tube_stairs', 7, 10, 11)
    t.fill(1, 0, 0, 5, 0, 10, metal)
    t.fill(1, 1, 0, 5, 8, 10, hull)
    t.fill(2, 1, 0, 4, 7, 10, AIR)
    for i in range(4):
        z = 3 + i
        for x in range(2, 5):
            if i:
                t.fill(x, 1, z, x, i, z, hull)
            t.stairs(x, 1 + i, z, STEP, 'south')
    t.fill(2, 1, 7, 4, 3, 10, hull)
    t.fill(2, 4, 7, 4, 4, 10, metal)
    t.fill(2, 4, 0, 4, 7, 0, hull)
    t.fill(2, 1, 10, 4, 4, 10, hull)
    t.fill(2, 5, 10, 4, 7, 10, AIR)
    for z in (2, 5, 8):
        t.set(3, 8, z, LIGHT)
    ends(t, 3, 5, 11)

    # A flooded tube: water standing in the floor, a window cracked, crystals pushing in.
    t = tube('lab/tube_flooded', 11)
    for z in range(2, 9):
        t.set(3, 0, z, WATER)
        if z % 2:
            t.set(2, 0, z, WATER)
    t.set(5, 2, 5, AIR)
    t.set(5, 3, 5, crystal)
    t.set(4, 1, 5, crystal)
    t.set(4, 1, 6, VIOLET)
    t.scatter(2, 1, 1, 4, 1, 9, rubble, 2)

    for name, connector, target, block in (('lab/cap_door', 'tube_end', 'door', ROCK), ('lab/cap_tube', 'door', 'tube_end', TILES)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, block)
        t.set(2, 2, 0, LIGHT if block == TILES else GLOW)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=block)

    # ------------------------------------------------------------------ halls

    # Specimen tanks along two walls, glowing sea pickles in them; a table of samples between.
    t = hall('lab/specimens', 15, 15, 6)
    for z in range(2, 13, 3):
        tank(t, 1, z, 4, 2)
        tank(t, 13, z, 4, 12)
    t.fill(6, 1, 5, 8, 1, 9, BENCH)
    for z in (5, 7, 9):
        t.set(7, 2, z, crystal)
    t.set(6, 2, 6, 'flower_pot')
    t.set(8, 2, 8, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    doors(t, ('north', 'south'))

    # The analysis lab: benches round the walls (brewing stands, cauldrons), a sample on a pedestal under a lens.
    t = hall('lab/analysis', 13, 13, 5)
    t.fill(1, 1, 1, 11, 1, 1, BENCH)
    t.fill(1, 1, 11, 4, 1, 11, BENCH)
    t.fill(8, 1, 11, 11, 1, 11, BENCH)
    for x in (2, 6, 10):
        t.set(x, 2, 1, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    for x in (4, 8):
        t.set(x, 2, 1, 'flower_pot')
    t.set(2, 1, 10, 'water_cauldron[level=3]')
    t.set(10, 1, 10, 'cauldron')
    t.set(6, 1, 6, DARK)
    t.set(6, 2, 6, VIOLET)
    t.set(6, 5, 6, 'end_rod[facing=down]')
    t.set(6, 4, 6, BARS)
    doors(t, ('east', 'west', 'south'))

    # The server room: racks of observers and dark glass in rows, cold light.
    t = hall('lab/servers', 13, 11, 5, floor=PLATE)
    for x in (2, 4, 8, 10):
        for z in range(2, 9):
            if z == 5:
                continue
            t.set(x, 1, z, DARK)
            t.set(x, 2, z, 'observer[facing=east,powered=false]' if x < 6 else 'observer[facing=west,powered=false]')
            t.set(x, 3, z, 'black_stained_glass')
            t.set(x, 4, z, TILES)
    t.fill(6, 0, 1, 6, 0, 9, GLASS)
    doors(t, ('north', 'south'))

    # Quarters: bunks, lockers, a table.
    t = hall('lab/quarters', 13, 11, 4)
    for x in (1, 4, 8, 11):
        t.set(x, 1, 1, 'light_blue_bed[facing=north,occupied=false,part=head]')
        t.set(x, 1, 2, 'light_blue_bed[facing=north,occupied=false,part=foot]')
        t.set(x, 1, 9, 'white_bed[facing=south,occupied=false,part=head]')
        t.set(x, 1, 8, 'white_bed[facing=south,occupied=false,part=foot]')
    for x in (2, 3, 9, 10):
        t.set(x, 1, 1, BARREL)
    t.fill(5, 1, 4, 7, 1, 6, BENCH)
    t.set(6, 4, 5, LANTERN)
    doors(t, ('east', 'west'))

    # The generator: a core of crystal lights in a cage, magma under grates, pipes to the walls.
    t = hall('lab/generator', 13, 13, 6, floor=PLATE)
    t.fill(4, 1, 4, 8, 1, 8, TILES)
    t.fill(5, 2, 5, 7, 5, 7, BARS)
    t.fill(6, 2, 6, 6, 5, 6, GLOW)
    t.fill(4, 6, 4, 8, 6, 8, TILES)
    for x, z in ((2, 2), (10, 2), (2, 10), (10, 10)):
        t.set(x, 0, z, 'magma_block')
    for x0, z0, x1, z1, axis in ((1, 6, 4, 6, 'x'), (8, 6, 11, 6, 'x'), (6, 1, 6, 4, 'z'), (6, 8, 6, 11, 'z')):
        t.fill(x0, 4, z0, x1, 4, z1, f'polished_basalt[axis={axis}]')
    doors(t, ('north', 'south', 'east'))

    # A fungal greenhouse: beds of moss and mycelium, mushrooms, shroomlights, glowing vines from the roof.
    t = hall('lab/greenhouse', 15, 13, 6)
    for x0 in (2, 9):
        t.fill(x0, 0, 2, x0 + 3, 0, 10, fungus)
        t.walls(x0 - 1, 1, 1, x0 + 4, 1, 11, SLAB)
        for _ in range(8):
            x, z = t.rng.randint(x0, x0 + 3), t.rng.randint(2, 10)
            t.set(x, 1, z, t.rng.choice(('red_mushroom', 'brown_mushroom', 'moss_carpet', 'azalea')))
        t.set(x0 + 1, 1, 6, 'shroomlight')
        t.set(x0 + 2, 2, 4, 'shroomlight')
    for x in range(1, 14, 3):
        for z in range(2, 11, 4):
            t.set(x, 6, z, 'cave_vines_plant[berries=true]')
            t.set(x, 5, z, 'cave_vines[age=25,berries=true]')
    t.fill(1, 7, 4, 13, 7, 8, BLUE)
    for x in range(3, 13, 4):
        t.set(x, 7, 6, LIGHT)
    doors(t, ('north', 'south'))

    # A store: crates and barrels in rows.
    t = hall('lab/store', 13, 11, 5)
    for x in (2, 5, 8, 11):
        for z in range(2, 9):
            if z == 5:
                continue
            for y in range(1, 1 + t.rng.randint(1, 3)):
                t.set(x, y, z, CRATE if t.rng.random() < 0.7 else BARREL)
    doors(t, ('north',))

    # A hall flooded through a breach: rock and crystals coming through a wall, water over the floor.
    t = hall('lab/flooded', 13, 13, 6, wall=worn)
    for x in range(1, 12):
        for z in range(1, 12):
            t.set(x, 0, z, WATER if t.rng.random() < 0.7 else DARK)
    for x in range(13):
        for y in range(0, 8):
            for z in range(8, 13):
                if ((x - 9) ** 2 + (y - 3) ** 2 * 1.3 + (z - 12) ** 2) ** 0.5 < 3.6 + t.rng.random():
                    t.set(x, y, z, rubble if y < 3 else ROCK)
    spike(t, 8, 1, 9, 5)
    spike(t, 10, 6, 9, 3, down=True)
    t.fill(1, 1, 1, 3, 1, 2, CRATE)
    t.set(2, 2, 1, crystal)
    doors(t, ('north', 'east'))

    # A hall caved in: a heap of rock from the roof, crystals grown on it, a bench overturned.
    t = hall('lab/caved_in', 13, 13, 6)
    for x in range(1, 12):
        for z in range(1, 12):
            d = ((x - 4) ** 2 + (z - 8) ** 2) ** 0.5
            h = min(7, round(6.5 - 1.5 * d + t.rng.random() * 0.8))
            for y in range(1, h + 1):
                t.set(x, y, z, rubble)
    t.fill(2, 7, 6, 6, 7, 10, AIR, chance=0.7)
    spike(t, 4, 7, 8, 3, core=False)
    spike(t, 9, 1, 3, 3)
    t.fill(8, 1, 9, 10, 1, 9, SLAB)
    doors(t, ('north', 'south'))

    # A junction: four doors round a pillar of crystal lights.
    t = hall('lab/junction', 11, 11, 6)
    t.fill(4, 1, 4, 6, 1, 6, TILES)
    t.fill(5, 2, 5, 5, 5, 5, GLOW)
    t.fill(4, 2, 4, 6, 5, 6, BLUE, only=(AIR,))
    t.disc(5, 0, 5, 4.5, PLATE, ring=True)
    doors(t, ('north', 'south', 'east', 'west'))

    pack.pool(TUBES, [(P + 'lab/tube', 3), (P + 'lab/tube_glass', 3), (P + 'lab/bridge', 2), (P + 'lab/tube_stairs', 2), (P + 'lab/tube_flooded', 1)],
              fallback=P + 'lab/door_caps')
    pack.pool(P + 'lab/door_caps', [(P + 'lab/cap_door', 1)])
    pack.pool(AIRLOCK, [(P + 'lab/airlock', 1)], fallback=P + 'lab/door_caps')
    pack.pool(HALLS, [(P + 'lab/specimens', 2), (P + 'lab/analysis', 2), (P + 'lab/servers', 2), (P + 'lab/quarters', 2), (P + 'lab/generator', 2),
                      (P + 'lab/greenhouse', 2), (P + 'lab/store', 1), (P + 'lab/flooded', 1), (P + 'lab/caved_in', 1), (P + 'lab/junction', 3)],
              fallback=P + 'lab/tube_caps')
    pack.pool(P + 'lab/tube_caps', [(P + 'lab/cap_tube', 1)])
    pack.pool(OUTSIDE, [(P + 'lab/harvest', 3), (P + 'lab/drill_rig', 2), (P + 'lab/deck', 2)])
    pack.pool(P + 'lab/start', [(P + 'lab/core', 1)])
    caverns = [P + b for b in ('crystal_caverns', 'luminous_caves', 'rocky_abyss', 'fungal_forest')]
    # Started on a cavern's floor between y 38 and 110 (above the drowned depths), 10 blocks of air over it.
    pack.cavern(P + 'lab', P + 'lab/start', caverns, size=6, max_distance=90, min_y=38, max_y=110, clearance=10, reach=12, open_sides=5)

    # ------------------------------------------------------------------ the small ones

    # A small lab: a pod with windows, a bench, a tank, a light; or left to the cave (cobwebs, crystals, broken glass).
    def small_lab(path, abandoned):
        t = pack.template(P + path, 11, 8, 11)
        t.fill(0, 0, 0, 10, 0, 10, ROCK_FLOOR)
        t.fill(0, 1, 0, 10, 7, 10, AIR)
        t.fill(1, 0, 1, 9, 0, 9, metal)
        t.fill(1, 1, 1, 9, 5, 9, hull)
        t.fill(2, 1, 2, 8, 4, 8, AIR)
        t.walls(1, 1, 1, 9, 1, 9, TILES)
        t.fill(4, 1, 1, 6, 3, 1, AIR)
        for side in ('east', 'west', 'south'):
            for a in (3, 7):
                x, z = {'east': (9, a), 'west': (1, a), 'south': (a, 9)}[side]
                t.fill(x, 2, z, x, 3, z, PANE)
        t.fill(2, 1, 8, 5, 1, 8, BENCH)
        t.set(3, 2, 8, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
        t.set(8, 1, 8, PICKLE)
        t.fill(8, 2, 8, 8, 3, 8, WATER)
        t.fill(7, 1, 8, 7, 3, 8, BLUE)
        t.fill(8, 1, 7, 8, 3, 7, BLUE)
        t.fill(7, 1, 7, 7, 3, 7, TILES)
        t.set(8, 4, 8, TILES)
        t.set(5, 5, 5, LIGHT)
        t.fill(2, 1, 2, 2, 2, 3, CRATE)
        t.set(5, 6, 5, ROD)
        if abandoned:
            for x in range(1, 10):
                for y in range(2, 4):
                    for z in range(1, 10):
                        if t.get(x, y, z) == u.norm(PANE) and t.rng.random() < 0.6:
                            t.set(x, y, z, AIR)
            t.set(5, 5, 5, hull)
            t.scatter(2, 1, 2, 8, 4, 8, 'cobweb', 5, on_floor=False)
            spike(t, 7, 1, 3, 3)
            spike(t, 3, 4, 5, 2, core=False, down=True)
            t.scatter(0, 1, 0, 10, 1, 10, rubble, 4)
            t.set(2, 4, 8, GLOW)
        return t

    small_lab('sites/small_lab', False)
    small_lab('sites/small_lab_abandoned', True)

    # An analysis station: a glass dome over an instrument, racks of samples round it.
    def station(path, overgrown):
        t = pack.template(P + path, 13, 9, 13)
        t.fill(0, 0, 0, 12, 0, 12, ROCK_FLOOR)
        t.fill(0, 1, 0, 12, 8, 12, AIR)
        t.disc(6, 0, 6, 5.5, metal)
        t.disc(6, 0, 6, 5.5, TILES, ring=True)
        t.dome(6, 1, 6, 4.5, u.Mix((GLASS, 3), (BLUE, 2)))
        t.disc(6, 1, 6, 4.5, TILES, ring=True)
        t.fill(5, 1, 2, 7, 3, 2, AIR)
        t.fill(5, 1, 1, 7, 2, 1, AIR)
        t.set(6, 1, 6, DARK)
        t.set(6, 2, 6, 'observer[facing=up,powered=false]')
        t.set(6, 3, 6, BARS)
        t.set(6, 4, 6, 'end_rod[facing=up]')
        t.set(6, 5, 6, GLOW)
        for x, z in ((4, 7), (8, 7), (6, 9)):
            t.set(x, 1, z, BENCH)
            t.set(x, 2, z, crystal)
        t.set(4, 1, 4, 'lectern[facing=north,has_book=false,powered=false]')
        for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
            t.fill(x, 1, z, x, 2, z, DARK)
            t.set(x, 3, z, LIGHT)
        if overgrown:
            for x in range(13):
                for y in range(1, 7):
                    for z in range(13):
                        if t.get(x, y, z) in (u.norm(GLASS), u.norm(BLUE)) and t.rng.random() < 0.3:
                            t.set(x, y, z, AIR)
            for _ in range(7):
                x, z = t.rng.randint(1, 11), t.rng.randint(1, 11)
                if t.get(x, 1, z) in (None, AIR):
                    t.set(x, 1, z, t.rng.choice(('red_mushroom', 'brown_mushroom')))
            t.fill(1, 0, 8, 4, 0, 11, 'mycelium[snowy=false]')
            t.set(2, 1, 9, 'shroomlight')
            spike(t, 10, 1, 9, 4)
            t.set(11, 3, 11, AIR)
        return t

    station('sites/station', False)
    station('sites/station_overgrown', True)

    # A drilling post: a derrick over a bore, the drill on its chain, crates and a generator.
    def drill(path, collapsed):
        t = pack.template(P + path, 11, 16, 11)
        t.fill(0, 0, 0, 10, 0, 10, ROCK_FLOOR)
        t.fill(0, 1, 0, 10, 15, 10, AIR)
        t.fill(2, 0, 2, 8, 0, 8, metal)
        for y in range(3, 12, 3):
            t.walls(3, y, 3, 7, y, 7, BARS)
        for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
            t.fill(x, 1, z, x, 12, z, DARK)
        t.fill(3, 13, 3, 7, 13, 7, TILES)
        t.fill(5, 8, 5, 5, 12, 5, CHAIN)
        t.fill(5, 2, 5, 5, 7, 5, 'polished_basalt[axis=y]')
        t.set(5, 1, 5, 'pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]')
        t.set(5, 0, 5, WATER)
        t.set(5, 14, 5, LIGHT)
        t.fill(0, 1, 0, 1, 2, 1, CRATE)
        t.fill(8, 1, 8, 9, 2, 9, TILES)
        t.set(9, 3, 9, GLOW)
        t.fill(6, 1, 9, 7, 1, 9, 'polished_basalt[axis=x]')
        if collapsed:
            t.fill(0, 7, 0, 10, 15, 10, AIR)
            t.fill(7, 3, 3, 7, 6, 7, AIR)
            for x in range(8, 11):
                t.set(x, 1, 4, 'polished_basalt[axis=x]' if x < 10 else DARK)
            t.set(9, 1, 5, TILES)
            t.set(10, 1, 6, TILES)
            t.fill(5, 1, 5, 5, 6, 5, AIR)
            t.set(4, 1, 5, 'polished_basalt[axis=x]')
            t.scatter(0, 1, 0, 10, 1, 10, rubble, 6)
            t.scatter(0, 1, 0, 10, 1, 10, BARS, 3)
        return t

    drill('sites/drill', False)
    drill('sites/drill_collapsed', True)

    # An observation platform on four legs: a ladder, rails, a searchlight and a lectern for the notes.
    def platform(path, broken):
        t = pack.template(P + path, 11, 11, 11)
        t.fill(0, 0, 0, 10, 0, 10, ROCK_FLOOR)
        t.fill(0, 1, 0, 10, 10, 10, AIR)
        for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
            t.fill(x, 1, z, x, 5, z, DARK)
        t.fill(1, 6, 1, 9, 6, 9, metal)
        t.walls(1, 7, 1, 9, 7, 9, BARS)
        t.fill(5, 7, 1, 5, 7, 1, AIR)
        t.set(5, 6, 1, AIR)
        t.fill(5, 1, 0, 5, 7, 0, TILES)
        t.ladder(5, 1, 7, 1, 'south')
        t.set(5, 7, 8, DARK)
        t.set(5, 8, 8, LIGHT)
        t.set(5, 9, 8, 'daylight_detector[inverted=false,power=0]')
        t.set(3, 7, 6, 'lectern[facing=east,has_book=false,powered=false]')
        t.set(7, 7, 3, CRATE)
        t.set(2, 8, 2, LIGHT)
        t.set(8, 8, 8, LIGHT)
        if broken:
            t.fill(6, 6, 5, 9, 7, 9, AIR)
            t.fill(8, 3, 8, 8, 5, 8, AIR)
            t.set(8, 8, 8, AIR)
            for x, z in ((7, 7), (8, 6), (9, 8), (6, 9)):
                t.set(x, 1, z, t.rng.choice((FLOOR, PLATE, BARS)))
            t.set(5, 8, 8, AIR)
            t.set(5, 9, 8, AIR)
            spike(t, 3, 1, 8, 4)
        return t

    platform('sites/platform', False)
    platform('sites/platform_broken', True)

    everywhere = caverns + [P + 'underground_lakes']
    for name, variants in (('small_lab', ('small_lab', 'small_lab_abandoned')), ('station', ('station', 'station_overgrown')),
                           ('drill', ('drill', 'drill_collapsed')), ('platform', ('platform', 'platform_broken'))):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.cavern(P + name, P + 'sites/' + name, everywhere, size=1, max_distance=32, min_y=36, max_y=114, clearance=7, reach=6, open_sides=5)

    # The laboratory: one try per 26 chunks (416 blocks), the small sites: one per 9 chunks (144 blocks), away from it; tighter than
    # on the surface, a try failing about once in two (no cavern open enough in the chunk).
    pack.structure_set(P + 'landmark', [(P + 'lab', 1)], spacing=26, separation=10, salt=1846210599)
    pack.structure_set(P + 'sites', [(P + 'small_lab', 3), (P + 'station', 3), (P + 'drill', 3), (P + 'platform', 2)],
                       spacing=9, separation=4, salt=327045863, exclusion=(P + 'landmark', 6))
