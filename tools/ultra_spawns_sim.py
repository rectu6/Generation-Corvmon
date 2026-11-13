"""Model of Cobblemon's world spawn selection on the generated data (bucket first, then the
details of that bucket that can spawn there, by weight), with the mod's rules: the weather's
weights and its own species, the dangerous weather's buckets, the Ultra Beasts' kept share.
Usage: python -I sim_spawns.py <Generations-Core>"""
import json, glob, os, random, sys, collections

ROOT = sys.argv[1]
DATA = os.path.join(ROOT, 'common/src/main/resources/data/generations_core')
BUCKETS = {'common': 94.0, 'uncommon': 5.0, 'rare': 0.5, 'ultra-rare': 0.2, 'boss': 0.3, 'collectibles': 0.2}
DANGEROUS = {'uncommon': 1.5, 'rare': 2.5}
KEEP = 1 / 30

details = []
for f in glob.glob(os.path.join(DATA, 'spawn_pool_world/ultra_space/*/*.json')):
    details += json.load(open(f, encoding='utf-8'))['spawns']
rules = {os.path.basename(f)[:-5]: json.load(open(f, encoding='utf-8')) for f in glob.glob(os.path.join(DATA, 'spawn_rules/ultra_space/*.json'))}
tags = {}
for f in glob.glob(os.path.join(DATA, 'tags/worldgen/biome/ultra_space/*.json')):
    tags['#generations_core:ultra_space/' + os.path.basename(f)[:-5]] = set(json.load(open(f))['values'])


def ids_in(expr):
    return {part.split("'")[1] for part in expr.split('||')}


def eligible(biome, weather, ground_only=True):
    out = []
    for d in details:
        if ground_only and d['spawnablePositionType'] != 'grounded':
            continue
        biomes = set()
        for b in d['condition']['biomes']:
            biomes |= tags.get(b, {b})
        if biome not in biomes:
            continue
        w = d['weight']
        allowed = True
        for event, rule in rules.items():
            for c in rule['components']:
                if d['id'] not in ids_in(c['spawnDetailSelector']):
                    continue
                active = weather == event
                if c['type'] == 'filter' and not active:
                    allowed = False
                if c['type'] == 'weight' and active:
                    w *= float(c['weight'].split('*')[1])
        if allowed:
            out.append((d, w))
    return out


def simulate(biome, weather, dangerous, n, rng):
    pool = eligible(biome, weather)
    by_bucket = collections.defaultdict(list)
    for d, w in pool:
        by_bucket[d['bucket']].append((d, w))
    weights = {b: w * (DANGEROUS.get(b, 1) if dangerous else 1) for b, w in BUCKETS.items()}
    total = sum(weights.values())
    spawns = collections.Counter()
    beasts = 0
    for _ in range(n):
        r = rng.random() * total
        for b, w in weights.items():
            if r < w:
                break
            r -= w
        candidates = by_bucket.get(b)
        if not candidates:
            continue
        t = sum(w for _, w in candidates)
        r = rng.random() * t
        for d, w in candidates:
            if r < w:
                break
            r -= w
        if '/ultra_beast/' in d['id']:
            if rng.random() >= KEEP:
                continue
            beasts += 1
        spawns[(d['bucket'], d['pokemon'])] += 1
    return spawns, beasts


rng = random.Random(5)
N = 2_000_000
for biome, weather, dangerous in [
    ('generations_core:ultra_desert/ivory_canyon', '', False),
    ('generations_core:ultra_desert/ivory_canyon', 'sandstorm', True),
    ('generations_core:ultra_central/electric_mountains', '', False),
    ('generations_core:ultra_central/electric_mountains', 'electric_storm', True),
    ('generations_core:ultra_abyss/crystal_caverns', '', False),
    ('generations_core:ultra_abyss/crystal_caverns', 'crystal_rain', False),
    ('generations_core:ultra_abyss/crystal_caverns', 'toxic_mist', True),
]:
    spawns, beasts = simulate(biome, weather, dangerous, N, rng)
    total = sum(spawns.values())
    per_bucket = collections.Counter()
    for (b, _), c in spawns.items():
        per_bucket[b] += c
    print('%s %s: %d spawns of %d attempts' % (biome.split(':')[1], weather or 'calm', total, N))
    print('   buckets: ' + ', '.join('%s %.2f%%' % (b, 100 * c / total) for b, c in sorted(per_bucket.items())))
    rare = sorted(((c, s) for (b, s), c in spawns.items() if b in ('rare', 'ultra-rare')), reverse=True)
    print('   rare: ' + ', '.join('%s 1/%d' % (s, total // c) for c, s in rare))
    print('   Ultra Beasts: %d, 1 spawn in %s' % (beasts, total // beasts if beasts else '-'))
