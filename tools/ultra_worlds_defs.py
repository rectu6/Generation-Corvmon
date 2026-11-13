"""
The seven Ultra Space worlds and their 35 biomes (phase 4.2), for tools/ultra_worlds.py.

Each biome: where it is (climate box, first match wins, the last takes the rest), its
colours, its surface, its relief (in blocks, masked to the biome), its features. Blocks
are vanilla and Generations mixed, chosen by look and role; no new block.

Scenery is no mine (phase 4.3.2): the big formations (spikes, geodes, veins of light,
patches) are made of blocks that give nothing precious back: glass and stained glass
(nothing without Silk Touch), packed ice, prismarine, terracotta, shroomlight, the
mod's glowing charge stone, a little crystal_light for the light (it cannot be turned
back into crystals). No copper block, no crystal_block / ultra_crystal / amethyst
block, no froglight or sea lantern in bulk. The real ores come from
ultra_resources.json (UltraResourcesConfig), measured.
"""


def worlds(u):
    g = u.gen
    out = [jungle(u, g), desert(u, g), central(u, g), crater(u, g), forest(u, g), abyss(u, g), skyscraper(u, g)]
    return out


def ultra_flowers(g):
    names = ['calil', 'dichro_eulb', 'muilla', 'noilednad', 'pilut_der', 'pilut_egnaro', 'pilut_etihw', 'pilut_knip',
             'rewolfnroc', 'yellav_eht_fo_ylil', 'yppop', 'ysiad_eyexo']
    return [(g(n), 1) for n in names if n != 'calil']


def ballonlea(g):
    colours = ['blue', 'green', 'pink', 'yellow']
    out = [(g(f'ballonlea_{c}_mushroom'), 3) for c in colours]
    out += [(g(f'group_ballonlea_{c}_mushroom'), 2) for c in colours]
    out += [(g(f'tall_ballonlea_{c}_mushroom'), 1) for c in colours]
    return out


# ------------------------------------------------------------------ 1. Ultra-Jungle (Buzzwole)

def jungle(u, g):
    w = u.World('ultra_jungle', 'Ultra-Jungle', 'Ultra Jungle', sea=63, offset_shift=6, region_scale=1.6)
    V = u.vanilla
    swap_logs = {'minecraft:jungle_log': g('ultra_jungle_log')}

    giant = V('configured_feature/mega_jungle_tree')
    giant = u.swap(giant, swap_logs)
    giant['config']['trunk_placer'].update({'base_height': 18, 'height_rand_a': 3, 'height_rand_b': 12})
    giant['config']['foliage_placer'].update({'radius': 3})
    f_giant = w.feature('giant_tree', giant, u.trees_at(2, 'jungle_sapling'))
    f_tree = w.feature('jungle_tree', u.swap(V('configured_feature/jungle_tree'), swap_logs), u.trees_at(6, 'jungle_sapling'))
    f_bush = w.feature('jungle_bush', u.swap(V('configured_feature/jungle_bush'), swap_logs), u.trees_at(5, 'jungle_sapling'))
    f_grass = 'minecraft:patch_grass_jungle'
    f_flowers = w.feature('ultra_flowers', u.plants(u.weighted(*ultra_flowers(g)), 24, 6, 2), [u.count(3), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])
    f_vines = 'minecraft:vines'

    mangrove = u.swap(V('configured_feature/mangrove'), {'minecraft:mangrove_log': g('ultra_jungle_log')})
    f_mangrove = w.feature('swamp_tree', mangrove, [u.count(7), u.SQUARE, {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 4},
                                                    u.heightmap('OCEAN_FLOOR'), u.BIOME, u.survives('mangrove_propagule')])
    f_lily = 'minecraft:patch_waterlily'
    f_clay = 'minecraft:disk_clay'
    f_seagrass = 'minecraft:seagrass_swamp'
    f_springs = w.feature('springs', 'minecraft:spring_water', [u.count(40), u.SQUARE, u.height_range(60, 200), u.BIOME])

    fungus = u.cf('huge_fungus', {'decor_state': u.st('shroomlight'), 'hat_state': u.st('warped_wart_block'),
                                  'stem_state': u.log(g('ultra_dark_log')), 'valid_base_block': u.st('moss_block'), 'planted': False,
                                  'replaceable_blocks': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:short_grass', 'minecraft:fern', 'minecraft:moss_carpet']}})
    f_fungus = w.feature('glowing_fungus', fungus, u.on_surface(2, 'MOTION_BLOCKING_NO_LEAVES'))
    f_ballonlea = w.feature('ballonlea', u.patch(u.weighted(*ballonlea(g)), 20, 6, 2), u.on_surface(5, 'MOTION_BLOCKING_NO_LEAVES'))
    f_froglights = w.feature('glow_spikes', u.crystal(u.weighted(('shroomlight', 3), ('lime_stained_glass', 2), ('yellow_stained_glass', 2), ('green_stained_glass', 1)),
                                                      'shroomlight', u.uniform(2, 5), u.uniform(0, 1), u.uniform(1, 2), 0.3),
                             [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING_NO_LEAVES'), u.BIOME])
    f_glow_tree = w.feature('glowing_tree', u.swap(V('configured_feature/jungle_tree'), {'minecraft:jungle_log': g('ultra_dark_log')}),
                            u.trees_at(3, 'jungle_sapling'))
    f_moss = w.feature('moss', 'minecraft:moss_patch', u.on_surface(2, 'MOTION_BLOCKING_NO_LEAVES'))
    f_spores = 'minecraft:spore_blossom'

    dark = u.swap(V('configured_feature/dark_oak'), {'minecraft:dark_oak_log': g('ultra_dark_log')})
    f_dark = w.feature('ancient_tree', dark, [u.u_noise_count(), u.SQUARE, u.dry(), u.heightmap('OCEAN_FLOOR'), u.BIOME, u.survives('dark_oak_sapling')])
    twisted_tree = u.swap(V('configured_feature/azalea_tree'), {'minecraft:oak_log': g('ultra_jungle_log')})
    f_twisted = w.feature('twisted_tree', twisted_tree, u.trees_at(3, 'oak_sapling'))
    rocks = []
    for name in ('mossy_cobblestone', g('mossy_charge_cobblestone'), g('mossy_volcanic_cobblestone'), g('ultra_cobblestone')):
        rocks.append(w.feature('rock_' + name.split(':')[-1], u.rock(name), [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME]))

    sky, fog = '#4FD6A0', '#8FE8C0'
    colours = lambda water, foliage, grass='#2ED36A': (sky, fog, water, '#0B3D2A', grass, foliage)
    w.biomes = [
        u.Biome('mountain_jungle', 'Jungle montagneuse', 'Mountain jungle', {'erosion': (-2.0, -0.22), 'continentalness': (-0.2, 2.0)},
                colours('#2BB673', '#1FB98A'),
                u.surface('grass_block', 'dirt', 'gravel', steep_block='stone', patches=[(0.5, 'moss_block', 'minecraft:surface')]),
                {'fluid_springs': [f_springs], 'vegetal_decoration': [f_tree, f_giant, f_bush, f_grass, f_vines, f_flowers]},
                carvers=['cave', 'cave_extra_underground', 'canyon'], relief=[('raise', 18), ('ridges', 24, 1.1)], factor=0.65,
                tags=['forested', 'mountainous']),
        u.Biome('swamp_jungle', 'Jungle marécageuse', 'Swamp jungle', {'continentalness': (-2.0, -0.28), 'erosion': (-0.15, 2.0)},
                colours('#3F8F5A', '#2D9F6A', '#3BB55C'),
                u.surface('grass_block', 'dirt', 'mud', patches=[(0.15, 'mud', 'minecraft:surface'), (0.55, 'clay', 'minecraft:surface')]),
                {'underground_ores': [f_clay], 'vegetal_decoration': [f_mangrove, f_lily, f_seagrass, f_grass, f_flowers, f_vines]},
                flatten=(1, 0.8), relief=[('noise', 4, 3.0)], tags=['forested', 'watery']),
        u.Biome('glowing_jungle', 'Jungle bioluminescente', 'Glowing jungle', {'weirdness': (0.45, 2.0), 'temperature': (-0.1, 2.0)},
                ('#1E4A5A', '#3AD6E0', '#30E0C0', '#0A2E3A', '#1FB8A0', '#16C8C8'),
                u.surface('moss_block', 'rooted_dirt', 'clay', patches=[(0.4, 'mycelium', 'minecraft:surface')]),
                {'local_modifications': [f_moss], 'vegetal_decoration': [f_glow_tree, f_fungus, f_froglights, f_ballonlea, f_spores, f_flowers]},
                relief=[('noise', 8, 1.2)], particle=('spore_blossom_air', 0.006), tags=['forested', 'luminous', 'rare']),
        u.Biome('overgrown_jungle', 'Jungle des ruines naturelles', 'Overgrown jungle', {'humidity': (-2.0, -0.15)},
                colours('#2BB673', '#4DAA60', '#4CAF50'),
                u.surface('grass_block', 'dirt', 'gravel', steep_block='mossy_cobblestone',
                          patches=[(0.2, 'podzol', 'minecraft:surface'), (0.55, 'coarse_dirt', 'minecraft:surface')]),
                {'local_modifications': rocks + [f_moss], 'vegetal_decoration': [f_dark, f_twisted, f_bush, f_grass, f_vines, f_flowers]},
                relief=[('noise', 14, 1.4)], tags=['forested']),
        u.Biome('giant_jungle', 'Jungle géante', 'Giant jungle', {},
                colours('#2BB673', '#1FD6A0'),
                u.surface('grass_block', 'dirt', 'gravel', steep_block='moss_block', patches=[(0.25, 'podzol', 'minecraft:surface')]),
                {'vegetal_decoration': [f_giant, f_tree, f_bush, f_grass, f_vines, f_flowers]},
                carvers=['cave'], relief=[('noise', 10, 0.9)], tags=['forested']),
    ]
    return w


# ------------------------------------------------------------------ 2. Ultra-Désert (Pheromosa)

def strata(u, g, bottom, layers):
    """Bands of rock by height from `bottom` up, each (block, thickness): cliffs show them."""
    rules = []
    y = bottom
    for name, thickness in layers:
        y += thickness
    for name, thickness in reversed(layers):
        y -= thickness
        rules.append(u.cond(u.y_above(y), u.block(name)))
    return u.cond(u.y_above(bottom), u.seq(*rules))


def desert(u, g):
    w = u.World('ultra_desert', 'Ultra-Désert', 'Ultra Desert', sea=50, offset_shift=8, default_block=g('ultra_sandstone'), time=6000)
    V = u.vanilla
    f_rock_white = w.feature('rock_white', u.rock(g('ultra_sandstone')), [u.rarity(3), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])
    f_dead = w.feature('dead_bush', u.plants('dead_bush', 6, 6, 2), u.on_surface(1))
    crystals = u.weighted(('glass', 3), ('white_stained_glass', 2), ('light_blue_stained_glass', 2), ('magenta_stained_glass', 1),
                          (g('mirror_glass'), 1), (g('crystal_light'), 1))
    f_crystals = w.feature('crystals', u.crystal(crystals, u.weighted(('white_stained_glass', 4), (g('crystal_light'), 1)),
                                                 u.uniform(3, 10), u.uniform(0, 2), u.uniform(1, 4), 0.4),
                           u.on_surface(3, 'MOTION_BLOCKING'))
    # A geode of glass: no amethyst block, no cluster (the small buds give nothing).
    geode = u.swap(V('configured_feature/amethyst_geode'), {'minecraft:amethyst_block': 'minecraft:light_blue_stained_glass',
                                                            'minecraft:budding_amethyst': g('crystal_light'),
                                                            'minecraft:smooth_basalt': g('ultra_sandstone'),
                                                            'minecraft:amethyst_cluster': 'minecraft:small_amethyst_bud',
                                                            'minecraft:large_amethyst_bud': 'minecraft:small_amethyst_bud',
                                                            'minecraft:medium_amethyst_bud': 'minecraft:small_amethyst_bud'})
    f_geode = w.feature('crystal_geode', geode, [u.rarity(5), u.SQUARE, u.height_range(10, 70), u.BIOME])
    f_rock_tan = w.feature('rock_tan', u.rock(g('rock')), [u.count(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])
    palm = u.tree({'type': 'minecraft:straight_trunk_placer', 'base_height': 6, 'height_rand_a': 3, 'height_rand_b': 0}, u.log('jungle_log'),
                  {'type': 'minecraft:acacia_foliage_placer', 'radius': 3, 'offset': 0}, u.leaves('jungle_leaves'))
    f_palm = w.feature('palm', palm, u.trees_at(2, 'jungle_sapling'))
    f_cane = 'minecraft:patch_sugar_cane'
    f_lily = 'minecraft:patch_waterlily'
    f_oasis_flowers = w.feature('oasis_flowers', u.plants(u.weighted(*ultra_flowers(g)), 24, 6, 2), u.on_surface(4, 'MOTION_BLOCKING'))
    f_oasis_grass = w.feature('oasis_grass', u.plants(u.weighted(('short_grass', 4), ('fern', 1)), 32, 7, 3), u.on_surface(8))
    glass = u.weighted(('glass', 3), ('tinted_glass', 1), ('white_stained_glass', 1), ('light_blue_stained_glass', 1))
    f_glass = w.feature('glass_spikes', u.crystal(glass, 'smooth_quartz', u.uniform(4, 12), u.uniform(0, 1), u.uniform(1, 3), 0.25),
                        [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])

    sky, fog = '#FFB878', '#FFE0B8'
    col = lambda water='#D9883A', grass=None, foliage=None: (sky, fog, water, '#50300F', grass, foliage)
    canyon_layers = [(g('ultra_sandstone'), 5), (g('rock'), 3), ('white_terracotta', 4), (g('bleach_stone'), 3), ('calcite', 2),
                     (g('golden_temple_sandstone'), 2), (g('ultra_sandstone'), 6), ('light_gray_terracotta', 2), (g('rock'), 4),
                     ('white_terracotta', 3), (g('normal_sandstone'), 3), (g('ultra_sandstone'), 4)] * 3
    w.biomes = [
        u.Biome('ivory_canyon', "Canyon d'ivoire", 'Ivory canyon', {'erosion': (-2.0, -0.2), 'continentalness': (-0.1, 2.0)},
                col(), u.surface(g('ultra_sand'), g('ultra_sandstone'), deep=strata(u, g, 50, canyon_layers)),
                {'vegetal_decoration': [f_rock_tan, f_dead]},
                carvers=['canyon'], relief=[('raise', 26), ('valley', 46, 0.3)], factor=1.6, tags=['arid', 'mountainous']),
        u.Biome('oasis', 'Oasis extraterrestre', 'Alien oasis', {'continentalness': (-2.0, -0.35), 'humidity': (-0.1, 2.0)},
                col('#36C8D8', '#4FD86A', '#3FC85A'),
                u.surface('grass_block', g('rich_soil_1'), g('golden_temple_sand'), patches=[(0.3, g('rich_soil_2'), 'minecraft:surface'),
                                                                                            (0.6, g('golden_temple_sand'), 'minecraft:surface')]),
                {'vegetal_decoration': [f_palm, f_cane, f_lily, f_oasis_grass, f_oasis_flowers]},
                flatten=(-18, 0.85), relief=[('noise', 10, 3.0)], tags=['watery', 'rare']),
        u.Biome('glass_desert', 'Désert de verre', 'Glass desert', {'temperature': (0.35, 2.0), 'humidity': (-2.0, 0.0)},
                col(), u.surface(g('ultra_smooth_sandstone'), g('ultra_sandstone'), steep_block='smooth_quartz',
                                 patches=[(0.25, g('mirrored_floor_2'), 'minecraft:surface'), (0.5, g('mirrored_floor_1'), 'minecraft:surface'),
                                          (0.75, g('mirrored_floor_3'), 'minecraft:surface')]),
                {'vegetal_decoration': [f_glass, f_rock_white]},
                relief=[('dunes', 7, 1.6)], tags=['arid', 'crystalline', 'rare']),
        u.Biome('crystal_desert', 'Désert cristallin', 'Crystal desert', {'temperature': (0.15, 2.0), 'humidity': (-0.05, 2.0)},
                col(), u.surface(g('ultra_sand'), g('ultra_sandstone'), steep_block='calcite', patches=[(0.4, 'calcite', 'minecraft:calcite')]),
                {'underground_structures': [f_geode], 'vegetal_decoration': [f_crystals, f_dead]},
                relief=[('ridges', 14, 2.2)], factor=0.8, tags=['arid', 'crystalline']),
        u.Biome('white_dunes', 'Désert blanc', 'White dunes', {},
                col(), u.surface(g('ultra_sand'), g('ultra_sandstone'), patches=[(0.65, g('ultra_smooth_sandstone'), 'minecraft:surface')]),
                {'vegetal_decoration': [f_rock_white, f_dead]},
                relief=[('dunes', 16, 1.2)], tags=['arid']),
    ]
    return w


# ------------------------------------------------------------------ 3. Ultra-Centrale (Xurkitree)

def central(u, g):
    w = u.World('ultra_central', 'Ultra-Centrale', 'Ultra Plant', sea=35, offset_shift=8, default_block=g('charge_stone'), time=18000, light=0.08)
    sparks = u.weighted((g('glowing_charge_cobblestone'), 2), (g('crystal_light'), 1))
    f_small = w.feature('small_crystals', u.crystal(sparks, g('glowing_charge_cobblestone'), u.uniform(1, 4), 0, u.uniform(1, 3), 0.3), u.on_surface(2, 'MOTION_BLOCKING'))
    f_cables = w.feature('cables', u.twisted(u.weighted((g('charge_dripstone_block'), 2), (g('smooth_charge_stone'), 1)), g('glowing_charge_cobblestone'),
                                             u.uniform(8, 20), u.uniform(2, 3), 1.5, 0.35), u.on_surface(2, 'MOTION_BLOCKING'))
    f_veins = w.feature('glowing_veins', u.ore(g('glowing_charge_cobblestone'), [g('charge_stone'), g('charge_cobblestone')], 12), [u.count(10), u.SQUARE, u.height_range(50, 220), u.BIOME])
    big = u.weighted(('yellow_stained_glass', 3), ('cyan_stained_glass', 2), ('glass', 2), (g('glowing_charge_cobblestone'), 2), (g('crystal_light'), 1))
    f_field = w.feature('crystal_field', u.crystal(big, g('glowing_charge_cobblestone'), u.uniform(3, 14), u.uniform(0, 2), u.uniform(2, 5), 0.45), u.on_surface(4, 'MOTION_BLOCKING'))
    f_columns = w.feature('magnetic_columns', u.twisted('polished_basalt', g('glowing_charge_cobblestone'), u.uniform(10, 24), u.uniform(1, 2), 0.7, 0.15),
                          u.on_surface(1, 'MOTION_BLOCKING'))
    f_rock = w.feature('rock_charge', u.rock(g('charge_cobblestone')), [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])
    # Verdigris stains, without a block of copper (a copper block is nine ingots: Poké Balls).
    f_copper = w.feature('verdigris_patches', u.disk(u.weighted(('dark_prismarine', 2), ('cyan_terracotta', 1)), [g('smooth_charge_stone'), g('charge_stone')], (1, 3), 1),
                         [u.rarity(6), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])

    col = lambda: ('#2A2A10', '#E8D040', '#C8B830', '#30300A', None, None)
    w.biomes = [
        u.Biome('electric_mountains', 'Montagnes électriques', 'Electric mountains', {'erosion': (-2.0, -0.25), 'continentalness': (-0.2, 2.0)},
                col(), u.surface(g('charge_stone'), g('charge_stone'), 'gravel', steep_block=g('charge_cobblestone'),
                                 patches=[(0.6, 'smooth_basalt', 'minecraft:surface')]),
                {'underground_ores': [f_veins], 'vegetal_decoration': [f_small]},
                carvers=['cave', 'canyon'], relief=[('raise', 16), ('ridges', 34, 1.0)], factor=0.7, tags=['electric', 'mountainous']),
        u.Biome('magnetic_valley', 'Vallée magnétique', 'Magnetic valley', {'weirdness': (-0.1, 0.1)},
                col(), u.surface('smooth_basalt', 'basalt', 'gravel', steep_block='polished_basalt', patches=[(0.3, g('charge_cobblestone'), 'minecraft:surface')]),
                {'vegetal_decoration': [f_columns, f_rock]},
                relief=[('valley', 30, 0.2)], factor=0.8, tags=['electric']),
        u.Biome('crystal_fields', 'Champs de cristaux', 'Crystal fields', {'temperature': (0.38, 2.0), 'humidity': (-0.1, 2.0)},
                col(), u.surface(g('smooth_charge_stone'), g('charge_stone'), 'gravel', patches=[(0.0, 'calcite', 'minecraft:calcite')]),
                {'vegetal_decoration': [f_field, f_small]},
                relief=[('dunes', 6, 2.0)], particle=('electric_spark', 0.004), tags=['electric', 'crystalline', 'luminous', 'rare']),
        u.Biome('cable_forest', 'Forêt de câbles', 'Cable forest', {'humidity': (0.12, 2.0)},
                col(), u.surface(g('charge_stone'), g('charge_stone'), 'gravel', steep_block='deepslate',
                                 patches=[(0.2, g('mossy_charge_cobblestone'), 'minecraft:surface')]),
                {'vegetal_decoration': [f_cables, f_rock]},
                relief=[('noise', 10, 1.6)], tags=['electric']),
        u.Biome('conductive_plains', 'Plaines conductrices', 'Conductive plains', {},
                col(), u.surface(g('smooth_charge_stone'), g('charge_stone'), 'gravel',
                                 patches=[(0.35, g('charge_cobblestone'), 'minecraft:surface'), (0.75, g('glowing_charge_cobblestone'), 'minecraft:surface')]),
                {'local_modifications': [f_copper], 'vegetal_decoration': [f_small]},
                flatten=(5, 0.5), particle=('electric_spark', 0.002), tags=['electric']),
    ]
    return w


# ------------------------------------------------------------------ 4. Ultra-Cratère (Celesteela)
# Yellow grass under an orange sky, craters lined with green moss, red spires, black
# arches, steam rising from the vents.

GIANT_FLOOR = 48  # below it, a giant crater's floor (brown earth, embers); a ring of moss above


def crater(u, g):
    w = u.World('ultra_crater', 'Ultra-Cratère', 'Ultra Crater', sea=20, offset_shift=4, default_block='tuff')
    yellow_rim = u.weighted(('grass_block', 3), ('yellow_terracotta', 1))
    green = u.crater(u.uniform(3, 10), 0.5, 'moss_block', yellow_rim, 'coarse_dirt', 2)
    f_craters = w.feature('green_craters', green, [u.count(2), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_craters_few = w.feature('green_craters_few', green, [u.rarity(3), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    spire = u.weighted(('terracotta', 3), ('red_terracotta', 3), ('brown_terracotta', 2), ('granite', 1))
    big_spire = u.crystal(spire, 'red_terracotta', u.uniform(8, 24), u.uniform(2, 3), 1, 0.08)
    f_spires = w.feature('red_spires', big_spire, [u.rarity(4), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_spires_dense = w.feature('red_spires_dense', big_spire, [u.count(2), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_spirelets = w.feature('small_spires', u.crystal(spire, 'terracotta', u.uniform(3, 8), u.uniform(0, 1), u.uniform(1, 3), 0.15),
                            [u.rarity(2), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    arch = u.twisted(u.weighted(('blackstone', 3), ('polished_blackstone', 1), ('smooth_basalt', 1)), 'polished_blackstone', u.uniform(4, 7), 1, 0.25, 1.0)
    f_arches = w.feature('black_arches', arch, [u.rarity(4), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    vent = u.vent(u.weighted(('granite', 2), ('brown_terracotta', 1), ('tuff', 1)),
                  u.weighted(('coarse_dirt', 2), ('packed_mud', 1), ('brown_terracotta', 1)), u.uniform(1, 3))
    f_vents_few = w.feature('steam_vents_few', vent, [u.rarity(7), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_vents = w.feature('steam_vents', vent, [u.count(2), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_embers = w.feature('embers', u.disk('magma_block', ['coarse_dirt', 'rooted_dirt', 'packed_mud'], (0, 1), 1),
                         [u.count(5), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_grass = w.feature('yellow_grass', u.plants(u.weighted(('short_grass', 6), ('fern', 1)), 32, 7, 3), u.on_surface(4))
    f_dry = w.feature('dry_bushes', u.plants('dead_bush', 6, 6, 2), u.on_surface(1))
    f_boulders = w.feature('boulders', u.rock('granite'), [u.rarity(6), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])

    sky = '#F4BC6A'
    col = lambda fog='#EBA65C', grass='#D8B640': (sky, fog, '#7FC06A', '#30502A', grass, '#C9A23A')
    plateau_layers = [('yellow_terracotta', 4), ('orange_terracotta', 3), ('terracotta', 2), ('yellow_terracotta', 5), ('white_terracotta', 2),
                      ('orange_terracotta', 4), ('red_terracotta', 2), ('yellow_terracotta', 4), ('brown_terracotta', 2)] * 4
    giant_floor = [
        u.cond(u.neg(u.y_above(GIANT_FLOOR)), u.seq(u.cond(u.noise_between(0.35, noise='minecraft:surface'), u.block('rooted_dirt')),
                                                     u.cond(u.noise_between(-0.1, noise='minecraft:surface'), u.block('packed_mud')),
                                                     u.block('coarse_dirt'))),
        u.cond(u.neg(u.y_above(GIANT_FLOOR + 6)), u.block("moss_block")),
    ]
    w.biomes = [
        u.Biome('yellow_plateaus', 'Plateaux jaunes', 'Yellow plateaus', {'erosion': (-2.0, -0.28), 'continentalness': (-0.2, 2.0)},
                col(), u.surface('grass_block', 'yellow_terracotta', 'gravel', first=[u.cond(u.steep(), strata(u, g, 40, plateau_layers))],
                                 patches=[(0.55, 'coarse_dirt', 'minecraft:surface')], deep=strata(u, g, 40, plateau_layers)),
                {'local_modifications': [f_craters], 'vegetal_decoration': [f_arches, f_spirelets, f_grass]},
                carvers=['cave'], relief=[('raise', 30), ('ridges', 14, 1.2)], factor=0.55, tags=['arid', 'mountainous']),
        u.Biome('steam_fields', 'Champs de fumerolles', 'Steam fields', {'temperature': (0.45, 2.0)},
                col('#D89A5A', '#B89A40'),
                u.surface('coarse_dirt', 'brown_terracotta', 'gravel', steep_block='granite',
                          patches=[(-0.3, 'packed_mud', 'minecraft:surface'), (0.15, 'rooted_dirt', 'minecraft:surface'),
                                   (0.45, 'grass_block', 'minecraft:surface'), (0.75, 'granite', 'minecraft:surface')]),
                {'vegetal_decoration': [f_vents, f_spirelets, f_boulders, f_dry, f_grass]},
                carvers=['cave'], relief=[('noise', 6, 2.0)], flatten=(8, 0.3), particle=('white_ash', 0.015), tags=['volcanic']),
        u.Biome('giant_craters', 'Grand cratère', 'Giant crater', {'temperature': (0.0, 0.45), 'humidity': (-2.0, 0.0)},
                col(), u.surface('grass_block', 'yellow_terracotta', 'gravel', steep_block='yellow_terracotta', first=giant_floor,
                                 patches=[(0.6, 'coarse_dirt', 'minecraft:surface')]),
                {'local_modifications': [f_craters_few, f_embers], 'vegetal_decoration': [f_arches, f_vents_few, f_grass]},
                relief=[('craters', 224, 40.0, 96.0, 42.0, 14.0, 0.8)], tags=['barren']),
        u.Biome('spire_forest', 'Forêt de flèches', 'Spire forest', {'weirdness': (-0.15, 0.15)},
                col(), u.surface('grass_block', 'yellow_terracotta', 'gravel', steep_block='terracotta',
                                 patches=[(0.3, 'coarse_dirt', 'minecraft:surface'), (0.6, 'brown_terracotta', 'minecraft:surface')]),
                {'local_modifications': [f_craters_few], 'vegetal_decoration': [f_spires_dense, f_spirelets, f_vents_few, f_arches, f_grass]},
                relief=[('noise', 6, 2.0)], tags=['arid', 'mountainous']),
        u.Biome('crater_plains', 'Plaines aux cratères', 'Crater plains', {},
                col(), u.surface('grass_block', 'yellow_terracotta', 'gravel', steep_block='yellow_terracotta',
                                 patches=[(-0.035, 0.035, 'moss_block', 'minecraft:gravel'), (0.62, 'coarse_dirt', 'minecraft:surface')]),
                {'local_modifications': [f_craters], 'vegetal_decoration': [f_spires, f_arches, f_vents_few, f_boulders, f_grass]},
                relief=[('noise', 5, 1.0)], flatten=(6, 0.3), tags=['arid']),
    ]
    return w


# ------------------------------------------------------------------ 5. Ultra-Forêt (Kartana)
# A deep green, misty forest: zen gardens of raked sand round mossy pointed rocks,
# trees of golden blossoms, paper-white origami trees.

def forest(u, g):
    w = u.World('ultra_forest', 'Ultra-Forêt', 'Ultra Forest', sea=63, offset_scale=0.75, offset_shift=6)
    V = u.vanilla
    blob = lambda r, h: {'type': 'minecraft:blob_foliage_placer', 'radius': r, 'offset': 0, 'height': h}
    pine = {'type': 'minecraft:pine_foliage_placer', 'radius': 1, 'offset': 1, 'height': u.uniform(3, 4)}
    straight = lambda h, a: {'type': 'minecraft:straight_trunk_placer', 'base_height': h, 'height_rand_a': a, 'height_rand_b': 0}
    origami_a = u.tree(straight(5, 2), u.log('birch_log'), blob(2, 3), u.leaves('oak_leaves'))
    origami_b = u.tree(straight(6, 3), u.log(g('ultra_dark_log')), pine, u.leaves('oak_leaves'),
                       min_size={'type': 'minecraft:two_layers_feature_size', 'limit': 2, 'lower_size': 0, 'upper_size': 2})
    f_origami = w.feature('origami_trees', u.selector((origami_a, 0.5), default=origami_b), u.trees_at(7, 'oak_sapling'))
    paper = u.weighted(('calcite', 2), ('smooth_quartz', 1), ('white_concrete', 1))
    f_folds = w.feature('paper_folds', u.crystal(paper, 'smooth_quartz', u.uniform(2, 6), u.uniform(0, 1), u.uniform(1, 2), 0.6),
                        [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])
    f_flowers = w.feature('ultra_flowers', u.plants(u.weighted(*ultra_flowers(g)), 24, 6, 2), u.on_surface(3, 'MOTION_BLOCKING'))
    f_grass = w.feature('grass', u.plants(u.weighted(('short_grass', 5), ('fern', 1)), 32, 7, 3), u.on_surface(6))
    f_ferns = w.feature('ferns', u.plants(u.weighted(('fern', 4), ('short_grass', 3)), 40, 7, 3), u.on_surface(8))
    f_large_ferns = 'minecraft:patch_large_fern'
    f_moss = w.feature('moss_carpets', u.plants('moss_carpet', 24, 6, 2), u.on_surface(3))
    f_bamboo_light = 'minecraft:bamboo_light'
    # Ponds: the pools vanilla makes in lush caves (water lakes, LakeFeature, read the biome
    # beyond their region to freeze and crash the chunk; vanilla has made none since 1.18).
    pond = u.cf('waterlogged_vegetation_patch', {
        'depth': 2, 'extra_bottom_block_chance': 0.6, 'extra_edge_column_chance': 0.5, 'ground_state': u.simple('clay'),
        'replaceable': '#minecraft:lush_ground_replaceable', 'surface': 'floor', 'vegetation_chance': 0.12,
        'vegetation_feature': {'feature': 'minecraft:dripleaf', 'placement': []}, 'vertical_range': 5, 'xz_radius': u.uniform(3, 6)})
    f_lake = w.feature('pond', pond, [u.rarity(3), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_springs = w.feature('springs', 'minecraft:spring_water', [u.count(10), u.SQUARE, u.height_range(40, 160), u.BIOME])

    # The misty emerald forest: tall dark trunks, dark green crowns (spruce leaves keep their green).
    dark = u.swap(V('configured_feature/dark_oak'), {'minecraft:dark_oak_log': g('ultra_dark_log'), 'minecraft:dark_oak_leaves': 'minecraft:spruce_leaves'})
    mega = u.swap(V('configured_feature/mega_spruce'), {'minecraft:spruce_log': g('ultra_dark_log')})
    tall = u.tree(straight(10, 5), u.log(g('ultra_dark_log')),
                  {'type': 'minecraft:spruce_foliage_placer', 'radius': u.uniform(2, 3), 'offset': u.uniform(0, 2), 'trunk_height': u.uniform(1, 2)},
                  u.leaves('spruce_leaves'))
    f_emerald = w.feature('emerald_trees', u.selector((dark, 0.35), (mega, 0.15), default=tall), u.trees_at(8, 'spruce_sapling'))
    mossy = u.weighted(('moss_block', 3), ('mossy_cobblestone', 2), ('green_terracotta', 1), ('mossy_stone_bricks', 1))
    f_mossy_spires = w.feature('mossy_spires', u.crystal(mossy, 'mossy_cobblestone', u.uniform(4, 12), u.uniform(1, 2), 1, 0.15),
                               [u.rarity(3), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    # The golden grove: dark branches, blossoms of yellow (oak leaves, tinted by the biome).
    golden = u.swap(V('configured_feature/cherry'), {'minecraft:cherry_log': g('ultra_dark_log'), 'minecraft:cherry_leaves': 'minecraft:oak_leaves'})
    f_golden = w.feature('golden_trees', golden, u.trees_at(5, 'oak_sapling'))
    f_golden_few = w.feature('golden_trees_few', golden, [u.rarity(3), u.SQUARE, u.dry(), u.heightmap('OCEAN_FLOOR'), u.BIOME, u.survives('oak_sapling')])
    yellow = u.weighted(('dandelion', 3), (g('flabebe_flower_yellow'), 2), (g('noilednad'), 1))
    f_yellow = w.feature('yellow_flowers', u.plants(yellow, 24, 6, 2), u.on_surface(3, 'MOTION_BLOCKING'))
    # Zen gardens: raked white sand, grooves of calcite, edged with moss.
    zen = u.zen('white_concrete_powder', 'calcite', u.weighted(('moss_block', 3), ('mossy_cobblestone', 1)), mossy, 'moss_carpet',
                u.uniform(6, 12), u.uniform(1, 3), u.uniform(3, 9))
    f_zen = w.feature('zen_gardens', zen, [u.rarity(2), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])

    blades = u.weighted((u.st('oak_leaves', distance=1, persistent='true', waterlogged='false'), 3), ('smooth_stone', 1))
    f_blades = w.feature('leaf_blades', u.crystal(blades, u.st('oak_leaves', distance=1, persistent='true', waterlogged='false'),
                                                  u.uniform(4, 10), u.uniform(0, 1), u.uniform(2, 4), 0.7), u.on_surface(3, 'MOTION_BLOCKING'))
    f_cut_rocks = w.feature('cut_rocks', u.crystal(u.weighted(('smooth_stone', 2), ('calcite', 1)), 'stone', u.uniform(2, 5), u.uniform(1, 2), u.uniform(1, 2), 0.2),
                            [u.rarity(2), u.SQUARE, u.heightmap('MOTION_BLOCKING'), u.BIOME])

    sky = '#8CCB94'
    col = lambda fog, grass, foliage, water='#3F9A8A': (sky, fog, water, '#163A32', grass, foliage)
    w.biomes = [
        u.Biome('blade_valley', 'Vallée des feuilles tranchantes', 'Blade valley', {'weirdness': (-0.07, 0.07), 'erosion': (-2.0, 0.1)},
                col('#B0D8B8', '#B8C8B0', '#CFD8E0'), u.surface('grass_block', 'dirt', 'gravel', steep_block='calcite', patches=[(0.4, 'smooth_stone', 'minecraft:surface')]),
                {'vegetal_decoration': [f_blades, f_cut_rocks, f_grass]},
                relief=[('valley', 32, 0.12)], factor=0.7, tags=['forested', 'rare']),
        u.Biome('zen_garden', 'Jardin zen', 'Zen garden', {'humidity': (0.2, 2.0)},
                col('#78B482', '#6AB04A', '#E0C63A'), u.surface('grass_block', 'dirt', 'gravel', patches=[(0.5, 'moss_block', 'minecraft:surface')]),
                {'local_modifications': [f_zen], 'vegetal_decoration': [f_mossy_spires, f_golden_few, f_ferns, f_yellow]},
                relief=[('noise', 3, 1.0)], flatten=(8, 0.5), particle=('spore_blossom_air', 0.004), tags=['forested']),
        u.Biome('golden_grove', "Bosquet aux fleurs d'or", 'Golden blossom grove', {'temperature': (0.15, 2.0)},
                col('#8DBE7A', '#7DB850', '#E6C72A'), u.surface('grass_block', 'dirt', 'gravel', patches=[(0.55, 'moss_block', 'minecraft:surface'),
                                                                                                         (0.3, 'podzol', 'minecraft:surface')]),
                {'lakes': [f_lake], 'vegetal_decoration': [f_golden, f_yellow, f_flowers, f_grass]},
                relief=[('noise', 8, 1.0)], tags=['forested']),
        u.Biome('origami_forest', 'Forêt origami', 'Origami forest', {'temperature': (-2.0, -0.25)},
                col('#D0EAD4', '#C8E0B8', '#F2F2EE'), u.surface('grass_block', 'dirt', 'gravel', steep_block='calcite',
                                                                patches=[(0.45, 'calcite', 'minecraft:calcite'), (0.75, 'snow_block', 'minecraft:surface')]),
                {'vegetal_decoration': [f_origami, f_folds, f_grass, f_flowers]},
                relief=[('noise', 7, 1.0)], tags=['forested']),
        u.Biome('misty_forest', 'Forêt émeraude brumeuse', 'Misty emerald forest', {},
                col('#5D9A66', '#4E9A3A', '#3F8A2E'), u.surface('grass_block', 'dirt', 'gravel', steep_block='mossy_cobblestone',
                                                                patches=[(0.5, 'moss_block', 'minecraft:surface'), (0.25, 'podzol', 'minecraft:surface')]),
                {'lakes': [f_lake], 'fluid_springs': [f_springs], 'vegetal_decoration': [f_mossy_spires, f_emerald, f_bamboo_light, f_large_ferns, f_ferns, f_moss]},
                relief=[('noise', 6, 1.0)], particle=('spore_blossom_air', 0.008), tags=['forested']),
    ]
    return w


# ------------------------------------------------------------------ 6. Ultra-Abysse (Nihilego): underground

def cave_surface(u, floor, under, ceiling, patches=(), underwater=None):
    floor_rules = [u.cond(u.noise_between(lo, noise=n), u.block(b)) for lo, b, n in patches] + [u.block(floor)]
    return u.seq(
        u.cond(u.stone_depth('floor'), u.seq(u.cond(u.above_water(), u.seq(*floor_rules)), u.block(underwater or under))),
        u.cond(u.stone_depth('ceiling'), u.block(ceiling)),
        u.cond(u.stone_depth('floor', True), u.block(under)),
    )


def abyss(u, g):
    w = u.World('ultra_abyss', 'Ultra-Abysse', 'Ultra Deep Sea', kind='caves', sea=32, default_block=g('cave_rock'), aquifers=False, light=0.12, region_scale=1.4)
    crystals = u.weighted(('cyan_stained_glass', 3), ('light_blue_stained_glass', 3), ('purple_stained_glass', 2), ('packed_ice', 2), (g('crystal_light'), 1))
    heart = u.weighted(('packed_ice', 2), (g('crystal_light'), 1))
    f_floor_crystals = w.feature('floor_crystals', u.crystal(crystals, heart, u.uniform(4, 16), u.uniform(1, 3), u.uniform(1, 4), 0.3), u.cave_floor(10, -30, 120))
    f_ceiling_crystals = w.feature('hanging_crystals', u.crystal(crystals, heart, u.uniform(3, 12), u.uniform(0, 2), u.uniform(1, 3), 0.25, True), u.cave_ceiling(8, -30, 124))
    f_dripstone = w.feature('large_dripstone', 'minecraft:large_dripstone', [u.count(8), u.SQUARE, u.height_range(-50, 120), u.BIOME])
    f_geodes = 'minecraft:amethyst_geode'

    f_red = w.feature('huge_red_mushroom', 'minecraft:huge_red_mushroom', u.cave_floor(6, -20, 120))
    f_brown = w.feature('huge_brown_mushroom', 'minecraft:huge_brown_mushroom', u.cave_floor(4, -20, 120))
    f_ballonlea = w.feature('ballonlea', u.patch(u.weighted(*ballonlea(g)), 24, 6, 2), u.cave_floor(16, -40, 124))
    f_lichen = 'minecraft:glow_lichen'

    f_lantern = w.feature('prismarine_spikes', u.crystal(u.weighted((g('crystal_light'), 1), ('prismarine', 2), ('dark_prismarine', 2)), 'prismarine_bricks',
                                                         u.uniform(1, 4), u.uniform(0, 1), u.uniform(1, 2), 0.2), u.cave_floor(5, -60, 40))

    f_sculk = w.feature('sculk', 'minecraft:sculk_vein', [u.count(30), u.SQUARE, u.height_range(-60, 120), u.BIOME])
    f_pointed = 'minecraft:pointed_dripstone'
    f_rubble = w.feature('rubble', u.rock(g('rock')), u.cave_floor(4, -20, 120))

    glow = [(g('glowing_charge_cobblestone'), 4), (g('crystal_light'), 1), ('shroomlight', 1)]
    f_glow_veins = w.feature('glowing_veins', u.ore(u.weighted(*glow)['entries'][0]['data'], [g('cave_rock'), g('rock')], 10), [u.count(14), u.SQUARE, u.height_range(-60, 124), u.BIOME])
    f_froglight_veins = w.feature('shroomlight_veins', u.ore('shroomlight', [g('cave_rock')], 6), [u.count(8), u.SQUARE, u.height_range(-60, 124), u.BIOME])
    f_pearl_veins = w.feature('light_veins', u.ore(g('crystal_light'), [g('cave_rock')], 4), [u.count(3), u.SQUARE, u.height_range(-60, 124), u.BIOME])
    f_vines = 'minecraft:cave_vines'
    f_glow_spikes = w.feature('glow_spikes', u.crystal(u.weighted(*glow), g('crystal_light'), u.uniform(2, 7), u.uniform(0, 1), u.uniform(1, 3), 0.35), u.cave_floor(6, -40, 120))

    deep = '#0A1E40'
    w.biomes = [
        u.Biome('underground_lakes', 'Lacs souterrains', 'Underground lakes', {'humidity': (0.35, 2.0)},
                (deep, '#10306A', '#1A5CFF', '#031030', None, None),
                cave_surface(u, g('gray_cave_rock_floor'), g('cave_rock'), g('cave_rock'), patches=[(0.4, g('rock'), 'minecraft:surface')],
                             underwater='prismarine'),
                {'vegetal_decoration': [f_lantern, f_lichen]}, arrival=False, tags=['underground', 'watery']),
        u.Biome('luminous_caves', 'Grottes luminescentes', 'Luminous caves', {'temperature': (0.42, 2.0)},
                (deep, '#1A4A5A', '#30C8E0', '#03202A', None, None),
                cave_surface(u, 'moss_block', g('cave_rock'), 'calcite', patches=[(0.5, g('glowing_charge_cobblestone'), 'minecraft:surface'),
                                                                                  (0.2, 'calcite', 'minecraft:calcite')]),
                {'underground_ores': [f_glow_veins, f_froglight_veins, f_pearl_veins], 'vegetal_decoration': [f_glow_spikes, f_vines, f_lichen, f_ballonlea]},
                particle=('glow', 0.004), tags=['underground', 'luminous', 'rare']),
        u.Biome('fungal_forest', 'Forêt fongique', 'Fungal forest', {'humidity': (0.05, 0.35)},
                (deep, '#2A1A4A', '#3A5CFF', '#100830', None, None),
                cave_surface(u, 'mycelium', 'dirt', 'moss_block', patches=[(0.5, 'moss_block', 'minecraft:surface')]),
                {'vegetal_decoration': [f_red, f_brown, f_ballonlea, f_lichen]},
                particle=('mycelium', 0.01), tags=['underground', 'forested', 'luminous']),
        u.Biome('rocky_abyss', 'Abîmes rocheux', 'Rocky abyss', {'temperature': (-2.0, -0.18)},
                (deep, '#08101A', '#1A3C8F', '#020810', None, None),
                cave_surface(u, g('rock'), g('cave_rock'), 'deepslate', patches=[(0.2, 'tuff', 'minecraft:surface'), (0.55, 'deepslate', 'minecraft:surface')]),
                {'underground_decoration': [f_sculk], 'vegetal_decoration': [f_pointed, f_rubble]},
                tags=['underground']),
        u.Biome('crystal_caverns', 'Cavernes cristallines', 'Crystal caverns', {},
                (deep, '#1A2A5A', '#4A7CFF', '#081840', None, None),
                cave_surface(u, g('cave_rock_floor'), g('cave_rock'), 'calcite', patches=[(0.45, 'calcite', 'minecraft:calcite')]),
                {'underground_structures': [f_geodes], 'vegetal_decoration': [f_floor_crystals, f_ceiling_crystals, f_dripstone]},
                tags=['underground', 'crystalline']),
    ]

    # Light for the caverns, shaders included: a shader ignores the world's ambient light
    # (dimension_type), only light coming from blocks counts for it. Minecraft's own
    # invisible light blocks, in the open air of the halls, last of all (a light block
    # is not air: features looking for air would stop on it). Nothing to mine: they
    # cannot be picked up nor even hit in survival.
    cavern_light = u.cf('simple_block', {'to_place': u.simple(u.st('light', level=15, waterlogged='false'))})
    f_light = w.feature('cavern_light', cavern_light, [
        u.count(u.uniform(5, 9)), u.SQUARE, u.height_range(-20, 118),
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}}, u.BIOME])
    for b in w.biomes:
        b.features.setdefault('top_layer_modification', []).append(f_light)

    # The caves' shape: water only in the lakes (fill under y 30 elsewhere, dig wide basins
    # with islets there), and vertical shafts in the rocky abyss.
    lakes, rocky = w.biomes[0], w.biomes[3]

    def lakes_and_fill(world, final):
        lake_mask = world.mask(lakes)
        below = u.ygrad(22, 34, 1.0, 0.0)            # 1 under y 22, 0 above y 34
        fill = u.mul(u.mul(below, u.add(1.0, u.mul(-1.0, lake_mask))), 2.0)
        carve_band = u.mul(u.ygrad(-20, -8, 0.0, 1.0), u.ygrad(36, 46, 1.0, 0.0))
        islets = u.add(u.dnoise(g('ultra_islets'), 1.6), -0.35)
        carved = u.add(10.0, u.mul(u.mul(lake_mask, carve_band), u.add(-10.0, u.mul(4.0, islets))))
        return u.dmin(u.dmax(final, fill), carved)

    def shafts(world, final):
        band = u.mul(u.ygrad(-40, -24, 0.0, 1.0), u.ygrad(100, 114, 1.0, 0.0))
        n = u.dnoise(g('ultra_shafts'), 2.4)
        cap = u.add(10.0, u.mul(u.mul(world.mask(rocky), band), u.add(-10.0, u.mul(8.0, u.add(0.45, u.mul(-1.0, n))))))
        return u.dmin(final, cap)

    w.final_terms = [(lakes_and_fill, ()), (shafts, ())]
    return w


# ------------------------------------------------------------------ 7. Ultra-Gratte-Ciel
# An overgrown city: a grid of streets, towers and houses (generations_core:city_block,
# one per chunk), roof gardens, vines, parks; ruins where it collapsed.

def skyscraper(u, g):
    w = u.World('ultra_skyscraper', 'Ultra-Gratte-Ciel', 'Ultra Ruin', sea=63, offset_scale=0.6, offset_shift=8, factor=1.3, default_block='stone')
    trees = u.selector(('minecraft:fancy_oak', 0.2), ('minecraft:azalea_tree', 0.2), ('minecraft:birch', 0.12), ('minecraft:jungle_bush', 0.15),
                       default='minecraft:oak')
    # Grown by the city blocks (gardens, roofs, sidewalks): no placement of its own.
    city_trees = w.feature('city_trees', trees, [])

    road = u.weighted(('gray_concrete', 12), ('gray_concrete_powder', 2), ('cobbled_deepslate', 1), ('gravel', 1), ('moss_block', 1), ('coarse_dirt', 1))
    road_broken = u.weighted(('gray_concrete', 6), ('cobbled_deepslate', 2), ('gravel', 3), ('moss_block', 2), ('coarse_dirt', 2), ('cracked_deepslate_tiles', 1))
    line = u.weighted(('white_concrete', 4), ('light_gray_concrete', 1))
    line_faded = u.weighted(('white_concrete', 2), ('light_gray_concrete', 2), ('gray_concrete', 1))
    walk = u.weighted(('smooth_stone', 4), ('polished_andesite', 3), ('stone_bricks', 2), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1))
    walk_broken = u.weighted(('polished_andesite', 2), ('cracked_stone_bricks', 2), ('mossy_stone_bricks', 2), ('gravel', 1), ('moss_block', 1))
    streets = u.streets(road, line, walk)
    streets_ruined = u.streets(road_broken, line_faded, walk_broken, 0.12)

    P = u.palette
    mossy = lambda b, m=10: u.weighted((b, m), ('moss_block', 1))
    glass_blue = u.weighted(('glass', 3), ('light_blue_stained_glass', 2), ('cyan_stained_glass', 1))
    glass_gray = u.weighted(('gray_stained_glass', 2), ('glass', 1), ('light_gray_stained_glass', 1))
    glass_plain = u.weighted(('glass', 4), ('white_stained_glass', 1))
    towers = [
        P(mossy('white_concrete'), 'light_gray_concrete', glass_blue, 'smooth_stone'),
        P(mossy('light_gray_concrete'), 'gray_concrete', glass_gray, 'polished_andesite'),
        P(mossy('smooth_quartz'), 'light_gray_concrete', glass_blue, 'smooth_stone'),
        P(mossy('deepslate_tiles'), 'polished_deepslate', glass_gray, 'deepslate_tiles'),
        P(mossy(g('ultra_light_gray')), g('ultra_gray'), glass_blue, 'smooth_stone'),
        P(mossy('cyan_terracotta'), 'white_concrete', glass_blue, 'smooth_stone'),
        P(glass_blue, 'gray_concrete', glass_blue, 'smooth_stone'),
    ]
    homes = [
        P(mossy('orange_terracotta'), 'white_terracotta', glass_plain, 'smooth_stone'),
        P(mossy('yellow_terracotta'), 'white_concrete', glass_plain, 'smooth_stone'),
        P(mossy('white_terracotta'), 'brown_terracotta', glass_plain, 'smooth_stone'),
        P(mossy('light_blue_terracotta'), 'white_concrete', glass_plain, 'smooth_stone'),
        P(mossy('pink_terracotta'), 'white_terracotta', glass_plain, 'smooth_stone'),
        P(mossy('lime_terracotta'), 'white_concrete', glass_plain, 'smooth_stone'),
        P(u.weighted(('bricks', 10), ('mossy_stone_bricks', 1)), 'stone_bricks', glass_plain, 'smooth_stone'),
    ]
    pavilions = [P(u.weighted(('mossy_stone_bricks', 3), ('stone_bricks', 2), ('cracked_stone_bricks', 1)), 'chiseled_stone_bricks', 'air', 'smooth_stone')]
    industry = [
        P(u.weighted(('gray_concrete', 6), ('cyan_terracotta', 1)), 'bricks', glass_gray, 'polished_andesite'),
        P(u.weighted(('deepslate_bricks', 6), ('cracked_deepslate_bricks', 1)), 'bricks', glass_gray, 'polished_deepslate'),
        P('mud_bricks', 'bricks', glass_gray, 'packed_mud'),
        P(u.weighted(('light_gray_concrete', 5), ('green_terracotta', 1)), 'gray_concrete', glass_gray, 'smooth_stone'),
    ]
    rubble = u.weighted(('cobbled_deepslate', 2), ('gravel', 2), ('cracked_stone_bricks', 1), ('andesite', 1), ('cobblestone', 1))
    lawn = u.weighted(('grass_block', 6), ('moss_block', 1), ('coarse_dirt', 1))
    roof_cover = u.weighted(('gravel', 2), ('smooth_stone', 2), ('moss_block', 1))
    # Green roofs, like verdigris, without a block of copper.
    copper_roof = u.weighted(('dark_prismarine', 3), ('prismarine_bricks', 2), ('green_terracotta', 1))

    def block(name, config):
        # At the chunk's corner (no in_square): the streets of every chunk meet.
        return w.feature(name, config, [u.BIOME])
    f_downtown = block('city_downtown', u.city(streets, towers, u.uniform(24, 84), 0.85, lawn, roof_cover, 0.45, 0.12, 0.05, 0.2,
                                                u.ruin(0.15, 0.03, 0.08, rubble), city_trees))
    f_homes = block('city_residential', u.city(streets, homes, u.uniform(8, 26), 0.75, lawn, roof_cover, 0.35, 0.1, 0.1, 0.15,
                                               u.ruin(0.12, 0.03, 0.05, rubble), city_trees))
    f_park = block('city_park', u.city(streets, pavilions, u.uniform(4, 6), 0.12, lawn, 'moss_block', 0.6, 0.25, 0.0, 0.45,
                                       u.ruin(0.5, 0.25, 0.3, rubble), city_trees))
    f_ruins = block('city_ruins', u.city(streets_ruined, towers + homes[:3],
                                         u.uniform(16, 64), 0.8, u.weighted(('coarse_dirt', 3), ('grass_block', 3), ('gravel', 2), ('moss_block', 1)),
                                         'gravel', 0.3, 0.2, 0.0, 0.1, u.ruin(0.6, 0.15, 0.75, rubble), city_trees))
    f_industry = block('city_industry', u.city(streets, industry, u.uniform(6, 12), 0.85, u.weighted(('gravel', 3), ('coarse_dirt', 2), ('grass_block', 2)),
                                               copper_roof, 0.05, 0.06, 0.6, 0.05, u.ruin(0.35, 0.06, 0.12, rubble), city_trees))

    f_grass = w.feature('grass', u.plants(u.weighted(('short_grass', 6), ('fern', 1)), 32, 7, 3), u.on_surface(8))
    f_tall_grass = 'minecraft:patch_tall_grass'
    f_moss = w.feature('moss_carpets', u.plants('moss_carpet', 24, 6, 2), u.on_surface(3))
    f_flowers = 'minecraft:flower_default'
    f_vines = 'minecraft:vines'
    f_park_trees = w.feature('park_trees', trees, u.trees_at(2, 'oak_sapling'))
    f_craters = w.feature('craters', u.crater(u.uniform(3, 7), 0.5, 'gravel', 'cobbled_deepslate', 'cracked_stone_bricks', 5),
                          [u.rarity(5), u.SQUARE, u.heightmap('WORLD_SURFACE_WG'), u.BIOME])
    f_dead = w.feature('dead_bush', u.plants('dead_bush', 8, 6, 2), u.on_surface(1))

    sky, fog = '#A9B9C2', '#BAC4BC'
    col = lambda fog_=fog, grass='#6AAA40': (sky, fog_, '#4A7A6A', '#1A3028', grass, '#5A9A30')
    surface = u.surface('grass_block', 'dirt', 'gravel', patches=[(0.5, 'coarse_dirt', 'minecraft:surface')])
    w.biomes = [
        u.Biome('urban_park', 'Parc urbain', 'Urban park', {'continentalness': (-2.0, -0.3), 'erosion': (-0.1, 2.0)},
                col(grass='#62B640'), surface,
                {'surface_structures': [f_park], 'vegetal_decoration': [f_park_trees, f_flowers, f_tall_grass, f_grass, f_moss]},
                flatten=(6, 0.7), relief=[('noise', 4, 2.0)], tags=['urban', 'forested']),
        u.Biome('industrial_zone', 'Zone industrielle', 'Industrial zone', {'temperature': (0.2, 2.0), 'humidity': (-2.0, 0.05)},
                col('#9EA39A'), surface,
                {'surface_structures': [f_industry], 'vegetal_decoration': [f_grass]},
                flatten=(8, 0.9), particle=('white_ash', 0.006), tags=['urban', 'industrial', 'has_ruins']),
        u.Biome('collapsed_ruins', 'Ruines effondrées', 'Collapsed ruins', {'erosion': (-2.0, -0.22)},
                col('#A8A8A0'), surface,
                {'surface_structures': [f_ruins], 'vegetal_decoration': [f_craters, f_vines, f_grass, f_moss, f_dead]},
                flatten=(8, 0.8), relief=[('noise', 4, 2.0)], particle=('white_ash', 0.01), tags=['urban', 'barren', 'has_ruins']),
        u.Biome('residential', 'Quartier résidentiel', 'Residential district', {'humidity': (0.05, 2.0)},
                col(), surface,
                {'surface_structures': [f_homes], 'vegetal_decoration': [f_vines, f_flowers, f_grass, f_moss]},
                flatten=(8, 0.85), tags=['urban', 'has_ruins']),
        u.Biome('downtown', 'Centre-ville envahi', 'Overgrown downtown', {},
                col(), surface,
                {'surface_structures': [f_downtown], 'vegetal_decoration': [f_vines, f_grass, f_moss]},
                flatten=(8, 0.9), tags=['urban', 'has_ruins']),
    ]
    return w
