# Corrections des données de Generations

Erreurs présentes dans les logs avec Cobblemon 1.8.4, absentes depuis ces
corrections (vérifié en jeu le 2026-11-08).

## CT dans les coffres

Les tables `loot_table/chests/*_ball.json` donnaient les anciennes CT de
Generations (`generations_core:tm_1` à `tm_237`), retirées depuis que Cobblemon
1.8 a ses propres CT : 33 tables ne se chargeaient plus, ni les tables vanilla qui
les injectent. Chaque entrée est maintenant `cobblemon:technical_machine` avec
le composant `cobblemon:tm_move` de la même attaque. La correspondance numéro →
attaque vient de `GenerationsSchemas.TMS_TO_CONVERT_IN_1_8_COBBLEMON`, celle qui
convertit déjà les anciennes CT des inventaires. 656 entrées sont converties.

Ces fichiers sont dans `common/src/main/generated` : le générateur
`GenerationsChestLoot` ne les produit plus.

## Ultra pillars

`ultra_pillar` et `ultra_pillar_broken` étaient enregistrés et avaient leurs
textures, mais aucun blockstate ni modèle. Ils utilisent maintenant les modèles
de pilier communs (`pillar`, `pillar_partial`, `pillar_connected`) comme les
autres piliers, avec leur loot table. Les textures du pilier cassé ne suivent pas
son identifiant (`ultra_pillar_fractured`, `broken_ultra_pillar`) :
`BlockDatagen.registerPillar` accepte maintenant leurs noms.

## Urshifu Poing Final

`species_additions/generation8/urshifu_rapid.json` visait l'espèce
`cobblemon:urshifu_rapid`, qui n'existe pas (Poing Final est une forme
d'Urshifu), avec l'ancien format de sommeil. Urshifu a déjà son comportement de
sommeil : le fichier est supprimé.

## Preset `river`

Cobblemon 1.8 n'a plus de preset de spawn `river`. Tynamo, Eelektrik et
Eelektross (`spawn_pool_world/0602` à `0604`) l'utilisaient : il est remplacé par
la condition `"biomes": ["#cobblemon:is_river"]`.
