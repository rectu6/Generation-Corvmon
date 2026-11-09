# Writes the default raid tables (pokemon.json, rewards.json) into the mod resources.
import json, os

OUT = "C:/Users/legam/Desktop/generation correction/project Corvemon/Generations-Core/common/src/main/resources/generations_core/raids"
os.makedirs(OUT, exist_ok=True)

def c(names):
    return ["cobblemon:" + n for n in names.split()]

OCEAN = ["#cobblemon:is_ocean", "#cobblemon:is_deep_ocean"]
RIVER = ["#cobblemon:is_river", "#cobblemon:is_freshwater"]
BEACH = ["#cobblemon:is_beach", "#cobblemon:is_coast"]
MOUNTAIN = ["#cobblemon:is_mountain", "#cobblemon:is_peak", "#cobblemon:is_hills", "#cobblemon:is_highlands", "#cobblemon:is_plateau"]
FOREST = ["#cobblemon:is_forest", "#cobblemon:is_taiga"]
JUNGLE = ["#cobblemon:is_jungle", "#cobblemon:is_tropical_island", "#cobblemon:is_bamboo"]
DESERT = ["#cobblemon:is_desert", "#cobblemon:is_badlands", "#cobblemon:is_arid"]
SNOWY = ["#cobblemon:is_snowy", "#cobblemon:is_freezing", "#cobblemon:is_tundra", "#cobblemon:is_glacial", "#cobblemon:is_frozen_ocean"]
SWAMP = ["#cobblemon:is_swamp"]
PLAINS = ["#cobblemon:is_plains", "#cobblemon:is_grassland", "#cobblemon:is_savanna", "#cobblemon:is_shrubland"]
FLORAL = ["#cobblemon:is_floral", "#cobblemon:is_cherry_blossom", "#cobblemon:is_magical"]
SPOOKY = ["#cobblemon:is_spooky"]
MUSHROOM = ["#cobblemon:is_mushroom"]
VOLCANIC = ["#cobblemon:is_volcanic", "#cobblemon:is_thermal"]
CAVE = ["#cobblemon:is_cave", "#cobblemon:is_lush", "#cobblemon:is_dripstone", "#cobblemon:is_deep_dark"]

tables = []
def add(biomes, stars, names, weight=10, gmax=False, exclude=None):
    entry = {"biomes": biomes, "species": c(names), "stars": stars, "weight": weight}
    if gmax:
        entry["gigantamax"] = True
    if exclude:
        entry["excludeBiomes"] = exclude
    tables.append(entry)

# Oceans: Water and sea Pokémon.
add(OCEAN, [1], "magikarp tentacool horsea shellder krabby finneon remoraid wingull clamperl wishiwashi")
add(OCEAN, [2], "staryu chinchou corsola qwilfish luvdisc wailmer frillish clauncher skrelp mantyke carvanha")
add(OCEAN, [3], "tentacruel seadra pelipper lanturn octillery lumineon clawitzer sharpedo huntail gorebyss bruxish")
add(OCEAN, [4], "starmie cloyster mantine wailord jellicent dragalge barraskewda relicanth toxapex dhelmise golisopod")
add(OCEAN, [4], "kingler", 4, gmax=True)
add(OCEAN, [5], "gyarados lapras kingdra milotic")
add(OCEAN, [5], "lapras", 4, gmax=True)

# Rivers and lakes.
add(RIVER, [1], "goldeen poliwag psyduck marill wooper lotad buizel tympole feebas surskit ducklett chewtle")
add(RIVER, [2], "seaking poliwhirl quagsire barboach basculin azumarill lombre palpitoad masquerain")
add(RIVER, [3], "golduck poliwrath whiscash floatzel swanna crawdaunt araquanid")
add(RIVER, [4], "politoed ludicolo seismitoad slowking")
add(RIVER, [4], "drednaw", 4, gmax=True)
add(RIVER, [5], "swampert milotic gyarados")

# Beaches and coasts.
add(BEACH, [1], "krabby wingull shellos corphish dwebble wimpod slowpoke sandygast mareanie")
add(BEACH, [2], "staryu corsola binacle pyukumuku")
add(BEACH, [3], "pelipper crawdaunt gastrodon palossand")
add(BEACH, [4], "slowbro crustle barbaracle golisopod")
add(BEACH, [4], "kingler drednaw", 4, gmax=True)
add(BEACH, [5], "gyarados lapras")

# Mountains, peaks and hills: Rock, Ground, Steel, Fighting.
add(MOUNTAIN, [1], "geodude machop roggenrola rockruff nosepass aron rolycoly nacli")
add(MOUNTAIN, [2], "graveler onix rhyhorn boldore lairon machoke tinkatuff")
add(MOUNTAIN, [3], "skarmory carkol bisharp naclstack rhydon lycanroc")
add(MOUNTAIN, [4], "golem steelix aggron gigalith probopass garganacl tinkaton")
add(MOUNTAIN, [4], "machamp coalossal corviknight", 4, gmax=True)
add(MOUNTAIN, [5], "tyranitar metagross salamence rhyperior kingambit")

# Forests and taigas: Grass, Bug and forest dwellers.
add(FOREST, [1], "caterpie weedle pidgey oddish sewaddle skwovet pikipek nickit hoothoot ledyba burmy seedot blipbug combee")
add(FOREST, [2], "butterfree beedrill gloom venonat pineco swadloon nuzleaf kricketune teddiursa dottler")
add(FOREST, [3], "noctowl stantler forretress vespiquen thievul venomoth ledian")
add(FOREST, [4], "vileplume scizor ursaring leavanny shiftry")
add(FOREST, [4], "butterfree orbeetle", 4, gmax=True)
add(FOREST, [5], "ursaluna kleavor")
add(FOREST, [5], "venusaur rillaboom", 4, gmax=True)

# Jungles and bamboo.
add(JUNGLE, [1], "paras spinarak grubbin bounsweet aipom venipede pikipek")
add(JUNGLE, [2], "ariados charjabug whirlipede steenee trumbeak")
add(JUNGLE, [3], "ambipom toucannon tropius lurantis")
add(JUNGLE, [4], "vikavolt tsareena scolipede oranguru passimian")
add(JUNGLE, [5], "tangrowth")
add(JUNGLE, [5], "rillaboom", 4, gmax=True)

# Deserts and badlands: Ground, Rock, Fire.
add(DESERT, [1], "sandshrew cacnea trapinch sandile silicobra diglett helioptile numel")
add(DESERT, [2], "dugtrio krokorok hippopotas vibrava phanpy")
add(DESERT, [3], "sandslash cacturne heliolisk maractus camerupt")
add(DESERT, [4], "flygon krookodile hippowdon donphan cofagrigus")
add(DESERT, [4], "sandaconda", 4, gmax=True)
add(DESERT, [5], "garchomp")

# Snowy biomes: Ice.
add(SNOWY, [1], "snover snorunt swinub spheal cubchoo vanillite snom bergmite frigibax")
add(SNOWY, [2], "piloswine sealeo vanillish sneasel cetoddle arctibax")
add(SNOWY, [3], "glalie froslass abomasnow frosmoth")
add(SNOWY, [4], "walrein avalugg weavile mamoswine beartic cetitan crabominable")
add(SNOWY, [5], "lapras baxcalibur vanilluxe")

# Swamps: Poison, Water.
add(SWAMP, [1], "grimer ekans croagunk wooper lotad tympole mareanie gulpin")
add(SWAMP, [2], "quagsire skrelp palpitoad koffing toedscool")
add(SWAMP, [3], "arbok swalot stunfisk seismitoad")
add(SWAMP, [3], "garbodor", 4, gmax=True)
add(SWAMP, [4], "muk weezing toxapex dragalge toxicroak toedscruel")
add(SWAMP, [5], "goodra swampert")

# Plains, savannas, grasslands.
add(PLAINS, [1], "rattata pidgey bunnelby lillipup wooloo yungoos fletchling eevee sentret zigzagoon starly patrat lechonk")
add(PLAINS, [2], "ponyta doduo herdier rufflet pidgeotto fletchinder staravia")
add(PLAINS, [3], "dodrio stoutland raticate girafarig dubwool pidgeot miltank")
add(PLAINS, [4], "rapidash staraptor braviary tauros bouffalant sawsbuck")
add(PLAINS, [4], "corviknight", 3, gmax=True)
add(PLAINS, [5], "arcanine slaking togekiss")
add(PLAINS, [5], "snorlax", 4, gmax=True)

# Flowers, cherry groves, magical places: Fairy.
add(FLORAL, [1], "flabebe cottonee budew hatenna milcery petilil fomantis clefairy")
add(FLORAL, [2], "floette roselia hattrem shiinotic")
add(FLORAL, [3], "whimsicott lilligant clefable")
add(FLORAL, [4], "roserade sylveon comfey")
add(FLORAL, [4], "alcremie hatterene", 4, gmax=True)
add(FLORAL, [5], "florges togekiss")

# Dark forests: Ghost.
add(SPOOKY, [1], "gastly shuppet duskull litwick phantump pumpkaboo sinistea greavard")
add(SPOOKY, [2], "haunter misdreavus sableye lampent")
add(SPOOKY, [3], "banette dusclops trevenant gourgeist houndstone")
add(SPOOKY, [4], "mismagius dusknoir chandelure polteageist aegislash")
add(SPOOKY, [4], "gengar", 4, gmax=True)
add(SPOOKY, [5], "dragapult hydreigon")

# Mushroom fields.
add(MUSHROOM, [1], "paras shroomish foongus morelull")
add(MUSHROOM, [2], "parasect shiinotic")
add(MUSHROOM, [3], "breloom amoonguss")
add(MUSHROOM, [4], "breloom amoonguss parasect")
add(MUSHROOM, [5], "blissey")

# Volcanic places: Fire.
add(VOLCANIC, [1], "slugma numel litleo salandit houndour sizzlipede rolycoly")
add(VOLCANIC, [2], "carkol pyroar")
add(VOLCANIC, [3], "torkoal camerupt magcargo salazzle")
add(VOLCANIC, [4], "magmar houndoom turtonator")
add(VOLCANIC, [4], "centiskorch coalossal", 4, gmax=True)
add(VOLCANIC, [5], "volcarona magmortar arcanine")

# Caves and lush caves (surface dens are rare there).
add(CAVE, [1], "zubat geodude noibat dunsparce")
add(CAVE, [2], "golbat graveler")
add(CAVE, [3], "crobat noivern")
add(CAVE, [4], "excadrill steelix")
add(CAVE, [5], "garchomp")

# Anywhere, when the biome has nothing for the tier.
fallback = []
def fb(stars, names, weight=10, gmax=False):
    entry = {"species": c(names), "stars": stars, "weight": weight}
    if gmax:
        entry["gigantamax"] = True
    fallback.append(entry)

fb([1], "rattata pidgey bidoof zigzagoon lechonk pikachu eevee")
fb([2], "growlithe ponyta meowth vulpix")
fb([2], "meowth", 3, gmax=True)
fb([3], "raichu persian electabuzz")
fb([3], "pikachu eevee", 3, gmax=True)
fb([4], "ninetales umbreon espeon")
fb([4], "venusaur charizard blastoise", 3, gmax=True)
fb([5], "dragonite snorlax togekiss garchomp tyranitar metagross")

# 6-star raids: legendaries and mythicals, by affinity, then anywhere.
legendaries = []
def lg(biomes, names, weight=5):
    entry = {"species": c(names), "stars": [6], "weight": weight}
    if biomes:
        entry["biomes"] = biomes
    legendaries.append(entry)

lg(OCEAN, "kyogre lugia latias latios tapufini")
lg(OCEAN, "manaphy phione", 2)
lg(RIVER, "suicune uxie mesprit azelf")
lg(DESERT + VOLCANIC, "groudon entei moltres heatran regirock")
lg(MOUNTAIN, "rayquaza registeel hooh dialga palkia regigigas cobalion terrakion")
lg(SNOWY, "articuno regice kyurem glastrier")
lg(PLAINS, "zapdos raikou landorus thundurus tornadus zacian zamazenta")
lg(FOREST + JUNGLE, "virizion xerneas tapukoko tapulele tapubulu")
lg(FOREST + JUNGLE + FLORAL, "celebi shaymin zarude", 2)
lg(SPOOKY, "giratina yveltal spectrier")
lg(SPOOKY, "darkrai marshadow", 2)
lg(FLORAL, "cresselia")
lg(FLORAL, "jirachi mew", 2)
lg([], "mewtwo", 3)

json.dump({"tables": tables, "fallback": fallback, "legendaries": legendaries},
          open(os.path.join(OUT, "pokemon.json"), "w", encoding="utf-8"), indent=2, ensure_ascii=False)

# ---- rewards ---------------------------------------------------------------------

def r(item, stars=None, weight=10, min_=1, max_=1, rarity="common", types=None):
    entry = {"item": item, "weight": weight, "min": min_, "max": max_, "rarity": rarity}
    if stars:
        entry["stars"] = stars
    if types:
        entry["types"] = types
    return entry

guaranteed = [
    r("cobblemon:exp_candy_xs", [1], 1.0, 3, 4),
    r("cobblemon:exp_candy_s", [2], 1.0, 3, 5),
    r("cobblemon:exp_candy_m", [3], 1.0, 3, 5),
    r("cobblemon:exp_candy_l", [4], 1.0, 3, 5),
    r("cobblemon:exp_candy_xl", [5], 1.0, 3, 5),
    r("cobblemon:exp_candy_xl", [6], 1.0, 5, 7),
    r("generations_core:dynamax_candy", [3], 0.5, 1, 1),
    r("generations_core:dynamax_candy", [4], 0.8, 1, 2),
    r("generations_core:dynamax_candy", [5], 1.0, 2, 3),
    r("generations_core:dynamax_candy", [6], 1.0, 3, 4),
    r("cobblemon:rare_candy", [5], 0.6, 1, 1, "rare"),
    r("cobblemon:rare_candy", [6], 1.0, 1, 2, "rare"),
]

star_rewards = [
    r("cobblemon:exp_candy_s", [1, 2], 30, 1, 2),
    r("cobblemon:exp_candy_m", [2, 3], 25, 1, 2),
    r("cobblemon:exp_candy_l", [4, 5], 20, 1, 2),
    r("cobblemon:exp_candy_xl", [5, 6], 15, 1, 2),
    r("generations_core:dynamax_candy", [2, 3, 4, 5, 6], 12),
    r("cobblemon:sitrus_berry", [1, 2, 3], 20, 1, 3),
    r("cobblemon:lum_berry", [2, 3, 4], 12, 1, 2),
    r("cobblemon:poke_ball", [1, 2], 15, 2, 4),
    r("cobblemon:great_ball", [2, 3], 12, 2, 3),
    r("cobblemon:ultra_ball", [3, 4, 5, 6], 10, 1, 3),
    r("cobblemon:hp_up", [2, 3, 4, 5, 6], 6),
    r("cobblemon:protein", [2, 3, 4, 5, 6], 6),
    r("cobblemon:iron", [2, 3, 4, 5, 6], 6),
    r("cobblemon:calcium", [2, 3, 4, 5, 6], 6),
    r("cobblemon:zinc", [2, 3, 4, 5, 6], 6),
    r("cobblemon:carbos", [2, 3, 4, 5, 6], 6),
    r("cobblemon:pp_up", [3, 4, 5, 6], 6, rarity="rare"),
    r("cobblemon:pp_max", [5, 6], 2, rarity="very_rare"),
    r("cobblemon:rare_candy", [3, 4, 5, 6], 5, rarity="rare"),
    r("cobblemon:ability_capsule", [4, 5, 6], 4, rarity="rare"),
    r("cobblemon:ability_patch", [5], 1, rarity="very_rare"),
    r("cobblemon:ability_patch", [6], 2, rarity="very_rare"),
    r("generations_core:bottle_cap", [4, 5, 6], 3, rarity="rare"),
    r("generations_core:max_mushrooms", [3, 4, 5, 6], 5, 1, 2, "rare"),
    r("generations_core:gold_bottle_cap", [6], 1, rarity="very_rare"),
    r("cobblemon:adamant_mint", [4, 5, 6], 2, rarity="rare"),
    r("cobblemon:modest_mint", [4, 5, 6], 2, rarity="rare"),
    r("cobblemon:jolly_mint", [4, 5, 6], 2, rarity="rare"),
    r("cobblemon:timid_mint", [4, 5, 6], 2, rarity="rare"),
    r("minecraft:emerald", [3, 4, 5, 6], 6, 1, 3),
    r("minecraft:diamond", [5, 6], 3, 1, 2, "rare"),
]

def t(types, items):
    out = []
    for item, stars, weight, mn, mx, rarity in items:
        out.append(r(item, stars, weight, mn, mx, rarity, types))
    return out

type_rewards = []
type_rewards += t(["fire"], [("cobblemon:charcoal_stick", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:blaze_powder", [1, 2, 3, 4, 5, 6], 12, 1, 3, "common"), ("cobblemon:fire_stone", [3, 4, 5, 6], 4, 1, 1, "rare")])
type_rewards += t(["water"], [("cobblemon:mystic_water", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:prismarine_shard", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common"), ("cobblemon:water_stone", [3, 4, 5, 6], 4, 1, 1, "rare")])
type_rewards += t(["grass"], [("cobblemon:miracle_seed", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:moss_block", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common"), ("cobblemon:leaf_stone", [3, 4, 5, 6], 4, 1, 1, "rare")])
type_rewards += t(["electric"], [("cobblemon:magnet", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:copper_ingot", [1, 2, 3, 4, 5, 6], 12, 2, 5, "common"), ("cobblemon:thunder_stone", [3, 4, 5, 6], 4, 1, 1, "rare")])
type_rewards += t(["ice"], [("cobblemon:never_melt_ice", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:packed_ice", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common"), ("cobblemon:ice_stone", [3, 4, 5, 6], 4, 1, 1, "rare")])
type_rewards += t(["fighting"], [("cobblemon:black_belt", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:leather", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common")])
type_rewards += t(["poison"], [("cobblemon:poison_barb", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:spider_eye", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common")])
type_rewards += t(["ground"], [("cobblemon:soft_sand", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:clay_ball", [1, 2, 3, 4, 5, 6], 12, 3, 6, "common")])
type_rewards += t(["flying"], [("cobblemon:sharp_beak", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:feather", [1, 2, 3, 4, 5, 6], 12, 3, 6, "common")])
type_rewards += t(["psychic"], [("cobblemon:twisted_spoon", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:ender_pearl", [2, 3, 4, 5, 6], 8, 1, 2, "common"), ("cobblemon:dawn_stone", [4, 5, 6], 3, 1, 1, "rare")])
type_rewards += t(["bug"], [("cobblemon:silver_powder", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:honeycomb", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common")])
type_rewards += t(["rock"], [("cobblemon:hard_stone", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:amethyst_shard", [1, 2, 3, 4, 5, 6], 12, 2, 4, "common")])
type_rewards += t(["ghost"], [("cobblemon:spell_tag", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:phantom_membrane", [2, 3, 4, 5, 6], 8, 1, 2, "common"), ("cobblemon:dusk_stone", [4, 5, 6], 3, 1, 1, "rare")])
type_rewards += t(["dragon"], [("cobblemon:dragon_fang", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("cobblemon:dragon_scale", [4, 5, 6], 3, 1, 1, "rare")])
type_rewards += t(["dark"], [("cobblemon:black_glasses", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:obsidian", [1, 2, 3, 4, 5, 6], 10, 1, 3, "common"), ("cobblemon:dusk_stone", [4, 5, 6], 3, 1, 1, "rare")])
type_rewards += t(["steel"], [("cobblemon:metal_coat", [1, 2, 3, 4, 5, 6], 8, 1, 1, "common"), ("minecraft:iron_ingot", [1, 2, 3, 4, 5, 6], 14, 2, 5, "common"), ("minecraft:gold_ingot", [3, 4, 5, 6], 6, 1, 3, "rare")])
type_rewards += t(["fairy"], [("cobblemon:fairy_feather", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:glow_berries", [1, 2, 3, 4, 5, 6], 12, 2, 5, "common"), ("cobblemon:moon_stone", [3, 4, 5, 6], 4, 1, 1, "rare"), ("cobblemon:shiny_stone", [4, 5, 6], 3, 1, 1, "rare")])
type_rewards += t(["normal"], [("cobblemon:silk_scarf", [1, 2, 3, 4, 5, 6], 10, 1, 1, "common"), ("minecraft:leather", [1, 2, 3, 4, 5, 6], 10, 2, 4, "common"), ("cobblemon:moon_stone", [3, 4, 5, 6], 3, 1, 1, "rare")])

json.dump({"starRolls": [2, 2, 3, 3, 4, 5], "typeRolls": [1, 1, 1, 2, 2, 2], "guaranteed": guaranteed, "starRewards": star_rewards, "typeRewards": type_rewards},
          open(os.path.join(OUT, "rewards.json"), "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(len(tables), len(fallback), len(legendaries), len(star_rewards), len(type_rewards))
