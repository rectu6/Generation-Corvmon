"""
Ultra Jungle (Buzzwole, Pheromosa): its structures, for tools/ultra_structures.py.

The great one: the colossal plant temple, a jigsaw of buildings joined by causeways and galleries
(a stepped pyramid of four tiers, a grand stair up its north face, a hall under stepped vaults round
a mother tree that grows up through the shrine on top; courtyards with a pool, small shrines, a hall
of roots, a hall of glyphs, garden terraces, a ruined tower, plazas, a fallen hall; gateways open on
obelisks, steles and stone heads).
The small ones: an explorers' camp, an observatory up its own tree, overgrown ruins, a research
cabin, each in two variants.

Moss-green temple bricks and blocks (Generations), mossy and chiseled stone bricks, the Ultra jungle
wood, leaves that never decay, vines and roots; shroomlights, lanterns, glow berries and crystal
lights (the shaders ignore the ambient light). Same connectors as the Ultra Crater: a room's door is
named `door` and asks the path pool for a `tube_end`; a path's end is named `tube_end` and asks the
room pool for a `door`. Doors 3 wide and 3 high, their connector at the bottom middle, floors at y 0.
"""

WORLD = 'ultra_jungle'
P = WORLD + '/'

AIR = 'minecraft:air'
BRICK = 'g:temple_brick'
BLOCK = 'g:temple_block'
CARVED = 'chiseled_stone_bricks'
MOSSY = 'mossy_stone_bricks'
LOG = 'g:ultra_jungle_log[axis=y]'
WOOD = 'g:ultra_jungle_wood[axis=y]'
PLANKS = 'g:ultra_jungle_planks'
FENCE = 'g:ultra_jungle_fence'
SHELVES = 'g:ultra_jungle_bookshelf'
STEP = 'g:temple_brick_stairs'
WOOD_STEP = 'g:ultra_jungle_stairs'
SLAB = 'g:temple_brick_slab[type=bottom,waterlogged=false]'
PLANK_SLAB = 'g:ultra_jungle_slab[type=bottom,waterlogged=false]'
RAIL = 'g:temple_brick_wall'
LEAVES = 'jungle_leaves[distance=1,persistent=true,waterlogged=false]'
LANTERN = 'lantern[hanging=true,waterlogged=false]'
POST_LANTERN = 'lantern[hanging=false,waterlogged=false]'
GLOW = 'g:crystal_light'
SHROOM = 'shroomlight'
WATER = 'water[level=0]'
ROOTS = 'hanging_roots[waterlogged=false]'
CRATE = 'g:crate'

PATHS = P + 'temple/paths'
ROOMS = P + 'temple/rooms'
EXTERIOR = P + 'temple/exterior'
SIDES = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def build(u, pack):
    old = u.Mix((BRICK, 6), (MOSSY, 3), (BLOCK, 2), ('cracked_stone_bricks', 1))
    paving = u.Mix((BLOCK, 6), (MOSSY, 2), ('moss_block', 1), ('cracked_stone_bricks', 1))
    rubble = u.Mix(('mossy_cobblestone', 4), (MOSSY, 2), ('cobblestone', 1), ('moss_block', 2), ('gravel', 1))
    low = u.Mix(('fern', 3), ('short_grass', 5), ('moss_carpet', 4), (AIR, 5))
    bushy = u.Mix(('fern', 3), ('short_grass', 3), ('azalea', 1), ('flowering_azalea', 1), ('moss_carpet', 2), (AIR, 3))
    soil = u.Mix(('moss_block', 3), ('rooted_dirt', 2), ('podzol', 2), ('grass_block', 2))
    clearing = u.Mix(('coarse_dirt', 3), ('podzol', 3), ('rooted_dirt', 1), ('moss_block', 2), ('grass_block', 2))

    # ------------------------------------------------------------------ helpers

    def full(t, x, y, z):
        b = t.get(x, y, z) if t.inside(x, y, z) else None
        return b is not None and b != AIR and u.is_full(b)

    def vine(side):
        return 'vine[' + ','.join(f'{k}={"true" if k == side else "false"}' for k in ('east', 'north', 'south', 'up', 'west')) + ']'

    def vines(t, x0, y0, z0, x1, y1, z1, chance=0.3, length=5):
        """Vines down the faces of full blocks: in each column, from the highest free cell against one, a few blocks long."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for side, (dx, dz) in SIDES.items():
                    if t.rng.random() >= chance:
                        continue
                    for y in range(y1, y0 - 1, -1):
                        if t.get(x, y, z) in (None, AIR) and full(t, x + dx, y, z + dz):
                            for yy in range(y, max(y0, y - t.rng.randint(1, length) + 1) - 1, -1):
                                if t.get(x, yy, z) not in (None, AIR) or not full(t, x + dx, yy, z + dz):
                                    break
                                t.set(x, yy, z, vine(side))
                            break

    def hang(t, x0, y0, z0, x1, y1, z1, block, count):
        """`count` blocks hung under the ceilings of the box (cells of air under a full block)."""
        cells = [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)
                 if t.get(x, y, z) == AIR and full(t, x, y + 1, z)]
        for x, y, z in t.rng.sample(cells, min(count, len(cells))):
            t.set(x, y, z, block)

    def berries(t, x0, y0, z0, x1, y1, z1, count):
        """Glow berries hanging from the ceilings: a light that the shaders keep."""
        cells = [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)
                 if t.get(x, y, z) == AIR and full(t, x, y + 1, z)]
        for x, y, z in t.rng.sample(cells, min(count, len(cells))):
            n = t.rng.randint(1, 3)
            for i in range(n):
                if y - i < y0 or t.get(x, y - i, z) != AIR:
                    break
                last = i == n - 1 or y - i - 1 < y0 or t.get(x, y - i - 1, z) != AIR
                t.set(x, y - i, z, 'cave_vines[age=25,berries=true]' if last else 'cave_vines_plant[berries=true]')
                if last:
                    break

    def bush(t, cx, cy, cz, r, free=(None,), flat=1.6):
        """A clump of leaves (only in the cells left to the terrain, unless told otherwise)."""
        n = int(r) + 1
        for dx in range(-n, n + 1):
            for dy in range(-n, n + 1):
                for dz in range(-n, n + 1):
                    x, y, z = cx + dx, cy + dy, cz + dz
                    if y < 1 or not t.inside(x, y, z) or t.get(x, y, z) not in free:
                        continue
                    if (dx * dx + dy * dy * flat + dz * dz) ** 0.5 + t.rng.random() * 0.8 <= r:
                        t.set(x, y, z, LEAVES)

    def tree(t, x0, z0, w, y0, top, crown):
        """A trunk w x w from y0 to top, roots at its foot, four branches and a crown round its top."""
        t.fill(x0, y0, z0, x0 + w - 1, top, z0 + w - 1, LOG)
        cx, cz = x0 + w // 2, z0 + w // 2
        for side, (dx, dz) in SIDES.items():
            ax = x0 + w if dx > 0 else x0 - 1 if dx < 0 else cx
            az = z0 + w if dz > 0 else z0 - 1 if dz < 0 else cz
            for i in range(t.rng.randint(1, 3)):
                for y in ((y0, y0 + 1) if i == 0 else (y0,)):
                    x, z = ax + dx * i, az + dz * i
                    if t.inside(x, y, z) and t.get(x, y, z) in (None, AIR):
                        t.set(x, y, z, WOOD)
            by = top - 1 - t.rng.randint(0, 2)
            n = max(1, int(crown) - 3)
            for i in range(n):
                x, z = ax + dx * i, az + dz * i
                if t.inside(x, by, z) and t.get(x, by, z) in (None, AIR):
                    t.set(x, by, z, f'g:ultra_jungle_log[axis={"x" if dx else "z"}]')
            bush(t, ax + dx * (n - 1), by + 1, az + dz * (n - 1), 2.5, free=(None, AIR))
        bush(t, cx, top + 1, cz, crown, free=(None, AIR), flat=2.2)

    def crumble(t, cx, cy, cz, r):
        """A part of a building fallen: what it has within r of the point goes (the terrain is left alone)."""
        sx, sy, sz = t.size
        for x in range(sx):
            for y in range(1, sy):
                for z in range(sz):
                    if (x, y, z) in t.nbt or t.get(x, y, z) is None:
                        continue
                    if ((x - cx) ** 2 + (y - cy) ** 2 * 1.4 + (z - cz) ** 2) ** 0.5 + t.rng.random() * 1.2 <= r:
                        t.set(x, y, z, AIR)

    def beam(t, a, b, block=WOOD):
        """A straight root from a to b."""
        n = max(abs(b[i] - a[i]) for i in range(3)) or 1
        for s in range(n + 1):
            p = tuple(round(a[i] + (b[i] - a[i]) * s / n) for i in range(3))
            if t.inside(*p):
                t.set(*p, block)

    def doors(t, sides, pool=PATHS):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=CARVED)

    def hall(path, sx, sz, h, floor=None, wall=None, extra=0):
        """A closed hall over the whole template: floor at y 0, room up to h, roof at h + 1, carved corners."""
        t = pack.template(P + path, sx, h + 2 + extra, sz)
        t.room(0, 0, sx - 1, sz - 1, h, wall or old, floor or paving, old)
        for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            t.fill(x, 0, z, x, h + 1, z, CARVED)
        return t

    # ------------------------------------------------------------------ the pyramid (start)

    t = pack.template(P + 'temple/core', 33, 32, 37)

    def inset(x, z):
        return min(x, z - 4, 32 - x, 36 - z)

    def tier_top(i):
        return 4 * (min(i, 12) // 4 + 1)

    def ceiling(i):
        """The hall's vault over a cell of the given inset: three steps up to the middle (0: no hall)."""
        return 0 if i < 5 else 6 if i < 8 else 9 if i < 11 else 12

    for x in range(33):
        for z in range(4, 37):
            i = inset(x, z)
            h = tier_top(i)
            t.set(x, 0, z, paving)
            t.fill(x, 1, z, x, h, z, old)
            if i % 4 == 0 and i < 12:
                if (x + z) % 2 == 0:
                    t.set(x, h - 1, z, CARVED)            # a frieze under each tier's rim
            elif i < 12 and t.rng.random() < 0.3:
                t.set(x, h, z, 'moss_block')               # moss on the terraces
            v = ceiling(i)
            if v:
                t.fill(x, 1, z, x, v - 1, z, AIR)
                t.set(x, 0, z, CARVED if (x + z) % 4 == 0 else BLOCK)
    # Passages from the three doors to the hall.
    for x0, z0, x1, z1 in ((28, 19, 32, 21), (0, 19, 4, 21), (15, 32, 17, 36)):
        t.fill(x0, 1, z0, x1, 3, z1, AIR)
    for x, z in ((30, 20), (2, 20), (16, 34)):
        t.set(x, 3, z, LANTERN)
    # The hall: a ring of water round the mother tree, pillars at the vault's steps, altars, lights.
    t.disc(16, 0, 20, 3.5, WATER, ring=True)
    for px, pz in ((8, 12), (24, 12), (8, 28), (24, 28)):
        t.fill(px, 1, pz, px, 8, pz, CARVED)
        t.set(px, 4, pz, GLOW)
    for px, pz in ((11, 15), (21, 15), (11, 25), (21, 25)):
        t.fill(px, 1, pz, px, 11, pz, CARVED)
        t.set(px, 7, pz, GLOW)
    for x, z in ((16, 15), (16, 25), (11, 20), (21, 20)):
        t.set(x, 1, z, CARVED)
        t.set(x, 2, z, GLOW)
    for x, z in ((16, 10), (16, 30), (6, 20), (26, 20)):
        t.set(x, 5, z, LANTERN)
    for x, z in ((13, 17), (19, 17), (13, 23), (19, 23), (16, 16), (16, 24), (12, 20), (20, 20)):
        t.set(x, 12, z, SHROOM)
    # The top: a shrine of four pillars and a lintel round the tree, an altar.
    for px, pz in ((12, 16), (20, 16), (12, 24), (20, 24)):
        t.fill(px, 17, pz, px, 21, pz, CARVED)
    t.walls(12, 22, 16, 20, 22, 24, BRICK)
    for x, z in ((16, 16), (16, 24), (12, 20), (20, 20)):
        t.set(x, 21, z, LANTERN)
    t.set(16, 17, 23, CARVED)
    t.set(16, 18, 23, GLOW)
    # The grand stair up the north face, built out over it, with balustrades.
    t.fill(13, 0, 0, 19, 0, 3, paving)
    for s in range(16):
        y = s + 1
        for x in range(14, 19):
            if y > 1:
                t.fill(x, 1, s, x, y - 1, s, old)
            t.stairs(x, y, s, STEP, 'south')
            t.fill(x, y + 1, s, x, y + 3, s, AIR)
        for x in (13, 19):
            t.fill(x, 1, s, x, y, s, old)
            t.set(x, y + 1, s, RAIL)
            if s % 4 == 1:
                t.set(x, y + 2, s, POST_LANTERN)
    # The mother tree, up through the hall and the shrine.
    tree(t, 15, 19, 3, 1, 26, 6)
    # Overgrowth: roots and berries under the vaults, vines, plants, bushes on the terraces.
    hang(t, 5, 1, 9, 27, 11, 31, ROOTS, 40)
    berries(t, 5, 1, 9, 27, 11, 31, 10)
    vines(t, 5, 1, 9, 27, 11, 31, 0.3, 5)
    t.scatter(5, 1, 9, 27, 1, 31, low, 60)
    for _ in range(30):
        x, z = t.rng.randint(0, 32), t.rng.randint(4, 36)
        i = inset(x, z)
        if i >= 12 or i % 4 == 0 or (12 <= x <= 20 and z <= 17):
            continue
        bush(t, x, tier_top(i) + 1, z, t.rng.choice((1.2, 1.8, 2.3)))
    vines(t, 0, 1, 0, 32, 16, 36, 0.12, 4)
    t.scatter(0, 5, 4, 32, 13, 36, low, 70)
    t.scatter(12, 17, 16, 20, 17, 24, low, 10)
    for side, along in (('east', 20), ('west', 20), ('south', 16)):
        t.door(side, along, PATHS, frame=CARVED)

    # ------------------------------------------------------------------ paths (along z, 5 wide in a template 7 wide)

    def ends(t, y_out, length):
        t.jigsaw(3, 1, 0, 'north', 'tube_end', 'door', ROOMS)
        t.jigsaw(3, y_out, length - 1, 'south', 'tube_end', 'door', ROOMS)

    def causeway(path, length):
        """A paved way in the open, low parapets, posts with lanterns."""
        t = pack.template(P + path, 7, 6, length)
        t.fill(1, 0, 0, 5, 0, length - 1, paving)
        t.fill(2, 1, 0, 4, 4, length - 1, AIR)
        for z in range(length):
            post = z % 4 == 2
            for x in (1, 5):
                t.set(x, 1, z, CARVED if post or z in (0, length - 1) else RAIL)
                if post:
                    t.set(x, 2, z, RAIL)
                    t.set(x, 3, z, POST_LANTERN)
        t.scatter(2, 1, 1, 4, 1, length - 2, low, length // 2)
        for z in range(1, length - 1, 3):
            if t.rng.random() < 0.6:
                bush(t, t.rng.choice((0, 6)), 1, z, 1.4)
        vines(t, 0, 1, 0, 6, 3, length - 1, 0.2, 2)
        ends(t, 1, length)
        return t

    causeway('temple/causeway', 9)
    causeway('temple/causeway_long', 15)

    # A causeway half fallen: parapets gone, rubble, a column lying across one side.
    t = causeway('temple/causeway_broken', 11)
    for z in range(3, 8):
        for x in (1, 5):
            if t.rng.random() < 0.7:
                t.fill(x, 1, z, x, 3, z, AIR)
        for x in range(1, 6):
            if t.rng.random() < 0.4:
                t.set(x, 0, z, rubble)
    t.scatter(1, 1, 3, 5, 1, 8, rubble, 4)
    t.fill(4, 1, 5, 6, 1, 5, CARVED)

    # A covered gallery: a stepped vault, lanterns, openings on the leaves.
    t = pack.template(P + 'temple/gallery', 7, 7, 11)
    t.fill(1, 0, 0, 5, 0, 10, paving)
    for x in (1, 5):
        t.fill(x, 1, 0, x, 3, 10, old)
    t.fill(2, 1, 0, 4, 3, 10, AIR)
    t.fill(1, 4, 0, 5, 4, 10, old)
    t.fill(3, 4, 0, 3, 4, 10, AIR)
    t.fill(2, 5, 0, 4, 5, 10, old)
    for z in (0, 10):
        t.fill(1, 1, z, 1, 3, z, CARVED)
        t.fill(5, 1, z, 5, 3, z, CARVED)
        t.fill(1, 4, z, 5, 4, z, CARVED)
    for z in (2, 6):
        t.set(3, 4, z, LANTERN)
    for z in (4, 8):
        t.set(1, 2, z, AIR)
        t.set(5, 2, z, AIR)
        bush(t, 0, 2, z, 1.3)
        bush(t, 6, 2, z, 1.3)
    for z in range(1, 10, 3):
        bush(t, 3, 6, z, 1.5)
    hang(t, 2, 1, 1, 4, 4, 9, ROOTS, 4)
    vines(t, 0, 1, 1, 6, 5, 9, 0.3, 3)
    t.scatter(2, 1, 1, 4, 1, 9, low, 5)
    ends(t, 1, 11)

    # A stair in the open: in at the bottom (north), out 4 higher (south).
    t = pack.template(P + 'temple/stairs', 7, 10, 11)
    t.fill(1, 0, 0, 5, 0, 10, paving)
    t.fill(2, 1, 0, 4, 8, 10, AIR)
    for i in range(4):
        z = 3 + i
        for x in range(2, 5):
            if i:
                t.fill(x, 1, z, x, i, z, old)
            t.stairs(x, 1 + i, z, STEP, 'south')
    t.fill(2, 1, 7, 4, 3, 10, old)
    t.fill(2, 4, 7, 4, 4, 10, paving)
    for z in range(11):
        level = 0 if z < 3 else min(z - 2, 4)
        for x in (1, 5):
            if level:
                t.fill(x, 1, z, x, level, z, old)
            t.set(x, level + 1, z, CARVED if z in (0, 10) else RAIL)
    for x in (1, 5):
        t.set(x, 6, 8, POST_LANTERN)
        t.set(x, 5, 8, CARVED)
    bush(t, 0, 3, 8, 1.6)
    bush(t, 6, 2, 3, 1.6)
    vines(t, 0, 1, 0, 6, 6, 10, 0.2, 3)
    ends(t, 5, 11)

    # A gateway to the outside: two carved posts, a lintel with a glowing keystone; what lies beyond is drawn from `exterior`.
    t = pack.template(P + 'temple/gate', 7, 7, 5)
    t.fill(1, 0, 0, 5, 0, 4, paving)
    t.fill(2, 1, 0, 4, 4, 4, AIR)
    for x in (1, 5):
        t.fill(x, 1, 1, x, 4, 3, CARVED)
    t.fill(1, 5, 1, 5, 5, 3, old)
    t.set(3, 5, 2, GLOW)
    t.fill(0, 1, 2, 0, 2, 2, RAIL)
    t.fill(6, 1, 2, 6, 2, 2, RAIL)
    vines(t, 0, 1, 0, 6, 5, 4, 0.35, 3)
    bush(t, 3, 6, 2, 1.4)
    t.jigsaw(3, 1, 0, 'north', 'tube_end', 'door', ROOMS)
    t.jigsaw(3, 0, 4, 'south', 'exit', 'pad', EXTERIOR, final=BLOCK)

    # Closed ends: a doorway walled up, a path ending on rubble.
    for name, connector, target, block in (('temple/cap_door', 'tube_end', 'door', old), ('temple/cap_path', 'door', 'tube_end', rubble)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 4, 0, block)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=MOSSY)

    # ------------------------------------------------------------------ rooms

    # A courtyard: low walls, a pool round a glowing column, a tree in each corner.
    t = pack.template(P + 'temple/courtyard', 17, 10, 17)
    t.fill(0, 0, 0, 16, 0, 16, paving)
    t.fill(1, 1, 1, 15, 6, 15, AIR)
    t.walls(0, 1, 0, 16, 2, 16, old)
    t.walls(0, 3, 0, 16, 3, 16, SLAB)
    for x, z in ((0, 0), (16, 0), (0, 16), (16, 16)):
        t.fill(x, 1, z, x, 4, z, CARVED)
        t.set(x, 5, z, POST_LANTERN)
    t.disc(8, 0, 8, 4.5, CARVED)
    t.disc(8, 0, 8, 3.5, WATER)
    t.fill(8, 0, 8, 8, 2, 8, CARVED)
    t.set(8, 3, 8, GLOW)
    for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)):
        tree(t, x, z, 1, 1, 5, 2.5)
    t.scatter(1, 1, 1, 15, 1, 15, low, 30)
    t.scatter(1, 1, 1, 15, 1, 15, bushy, 6)
    vines(t, 0, 1, 0, 16, 4, 16, 0.2, 2)
    doors(t, ('north', 'south', 'east'))

    # A small shrine: a pyramid of three tiers, a chamber with an altar, lanterns on top.
    t = pack.template(P + 'temple/shrine', 13, 12, 13)
    for x in range(13):
        for z in range(13):
            i = min(x, z, 12 - x, 12 - z)
            h = 3 if i < 2 else 6 if i < 4 else 8
            t.set(x, 0, z, paving)
            t.fill(x, 1, z, x, h, z, old)
            if i in (0, 2) and (x + z) % 2 == 0:
                t.set(x, h - 1, z, CARVED)
    t.fill(2, 1, 2, 10, 4, 10, AIR)
    t.fill(5, 1, 0, 7, 3, 1, AIR)
    t.fill(5, 1, 11, 7, 3, 12, AIR)
    t.fill(2, 0, 2, 10, 0, 10, BLOCK)
    t.set(6, 1, 6, CARVED)
    t.set(6, 2, 6, GLOW)
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        t.set(x, 4, z, LANTERN)
    for x, z in ((4, 4), (8, 4), (4, 8), (8, 8)):
        t.set(x, 9, z, RAIL)
        t.set(x, 10, z, POST_LANTERN)
    t.set(6, 9, 6, CARVED)
    t.set(6, 10, 6, GLOW)
    for _ in range(8):
        x, z = t.rng.randint(0, 12), t.rng.randint(0, 12)
        i = min(x, z, 12 - x, 12 - z)
        if i in (0, 1, 2, 3):
            bush(t, x, (3 if i < 2 else 6) + 1, z, 1.3)
    hang(t, 2, 1, 2, 10, 4, 10, ROOTS, 8)
    vines(t, 0, 1, 0, 12, 8, 12, 0.2, 3)
    t.scatter(2, 1, 2, 10, 1, 10, low, 10)
    doors(t, ('north', 'south'))

    # The hall of roots: roots of the mother tree come through the vault, glow berries, shroomlights in the walls.
    t = hall('temple/roots', 13, 15, 6, floor=soil)
    for a, b in (((1, 6, 2), (4, 1, 5)), ((11, 6, 3), (8, 1, 6)), ((1, 6, 9), (3, 1, 12)), ((11, 6, 10), (9, 1, 13)),
                 ((2, 6, 7), (1, 1, 7)), ((10, 6, 7), (11, 1, 8)), ((4, 6, 1), (2, 2, 3)), ((8, 6, 13), (10, 2, 12))):
        beam(t, a, b)
    for z in (3, 7, 11):
        t.set(0, 3, z, SHROOM)
        t.set(12, 3, z, SHROOM)
    t.fill(5, 0, 0, 7, 0, 14, paving)
    hang(t, 1, 1, 1, 11, 6, 13, ROOTS, 22)
    berries(t, 1, 1, 1, 11, 6, 13, 8)
    vines(t, 1, 1, 1, 11, 6, 13, 0.25, 4)
    t.scatter(1, 1, 1, 11, 1, 13, low, 20)
    doors(t, ('north', 'south'))

    # The hall of glyphs: carved bands, shelves of the Ultra jungle wood, glowing niches, a reading desk.
    t = hall('temple/glyphs', 11, 15, 5, floor=BLOCK)
    for z in range(1, 14):
        for x in (0, 10):
            for y in (2, 4):
                if (z + y) % 2 == 0:
                    t.set(x, y, z, CARVED)
    for z in range(2, 13):
        if z % 4 == 0:
            t.set(0, 3, z, GLOW)
            t.set(10, 3, z, GLOW)
            continue
        t.fill(1, 1, z, 1, 3, z, SHELVES)
        t.fill(9, 1, z, 9, 3, z, SHELVES)
    t.set(5, 1, 6, 'lectern[facing=north,has_book=false,powered=false]')
    t.fill(4, 1, 9, 6, 1, 9, 'g:temple_brick_slab[type=top,waterlogged=false]')
    t.stairs(5, 1, 10, STEP, 'south')
    t.set(4, 2, 9, 'flower_pot')
    t.set(6, 2, 9, 'potted_fern')
    for z in (3, 11):
        t.set(5, 5, z, LANTERN)
    vines(t, 1, 1, 1, 9, 5, 13, 0.12, 3)
    t.scatter(2, 1, 1, 8, 1, 13, u.Mix(('moss_carpet', 2), (AIR, 1)), 8)
    doors(t, ('north', 'south'))

    # A garden terrace: beds of moss and azaleas, a channel of water, a pergola under leaves, a tree.
    t = pack.template(P + 'temple/garden', 15, 10, 15)
    t.fill(0, 0, 0, 14, 0, 14, paving)
    t.fill(1, 1, 1, 13, 7, 13, AIR)
    t.walls(0, 1, 0, 14, 1, 14, old)
    t.walls(0, 2, 0, 14, 2, 14, SLAB)
    for bx, bz in ((2, 2), (9, 2), (2, 9), (9, 9)):
        t.fill(bx, 0, bz, bx + 3, 0, bz + 3, soil)
        t.scatter(bx, 1, bz, bx + 3, 1, bz + 3, bushy, 7)
    t.fill(1, 0, 7, 13, 0, 7, WATER)
    t.set(7, 0, 7, CARVED)
    for x, z in ((6, 6), (8, 6), (6, 8), (8, 8)):
        t.fill(x, 1, z, x, 3, z, LOG)
    t.fill(6, 4, 6, 8, 4, 8, LEAVES)
    bush(t, 7, 5, 7, 1.6, free=(None, AIR))
    t.set(7, 3, 7, LANTERN)
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        t.fill(x, 1, z, x, 3, z, CARVED)
        t.set(x, 4, z, POST_LANTERN)
    tree(t, 11, 11, 1, 1, 5, 2.5)
    t.scatter(1, 1, 1, 13, 1, 13, low, 12)
    doors(t, ('north', 'south'))

    # A ruined tower: a ladder up to a platform, battlements, one corner of the top fallen.
    t = pack.template(P + 'temple/tower', 9, 18, 9)
    t.room(0, 0, 8, 8, 13, old, paving, old)
    for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
        t.fill(x, 0, z, x, 15, z, CARVED)
    for x in range(9):
        for z in range(9):
            if (x in (0, 8) or z in (0, 8)) and (x + z) % 2 == 0:
                t.set(x, 15, z, old)
    for y in (6, 10):
        t.set(0, y, 4, AIR)
        t.set(8, y, 4, AIR)
        t.set(4, y, 0, AIR)
    for x, y, z in ((0, 5, 2), (8, 5, 6), (0, 9, 6), (8, 9, 2)):
        t.set(x, y, z, GLOW)
    t.set(4, 12, 4, LANTERN)
    t.ladder(4, 1, 14, 7, 'north')
    crumble(t, 8, 15, 0, 4.5)
    t.scatter(1, 1, 1, 7, 1, 6, rubble, 6)
    bush(t, 2, 15, 6, 2.0)
    vines(t, 0, 1, 0, 8, 14, 8, 0.25, 5)
    doors(t, ('north',))

    # A plaza where four paths meet: a carved ring, an obelisk on steps, lanterns at the corners.
    t = pack.template(P + 'temple/plaza', 11, 8, 11)
    t.fill(0, 0, 0, 10, 0, 10, paving)
    t.fill(0, 1, 0, 10, 5, 10, AIR)
    t.disc(5, 0, 5, 3.5, CARVED, ring=True)
    t.fill(4, 1, 4, 6, 1, 6, BRICK)
    for x, z, facing in ((5, 3, 'south'), (5, 7, 'north'), (3, 5, 'east'), (7, 5, 'west')):
        t.stairs(x, 1, z, STEP, facing)
    t.fill(5, 2, 5, 5, 5, 5, CARVED)
    t.set(5, 6, 5, GLOW)
    for x, z in ((1, 1), (9, 1), (1, 9), (9, 9)):
        t.set(x, 1, z, RAIL)
        t.set(x, 2, z, POST_LANTERN)
    t.scatter(0, 1, 0, 10, 1, 10, low, 14)
    doors(t, ('north', 'south', 'east', 'west'))

    # A fallen hall: the roof and a corner down, rubble, a tree grown up through the hole.
    t = hall('temple/fallen', 13, 13, 5, extra=6)
    crumble(t, 11, 6, 2, 5.5)
    t.fill(5, 6, 5, 7, 6, 7, AIR)
    tree(t, 6, 6, 1, 1, 9, 3.5)
    t.scatter(1, 1, 1, 11, 1, 11, rubble, 12)
    hang(t, 1, 1, 1, 11, 5, 11, ROOTS, 8)
    vines(t, 0, 1, 0, 12, 6, 12, 0.25, 4)
    t.scatter(1, 1, 1, 11, 1, 11, low, 14)
    t.set(2, 5, 10, LANTERN)
    t.set(10, 5, 10, LANTERN)
    doors(t, ('north', 'south'))

    # ------------------------------------------------------------------ outside the gateways

    # An obelisk on a carved base, a glowing band near its top.
    t = pack.template(P + 'temple/obelisk', 7, 12, 7)
    t.disc(3, 0, 3, 3, paving)
    t.fill(2, 1, 2, 4, 1, 4, CARVED)
    t.fill(3, 2, 3, 3, 8, 3, BRICK)
    t.set(3, 5, 3, CARVED)
    t.set(3, 9, 3, GLOW)
    t.set(3, 10, 3, SLAB)
    vines(t, 0, 1, 0, 6, 9, 6, 0.4, 4)
    t.scatter(0, 1, 0, 6, 1, 6, low, 6)
    t.jigsaw(3, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=BLOCK)

    # Steles in a half circle round a low altar.
    t = pack.template(P + 'temple/steles', 11, 6, 9)
    t.fill(0, 0, 0, 10, 0, 8, paving, chance=0.7)
    for x, z, h in ((1, 4, 2), (3, 6, 3), (5, 7, 4), (7, 6, 3), (9, 4, 2)):
        t.fill(x, 1, z, x, h, z, CARVED)
        t.set(x, 0, z, BRICK)
    t.set(5, 5, 7, GLOW)
    t.set(5, 1, 3, SLAB)
    vines(t, 0, 1, 0, 10, 4, 8, 0.3, 3)
    t.scatter(0, 1, 0, 10, 1, 8, bushy, 8)
    t.jigsaw(5, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=BLOCK)

    # A great stone head looking at the gateway, its eyes glowing, moss on its crown.
    t = pack.template(P + 'temple/statue', 7, 7, 7)
    t.fill(1, 0, 1, 5, 0, 5, paving)
    t.fill(2, 1, 2, 4, 4, 4, MOSSY)
    t.set(2, 3, 2, GLOW)
    t.set(4, 3, 2, GLOW)
    t.set(3, 2, 2, CARVED)
    t.stairs(3, 1, 2, 'mossy_stone_brick_stairs', 'south', half='top')
    t.fill(2, 5, 2, 4, 5, 4, 'moss_carpet')
    bush(t, 5, 1, 5, 1.4)
    vines(t, 1, 1, 1, 5, 4, 5, 0.3, 3)
    t.jigsaw(3, 0, 0, 'north', 'pad', 'exit', 'minecraft:empty', final=BLOCK)

    pack.pool(PATHS, [(P + 'temple/causeway', 3), (P + 'temple/causeway_long', 2), (P + 'temple/gallery', 3), (P + 'temple/stairs', 2),
                      (P + 'temple/causeway_broken', 1), (P + 'temple/gate', 2)], fallback=P + 'temple/door_caps')
    pack.pool(P + 'temple/door_caps', [(P + 'temple/cap_door', 2), (P + 'temple/gate', 1)])
    pack.pool(ROOMS, [(P + 'temple/courtyard', 3), (P + 'temple/shrine', 2), (P + 'temple/roots', 2), (P + 'temple/glyphs', 2),
                      (P + 'temple/garden', 2), (P + 'temple/tower', 1), (P + 'temple/plaza', 3), (P + 'temple/fallen', 2)],
              fallback=P + 'temple/path_caps')
    pack.pool(P + 'temple/path_caps', [(P + 'temple/cap_path', 1)])
    pack.pool(EXTERIOR, [(P + 'temple/obelisk', 3), (P + 'temple/steles', 2), (P + 'temple/statue', 2), (None, 2)])
    pack.pool(P + 'temple/start', [(P + 'temple/core', 1)])
    pack.jigsaw(P + 'temple', P + 'temple/start', [P + 'giant_jungle', P + 'overgrown_jungle', P + 'glowing_jungle'], size=7, max_distance=100)

    # ------------------------------------------------------------------ the small ones

    def clear(t, cx, cz, r, h):
        """A clearing: trodden ground, the air above it emptied."""
        t.disc(cx, 0, cz, r, clearing)
        for y in range(1, h + 1):
            t.disc(cx, y, cz, r, AIR)

    def tent(t, x0, z0, length, cloth, torn=False):
        """A tent along z, open at its north end: a ridge of cloth over a floor of carpet."""
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
        t.fill(x0 + 1, 1, z0 + 1, x0 + 3, 1, back - 1, 'brown_carpet')
        t.set(x0 + 1, 1, back - 1, CRATE)

    # An explorers' camp: two tents, a fire ring with log benches, crates, a lantern post, a map table.
    def camp(path, abandoned):
        t = pack.template(P + path, 13, 7, 13)
        clear(t, 6, 6, 6, 5)
        tent(t, 1, 1, 5, 'green_wool' if not abandoned else 'lime_wool', torn=abandoned)
        tent(t, 7, 1, 5, 'white_wool' if not abandoned else 'light_gray_wool', torn=abandoned)
        t.set(6, 1, 8, 'campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]' if abandoned
              else 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        for x, z in ((5, 8), (7, 8), (6, 7), (6, 9)):
            t.set(x, 0, z, 'cobblestone')
        t.fill(4, 1, 10, 8, 1, 10, 'g:ultra_jungle_log[axis=x]')
        t.fill(3, 1, 7, 3, 1, 9, 'g:ultra_jungle_log[axis=z]')
        t.set(10, 1, 8, 'cartography_table')
        t.set(10, 1, 9, 'barrel[facing=up,open=false]')
        t.fill(11, 1, 10, 11, 2, 10, CRATE)
        t.set(9, 1, 11, CRATE)
        t.set(1, 1, 9, FENCE)
        t.set(1, 2, 9, FENCE)
        t.set(1, 3, 9, POST_LANTERN)
        if abandoned:
            t.scatter(0, 1, 0, 12, 1, 12, bushy, 10)
            t.scatter(1, 1, 1, 11, 3, 6, 'cobweb', 3, on_floor=False)
            bush(t, 2, 3, 3, 1.6, free=(None, AIR))
            vines(t, 0, 1, 0, 12, 3, 12, 0.25, 2)
        else:
            t.scatter(0, 1, 0, 12, 1, 12, low, 8)
        return t

    camp('sites/camp', False)
    camp('sites/camp_abandoned', True)

    # An observatory up its own tree: a ladder up the trunk, a round deck with a rail, a hut of planks round
    # the trunk with windows, a telescope on the deck.
    def observatory(t, x0, z0, deck, top, crown, hut=True):
        cx, cz = x0 + 1, z0 + 1
        tree(t, x0, z0, 2, 1, top, crown)
        r = 5.5 if hut else 3.5
        t.disc(cx, deck, cz, r, PLANKS, only=[None, AIR])
        t.disc(cx, deck + 1, cz, r, FENCE, ring=True, only=[None, AIR])
        for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            # struts under the deck
            t.set(cx + dx, deck - 1, cz + dz, FENCE)
        if hut:
            for y in range(deck + 1, deck + 4):
                t.disc(cx, y, cz, 3.5, 'glass_pane' if y == deck + 2 else PLANKS, ring=True, only=[None, AIR])
            t.disc(cx, deck + 4, cz, 4, PLANK_SLAB, only=[None, AIR])
            for dx in (3, -3):
                t.fill(cx + dx, deck + 1, cz, cx + dx, deck + 2, cz, AIR)
            t.set(cx - 2, deck + 1, cz, 'lectern[facing=east,has_book=false,powered=false]')
            t.set(cx + 1, deck + 1, cz + 1, 'cartography_table')
            t.set(cx + 2, deck + 1, cz - 1, CRATE)
            t.set(cx + 1, deck + 3, cz - 1, LANTERN)
            t.set(cx - 4, deck + 1, cz, 'polished_blackstone_wall')
            t.set(cx - 4, deck + 2, cz, 'end_rod[facing=west]')
        else:
            t.set(cx + 2, deck + 1, cz + 1, 'polished_blackstone_wall')
            t.set(cx + 2, deck + 2, cz + 1, 'end_rod[facing=east]')
            t.set(cx - 2, deck + 1, cz - 1, CRATE)
        t.ladder(cx - 1, 1, deck + 1, cz - 2, 'north')
        return cx, cz

    def treehouse(path, twin):
        if not twin:
            t = pack.template(P + path, 15, 26, 15)
            cx, cz = observatory(t, 6, 6, 13, 20, 5)
            for dx in (5, -5):
                t.set(cx + dx, 15, cz, POST_LANTERN)
            return t
        t = pack.template(P + path, 25, 26, 13)
        observatory(t, 5, 5, 12, 19, 5)
        observatory(t, 17, 5, 12, 17, 4, hut=False)
        # The bridge between the two decks, a few planks missing.
        for x in range(11, 16):
            t.set(x, 12, 6, PLANKS if t.rng.random() < 0.85 else AIR)
            t.set(x, 13, 5, FENCE)
            t.set(x, 13, 7, FENCE)
            t.set(x, 13, 6, AIR)
        t.set(11, 13, 6, AIR)
        t.set(15, 13, 6, AIR)
        return t

    treehouse('sites/treehouse', False)
    treehouse('sites/treehouse_twin', True)

    # Overgrown ruins: a court of broken walls with fallen columns, or a tower half down.
    def ruins(path, tower):
        if not tower:
            t = pack.template(P + path, 13, 8, 13)
            t.fill(1, 0, 1, 11, 0, 11, paving, chance=0.75)
            for x in range(1, 12):
                for z in range(1, 12):
                    if x in (1, 11) or z in (1, 11):
                        h = t.rng.choice((0, 1, 1, 2, 2, 3))
                        if h:
                            t.fill(x, 1, z, x, h, z, old)
            for x, z in ((3, 3), (9, 3), (3, 9)):
                t.fill(x, 1, z, x, t.rng.randint(2, 5), z, CARVED)
            t.fill(6, 1, 9, 9, 1, 9, CARVED)          # the fourth column, fallen
            t.set(6, 1, 6, SLAB)
            t.set(6, 0, 6, GLOW)
            tree(t, 8, 5, 1, 1, 6, 3)
            t.scatter(1, 1, 1, 11, 1, 11, bushy, 12)
            vines(t, 0, 1, 0, 12, 5, 12, 0.3, 3)
            return t
        t = pack.template(P + path, 9, 14, 9)
        t.fill(1, 0, 1, 7, 0, 7, paving)
        for x in range(1, 8):
            for z in range(1, 8):
                if x in (1, 7) or z in (1, 7):
                    h = max(2, 11 - abs(x - 1) - abs(z - 7) + t.rng.randint(-2, 1))
                    t.fill(x, 1, z, x, min(h, 12), z, old)
        t.fill(2, 1, 2, 6, 12, 6, AIR)
        t.fill(3, 1, 1, 5, 3, 1, AIR)
        t.fill(2, 5, 2, 6, 5, 6, old, chance=0.5)
        t.scatter(2, 1, 2, 6, 1, 6, rubble, 6)
        t.set(4, 5, 4, old)
        t.set(4, 4, 4, LANTERN)
        t.scatter(0, 1, 0, 8, 1, 8, rubble, 5)
        bush(t, 2, 10, 6, 2.0)
        vines(t, 0, 1, 0, 8, 12, 8, 0.3, 5)
        return t

    ruins('sites/ruins', False)
    ruins('sites/ruins_tower', True)

    # A research cabin of the Ultra jungle wood: on stilts with a ladder up to its porch, or on the ground and overgrown.
    def cabin(path, stilts):
        base = 4 if stilts else 0
        t = pack.template(P + path, 11, base + 10, 11)
        if stilts:
            for x, z in ((1, 2), (9, 2), (1, 8), (9, 8), (5, 1)):
                t.fill(x, 0, z, x, base - 1, z, LOG)
            t.ladder(5, 1, base, 0, 'north')
        t.fill(1, base, 1, 9, base, 8, PLANKS)
        t.fill(1, base + 1, 2, 9, base + 3, 8, PLANKS)
        t.fill(2, base + 1, 3, 8, base + 3, 7, AIR)
        for x, z in ((1, 2), (9, 2), (1, 8), (9, 8)):
            t.fill(x, base + 1, z, x, base + 3, z, LOG)
        for x in (3, 7):
            t.set(x, base + 2, 2, 'glass_pane')
            t.set(x, base + 2, 8, 'glass_pane')
        for z in (4, 6):
            t.set(1, base + 2, z, 'glass_pane')
            t.set(9, base + 2, z, 'glass_pane')
        t.fill(5, base + 1, 2, 5, base + 2, 2, AIR)
        for x in range(1, 10):
            if x != 5:
                t.set(x, base + 1, 1, FENCE)
        # The roof: two slopes of stairs along z, gables of planks.
        for x in range(11):
            d = min(x, 10 - x)
            y = base + 3 + d
            for z in range(1, 10):
                if x == 5:
                    t.set(x, y, z, PLANKS)
                else:
                    t.stairs(x, y, z, WOOD_STEP, 'east' if x < 5 else 'west')
            if 2 <= x <= 8 and y - 1 >= base + 4:
                for z in (2, 8):
                    t.fill(x, base + 4, z, x, y - 1, z, PLANKS)
                t.fill(x, base + 4, 3, x, y - 1, 7, AIR)
        # Inside: shelves, a bench with a brewing stand and pots, a lectern, crates, a lantern from the ridge.
        t.fill(8, base + 1, 4, 8, base + 2, 6, SHELVES)
        t.fill(2, base + 1, 6, 4, base + 1, 7, PLANK_SLAB.replace('bottom', 'top'))
        t.set(2, base + 2, 7, 'brewing_stand')
        t.set(3, base + 2, 7, 'potted_fern')
        t.set(4, base + 2, 7, 'flower_pot')
        t.set(2, base + 1, 4, 'g:ultra_jungle_crafting_table')
        t.set(6, base + 1, 5, 'lectern[facing=west,has_book=false,powered=false]')
        t.set(2, base + 1, 3, CRATE)
        t.set(8, base + 1, 7, 'barrel[facing=up,open=false]')
        t.set(5, base + 7, 5, LANTERN)
        if stilts:
            t.scatter(0, 1, 0, 10, 1, 10, low, 6)
            vines(t, 0, 1, 0, 10, base, 10, 0.25, 3)
        else:
            vines(t, 0, 1, 0, 10, 4, 10, 0.4, 3)
            for x, z in ((0, 1), (10, 9), (0, 9)):
                bush(t, x, 4, z, 1.8)
            t.scatter(0, 1, 0, 10, 1, 10, bushy, 10)
            t.scatter(2, 1, 3, 8, 1, 7, u.Mix(('moss_carpet', 2), (AIR, 1)), 6)
        return t

    cabin('sites/cabin', True)
    cabin('sites/cabin_ground', False)

    jungle = [P + b for b in ('giant_jungle', 'overgrown_jungle', 'glowing_jungle', 'mountain_jungle', 'swamp_jungle')]
    for name, variants, biomes in (
            ('camp', ('camp', 'camp_abandoned'), [P + b for b in ('giant_jungle', 'overgrown_jungle', 'glowing_jungle', 'mountain_jungle')]),
            ('treehouse', ('treehouse', 'treehouse_twin'), [P + b for b in ('giant_jungle', 'overgrown_jungle', 'glowing_jungle', 'swamp_jungle')]),
            ('ruins', ('ruins', 'ruins_tower'), jungle),
            ('cabin', ('cabin', 'cabin_ground'), jungle)):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32)

    # The temple: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from the temple.
    pack.structure_set(P + 'landmark', [(P + 'temple', 1)], spacing=36, separation=14, salt=518204377)
    pack.structure_set(P + 'sites', [(P + 'camp', 3), (P + 'treehouse', 3), (P + 'ruins', 3), (P + 'cabin', 2)],
                       spacing=11, separation=5, salt=2061938455, exclusion=(P + 'landmark', 8))
