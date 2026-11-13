"""
Ultra Space worlds (phases 4.1 and 4.2): writes the data of the seven Ultra Space
dimensions and their 35 biomes into common/src/main/resources/data/generations_core.
Minecraft generates them itself, chunk by chunk, like the Nether and the End.

Per world (ids fixed forever: saves name them):
- dimension/<world>.json             multi_noise biome source: the world's 5 biomes, each
                                     where its climate is (temperature, humidity,
                                     continentalness, erosion, weirdness)
- dimension_type/<world>.json        sky, time, height (-64..320 for all)
- worldgen/noise_settings/<world>.json  the vanilla router (Overworld's, caves' for the
                                     Abyss) with the world's own terrain functions
- worldgen/density_function/<world>/  offset, depth, factor, jaggedness, sloped_cheese,
                                     temperature, vegetation: vanilla's, plus each
                                     biome's relief (dunes, canyons, valleys, craters...)
                                     masked by the same climate that places the biome,
                                     so it lies exactly where the biome is, fading at
                                     its edges
- worldgen/biome/<world>/<biome>.json  colours, particles, features, carvers
- worldgen/configured_feature/<world>/..., worldgen/placed_feature/<world>/...
                                     vanilla feature types with our blocks, plus the
                                     mod's own (generations_core:crater, crystal_spike,
                                     twisted_column, zen_garden, steam_vent, city_block,
                                     density function craters), and the world's
                                     ultra_resources (its ores, from the server's config)
- tags/worldgen/biome/...            for the future Cobblemon spawns and weather
- the 4.1 single biomes (worldgen/biome/<world>.json) and the biomes a world had
  before (World.legacy) are kept, unused by the generation, so that chunks saved with
  them stay valid.
- tags/block/ultra_arrival_built[/<world>]: what arrivals take for a player's build.

The feature order of a world is one total order per step (no "feature order cycle").
Region sizes and rarities: REGION_SCALE and each biome's placement here; a datapack
can also override any file.

Vanilla data comes from Minecraft's official data generator, run on the vanilla
server jar of the Loom cache in an empty folder:
    java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --server --output gen
then: python tools/ultra_worlds.py <gen>/data/minecraft
"""
import copy
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'common', 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data', 'generations_core')
NS = 'generations_core'
VANILLA = None  # <gen>/data/minecraft/worldgen

STEPS = ['raw_generation', 'lakes', 'local_modifications', 'underground_structures', 'surface_structures', 'strongholds',
         'underground_ores', 'underground_decoration', 'fluid_springs', 'vegetal_decoration', 'top_layer_modification']
AXES = ['temperature', 'humidity', 'continentalness', 'erosion', 'weirdness']
FULL = (-2.0, 2.0)


def vanilla(rel):
    with open(os.path.join(VANILLA, rel + '.json'), encoding='utf-8') as f:
        return json.load(f)


def rgb(hex_colour):
    return int(hex_colour.lstrip('#'), 16)


# ---------------------------------------------------------------- block states and providers

def mc(name):
    return name if ':' in name else 'minecraft:' + name


def gen(name):
    return NS + ':' + name


def st(name, **props):
    state = {'Name': mc(name)}
    if props:
        state['Properties'] = {k: str(v).lower() for k, v in props.items()}
    return state


def log(name):
    return st(name, axis='y')


def leaves(name):
    return st(name, distance=7, persistent='false', waterlogged='false')


def simple(state):
    return {'type': 'minecraft:simple_state_provider', 'state': state if isinstance(state, dict) else st(state)}


def weighted(*pairs):
    """(state or name, weight) pairs; a bare name weighs 1."""
    items = [p if isinstance(p, tuple) else (p, 1) for p in pairs]
    return {'type': 'minecraft:weighted_state_provider',
            'entries': [{'data': s if isinstance(s, dict) else st(s), 'weight': w} for s, w in items]}


def uniform(a, b):
    return {'type': 'minecraft:uniform', 'min_inclusive': a, 'max_inclusive': b}


def swap(obj, mapping):
    """A copy of a vanilla JSON with block names replaced (states keep their properties)."""
    obj = copy.deepcopy(obj)

    def walk(o):
        if isinstance(o, dict):
            if isinstance(o.get('Name'), str) and o['Name'] in mapping:
                new = mapping[o['Name']]
                if isinstance(new, dict):
                    o.clear()
                    o.update(copy.deepcopy(new))
                else:
                    o['Name'] = new
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(obj)
    return obj


# ---------------------------------------------------------------- density functions

def add(a, b):
    return {'type': 'minecraft:add', 'argument1': a, 'argument2': b}


def mul(a, b):
    return {'type': 'minecraft:mul', 'argument1': a, 'argument2': b}


def clamp(x, lo, hi):
    return {'type': 'minecraft:clamp', 'input': x, 'min': lo, 'max': hi}


def dabs(x):
    return {'type': 'minecraft:abs', 'argument': x}


def dsquare(x):
    return {'type': 'minecraft:square', 'argument': x}


def dmin(a, b):
    return {'type': 'minecraft:min', 'argument1': a, 'argument2': b}


def dmax(a, b):
    return {'type': 'minecraft:max', 'argument1': a, 'argument2': b}


def dnoise(noise, xz, y=0.0):
    return {'type': 'minecraft:noise', 'noise': noise, 'xz_scale': xz, 'y_scale': y}


def cache2d(x):
    return {'type': 'minecraft:flat_cache', 'argument': {'type': 'minecraft:cache_2d', 'argument': x}}


def ygrad(from_y, to_y, from_value, to_value):
    return {'type': 'minecraft:y_clamped_gradient', 'from_y': from_y, 'to_y': to_y, 'from_value': from_value, 'to_value': to_value}


def ramp(x, a, b):
    """0 at a, 1 at b (a > b for a falling ramp), clamped."""
    return clamp(mul(add(x, -a), 1.0 / (b - a)), 0.0, 1.0)


def total(terms):
    if not terms:
        return 0.0
    out = terms[0]
    for t in terms[1:]:
        out = add(out, t)
    return out


def product(terms):
    if not terms:
        return 1.0
    out = terms[0]
    for t in terms[1:]:
        out = mul(out, t)
    return out


# ---------------------------------------------------------------- surface rules

def block(name):
    return {'type': 'minecraft:block', 'result_state': st(name) if isinstance(name, str) else name}


def seq(*rules):
    return {'type': 'minecraft:sequence', 'sequence': list(rules)}


def cond(test, then):
    return {'type': 'minecraft:condition', 'if_true': test, 'then_run': then}


def stone_depth(surface_type='floor', add_surface_depth=False, offset=0, secondary=0):
    return {'type': 'minecraft:stone_depth', 'add_surface_depth': add_surface_depth, 'offset': offset,
            'secondary_depth_range': secondary, 'surface_type': surface_type}


def above_water(offset=-1):
    return {'type': 'minecraft:water', 'add_stone_depth': False, 'offset': offset, 'surface_depth_multiplier': 0}


def y_above(y, mult=0):
    return {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': mult, 'add_stone_depth': False}


def noise_between(lo, hi=1.7976931348623157e308, noise='minecraft:surface'):
    return {'type': 'minecraft:noise_threshold', 'noise': noise, 'min_threshold': lo, 'max_threshold': hi}


def biome_is(*ids):
    return {'type': 'minecraft:biome', 'biome_is': list(ids)}


def steep():
    return {'type': 'minecraft:steep'}


def neg(c):
    return {'type': 'minecraft:not', 'invert': c}


def surface(top, under, underwater=None, steep_block=None, patches=(), deep=None, first=()):
    """
    The usual layers of a biome: its top block (`under` over a cave ceiling, so that
    sand never hangs), `under` for a few blocks, `underwater` on a water bed, the cliff
    block on steep slopes, and patches over the top: (min noise, block, noise), or
    (min, max, block, noise) for a band (winding paths).
    first: rules for the top before all the others (by height...).
    deep: a rule for everything else in the biome (strata...), after the surface.
    """
    floor_rules = list(first)
    if steep_block:
        floor_rules.append(cond(steep(), block(steep_block)))
    # Bands first, then the patches from the highest threshold down (each takes what is
    # above its threshold: in any other order the higher ones would never show).
    patches = [p for p in patches if len(p) == 4] + sorted((p for p in patches if len(p) == 3), key=lambda p: -p[0])
    for patch_ in patches:
        if len(patch_) == 4:
            lo, hi, name, noise = patch_
            floor_rules.append(cond(noise_between(lo, hi, noise=noise), block(name)))
        else:
            lo, name, noise = patch_
            floor_rules.append(cond(noise_between(lo, noise=noise), block(name)))
    floor_rules.append(cond(stone_depth('ceiling'), block(under)))
    floor_rules.append(block(top))
    rules = [
        cond(stone_depth('floor'), seq(
            cond(above_water(), seq(*floor_rules)),
            block(underwater or under),
        )),
        cond(stone_depth('floor', True), block(under)),
    ]
    out = [cond({'type': 'minecraft:above_preliminary_surface'}, seq(*rules))]
    if deep:
        out.append(deep)
    return seq(*out)


# ---------------------------------------------------------------- placement

def count(n):
    return {'type': 'minecraft:count', 'count': n}


def rarity(n):
    return {'type': 'minecraft:rarity_filter', 'chance': n}


SQUARE = {'type': 'minecraft:in_square'}
BIOME = {'type': 'minecraft:biome'}


def heightmap(name='WORLD_SURFACE_WG'):
    return {'type': 'minecraft:heightmap', 'heightmap': name}


def dry():
    return {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0}


def noise_count(ratio, factor=80.0, offset=0.3):
    return {'type': 'minecraft:noise_based_count', 'noise_to_count_ratio': ratio, 'noise_factor': factor, 'noise_offset': offset}


def u_noise_count(ratio=80, factor=6.0, offset=0.2):
    """Trees by a noise: dense here, clearings there."""
    return noise_count(ratio, factor, offset)


def height_range(lo, hi):
    return {'type': 'minecraft:height_range', 'height': uniform({'absolute': lo}, {'absolute': hi})}


def scan(direction='down', steps=16, target=None):
    return {'type': 'minecraft:environment_scan', 'direction_of_search': direction, 'max_steps': steps,
            'target_condition': target or {'type': 'minecraft:solid'},
            'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}}


def offset_y(y):
    return {'type': 'minecraft:random_offset', 'xz_spread': 0, 'y_spread': y}


def survives(state):
    return {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:would_survive', 'state': st(state, stage=0) if 'sapling' in state else st(state)}}


def on_surface(n, map_name='WORLD_SURFACE_WG'):
    return [count(n), SQUARE, heightmap(map_name), BIOME]


def trees_at(n, sapling='oak_sapling'):
    return [count(n), SQUARE, dry(), heightmap('OCEAN_FLOOR'), BIOME, survives(sapling)]


def cave_floor(n, lo, hi):
    return [count(n), SQUARE, height_range(lo, hi), scan('down', 24), offset_y(1), BIOME]


def cave_ceiling(n, lo, hi):
    return [count(n), SQUARE, height_range(lo, hi), scan('up', 24), offset_y(-1), BIOME]


# ---------------------------------------------------------------- configured features (vanilla types)

def cf(kind, config):
    return {'type': mc(kind), 'config': config}


def tree(trunk_placer, trunk, foliage_placer, foliage, decorators=(), min_size=None, root_placer=None, dirt='dirt'):
    config = {
        'decorators': list(decorators),
        'dirt_provider': simple(dirt),
        'foliage_placer': foliage_placer,
        'foliage_provider': foliage if 'type' in foliage else simple(foliage),
        'force_dirt': False,
        'ignore_vines': True,
        'minimum_size': min_size or {'type': 'minecraft:two_layers_feature_size', 'limit': 1, 'lower_size': 0, 'upper_size': 1},
        'trunk_placer': trunk_placer,
        'trunk_provider': trunk if 'type' in trunk else simple(trunk),
    }
    if root_placer:
        config['root_placer'] = root_placer
    return cf('tree', config)


def patch(provider, tries=32, xz=7, y=3, on=None):
    """Plants (or any small block) scattered on air above ground; `on` limits the ground."""
    predicate = {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}
    if on:
        predicate = {'type': 'minecraft:all_of', 'predicates': [predicate, {'type': 'minecraft:matching_blocks', 'blocks': [mc(b) for b in on], 'offset': [0, -1, 0]}]}
    provider = provider if isinstance(provider, dict) and 'type' in provider else simple(provider)
    return cf('random_patch', {'feature': {'feature': cf('simple_block', {'to_place': provider}), 'placement': [
        {'type': 'minecraft:block_predicate_filter', 'predicate': predicate}]}, 'tries': tries, 'xz_spread': xz, 'y_spread': y})


def plants(provider, tries=32, xz=7, y=3):
    """Plants that check their own ground (would_survive)."""
    provider = provider if isinstance(provider, dict) and 'type' in provider else simple(provider)
    sample = provider['state'] if provider['type'] == 'minecraft:simple_state_provider' else provider['entries'][0]['data']
    return cf('random_patch', {'feature': {'feature': cf('simple_block', {'to_place': provider}), 'placement': [
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
            {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'},
            {'type': 'minecraft:would_survive', 'state': sample}]}}]}, 'tries': tries, 'xz_spread': xz, 'y_spread': y})


def rock(name):
    return cf('forest_rock', {'state': st(name)})


def disk(provider, targets, r=(2, 5), half_height=2):
    provider = provider if isinstance(provider, dict) and 'type' in provider else simple(provider)
    return cf('disk', {'half_height': half_height, 'radius': uniform(*r),
                       'state_provider': {'fallback': provider, 'rules': []},
                       'target': {'type': 'minecraft:matching_blocks', 'blocks': [mc(t) for t in targets]}})


def ore(state, targets, size, discard=0.0):
    return cf('ore', {'discard_chance_on_air_exposure': discard, 'size': size, 'targets': [
        {'state': st(state) if isinstance(state, str) else state,
         'target': {'predicate_type': 'minecraft:block_match', 'block': mc(t)}} for t in targets]})


def lake(fluid, barrier):
    return cf('lake', {'barrier': simple(barrier), 'fluid': simple(st(fluid, level=0))})


def crater(radius, depth, floor, rim, ejecta, ejecta_count=6):
    def p(x):
        return x if isinstance(x, dict) and 'type' in x else simple(x)
    return {'type': gen('crater'), 'config': {'radius': radius, 'depth': depth, 'floor': p(floor), 'rim': p(rim), 'ejecta': p(ejecta), 'ejecta_count': ejecta_count}}


def crystal(block_p, core, height, radius, n, tilt=0.35, hanging=False):
    def p(x):
        return x if isinstance(x, dict) and 'type' in x else simple(x)
    return {'type': gen('crystal_spike'), 'config': {'block': p(block_p), 'core': p(core), 'height': height, 'radius': radius, 'count': n, 'tilt': tilt, 'hanging': hanging}}


def twisted(block_p, accent, height, strands, turns, arch):
    def p(x):
        return x if isinstance(x, dict) and 'type' in x else simple(x)
    return {'type': gen('twisted_column'), 'config': {'block': p(block_p), 'accent': p(accent), 'height': height, 'strands': strands, 'turns': turns, 'arch_chance': arch}}


def _p(x):
    return x if isinstance(x, dict) and 'type' in x else simple(x)


def zen(sand, groove, edge, rock, scatter, radius, rocks, rock_height):
    """Our zen garden: raked sand in rings round pointed rocks (generations_core:zen_garden)."""
    return {'type': gen('zen_garden'), 'config': {'sand': _p(sand), 'groove': _p(groove), 'edge': _p(edge), 'rock': _p(rock),
                                                  'scatter': _p(scatter), 'radius': radius, 'rocks': rocks, 'rock_height': rock_height}}


def vent(rim, ground, radius):
    """Our steam vent: a signal campfire in a ring of rock (generations_core:steam_vent)."""
    return {'type': gen('steam_vent'), 'config': {'rim': _p(rim), 'ground': _p(ground), 'radius': radius}}


def palette(wall, accent, window, floor):
    return {'wall': _p(wall), 'accent': _p(accent), 'window': _p(window), 'floor': _p(floor)}


def streets(road, line, sidewalk, fire_chance=0.0):
    return {'road': _p(road), 'line': _p(line), 'sidewalk': _p(sidewalk), 'fire_chance': fire_chance}


def ruin(broken_windows, holes, collapse, rubble):
    return {'broken_windows': broken_windows, 'holes': holes, 'collapse': collapse, 'rubble': _p(rubble)}


def city(streets_, palettes, height, building, ground, roof, roof_garden, vines, chimney, pond, ruin_, vegetation=None):
    """Our city block, one per chunk: streets and a building or a garden (generations_core:city_block)."""
    config = {'streets': streets_, 'palettes': palettes, 'height': height, 'building_chance': building, 'ground': _p(ground),
              'roof': _p(roof), 'roof_garden': roof_garden, 'vines': vines, 'chimney': chimney, 'pond': pond, 'ruin': ruin_}
    if vegetation:
        config['vegetation'] = vegetation
    return {'type': gen('city_block'), 'config': config}


def selector(*features_weights, default=None):
    """One of several (configured feature, chance) inline, else default."""
    return cf('random_selector', {
        'features': [{'chance': c, 'feature': {'feature': f, 'placement': []}} for f, c in features_weights],
        'default': {'feature': default or features_weights[-1][0], 'placement': []},
    })


# ---------------------------------------------------------------- worlds and biomes

class Biome:
    def __init__(self, path, fr, en, place, colours, surface_rule, features=None, carvers=(), relief=(), factor=1.0,
                 jagged=1.0, flatten=None, tags=(), temperature=0.8, particle=None, arrival=True, share=None):
        self.path = path
        self.fr, self.en = fr, en
        self.place = place              # {axis: (lo, hi)}: where it is, in priority order among the world's
        self.colours = colours          # sky, fog, water, water_fog, grass, foliage
        self.surface_rule = surface_rule
        self.features = features or {}  # step -> [placed feature id]
        self.carvers = list(carvers)
        self.relief = list(relief)      # terms in blocks (see World.term)
        self.factor = factor            # terrain factor multiplier inside (lower: wilder, cliffs)
        self.jagged = jagged
        self.flatten = flatten          # (height in blocks from y 64, strength 0..1)
        self.tags = list(tags)
        self.temperature = temperature
        self.particle = particle        # (type, probability)
        self.arrival = arrival          # arrivals may land here
        self.share = share              # expected part of the world (for the check)


class World:
    def __init__(self, wid, fr, en, kind='surface', sea=63, default_block='stone', default_fluid='water', aquifers=True,
                 time=None, light=0.0, offset_scale=1.0, offset_shift=0.0, factor=1.0, jagged=1.0, region_scale=1.6,
                 bed=True):
        self.id = wid
        self.fr, self.en = fr, en
        self.kind = kind                # 'surface' (Overworld router) or 'caves' (vanilla caves router)
        self.sea = sea
        self.default_block = default_block
        self.default_fluid = default_fluid
        self.aquifers = aquifers
        self.time = time
        self.light = light
        self.offset_scale = offset_scale
        self.offset_shift = offset_shift
        self.factor = factor
        self.jagged = jagged
        self.region_scale = region_scale
        self.bed = bed
        self.biomes = []
        self.configured = {}            # name -> json
        self.placed = {}                # name -> json
        self.final_terms = []           # caves worlds: (kind, args) applied to the final density
        self.legacy = []                # (path, fr, en, colours): former biomes, kept for saved chunks, not generated

    # ids
    def bid(self, biome):
        return f'{NS}:{self.id}/{biome.path}'

    def fid(self, name):
        return f'{NS}:{self.id}/{name}'

    def df(self, name):
        return f'{NS}:{self.id}/{name}'

    # features
    def feature(self, name, configured, placement):
        """A configured feature (inline JSON, or a vanilla/our id) placed with this placement; its placed id."""
        if isinstance(configured, dict):
            self.configured[name] = configured
            ref = self.fid(name)
        else:
            ref = configured
        self.placed[name] = {'feature': ref, 'placement': placement}
        return self.fid(name)

    # climate
    @property
    def T(self):
        return self.df('temperature')

    @property
    def H(self):
        return self.df('vegetation')

    def axis(self, name):
        return {'temperature': self.T, 'humidity': self.H, 'continentalness': 'minecraft:overworld/continents',
                'erosion': 'minecraft:overworld/erosion', 'weirdness': 'minecraft:overworld/ridges'}[name]

    def box_mask(self, place, width=0.05):
        terms = []
        for ax, (lo, hi) in place.items():
            x = self.axis(ax)
            if lo > FULL[0]:
                terms.append(ramp(x, lo - width, lo + width))
            if hi < FULL[1]:
                terms.append(ramp(x, hi + width, hi - width))
        return product(terms)

    def mask(self, biome):
        """Where the biome is (1 inside, fading at its edges): its box, minus the ones before it."""
        m = [self.box_mask(biome.place)]
        for other in self.biomes:
            if other is biome:
                break
            m.append(add(1.0, mul(-1.0, self.box_mask(other.place))))
        return product(m)

    def term(self, t):
        """A relief term, in the terrain's depth units (blocks / 128)."""
        kind = t[0]
        if kind == 'raise':
            return t[1] / 128.0
        if kind == 'noise':          # ('noise', amplitude, xz scale)
            return mul(t[1] / 128.0, dnoise(gen('ultra_relief'), t[2]))
        if kind == 'dunes':          # ('dunes', height, xz scale): crests
            return mul(t[1] / 128.0, dsquare(add(1.0, mul(-1.0, dabs(dnoise(gen('ultra_dunes'), t[2]))))))
        if kind == 'ridges':         # ('ridges', height, xz scale): sharp crests
            return mul(t[1] / 128.0, add(1.0, mul(-1.0, dabs(dnoise(gen('ultra_relief'), t[2])))))
        if kind == 'valley':         # ('valley', depth, width): along weirdness 0, flat floor, steep walls
            return mul(-t[1] / 128.0, clamp(mul(add(1.0, mul(-1.0 / t[2], dabs('minecraft:overworld/ridges'))), 2.0), 0.0, 1.0))
        if kind == 'craters':        # ('craters', cell, min r, max r, depth, rim, chance)
            return {'type': 'minecraft:cache_2d', 'argument': {'type': gen('craters'), 'noise': gen('ultra_craters'), 'cell_size': t[1],
                    'min_radius': t[2], 'max_radius': t[3], 'depth': t[4], 'rim': t[5], 'chance': t[6]}}
        raise ValueError(kind)

    # placement of the biomes: disjoint boxes, in priority order
    def entries(self):
        remaining = [{a: FULL for a in AXES}]
        out = []
        for b in self.biomes:
            box = {a: b.place.get(a, FULL) for a in AXES}
            mine, rest = [], []
            for r in remaining:
                inter = intersect(r, box)
                if inter:
                    mine.append(inter)
                rest.extend(subtract(r, box))
            remaining = rest
            for m in mine:
                params = {a: [round(m[a][0], 4), round(m[a][1], 4)] for a in AXES}
                params['depth'] = [-2.0, 2.0]
                params['offset'] = 0.0
                out.append({'biome': self.bid(b), 'parameters': params})
        if remaining:
            raise ValueError(f'{self.id}: the last biome must cover what is left')
        return out


def intersect(a, b):
    out = {}
    for ax in AXES:
        lo, hi = max(a[ax][0], b[ax][0]), min(a[ax][1], b[ax][1])
        if lo >= hi:
            return None
        out[ax] = (lo, hi)
    return out


def subtract(a, b):
    if not intersect(a, b):
        return [a]
    out, cur = [], dict(a)
    for ax in AXES:
        lo, hi = cur[ax]
        blo, bhi = b[ax]
        if lo < blo:
            out.append({**cur, ax: (lo, blo)})
        if bhi < hi:
            out.append({**cur, ax: (bhi, hi)})
        cur[ax] = (max(lo, blo), min(hi, bhi))
    return out


# ---------------------------------------------------------------- writing

written = []


def write(rel, value):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write('\n')
    written.append(rel)


def router_surface(w):
    """The Overworld's router with the world's own terrain functions and climate."""
    router = copy.deepcopy(vanilla('noise_settings/overworld')['noise_router'])
    text = json.dumps(router)
    for name in ('depth', 'factor', 'sloped_cheese'):
        text = text.replace(f'"minecraft:overworld/{name}"', f'"{w.df(name)}"')
    router = json.loads(text)
    router['temperature'] = w.T
    router['vegetation'] = w.H
    return router


def terrain_functions(w):
    """offset, depth, factor, jaggedness, sloped_cheese of a surface world."""
    reliefs = [(b, w.mask(b)) for b in w.biomes]
    terms = [mul('minecraft:overworld/offset', w.offset_scale), w.offset_shift / 128.0]
    for b, m in reliefs:
        if b.relief:
            terms.append(mul(m, total([w.term(t) for t in b.relief])))
    write(f'worldgen/density_function/{w.id}/offset_base.json', cache2d(total(terms)))
    offset = w.df('offset_base')
    for b, m in reliefs:
        if b.flatten:
            height, strength = b.flatten
            # The terrain's offset 0 is y 128 (vanilla's surface near y 64 is offset -0.5): the
            # height is counted from y 64, a block is 1/128.
            offset = add(offset, mul(mul(m, strength), add((height - 64) / 128.0, mul(-1.0, w.df('offset_base')))))
    write(f'worldgen/density_function/{w.id}/offset.json', cache2d(offset))
    write(f'worldgen/density_function/{w.id}/depth.json', add(ygrad(-64, 320, 1.5, -1.5), w.df('offset')))
    factor = [w.factor]
    jag = [w.jagged]
    for b, m in reliefs:
        if b.factor != 1.0:
            factor.append(mul(m, w.factor * (b.factor - 1.0)))
        if b.jagged != 1.0:
            jag.append(mul(m, w.jagged * (b.jagged - 1.0)))
    write(f'worldgen/density_function/{w.id}/factor.json', cache2d(mul('minecraft:overworld/factor', total(factor))))
    write(f'worldgen/density_function/{w.id}/jaggedness.json', cache2d(mul('minecraft:overworld/jaggedness', total(jag))))
    cheese = json.dumps(vanilla('density_function/overworld/sloped_cheese'))
    for name in ('depth', 'factor', 'jaggedness'):
        cheese = cheese.replace(f'"minecraft:overworld/{name}"', f'"{w.df(name)}"')
    write(f'worldgen/density_function/{w.id}/sloped_cheese.json', json.loads(cheese))


def climate_functions(w, base):
    scale = 0.25 / w.region_scale
    for name, key in (('temperature', 'temperature'), ('vegetation', 'vegetation')):
        f = copy.deepcopy(base[key])
        f['xz_scale'] = scale
        write(f'worldgen/density_function/{w.id}/{name}.json', f)


def noise_settings(w):
    if w.kind == 'surface':
        settings = copy.deepcopy(vanilla('noise_settings/overworld'))
        climate_functions(w, settings['noise_router'])
        terrain_functions(w)
        settings['noise_router'] = router_surface(w)
    else:
        settings = copy.deepcopy(vanilla('noise_settings/caves'))
        climate_functions(w, settings['noise_router'])
        router = settings['noise_router']
        router['temperature'] = w.T
        router['vegetation'] = w.H
        final = router['final_density']
        for kind, args in w.final_terms:
            final = kind(w, final, *args)
        router['final_density'] = final
    settings['sea_level'] = w.sea
    settings['default_block'] = st(w.default_block)
    settings['default_fluid'] = st(w.default_fluid, level=0)
    settings['aquifers_enabled'] = w.aquifers
    settings['ore_veins_enabled'] = False
    settings['disable_mob_generation'] = True
    settings['surface_rule'] = surface_rules(w)
    return settings


def surface_rules(w):
    overworld = vanilla('noise_settings/overworld')['surface_rule']['sequence']
    bedrock_floor, deepslate = overworld[0], overworld[2]
    rules = [bedrock_floor]
    if w.kind == 'caves':
        # The roof: bedrock at the top of the caves.
        rules.append(cond(neg({'type': 'minecraft:vertical_gradient', 'random_name': gen('ultra_roof'),
                                'true_at_and_below': {'below_top': 5}, 'false_at_and_above': {'below_top': 0}}), block('bedrock')))
    rules += [cond(biome_is(w.bid(b)), b.surface_rule) for b in w.biomes]
    if w.kind == 'surface':
        rules.append(deepslate)
    return seq(*rules)


def dimension_type(w):
    kind = {
        'ambient_light': w.light, 'bed_works': w.bed, 'coordinate_scale': 1.0, 'effects': 'minecraft:overworld',
        'has_ceiling': w.kind == 'caves', 'has_raids': False, 'has_skylight': w.kind != 'caves', 'height': 384,
        'infiniburn': '#minecraft:infiniburn_overworld', 'logical_height': 384, 'min_y': -64,
        'monster_spawn_block_light_limit': 0,
        'monster_spawn_light_level': {'type': 'minecraft:uniform', 'max_inclusive': 7, 'min_inclusive': 0},
        'natural': True, 'piglin_safe': False, 'respawn_anchor_works': False, 'ultrawarm': False,
    }
    if w.time is not None:
        kind['fixed_time'] = w.time
    return kind


def biome_json(w, b, order):
    sky, fog, water, water_fog, grass, foliage = b.colours
    effects = {'fog_color': rgb(fog), 'sky_color': rgb(sky), 'water_color': rgb(water), 'water_fog_color': rgb(water_fog)}
    if grass:
        effects['grass_color'] = rgb(grass)
    if foliage:
        effects['foliage_color'] = rgb(foliage)
    if b.particle:
        effects['particle'] = {'options': {'type': mc(b.particle[0])}, 'probability': b.particle[1]}
    effects['mood_sound'] = {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.cave', 'tick_delay': 6000}
    features = []
    for i, step in enumerate(STEPS):
        ids = b.features.get(step, [])
        features.append(sorted(ids, key=lambda f: order[(step, f)]))
    return {
        'carvers': {'air': [mc(c) for c in b.carvers]} if b.carvers else {},
        'downfall': 0.4,
        'effects': effects,
        'features': features,
        'has_precipitation': False,
        'spawn_costs': {},
        'spawners': {},
        'temperature': b.temperature,
    }


def legacy_biome(w):
    """The 4.1 single biome: kept for chunks saved with it, not generated any more."""
    first = w.biomes[0]
    sky, fog, water, water_fog, grass, foliage = first.colours
    effects = {'fog_color': rgb(fog), 'sky_color': rgb(sky), 'water_color': rgb(water), 'water_fog_color': rgb(water_fog)}
    return {'carvers': {}, 'downfall': 0.0, 'effects': effects, 'features': [], 'has_precipitation': False,
            'spawn_costs': {}, 'spawners': {}, 'temperature': 0.8}


def former_biome(colours):
    """A biome the world made before: kept for chunks saved with it, not generated any more."""
    sky, fog, water, water_fog, grass, foliage = colours
    effects = {'fog_color': rgb(fog), 'sky_color': rgb(sky), 'water_color': rgb(water), 'water_fog_color': rgb(water_fog)}
    if grass:
        effects['grass_color'] = rgb(grass)
    if foliage:
        effects['foliage_color'] = rgb(foliage)
    return {'carvers': {}, 'downfall': 0.4, 'effects': effects, 'features': [], 'has_precipitation': False,
            'spawn_costs': {}, 'spawners': {}, 'temperature': 0.8}


def feature_order(w):
    order = {}
    for b in w.biomes:
        for step in STEPS:
            for f in b.features.get(step, []):
                order.setdefault((step, f), len(order))
    return order


def add_resources(w):
    """
    The world's ores (phase 4.3.2): one feature, in every biome's ore step, which lays what
    the server's ultra_resources.json lists for the world (UltraResourcesConfig), chunk by chunk.
    """
    fid = w.feature('resources', {'type': gen('ultra_resources'), 'config': {'world': w.id}}, [])
    for b in w.biomes:
        b.features.setdefault('underground_ores', []).append(fid)


def write_world(w):
    for name, value in w.configured.items():
        write(f'worldgen/configured_feature/{w.id}/{name}.json', value)
    for name, value in w.placed.items():
        write(f'worldgen/placed_feature/{w.id}/{name}.json', value)
    order = feature_order(w)
    for b in w.biomes:
        write(f'worldgen/biome/{w.id}/{b.path}.json', biome_json(w, b, order))
    write(f'worldgen/biome/{w.id}.json', legacy_biome(w))
    for path, fr, en, colours in w.legacy:
        write(f'worldgen/biome/{w.id}/{path}.json', former_biome(colours))
    write(f'worldgen/noise_settings/{w.id}.json', noise_settings(w))
    write(f'dimension_type/{w.id}.json', dimension_type(w))
    write(f'dimension/{w.id}.json', {
        'type': f'{NS}:{w.id}',
        'generator': {'type': 'minecraft:noise', 'settings': f'{NS}:{w.id}',
                      'biome_source': {'type': 'minecraft:multi_noise', 'biomes': w.entries()}},
    })


def write_noises():
    write('worldgen/noise/ultra_relief.json', {'firstOctave': -8, 'amplitudes': [1.0, 1.0, 0.5, 0.25]})
    write('worldgen/noise/ultra_dunes.json', {'firstOctave': -6, 'amplitudes': [1.0, 0.4]})
    write('worldgen/noise/ultra_craters.json', {'firstOctave': -4, 'amplitudes': [1.0]})
    write('worldgen/noise/ultra_shafts.json', {'firstOctave': -6, 'amplitudes': [1.0, 0.5]})
    write('worldgen/noise/ultra_islets.json', {'firstOctave': -5, 'amplitudes': [1.0, 0.5]})


def optional_tag(entries):
    return {'replace': False, 'values': [{'id': e, 'required': False} for e in entries]}


def write_tags(worlds):
    everything = []
    themes = {}
    for w in worlds:
        ids = [w.bid(b) for b in w.biomes]
        everything += ids
        write(f'tags/worldgen/biome/ultra_space/{w.id}.json', {'replace': False, 'values': ids})
        for b in w.biomes:
            for t in b.tags:
                themes.setdefault(t, []).append(w.bid(b))
    write('tags/worldgen/biome/ultra_space.json', {'replace': False, 'values': [f'#{NS}:ultra_space/{w.id}' for w in worlds]})
    for t, ids in sorted(themes.items()):
        name = t if t.startswith('has_') else f'is_{t}'
        write(f'tags/worldgen/biome/ultra_space/{name}.json', {'replace': False, 'values': ids})
    # Where arrivals may not land (lava, toxic water, lakes): read by UltraArrivalSearch.
    avoid = [w.bid(b) for w in worlds for b in w.biomes if not b.arrival]
    write('tags/worldgen/biome/ultra_space/no_arrival.json', {'replace': False, 'values': avoid})
    return everything


BUILT = [
    '#minecraft:planks', '#minecraft:wool', '#minecraft:wool_carpets', '#minecraft:doors', '#minecraft:trapdoors',
    '#minecraft:beds', '#minecraft:fences', '#minecraft:fence_gates', '#minecraft:wooden_slabs', '#minecraft:wooden_stairs',
    '#minecraft:all_signs', '#minecraft:rails', '#minecraft:banners', '#minecraft:impermeable', '#minecraft:candles',
    '#minecraft:buttons', '#minecraft:pressure_plates',
    'minecraft:glass_pane', 'minecraft:torch', 'minecraft:wall_torch', 'minecraft:lantern', 'minecraft:crafting_table',
    'minecraft:furnace', 'minecraft:chest', 'minecraft:barrel', 'minecraft:ladder', 'minecraft:scaffolding',
    'minecraft:bricks', 'minecraft:stone_bricks', 'minecraft:cobblestone',
]
# The Ultra Skyscraper's city is built of these: there, they are no sign of a player's build.
CITY_BLOCKS = ['#minecraft:impermeable', 'minecraft:glass_pane', 'minecraft:bricks', 'minecraft:stone_bricks', 'minecraft:cobblestone']
UNSAFE = [
    'minecraft:lava', 'minecraft:magma_block', 'minecraft:cactus', 'minecraft:sweet_berry_bush', 'minecraft:powder_snow',
    '#minecraft:fire', '#minecraft:campfires', 'minecraft:pointed_dripstone', 'minecraft:wither_rose', 'minecraft:cobweb',
    '#minecraft:leaves', 'minecraft:scaffolding', 'generations_core:pointed_charge_dripstone',
]


def write_lang(worlds):
    """The biomes' names (F3, /locate, map mods) in the language files."""
    for path, attr, indent in ((os.path.join(RES, 'assets', 'generations_core', 'lang', 'fr_fr.json'), 'fr', ''),
                               (os.path.join(ROOT, 'common', 'src', 'main', 'generated', 'resources', 'assets', 'generations_core', 'lang', 'en_us.json'), 'en', '  ')):
        with open(path, encoding='utf-8') as f:
            text = f.read()
        data = json.loads(text)
        new = {}
        for w in worlds:
            for b in w.biomes:
                key = f'biome.{NS}.{w.id}/{b.path}'
                new[key] = f"{getattr(w, attr)} : {getattr(b, attr)}" if attr == 'fr' else f"{getattr(w, attr)}: {getattr(b, attr)}"
            for path, fr, en, _ in w.legacy:
                key = f'biome.{NS}.{w.id}/{path}'
                new[key] = f"{w.fr} : {fr} (ancien)" if attr == 'fr' else f"{w.en}: {en} (old)"
        lines = [l for l in text.rstrip().rstrip('}').rstrip().split('\n')]
        # Replace existing keys, add the others before the closing brace.
        body = '\n'.join(lines)
        for key, value in new.items():
            entry = f'{indent}{json.dumps(key)}: {json.dumps(value, ensure_ascii=False)}'
            if f'"{key}"' in body:
                start = body.index(f'"{key}"')
                line_start = body.rfind('\n', 0, start) + 1
                line_end = body.find('\n', start)
                line_end = len(body) if line_end < 0 else line_end
                old = body[line_start:line_end]
                body = body[:line_start] + entry + (',' if old.rstrip().endswith(',') else '') + body[line_end:]
            else:
                body = body.rstrip()
                if not body.endswith(','):
                    body += ','
                body += '\n' + entry
        body = body.rstrip().rstrip(',')
        out = body + '\n}\n'
        json.loads(out)
        with open(path, 'w', encoding='utf-8', newline='') as f:
            f.write(out)


def main():
    global VANILLA
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    VANILLA = os.path.join(sys.argv[1], 'worldgen')
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import ultra_worlds_defs
    worlds = ultra_worlds_defs.worlds(sys.modules[__name__])
    # Fresh folders for what this tool owns (the 4.1 single biomes are rewritten too).
    for sub in ('worldgen/density_function', 'worldgen/configured_feature', 'worldgen/placed_feature'):
        for w in worlds:
            shutil.rmtree(os.path.join(DATA, sub, w.id), ignore_errors=True)
    for w in worlds:
        shutil.rmtree(os.path.join(DATA, 'worldgen', 'biome', w.id), ignore_errors=True)
    write_noises()
    for w in worlds:
        add_resources(w)
        write_world(w)
    write_tags(worlds)
    write('tags/block/ultra_arrival_built.json', optional_tag(BUILT))
    write('tags/block/ultra_arrival_built/ultra_skyscraper.json', optional_tag([b for b in BUILT if b not in CITY_BLOCKS]))
    write('tags/block/ultra_arrival_unsafe.json', optional_tag(UNSAFE))
    write_lang(worlds)
    for w in worlds:
        print(f"{w.id}: {len(w.biomes)} biomes, {len(w.entries())} climate boxes, {len(w.placed)} placed features")
    print(f'{len(written)} files written')


if __name__ == '__main__':
    main()
