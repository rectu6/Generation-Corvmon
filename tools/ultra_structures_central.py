"""
Ultra Plant (Xurkitree): its structures, for tools/ultra_structures.py.

The great one: the power plant, a jigsaw of halls joined by service corridors. The generator hall
(a tower of charge stone with a glowing core behind slits of glass, an arc coil on top, four
capacitors, a catwalk round the walls); a gatehouse opens it on the outside, where a switchyard,
a cooling tower or a chimney stand. Corridors: plain, a conduit of glowing cables, an open
catwalk, stairs, a damaged one. Halls: a control room, a transformer hall, a battery room, a
turbine room, a workshop, a cooling room, a store, a hall struck by a discharge, junctions.
The small ones: a substation, a maintenance station, a relay tower, a technical shelter, each in
two variants.

Charge stone everywhere (bricks, chiseled, smooth, cobbled, glowing), basalt pipes, metal floors,
hazard stripes, cyan and yellow glass, lightning lanterns and crystal lights (the world is night).
No copper (nor lightning rods, made of it), no crystal block. Same connectors as the other worlds:
a hall's door is named `door` and asks the corridor pool for a `tube_end`; a corridor's end is
named `tube_end` and asks the hall pool for a `door`. Doors 3 wide and 3 high, floors at y 0.
"""

WORLD = 'ultra_central'
P = WORLD + '/'

AIR = 'minecraft:air'
BRICKS = 'g:charge_stone_bricks'
CRACKED = 'g:cracked_charge_stone_bricks'
TRIM = 'g:chiseled_charge_stone_bricks'
SMOOTH = 'g:smooth_charge_stone'
STONE = 'g:charge_stone'
COBBLE = 'g:charge_cobblestone'
MOSSY = 'g:mossy_charge_cobblestone'
SPARK = 'g:glowing_charge_cobblestone'
DRIP = 'g:charge_dripstone_block'
FLOOR = 'g:subway_floor_plain'
PLATE = 'g:subway_floor_cross'
LIGHT = 'g:lightning_lantern'
GLOW = 'g:crystal_light'
GLASS = 'cyan_stained_glass'
AMBER = 'yellow_stained_glass'
PANE = 'cyan_stained_glass_pane'
BARS = 'iron_bars'
CHAIN = 'chain[axis=y,waterlogged=false]'
ROD = 'end_rod[facing=up]'
STEP = 'g:charge_stone_brick_stairs'
SLAB = 'g:smooth_charge_stone_slab[type=bottom,waterlogged=false]'
TOP_SLAB = 'g:smooth_charge_stone_slab[type=top,waterlogged=false]'
RAIL = 'g:charge_stone_brick_wall'
DESK = 'polished_deepslate_slab[type=top,waterlogged=false]'
SCREEN = 'black_stained_glass'
YELLOW = 'yellow_concrete'
BLACK = 'black_concrete'
CRATE = 'g:crate'
BARREL = 'barrel[facing=up,open=false]'
LANTERN = 'lantern[hanging=true,waterlogged=false]'

CORRIDORS = P + 'plant/corridors'
HALLS = P + 'plant/halls'
GATE = P + 'plant/gate'
OUTSIDE = P + 'plant/outside'


def pipe(axis):
    return f'polished_basalt[axis={axis}]'


def build(u, pack):
    hull = u.Mix((BRICKS, 7), (CRACKED, 2), (STONE, 1))
    worn = u.Mix((BRICKS, 4), (CRACKED, 3), (COBBLE, 2), (MOSSY, 1))
    metal = u.Mix((FLOOR, 5), (PLATE, 1))
    ground = u.Mix(('gravel', 3), (COBBLE, 2), (STONE, 1))
    rubble = u.Mix((COBBLE, 3), (CRACKED, 2), ('gravel', 2), (DRIP, 1))

    # ------------------------------------------------------------------ helpers

    def hazard(t, x0, z0, x1, z1, y=0):
        """A band of yellow and black stripes round a rectangle of the floor."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    t.set(x, y, z, YELLOW if (x + z) % 2 == 0 else BLACK)

    def capacitor(t, x, z, h, base=TRIM):
        """A column of glowing charge stone behind cyan glass, on a chiseled base under a chiseled cap (y 1 to h + 1)."""
        t.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, base)
        t.fill(x - 1, 2, z - 1, x + 1, h, z + 1, GLASS)
        t.fill(x, 2, z, x, h, z, SPARK)
        t.fill(x - 1, h + 1, z - 1, x + 1, h + 1, z + 1, base)
        t.set(x, h + 2, z, ROD)

    def transformer(t, x0, z0, h=3):
        """A block of charge stone 3 x 3 with cooling fins of iron bars, insulators of end rods on top (y 1 to h)."""
        t.fill(x0, 1, z0, x0 + 2, h, z0 + 2, BRICKS)
        t.fill(x0, h, z0, x0 + 2, h, z0 + 2, TRIM)
        for z in range(z0, z0 + 3):
            t.fill(x0 - 1, 1, z, x0 - 1, h - 1, z, BARS)
            t.fill(x0 + 3, 1, z, x0 + 3, h - 1, z, BARS)
        for x in (x0, x0 + 2):
            t.set(x, h + 1, z0 + 1, ROD)
        t.set(x0 + 1, h + 1, z0 + 1, SPARK)

    def pylon(t, x, z, h, arms='x'):
        """A lattice pylon: iron bars round a basalt core, a cross-arm at the top, insulators and cables hanging."""
        t.fill(x, 1, z, x, h, z, pipe('y'))
        for y in range(1, h + 1, 2):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if y < h - 1:
                    t.set(x + dx, y, z + dz, BARS)
        for d in (-2, -1, 1, 2):
            ax, az = (x + d, z) if arms == 'x' else (x, z + d)
            t.set(ax, h, az, pipe(arms))
            if abs(d) == 2:
                t.set(ax, h - 1, az, CHAIN)
                t.set(ax, h + 1, az, ROD)
        t.set(x, h + 1, z, LIGHT)

    def sparks(t, x0, y0, z0, x1, y1, z1, count):
        t.scatter(x0, y0, z0, x1, y1, z1, SPARK, count)

    def doors(t, sides, pool=CORRIDORS):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=TRIM)

    def hall(path, sx, sz, h, floor=None, wall=None):
        """A closed hall over the whole template: floor at y 0, room up to h, roof at h + 1; basalt corners,
        a band of chiseled stone at y 1, a hazard stripe along the walls, lightning lanterns in the roof."""
        t = pack.template(P + path, sx, h + 2, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall or hull, floor or metal, SMOOTH)
        t.walls(0, 1, 0, sx - 1, 1, sz - 1, TRIM)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, pipe('y'))
        hazard(t, 1, 1, sx - 2, sz - 2)
        for x in range(3, sx - 2, 4):
            for z in range(3, sz - 2, 4):
                t.set(x, h + 1, z, LIGHT)
        return t

    # ------------------------------------------------------------------ the generator hall (start)

    t = pack.template(P + 'plant/generator', 27, 22, 27)
    c = 13
    t.room(0, 0, 26, 26, 14, hull, metal, SMOOTH)
    t.walls(0, 1, 0, 26, 1, 26, TRIM)
    t.walls(0, 6, 0, 26, 6, 26, TRIM)
    t.walls(0, 14, 0, 26, 14, 26, TRIM)
    for a in range(0, 27, 6):
        for x, z in ((a, 0), (a, 26), (0, a), (26, a)):
            t.fill(x, 0, z, x, 15, z, pipe('y'))
    for side in ('north', 'south', 'east', 'west'):
        t.windows(side, 9, 11, 3, 23, 6, GLASS, width=3)
    # Skylights of amber glass, lanterns in the roof.
    for a in (5, 21):
        t.fill(a - 1, 15, 4, a + 1, 15, 22, AMBER)
    for x in range(3, 24, 5):
        for z in range(3, 24, 5):
            if (x - c) ** 2 + (z - c) ** 2 > 7 ** 2:
                t.set(x, 15, z, LIGHT)
    # The generator: a tower of charge stone, its glowing core seen through slits, an arc coil through the roof.
    t.disc(c, 0, c, 6.5, YELLOW, ring=True)
    t.disc(c, 0, c, 5.5, BLACK, ring=True)
    for y in range(1, 12):
        t.disc(c, y, c, 4.5, TRIM if y in (1, 6, 11) else BRICKS)
        t.disc(c, y, c, 2.5, SPARK)
    for y in range(2, 11):
        if y == 6:
            continue
        for dx, dz in ((4, 0), (-4, 0), (0, 4), (0, -4), (3, 3), (-3, 3), (3, -3), (-3, -3)):
            t.set(c + dx, y, c + dz, GLASS)
    for y in range(12, 21):
        r = 2.5 if y % 2 == 0 else 1.5
        t.disc(c, y, c, r, BARS if y % 2 == 0 else TRIM)
        t.set(c, y, c, SPARK)
    t.set(c, 21, c, ROD)
    t.disc(c, 15, c, 3.5, AIR)
    t.disc(c, 15, c, 2.5, BARS)
    t.set(c, 15, c, SPARK)
    # Four capacitors, the cables to the generator under grates.
    for x, z in ((5, 5), (21, 5), (5, 21), (21, 21)):
        capacitor(t, x, z, 8)
        for i in range(2, 8):
            t.set(x + (i if x < c else -i), 0, z + (i if z < c else -i), SPARK)
    # A catwalk round the walls at y 7, iron bars along it, ladders in two corners.
    t.fill(1, 7, 1, 25, 7, 25, TOP_SLAB, only=(AIR,))
    t.fill(4, 7, 4, 22, 7, 22, AIR, only=(u.norm(TOP_SLAB),))
    t.walls(3, 8, 3, 23, 8, 23, BARS)
    t.ladder(1, 1, 7, 2, 'east')
    t.ladder(25, 1, 7, 24, 'west')
    # The control desk facing the generator, by the gate.
    t.fill(10, 1, 3, 16, 1, 3, DESK)
    for x in range(10, 17):
        t.set(x, 2, 3, SCREEN if x % 2 else GLASS)
        t.stairs(x, 1, 4, STEP, 'south')
    t.set(13, 2, 3, GLOW)
    # A crane hook hanging over the floor.
    t.fill(20, 9, 13, 20, 14, 13, CHAIN)
    t.set(20, 8, 13, 'anvil[facing=north]')
    t.fill(3, 1, 12, 4, 2, 14, CRATE)
    t.door('north', c, GATE, frame=TRIM)
    for side in ('east', 'west', 'south'):
        t.door(side, c, CORRIDORS, frame=TRIM)

    # ------------------------------------------------------------------ the gatehouse: from the hall to the outside

    t = pack.template(P + 'plant/gatehouse', 13, 8, 11)
    t.fill(0, 0, 0, 12, 0, 10, metal)
    t.fill(0, 1, 0, 12, 6, 10, hull)
    t.fill(1, 1, 1, 11, 5, 9, AIR)
    t.walls(0, 1, 0, 12, 1, 10, TRIM)
    t.fill(0, 6, 0, 12, 6, 10, SMOOTH)
    t.fill(5, 1, 0, 7, 3, 0, AIR)
    # The way out: a wide gate, a barrier, a guard's booth with its screens.
    t.fill(3, 1, 10, 9, 4, 10, AIR)
    t.fill(3, 5, 10, 9, 5, 10, YELLOW)
    t.fill(4, 1, 6, 8, 1, 6, BARS)
    t.set(6, 1, 6, AIR)
    t.fill(1, 1, 2, 3, 1, 2, DESK)
    t.set(1, 2, 2, SCREEN)
    t.set(2, 2, 2, GLASS)
    t.stairs(2, 1, 3, STEP, 'south')
    t.fill(9, 1, 1, 11, 2, 3, CRATE)
    hazard(t, 3, 7, 9, 9)
    for x in (3, 9):
        t.set(x, 5, 5, LIGHT)
    t.windows('east', 2, 3, 2, 8, 3, PANE)
    t.windows('west', 2, 3, 5, 8, 3, PANE)
    t.jigsaw(6, 1, 0, 'north', 'tube_end', 'door', HALLS)
    t.jigsaw(6, 0, 10, 'south', 'exit', 'yard', OUTSIDE, final=FLOOR)

    # ------------------------------------------------------------------ outside the gate

    # A switchyard: gravel, transformers, two pylons, a fence of iron bars.
    t = pack.template(P + 'plant/switchyard', 17, 16, 17)
    t.fill(0, 0, 0, 16, 0, 16, ground)
    t.fill(0, 1, 0, 16, 15, 16, AIR)
    t.walls(0, 1, 0, 16, 2, 16, BARS)
    for x, z in ((0, 0), (4, 0), (12, 0), (16, 0), (0, 16), (8, 16), (16, 16), (0, 8), (16, 8)):
        t.fill(x, 1, z, x, 3, z, pipe('y'))
    t.fill(7, 1, 0, 9, 2, 0, AIR)
    t.fill(7, 0, 0, 9, 0, 6, FLOOR)
    for x0, z0 in ((2, 3), (12, 3), (2, 11), (12, 11)):
        transformer(t, x0, z0)
    pylon(t, 8, 9, 13)
    pylon(t, 8, 14, 11)
    t.fill(6, 12, 9, 6, 12, 14, CHAIN.replace('axis=y', 'axis=z'))
    t.fill(10, 12, 9, 10, 12, 14, CHAIN.replace('axis=y', 'axis=z'))
    t.jigsaw(8, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)

    # A cooling tower: a hollow shell, narrower in its waist, a basin inside, steam rising from it.
    t = pack.template(P + 'plant/cooling_tower', 19, 26, 19)
    c = 9
    t.disc(c, 0, c, 9.0, ground)
    for y in range(1, 25):
        r = 8.5 - 3.0 * (1 - abs(y - 16) / 16.0) ** 1.5 if y <= 16 else 5.5 + (y - 16) * 0.25
        t.disc(c, y, c, r, SMOOTH if y % 6 else TRIM, ring=True)
        t.disc(c, y, c, r - 1.0, AIR)
    t.disc(c, 0, c, 6.5, TRIM)
    t.disc(c, 0, c, 5.5, 'water[level=0]')
    for dx, dz in ((0, 0), (2, 1), (-2, -1), (1, -2), (-1, 2)):
        t.set(c + dx, -0 + 0, c + dz, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
        t.set(c + dx, -0 + 1, c + dz, AIR)
    for y in range(1, 4):
        t.fill(c - 1, y, 0, c + 1, y, 2, AIR)
    t.fill(c - 1, 0, 0, c + 1, 0, 3, FLOOR)
    t.set(c, 4, 1, LIGHT)
    t.set(c, 25, c - 6, LIGHT)
    t.jigsaw(c, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)

    # A chimney on a yard of pipes.
    t = pack.template(P + 'plant/chimney', 11, 30, 11)
    t.fill(0, 0, 0, 10, 0, 10, ground)
    t.fill(0, 1, 0, 10, 29, 10, AIR)
    t.fill(3, 1, 3, 7, 26, 7, BRICKS)
    t.fill(4, 1, 4, 6, 26, 6, AIR)
    for y in range(2, 27, 6):
        t.walls(3, y, 3, 7, y, 7, YELLOW if y % 12 == 2 else BLACK)
    t.walls(3, 27, 3, 7, 27, 7, TRIM)
    t.set(5, 0, 5, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
    t.set(5, 28, 3, LIGHT)
    t.set(5, 28, 7, LIGHT)
    t.ladder(5, 1, 26, 2, 'north')
    t.fill(7, 1, 5, 7, 2, 5, AIR)
    t.fill(1, 1, 8, 9, 1, 8, pipe('x'))
    t.fill(1, 1, 2, 1, 1, 7, pipe('z'))
    t.fill(8, 1, 1, 9, 2, 2, CRATE)
    t.jigsaw(5, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=FLOOR)

    # ------------------------------------------------------------------ corridors (along z)

    def ends(t, x, y_out, length):
        t.jigsaw(x, 1, 0, 'north', 'tube_end', 'door', HALLS)
        t.jigsaw(x, y_out, length - 1, 'south', 'tube_end', 'door', HALLS)

    def corridor(path, length):
        """A corridor 3 wide inside (x 2 to 4), 3 high, a pipe along each wall at y 3, lanterns in the roof."""
        t = pack.template(P + path, 7, 6, length)
        t.fill(1, 0, 0, 5, 0, length - 1, metal)
        t.fill(1, 1, 0, 5, 4, length - 1, hull)
        t.fill(2, 1, 0, 4, 3, length - 1, AIR)
        t.fill(1, 3, 0, 1, 3, length - 1, pipe('z'))
        t.fill(5, 3, 0, 5, 3, length - 1, pipe('z'))
        for z in range(2, length - 1, 4):
            t.set(3, 4, z, LIGHT)
        for z in (0, length - 1):
            t.fill(1, 1, z, 1, 3, z, TRIM)
            t.fill(5, 1, z, 5, 3, z, TRIM)
            t.fill(1, 4, z, 5, 4, z, TRIM)
        ends(t, 3, 1, length)
        return t

    corridor('plant/corridor', 9)

    # A conduit: cables glowing behind iron bars on one side, windows on the other.
    t = corridor('plant/conduit', 11)
    t.fill(1, 1, 1, 1, 2, 9, SPARK)
    for z in range(1, 10):
        if z != 5:
            t.fill(2, 1, z, 2, 2, z, BARS)
        if z % 3:
            t.set(5, 2, z, PANE)

    # An open catwalk: a floor of plates between iron bars, posts at its ends, lights on posts.
    t = pack.template(P + 'plant/catwalk', 7, 6, 13)
    t.fill(1, 0, 0, 5, 0, 12, PLATE)
    t.fill(1, 1, 0, 5, 5, 12, AIR)
    t.fill(1, 1, 1, 1, 1, 11, BARS)
    t.fill(5, 1, 1, 5, 1, 11, BARS)
    for z in (0, 12):
        t.fill(1, 1, z, 1, 3, z, pipe('y'))
        t.fill(5, 1, z, 5, 3, z, pipe('y'))
        t.fill(1, 4, z, 5, 4, z, TRIM)
    for z in (4, 8):
        t.set(1, 2, z, pipe('y'))
        t.set(1, 3, z, LIGHT)
        t.set(5, 2, z, pipe('y'))
        t.set(5, 3, z, LIGHT)
    ends(t, 3, 1, 13)

    # Stairs: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'plant/corridor_stairs', 7, 10, 11)
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

    # A damaged corridor: a pipe burst, a hole in the roof, rubble, sparks.
    t = corridor('plant/corridor_damaged', 11)
    t.fill(1, 4, 4, 5, 4, 7, AIR, chance=0.6)
    t.fill(5, 3, 3, 5, 3, 6, AIR)
    t.set(4, 3, 5, pipe('x'))
    t.scatter(2, 1, 1, 4, 1, 9, rubble, 4)
    sparks(t, 2, 1, 1, 4, 1, 9, 2)
    t.fill(5, 1, 6, 5, 2, 6, CRACKED)

    for name, connector, target, block in (('plant/cap_door', 'tube_end', 'door', BRICKS), ('plant/cap_corridor', 'door', 'tube_end', TRIM)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, block)
        t.set(2, 2, 0, LIGHT)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=block)

    # ------------------------------------------------------------------ halls

    # The control room: three rows of desks with screens and seats, a holographic table, levers on the walls.
    t = hall('plant/control_room', 15, 13, 5)
    for z in (3, 6):
        t.fill(3, 1, z, 11, 1, z, DESK)
        for x in range(3, 12):
            t.set(x, 2, z, SCREEN if (x + z) % 3 else GLASS)
            t.stairs(x, 1, z + 1, STEP, 'south')
    t.fill(2, 2, 12, 12, 4, 12, SCREEN)
    for x in range(3, 12, 2):
        t.set(x, 3, 12, GLASS)
    t.set(7, 1, 9, GLOW)
    t.set(7, 2, 9, 'light_blue_stained_glass')
    for z in (2, 4, 8, 10):
        t.set(1, 2, z, 'lever[face=wall,facing=east,powered=false]')
        t.set(13, 2, z, 'lever[face=wall,facing=west,powered=false]')
    doors(t, ('north', 'east', 'west'))

    # The transformer hall: four transformers, cables from their insulators to the roof.
    t = hall('plant/transformers', 15, 15, 7)
    for x0, z0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
        transformer(t, x0, z0, 4)
        t.fill(x0 + 1, 6, z0 + 1, x0 + 1, 7, z0 + 1, CHAIN)
    t.fill(7, 0, 1, 7, 0, 13, PLATE)
    t.fill(1, 0, 7, 13, 0, 7, PLATE)
    doors(t, ('north', 'south', 'east'))

    # The battery room: two rows of glowing cells behind bars, a walkway between them.
    t = hall('plant/batteries', 13, 15, 5)
    for cell, front, guard in ((1, 2, 3), (11, 10, 9)):
        for z in range(2, 13, 2):
            t.set(cell, 1, z, TRIM)
            t.fill(cell, 2, z, cell, 3, z, SPARK)
            t.set(cell, 4, z, TRIM)
            t.fill(front, 1, z, front, 4, z, GLASS)
        t.fill(guard, 1, 2, guard, 1, 12, BARS)
    t.fill(5, 0, 1, 7, 0, 13, PLATE)
    doors(t, ('north', 'south'))

    # The turbine room: a turbine lying along x on a chiseled bed, its wheel glowing, a catwalk over it.
    t = hall('plant/turbines', 17, 13, 7)
    t.fill(3, 1, 4, 13, 1, 8, TRIM)
    for x in range(3, 14):
        r = 2.5 if x in (3, 4, 12, 13) else 1.5
        for dy in range(-3, 4):
            for dz in range(-3, 4):
                if dy * dy + dz * dz <= r * r and 2 <= 4 + dy:
                    t.set(x, 4 + dy, 6 + dz, pipe('x'))
    for dy in range(-3, 4):
        for dz in range(-3, 4):
            if dy * dy + dz * dz <= 3.5 ** 2 and 4 + dy >= 2:
                t.set(8, 4 + dy, 6 + dz, SPARK if dy * dy + dz * dz <= 2.5 ** 2 else BARS)
    t.fill(1, 5, 2, 15, 5, 2, TOP_SLAB)
    t.fill(1, 5, 10, 15, 5, 10, TOP_SLAB)
    t.ladder(15, 1, 5, 1, 'south')
    doors(t, ('east', 'west'))

    # A workshop: benches, an anvil, a grindstone, crates, a generator part taken apart.
    t = hall('plant/workshop', 13, 13, 5)
    t.fill(1, 1, 1, 5, 1, 1, DESK)
    t.set(1, 1, 2, 'crafting_table')
    t.set(2, 1, 2, 'smithing_table')
    t.set(4, 1, 2, 'anvil[facing=east]')
    t.set(5, 1, 2, 'grindstone[face=floor,facing=north]')
    t.fill(9, 1, 9, 11, 2, 11, CRATE)
    t.set(8, 1, 11, BARREL)
    t.fill(5, 1, 6, 7, 1, 7, pipe('x'))
    t.set(6, 2, 6, SPARK)
    t.set(6, 2, 7, BARS)
    t.fill(6, 3, 6, 6, 5, 6, CHAIN)
    doors(t, ('north', 'south'))

    # The cooling room: two basins of water, pipes running into them, blue ice in the water.
    t = hall('plant/cooling', 15, 15, 6)
    for xr in (1, 9):
        t.walls(xr, 0, 2, xr + 4, 1, 12, TRIM)
        t.fill(xr + 1, 0, 3, xr + 3, 1, 11, 'water[level=0]')
        t.set(xr + 1, 1, 5, 'packed_ice')
        t.set(xr + 3, 1, 9, 'packed_ice')
        t.fill(xr + 2, 6, 1, xr + 2, 6, 13, pipe('z'))
        t.fill(xr + 2, 2, 7, xr + 2, 5, 7, pipe('y'))
    t.fill(6, 0, 1, 8, 0, 13, PLATE)
    doors(t, ('north', 'south'))

    # A store: shelves of crates and barrels.
    t = hall('plant/store', 13, 11, 5)
    for x in (2, 5, 8, 11):
        for z in range(2, 9):
            if z == 5:
                continue
            h = t.rng.randint(1, 3)
            for y in range(1, h + 1):
                t.set(x, y, z, CRATE if t.rng.random() < 0.7 else BARREL)
    doors(t, ('north',))

    # A hall struck by a discharge: the roof torn open, a capacitor burst, scorched floor, sparks.
    t = hall('plant/struck', 13, 13, 6, wall=worn)
    for x in range(13):
        for y in range(4, 8):
            for z in range(13):
                if ((x - 8) ** 2 + (y - 7) ** 2 + (z - 5) ** 2) ** 0.5 < 4.2 + t.rng.random():
                    t.set(x, y, z, AIR)
    t.fill(3, 1, 7, 5, 1, 9, TRIM)
    t.fill(3, 2, 7, 5, 2, 9, GLASS, chance=0.4)
    t.set(4, 2, 8, SPARK)
    for _ in range(14):
        x, z = t.rng.randint(1, 11), t.rng.randint(1, 11)
        t.set(x, 0, z, u.Mix(('blackstone', 2), ('black_concrete_powder', 2), (SPARK, 1)))
    t.scatter(1, 1, 1, 11, 1, 11, rubble, 8)
    doors(t, ('north', 'south'))

    # A junction: four doors round an electric pillar in its cage.
    t = hall('plant/junction', 11, 11, 6)
    t.fill(4, 1, 4, 6, 1, 6, TRIM)
    t.fill(5, 2, 5, 5, 5, 5, SPARK)
    t.fill(4, 2, 4, 6, 5, 6, BARS, only=(AIR,))
    t.set(5, 6, 5, GLOW)
    doors(t, ('north', 'south', 'east', 'west'))

    pack.pool(CORRIDORS, [(P + 'plant/corridor', 3), (P + 'plant/conduit', 3), (P + 'plant/catwalk', 2), (P + 'plant/corridor_stairs', 2),
                          (P + 'plant/corridor_damaged', 1)], fallback=P + 'plant/door_caps')
    pack.pool(P + 'plant/door_caps', [(P + 'plant/cap_door', 1)])
    pack.pool(GATE, [(P + 'plant/gatehouse', 1)], fallback=P + 'plant/door_caps')
    pack.pool(HALLS, [(P + 'plant/control_room', 2), (P + 'plant/transformers', 2), (P + 'plant/batteries', 2), (P + 'plant/turbines', 2),
                      (P + 'plant/workshop', 2), (P + 'plant/cooling', 2), (P + 'plant/store', 1), (P + 'plant/struck', 1),
                      (P + 'plant/junction', 3)], fallback=P + 'plant/corridor_caps')
    pack.pool(P + 'plant/corridor_caps', [(P + 'plant/cap_corridor', 1)])
    pack.pool(OUTSIDE, [(P + 'plant/switchyard', 3), (P + 'plant/cooling_tower', 2), (P + 'plant/chimney', 2)])
    pack.pool(P + 'plant/start', [(P + 'plant/generator', 1)])
    plains = [P + b for b in ('conductive_plains', 'magnetic_valley', 'crystal_fields', 'cable_forest')]
    pack.jigsaw(P + 'plant', P + 'plant/start', plains, size=7, max_distance=100)

    # ------------------------------------------------------------------ the small ones

    # A substation: a fenced yard, two transformers, a pylon, a hut with its desk.
    def substation(path, ruined):
        t = pack.template(P + path, 15, 15, 15)
        t.fill(0, 0, 0, 14, 0, 14, ground)
        t.fill(0, 1, 0, 14, 14, 14, AIR)
        t.walls(0, 1, 0, 14, 2, 14, BARS)
        for x, z in ((0, 0), (7, 0), (14, 0), (0, 7), (14, 7), (0, 14), (14, 14)):
            t.fill(x, 1, z, x, 3, z, pipe('y'))
        t.fill(6, 1, 14, 8, 2, 14, AIR)
        t.fill(6, 0, 9, 8, 0, 14, FLOOR)
        transformer(t, 2, 2)
        transformer(t, 2, 7)
        pylon(t, 10, 4, 12, arms='z')
        # The hut.
        t.fill(9, 0, 9, 13, 0, 13, FLOOR)
        t.fill(9, 1, 9, 13, 4, 13, BRICKS)
        t.fill(10, 1, 10, 12, 3, 12, AIR)
        t.fill(11, 1, 9, 11, 2, 9, AIR)
        t.fill(9, 4, 9, 13, 4, 13, SMOOTH)
        t.set(12, 1, 12, DESK)
        t.set(12, 2, 12, SCREEN)
        t.set(10, 1, 12, CRATE)
        t.set(11, 3, 11, LIGHT)
        t.set(13, 2, 11, PANE)
        if ruined:
            for x, z in ((0, 5), (0, 6), (14, 9), (4, 14), (3, 0), (11, 0)):
                t.fill(x, 1, z, x, 2, z, AIR)
            t.fill(1, 1, 7, 5, 4, 9, AIR)                     # a transformer gone over
            t.fill(1, 1, 8, 4, 1, 10, worn, chance=0.6)
            t.fill(10, 4, 10, 13, 4, 12, AIR, chance=0.5)
            t.fill(8, 10, 1, 12, 14, 7, AIR)                  # the pylon broken
            t.set(11, 1, 3, pipe('x'))
            t.set(12, 1, 3, pipe('x'))
            t.scatter(1, 1, 1, 13, 1, 13, MOSSY, 6)
            sparks(t, 1, 1, 1, 8, 1, 13, 3)
        return t

    substation('sites/substation', False)
    substation('sites/substation_ruined', True)

    # A maintenance station: a shed open at the front under a gantry crane, benches, crates.
    def maintenance(path, abandoned):
        t = pack.template(P + path, 15, 10, 13)
        t.fill(0, 0, 0, 14, 0, 12, metal)
        t.fill(0, 1, 0, 14, 9, 12, AIR)
        t.fill(1, 1, 5, 13, 5, 12, hull)
        t.fill(2, 1, 6, 12, 4, 11, AIR)
        t.fill(4, 1, 5, 10, 4, 5, AIR)
        t.fill(1, 5, 5, 13, 5, 12, SMOOTH)
        hazard(t, 3, 0, 11, 4)
        t.fill(2, 1, 11, 6, 1, 11, DESK)
        t.set(2, 1, 10, 'crafting_table')
        t.set(4, 1, 10, 'anvil[facing=east]')
        t.fill(10, 1, 9, 12, 2, 11, CRATE)
        t.set(9, 1, 11, BARREL)
        t.set(7, 4, 8, LIGHT)
        # The gantry: two posts, a beam, a hook.
        for x in (2, 12):
            t.fill(x, 1, 2, x, 7, 2, pipe('y'))
        t.fill(2, 8, 2, 12, 8, 2, pipe('x'))
        t.fill(7, 5, 2, 7, 7, 2, CHAIN)
        t.set(7, 4, 2, 'anvil[facing=north]' if not abandoned else AIR)
        t.set(2, 9, 2, LIGHT)
        t.set(12, 9, 2, LIGHT)
        t.fill(6, 1, 2, 8, 1, 3, pipe('z'))
        t.set(7, 2, 2, SPARK)
        if abandoned:
            t.fill(8, 5, 6, 13, 5, 12, AIR, chance=0.7)
            t.fill(9, 8, 2, 12, 9, 2, AIR)
            t.set(11, 1, 1, pipe('x'))
            t.set(12, 1, 1, pipe('x'))
            t.scatter(1, 1, 1, 13, 1, 11, rubble, 7)
            t.scatter(1, 1, 1, 13, 1, 11, MOSSY, 4)
        return t

    maintenance('sites/maintenance', False)
    maintenance('sites/maintenance_abandoned', True)

    # A relay tower: four legs of basalt braced with iron bars, platforms, a ladder, antennas and lights at the top.
    def relay(path, broken):
        top = 22
        t = pack.template(P + path, 9, top + 4, 9)
        t.fill(0, 0, 0, 8, 0, 8, ground)
        t.fill(1, 0, 1, 7, 0, 7, SMOOTH)
        for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
            t.fill(x, 1, z, x, top, z, pipe('y'))
        for y in range(3, top, 3):
            for a in range(3, 6):
                for x, z in ((a, 2), (a, 6), (2, a), (6, a)):
                    if (a + y) % 2 == 0:
                        t.set(x, y, z, BARS)
        for y in (8, 15, top):
            t.fill(2, y, 2, 6, y, 6, TOP_SLAB, only=(None, AIR))
            t.set(4, y, 3, AIR)
        t.ladder(4, 1, top, 3, 'south')
        t.fill(4, 1, 2, 4, top, 2, BRICKS)
        t.walls(2, top + 1, 2, 6, top + 1, 6, BARS)
        t.fill(4, top + 1, 4, 4, top + 3, 4, pipe('y'))
        t.set(4, top + 3, 4, ROD)
        for x, z in ((2, 2), (6, 6)):
            t.set(x, top + 2, z, LIGHT)
        t.set(2, top + 2, 6, ROD)
        t.set(6, top + 2, 2, ROD)
        t.set(5, 1, 6, CRATE)
        t.set(3, 1, 6, SPARK)
        if broken:
            t.fill(0, 16, 0, 8, top + 3, 8, AIR)
            t.fill(6, 13, 2, 6, 15, 6, AIR)
            for x, z in ((7, 1), (8, 3), (8, 4), (1, 8), (7, 8)):
                t.set(x, 1, z, pipe('x') if x > 6 else pipe('z'))
            t.set(7, 2, 1, LIGHT)
            t.scatter(0, 1, 0, 8, 1, 8, BARS, 4)
            t.set(4, 15, 4, SPARK)
        return t

    relay('sites/relay', False)
    relay('sites/relay_broken', True)

    # A technical shelter: a bunker of smooth charge stone, an iron door, panels of levers, a bench, a lamp.
    def shelter(path, overgrown):
        t = pack.template(P + path, 11, 7, 11)
        t.fill(0, 0, 0, 10, 0, 10, ground)
        t.fill(1, 0, 1, 9, 0, 9, metal)
        t.fill(1, 1, 1, 9, 4, 9, SMOOTH if not overgrown else worn)
        t.fill(2, 1, 2, 8, 3, 8, AIR)
        t.fill(0, 5, 0, 10, 5, 10, AIR)
        t.fill(1, 5, 1, 9, 5, 9, SLAB)
        t.walls(1, 4, 1, 9, 4, 9, TRIM)
        t.set(5, 1, 1, 'iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
        t.set(5, 2, 1, 'iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
        t.set(4, 2, 1, LIGHT)
        hazard(t, 4, 0, 6, 0)
        for x in (3, 5, 7):
            t.set(x, 2, 8, 'lever[face=wall,facing=north,powered=false]')
            t.set(x, 3, 8, SCREEN)
        t.fill(2, 1, 8, 8, 1, 8, TRIM)
        t.fill(8, 1, 3, 8, 1, 6, SLAB)
        t.set(2, 1, 3, CRATE)
        t.set(2, 1, 4, BARREL)
        t.set(5, 3, 5, LANTERN)
        t.set(9, 2, 5, PANE)
        t.set(1, 2, 5, PANE)
        if overgrown:
            t.set(5, 1, 1, AIR)
            t.set(5, 2, 1, AIR)
            t.fill(6, 4, 5, 8, 5, 8, AIR)
            t.fill(6, 1, 6, 8, 1, 7, rubble, chance=0.7)
            t.scatter(0, 1, 0, 10, 1, 10, MOSSY, 6)
            t.fill(1, 5, 1, 9, 5, 9, 'moss_carpet', only=(u.norm(SLAB),), chance=0.5)
            sparks(t, 2, 1, 2, 8, 1, 8, 2)
        return t

    shelter('sites/shelter', False)
    shelter('sites/shelter_overgrown', True)

    everywhere = [P + b for b in ('electric_mountains', 'magnetic_valley', 'crystal_fields', 'cable_forest', 'conductive_plains')]
    for name, variants, biomes in (
            ('substation', ('substation', 'substation_ruined'), plains),
            ('maintenance', ('maintenance', 'maintenance_abandoned'), plains),
            ('relay', ('relay', 'relay_broken'), everywhere),
            ('shelter', ('shelter', 'shelter_overgrown'), everywhere)):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32)

    # The plant: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from the plant.
    pack.structure_set(P + 'landmark', [(P + 'plant', 1)], spacing=36, separation=14, salt=1503348271)
    pack.structure_set(P + 'sites', [(P + 'substation', 3), (P + 'maintenance', 3), (P + 'relay', 2), (P + 'shelter', 3)],
                       spacing=11, separation=5, salt=702914457, exclusion=(P + 'landmark', 8))
