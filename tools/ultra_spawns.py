"""Phase 5: the Pokémon of the seven Ultra worlds, as Cobblemon's own data.

Writes into common/src/main/resources/data/generations_core:
- spawn_pool_world/ultra_space/<world>/<biome>.json   the biome's population (ids <world>/<biome>/<species>)
- spawn_pool_world/ultra_space/<world>/ultra_beasts.json  its Ultra Beasts, ultra-rare (ids <world>/ultra_beast/<species>);
  the server also keeps only a share of them and caps them (config ultra_spawns.json)
- spawn_pool_world/ultra_space/<world>/weather.json   the species of a weather only (ids <world>/weather/<event>/<species>)
- spawn_rules/ultra_space/<event>.json                Cobblemon spawn rules: weights while the weather lasts, and the
  filter that lets the weather's own species spawn only then. They read the world's Ultra weather from the MoLang
  function the mod adds: v.spawnable_position.world.ultra_weather ('' outside an event, and outside the Ultra worlds).

A datapack can override any of these files (same path). Usage: python tools/ultra_spawns.py <Generations-Core>"""
import json, os, shutil, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DATA = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'data', 'generations_core')
POOLS = os.path.join(DATA, 'spawn_pool_world', 'ultra_space')
RULES = os.path.join(DATA, 'spawn_rules', 'ultra_space')

# Per biome: common | uncommon | rare | ultra-rare (Cobblemon's buckets).
WORLDS = {
    'ultra_jungle': {
        'giant_jungle': 'caterpie metapod pikipek trumbeak|scyther heracross pinsir|tropius|',
        'glowing_jungle': 'morelull volbeat illumise|shinx joltik|lanturn|',
        'mountain_jungle': 'mankey makuhita timburr|primeape passimian|hawlucha|',
        'overgrown_jungle': 'sewaddle grubbin bounsweet|aipom oranguru|tangela|',
        'swamp_jungle': 'lotad wooper croagunk|surskit poliwhirl|goomy|',
    },
    'ultra_desert': {
        'crystal_desert': 'roggenrola nosepass|carbink|sigilyph|',
        'glass_desert': 'trapinch cacnea|helioptile|larvesta|',
        'ivory_canyon': 'sandshrew rhyhorn|hippopotas|gible|',
        'oasis': 'buizel wooper|cherubi maractus barboach|whiscash|',
        'white_dunes': 'sandile silicobra skorupi|dwebble||',
    },
    'ultra_central': {
        'cable_forest': 'joltik pichu|charjabug|vikavolt|',
        'conductive_plains': 'electrike blitzle pawmi|pawmo flaaffy||',
        'crystal_fields': 'magnemite minior|klink|porygon|',
        'electric_mountains': 'shinx emolga|elekid|luxio|',
        'magnetic_valley': 'magnemite nosepass klink|magneton bronzor|metang|',
    },
    'ultra_crater': {
        'crater_plains': 'clefairy lunatone solrock|elgyem|beldum|',
        'giant_craters': 'aron baltoy|lairon bronzor|minior|',
        'spire_forest': 'skarmory|corvisquire|corviknight|',
        'steam_fields': 'numel slugma torkoal|magby|turtonator|',
        'yellow_plateaus': 'rolycoly geodude|carkol|larvitar|',
    },
    'ultra_forest': {
        'blade_valley': 'pawniard|honedge|bisharp|',
        'golden_grove': 'cottonee petilil|oricorio||',
        'misty_forest': 'phantump natu|ralts|mimikyu|',
        'origami_forest': 'sewaddle seedot|swadloon|ferroseed|',
        'zen_garden': 'lotad hoppip|spinda|ferrothorn|',
    },
    'ultra_abyss': {
        'crystal_caverns': 'roggenrola carbink|glimmet|boldore|',
        'fungal_forest': 'foongus shroomish paras|morelull|amoonguss|',
        'luminous_caves': 'chinchou zubat|lanturn noibat|lampent|',
        'rocky_abyss': 'sableye nosepass dwebble|onix|larvitar|',
        'underground_lakes': 'tentacool shellos tympole|mareanie skrelp|frillish|',
    },
    'ultra_skyscraper': {
        'collapsed_ruins': 'litwick rattata|zorua|spiritomb|',
        'downtown': 'trubbish purrloin murkrow|scraggy|deino|',
        'industrial_zone': 'grimer koffing magnemite|varoom klink||',
        'residential': 'meowth stunky houndour|sneasel|absol|',
        'urban_park': 'pidove trubbish|zigzagoon|klefki|',
    },
}
BUCKETS = ['common', 'uncommon', 'rare', 'ultra-rare']
WEIGHTS = {'common': 10.0, 'uncommon': 6.0, 'rare': 3.0, 'ultra-rare': 1.0}
# Common levels of each world; uncommon +3/+5, rare +6/+10.
LEVELS = {'ultra_jungle': (20, 30), 'ultra_desert': (22, 32), 'ultra_central': (25, 35), 'ultra_crater': (28, 38),
          'ultra_forest': (25, 35), 'ultra_abyss': (30, 40), 'ultra_skyscraper': (30, 40)}
LEVEL_BONUS = {'common': (0, 0), 'uncommon': (3, 5), 'rare': (6, 10), 'ultra-rare': (8, 12)}
# In the water (with the "water" preset): submerged or at the surface.
WATER = {'lanturn': 'submerged', 'chinchou': 'submerged', 'tentacool': 'submerged', 'frillish': 'submerged',
         'skrelp': 'submerged', 'mareanie': 'submerged', 'barboach': 'submerged', 'whiscash': 'submerged',
         'buizel': 'surface', 'lotad': 'surface', 'surskit': 'surface'}

# The Ultra Beasts of each world: the destination's own (weight 1), and a second one a little rarer.
ULTRA_BEASTS = {
    'ultra_jungle': [('buzzwole', 1.0)], 'ultra_desert': [('pheromosa', 1.0)], 'ultra_central': [('xurkitree', 1.0)],
    'ultra_crater': [('celesteela', 1.0), ('blacephalon', 0.85)], 'ultra_forest': [('kartana', 1.0)],
    'ultra_abyss': [('nihilego', 1.0), ('poipole', 0.85)], 'ultra_skyscraper': [('guzzlord', 1.0), ('stakataka', 0.85)],
}
ULTRA_BEAST_LEVEL = '55-65'

# The weathers: species favoured (x factor) or held back while they last, and their own species
# (rare, a little stronger), which spawn only then. The harmless weathers only move the weights.
WEATHERS = {
    'tropical_rain': dict(world='ultra_jungle', boost='bounsweet lanturn lotad morelull poliwhirl sewaddle surskit tangela tropius wooper goomy', weaken='', exclusive=''),
    'extreme_heat': dict(world='ultra_jungle', boost='caterpie grubbin heracross illumise joltik metapod pinsir scyther volbeat', weaken='lotad wooper surskit', exclusive='salandit:4 volcarona:1.5'),
    'intense_sun': dict(world='ultra_desert', boost='helioptile cherubi maractus', weaken='', exclusive=''),
    'sandstorm': dict(world='ultra_desert', boost='hippopotas gible sandile', weaken='cherubi helioptile', exclusive='larvitar:4'),
    'energy_surge': dict(world='ultra_central', boost='blitzle charjabug electrike elekid emolga flaaffy joltik luxio magnemite magneton pawmi pawmo pichu shinx vikavolt', weaken='', exclusive=''),
    'electric_storm': dict(world='ultra_central', boost='elekid luxio vikavolt', weaken='', exclusive='rotom:4'),
    'ash_rain': dict(world='ultra_crater', boost='aron carkol geodude lairon larvitar lunatone magby minior numel rolycoly slugma solrock torkoal turtonator', weaken='', exclusive=''),
    'thermal_eruption': dict(world='ultra_crater', boost='magby turtonator', weaken='clefairy', exclusive='heatmor:4'),
    'dimensional_mist': dict(world='ultra_forest', boost='honedge mimikyu natu phantump ralts', weaken='', exclusive=''),
    'cutting_winds': dict(world='ultra_forest', boost='pawniard honedge', weaken='cottonee hoppip', exclusive='kleavor:3'),
    'crystal_rain': dict(world='ultra_abyss', boost='carbink roggenrola glimmet boldore', weaken='', exclusive=''),
    'toxic_mist': dict(world='ultra_abyss', boost='amoonguss foongus glimmet mareanie skrelp tentacool zubat', weaken='', exclusive='glimmora:3'),
    'dimensional_darkness': dict(world='ultra_skyscraper', boost='absol deino houndour litwick murkrow purrloin scraggy sneasel spiritomb stunky zorua', weaken='pidove', exclusive=''),
    'acid_rain': dict(world='ultra_skyscraper', boost='grimer klefki klink koffing magnemite stunky trubbish varoom', weaken='pidove zigzagoon', exclusive='toxtricity:4'),
}
BOOST, WEAKEN, EXCLUSIVE_LEVEL_BONUS = 3.0, 0.4, 5


def write(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write('\n')


def detail(id_, species, bucket, level, weight, biomes):
    d = {'id': id_, 'pokemon': species, 'type': 'pokemon', 'spawnablePositionType': WATER.get(species, 'grounded'),
         'bucket': bucket, 'level': level, 'weight': weight, 'condition': {'biomes': biomes}}
    if species in WATER:
        d['presets'] = ['water']
    return d


def level(world, bucket, bonus=0):
    lo, hi = LEVELS[world]
    a, b = LEVEL_BONUS[bucket]
    return '%d-%d' % (lo + a + bonus, hi + b + bonus)


def pool(spawns):
    return {'enabled': True, 'neededInstalledMods': [], 'neededUninstalledMods': [], 'spawns': spawns}


def main():
    shutil.rmtree(POOLS, ignore_errors=True)
    shutil.rmtree(RULES, ignore_errors=True)
    count = 0
    for world, biomes in WORLDS.items():
        species_of_world = set()
        for biome, text in biomes.items():
            spawns = []
            for bucket, names in zip(BUCKETS, text.split('|')):
                for species in names.split():
                    species_of_world.add(species)
                    spawns.append(detail('%s/%s/%s' % (world, biome, species), species, bucket, level(world, bucket),
                                         WEIGHTS[bucket], ['generations_core:%s/%s' % (world, biome)]))
            write(os.path.join(POOLS, world, biome + '.json'), pool(spawns))
            count += len(spawns)
        world_tag = ['#generations_core:ultra_space/' + world]
        write(os.path.join(POOLS, world, 'ultra_beasts.json'), pool([
            detail('%s/ultra_beast/%s' % (world, species), species, 'ultra-rare', ULTRA_BEAST_LEVEL, weight, world_tag)
            for species, weight in ULTRA_BEASTS[world]]))
        weather_spawns = []
        for event, w in WEATHERS.items():
            if w['world'] != world:
                continue
            for item in w['exclusive'].split():
                species, weight = item.split(':')
                weather_spawns.append(detail('%s/weather/%s/%s' % (world, event, species), species, 'rare',
                                             level(world, 'rare', EXCLUSIVE_LEVEL_BONUS), float(weight), world_tag))
            for s in (w['boost'] + ' ' + w['weaken']).split():
                assert s in species_of_world, (event, s)
        write(os.path.join(POOLS, world, 'weather.json'), pool(weather_spawns))
        count += len(weather_spawns)

    for event, w in WEATHERS.items():
        world = w['world']
        here = "v.spawnable_position.world.ultra_weather == '%s'" % event
        ours = "v.spawn_detail.id == '%s'"  # only the Ultra worlds' own spawns are touched

        def selector(names):
            ids = ['%s/%s/%s' % (world, biome, s) for biome, text in WORLDS[world].items()
                   for s in ' '.join(text.split('|')).split() if s in names.split()]
            return ' || '.join(ours % i for i in ids)

        components = []
        if w['boost'].strip():
            components.append({'type': 'weight', 'spawnDetailSelector': selector(w['boost']), 'spawnablePositionSelector': here,
                               'weight': 'v.weight * %g' % BOOST})
        if w['weaken'].strip():
            components.append({'type': 'weight', 'spawnDetailSelector': selector(w['weaken']), 'spawnablePositionSelector': here,
                               'weight': 'v.weight * %g' % WEAKEN})
        if w['exclusive'].strip():
            ids = ['%s/weather/%s/%s' % (world, event, item.split(':')[0]) for item in w['exclusive'].split()]
            components.append({'type': 'filter', 'spawnDetailSelector': ' || '.join(ours % i for i in ids),
                               'spawnablePositionSelector': 'true', 'allow': here})
        write(os.path.join(RULES, event + '.json'), {
            'displayName': 'Ultra Space weather: %s (%s)' % (event, world), 'enabled': True, 'components': components})
    print('spawn details', count, 'rules', len(WEATHERS))


if __name__ == '__main__':
    main()
