# Configuration des raids

Dossier `config/generations/core/raids/` (côté serveur). Les fichiers sont créés avec
les valeurs par défaut au premier lancement et relus à chaque démarrage du serveur.

- Une **erreur de syntaxe** dans un fichier est écrite dans les logs (« Raids: … is
  not valid JSON ») et ce fichier garde ses valeurs par défaut : pas de crash.
- Chaque valeur est **vérifiée** et ramenée dans un intervalle sûr (par exemple les
  IV parfaits entre 0 et 6).
- Une **espèce ou un objet inconnu** est ignoré avec un avertissement dans les logs.
- **Migration** : à la première création, `tiers.json` reprend les anciens
  multiplicateurs de PV (`raids.bossHealth` de `main.json`) et `capture.json` les
  anciens taux de capture 5★ et 6★.

Pour repartir des valeurs par défaut, supprimer le fichier : il est recréé au
démarrage suivant.

## `dens.json` — antres et réapparition

| Clé | Défaut | Effet |
|---|---|---|
| `denSpawnRarity` | `48` | Nombre moyen de chunks par antre |
| `minDistanceBetweenDens` | `96` | Distance minimale entre deux antres (blocs) |
| `allowWaterDens` | `true` | Antres aussi à la surface des océans, rivières et lacs |
| `denRespawnChance` | `0.75` | Chance, chaque matin, qu'un antre inactif reçoive un boss |
| `denRespawnTime` | `0` | Heure de la tentative, en ticks de la journée (0 = lever du soleil, 6000 = midi) |
| `starWeights` | `[42, 27, 18, 9, 3.5, 0.5]` | Poids des raids 1★ à 6★ |
| `legendaryWeightMultiplier` | `1.0` | Multiplie le poids des raids 6★ (légendaires) |
| `denDropsItem` | `false` | Un antre cassé redonne son bloc (non par défaut : pas de duplication) |
| `fleeEndsRaid` | `true` | Si tous les joueurs fuient ou se déconnectent, le raid est perdu et l'antre s'éteint |
| `wishingChipStars` | `[1, 2]` | Difficultés (min, max) du raid qu'une Puce Vœu fait apparaître dans un antre inactif |
| `wishingPieceStars` | `[3, 4]` | Même chose pour un Morceau Vœu |
| `wishingStarStars` | `[5, 5]` | Même chose pour une Étoile Vœu |
| `wishingStarLegendaryChance` | `0.01` | Chance qu'une Étoile Vœu fasse apparaître un raid 6★ légendaire à la place |

`denSpawnRarity` et `minDistanceBetweenDens` agissent sur les **nouveaux** chunks.
Une clé absente du fichier (ajoutée par une version plus récente) garde sa valeur par
défaut : l'ajouter au fichier pour la changer.

## `tiers.json` — les 6 niveaux de difficulté

Une entrée par étoile, dans l'ordre (1★ à 6★) :

| Clé | 1★ | 2★ | 3★ | 4★ | 5★ | 6★ | Effet |
|---|---|---|---|---|---|---|---|
| `minLevel` / `maxLevel` | 15–20 | 25–30 | 35–40 | 45–50 | 55–65 | 70–75 | Niveau du boss |
| `hpMultiplier` | 3 | 4 | 5 | 6 | 7 | 8 | PV du boss × |
| `perfectIvs` | 1 | 2 | 3 | 4 | 5 | 5 | IV à 31 garantis (statistiques différentes, au hasard ; 0 à 6) |
| `shinyRate` | 4096 | 4096 | 2048 | 1024 | 1024 | 512 | Chance shiny : 1 sur N |
| `shieldSegments` | 0 | 0 | 2 | 3 | 4 | 5 | Segments du bouclier (0 : pas de bouclier) |
| `maxAttacks` | 1 | 1 | 2 | 2 | 3 | 3 | Attaques maximum du boss par tour |
| `extraAttackChance` | 0 | 0 | 0.25 | 0.35 | 0.3 | 0.35 | Chance de chaque attaque au-delà de la première |
| `aiSkill` | 1 | 2 | 3 | 3 | 4 | 5 | Habileté de l'IA du boss (0 à 5) |
| `npcLevelOffset` | 0 | 0 | 0 | 0 | 0 | 0 | Niveau des Pokémon des PNJ = niveau max du raid + ce décalage |

`shockwave` (par étoile) :

| Clé | 3★ | 4★ | 5★ | 6★ | Effet |
|---|---|---|---|---|---|
| `enabled` | oui | oui | oui | oui | Ondes de choc possibles (non à 1★ et 2★) |
| `chance` | 0.15 | 0.2 | 0.25 | 0.3 | Chance par tour, une fois les conditions réunies |
| `maxUses` | 1 | 2 | 2 | 3 | Ondes de choc maximum par raid |
| `cooldownTurns` | 4 | 3 | 3 | 2 | Tours minimum entre deux ondes |
| `belowHealth` | 0.75 | 0.9 | 1.0 | 1.0 | Seulement sous cette part de PV du boss |

## `battle.json` — règles du combat

| Clé | Défaut | Effet |
|---|---|---|
| `maxTurns` | `10` | Tours avant la défaite |
| `maxFaints` | `4` | K.O. cumulés (tous participants) avant la défaite |
| `lobbySeconds` | `60` | Temps du salon avant le départ automatique |
| `turnSeconds` | `60` | En multijoueur, temps pour choisir avant qu'une attaque soit choisie à la place du joueur |

`shield` :

| Clé | Défaut | Effet |
|---|---|---|
| `thresholds` | `[0.5]` | Parts des PV où un bouclier se lève (une phase chacune, ex. `[0.6, 0.3]`) |
| `damageReduction` | `0.75` | Réduction des dégâts subis par le boss pendant le bouclier |
| `segmentsPerHit` | `1` | Segments brisés par attaque réussie |
| `maxMoveSegments` | `2` | Segments brisés par une capacité Max ou Gigamax |
| `multiHitBreaksPerHit` | `false` | Coups multiples : un segment par coup (`true`) ou par attaque (`false`) |
| `blockStatusMoves` | `true` | Les capacités de statut visant le boss échouent pendant le bouclier |
| `breakDefenseDrop` | `0` | Niveaux de Défense et Défense Spéciale perdus par le boss quand le bouclier se brise |

`shockwave` :

| Clé | Défaut | Effet |
|---|---|---|
| `clearBoosts` | `true` | Annule les changements de statistiques des participants |
| `suppressAbilities` | `true` | Neutralise les talents des participants (comme Suc Digestif) |
| `abilityTurns` | `2` | Tours de neutralisation des talents |
| `cureBossStatus` | `true` | Le boss guérit de son statut (paralysie, brûlure, poison, sommeil, gel) |

`cheers` (encouragements) :

| Clé | Défaut | Effet |
|---|---|---|
| `boostWeight` | `40` | Poids : une statistique au hasard monte pour les alliés |
| `cureWeight` | `25` | Poids : les statuts des alliés sont soignés |
| `healWeight` | `25` | Poids : les alliés récupèrent des PV |
| `nothingWeight` | `10` | Poids : aucun effet |
| `boostStages` | `1` | Niveaux de la statistique augmentée |
| `healShare` | `0.2` | Part des PV max rendus |
| `npcCheer` | `true` | Les PNJ K.O. encouragent d'eux-mêmes |
| `cheerSeconds` | `8` | Temps laissé à un joueur K.O. pour encourager |

## `capture.json` — la capture

| Clé | Défaut | Effet |
|---|---|---|
| `baseChance` | `[1, 1, 1, 1, 0.7, 0.35]` | Chance de base de 1★ à 6★ |
| `ballMultipliers` | Super Ball 1.2, Hyper Ball 1.4, Sombre Ball 1.4, Rapide Ball 1.2, Chrono Ball 1.4, Ultra Ball 0.5 | Multiplicateur par Poké Ball (identifiant d'objet) |
| `defaultBallMultiplier` | `1.0` | Multiplicateur des autres Poké Balls |
| `masterBallAlwaysCatches` | `true` | La Master Ball réussit toujours |
| `shinyMultiplier` / `legendaryMultiplier` | `1.0` | Multiplicateurs pour un boss shiny ou légendaire |
| `forbiddenBalls` | vides | Poké Balls interdites, une liste par étoile (`[[], [], [], [], [], ["cobblemon:master_ball"]]`) |
| `faintedPlayersEligible` | `true` | Un joueur dont le Pokémon est K.O. à la fin peut quand même capturer |

Chance finale = base × multiplicateur de la Poké Ball × multiplicateurs du boss,
plafonnée à 100 %. Chaque joueur a sa propre tentative, une seule.

## `pokemon.json` — tables par biome

Trois listes : `tables` (par biome), `fallback` (si le biome n'a rien pour la
difficulté), `legendaries` (raids 6★). Chaque entrée :

| Clé | Exemple | Effet |
|---|---|---|
| `biomes` | `["#cobblemon:is_ocean", "minecraft:river"]` | Biomes ou tags ; vide = partout |
| `excludeBiomes` | `["#minecraft:is_deep_ocean"]` | Biomes exclus |
| `species` | `"cobblemon:lapras"` ou une liste | Espèce(s) |
| `aspects` | `["alolan"]` | Forme (aspects Cobblemon) |
| `stars` | `[4, 5]` | Difficultés autorisées |
| `weight` | `10` | Poids d'apparition |
| `minLevel` / `maxLevel` | `50` / `55` | Niveaux (sinon ceux de la difficulté) |
| `gigantamax` | `true` | Facteur Gigamax (si l'espèce a une forme Gigamax) |

Les biomes moddés sont couverts par les tags de Cobblemon (`cobblemon:is_…`, qui
incluent les tags `c:`) ; on peut aussi ajouter des entrées avec leurs
identifiants. Si rien ne correspond, une espèce de la difficulté est tirée selon
le total de ses statistiques (légendaires à 6★).

## `rewards.json` — récompenses

| Clé | Défaut | Effet |
|---|---|---|
| `starRolls` | `[2, 2, 3, 3, 4, 5]` | Tirages dans `starRewards` par difficulté |
| `typeRolls` | `[1, 1, 1, 2, 2, 2]` | Tirages dans `typeRewards` par difficulté |
| `guaranteed` | Bonbons Exp., Bonbons Dynamax, Super Bonbons | Donnés selon leur `weight` = chance de 0 à 1 |
| `starRewards` | vitamines, PP Plus, Capsule Talent, Patch Talent, Capsules d'Argent et d'Or, menthes, Maxi Champis… | Tirés au poids |
| `typeRewards` | objets et ressources par type (Charbon pour Feu, Eau Mystique pour Eau…) | Tirés au poids selon les types du boss |

Chaque entrée : `item`, `stars` (vide = toutes), `types` (vide = tous), `weight`,
`min`, `max`, `rarity` (`common`, `rare`, `very_rare`, informatif).

## Génération des antres (datapack)

`data/generations_core/worldgen/placed_feature/raid_den.json` : la feature
`generations_core:raid_den` choisit elle-même son chunk et sa position ; la rareté
se règle dans `dens.json`.

## Régénérer les tables par défaut

`python tools/raid_tables.py` réécrit `pokemon.json` et `rewards.json` dans les
ressources du mod (les valeurs par défaut ; les fichiers du dossier config ne sont
pas touchés).
