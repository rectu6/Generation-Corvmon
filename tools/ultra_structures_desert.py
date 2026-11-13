"""
Ultra Desert (Kartana, Stakataka's sands): its structures, for tools/ultra_structures.py.

The great one: the buried crystal palace, a jigsaw of halls joined by galleries, set ten blocks under
the sand (no terrain adaptation: the sand stays round it; only its glass dome and four spires come
out of the dunes). A throne hall under the dome (a mirrored cross, reflecting pools, a ring of
prismarine columns, a crystal throne, a chandelier of glass); a covered stair climbs from it to the
surface, between two obelisks. Galleries: colonnades, a gallery of mirrors, stairs, a gallery half
full of sand. Halls: a hall of mirrors, a crystal garden, a basin, archives, an empty treasury,
quarters, a hall fallen in, crossroads.
The small ones: an outpost, a buried shrine (its stair goes down from the sand), an observation
station, crystal ruins, each in two variants.

The Golden Temple blocks (Generations: sandstone, cut, chiseled, smooth, prismarine), mirrored floors
and mirror glass, white / light blue / magenta glass, crystal lights and lanterns (the shaders ignore
the ambient light). No crystal block, Ultra crystal or amethyst (the rule of the scenery). Sand is
only laid on something solid. Same connectors as the Ultra Crater: a room's door is named `door` and
asks the gallery pool for a `tube_end`; a gallery's end is named `tube_end` and asks the room pool for
a `door`. Doors 3 wide and 3 high, their connector at the bottom middle, floors at y 0.
"""

WORLD = 'ultra_desert'
P = WORLD + '/'

AIR = 'minecraft:air'
SAND = 'g:golden_temple_sandstone'
CUT = 'g:golden_temple_cut_sandstone'
CHISELED = 'g:golden_temple_chiseled_sandstone'
SMOOTH = 'g:golden_temple_smooth_sandstone'
PRISM = 'g:golden_temple_prismarine'
PRISM_BRICKS = 'g:golden_temple_prismarine_bricks'
DARK = 'g:golden_temple_dark_prismarine'
MIRROR = 'g:mirrored_floor_1'
MIRROR_GLASS = 'g:mirror_glass'
GLOW = 'g:crystal_light'
STEP = 'g:golden_temple_sandstone_stairs'
PRISM_STEP = 'g:golden_temple_prismarine_brick_stairs'
SLAB = 'g:golden_temple_smooth_sandstone_slab[type=bottom,waterlogged=false]'
RAIL = 'g:golden_temple_sandstone_wall'
PRISM_RAIL = 'g:golden_temple_prismarine_brick_wall'
DUNE = 'g:ultra_sand'
ROCK = 'g:ultra_sandstone'
LANTERN = 'lantern[hanging=true,waterlogged=false]'
POST_LANTERN = 'lantern[hanging=false,waterlogged=false]'
WATER = 'water[level=0]'
CRATE = 'g:crate'

GALLERIES = P + 'palace/galleries'
ROOMS = P + 'palace/rooms'
ENTRANCE = P + 'palace/entrance'
EXTERIOR = P + 'palace/exterior'


def build(u, pack):
    gold = u.Mix((SAND, 6), (CUT, 3), (SMOOTH, 1))
    worn = u.Mix((SAND, 5), (CUT, 2), (ROCK, 2), ('g:cobble_ruins_1', 1), ('g:cobble_ruins_2', 1))
    paving = u.Mix((SMOOTH, 5), (CUT, 3), (SAND, 2))
    rubble = u.Mix(('g:cobble_ruins_1', 2), ('g:cobble_ruins_3', 2), (ROCK, 2), (CUT, 1))
    crystal = u.Mix(('light_blue_stained_glass', 3), ('white_stained_glass', 3), ('glass', 2), ('magenta_stained_glass', 1))
    glass_names = set(crystal.blocks)
    mirrors = ('g:mirrored_floor_1', 'g:mirrored_floor_2', 'g:mirrored_floor_3')

    # ------------------------------------------------------------------ helpers

    def full(t, x, y, z):
        b = t.get(x, y, z) if t.inside(x, y, z) else None
        return b is not None and b != AIR and u.is_full(b)

    def drift(t, x0, z0, x1, z1, y0, hmax):
        """Sand blown in: heaps up to hmax high, each column laid on something solid."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not full(t, x, y0 - 1, z):
                    continue
                h = round(hmax * t.rng.random() ** 1.5)
                for y in range(y0, y0 + h):
                    if t.get(x, y, z) not in (None, AIR):
                        break
                    t.set(x, y, z, DUNE)

    def spike(t, x, y, z, h, core=True):
        """A spike of glass, its base widened, a crystal light inside."""
        for i in range(h):
            if t.inside(x, y + i, z):
                t.set(x, y + i, z, crystal)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for i in range(max(1, h // 3)):
                if t.inside(x + dx, y + i, z + dz) and t.get(x + dx, y + i, z + dz) in (None, AIR):
                    t.set(x + dx, y + i, z + dz, crystal)
        if core:
            t.set(x, y + h // 3, z, GLOW)

    def column(t, x, z, top, glow_at=None):
        """A prismarine column on a chiseled base under a chiseled capital (y 1 to top)."""
        t.set(x, 1, z, CHISELED)
        t.fill(x, 2, z, x, top - 1, z, PRISM_BRICKS)
        t.set(x, top, z, CHISELED)
        if glow_at:
            t.set(x, glow_at, z, GLOW)

    def doors(t, sides, pool=GALLERIES):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=CHISELED)

    def chamber(path, sx, sz, h, floor=None, wall=None):
        """A closed hall over the whole template: floor at y 0, room up to h, roof at h + 1; chiseled corners, a
        band of prismarine round the walls, crystal lights in the roof."""
        t = pack.template(P + path, sx, h + 2, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall or gold, floor or paving, gold)
        t.walls(0, h, 0, sx - 1, h, sz - 1, PRISM)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, CHISELED)
        for x in range(3, sx - 2, 4):
            for z in range(3, sz - 2, 4):
                t.set(x, h + 1, z, GLOW)
        return t

    # ------------------------------------------------------------------ the throne hall (start)

    t = pack.template(P + 'palace/core', 25, 23, 25)
    c = 12
    t.fill(0, 0, 0, 24, 0, 24, SMOOTH)
    t.fill(0, 1, 0, 24, 10, 24, gold)
    t.fill(1, 1, 1, 23, 9, 23, AIR)
    t.walls(0, 5, 0, 24, 5, 24, CHISELED)
    t.walls(0, 9, 0, 24, 9, 24, PRISM)
    for a in (6, 18):
        for x, z in ((a, 0), (a, 24), (0, a), (24, a)):
            t.set(x, 5, z, GLOW)
    t.walls(0, 11, 0, 24, 11, 24, RAIL)
    # The floor: a mirrored cross, a dark ring under the dome, four reflecting pools.
    t.fill(11, 0, 1, 13, 0, 23, MIRROR)
    t.fill(1, 0, 11, 23, 0, 13, MIRROR)
    t.disc(c, 0, c, 6.5, 'g:mirrored_floor_2')
    t.disc(c, 0, c, 6.5, DARK, ring=True)
    for px, pz in ((4, 4), (17, 4), (4, 17), (17, 17)):
        t.walls(px - 1, 0, pz - 1, px + 4, 0, pz + 4, PRISM_BRICKS)
        t.fill(px, 0, pz, px + 3, 0, pz + 3, WATER)
        for x, z in ((px - 1, pz - 1), (px + 4, pz - 1), (px - 1, pz + 4), (px + 4, pz + 4)):
            t.set(x, 0, z, GLOW)
    # The columns, the lights in the ceiling.
    for x, z in ((8, 3), (16, 3), (8, 21), (16, 21), (3, 8), (3, 16), (21, 8), (21, 16)):
        column(t, x, z, 9, glow_at=5)
    for x in range(3, 22, 4):
        for z in range(3, 22, 4):
            if (x - c) ** 2 + (z - c) ** 2 > 8 ** 2:
                t.set(x, 10, z, GLOW)
    # The dome: glass on a chiseled ring, four ribs, a light at its top; a chandelier of glass hangs from it.
    t.dome(c, 10, c, 7.5, crystal)
    t.disc(c, 10, c, 7.5, CHISELED, ring=True)
    for x in range(25):
        for y in range(11, 18):
            for z in range(25):
                if (x == c or z == c) and t.get(x, y, z) in glass_names:
                    t.set(x, y, z, CUT)
    t.set(c, 17, c, GLOW)
    t.set(c, 18, c, 'end_rod[facing=up]')
    t.fill(c, 13, c, c, 16, c, crystal)
    t.set(c, 13, c, GLOW)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(c + dx, 16, c + dz, crystal)
        t.set(c + dx, 15, c + dz, crystal)
    # The four spires at the corners, crystals at their tops.
    for x0, z0 in ((0, 0), (22, 0), (0, 22), (22, 22)):
        t.fill(x0, 1, z0, x0 + 2, 14, z0 + 2, CUT)
        t.fill(x0, 15, z0, x0 + 2, 15, z0 + 2, CHISELED)
        spike(t, x0 + 1, 16, z0 + 1, 5, core=False)
        t.set(x0 + 1, 16, z0 + 1, GLOW)
        t.set(x0 + 1, 21, z0 + 1, 'end_rod[facing=up]')
    # The throne: a round dais, a seat of prismarine looking north, a crystal behind it.
    t.disc(c, 1, c, 3.5, SLAB)
    t.disc(c, 1, c, 2.5, SMOOTH)
    t.disc(c, 2, c, 1.5, SLAB)
    t.stairs(c, 2, c, PRISM_STEP, 'south')
    t.set(c - 1, 2, c, PRISM_RAIL)
    t.set(c + 1, 2, c, PRISM_RAIL)
    t.fill(c, 2, c + 1, c, 4, c + 1, 'light_blue_stained_glass')
    t.set(c, 5, c + 1, GLOW)
    # Sand blown in along the walls.
    drift(t, 1, 1, 6, 2, 1, 2)
    drift(t, 18, 21, 23, 23, 1, 3)
    drift(t, 21, 1, 23, 5, 1, 2)
    t.door('north', c, ENTRANCE, frame=CHISELED)
    for side in ('east', 'west', 'south'):
        t.door(side, c, GALLERIES, frame=CHISELED)

    # ------------------------------------------------------------------ the way up: a covered stair, 10 high, to the surface

    t = pack.template(P + 'palace/entrance', 7, 16, 15)

    def level(z):
        return 0 if z < 2 else min(z - 1, 10)

    for z in range(15):
        f = level(z)
        roof = max(5, f + 5)
        t.fill(1, 0, z, 5, roof, z, gold)
        t.fill(2, f + 1, z, 4, roof - 1, z, AIR)
        if 2 <= z <= 11:
            for x in range(2, 5):
                t.stairs(x, f, z, STEP, 'south')
        if z % 4 == 1:
            t.set(3, roof - 1, z, LANTERN)
    # The pavilion where it comes out: chiseled posts and roof, a crystal light.
    for x in (1, 5):
        t.fill(x, 11, 14, x, 14, 14, CHISELED)
    t.fill(1, 15, 12, 5, 15, 14, CHISELED)
    t.set(3, 15, 13, GLOW)
    t.jigsaw(3, 1, 0, 'north', 'tube_end', 'door', ROOMS)
    t.jigsaw(3, 10, 14, 'south', 'exit', 'pad', EXTERIOR, final=SMOOTH)

    # ------------------------------------------------------------------ galleries (along z)

    def ends(t, x, y_out, length):
        t.jigsaw(x, 1, 0, 'north', 'tube_end', 'door', ROOMS)
        t.jigsaw(x, y_out, length - 1, 'south', 'tube_end', 'door', ROOMS)

    def plain(path, length):
        """A gallery 3 wide inside (x 2 to 4), 3 high, walls and roof of sandstone, a crystal light in the roof every 4."""
        t = pack.template(P + path, 7, 6, length)
        t.fill(1, 0, 0, 5, 0, length - 1, paving)
        t.fill(1, 1, 0, 5, 4, length - 1, gold)
        t.fill(2, 1, 0, 4, 3, length - 1, AIR)
        for z in range(1, length - 1, 4):
            t.set(3, 4, z, GLOW)
        for z in (0, length - 1):
            t.fill(1, 1, z, 1, 3, z, CHISELED)
            t.fill(5, 1, z, 5, 3, z, CHISELED)
            t.fill(1, 4, z, 5, 4, z, CHISELED)
        ends(t, 3, 1, length)
        return t

    # Arches: a gallery with chiseled ribs and prismarine pilasters.
    t = plain('palace/gallery', 9)
    for z in (2, 6):
        t.fill(1, 1, z, 1, 3, z, PRISM_BRICKS)
        t.fill(5, 1, z, 5, 3, z, PRISM_BRICKS)
        t.fill(1, 4, z, 5, 4, z, CHISELED)
        t.set(3, 3, z, AIR)

    # The gallery of mirrors: mirrored floor, mirror glass on both walls, a line of lights.
    t = plain('palace/mirror_gallery', 11)
    for z in range(1, 10):
        t.set(3, 0, z, mirrors[z % 3])
        for x in (2, 4):
            t.set(x, 0, z, MIRROR)
        for y in (1, 2, 3):
            t.set(1, y, z, MIRROR_GLASS if y > 1 else PRISM)
            t.set(5, y, z, MIRROR_GLASS if y > 1 else PRISM)
    for z in range(2, 10, 2):
        t.set(3, 4, z, GLOW)

    # A gallery half full of sand: heaps along the walls, rubble, a roof cracked.
    t = plain('palace/gallery_sanded', 11)
    drift(t, 2, 1, 2, 9, 1, 2)
    drift(t, 4, 2, 4, 9, 1, 3)
    t.scatter(2, 1, 1, 4, 1, 9, rubble, 3)
    t.fill(1, 4, 3, 5, 4, 7, rubble, chance=0.5)

    # A colonnade: a wide gallery (x 1 to 7), two rows of columns, a mirrored strip, glowing niches.
    t = pack.template(P + 'palace/colonnade', 9, 7, 13)
    t.fill(0, 0, 0, 8, 0, 12, paving)
    t.fill(3, 0, 0, 5, 0, 12, MIRROR)
    t.fill(0, 1, 0, 8, 6, 12, gold)
    t.fill(1, 1, 0, 7, 5, 12, AIR)
    for z in (2, 6, 10):
        for x in (2, 6):
            column(t, x, z, 5)
    for z in (4, 8):
        t.set(4, 6, z, GLOW)
        t.set(0, 3, z, GLOW)
        t.set(8, 3, z, GLOW)
    for z in (0, 12):
        t.fill(0, 1, z, 8, 5, z, gold)
        t.fill(3, 1, z, 5, 3, z, AIR)
        t.fill(2, 4, z, 6, 4, z, CHISELED)
    drift(t, 1, 1, 1, 11, 1, 2)
    ends(t, 4, 1, 13)

    # A stair gallery: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'palace/gallery_stairs', 7, 10, 11)
    t.fill(1, 0, 0, 5, 0, 10, paving)
    t.fill(1, 1, 0, 5, 8, 10, gold)
    t.fill(2, 1, 0, 4, 7, 10, AIR)
    for i in range(4):
        z = 3 + i
        for x in range(2, 5):
            if i:
                t.fill(x, 1, z, x, i, z, gold)
            t.stairs(x, 1 + i, z, STEP, 'south')
    t.fill(2, 1, 7, 4, 3, 10, gold)
    t.fill(2, 4, 7, 4, 4, 10, paving)
    t.fill(2, 4, 0, 4, 7, 0, gold)          # above the low door
    t.fill(2, 1, 10, 4, 4, 10, gold)        # under the high door
    t.fill(2, 5, 10, 4, 7, 10, AIR)
    for z in (2, 5, 8):
        t.set(3, 8, z, GLOW)
    ends(t, 3, 5, 11)

    # The way out of a room toward the sand (into `entrance` and the caps): the covered stair is reused.
    for name, connector, target, block in (('palace/cap_door', 'tube_end', 'door', ROCK), ('palace/cap_gallery', 'door', 'tube_end', CUT)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, block)
        t.set(2, 2, 0, CHISELED)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=block)

    # ------------------------------------------------------------------ rooms

    # The hall of mirrors: floors of three mirrors, mirror glass on the walls, pillars of glass round a light.
    t = chamber('palace/mirrors', 15, 15, 6)
    for x in range(1, 14):
        for z in range(1, 14):
            t.set(x, 0, z, mirrors[(x + z) % 3])
    for a in range(2, 13):
        if a % 3 == 1:
            continue
        for y in (2, 3, 4):
            for x, z in ((0, a), (14, a), (a, 0), (a, 14)):
                t.set(x, y, z, MIRROR_GLASS)
    for x, z in ((4, 4), (10, 4), (4, 10), (10, 10)):
        t.fill(x, 1, z, x, 6, z, crystal)
        t.set(x, 3, z, GLOW)
    t.set(7, 6, 7, LANTERN)
    doors(t, ('north', 'south'))

    # A crystal garden: a floor of sand, spikes of glass with lights inside.
    t = chamber('palace/crystal_garden', 15, 15, 7, floor=DUNE)
    t.fill(6, 0, 0, 8, 0, 14, SMOOTH)
    t.fill(0, 0, 6, 14, 0, 8, SMOOTH)
    for x, z, h in ((3, 3, 6), (11, 3, 5), (3, 11, 4), (11, 11, 7), (2, 7, 3), (12, 9, 3), (9, 12, 2), (5, 2, 2)):
        spike(t, x, 1, z, h)
    drift(t, 1, 1, 4, 4, 1, 1)
    doors(t, ('north', 'south', 'east'))

    # A basin: two pools on each side of the way, a column with a light standing in each.
    t = chamber('palace/basin', 13, 13, 6)
    for x0 in (2, 8):
        t.walls(x0 - 1, 0, 2, x0 + 3, 0, 10, PRISM_BRICKS)
        t.fill(x0, 0, 3, x0 + 2, 0, 9, WATER)
        cx = x0 + 1
        t.fill(cx, 0, 6, cx, 2, 6, PRISM)
        t.set(cx, 3, 6, GLOW)
    t.fill(5, 0, 1, 7, 0, 11, MIRROR)
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        column(t, x, z, 6)
    doors(t, ('north', 'south'))

    # The archives: rows of shelves, a reading desk, sand in a corner.
    t = chamber('palace/archives', 11, 15, 5)
    for z in range(2, 13):
        if z == 7:
            continue
        t.fill(1, 1, z, 1, 3, z, 'bookshelf')
        t.fill(9, 1, z, 9, 3, z, 'bookshelf')
        if 3 <= z <= 11:
            t.fill(3, 1, z, 3, 2, z, 'bookshelf')
            t.fill(7, 1, z, 7, 2, z, 'bookshelf')
    t.set(5, 1, 7, 'lectern[facing=north,has_book=false,powered=false]')
    t.set(0, 2, 7, GLOW)
    t.set(10, 2, 7, GLOW)
    t.set(5, 5, 3, LANTERN)
    t.set(5, 5, 11, LANTERN)
    drift(t, 8, 12, 9, 13, 1, 2)
    doors(t, ('north', 'south'))

    # The treasury, found empty: pedestals under glass cases, a gilded altar at the back (a dead end).
    t = chamber('palace/treasury', 13, 13, 6)
    for x in (3, 9):
        for z in (4, 7, 10):
            t.set(x, 1, z, CHISELED)
            t.set(x, 2, z, 'glass')
    t.fill(4, 1, 11, 8, 1, 11, CHISELED)
    t.fill(5, 2, 11, 7, 2, 11, SLAB)
    t.set(6, 2, 11, GLOW)
    t.fill(1, 0, 1, 11, 0, 11, DARK)
    t.fill(5, 0, 0, 7, 0, 10, MIRROR)
    t.set(6, 6, 6, LANTERN)
    doors(t, ('north',))

    # Quarters: four alcoves behind low walls, carpets and cushions, jars, lanterns.
    t = chamber('palace/quarters', 13, 11, 4)
    for x in (4, 8):
        t.fill(x, 1, 1, x, 3, 3, CUT)
        t.fill(x, 1, 7, x, 3, 9, CUT)
    for x0, z0, carpet, cushion in ((1, 1, 'yellow_carpet', 'orange_wool'), (9, 1, 'orange_carpet', 'yellow_wool'),
                                     (1, 7, 'orange_carpet', 'white_wool'), (9, 7, 'yellow_carpet', 'orange_wool')):
        t.fill(x0, 1, z0, x0 + 2, 1, z0 + 2, carpet)
        t.set(x0, 1, z0 + (2 if z0 == 1 else 0), cushion)
        t.set(x0 + 2, 1, z0 + (2 if z0 == 1 else 0), 'barrel[facing=up,open=false]')
        t.set(x0 + 1, 4, z0 + 1, LANTERN)
    t.set(1, 1, 5, 'potted_dead_bush')
    t.set(11, 1, 5, 'flower_pot')
    doors(t, ('north', 'south'))

    # A hall fallen in: a heap of sand up through a hole in the roof, rubble, a broken column.
    t = chamber('palace/collapsed', 13, 13, 6)
    for x in range(1, 12):
        for z in range(1, 12):
            d = ((x - 9) ** 2 + (z - 9) ** 2) ** 0.5
            h = min(6, round(6 - 1.6 * d + t.rng.random() * 0.6))
            for y in range(1, h + 1):
                t.set(x, y, z, DUNE)
    t.set(9, 7, 9, DUNE)
    column(t, 3, 3, 6)
    t.fill(3, 4, 3, 3, 6, 3, AIR)
    t.fill(4, 1, 4, 6, 1, 4, PRISM_BRICKS)
    t.scatter(1, 1, 1, 11, 1, 11, rubble, 8)
    doors(t, ('north', 'south'))

    # Crossroads: four doors, an obelisk of prismarine with a light, columns at the corners.
    t = chamber('palace/crossroads', 11, 11, 6)
    t.fill(4, 1, 4, 6, 1, 6, CHISELED)
    t.fill(5, 2, 5, 5, 5, 5, PRISM)
    t.set(5, 6, 5, GLOW)
    for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
        column(t, x, z, 6)
    t.disc(5, 0, 5, 4.5, MIRROR, ring=True)
    doors(t, ('north', 'south', 'east', 'west'))

    # ------------------------------------------------------------------ at the top of the stair, on the sand

    # A forecourt: two obelisks with glowing tips, low walls.
    t = pack.template(P + 'palace/forecourt', 11, 9, 9)
    t.fill(0, 0, 0, 10, 0, 8, paving)
    for x in (2, 8):
        t.set(x, 1, 4, CHISELED)
        t.fill(x, 2, 4, x, 6, 4, CUT)
        t.set(x, 7, 4, GLOW)
        t.set(x, 8, 4, 'end_rod[facing=up]')
    for x in (0, 10):
        t.fill(x, 1, 1, x, 1, 7, RAIL)
    t.jigsaw(5, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=SMOOTH)

    # A cluster of crystals out of the sand.
    t = pack.template(P + 'palace/crystals', 9, 11, 9)
    t.disc(4, 0, 4, 3.5, DUNE)
    spike(t, 4, 1, 5, 8)
    spike(t, 2, 1, 3, 4)
    spike(t, 6, 1, 3, 5)
    spike(t, 6, 1, 7, 3)
    t.set(4, 0, 0, SMOOTH)
    t.jigsaw(4, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=SMOOTH)

    # A stele on steps.
    t = pack.template(P + 'palace/stele', 7, 7, 7)
    t.fill(1, 0, 1, 5, 0, 5, paving)
    t.fill(2, 1, 2, 4, 1, 4, SLAB)
    t.fill(3, 1, 3, 3, 4, 3, CHISELED)
    t.fill(2, 2, 3, 4, 3, 3, CUT)
    t.set(3, 5, 3, GLOW)
    t.jigsaw(3, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=SMOOTH)

    pack.pool(GALLERIES, [(P + 'palace/gallery', 3), (P + 'palace/colonnade', 3), (P + 'palace/mirror_gallery', 2),
                          (P + 'palace/gallery_stairs', 2), (P + 'palace/gallery_sanded', 1)], fallback=P + 'palace/door_caps')
    pack.pool(P + 'palace/door_caps', [(P + 'palace/cap_door', 1)])
    pack.pool(ENTRANCE, [(P + 'palace/entrance', 1)], fallback=P + 'palace/door_caps')
    pack.pool(ROOMS, [(P + 'palace/mirrors', 2), (P + 'palace/crystal_garden', 2), (P + 'palace/basin', 2), (P + 'palace/archives', 2),
                      (P + 'palace/treasury', 1), (P + 'palace/quarters', 2), (P + 'palace/collapsed', 2), (P + 'palace/crossroads', 3)],
              fallback=P + 'palace/gallery_caps')
    pack.pool(P + 'palace/gallery_caps', [(P + 'palace/cap_gallery', 1)])
    pack.pool(EXTERIOR, [(P + 'palace/forecourt', 3), (P + 'palace/crystals', 2), (P + 'palace/stele', 1)])
    pack.pool(P + 'palace/start', [(P + 'palace/core', 1)])
    # Ten blocks under the surface, the sand left round it.
    pack.jigsaw(P + 'palace', P + 'palace/start', [P + 'glass_desert', P + 'crystal_desert', P + 'white_dunes'], size=7, max_distance=100,
                adaptation='none', start_height=-10)

    # ------------------------------------------------------------------ the small ones

    def tent(t, x0, z0, length, cloth, torn=False):
        """A tent along z, open at its north end."""
        for z in range(z0, z0 + length):
            for dx, y in ((0, 1), (1, 2), (2, 3), (3, 2), (4, 1)):
                if torn and t.rng.random() < 0.3:
                    continue
                t.set(x0 + dx, y, z, cloth)
            t.fill(x0 + 1, 1, z, x0 + 3, 1, z, AIR)
            t.set(x0 + 2, 2, z, AIR)
        back = z0 + length - 1
        t.fill(x0 + 1, 1, back, x0 + 3, 1, back, cloth)
        t.set(x0 + 2, 2, back, cloth)
        t.fill(x0 + 1, 1, z0 + 1, x0 + 3, 1, back - 1, 'yellow_carpet')
        t.set(x0 + 3, 1, back - 1, CRATE)

    # An outpost: a watchtower with a ladder to its battlements, a yard behind a low wall, a tent, crates.
    def outpost(path, sanded):
        t = pack.template(P + path, 13, 12, 13)
        t.fill(0, 0, 0, 12, 0, 12, paving)
        t.fill(0, 1, 0, 12, 10, 12, AIR)
        t.walls(0, 1, 0, 12, 1, 12, worn)
        t.walls(0, 2, 0, 12, 2, 12, RAIL)
        t.fill(5, 1, 12, 7, 2, 12, AIR)
        # The tower in the north-east corner.
        t.fill(7, 0, 0, 11, 8, 4, CUT)
        t.fill(8, 1, 1, 10, 7, 3, AIR)
        t.fill(9, 1, 4, 9, 2, 4, AIR)
        t.ladder(9, 1, 8, 1, 'south')
        for x in range(7, 12):
            for z in range(0, 5):
                if (x in (7, 11) or z in (0, 4)) and (x + z) % 2 == 0:
                    t.set(x, 9, z, CUT)
        for x, z in ((8, 0), (10, 4), (7, 2), (11, 2)):
            t.set(x, 5, z, 'glass_pane')
        t.set(9, 7, 3, LANTERN)
        if not sanded:
            t.set(8, 10, 0, POST_LANTERN)
        tent(t, 1, 1, 5, 'white_wool' if not sanded else 'light_gray_wool', torn=sanded)
        t.fill(2, 1, 8, 3, 2, 9, CRATE)
        t.set(4, 1, 9, 'barrel[facing=up,open=false]')
        t.set(9, 1, 8, 'g:ultra_sandstone')
        t.set(9, 2, 8, GLOW)
        if sanded:
            drift(t, 1, 6, 6, 11, 1, 3)
            drift(t, 7, 9, 11, 11, 1, 2)
            for x, z in ((0, 4), (12, 7), (3, 0)):
                t.fill(x, 1, z, x, 2, z, AIR)
            t.scatter(1, 1, 1, 11, 1, 11, rubble, 5)
        return t

    outpost('sites/outpost', False)
    outpost('sites/outpost_sanded', True)

    # A buried shrine: a stair down from the sand between two posts, a chamber under it (altar, crystal, columns).
    def shrine(path, collapsed):
        t = pack.template(P + path, 9, 10, 13)
        # Layer 5 is the level of the sand: the stair goes down from z 1 to the chamber's floor.
        t.fill(2, 0, 0, 6, 5, 5, gold)
        for i in range(5):
            z, y = 1 + i, 5 - i
            for x in range(3, 6):
                t.stairs(x, y, z, STEP, 'north')
                if y < 5:
                    t.fill(x, y + 1, z, x, 5, z, AIR)
        for x in (2, 6):
            t.fill(x, 6, 0, x, 7, 0, CHISELED)
            t.set(x, 8, 0, GLOW)
        # The chamber.
        t.fill(0, 0, 6, 8, 5, 12, gold)
        t.fill(1, 1, 7, 7, 4, 11, AIR)
        t.fill(1, 0, 7, 7, 0, 11, DARK)
        t.fill(3, 1, 6, 5, 3, 6, AIR)
        t.fill(4, 0, 6, 4, 0, 10, MIRROR)
        t.set(4, 1, 10, CHISELED)
        t.set(4, 2, 10, GLOW)
        spike(t, 4, 1, 11, 4, core=False)
        for x, z in ((1, 7), (7, 7), (1, 11), (7, 11)):
            column(t, x, z, 4)
        t.walls(0, 3, 6, 8, 3, 12, PRISM)
        t.set(0, 2, 9, GLOW)
        t.set(8, 2, 9, GLOW)
        t.set(4, 4, 8, LANTERN)
        if collapsed:
            for x, z in ((6, 9), (6, 10), (5, 10), (2, 8)):
                t.fill(x, 1, z, x, 4 if (x, z) == (6, 10) else t.rng.randint(1, 3), z, DUNE)
            t.set(6, 5, 10, DUNE)
            t.scatter(1, 1, 7, 7, 1, 11, rubble, 4)
        else:
            drift(t, 1, 7, 2, 8, 1, 1)
        return t

    shrine('sites/shrine', False)
    shrine('sites/shrine_collapsed', True)

    # An observation station: a raised platform, a glass dome over a telescope, mirrors on posts at the corners.
    def observatory(path, abandoned):
        t = pack.template(P + path, 13, 10, 13)
        t.fill(1, 0, 1, 11, 1, 11, CUT)
        t.fill(0, 0, 0, 12, 0, 12, paving)
        t.fill(0, 2, 0, 12, 8, 12, AIR)
        for x, z, facing in ((6, 0, 'south'), (6, 12, 'north')):
            t.stairs(x, 1, z, STEP, facing)
        t.walls(1, 2, 1, 11, 2, 11, RAIL)
        t.set(6, 2, 1, AIR)
        t.set(6, 2, 11, AIR)
        t.dome(6, 2, 6, 3.5, crystal)
        t.fill(5, 2, 3, 7, 3, 3, AIR)
        t.set(6, 3, 6, PRISM_RAIL)
        t.set(6, 4, 6, 'end_rod[facing=north]')
        t.set(6, 2, 6, CHISELED)
        t.set(5, 2, 7, 'lectern[facing=north,has_book=false,powered=false]')
        t.set(7, 2, 7, CRATE)
        t.set(6, 5, 6, GLOW)
        for x, z in ((2, 2), (10, 2), (2, 10), (10, 10)):
            t.fill(x, 2, z, x, 3, z, PRISM_RAIL)
            t.fill(x, 4, z, x, 5, z, MIRROR_GLASS)
        if abandoned:
            for x in range(2, 11):
                for y in range(4, 7):
                    for z in range(2, 11):
                        if t.get(x, y, z) in glass_names and t.rng.random() < 0.45:
                            t.set(x, y, z, AIR)
            t.fill(10, 4, 10, 10, 5, 10, AIR)
            t.fill(9, 2, 9, 10, 2, 9, MIRROR_GLASS)
            drift(t, 2, 2, 5, 10, 2, 2)
            drift(t, 0, 9, 12, 12, 1, 2)
        return t

    observatory('sites/observatory', False)
    observatory('sites/observatory_abandoned', True)

    # Crystal ruins: a ring of broken columns among shards of glass, or an arch still standing over fallen stones.
    def ruins(path, arch):
        t = pack.template(P + path, 13, 10, 13)
        t.disc(6, 0, 6, 5.5, paving)
        if not arch:
            for k, (x, z) in enumerate(((6, 1), (10, 3), (11, 7), (8, 11), (4, 11), (1, 7), (2, 3))):
                h = t.rng.randint(1, 6)
                t.set(x, 1, z, CHISELED)
                if h > 1:
                    t.fill(x, 2, z, x, h, z, PRISM_BRICKS)
                if k % 3 == 0:
                    t.set(x, h + 1, z, GLOW)
            spike(t, 6, 1, 6, 6)
            t.fill(3, 1, 8, 5, 1, 8, PRISM_BRICKS)
        else:
            for x in (3, 9):
                t.set(x, 1, 6, CHISELED)
                t.fill(x, 2, 6, x, 6, 6, PRISM_BRICKS)
            t.fill(3, 7, 6, 9, 7, 6, CUT)
            t.set(6, 7, 6, GLOW)
            t.set(6, 8, 6, CHISELED)
            t.fill(9, 3, 6, 9, 6, 6, AIR)                 # one leg broken off
            t.fill(9, 7, 6, 9, 7, 6, AIR)
            t.fill(10, 1, 8, 10, 1, 11, PRISM_BRICKS)     # its stones on the ground
            t.set(9, 1, 10, CUT)
            spike(t, 3, 1, 9, 3)
            spike(t, 8, 1, 2, 4)
        for _ in range(10):
            x, z = t.rng.randint(1, 11), t.rng.randint(1, 11)
            if t.get(x, 1, z) is None and full(t, x, 0, z):
                t.set(x, 1, z, crystal)
        drift(t, 1, 1, 11, 11, 1, 1)
        return t

    ruins('sites/ruins', False)
    ruins('sites/ruins_arch', True)

    desert = [P + b for b in ('ivory_canyon', 'oasis', 'glass_desert', 'crystal_desert', 'white_dunes')]
    dunes = [P + b for b in ('glass_desert', 'crystal_desert', 'white_dunes')]
    for name, variants, biomes, extra in (
            ('outpost', ('outpost', 'outpost_sanded'), dunes + [P + 'oasis'], {}),
            ('shrine', ('shrine', 'shrine_collapsed'), dunes, {'adaptation': 'none', 'start_height': -5}),
            ('observatory', ('observatory', 'observatory_abandoned'), desert, {}),
            ('ruins', ('ruins', 'ruins_arch'), desert, {})):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32, **extra)

    # The palace: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from the palace.
    pack.structure_set(P + 'landmark', [(P + 'palace', 1)], spacing=36, separation=14, salt=1395027761)
    pack.structure_set(P + 'sites', [(P + 'outpost', 3), (P + 'shrine', 3), (P + 'observatory', 2), (P + 'ruins', 3)],
                       spacing=11, separation=5, salt=884512093, exclusion=(P + 'landmark', 8))
