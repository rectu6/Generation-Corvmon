"""Writes the data of phases 4.4/4.5 into Generations-Core: damage types and tags, item
tags, recipes, item models, textures (pure-Python PNG) and the FR/EN texts.
Usage: python ultra_weather_data.py <Generations-Core>"""
import json, os, random, struct, sys, zlib

ROOT = sys.argv[1]
RES = os.path.join(ROOT, 'common', 'src', 'main', 'resources')
GEN = os.path.join(ROOT, 'common', 'src', 'main', 'generated', 'resources')
DATA = os.path.join(RES, 'data')
ASSETS = os.path.join(RES, 'assets', 'generations_core')


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write('\n')


SHIELDS = [  # id, colour, stone, material
    ('thermal', 0xFF8A3D, 'cobblemon:leaf_stone', 'generations_core:ultra_jungle_log'),
    ('sand', 0xE3C07A, 'cobblemon:sun_stone', 'generations_core:ultra_sandstone'),
    ('insulating', 0xFFE14D, 'cobblemon:thunder_stone', 'generations_core:charge_stone'),
    ('volcanic', 0xF0552E, 'cobblemon:fire_stone', 'generations_core:meteorite'),
    ('aerodynamic', 0xC6B5FF, 'cobblemon:shiny_stone', 'generations_core:ultra_dark_log'),
    ('filtration', 0x72E07C, 'cobblemon:moon_stone', 'generations_core:sapphire'),
    ('anticorrosion', 0xA5D84A, 'cobblemon:dawn_stone', 'generations_core:silicon'),
]
HAZARDS = [  # damage type, hurt effect
    ('extreme_heat', 'burning'), ('sandstorm', 'hurt'), ('electric_storm', 'hurt'), ('thermal_eruption', 'burning'),
    ('cutting_winds', 'poking'), ('toxic_mist', 'hurt'), ('acid_rain', 'burning'),
]

# ---- damage types and their tags ----
damage_ids = ['generations_core:ultra_suffocation'] + ['generations_core:ultra_' + h for h, _ in HAZARDS]
write_json(os.path.join(DATA, 'generations_core', 'damage_type', 'ultra_suffocation.json'),
           {'effects': 'drowning', 'exhaustion': 0.0, 'message_id': 'generations_core.ultra_suffocation', 'scaling': 'never'})
for hazard, effect in HAZARDS:
    value = {'exhaustion': 0.0, 'message_id': 'generations_core.ultra_' + hazard, 'scaling': 'never'}
    if effect != 'hurt':
        value['effects'] = effect
    write_json(os.path.join(DATA, 'generations_core', 'damage_type', 'ultra_' + hazard + '.json'), value)
for tag in ('bypasses_armor', 'no_knockback', 'bypasses_shield'):
    path = os.path.join(DATA, 'minecraft', 'tags', 'damage_type', tag + '.json')
    with open(path, encoding='utf-8') as f:
        value = json.load(f)
    for d in damage_ids:
        if d not in value['values']:
            value['values'].append(d)
    write_json(path, value)

# ---- item tags ----
shield_items = ['generations_core:%s_shield' % s for s, _, _, _ in SHIELDS]
write_json(os.path.join(DATA, 'generations_core', 'tags', 'item', 'ultra_shields.json'), {'values': shield_items})
write_json(os.path.join(DATA, 'generations_core', 'tags', 'item', 'ultra_suit.json'),
           {'values': ['generations_core:ultra_' + p for p in ('helmet', 'chestplate', 'leggings', 'boots')]})

# ---- recipes ----
for shield, _, stone, material in SHIELDS:
    write_json(os.path.join(DATA, 'generations_core', 'recipe', shield + '_shield.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment',
        'key': {'Z': {'item': 'generations_core:z_ingot'}, 'M': {'item': material}, 'S': {'item': stone}},
        'pattern': ['ZMZ', 'MSM', 'ZMZ'],
        'result': {'count': 1, 'id': 'generations_core:%s_shield' % shield},
    })
write_json(os.path.join(DATA, 'generations_core', 'recipe', 'ultra_shield_install.json'), {
    'type': 'generations_core:ultra_shield_smithing',
    'template': {'tag': 'generations_core:ultra_shields'},
    'base': {'tag': 'generations_core:ultra_suit'},
    'addition': {'item': 'generations_core:z_ingot'},
})

# ---- models ----
for shield, _, _, _ in SHIELDS:
    write_json(os.path.join(ASSETS, 'models', 'item', shield + '_shield.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'generations_core:item/ultra_shields/%s_shield' % shield}})


# ---- textures ----
def png(path, width, height, pixels):
    """pixels: list of rows of (r, g, b, a)."""
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in pixels)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


# A heater shield: rows of (first column, last column).
SHAPE = {1: (2, 13), 2: (1, 14), 3: (1, 14), 4: (1, 14), 5: (1, 14), 6: (1, 14), 7: (1, 14), 8: (1, 14),
         9: (2, 13), 10: (2, 13), 11: (3, 12), 12: (4, 11), 13: (5, 10), 14: (6, 9), 15: (7, 8)}
SYMBOLS = {  # 8x8, placed at (4, 3)
    'thermal': ['...#....', '...##...', '..###...', '..####..', '.##.##..', '.#...#..', '.##.##..', '..###...'],
    'sand': ['........', '.##..##.', '#..##..#', '........', '.##..##.', '#..##..#', '........', '........'],
    'insulating': ['....##..', '...##...', '..##....', '.######.', '....##..', '...##...', '..##....', '.##.....'],
    'volcanic': ['..#..#..', '...##...', '...##...', '..####..', '..####..', '.######.', '.######.', '########'],
    'aerodynamic': ['######..', '......#.', '.####..#', '.....#..', '#######.', '........', '.#####..', '......#.'],
    'filtration': ['#.#.#.#.', '.#.#.#.#', '#.#.#.#.', '.#.#.#.#', '#.#.#.#.', '.#.#.#.#', '#.#.#.#.', '.#.#.#.#'],
    'anticorrosion': ['...##...', '...##...', '..####..', '.##..##.', '.#....#.', '.#....#.', '.##..##.', '..####..'],
}


def mix(c, factor):
    return tuple(max(0, min(255, int(v * factor))) for v in c)


def towards(c, d, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c, d))


for shield, colour, _, _ in SHIELDS:
    base = ((colour >> 16) & 255, (colour >> 8) & 255, colour & 255)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y, (x0, x1) in SHAPE.items():
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y == 1 or y == 15 or (y + 1 in SHAPE and not (SHAPE[y + 1][0] <= x <= SHAPE[y + 1][1]))
            if edge:
                c = mix(base, 0.32)
            else:
                rim = x in (x0 + 1, x1 - 1) or y == 2
                light = 1.15 - (x + y) * 0.018
                c = mix(base, light)
                if rim:
                    c = towards(c, (255, 255, 255), 0.35)
            pixels[y][x] = c + (255,)
    for j, row in enumerate(SYMBOLS[shield]):
        for i, cell in enumerate(row):
            x, y = 4 + i, 3 + j
            if cell == '#' and pixels[y][x][3]:
                pixels[y][x] = towards(base, (255, 255, 248), 0.85) + (255,)
            elif cell == '.' and pixels[y][x][3] and j > 0 and SYMBOLS[shield][j - 1][i] == '#':
                pixels[y][x] = mix(base, 0.62) + (255,)
    png(os.path.join(ASSETS, 'textures', 'item', 'ultra_shields', shield + '_shield.png'), 16, 16, pixels)

# The acid rain: Minecraft's rain sheet (64 x 256) in a sickly green.
rng = random.Random(4404)
rain = [[(0, 0, 0, 0)] * 64 for _ in range(256)]
for _ in range(220):
    x, y, length = rng.randrange(64), rng.randrange(256), rng.randrange(6, 16)
    shade = rng.uniform(0.75, 1.0)
    for k in range(length):
        a = int(200 * (1 - k / length) * shade)
        rain[(y + k) % 256][x] = (int(176 * shade), int(232 * shade), int(84 * shade), max(rain[(y + k) % 256][x][3], a))
png(os.path.join(ASSETS, 'textures', 'environment', 'ultra_acid_rain.png'), 64, 256, rain)

# ---- texts ----
EVENTS = {
    'tropical_rain': ('Pluies tropicales', 'Tropical Rain'), 'extreme_heat': ('Canicule extrême', 'Extreme Heatwave'),
    'intense_sun': ('Soleil intense', 'Intense Sun'), 'sandstorm': ('Tempête de sable', 'Sandstorm'),
    'energy_surge': ('Surcharge énergétique', 'Energy Surge'), 'electric_storm': ('Orage électrique', 'Electric Storm'),
    'ash_rain': ('Pluie de cendres', 'Ash Rain'), 'thermal_eruption': ('Éruption thermique', 'Thermal Eruption'),
    'dimensional_mist': ('Brume dimensionnelle', 'Dimensional Mist'), 'cutting_winds': ('Vents tranchants', 'Cutting Winds'),
    'crystal_rain': ('Pluie cristalline', 'Crystal Rain'), 'toxic_mist': ('Brume toxique', 'Toxic Mist'),
    'dimensional_darkness': ('Obscurité dimensionnelle', 'Dimensional Darkness'), 'acid_rain': ('Pluie acide', 'Acid Rain'),
}
STARTS = {
    'tropical_rain': ("Des pluies tropicales chaudes s'abattent sur l'Ultra-Jungle.", 'Warm tropical rain pours over the Ultra Jungle.'),
    'extreme_heat': ("Une canicule extrême écrase l'Ultra-Jungle !", 'An extreme heatwave crushes the Ultra Jungle!'),
    'intense_sun': ("Un soleil intense inonde l'Ultra-Désert de lumière.", 'An intense sun floods the Ultra Desert with light.'),
    'sandstorm': ("Une tempête de sable se lève sur l'Ultra-Désert !", 'A sandstorm rises over the Ultra Desert!'),
    'energy_surge': ("Une surcharge énergétique parcourt l'Ultra-Centrale.", 'An energy surge runs through the Ultra Plant.'),
    'electric_storm': ("Un orage électrique éclate sur l'Ultra-Centrale !", 'An electric storm breaks over the Ultra Plant!'),
    'ash_rain': ("Une pluie de cendres tombe sur l'Ultra-Cratère.", 'Ash falls over the Ultra Crater.'),
    'thermal_eruption': ("Une éruption thermique secoue l'Ultra-Cratère !", 'A thermal eruption shakes the Ultra Crater!'),
    'dimensional_mist': ("Une brume dimensionnelle envahit l'Ultra-Forêt.", 'A dimensional mist fills the Ultra Forest.'),
    'cutting_winds': ("Des vents tranchants balaient l'Ultra-Forêt !", 'Cutting winds sweep the Ultra Forest!'),
    'crystal_rain': ("Une pluie cristalline tombe des voûtes de l'Ultra-Abysse.", "A crystal rain falls from the Ultra Abyss' vaults."),
    'toxic_mist': ("Une brume toxique envahit les cavernes de l'Ultra-Abysse !", "A toxic mist fills the Ultra Abyss' caverns!"),
    'dimensional_darkness': ("Une obscurité dimensionnelle recouvre l'Ultra-Gratte-Ciel.", 'A dimensional darkness covers the Ultra Megalopolis.'),
    'acid_rain': ("Une pluie acide s'abat sur l'Ultra-Gratte-Ciel !", 'Acid rain pours over the Ultra Megalopolis!'),
}
ADVICE = {
    'extreme_heat': ("Restez à l'ombre, sous terre ou dans l'eau, ou équipez le bouclier thermique.", 'Stay in the shade, underground or in water, or wear the thermal shield.'),
    'sandstorm': ('Abritez-vous dans une construction ou une caverne, ou équipez le bouclier anti-sable.', 'Shelter in a building or a cave, or wear the sand shield.'),
    'electric_storm': ("Restez sous un toit plein, hors de l'eau, ou équipez le bouclier isolant.", 'Stay under a solid roof, out of the water, or wear the insulating shield.'),
    'thermal_eruption': ("Réfugiez-vous dans un abri fermé, sous terre ou dans l'eau, ou équipez le bouclier volcanique.", 'Take refuge in a closed shelter, underground or in water, or wear the volcanic shield.'),
    'cutting_winds': ('Entrez dans un bâtiment ou une grotte, ou équipez le bouclier aérodynamique.', 'Get into a building or a cave, or wear the aerodynamic shield.'),
    'toxic_mist': ('Enfermez-vous dans un espace clos (portes fermées), ou équipez le bouclier de filtration.', 'Shut yourself in a closed space (doors closed), or wear the filtration shield.'),
    'acid_rain': ('Mettez-vous sous un toit, ou équipez le bouclier anticorrosion.', 'Get under a roof, or wear the anticorrosion shield.'),
}
DEATHS = {
    'ultra_suffocation': ('%1$s a suffoqué dans un Ultra-Monde', '%1$s suffocated in an Ultra world'),
    'ultra_extreme_heat': ('%1$s a succombé à la canicule extrême', '%1$s succumbed to the extreme heatwave'),
    'ultra_sandstorm': ('%1$s a été emporté par la tempête de sable', '%1$s was swept away by the sandstorm'),
    'ultra_electric_storm': ("%1$s a été foudroyé par l'orage électrique", '%1$s was struck down by the electric storm'),
    'ultra_thermal_eruption': ("%1$s a été consumé par l'éruption thermique", '%1$s was consumed by the thermal eruption'),
    'ultra_cutting_winds': ('%1$s a été lacéré par les vents tranchants', '%1$s was cut to pieces by the cutting winds'),
    'ultra_toxic_mist': ('%1$s a respiré la brume toxique', '%1$s breathed the toxic mist'),
    'ultra_acid_rain': ('%1$s a été rongé par la pluie acide', '%1$s was eaten away by the acid rain'),
}
SHIELD_NAMES = {
    'thermal': ('Bouclier thermique', 'Thermal Shield', 'Thermique', 'Thermal'),
    'sand': ('Bouclier anti-sable', 'Sand Shield', 'Anti-sable', 'Sand'),
    'insulating': ('Bouclier isolant', 'Insulating Shield', 'Isolant', 'Insulating'),
    'volcanic': ('Bouclier volcanique', 'Volcanic Shield', 'Volcanique', 'Volcanic'),
    'aerodynamic': ('Bouclier aérodynamique', 'Aerodynamic Shield', 'Aérodynamique', 'Aerodynamic'),
    'filtration': ('Bouclier de filtration', 'Filtration Shield', 'Filtration', 'Filtration'),
    'anticorrosion': ('Bouclier anticorrosion', 'Anticorrosion Shield', 'Anticorrosion', 'Anticorrosion'),
}
OTHER = {
    'generations_core.ultra_shield.tooltip.protects': ('Protège de : %1$s (%2$s)', 'Protects against: %1$s (%2$s)'),
    'generations_core.ultra_shield.tooltip.pieces': ('Protection complète : ce bouclier sur les quatre pièces', 'Full protection: this shield on all four pieces'),
    'generations_core.ultra_shield.tooltip.install': ("S'installe à la table de forge : bouclier + pièce Ultra + Lingot Z", 'Installed at the smithing table: shield + Ultra piece + Z Ingot'),
    'generations_core.ultra_shield.tooltip.breathing': ('Ne remplace pas la combinaison pour respirer', 'Does not replace the suit for breathing'),
    'generations_core.ultra_shield.jei': ("Bouclier environnemental de l'Ultra-Combinaison. À la table de forge, placez le bouclier (case du modèle), une pièce de l'Ultra-Combinaison (case de base) et un Lingot Z (case d'ajout) : la pièce garde ses enchantements et reçoit le bouclier. Les sept boucliers se cumulent sur une même pièce. Un bouclier ne protège de la météo dangereuse de son Ultra-Monde que s'il est installé sur les quatre pièces portées.",
                                          "An environmental shield of the Ultra Suit. At the smithing table, put the shield (template slot), a piece of the Ultra Suit (base slot) and a Z Ingot (addition slot): the piece keeps its enchantments and gets the shield. The seven shields stack on the same piece. A shield protects against its Ultra world's dangerous weather only when installed on all four pieces worn."),
    'generations_core.ultra_suit.tooltip.breathing': ('Ultra-Combinaison : respiration dans les Ultra-Mondes (4 pièces)', 'Ultra Suit: breathing in the Ultra worlds (4 pieces)'),
    'generations_core.ultra_suit.tooltip.none': ('Aucun bouclier installé', 'No shield installed'),
    'generations_core.ultra_suit.tooltip.install': ('Boucliers : table de forge + Lingot Z', 'Shields: smithing table + Z Ingot'),
    'generations_core.ultra_suit.tooltip.shields': ('Boucliers installés (%1$s/%2$s) :', 'Shields installed (%1$s/%2$s):'),
    'generations_core.ultra_suit.no_air': ("L'air de l'Ultra-Monde est irrespirable : équipez les quatre pièces de l'Ultra-Combinaison !", "The Ultra world's air cannot be breathed: wear all four pieces of the Ultra Suit!"),
    'generations_core.ultra_suit.warning': ("Attention : sans l'Ultra-Combinaison complète, vous ne pourrez pas respirer ici.", 'Careful: without the full Ultra Suit, you cannot breathe here.'),
    'generations_core.ultra_suit.grace': ("Air respirable encore %s s : équipez l'Ultra-Combinaison complète !", 'Breathable air for %s more seconds: wear the full Ultra Suit!'),
    'generations_core.ultra_weather.end': ('%s se dissipe.', '%s fades away.'),
    'generations_core.ultra_weather.warning': ('%1$s approche : abritez-vous ! (%2$s s)', '%1$s is coming: take shelter! (%2$s s)'),
    'generations_core.ultra_weather.hud.harmless': ('Sans danger', 'Harmless'),
    'generations_core.ultra_weather.hud.grace': ('Répit : %s s', 'Respite: %s s'),
    'generations_core.ultra_weather.hud.sheltered': ("À l'abri", 'Sheltered'),
    'generations_core.ultra_weather.hud.shielded': ('Protégé (bouclier %s)', 'Protected (%s shield)'),
    'generations_core.ultra_weather.hud.partial': ('Bouclier incomplet : %s/4 pièces', 'Incomplete shield: %s/4 pieces'),
    'generations_core.ultra_weather.hud.exposed': ('Exposé !', 'Exposed!'),
    'generations_core.ultra_weather.hud.shelter.shade': ('Abri : ombre, sous terre ou eau', 'Shelter: shade, underground or water'),
    'generations_core.ultra_weather.hud.shelter.building': ('Abri : construction ou caverne', 'Shelter: building or cave'),
    'generations_core.ultra_weather.hud.shelter.solid_roof': ("Abri : toit plein, hors de l'eau", 'Shelter: solid roof, out of the water'),
    'generations_core.ultra_weather.hud.shelter.heat_refuge': ('Abri : refuge fermé, sous terre ou eau', 'Shelter: closed refuge, underground or water'),
    'generations_core.ultra_weather.hud.shelter.sealed': ('Abri : espace clos, portes fermées', 'Shelter: closed space, doors shut'),
    'generations_core.ultra_weather.hud.shelter.roof': ('Abri : toit (pas de feuillage)', 'Shelter: a roof (not leaves)'),
    'generations_core.ultra_weather.status.unknown': ('%s : inconnu', '%s: unknown'),
    'generations_core.ultra_weather.status.off': ('%s : météo désactivée', '%s: weather off'),
    'generations_core.ultra_weather.status.active': ('%1$s : %2$s (encore %3$s min)', '%1$s: %2$s (%3$s min left)'),
    'generations_core.ultra_weather.status.calm': ('%1$s : accalmie, puis %2$s dans %3$s min', '%1$s: calm, then %2$s in %3$s min'),
    'generations_core.ultra_weather.command.title': ('Météo des Ultra-Mondes :', 'Weather of the Ultra worlds:'),
    'generations_core.ultra_weather.command.unknown_world': ('Monde inconnu : %1$s (mondes : %2$s)', 'Unknown world: %1$s (worlds: %2$s)'),
    'generations_core.ultra_weather.command.unknown_event': ('Météo inconnue : %1$s (possibles : %2$s)', 'Unknown weather: %1$s (possible: %2$s)'),
    'generations_core.ultra_weather.command.cleared': ('%s : accalmie.', '%s: calm.'),
    'generations_core.ultra_weather.command.set': ('%1$s : %2$s commence.', '%1$s: %2$s begins.'),
    'generations_core.ultra_suit.command.pieces': ('Ultra-Combinaison : %1$s/4 pièces portées, air %2$s/%3$s', 'Ultra Suit: %1$s/4 pieces worn, air %2$s/%3$s'),
    'generations_core.ultra_suit.command.shield': ('%1$s : %2$s/4 pièces', '%1$s: %2$s/4 pieces'),
    'generations_core.ultra_suit.command.unknown_shield': ('Bouclier inconnu (possibles : %s)', 'Unknown shield (possible: %s)'),
    'generations_core.ultra_suit.command.not_a_piece': ("Tenez une pièce de l'Ultra-Combinaison dans la main principale.", 'Hold a piece of the Ultra Suit in your main hand.'),
    'generations_core.ultra_suit.command.added': ('Bouclier %s installé.', '%s shield installed.'),
    'generations_core.ultra_suit.command.removed': ('Bouclier %s retiré.', '%s shield removed.'),
    'generations_core.ultra_suit.command.given': ('Ultra-Combinaison complète avec les sept boucliers donnée.', 'Full Ultra Suit with the seven shields given.'),
}

fr, en = {}, {}
for key, (f, e, sf, se) in SHIELD_NAMES.items():
    fr['item.generations_core.%s_shield' % key], en['item.generations_core.%s_shield' % key] = f, e
    fr['generations_core.ultra_shield.' + key], en['generations_core.ultra_shield.' + key] = sf, se
for key, (f, e) in EVENTS.items():
    fr['generations_core.ultra_weather.event.' + key], en['generations_core.ultra_weather.event.' + key] = f, e
for key, (f, e) in STARTS.items():
    fr['generations_core.ultra_weather.start.' + key], en['generations_core.ultra_weather.start.' + key] = f, e
for key, (f, e) in ADVICE.items():
    fr['generations_core.ultra_weather.advice.' + key], en['generations_core.ultra_weather.advice.' + key] = f, e
for key, (f, e) in DEATHS.items():
    fr['death.attack.generations_core.' + key], en['death.attack.generations_core.' + key] = f, e
    fr['death.attack.generations_core.%s.player' % key] = f + ' en fuyant %2$s'
    en['death.attack.generations_core.%s.player' % key] = e + ' whilst fleeing %2$s'
for key, (f, e) in OTHER.items():
    fr[key], en[key] = f, e


def add_texts(path, texts):
    with open(path, encoding='utf-8') as f:
        text = f.read()
    existing = json.loads(text)
    new = {k: v for k, v in texts.items() if k not in existing}
    body = text.rstrip()
    assert body.endswith('}')
    body = body[:-1].rstrip()
    # The indentation of the file's entries.
    last = body.splitlines()[-1]
    indent = last[:len(last) - len(last.lstrip())]
    lines = [indent + json.dumps(k, ensure_ascii=False) + ': ' + json.dumps(v, ensure_ascii=False) for k, v in new.items()]
    if lines:
        body += ',\n' + ',\n'.join(lines)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(body + '\n}\n')
    json.loads(open(path, encoding='utf-8').read())
    return len(new)


print('fr', add_texts(os.path.join(ASSETS, 'lang', 'fr_fr.json'), fr))
print('en', add_texts(os.path.join(GEN, 'assets', 'generations_core', 'lang', 'en_us.json'), en))
print('ok')
