"""
RKS Machine recipes (RKS-PLAN.md, lots 1 and 2): moves the RKS recipes to the counted format
generations_core:rks_protocol.

- Removes the Mega Stone, Z-Crystal, Paradox, Koraidon and Miraidon recipes (decision D1),
  and the 150 recipe-unlock advancements of advancement/recipes/rks (they rewarded recipe ids
  that never existed: generations_core:<name> instead of generations_core:recipes/rks/<name>).
- Converts the kept recipes as they were (same items, same amounts, same results): Mewtwo,
  Type: Null, Magearna, Genesect (genetic); Sacred Ash, Wonder Egg, Soul Heart, the 28 Unown
  blocks grouped as "unown_blocks" (items). soul_heart_alt had the same items as soul_heart in
  another shape: once counted, it is the same recipe, so it is removed.
- Writes one fossil restoration per Cobblemon fossil (data/cobblemon/fossils in the Cobblemon jar):
  its fossils + 32 bone meal, 60 s; the result names the Cobblemon fossil, whose Pokemon is read
  from Cobblemon at the time of the restoration (decision D4).

Safe to run again: it only rewrites the RKS recipe folder.
Usage: python tools/rks_recipes.py <Cobblemon jar>
"""

import json
import os
import sys
import zipfile
from collections import OrderedDict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "generated", "resources", "data", "generations_core")
RECIPES = os.path.join(ROOT, "recipe", "recipes", "rks")
ADVANCEMENTS = os.path.join(ROOT, "advancement", "recipes", "rks")

PARADOX = {
    "brutebonnet", "fluttermane", "gougingfire", "greattusk", "ironboulder", "ironbundle", "ironcrown", "ironhands",
    "ironjugulis", "ironleaves", "ironmoth", "ironthorns", "irontreads", "ironvaliant", "ragingbolt", "roaringmoon",
    "sandyshocks", "screamtail", "slitherwing", "walkingwake", "koraidon", "miraidon",
}

GENETIC = {"mewtwo": 10, "type_null": 20, "magearna": 30, "genesect": 40}
ITEMS = {"rainbow_wing": ("sacred_ash", 10), "wonder_egg": ("wonder_egg", 20), "soul_heart": ("soul_heart", 30)}
DUPLICATES = {"soul_heart_alt"}

FOSSIL_COST = [{"ingredient": {"type": "generations_core:item", "items": "minecraft:bone_meal"}, "count": 32}]
FOSSIL_TICKS = 1200


def family(name, recipe):
    """What a recipe makes: pokemon:<species> or item:<id>."""
    result = recipe["result"]
    if result["type"] == "generations_core:pokemon":
        return "pokemon", result["species"].split(":")[1]
    stack = result.get("stack", {})
    return "item", (stack.get("id") or result.get("item") or "").split(":")[1]


def removed(name, recipe):
    kind, what = family(name, recipe)
    if kind == "pokemon":
        return what in PARADOX
    if what.startswith("unown_block"):
        return False
    return what.endswith("_z") or "ium_z" in what or what.endswith("ite") or what.endswith("ite_x") or what.endswith("ite_y")


def counted(recipe):
    """The recipe's ingredients with their amounts, in the order they first appear."""
    amounts = OrderedDict()
    if recipe["type"] == "generations_core:shaped_rks":
        key = recipe["pattern"]["key"]
        for row in recipe["pattern"]["pattern"]:
            for c in row:
                if c == " ":
                    continue
                ingredient = json.dumps(key[c], sort_keys=True)
                amounts[ingredient] = amounts.get(ingredient, 0) + 1
    else:
        for item in recipe["pattern"]:
            ingredient = json.dumps(item, sort_keys=True)
            amounts[ingredient] = amounts.get(ingredient, 0) + 1
    return [{"ingredient": json.loads(k), "count": v} for k, v in amounts.items()]


def protocol(category, order, ingredients, result, ticks, description, group=None, kind="standard", old=None):
    out = OrderedDict()
    out["type"] = "generations_core:rks_protocol"
    out["category"] = category
    out["order"] = order
    if group:
        out["group"] = group
    out["description"] = description
    if kind != "standard":
        out["protocol"] = kind
    out["ingredients"] = ingredients
    out["result"] = result
    out["processingTime"] = ticks
    if old is not None:
        if not old.get("consumesTimeCapsules", True):
            out["consumesTimeCapsules"] = False
        if "key" in old:
            out["key"] = old["key"]
        if old.get("experience", 1.0) != 1.0:
            out["experience"] = old["experience"]
    return out


def write(name, data):
    with open(os.path.join(RECIPES, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    jar = sys.argv[1]

    counts = {"removed": 0, "genetic": 0, "items": 0, "unown": 0, "fossil": 0, "already": 0}
    unown = []
    for file in sorted(os.listdir(RECIPES)):
        name = file[:-5]
        path = os.path.join(RECIPES, file)
        with open(path, encoding="utf-8") as f:
            recipe = json.load(f)
        if recipe["type"] == "generations_core:rks_protocol":
            if recipe.get("category") == "fossil":
                os.remove(path)  # rewritten from the jar below
            else:
                counts["already"] += 1
            continue
        if removed(name, recipe) or name in DUPLICATES:
            os.remove(path)
            counts["removed"] += 1
            continue
        kind, what = family(name, recipe)
        if name in GENETIC:
            data = protocol("genetic", GENETIC[name], counted(recipe), recipe["result"], recipe.get("processingTime", 240),
                            "generations_core.rks.recipe.%s.desc" % name, kind="mewtwo" if name == "mewtwo" else "standard", old=recipe)
            write(name, data)
            counts["genetic"] += 1
        elif name in ITEMS:
            new_name, order = ITEMS[name]
            data = protocol("items", order, counted(recipe), recipe["result"], recipe.get("processingTime", 240),
                            "generations_core.rks.recipe.%s.desc" % new_name, old=recipe)
            os.remove(path)
            write(new_name, data)
            counts["items"] += 1
        elif what.startswith("unown_block"):
            unown.append((name, recipe))
        else:
            raise SystemExit("Unclassified RKS recipe: " + name)

    # The 28 Unown blocks, one entry in the menu: a to z, then ! and ?.
    def unown_order(entry):
        letter = entry[0][len("unown_block_"):]
        return (len(letter) > 1, letter)
    for i, (name, recipe) in enumerate(sorted(unown, key=unown_order)):
        write(name, protocol("items", 100 + i, counted(recipe), recipe["result"], recipe.get("processingTime", 240),
                             "generations_core.rks.recipe.unown_blocks.desc", group="unown_blocks", old=recipe))
        counts["unown"] += 1

    with zipfile.ZipFile(jar) as z:
        fossils = sorted(n for n in z.namelist() if n.startswith("data/cobblemon/fossils/") and n.endswith(".json"))
        for i, entry in enumerate(fossils):
            fossil = os.path.basename(entry)[:-5]
            data = json.loads(z.read(entry).decode("utf-8"))
            amounts = OrderedDict()
            for item in data["fossils"]:
                amounts[item] = amounts.get(item, 0) + 1
            ingredients = [{"ingredient": {"type": "generations_core:item", "items": k}, "count": v} for k, v in amounts.items()]
            write("fossil_" + fossil, protocol("fossil", 10 * (i + 1), ingredients + FOSSIL_COST,
                                               {"type": "generations_core:fossil", "fossil": "cobblemon:" + fossil,
                                                "species": "cobblemon:" + data["result"].split()[0].lower()}, FOSSIL_TICKS,
                                               "generations_core.rks.recipe.fossil.desc"))
            counts["fossil"] += 1

    advancements = 0
    if os.path.isdir(ADVANCEMENTS):
        for file in os.listdir(ADVANCEMENTS):
            os.remove(os.path.join(ADVANCEMENTS, file))
            advancements += 1
        os.rmdir(ADVANCEMENTS)

    print("removed %(removed)d, genetic %(genetic)d, items %(items)d, unown %(unown)d, fossils %(fossil)d, already converted %(already)d" % counts)
    print("advancements removed: %d" % advancements)
    print("recipes now: %d" % len(os.listdir(RECIPES)))


if __name__ == "__main__":
    main()
