"""
Ultra Megalopolis (Necrozma's ruined city): its structures, for tools/ultra_structures.py.

The great one: a complex of ruined towers whose floors can be visited. The main tower's lobby is
the start; its floors are stacked by the jigsaw itself (a connector up in the middle of each floor,
one down in the next one's slab: both keep the same turn), twelve of them, then a roof. Each floor
has its concrete core with a ladder through all the slabs. The lobby opens on a plaza, and on
streets: arcades, open streets, a passage, steps, a street of rubble, which lead to the lobbies of
two other towers (glass offices, brick flats), a mall, a car park, an overgrown atrium, a metro
hall, crossroads. Floors: offices, flats, archives, fallen in, burnt, overgrown, an empty shell.
Roofs: a helipad, a garden, a water tank, a spire, a broken top.
The small ones: a filling station, a small fallen building, a security post, a warehouse, each in
two variants.

Concrete, stone bricks, bricks, glass panes (many broken), the city's own blocks, lightning lanterns in
the slabs (a slab lights the floor under it too: the shaders want real light), moss and leaves
where the city went wild. The city blocks of the world (generations_core:city_block) leave the
chunks of these structures alone. Ground connectors as in the other worlds (a room's door `door`
asks the street pool for a `tube_end`, a street's end `tube_end` asks the room pool for a `door`);
floors: `up` (top of a floor, asking for a `down`) and `down` (in the next one's slab).
"""

WORLD = 'ultra_skyscraper'
P = WORLD + '/'

AIR = 'minecraft:air'
CONCRETE = 'light_gray_concrete'
GREY = 'gray_concrete'
WHITE = 'white_concrete'
BLACK = 'black_concrete'
STONE = 'stone_bricks'
SMOOTH = 'smooth_stone'
ANDESITE = 'polished_andesite'
BRICKS = 'bricks'
ASPHALT = 'gray_concrete'
LINE = 'white_concrete'
PANE = 'glass_pane'
TINT = 'light_gray_stained_glass_pane'
LIGHT = 'g:lightning_lantern'
LANTERN = 'lantern[hanging=true,waterlogged=false]'
STANDING = 'lantern[hanging=false,waterlogged=false]'
DESK = 'polished_andesite_slab[type=top,waterlogged=false]'
SLAB = 'smooth_stone_slab[type=bottom,waterlogged=false]'
SEAT = 'polished_blackstone_stairs'
STEP = 'stone_brick_stairs'
RAIL = 'andesite_wall'
BARS = 'iron_bars'
MOSS = 'moss_block'
CARPET = 'moss_carpet'
LEAVES = 'oak_leaves[distance=1,persistent=true,waterlogged=false]'
AZALEA = 'azalea_leaves[distance=1,persistent=true,waterlogged=false]'
CRATE = 'g:crate'
BARREL = 'barrel[facing=up,open=false]'
FLOOR_TILE = 'g:house_floor_4'
SUBWAY = 'g:subway_floor_path'
SUBWAY_PLAIN = 'g:subway_floor_plain'

STREETS = P + 'complex/streets'
ROOMS = P + 'complex/rooms'
PLAZA = P + 'complex/plaza'
MAIN_FLOORS = P + 'complex/main_floors'
MAIN_ROOFS = P + 'complex/main_roofs'
OFFICE_FLOORS = P + 'complex/office_floors'
OFFICE_ROOFS = P + 'complex/office_roofs'
FLAT_FLOORS = P + 'complex/flat_floors'
FLAT_ROOFS = P + 'complex/flat_roofs'


def build(u, pack):
    rubble = u.Mix(('gravel', 3), ('cobblestone', 2), ('cracked_stone_bricks', 2), ('andesite', 1), ('gray_concrete_powder', 1))
    road = u.Mix((ASPHALT, 10), ('gray_concrete_powder', 2), ('cobbled_deepslate', 1), ('gravel', 1), ('coarse_dirt', 1))
    walk = u.Mix((SMOOTH, 4), (ANDESITE, 3), (STONE, 2), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1))
    wild = u.Mix((MOSS, 3), ('grass_block', 2), ('coarse_dirt', 1))
    burnt = u.Mix((BLACK, 3), ('black_concrete_powder', 2), ('blackstone', 1), ('gray_concrete', 2))
    weeds = ('short_grass', 'fern', 'moss_carpet', 'short_grass')

    # ------------------------------------------------------------------ helpers

    def slab_lights(t, s, y=0, step=4, block=LIGHT):
        """Lightning lanterns set in a slab, in a grid: they light the floor above and the one below."""
        for x in range(2, s - 2, step):
            for z in range(2, s - 2, step):
                t.set(x, y, z, block)

    def core(t, s, top, y0=1, door=True):
        """The tower's concrete core in the middle (5 x 5), a ladder up its south side from y 0 to `top`."""
        c = s // 2
        t.walls(c - 2, y0, c - 2, c + 2, top, c + 2, SMOOTH)
        t.fill(c - 1, y0, c - 1, c + 1, top, c + 1, AIR)
        if door:
            t.fill(c, y0, c - 2, c, y0 + 1, c - 2, AIR)
        t.ladder(c, 0 if y0 == 1 and t.get(c, 0, c + 1) == AIR else y0, top, c + 1, 'north')

    def facade(t, s, style, broken=0.0, y0=1, y1=4):
        """The outer walls of a floor: glass between columns (offices) or bricks with windows (flats); a band at the top."""
        for x in range(s):
            for z in range(s):
                if x not in (0, s - 1) and z not in (0, s - 1):
                    continue
                corner = x in (0, s - 1) and z in (0, s - 1)
                a = x if z in (0, s - 1) else z
                if style == 'glass':
                    if corner or a % 3 == 0:
                        t.fill(x, y0, z, x, y1, z, CONCRETE)
                    else:
                        t.fill(x, y0, z, x, y1 - 1, z, PANE if (x + z) % 5 else TINT)
                        t.set(x, y1, z, GREY)
                else:
                    t.fill(x, y0, z, x, y1, z, BRICKS)
                    if not corner and a % 2 == 1:
                        t.fill(x, y0 + 1, z, x, y1 - 1, z, PANE)
                    t.set(x, y1, z, STONE)
        if broken:
            for (x, y, z), b in list(t.blocks.items()):
                if b in (u.norm(PANE), u.norm(TINT)) and t.rng.random() < broken:
                    t.set(x, y, z, AIR)

    def floor_piece(path, s, style, broken=0.1):
        """A floor of a tower: its slab (lightning lanterns in it, the hole of the ladder), the core, the facade,
        a connector down in the slab and one up at the top, in the middle."""
        c = s // 2
        t = pack.template(P + path, s, 5, s)
        t.fill(0, 0, 0, s - 1, 0, s - 1, ANDESITE if style == 'glass' else SMOOTH)
        slab_lights(t, s)
        t.fill(0, 1, 0, s - 1, 4, s - 1, AIR)
        t.set(c, 0, c + 1, AIR)
        core(t, s, 4)
        facade(t, s, style, broken)
        t.jigsaw(c, 0, c, 'down', 'down', 'up', 'minecraft:empty', final=ANDESITE if style == 'glass' else SMOOTH)
        return t

    def up(t, s, y, pool):
        c = s // 2
        t.jigsaw(c, y, c, 'up', 'up', 'down', pool)

    def roof_piece(path, s):
        """A roof: its slab with the ladder's hole, the hut where the ladder comes out, a parapet."""
        c = s // 2
        t = pack.template(P + path, s, 12, s)
        t.fill(0, 0, 0, s - 1, 0, s - 1, SMOOTH)
        t.fill(0, 1, 0, s - 1, 11, s - 1, AIR)
        t.set(c, 0, c + 1, AIR)
        t.walls(c - 2, 1, c - 2, c + 2, 3, c + 2, SMOOTH)
        t.fill(c - 1, 1, c - 1, c + 1, 3, c + 1, AIR)
        t.fill(c, 1, c - 2, c, 2, c - 2, AIR)
        t.fill(c - 2, 4, c - 2, c + 2, 4, c + 2, SLAB)
        t.ladder(c, 0, 1, c + 1, 'north')
        t.set(c, 3, c, LANTERN)
        t.walls(0, 1, 0, s - 1, 1, s - 1, RAIL)
        t.jigsaw(c, 0, c, 'down', 'down', 'up', 'minecraft:empty', final=SMOOTH)
        return t

    def scatter_rubble(t, s, count, y=1):
        t.scatter(1, y, 1, s - 2, y, s - 2, rubble, count)

    def outside_core(s):
        c = s // 2
        return lambda x, z: abs(x - c) > 2 or abs(z - c) > 2

    # Interiors of a floor (y 1 to 3; the core and a ring round it kept free).
    def offices(t, s):
        free = outside_core(s)
        c = s // 2
        for z in range(2, s - 2, 4):
            for x in range(2, s - 2):
                if not free(x, z) or abs(x - c) <= 3 and abs(z - c) <= 3:
                    continue
                if x % 5 == 0:
                    continue
                t.set(x, 1, z, DESK)
                t.set(x, 2, z, 'gray_stained_glass_pane' if x % 2 else AIR)
                t.stairs(x, 1, z + 1, SEAT, 'south')
        for x, z in ((1, 1), (s - 2, 1), (1, s - 2), (s - 2, s - 2)):
            t.set(x, 1, z, 'potted_fern')

    def flats(t, s):
        """Two flats in the south half (a wall between them, a doorway each from the common room), a common room
        in the north half, before the core's door."""
        c = s // 2
        for y in range(1, 5):
            for a in range(1, c - 2):
                t.set(a, y, c, WHITE)
                t.set(s - 1 - a, y, c, WHITE)
                t.set(c, y, s - 1 - a, WHITE)
        for x in (2, s - 3):
            t.fill(x, 1, c, x, 2, c, AIR)
        for west in (True, False):
            x0, x1 = (1, c - 1) if west else (c + 1, s - 2)
            bx, kx = (x0, x1) if west else (x1, x0)
            t.set(bx, 1, s - 2, 'red_bed[facing=south,occupied=false,part=head]')
            t.set(bx, 1, s - 3, 'red_bed[facing=south,occupied=false,part=foot]')
            t.set(kx, 1, s - 2, 'water_cauldron[level=1]')
            t.set(kx, 1, s - 3, 'crafting_table')
            t.set(kx, 1, s - 4, f'furnace[facing={"west" if west else "east"},lit=false]')
            t.set((x0 + x1) // 2, 1, s - 3, 'white_carpet')
            t.set(bx, 1, c + 1, 'bookshelf')
        t.fill(2, 1, 2, 3, 1, 3, DESK)
        t.stairs(1, 1, 2, SEAT, 'west')
        t.stairs(4, 1, 3, SEAT, 'east')
        t.fill(s - 3, 1, 1, s - 2, 2, 1, 'bookshelf')
        t.set(s - 2, 1, 3, 'potted_fern')

    def archives(t, s):
        free = outside_core(s)
        for x in range(2, s - 2, 3):
            for z in range(2, s - 2):
                if free(x, z) and abs(z - s // 2) > 3 and z % 6 != 0:
                    for y in range(1, 1 + t.rng.randint(2, 3)):
                        t.set(x, y, z, 'bookshelf' if t.rng.random() < 0.5 else CRATE)

    def overgrow(t, s, amount):
        """Moss on the floor, bushes of leaves, vines down the facade's inside."""
        for x in range(1, s - 1):
            for z in range(1, s - 1):
                if t.get(x, 1, z) != AIR or t.get(x, 0, z) in (AIR, u.norm(LIGHT)):
                    continue
                r = t.rng.random()
                if r < amount * 0.5:
                    t.set(x, 0, z, MOSS)
                    if t.rng.random() < 0.4:
                        t.set(x, 1, z, t.rng.choice(weeds))
                elif r < amount * 0.6:
                    t.set(x, 1, z, AZALEA if t.rng.random() < 0.5 else LEAVES)
        for a in range(1, s - 1):
            if t.rng.random() < amount:
                for side, (x, z, prop) in {'n': (a, 1, 'north'), 's': (a, s - 2, 'south'), 'w': (1, a, 'west'), 'e': (s - 2, a, 'east')}.items():
                    if t.rng.random() < 0.5 and t.get(x, 3, z) == AIR:
                        props = ','.join(f'{d}={"true" if d == prop else "false"}' for d in ('east', 'north', 'south', 'up', 'west'))
                        for y in range(4 - t.rng.randint(1, 3), 4):
                            if t.get(x, y, z) == AIR:
                                t.set(x, y, z, f'vine[{props}]')

    def burn(t, s):
        for x in range(1, s - 1):
            for z in range(1, s - 1):
                if t.get(x, 0, z) != u.norm(LIGHT) and t.rng.random() < 0.35:
                    t.set(x, 0, z, burnt)
        for (x, y, z), b in list(t.blocks.items()):
            if y > 0 and b in (u.norm(DESK), u.norm('bookshelf'), u.norm(CRATE)) and t.rng.random() < 0.6:
                t.set(x, y, z, 'black_terracotta' if b != u.norm(DESK) else 'blackstone_slab[type=top,waterlogged=false]')
        t.scatter(1, 1, 1, s - 2, 4, s - 2, 'cobweb', s // 2, on_floor=False)

    def holes(t, s, count, r=2.0):
        """Holes through the slab (the floor below sees up through them), rubble round them; never at the core."""
        c = s // 2
        for _ in range(count):
            hx, hz = t.rng.randint(2, s - 3), t.rng.randint(2, s - 3)
            if abs(hx - c) <= 4 and abs(hz - c) <= 4:
                continue
            for x in range(1, s - 1):
                for z in range(1, s - 1):
                    d = ((x - hx) ** 2 + (z - hz) ** 2) ** 0.5
                    if d < r + t.rng.random() * 0.8:
                        t.set(x, 0, z, AIR)
                        for y in (1, 2):
                            if t.get(x, y, z) not in (None, AIR) and y <= 2:
                                t.set(x, y, z, AIR)
                    elif d < r + 1.6 and t.get(x, 1, z) == AIR and t.rng.random() < 0.4:
                        t.set(x, 1, z, rubble)

    def shell(t, s):
        """Two sides of the facade gone but for their columns, rubble."""
        for x in range(1, s - 1):
            for y in range(1, 4):
                if x % 3:
                    t.set(x, y, 0, AIR)
        for z in range(1, s - 1):
            for y in range(1, 4):
                if z % 3:
                    t.set(s - 1, y, z, AIR)
        scatter_rubble(t, s, s)

    def car(t, x, z, axis, colour, wreck=False):
        """A car: a body of concrete 2 x 4, its cabin of glass, wheels of black concrete at its ends."""
        cells = [(x + i, z + j) for i in range(2) for j in range(4)] if axis == 'z' else [(x + j, z + i) for i in range(2) for j in range(4)]
        for n, (cx, cz) in enumerate(cells):
            end = (n % 4 in (0, 3)) if axis == 'z' else (n % 4 in (0, 3))
            t.set(cx, 1, cz, BLACK if end and not wreck else colour)
            if not end:
                t.set(cx, 2, cz, 'gray_stained_glass' if not wreck else (AIR if (cx + cz) % 2 else 'gray_stained_glass'))
        if wreck:
            for cx, cz in cells[:2]:
                t.set(cx, 1, cz, 'blackstone')

    def lamp_post(t, x, z, h=4):
        t.fill(x, 1, z, x, h, z, BARS)
        t.set(x, h + 1, z, 'smooth_stone_slab[type=top,waterlogged=false]')
        t.set(x, h, z, LIGHT)

    def doors(t, sides, pool=STREETS, frame=STONE):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=frame)

    def building(path, sx, sz, h, floor=None, wall=None, roof=SMOOTH):
        """A closed ground floor over the whole template: floor at y 0, room up to h, roof at h + 1, lightning lanterns in the roof."""
        t = pack.template(P + path, sx, h + 2, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall or CONCRETE, floor or walk, roof)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, GREY)
        for x in range(3, sx - 2, 4):
            for z in range(3, sz - 2, 4):
                t.set(x, h + 1, z, LIGHT)
        return t

    def lobby(path, s, h, style, pool, sides):
        """The ground floor of a tower: the core and its ladder, a desk, a lobby lit from its roof; open at the top
        (the first floor's slab is its ceiling): a connector up there, in the middle."""
        c = s // 2
        t = pack.template(P + path, s, h + 1, s)
        t.fill(0, 0, 0, s - 1, 0, s - 1, FLOOR_TILE)
        t.fill(0, 1, 0, s - 1, h, s - 1, AIR)
        core(t, s, h)
        facade(t, s, style, 0.25, 1, h)
        t.fill(c - 3, 1, 2, c + 3, 1, 2, DESK)
        t.set(c, 2, 2, 'gray_stained_glass_pane')
        for x, z in ((2, 2), (s - 3, 2), (2, s - 3), (s - 3, s - 3)):
            t.set(x, 1, z, 'potted_fern')
            t.set(x, h, z, LANTERN)
        for x in range(3, s - 3, 4):
            t.set(x, h, 1, 'end_rod[facing=down]')
        up(t, s, h, pool)
        doors(t, sides)
        return t

    # ------------------------------------------------------------------ the main tower: its lobby (start), floors, roofs

    S = 19
    t = lobby('complex/lobby', S, 6, 'glass', MAIN_FLOORS, ('east', 'west', 'south'))
    t.door('north', S // 2, PLAZA, frame=STONE)
    for z in (6, 12):
        for x in (3, 15):
            t.fill(x, 1, z, x, 6, z, STONE)
    t.fill(4, 1, 15, 6, 1, 16, SLAB)
    t.fill(12, 1, 15, 14, 1, 16, SLAB)
    t.scatter(1, 1, 1, 17, 1, 17, rubble, 6)

    def main_floor(name, kind):
        t = floor_piece('complex/main_' + name, S, 'glass', broken=0.15 if kind != 'burnt' else 0.6)
        if kind == 'offices':
            offices(t, S)
        elif kind == 'archives':
            archives(t, S)
        elif kind == 'fallen':
            offices(t, S)
            holes(t, S, 3, 2.2)
            scatter_rubble(t, S, 10)
        elif kind == 'burnt':
            offices(t, S)
            burn(t, S)
        elif kind == 'wild':
            offices(t, S)
            overgrow(t, S, 0.45)
        elif kind == 'shell':
            shell(t, S)
            overgrow(t, S, 0.15)
        up(t, S, 4, MAIN_FLOORS)
        return t

    for name, kind in (('offices', 'offices'), ('offices_2', 'offices'), ('archives', 'archives'), ('fallen', 'fallen'), ('burnt', 'burnt'),
                       ('wild', 'wild'), ('shell', 'shell')):
        main_floor(name, kind)

    # Roofs of the main tower: a helipad, a garden, a water tank, a spire.
    t = roof_piece('complex/main_helipad', S)
    for x in range(3, 16):
        for z in range(3, 16):
            if abs(x - 9) > 2 or abs(z - 9) > 2:
                t.set(x, 0, z, 'yellow_concrete' if x in (3, 15) or z in (3, 15) else GREY)
    for z in range(5, 14):
        if abs(z - 9) > 2:
            t.set(5, 0, z, WHITE)
            t.set(13, 0, z, WHITE)
    t.fill(5, 0, 9, 6, 0, 9, WHITE)
    t.fill(12, 0, 9, 13, 0, 9, WHITE)
    for x, z in ((1, 1), (17, 1), (1, 17), (17, 17)):
        t.set(x, 1, z, LIGHT)
    t.fill(16, 1, 9, 16, 6, 9, BARS)
    t.set(16, 7, 9, 'end_rod[facing=up]')

    t = roof_piece('complex/main_garden', S)
    for x in range(1, 18):
        for z in range(1, 18):
            if abs(x - 9) > 3 or abs(z - 9) > 3:
                t.set(x, 0, z, wild)
                if t.rng.random() < 0.25:
                    t.set(x, 1, z, t.rng.choice(weeds + ('dandelion', 'poppy')))
    for x, z in ((4, 4), (14, 5), (5, 14), (13, 13)):
        t.fill(x, 1, z, x, 3, z, 'oak_log[axis=y]')
        t.disc(x, 4, z, 2.5, LEAVES)
        t.disc(x, 5, z, 1.5, LEAVES)
    t.fill(2, 1, 9, 2, 1, 11, SLAB)

    t = roof_piece('complex/main_tank', S)
    for x, z in ((3, 3), (6, 3), (3, 6), (6, 6)):
        t.fill(x, 1, z, x, 4, z, 'spruce_fence')
    t.fill(3, 5, 3, 6, 5, 6, 'spruce_planks')
    t.fill(3, 6, 3, 6, 9, 6, 'spruce_planks')
    t.fill(4, 6, 4, 5, 9, 5, 'water[level=0]')
    t.fill(3, 10, 3, 6, 10, 6, 'spruce_slab[type=bottom,waterlogged=false]')
    for x, z in ((13, 3), (15, 3), (13, 15)):
        t.fill(x, 1, z, x + 1, 1, z + 1, BARREL)
        t.set(x, 2, z, 'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')

    t = roof_piece('complex/main_spire', S)
    t.fill(7, 5, 7, 11, 5, 11, SMOOTH)
    for y in range(6, 11):
        t.walls(8, y, 8, 10, y, 10, BARS if y % 2 else CONCRETE)
    t.fill(9, 6, 9, 9, 11, 9, 'iron_bars')
    t.set(9, 11, 9, LIGHT)
    for x, z in ((1, 1), (17, 17)):
        t.set(x, 2, z, LIGHT)

    pack.pool(MAIN_FLOORS, [(P + 'complex/main_offices', 4), (P + 'complex/main_offices_2', 3), (P + 'complex/main_archives', 2),
                            (P + 'complex/main_fallen', 2), (P + 'complex/main_burnt', 2), (P + 'complex/main_wild', 2), (P + 'complex/main_shell', 1)],
              fallback=MAIN_ROOFS)
    pack.pool(MAIN_ROOFS, [(P + 'complex/main_helipad', 2), (P + 'complex/main_garden', 2), (P + 'complex/main_tank', 1), (P + 'complex/main_spire', 2)])

    # ------------------------------------------------------------------ the other towers (15 x 15): glass offices, brick flats

    T = 15

    def tower_floor(prefix, style, name, kind, pool):
        t = floor_piece(f'complex/{prefix}_{name}', T, style, broken=0.2 if kind != 'burnt' else 0.6)
        work = flats if style == 'brick' else offices
        if kind == 'plain':
            work(t, T)
        elif kind == 'fallen':
            work(t, T)
            holes(t, T, 2, 1.8)
            scatter_rubble(t, T, 8)
        elif kind == 'burnt':
            work(t, T)
            burn(t, T)
        elif kind == 'wild':
            work(t, T)
            overgrow(t, T, 0.5)
        up(t, T, 4, pool)
        return t

    for prefix, style, floors, roofs in (('office', 'glass', OFFICE_FLOORS, OFFICE_ROOFS), ('flat', 'brick', FLAT_FLOORS, FLAT_ROOFS)):
        for name in ('plain', 'fallen', 'burnt', 'wild'):
            tower_floor(prefix, style, name, name, floors)
        t = roof_piece(f'complex/{prefix}_roof_garden', T)
        for x in range(1, 14):
            for z in range(1, 14):
                if abs(x - 7) > 3 or abs(z - 7) > 3:
                    t.set(x, 0, z, wild)
        for x, z in ((3, 3), (11, 11)):
            t.fill(x, 1, z, x, 2, z, 'oak_log[axis=y]')
            t.disc(x, 3, z, 1.5, AZALEA)
        t = roof_piece(f'complex/{prefix}_roof_tank', T)
        for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
            t.fill(x, 1, z, x, 3, z, 'spruce_fence')
        t.fill(2, 4, 2, 4, 7, 4, 'spruce_planks')
        t.set(3, 5, 3, 'water[level=0]')
        t.set(3, 6, 3, 'water[level=0]')
        t.fill(11, 1, 11, 12, 1, 12, BARREL)
        t = roof_piece(f'complex/{prefix}_roof_broken', T)
        holes(t, T, 2, 1.6)
        for x, z in ((0, 0), (3, 0), (6, 0), (14, 3), (14, 9), (0, 12)):
            t.fill(x, 1, z, x, t.rng.randint(2, 5), z, CONCRETE if style == 'glass' else BRICKS)
        scatter_rubble(t, T, 8)
        pack.pool(floors, [(P + f'complex/{prefix}_plain', 4), (P + f'complex/{prefix}_fallen', 2), (P + f'complex/{prefix}_burnt', 1),
                           (P + f'complex/{prefix}_wild', 2), (P + f'complex/{prefix}_roof_garden', 1), (P + f'complex/{prefix}_roof_tank', 1),
                           (P + f'complex/{prefix}_roof_broken', 1)], fallback=roofs)
        pack.pool(roofs, [(P + f'complex/{prefix}_roof_garden', 1), (P + f'complex/{prefix}_roof_tank', 1), (P + f'complex/{prefix}_roof_broken', 1)])

    t = lobby('complex/office_lobby', T, 5, 'glass', OFFICE_FLOORS, ('north', 'east'))
    t.scatter(1, 1, 1, 13, 1, 13, rubble, 4)
    t = lobby('complex/flat_lobby', T, 5, 'brick', FLAT_FLOORS, ('north', 'west'))
    t.fill(1, 1, 11, 3, 1, 13, 'g:crate')
    for x in (11, 12, 13):
        t.set(x, 1, 13, BARREL)

    # ------------------------------------------------------------------ the plaza before the main tower

    def plaza(path, kind):
        t = pack.template(P + path, 19, 9, 15)
        t.fill(0, 0, 0, 18, 0, 14, walk)
        t.fill(0, 1, 0, 18, 8, 14, AIR)
        t.fill(8, 0, 0, 10, 0, 14, SMOOTH)
        if kind == 'fountain':
            t.disc(9, 0, 8, 4.5, STONE)
            t.disc(9, 0, 8, 3.5, 'water[level=0]')
            t.disc(9, 1, 8, 4.5, SLAB, ring=True)
            t.fill(9, 0, 8, 9, 3, 8, STONE)
            t.set(9, 4, 8, LIGHT)
            for x in (2, 16):
                for z in (3, 11):
                    t.fill(x, 1, z, x + (1 if x < 9 else -1), 1, z, SLAB)
        else:
            for x in range(19):
                for z in range(2, 15):
                    if abs(x - 9) > 1 and t.rng.random() < 0.6:
                        t.set(x, 0, z, wild)
                        if t.rng.random() < 0.3:
                            t.set(x, 1, z, t.rng.choice(weeds))
            for x, z in ((4, 6), (14, 10), (3, 12)):
                t.fill(x, 1, z, x, 4, z, 'oak_log[axis=y]')
                t.disc(x, 5, z, 2.5, LEAVES)
                t.disc(x, 6, z, 1.5, LEAVES)
            car(t, 12, 3, 'x', 'red_concrete', wreck=True)
        for x, z in ((1, 1), (17, 1), (1, 13), (17, 13)):
            lamp_post(t, x, z)
        t.jigsaw(9, 1, 0, 'north', 'tube_end', 'door', 'minecraft:empty')
        return t

    plaza('complex/plaza_fountain', 'fountain')
    plaza('complex/plaza_wild', 'wild')

    # ------------------------------------------------------------------ streets (along z)

    def ends(t, x, y_out, length):
        t.jigsaw(x, 1, 0, 'north', 'tube_end', 'door', ROOMS)
        t.jigsaw(x, y_out, length - 1, 'south', 'tube_end', 'door', ROOMS)

    def street(path, length, ruined=False):
        """An open street: asphalt and its line between two sidewalks, lamp posts, weeds in the cracks."""
        t = pack.template(P + path, 7, 7, length)
        t.fill(0, 0, 0, 6, 0, length - 1, road)
        t.fill(0, 1, 0, 6, 6, length - 1, AIR)
        t.fill(0, 0, 0, 0, 0, length - 1, walk)
        t.fill(6, 0, 0, 6, 0, length - 1, walk)
        for z in range(1, length - 1, 3):
            t.set(3, 0, z, LINE)
        for z in range(length):
            if t.rng.random() < (0.35 if ruined else 0.12):
                t.set(t.rng.randint(1, 5), 0, z, t.rng.choice(('moss_block', 'coarse_dirt', 'gravel')))
        lamp_post(t, 0, length // 2)
        ends(t, 3, 1, length)
        return t

    street('complex/street', 11)
    t = street('complex/street_cars', 13)
    car(t, 1, 3, 'z', 'blue_concrete')
    car(t, 4, 7, 'z', 'white_concrete', wreck=True)

    # An arcade: a covered sidewalk, its columns, shop windows on one side.
    t = pack.template(P + 'complex/arcade', 7, 6, 13)
    t.fill(0, 0, 0, 6, 0, 12, walk)
    t.fill(0, 1, 0, 6, 5, 12, AIR)
    t.fill(0, 4, 0, 6, 4, 12, CONCRETE)
    for z in range(0, 13, 3):
        t.fill(1, 1, z, 1, 3, z, STONE)
    t.fill(5, 1, 0, 6, 3, 12, CONCRETE)
    for z in range(1, 12):
        if z % 3:
            t.fill(5, 1, z, 5, 3, z, PANE if t.rng.random() < 0.6 else AIR)
    t.fill(6, 1, 1, 6, 3, 11, AIR)
    t.fill(6, 0, 1, 6, 0, 11, FLOOR_TILE)
    for z in (2, 8):
        t.set(6, 1, z, CRATE)
        t.set(6, 1, z + 1, BARREL)
    for z in (4, 10):
        t.set(3, 3, z, LANTERN)
    t.fill(0, 5, 0, 6, 5, 12, SLAB)
    ends(t, 3, 1, 13)

    # A covered passage, tiled like the metro.
    t = pack.template(P + 'complex/passage', 7, 6, 11)
    t.fill(1, 0, 0, 5, 0, 10, SUBWAY)
    t.fill(1, 1, 0, 5, 4, 10, SUBWAY_PLAIN)
    t.fill(2, 1, 0, 4, 3, 10, AIR)
    t.fill(1, 1, 0, 1, 1, 10, WHITE)
    t.fill(5, 1, 0, 5, 1, 10, WHITE)
    for z in range(2, 10, 4):
        t.set(3, 4, z, LIGHT)
    t.set(2, 1, 5, SLAB)
    t.set(4, 1, 5, SLAB)
    for z in (0, 10):
        t.fill(1, 1, z, 1, 3, z, STONE)
        t.fill(5, 1, z, 5, 3, z, STONE)
        t.fill(1, 4, z, 5, 4, z, STONE)
    ends(t, 3, 1, 11)

    # Steps up a street: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'complex/steps', 7, 9, 11)
    t.fill(0, 0, 0, 6, 0, 10, walk)
    t.fill(0, 1, 0, 6, 8, 10, AIR)
    t.fill(0, 1, 7, 6, 4, 10, STONE)
    for i in range(4):
        z = 3 + i
        if i:
            t.fill(0, 1, z, 6, i, z, STONE)
        for x in range(1, 6):
            t.stairs(x, 1 + i, z, STEP, 'south')
        t.set(0, 1 + i, z, STONE)
        t.set(6, 1 + i, z, STONE)
    t.fill(1, 4, 7, 5, 4, 10, walk)
    for x in (0, 6):
        t.fill(x, 1, 0, x, 1, 2, RAIL)
        t.fill(x, 5, 7, x, 5, 10, RAIL)
    t.set(0, 5, 9, STANDING)
    ends(t, 3, 5, 11)

    # A street buried in rubble, a path cleared through it.
    t = street('complex/street_rubble', 11, ruined=True)
    for x in (0, 1, 5, 6):
        for z in range(11):
            h = t.rng.randint(0, 3 if x in (0, 6) else 2)
            for y in range(1, h + 1):
                t.set(x, y, z, rubble)
    t.set(0, 1, 5, AIR)
    t.fill(0, 2, 5, 0, 5, 5, AIR)

    for name, connector, target, block in (('complex/cap_door', 'tube_end', 'door', CONCRETE), ('complex/cap_street', 'door', 'tube_end', STONE)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, block)
        t.set(2, 2, 0, 'gray_stained_glass')
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=block)

    # ------------------------------------------------------------------ rooms at the street

    # A mall: shop fronts of glass on both sides, a dry fountain, benches, planters.
    t = building('complex/mall', 19, 15, 6, floor=FLOOR_TILE)
    for x0 in range(1, 15, 4):
        for z0, zf in ((1, 4), (10, 10)):
            t.fill(x0, 1, z0, x0 + 3, 4, z0 + 3, CONCRETE)
            t.fill(x0 + 1, 1, z0 + (0 if z0 == 1 else 1), x0 + 2, 3, z0 + (2 if z0 == 1 else 3), AIR)
            for x in range(x0, x0 + 4):
                t.set(x, 2, zf, PANE if t.rng.random() < 0.5 else AIR)
            t.fill(x0 + 1, 1, zf, x0 + 2, 2, zf, AIR)
            t.set(x0 + 1, 1, z0 + 1 if z0 == 1 else z0 + 2, CRATE)
    t.disc(9, 0, 7, 2.5, STONE)
    t.disc(9, 1, 7, 2.5, SLAB, ring=True)
    t.set(9, 1, 7, STONE)
    t.set(9, 2, 7, 'potted_dead_bush')
    for x in (3, 15):
        t.fill(x, 1, 7, x + (1 if x < 9 else -1), 1, 7, SLAB)
    t.scatter(1, 1, 5, 17, 1, 9, rubble, 4)
    for x, z in ((1, 6), (17, 8)):
        t.set(x, 1, z, 'potted_fern')
    doors(t, ('east', 'west'))

    # A car park: pillars, lines, wrecks.
    t = building('complex/car_park', 17, 15, 4, floor=ASPHALT, wall=GREY)
    for x in (4, 8, 12):
        for z in (4, 10):
            t.fill(x, 1, z, x, 4, z, CONCRETE)
    for x in range(2, 16, 3):
        t.fill(x, 0, 1, x, 0, 4, LINE)
        t.fill(x, 0, 10, x, 0, 13, LINE)
    car(t, 3, 1, 'z', 'red_concrete')
    car(t, 9, 10, 'z', 'yellow_concrete', wreck=True)
    car(t, 12, 1, 'z', 'white_concrete')
    for z in (2, 12):
        t.fill(0, 2, z, 0, 3, z + 1, AIR)
    t.scatter(1, 1, 5, 15, 1, 9, rubble, 4)
    doors(t, ('north',))

    # An atrium overgrown: a glass roof broken, a tree in the middle, vines, moss.
    t = building('complex/atrium', 15, 15, 8, floor=FLOOR_TILE, roof='glass')
    t.disc(7, 0, 7, 3.5, 'grass_block[snowy=false]')
    t.fill(7, 1, 7, 7, 5, 7, 'oak_log[axis=y]')
    t.disc(7, 6, 7, 3.5, LEAVES)
    t.disc(7, 7, 7, 2.5, LEAVES)
    t.disc(7, 8, 7, 1.5, LEAVES)
    for x in range(1, 14):
        for z in range(1, 14):
            if t.get(x, 9, z) == u.norm('glass') and t.rng.random() < 0.4:
                t.set(x, 9, z, AIR)
    overgrow(t, 15, 0.35)
    doors(t, ('north', 'south'))

    # A metro hall: a platform along the rails, a car of the metro, benches, tiles.
    t = building('complex/metro', 15, 13, 5, floor=SUBWAY, wall=SUBWAY_PLAIN)
    t.fill(1, 0, 8, 13, 0, 11, 'gravel')
    t.fill(1, 1, 10, 13, 1, 10, 'rail[shape=east_west,waterlogged=false]')
    t.fill(1, 0, 7, 13, 0, 7, 'yellow_concrete')
    t.fill(2, 1, 9, 9, 3, 11, 'white_concrete')
    t.fill(3, 2, 9, 8, 2, 9, 'gray_stained_glass')
    t.fill(5, 1, 9, 6, 2, 9, AIR)
    for x in (3, 7, 11):
        t.set(x, 1, 3, SLAB)
    t.fill(13, 1, 1, 13, 3, 1, 'yellow_concrete')
    doors(t, ('north', 'east'))

    # Crossroads: a small square, its traffic lights, a dead tree.
    t = pack.template(P + 'complex/crossroads', 11, 8, 11)
    t.fill(0, 0, 0, 10, 0, 10, road)
    t.fill(0, 1, 0, 10, 7, 10, AIR)
    for a in range(11):
        if a % 2 == 0:
            t.set(a, 0, 5, LINE)
            t.set(5, 0, a, LINE)
    for x, z in ((1, 1), (9, 1), (1, 9), (9, 9)):
        t.fill(x, 0, z, x + (1 if x < 5 else -1), 0, z + (1 if z < 5 else -1), walk)
        t.fill(x, 1, z, x, 4, z, BARS)
        t.set(x, 5, z, 'black_concrete')
        t.set(x, 4, z, 'redstone_lamp[lit=false]')
    t.set(5, 1, 5, 'coarse_dirt')
    t.fill(5, 0, 5, 5, 3, 5, 'stripped_oak_log[axis=y]')
    doors(t, ('north', 'south', 'east', 'west'), frame=None)

    pack.pool(STREETS, [(P + 'complex/street', 3), (P + 'complex/street_cars', 3), (P + 'complex/arcade', 3), (P + 'complex/passage', 2),
                        (P + 'complex/steps', 1), (P + 'complex/street_rubble', 2)], fallback=P + 'complex/door_caps')
    pack.pool(P + 'complex/door_caps', [(P + 'complex/cap_door', 1)])
    pack.pool(PLAZA, [(P + 'complex/plaza_fountain', 1), (P + 'complex/plaza_wild', 1)], fallback=P + 'complex/door_caps')
    pack.pool(ROOMS, [(P + 'complex/office_lobby', 3), (P + 'complex/flat_lobby', 3), (P + 'complex/mall', 2), (P + 'complex/car_park', 2),
                      (P + 'complex/atrium', 2), (P + 'complex/metro', 1), (P + 'complex/crossroads', 2)], fallback=P + 'complex/street_caps')
    pack.pool(P + 'complex/street_caps', [(P + 'complex/cap_street', 1)])
    pack.pool(P + 'complex/start', [(P + 'complex/lobby', 1)])
    city = [P + b for b in ('downtown', 'collapsed_ruins', 'residential')]
    # Twelve floors deep for the main tower (one jigsaw step per floor), the streets and the other towers within 80 blocks.
    pack.jigsaw(P + 'complex', P + 'complex/start', city, size=12, max_distance=80)

    # ------------------------------------------------------------------ the small ones

    # A filling station: a canopy on pillars over two pump islands, a small shop.
    def station(path, burnt_out):
        t = pack.template(P + path, 17, 8, 13)
        t.fill(0, 0, 0, 16, 0, 12, road)
        t.fill(0, 1, 0, 16, 7, 12, AIR)
        for x, z in ((2, 2), (10, 2), (2, 7), (10, 7)):
            t.fill(x, 1, z, x, 4, z, CONCRETE)
        t.fill(1, 5, 1, 11, 5, 8, WHITE)
        t.fill(1, 5, 1, 11, 5, 1, 'red_concrete')
        t.fill(1, 5, 8, 11, 5, 8, 'red_concrete')
        for x in (4, 8):
            t.set(x, 5, 4, LIGHT)
            t.set(x, 5, 6, LIGHT)
        for x in (5, 7):
            t.fill(x, 0, 3, x, 0, 6, SMOOTH)
            for z in (4, 5):
                t.set(x, 1, z, 'red_concrete')
                t.set(x, 2, z, 'black_concrete' if z == 4 else 'gray_stained_glass')
            t.set(x, 1, 3, 'chain[axis=y,waterlogged=false]')
        # The shop.
        t.fill(12, 0, 3, 16, 0, 11, FLOOR_TILE)
        t.fill(12, 1, 3, 16, 4, 11, WHITE)
        t.fill(13, 1, 4, 15, 3, 10, AIR)
        t.fill(12, 1, 5, 12, 3, 9, PANE)
        t.fill(12, 1, 7, 12, 2, 7, AIR)
        t.fill(12, 4, 3, 16, 4, 11, 'red_concrete')
        t.fill(15, 1, 4, 15, 2, 10, CRATE)
        t.set(13, 1, 4, DESK)
        t.set(14, 3, 7, LANTERN)
        car(t, 1, 10, 'x', 'blue_concrete', wreck=burnt_out)
        if burnt_out:
            t.fill(6, 5, 1, 11, 5, 8, AIR, chance=0.7)
            t.fill(10, 3, 7, 10, 4, 7, AIR)
            for (x, y, z), b in list(t.blocks.items()):
                if y == 0 and t.rng.random() < 0.25:
                    t.set(x, y, z, burnt)
            t.fill(12, 1, 5, 12, 3, 9, AIR)
            t.scatter(0, 1, 0, 16, 1, 12, rubble, 8)
            t.scatter(13, 1, 4, 15, 3, 10, 'cobweb', 3, on_floor=False)
        return t

    station('sites/station', False)
    station('sites/station_burnt', True)

    # A small building fallen in: four floors of bricks, a corner collapsed into a heap of rubble.
    def fallen(path, gutted):
        t = pack.template(P + path, 11, 20, 11)
        t.fill(0, 0, 0, 10, 0, 10, walk)
        t.fill(0, 1, 0, 10, 19, 10, AIR)
        for f in range(4):
            y = f * 4
            t.fill(1, y, 1, 9, y, 9, SMOOTH if f else FLOOR_TILE)
            for x in range(1, 10):
                for z in range(1, 10):
                    if x in (1, 9) or z in (1, 9):
                        t.fill(x, y + 1, z, x, y + 3, z, BRICKS)
                        if (x + z) % 2 and x not in (1, 9) or (x in (1, 9) and z % 2 and z not in (1, 9)):
                            t.fill(x, y + 2, z, x, y + 2, z, PANE if t.rng.random() < 0.5 else AIR)
            t.set(3, y + 1, 3, 'bookshelf' if f % 2 else CRATE)
            t.set(7, y + 1, 3, 'red_bed[facing=north,occupied=false,part=head]')
            t.set(7, y + 1, 4, 'red_bed[facing=north,occupied=false,part=foot]')
            t.set(2, y + 3, 5, LANTERN)
        t.fill(1, 16, 1, 9, 16, 9, SMOOTH)
        t.walls(1, 17, 1, 9, 17, 9, BRICKS)
        t.fill(5, 1, 8, 5, 16, 8, BRICKS)
        t.ladder(5, 1, 16, 7, 'north')
        t.fill(4, 1, 1, 6, 2, 1, AIR)
        # The collapse: everything within a sphere round a corner goes, a heap under it.
        cx, cz = (9, 9) if not gutted else (1, 9)
        for x in range(11):
            for y in range(1, 20):
                for z in range(11):
                    d = ((x - cx) ** 2 + ((y - 14) * 0.6) ** 2 + (z - cz) ** 2) ** 0.5
                    if d < 6.5 + t.rng.random():
                        t.set(x, y, z, AIR)
        for x in range(11):
            for z in range(11):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                h = round(4 - d * 0.7 + t.rng.random())
                for y in range(1, h + 1):
                    if t.get(x, y, z) in (None, AIR):
                        t.set(x, y, z, rubble)
        if gutted:
            for (x, y, z), b in list(t.blocks.items()):
                if b == u.norm(PANE):
                    t.set(x, y, z, AIR)
            overgrow(t, 11, 0.3)
        return t

    fallen('sites/fallen', False)
    fallen('sites/fallen_gutted', True)

    # A security post: a booth, a barrier, a fence of iron bars, a watchtower with its searchlight.
    def post(path, overrun):
        t = pack.template(P + path, 13, 11, 13)
        t.fill(0, 0, 0, 12, 0, 12, road)
        t.fill(0, 1, 0, 12, 10, 12, AIR)
        t.walls(0, 1, 0, 12, 2, 12, BARS)
        t.fill(4, 1, 0, 8, 2, 0, AIR)
        t.fill(4, 0, 0, 8, 0, 12, ASPHALT)
        # The booth.
        t.fill(1, 0, 1, 3, 0, 3, SMOOTH)
        t.fill(1, 1, 1, 3, 3, 3, 'gray_stained_glass')
        t.fill(2, 1, 2, 2, 2, 2, AIR)
        t.set(2, 1, 3, AIR)
        t.set(2, 2, 3, AIR)
        t.fill(1, 4, 1, 3, 4, 3, SLAB)
        t.set(2, 1, 1, DESK)
        t.set(2, 3, 2, LANTERN)
        # The barrier.
        t.set(4, 1, 2, 'yellow_concrete')
        t.fill(5, 1, 2, 8, 1, 2, 'oak_fence' if not overrun else AIR)
        # The watchtower.
        for x, z in ((9, 9), (11, 9), (9, 11), (11, 11)):
            t.fill(x, 1, z, x, 6, z, CONCRETE)
        t.fill(9, 7, 9, 11, 7, 11, SMOOTH)
        t.walls(9, 8, 9, 11, 8, 11, BARS)
        t.fill(10, 1, 10, 10, 7, 10, AIR)
        t.ladder(10, 1, 7, 10, 'north')
        t.fill(10, 1, 11, 10, 6, 11, CONCRETE)
        t.set(10, 8, 10, AIR)
        t.set(11, 9, 11, LIGHT)
        t.set(9, 9, 9, STANDING)
        t.fill(6, 1, 9, 7, 2, 10, BARREL)
        t.fill(1, 1, 9, 2, 1, 11, CRATE)
        if overrun:
            for x, z in ((0, 4), (0, 5), (12, 7), (9, 12), (12, 2)):
                t.fill(x, 1, z, x, 2, z, AIR)
            t.fill(1, 1, 1, 3, 3, 1, AIR)
            t.fill(9, 8, 9, 11, 9, 11, AIR, chance=0.6)
            t.set(11, 9, 11, AIR)
            car(t, 5, 6, 'z', 'green_concrete', wreck=True)
            t.scatter(0, 1, 0, 12, 1, 12, rubble, 6)
            t.scatter(0, 1, 0, 12, 1, 12, 'short_grass', 6)
        return t

    post('sites/post', False)
    post('sites/post_overrun', True)

    # A warehouse: a tall shed, its rolling door open, racks of crates, a forklift.
    def warehouse(path, caved):
        t = pack.template(P + path, 17, 10, 13)
        t.fill(0, 0, 0, 16, 0, 12, road)
        t.fill(0, 1, 0, 16, 9, 12, AIR)
        t.fill(1, 0, 1, 15, 0, 11, SMOOTH)
        t.fill(1, 1, 1, 15, 7, 11, GREY)
        t.fill(2, 1, 2, 14, 6, 10, AIR)
        t.fill(1, 7, 1, 15, 7, 11, CONCRETE)
        for x in range(3, 15, 4):
            t.fill(x, 7, 2, x, 7, 10, 'glass')
        t.fill(6, 1, 1, 10, 4, 1, AIR)
        t.fill(6, 5, 1, 10, 5, 1, 'yellow_concrete')
        for z in (3, 9):
            for x in range(3, 14):
                if x in (6, 7, 8, 9, 10) and z == 3:
                    continue
                t.set(x, 1, z, 'spruce_slab[type=top,waterlogged=false]' if x % 4 == 1 else CRATE)
                if t.rng.random() < 0.6:
                    t.set(x, 2, z, CRATE if t.rng.random() < 0.7 else BARREL)
        t.fill(12, 1, 6, 13, 1, 7, 'yellow_concrete')
        t.fill(11, 1, 6, 11, 3, 6, BARS)
        t.set(12, 2, 7, 'black_concrete')
        for x in (4, 12):
            t.set(x, 6, 6, LANTERN)
        if caved:
            for x in range(1, 16):
                for z in range(1, 12):
                    if ((x - 11) ** 2 + (z - 7) ** 2) ** 0.5 < 4 + t.rng.random():
                        t.set(x, 7, z, AIR)
                        if t.get(x, 1, z) == AIR and t.rng.random() < 0.5:
                            t.set(x, 1, z, rubble)
            t.scatter(2, 1, 2, 14, 1, 10, rubble, 6)
            t.scatter(2, 1, 2, 14, 1, 10, 'short_grass', 4)
            t.fill(6, 1, 1, 10, 4, 1, AIR)
        return t

    warehouse('sites/warehouse', False)
    warehouse('sites/warehouse_caved', True)

    everywhere = [P + b for b in ('urban_park', 'industrial_zone', 'collapsed_ruins', 'residential', 'downtown')]
    for name, variants, biomes in (
            ('station', ('station', 'station_burnt'), everywhere),
            ('fallen', ('fallen', 'fallen_gutted'), [P + b for b in ('downtown', 'collapsed_ruins', 'residential')]),
            ('post', ('post', 'post_overrun'), [P + b for b in ('industrial_zone', 'downtown', 'urban_park')]),
            ('warehouse', ('warehouse', 'warehouse_caved'), [P + b for b in ('industrial_zone', 'collapsed_ruins', 'residential')])):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32)

    # The complex: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from it.
    pack.structure_set(P + 'landmark', [(P + 'complex', 1)], spacing=36, separation=14, salt=1129468203)
    pack.structure_set(P + 'sites', [(P + 'station', 3), (P + 'fallen', 3), (P + 'post', 2), (P + 'warehouse', 3)],
                       spacing=11, separation=5, salt=478123391, exclusion=(P + 'landmark', 8))
