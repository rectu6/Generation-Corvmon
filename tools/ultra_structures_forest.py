"""
Ultra Forest (Kartana's woods): its structures, for tools/ultra_structures.py.

The great one: the origami sanctuary, a jigsaw of halls and courts joined by walkways. The main
hall stands in a walled court: dark posts, paper screens, a great roof of red shingles, inside a
giant paper crane on its dais between paper lanterns. A gate opens the court on the forest, where
a path of torii, a bamboo grove or a stone garden lead away. Walkways: roofed, lit by lanterns,
an arched bridge over a pond, a path of torii, stone steps, a path through bamboo. Halls and
courts: a meditation hall, a tea house, a court of raked sand, a koi pond, a bell pavilion, a
library of scrolls, a paper workshop, a gallery of folded sculptures, a pagoda, crossroads.
The small ones: a small shrine, a meditation pavilion, a wooden tower, a garden bridge, each in
two variants.

Ultra dark wood (posts, beams, floors), paper walls (calcite, white concrete, quartz) and white
glass screens, bamboo mosaic for the mats, shingles of Generations for the roofs, red torii,
stone lanterns, shroomlights for the paper lanterns (the shaders want real light). Same
connectors as the other worlds: a hall's door is named `door` and asks the walkway pool for a
`tube_end`; a walkway's end is named `tube_end` and asks the hall pool for a `door`. Doors 3 wide
and 3 high, their connector at the bottom middle, floors at y 0.
"""

WORLD = 'ultra_forest'
P = WORLD + '/'

AIR = 'minecraft:air'
LOG = 'g:ultra_dark_log[axis=y]'
PLANKS = 'g:ultra_dark_planks'
DARK_SLAB = 'g:ultra_dark_slab[type=bottom,waterlogged=false]'
DARK_STEP = 'g:ultra_dark_stairs'
FENCE = 'g:ultra_dark_fence'
MAT = 'bamboo_mosaic'
BAMBOO = 'stripped_bamboo_block[axis=y]'
SCREEN = 'white_stained_glass_pane'
SHINGLES = 'g:red_shingles'
SHINGLE_STEP = 'g:red_shingles_stairs'
GREY = 'g:gray_shingles'
GREY_STEP = 'g:gray_shingles_stairs'
RED = 'red_concrete'
BLACK = 'g:black_shingles'
STONE = 'stone_bricks'
STONE_STEP = 'stone_brick_stairs'
STONE_WALL = 'stone_brick_wall'
STONE_SLAB = 'smooth_stone_slab[type=bottom,waterlogged=false]'
CALCITE = 'calcite'
QUARTZ = 'smooth_quartz'
SAND = 'white_concrete_powder'
MOSS = 'moss_block'
CARPET = 'moss_carpet'
WATER = 'water[level=0]'
LILY = 'lily_pad'
LAMP = 'shroomlight'
GLOW = 'g:crystal_light'
LANTERN = 'lantern[hanging=true,waterlogged=false]'
STANDING = 'lantern[hanging=false,waterlogged=false]'
CHAIN = 'chain[axis=y,waterlogged=false]'
CANDLE = 'white_candle[candles=3,lit=true,waterlogged=false]'
LEAVES = 'azalea_leaves[distance=1,persistent=true,waterlogged=false]'
BLOSSOM = 'flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]'
CHERRY = 'cherry_leaves[distance=1,persistent=true,waterlogged=false]'

WALKWAYS = P + 'sanctuary/walkways'
HALLS = P + 'sanctuary/halls'
GATE = P + 'sanctuary/gate'
OUTSIDE = P + 'sanctuary/outside'


def beam(axis):
    return f'g:stripped_ultra_dark_log[axis={axis}]'


# A paper crane, head to the north: (x, y, z) cells from its middle, wings folded up.
CRANE = [(0, 0, -1), (0, 0, 0), (0, 0, 1), (-1, 0, 0), (1, 0, 0), (0, 1, 0),
         (-2, 1, 0), (2, 1, 0), (-3, 2, 0), (3, 2, 0), (-2, 1, 1), (2, 1, 1), (-4, 3, 0), (4, 3, 0),
         (0, 1, -2), (0, 2, -3), (0, 3, -3), (0, 3, -4),
         (0, 1, 2), (0, 2, 3), (0, 3, 4)]
# A paper frog (a smaller fold).
FROG = [(0, 0, 0), (-1, 0, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0), (0, 1, -1), (-1, 0, -1), (1, 0, -1), (-1, 0, 2), (1, 0, 2)]


def build(u, pack):
    paper = u.Mix((CALCITE, 4), ('white_concrete', 3), (QUARTZ, 1))
    path = u.Mix(('gravel', 3), ('stone_bricks', 2), ('mossy_stone_bricks', 1), ('mossy_cobblestone', 1))
    paving = u.Mix((STONE, 5), ('mossy_stone_bricks', 2), ('cracked_stone_bricks', 1), ('polished_andesite', 2))
    garden = u.Mix(('grass_block', 5), (MOSS, 3), ('podzol', 1))
    mossy = u.Mix((MOSS, 3), ('mossy_cobblestone', 2), (CARPET, 2))

    # ------------------------------------------------------------------ helpers

    def roof(t, x0, z0, x1, z1, y, step=SHINGLE_STEP, full=SHINGLES, ceiling=PLANKS, levels=None):
        """A hip roof over the rectangle (its eaves on it), a ceiling under its first layer; returns the top's y.
        With `levels`, only that many layers (a skirt round a storey)."""
        if ceiling:
            t.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, ceiling)
        k = 0
        while True:
            if levels is not None and k >= levels:
                return y + k - 1
            a0, a1, b0, b1, yy = x0 + k, x1 - k, z0 + k, z1 - k, y + k
            if a1 - a0 <= 1 or b1 - b0 <= 1:
                t.fill(a0, yy, b0, a1, yy, b1, full)
                return yy
            for x in range(a0, a1 + 1):
                t.stairs(x, yy, b0, step, 'south')
                t.stairs(x, yy, b1, step, 'north')
            for z in range(b0, b1 + 1):
                t.stairs(a0, yy, z, step, 'east')
                t.stairs(a1, yy, z, step, 'west')
            for x, z in ((a0, b0), (a1, b0), (a0, b1), (a1, b1)):
                t.set(x, yy, z, full)
            k += 1

    def gable(t, x0, x1, z0, z1, y, step=SHINGLE_STEP, full=SHINGLES):
        """A roof along z over x0..x1: stairs climbing from both eaves to a ridge of full blocks."""
        k = 0
        while x0 + k <= x1 - k:
            a, b = x0 + k, x1 - k
            for z in range(z0, z1 + 1):
                if b - a <= 1:
                    t.fill(a, y + k, z, b, y + k, z, full)
                else:
                    t.stairs(a, y + k, z, step, 'east')
                    t.stairs(b, y + k, z, step, 'west')
            k += 1

    def house(t, x0, z0, x1, z1, h, floor=MAT, walls=True):
        """Dark posts every 4 and at the corners, a paper base, screens of white glass, a beam under the roof."""
        t.fill(x0, 0, z0, x1, 0, z1, floor)
        t.fill(x0, 1, z0, x1, h, z1, AIR)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x not in (x0, x1) and z not in (z0, z1):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                post = corner or (z in (z0, z1) and (x - x0) % 4 == 0) or (x in (x0, x1) and (z - z0) % 4 == 0)
                if post:
                    t.fill(x, 1, z, x, h, z, LOG)
                    continue
                t.set(x, h, z, beam('x' if z in (z0, z1) else 'z'))
                if walls:
                    t.set(x, 1, z, paper)
                    t.fill(x, 2, z, x, h - 1, z, SCREEN)

    def torii(t, cx, z, half, h, axis='x'):
        """A red gate: posts at cx +- half, a red tie under a black lintel overhanging by one."""
        def at(a, y):
            return (cx + a, y, z) if axis == 'x' else (z, y, cx + a)
        for a in (-half, half):
            for y in range(1, h + 1):
                t.set(*at(a, y), RED)
        for a in range(-half, half + 1):
            t.set(*at(a, h - 1), RED)
        for a in range(-half - 1, half + 2):
            t.set(*at(a, h + 1), BLACK)
        t.set(*at(0, h), RED)

    def bamboo(t, x, z, h):
        """A cane of bamboo, its leaves at the top."""
        for y in range(1, h + 1):
            leaves = 'large' if y >= h - 1 else 'small' if y == h - 2 else 'none'
            t.set(x, y, z, f'bamboo[age=1,leaves={leaves},stage=0]')

    def ring_beam(t, x0, z0, x1, z1, y):
        for x in range(x0, x1 + 1):
            t.set(x, y, z0, beam('x'))
            t.set(x, y, z1, beam('x'))
        for z in range(z0 + 1, z1):
            t.set(x0, y, z, beam('z'))
            t.set(x1, y, z, beam('z'))

    def stone_lantern(t, x, y, z):
        """A stone lantern: a post, the light, a cap."""
        t.set(x, y, z, STONE_WALL)
        t.set(x, y + 1, z, STANDING)
        t.set(x, y + 2, z, STONE_SLAB)

    def paper_lantern(t, x, y, z, drop=1):
        """A paper lantern hanging from a chain under (x, y + drop + 1, z)."""
        t.fill(x, y + 1, z, x, y + drop, z, CHAIN)
        t.set(x, y, z, LAMP)

    def sculpt(t, cells, x, y, z, scale=1, block=None, flip=False):
        """A folded paper sculpture, each cell a cube of `scale`."""
        for dx, dy, dz in cells:
            if flip:
                dz = -dz
            for i in range(scale):
                for j in range(scale):
                    for k in range(scale):
                        t.set(x + dx * scale + i, y + dy * scale + j, z + dz * scale + k, block or paper)

    def doors(t, sides, pool=WALKWAYS, frame=LOG):
        sx, _, sz = t.size
        for side in sides:
            t.door(side, (sx if side in ('north', 'south') else sz) // 2, pool, frame=frame)

    def court(path, sx, sz, ground=None, wall=True, height=8):
        """An open court over the whole template: paved or green, a low wall of stone round it, stone lanterns at its corners."""
        t = pack.template(P + path, sx, height, sz)
        t.fill(0, 0, 0, sx - 1, 0, sz - 1, ground or garden)
        t.fill(0, 1, 0, sx - 1, height - 1, sz - 1, AIR)
        if wall:
            t.walls(0, 1, 0, sx - 1, 1, sz - 1, STONE)
            t.walls(0, 2, 0, sx - 1, 2, sz - 1, STONE_SLAB)
            for x, z in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
                t.fill(x, 1, z, x, 2, z, STONE)
                stone_lantern(t, x, 3, z)
        return t

    def hall(path, sx, sz, h, floor=MAT):
        """A closed hall over the whole template: a paper house, its roof (a hip roof, one over the walls)."""
        top = (min(sx, sz) + 1) // 2
        t = pack.template(P + path, sx, h + top + 2, sz)
        house(t, 0, 0, sx - 1, sz - 1, h, floor)
        roof(t, 0, 0, sx - 1, sz - 1, h + 1)
        return t

    # ------------------------------------------------------------------ the main hall (start)

    t = pack.template(P + 'sanctuary/main_hall', 27, 21, 27)
    c = 13
    t.fill(0, 0, 0, 26, 0, 26, paving)
    t.fill(0, 1, 0, 26, 20, 26, AIR)
    # The court's wall, its lanterns.
    t.walls(0, 1, 0, 26, 1, 26, STONE)
    t.walls(0, 2, 0, 26, 2, 26, STONE_SLAB)
    for a in (0, 6, 20, 26):
        for x, z in ((a, 0), (a, 26), (0, a), (26, a)):
            t.fill(x, 1, z, x, 2, z, STONE)
            stone_lantern(t, x, 3, z)
    # Gravel round the hall, moss and azaleas in the corners, the path of stone to it.
    t.fill(2, 0, 2, 24, 0, 24, 'gravel')
    t.fill(c - 1, 0, 0, c + 1, 0, 26, STONE)
    t.fill(0, 0, c - 1, 26, 0, c + 1, STONE)
    for x0, z0 in ((1, 1), (22, 1), (1, 22), (22, 22)):
        t.fill(x0, 0, z0, x0 + 3, 0, z0 + 3, MOSS)
        t.fill(x0 + 1, 1, z0 + 1, x0 + 2, 2, z0 + 2, BLOSSOM if (x0 + z0) % 2 else LEAVES)
    # The hall: on a stone base, 15 x 15, its great roof.
    t.fill(5, 0, 5, 21, 0, 21, STONE)
    house(t, 6, 6, 20, 20, 6)
    t.fill(6, 0, 6, 20, 0, 20, MAT)
    for x in range(7, 20):
        t.set(x, 0, 6, PLANKS)
    top = roof(t, 4, 4, 22, 22, 7)
    t.set(c, top + 1, c, GLOW)
    t.set(c, top + 2, c, 'end_rod[facing=up]')
    for x, z in ((4, 4), (22, 4), (4, 22), (22, 22)):
        paper_lantern(t, x, 5, z)
    # Its doors: one on each side, in line with the court's gates.
    for x, z in ((c, 6), (c, 20), (6, c), (20, c)):
        for d in (-1, 0, 1):
            xx, zz = (x + d, z) if z in (6, 20) else (x, z + d)
            t.fill(xx, 1, zz, xx, 3, zz, AIR)
    # Inside: mats, the dais, the giant crane between paper lanterns, candles.
    t.fill(9, 1, 11, 17, 1, 19, PLANKS)
    sculpt(t, CRANE, c, 2, 15, scale=1, block=paper)
    for x in (9, 17):
        for z in (9, 13, 17):
            paper_lantern(t, x, 5, z)
    for x in (10, 16):
        t.set(x, 2, 12, CANDLE)
    for z in (8, 11):
        for x in (9, 17):
            t.set(x, 1, z, 'white_carpet')
    t.door('north', c, GATE, frame=LOG)
    for side in ('east', 'west', 'south'):
        t.door(side, c, WALKWAYS, frame=LOG)

    # ------------------------------------------------------------------ the gate, from the court to the forest

    t = pack.template(P + 'sanctuary/gatehouse', 11, 12, 9)
    t.fill(0, 0, 0, 10, 0, 8, paving)
    t.fill(0, 1, 0, 10, 11, 8, AIR)
    house(t, 1, 1, 9, 7, 4, floor=STONE, walls=False)
    for z in (1, 7):
        for x in (2, 3, 7, 8):
            t.set(x, 1, z, paper)
            t.fill(x, 2, z, x, 3, z, SCREEN)
    t.fill(4, 1, 1, 6, 3, 1, AIR)
    t.fill(4, 1, 7, 6, 3, 7, AIR)
    roof(t, 0, 0, 10, 8, 5)
    paper_lantern(t, 5, 3, 4)
    for x in (2, 8):
        t.set(x, 1, 4, 'g:crate' if x == 2 else 'barrel[facing=up,open=false]')
    t.jigsaw(5, 1, 0, 'north', 'tube_end', 'door', HALLS)
    t.jigsaw(5, 0, 8, 'south', 'exit', 'yard', OUTSIDE, final=STONE)

    # A path of torii going away.
    t = pack.template(P + 'sanctuary/torii_path', 9, 9, 17)
    t.fill(0, 0, 0, 8, 0, 16, garden)
    t.fill(0, 1, 0, 8, 8, 16, AIR)
    t.fill(3, 0, 0, 5, 0, 16, path)
    for z in (2, 7, 12):
        torii(t, 4, z, 3, 5)
    for z in (4, 9, 14):
        stone_lantern(t, 1, 1, z)
        stone_lantern(t, 7, 1, z)
    t.jigsaw(4, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=STONE)

    # A bamboo grove round a stone path.
    t = pack.template(P + 'sanctuary/bamboo_grove', 13, 14, 13)
    t.fill(0, 0, 0, 12, 0, 12, garden)
    t.fill(0, 1, 0, 12, 13, 12, AIR)
    t.fill(5, 0, 0, 7, 0, 12, path)
    for x in range(13):
        for z in range(13):
            if 4 <= x <= 8 or t.rng.random() < 0.55:
                continue
            bamboo(t, x, z, t.rng.randint(6, 12))
    stone_lantern(t, 4, 1, 6)
    stone_lantern(t, 8, 1, 6)
    t.jigsaw(6, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=STONE)

    # A stone garden: raked sand, three rocks, moss.
    t = pack.template(P + 'sanctuary/stone_garden', 13, 6, 11)
    t.fill(0, 0, 0, 12, 0, 10, MOSS)
    t.fill(0, 1, 0, 12, 5, 10, AIR)
    t.fill(1, 0, 1, 11, 0, 9, SAND)
    for x in range(1, 12):
        if x % 2 == 0:
            t.fill(x, 0, 1, x, 0, 9, CALCITE)
    for x, z, h in ((3, 3, 3), (8, 4, 2), (6, 8, 1)):
        t.disc(x, 1, z, 1.5, 'mossy_cobblestone')
        t.fill(x, 1, z, x, h, z, 'cobblestone')
        t.disc(x, 0, z, 2.5, CALCITE, ring=True)
    t.fill(5, 0, 0, 7, 0, 0, STONE)
    t.jigsaw(6, 0, 0, 'north', 'yard', 'exit', 'minecraft:empty', final=STONE)

    # ------------------------------------------------------------------ walkways (along z)

    def ends(t, x, y_out, length):
        t.jigsaw(x, 1, 0, 'north', 'tube_end', 'door', HALLS)
        t.jigsaw(x, y_out, length - 1, 'south', 'tube_end', 'door', HALLS)

    def frames(t, length, post=LOG, lintel=None):
        for z in (0, length - 1):
            t.fill(1, 1, z, 1, 3, z, post)
            t.fill(5, 1, z, 5, 3, z, post)
            t.fill(1, 4, z, 5, 4, z, lintel or beam('x'))

    def walkway(path, length):
        """A roofed walkway, open on its sides between posts and railings, a gable roof of shingles."""
        t = pack.template(P + path, 7, 8, length)
        t.fill(0, 0, 0, 6, 0, length - 1, garden)
        t.fill(1, 0, 0, 5, 0, length - 1, PLANKS)
        t.fill(0, 1, 0, 6, 7, length - 1, AIR)
        for z in range(length):
            if z % 4 == 0 or z == length - 1:
                t.fill(1, 1, z, 1, 3, z, LOG)
                t.fill(5, 1, z, 5, 3, z, LOG)
            else:
                t.set(1, 1, z, FENCE)
                t.set(5, 1, z, FENCE)
        t.fill(1, 4, 0, 5, 4, length - 1, PLANKS)
        gable(t, 0, 6, 0, length - 1, 4)
        frames(t, length)
        ends(t, 3, 1, length)
        return t

    walkway('sanctuary/walkway', 9)

    t = walkway('sanctuary/walkway_lanterns', 13)
    for z in (2, 6, 10):
        paper_lantern(t, 3, 3, z, drop=0)
    for z in (2, 10):
        stone_lantern(t, 0, 1, z)
        stone_lantern(t, 6, 1, z)

    # An arched bridge over a pond: red rails, its deck one higher in the middle.
    t = pack.template(P + 'sanctuary/bridge', 7, 6, 13)
    t.fill(0, 0, 0, 6, 0, 12, garden)
    t.fill(0, 1, 0, 6, 5, 12, AIR)
    t.fill(0, 0, 2, 6, 0, 10, WATER)
    for x, z in ((0, 4), (6, 7), (1, 9), (5, 3)):
        t.set(x, 1, z, LILY)
    # The deck climbs half a block at a time (slab, plank, slab) and comes down again; red rails on it.
    deck = {2: (0, PLANKS), 3: (0, PLANKS), 4: (1, DARK_SLAB), 5: (1, PLANKS), 6: (2, DARK_SLAB), 7: (1, PLANKS), 8: (1, DARK_SLAB),
            9: (0, PLANKS), 10: (0, PLANKS)}
    for z, (y, block) in deck.items():
        t.fill(1, y, z, 5, y, z, block)
        for x in (1, 5):
            t.set(x, y + 1, z, 'red_nether_brick_wall')
    for x in (1, 5):
        t.fill(x, 0, 4, x, 1, 4, RED)
        t.fill(x, 0, 8, x, 1, 8, RED)
    for x, z in ((1, 4), (5, 4), (1, 8), (5, 8)):
        t.set(x, 3, z, STANDING)
    frames(t, 13, post='red_concrete', lintel=BLACK)
    ends(t, 3, 1, 13)

    # A path of torii: stone flags, three gates, lanterns, no walls.
    t = pack.template(P + 'sanctuary/torii_walk', 7, 8, 13)
    t.fill(0, 0, 0, 6, 0, 12, garden)
    t.fill(2, 0, 0, 4, 0, 12, paving)
    t.fill(0, 1, 0, 6, 7, 12, AIR)
    for z in (0, 6, 12):
        torii(t, 3, z, 2, 5)
    for z in (3, 9):
        stone_lantern(t, 0, 1, z)
        stone_lantern(t, 6, 1, z)
    ends(t, 3, 1, 13)

    # Stone steps: in at the bottom (north), out 4 higher (south), lanterns on the way.
    t = pack.template(P + 'sanctuary/steps', 7, 9, 11)
    t.fill(0, 0, 0, 6, 0, 10, garden)
    t.fill(0, 1, 0, 6, 8, 10, AIR)
    t.fill(0, 1, 7, 6, 4, 10, STONE)
    for i in range(4):
        z = 3 + i
        t.fill(0, 1, z, 6, i, z, STONE)
        for x in range(1, 6):
            t.stairs(x, 1 + i, z, STONE_STEP, 'south')
        t.set(0, 1 + i, z, STONE)
        t.set(6, 1 + i, z, STONE)
    t.fill(1, 4, 7, 5, 4, 10, paving)
    t.fill(0, 4, 7, 0, 4, 10, MOSS)
    t.fill(6, 4, 7, 6, 4, 10, MOSS)
    t.fill(0, 0, 0, 6, 0, 2, paving)
    stone_lantern(t, 0, 1, 1)
    stone_lantern(t, 6, 1, 1)
    stone_lantern(t, 0, 5, 9)
    stone_lantern(t, 6, 5, 9)
    for z in (0, 10):
        y = 1 if z == 0 else 5
        t.fill(1, y, z, 1, y + 2, z, LOG)
        t.fill(5, y, z, 5, y + 2, z, LOG)
        t.fill(1, y + 3, z, 5, y + 3, z, beam('x'))
    t.fill(2, 5, 10, 4, 7, 10, AIR)
    ends(t, 3, 5, 11)

    # A path through bamboo.
    t = pack.template(P + 'sanctuary/bamboo_walk', 7, 13, 11)
    t.fill(0, 0, 0, 6, 0, 10, garden)
    t.fill(2, 0, 0, 4, 0, 10, path)
    t.fill(0, 1, 0, 6, 12, 10, AIR)
    for x in (0, 6):
        for z in range(11):
            if t.rng.random() < 0.7:
                bamboo(t, x, z, t.rng.randint(5, 11))
    frames(t, 11, post=BAMBOO, lintel=beam('x'))
    t.fill(1, 4, 5, 5, 4, 5, beam('x'))
    paper_lantern(t, 3, 3, 5, drop=0)
    t.fill(1, 1, 5, 1, 3, 5, BAMBOO)
    t.fill(5, 1, 5, 5, 3, 5, BAMBOO)
    ends(t, 3, 1, 11)

    for name, connector, target, block in (('sanctuary/cap_door', 'tube_end', 'door', STONE), ('sanctuary/cap_walkway', 'door', 'tube_end', PLANKS)):
        t = pack.template(P + name, 5, 5, 1)
        t.fill(0, 0, 0, 4, 3, 0, block)
        t.fill(0, 4, 0, 4, 4, 0, STONE_SLAB)
        t.set(2, 2, 0, LAMP)
        t.jigsaw(2, 1, 0, 'north', connector, target, 'minecraft:empty', final=block)

    # ------------------------------------------------------------------ halls and courts

    # The meditation hall: mats, cushions in rows, a low altar with candles, lanterns.
    t = hall('sanctuary/meditation', 15, 13, 5)
    for x in range(3, 12, 2):
        for z in (4, 7):
            t.set(x, 1, z, 'white_carpet' if (x + z) % 4 else 'light_gray_carpet')
    t.fill(5, 1, 10, 9, 1, 11, DARK_SLAB)
    t.set(7, 2, 11, QUARTZ)
    t.set(6, 2, 10, CANDLE)
    t.set(8, 2, 10, CANDLE)
    t.set(5, 2, 11, 'potted_azalea_bush')
    t.set(9, 2, 11, 'potted_azalea_bush')
    for x in (4, 10):
        paper_lantern(t, x, 4, 6)
    doors(t, ('north', 'east', 'west'))

    # A tea house: a hearth, a low table, cushions, a window on a garden of moss.
    t = hall('sanctuary/tea_house', 13, 11, 4, floor=MAT)
    t.fill(5, 1, 4, 7, 1, 6, DARK_SLAB)
    t.set(6, 1, 5, 'campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
    for x, z in ((4, 5), (8, 5), (6, 3), (6, 7)):
        t.set(x, 1, z, 'red_carpet')
    t.set(2, 1, 8, 'flower_pot')
    t.set(10, 1, 8, 'potted_bamboo')
    t.set(2, 1, 2, 'barrel[facing=up,open=false]')
    paper_lantern(t, 6, 3, 2)
    doors(t, ('north', 'south'))

    # A court of raked sand: grooves of calcite, rocks with moss, a bench.
    t = court('sanctuary/sand_court', 15, 15)
    t.fill(2, 0, 2, 12, 0, 12, SAND)
    for z in range(2, 13, 2):
        t.fill(2, 0, z, 12, 0, z, CALCITE)
    for x, z, r in ((5, 5, 1.5), (10, 9, 1.0), (4, 10, 0.5)):
        t.disc(x, 1, z, r, 'mossy_cobblestone')
        t.disc(x, 0, z, r + 1.5, MOSS, ring=True)
    t.set(5, 2, 5, 'cobblestone')
    t.fill(7, 0, 0, 7, 0, 14, STONE)
    t.fill(0, 0, 7, 14, 0, 7, STONE)
    t.fill(11, 1, 3, 12, 1, 3, DARK_SLAB)
    doors(t, ('north', 'south', 'east'))

    # A koi pond: water with lily pads, an island with its lantern, stepping stones across.
    t = court('sanctuary/koi_pond', 15, 15)
    t.disc(7, 0, 7, 5.5, WATER)
    t.disc(7, 0, 7, 5.5, 'mossy_cobblestone', ring=True)
    t.disc(9, 0, 9, 1.5, MOSS)
    stone_lantern(t, 9, 1, 9)
    for x, z in ((4, 5), (10, 4), (5, 10), (3, 8)):
        t.set(x, 1, z, LILY)
    for z in (1, 3, 5, 7, 9, 11, 13):
        t.set(7, 0, z, 'smooth_stone')
    for x in (1, 3, 5, 9, 11, 13):
        t.set(x, 0, 7, 'smooth_stone')
    t.fill(1, 1, 1, 2, 2, 2, CHERRY)
    t.fill(12, 1, 12, 13, 2, 13, BLOSSOM)
    doors(t, ('north', 'south', 'west'))

    # A bell pavilion: four posts, a small roof, the bell hanging in the middle.
    t = court('sanctuary/bell_court', 11, 11, ground=paving, height=12)
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        t.fill(x, 1, z, x, 5, z, RED)
    t.fill(3, 6, 3, 7, 6, 7, PLANKS)
    roof(t, 2, 2, 8, 8, 6, ceiling=None)
    t.set(5, 5, 5, 'bell[attachment=ceiling,facing=north,powered=false]')
    t.fill(4, 0, 4, 6, 0, 6, STONE)
    doors(t, ('north', 'south'))

    # A library: shelves of scrolls, reading desks.
    t = hall('sanctuary/library', 13, 13, 5, floor=PLANKS)
    for x in (2, 4, 8, 10):
        for z in range(2, 11):
            if z == 6:
                continue
            t.fill(x, 1, z, x, 3, z, 'bookshelf')
    t.set(6, 1, 9, 'lectern[facing=north,has_book=false,powered=false]')
    t.set(6, 1, 3, 'lectern[facing=south,has_book=false,powered=false]')
    paper_lantern(t, 6, 4, 6)
    doors(t, ('north', 'south'))

    # A paper workshop: looms, stacks of white wool (the paper), a cutting table.
    t = hall('sanctuary/workshop', 13, 11, 4, floor=PLANKS)
    for x in (2, 4):
        t.set(x, 1, 2, 'loom[facing=south]')
    t.set(10, 1, 2, 'cartography_table')
    t.fill(8, 1, 7, 10, 1, 8, 'white_wool')
    t.set(9, 2, 8, 'white_wool')
    t.fill(4, 1, 5, 8, 1, 5, DARK_SLAB)
    for x in (5, 7):
        t.set(x, 2, 5, 'white_carpet')
    t.set(6, 2, 5, QUARTZ)
    paper_lantern(t, 6, 3, 3)
    doors(t, ('north', 'east'))

    # A gallery of folded sculptures: cranes and frogs on stone pedestals.
    t = hall('sanctuary/gallery', 17, 11, 6, floor=MAT)
    t.disc(8, 1, 5, 2.5, STONE_SLAB)
    sculpt(t, CRANE, 8, 2, 5, block=paper)
    sculpt(t, FROG, 3, 1, 8, block=QUARTZ, flip=True)
    sculpt(t, FROG, 13, 1, 8, block='white_wool', flip=True)
    for x in (4, 12):
        paper_lantern(t, x, 5, 3)
    doors(t, ('east', 'west'))

    # A pagoda: five tiers of roofs, a ladder up through its floors, a light at its top (a dead end).
    t = pack.template(P + 'sanctuary/pagoda', 11, 31, 11)
    t.fill(0, 0, 0, 10, 0, 10, paving)
    t.fill(0, 1, 0, 10, 30, 10, AIR)
    y = 0
    halves = (3, 3, 2, 2, 2)
    for tier, half in enumerate(halves):
        x0, x1 = 5 - half, 5 + half
        last = tier == len(halves) - 1
        t.fill(x0, y, x0, x1, y, x1, PLANKS)
        t.walls(x0, y + 1, x0, x1, y + 1, x1, paper)
        t.walls(x0, y + 2, x0, x1, y + 3, x1, SCREEN)
        ring_beam(t, x0, x0, x1, x1, y + 4)
        for x, z in ((x0, x0), (x1, x0), (x0, x1), (x1, x1)):
            t.fill(x, y + 1, z, x, y + 4, z, LOG)
        top = roof(t, x0 - 2, x0 - 2, x1 + 2, x1 + 2, y + 5, levels=None if last else 2)
        paper_lantern(t, x0 + 1, y + 3, x0 + 1)
        y += 5
    t.fill(5, 1, 5, 5, y - 1, 5, LOG)
    t.ladder(5, 1, y - 1, 6, 'south')
    t.set(5, top + 1, 5, 'end_rod[facing=up]')
    t.fill(4, 1, 2, 6, 3, 2, AIR)
    t.door('north', 5, WALKWAYS, frame=LOG)

    # Crossroads: an open court, a great stone lantern in its middle, four gates.
    t = court('sanctuary/crossroads', 11, 11, ground=paving, height=9)
    t.fill(4, 0, 4, 6, 0, 6, 'smooth_stone')
    t.fill(5, 1, 5, 5, 2, 5, STONE_WALL)
    t.set(5, 3, 5, LAMP)
    t.fill(4, 4, 4, 6, 4, 6, STONE_SLAB)
    t.set(5, 5, 5, STONE_SLAB)
    for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
        t.fill(x, 1, z, x, 2, z, LEAVES)
    doors(t, ('north', 'south', 'east', 'west'))

    pack.pool(WALKWAYS, [(P + 'sanctuary/walkway', 3), (P + 'sanctuary/walkway_lanterns', 3), (P + 'sanctuary/bridge', 2),
                         (P + 'sanctuary/torii_walk', 2), (P + 'sanctuary/steps', 2), (P + 'sanctuary/bamboo_walk', 2)],
              fallback=P + 'sanctuary/door_caps')
    pack.pool(P + 'sanctuary/door_caps', [(P + 'sanctuary/cap_door', 1)])
    pack.pool(GATE, [(P + 'sanctuary/gatehouse', 1)], fallback=P + 'sanctuary/door_caps')
    pack.pool(HALLS, [(P + 'sanctuary/meditation', 2), (P + 'sanctuary/tea_house', 2), (P + 'sanctuary/sand_court', 2), (P + 'sanctuary/koi_pond', 2),
                      (P + 'sanctuary/bell_court', 2), (P + 'sanctuary/library', 2), (P + 'sanctuary/workshop', 1), (P + 'sanctuary/gallery', 2),
                      (P + 'sanctuary/pagoda', 1), (P + 'sanctuary/crossroads', 3)], fallback=P + 'sanctuary/walkway_caps')
    pack.pool(P + 'sanctuary/walkway_caps', [(P + 'sanctuary/cap_walkway', 1)])
    pack.pool(OUTSIDE, [(P + 'sanctuary/torii_path', 3), (P + 'sanctuary/bamboo_grove', 2), (P + 'sanctuary/stone_garden', 2)])
    pack.pool(P + 'sanctuary/start', [(P + 'sanctuary/main_hall', 1)])
    woods = [P + b for b in ('origami_forest', 'zen_garden', 'golden_grove')]
    pack.jigsaw(P + 'sanctuary', P + 'sanctuary/start', woods, size=7, max_distance=100)

    # ------------------------------------------------------------------ the small ones

    # A small shrine: a little house on a stone base, a torii before it, lanterns, an offering box.
    def small_shrine(path, overgrown):
        t = pack.template(P + path, 9, 10, 13)
        t.fill(0, 0, 0, 8, 0, 12, garden)
        t.fill(0, 1, 0, 8, 9, 12, AIR)
        t.fill(3, 0, 0, 5, 0, 8, paving)
        torii(t, 4, 2, 3, 5)
        stone_lantern(t, 1, 1, 5)
        stone_lantern(t, 7, 1, 5)
        t.fill(2, 1, 8, 6, 1, 12, STONE)
        t.fill(3, 2, 9, 5, 2, 11, PLANKS)
        for x, z in ((3, 9), (5, 9), (3, 11), (5, 11)):
            t.fill(x, 3, z, x, 4, z, LOG)
        t.fill(4, 3, 10, 4, 3, 10, GLOW)
        t.fill(4, 3, 11, 4, 4, 11, paper)
        t.set(4, 4, 10, SCREEN)
        roof(t, 2, 8, 6, 12, 5, ceiling=PLANKS)
        t.stairs(4, 2, 8, DARK_STEP, 'south')
        t.set(3, 2, 8, CANDLE)
        if overgrown:
            t.fill(0, 4, 2, 8, 6, 2, AIR)                       # the torii's lintel fallen
            t.set(5, 1, 3, RED)
            t.set(6, 1, 3, BLACK)
            t.set(7, 1, 3, BLACK)
            t.set(7, 2, 5, AIR)
            t.set(7, 3, 5, AIR)
            t.set(8, 1, 6, STONE_SLAB)
            t.fill(2, 5, 8, 6, 7, 12, MOSS, only=(u.norm(SHINGLES),), chance=0.6)
            t.scatter(0, 1, 0, 8, 1, 12, mossy, 10)
            for x, z in ((1, 11), (7, 10), (0, 8)):
                t.fill(x, 1, z, x, 2, z, LEAVES)
        else:
            t.set(4, 1, 7, 'barrel[facing=up,open=false]')
        return t

    small_shrine('sites/shrine', False)
    small_shrine('sites/shrine_overgrown', True)

    # A meditation pavilion: an open pavilion on a deck by a pond, cushions, a lantern.
    def pavilion(path, ruined):
        t = pack.template(P + path, 13, 10, 13)
        t.fill(0, 0, 0, 12, 0, 12, garden)
        t.fill(0, 1, 0, 12, 9, 12, AIR)
        t.disc(9, 0, 9, 3.5, WATER)
        t.disc(9, 0, 9, 3.5, 'mossy_cobblestone', ring=True)
        t.set(9, 1, 8, LILY)
        t.set(10, 1, 10, LILY)
        t.fill(1, 0, 1, 7, 0, 7, PLANKS)
        for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
            t.fill(x, 1, z, x, 4, z, LOG)
        t.walls(1, 4, 1, 7, 4, 7, beam('x'))
        for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
            t.set(x, 4, z, LOG)
        roof(t, 0, 0, 8, 8, 5)
        for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)):
            t.set(x, 1, z, 'white_carpet')
        paper_lantern(t, 4, 3, 4)
        stone_lantern(t, 11, 1, 2)
        t.fill(4, 0, 8, 4, 0, 12, 'smooth_stone')
        if ruined:
            t.fill(0, 5, 0, 4, 9, 8, AIR, only=None, chance=0.7)
            t.fill(1, 2, 1, 1, 4, 1, AIR)
            t.fill(1, 1, 2, 3, 1, 2, beam('x'))
            t.scatter(0, 1, 0, 12, 1, 12, mossy, 10)
            t.fill(5, 5, 0, 8, 9, 8, MOSS, only=(u.norm(SHINGLES),))
            t.set(11, 3, 2, AIR)
        return t

    pavilion('sites/pavilion', False)
    pavilion('sites/pavilion_ruined', True)

    # A wooden tower: three floors on posts, ladders, a drum and lanterns at the top under its roof.
    def tower(path, broken):
        t = pack.template(P + path, 9, 22, 9)
        t.fill(0, 0, 0, 8, 0, 8, garden)
        t.fill(1, 0, 1, 7, 0, 7, paving)
        t.fill(0, 1, 0, 8, 21, 8, AIR)
        for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
            t.fill(x, 1, z, x, 15, z, LOG)
        for y in (5, 10, 15):
            t.fill(2, y, 2, 6, y, 6, PLANKS)
            t.set(4, y, 3, AIR)
            for x in range(2, 7):
                for z in range(2, 7):
                    if (x in (2, 6) or z in (2, 6)) and t.get(x, y + 1, z) in (None, AIR):
                        t.set(x, y + 1, z, FENCE)
        t.ladder(4, 1, 15, 3, 'south')
        t.fill(4, 1, 2, 4, 15, 2, PLANKS)
        t.set(4, 16, 2, LOG)
        for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
            t.fill(x, 16, z, x, 17, z, LOG)
        roof(t, 1, 1, 7, 7, 18)
        t.set(4, 16, 5, 'note_block[instrument=basedrum,note=0,powered=false]')
        paper_lantern(t, 3, 17, 5, drop=0)
        paper_lantern(t, 5, 17, 5, drop=0)
        t.set(2, 1, 4, 'barrel[facing=up,open=false]')
        if broken:
            t.fill(0, 16, 0, 8, 21, 8, AIR)
            t.fill(6, 11, 2, 6, 15, 6, AIR)
            t.fill(5, 15, 2, 6, 15, 6, AIR)
            for x, z in ((7, 7), (8, 5), (7, 1), (0, 7)):
                t.set(x, 1, z, beam('z') if x > 6 else beam('x'))
            t.scatter(0, 1, 0, 8, 1, 8, mossy, 6)
            t.set(8, 1, 3, SHINGLES)
            t.set(8, 1, 2, SHINGLES)
        return t

    tower('sites/tower', False)
    tower('sites/tower_broken', True)

    # A garden bridge: a red arched bridge over a stream, lanterns at its ends, maples of blossom.
    def garden_bridge(path, stone):
        t = pack.template(P + path, 15, 8, 11)
        t.fill(0, 0, 0, 14, 0, 10, garden)
        t.fill(0, 1, 0, 14, 7, 10, AIR)
        t.fill(4, 0, 0, 10, 0, 10, WATER)
        t.fill(4, 0, 0, 4, 0, 10, 'mossy_cobblestone')
        t.fill(10, 0, 0, 10, 0, 10, 'mossy_cobblestone')
        for x, z in ((6, 2), (8, 8), (7, 1)):
            t.set(x, 1, z, LILY)
        deck = STONE_SLAB if stone else DARK_SLAB
        rail = 'stone_brick_wall' if stone else 'red_nether_brick_wall'
        full = STONE if stone else PLANKS
        rails = u.Mix((rail, 3), ('mossy_stone_brick_wall', 1)) if stone else rail
        profile = {2: (0, full), 3: (0, full), 4: (1, deck), 5: (1, full), 6: (2, deck), 7: (2, deck), 8: (2, deck), 9: (1, full),
                   10: (1, deck), 11: (0, full), 12: (0, full)}
        for x, (y, block) in profile.items():
            for z in (4, 5, 6):
                t.set(x, y, z, block)
            t.set(x, y + 1, 3, rails)
            t.set(x, y + 1, 7, rails)
        for x in (2, 12):
            stone_lantern(t, x, 1, 2)
            stone_lantern(t, x, 1, 8)
        t.fill(0, 1, 0, 1, 3, 1, CHERRY)
        t.fill(13, 1, 9, 14, 3, 10, CHERRY)
        t.fill(0, 0, 4, 1, 0, 6, paving)
        t.fill(13, 0, 4, 14, 0, 6, paving)
        if stone:
            for x in (5, 9):
                t.set(x, 3, 3, AIR)
            t.scatter(0, 1, 0, 14, 1, 10, mossy, 8)
        return t

    garden_bridge('sites/bridge', False)
    garden_bridge('sites/bridge_stone', True)

    everywhere = [P + b for b in ('blade_valley', 'zen_garden', 'golden_grove', 'origami_forest', 'misty_forest')]
    for name, variants, biomes in (
            ('shrine', ('shrine', 'shrine_overgrown'), everywhere),
            ('pavilion', ('pavilion', 'pavilion_ruined'), everywhere),
            ('tower', ('tower', 'tower_broken'), everywhere),
            ('bridge', ('bridge', 'bridge_stone'), everywhere)):
        pack.pool(P + 'sites/' + name, [(P + 'sites/' + v, 1) for v in variants])
        pack.jigsaw(P + name, P + 'sites/' + name, biomes, size=1, max_distance=32)

    # The sanctuary: one try per 36 chunks (576 blocks); the small sites: one per 11 chunks (176 blocks), away from it.
    pack.structure_set(P + 'landmark', [(P + 'sanctuary', 1)], spacing=36, separation=14, salt=1237785443)
    pack.structure_set(P + 'sites', [(P + 'shrine', 3), (P + 'pavilion', 3), (P + 'tower', 2), (P + 'bridge', 3)],
                       spacing=11, separation=5, salt=591130087, exclusion=(P + 'landmark', 8))
