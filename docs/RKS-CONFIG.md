# RKS Machine : recettes et configuration

Documentation de la refonte RKS (plan : `RKS-PLAN.md` hors du dépôt). Complétée à chaque étape.

## 1. Recettes (`data/generations_core/recipe/recipes/rks/*.json`)

Format `generations_core:rks_protocol`, modifiable par pack de données et rechargé par `/reload` :

| Champ | Obligatoire | Rôle |
|---|---|---|
| `category` | oui | `genetic`, `fossil` ou `items` : la famille du menu |
| `order` | non (0) | tri dans la famille |
| `group` | non | variantes regroupées en une entrée (`unown_blocks` pour les 28 blocs Zarbi) |
| `description` | non | clé de traduction affichée dans le menu |
| `protocol` | non (`standard`) | `mewtwo` : expérience de clonage, avec les conditions de `rks/mewtwo.json` |
| `ingredients` | oui | liste de `{ "ingredient": {...}, "count": n }` ; types d'ingrédients : `generations_core:item`, `item_tag`, `damage`, `data_component`, `pokemon_item`, `time_capsule` |
| `result` | oui | `generations_core:item` (`stack`), `generations_core:pokemon` (`species`, `aspects`, `level`, `spawnInWorld`), `generations_core:fossil` (`fossil` = identifiant d'un fossile de Cobblemon, `species` = espèce affichée) |
| `processingTime` | non (240) | durée en ticks (20 = 1 s) |
| `consumesTimeCapsules` | non (vrai) | faux : la Time Capsule est rendue |

Les ingrédients peuvent être dans n'importe quelle case de la machine, dans n'importe quel ordre.
Les anciens formats `shaped_rks` / `shapeless_rks` sont encore lus, mais ne sont plus affichés dans JEI.

Recettes livrées (outil `tools/rks_recipes.py`) :

- **Expériences génétiques** : Mewtwo (6 Fossiles Mew, 1 Fibre d'ADN Mew, 1 Orbe, 1 Pointeau ADN), Type: Null, Magearna, Genesect.
- **Restauration de fossiles** : une recette par fossile de Cobblemon (15), avec ses fossiles + 32 poudres d'os, 60 s.
  Le Pokémon est celui de la cuve de restauration de Cobblemon (lu dans Cobblemon au moment de la restauration) ;
  il va dans l'équipe, ou dans le PC si elle est pleine.
- **Objets scientifiques et légendaires** : Cendre Sacrée, Œuf Miracle, Animacœur, 28 blocs Zarbi.

Retirées de la RKS (les objets et les espèces restent dans le jeu) : 47 Méga-Gemmes, 44 Cristaux Z, 21 Paradoxe,
Koraidon et Miraidon.

## 2. `config/generations/core/rks/rks_machine.json`

| Clé | Défaut | Rôle |
|---|---|---|
| `categories` | toutes `true` | une famille à `false` ne fabrique plus rien |
| `assemblySeconds` | 3 | animation d'assemblage des 5 modules |
| `analysisSeconds` | 4 | « Analyser les échantillons » (Mewtwo) |
| `repairSeconds` | 4 | animation du kit de réparation |
| `protectModulesDuringExperiment` | `true` | modules incassables pendant la croissance et l'éveil |
| `assistedPlacementRange` | 4 | un module posé à cette distance d'une console incomplète va à sa place |
| `hologramSeconds` | 30 | durée de l'hologramme des modules manquants |

Les durées s'appliquent au démarrage suivant du serveur (le fichier est relu à chaque démarrage).

## 3. `config/generations/core/rks/mewtwo.json`

| Clé | Défaut | Rôle |
|---|---|---|
| `energyRequired` | 100 | énergie psychique à réunir (la réserve ne la dépasse jamais) |
| `points` | 1 / 2 / 3 / 2 | `firstStage`, `middleStage`, `finalStage`, `noEvolution` |
| `speciesPoints` | vide | valeur imposée par espèce, ex. `"cobblemon:wobbuffet": 2` |
| `excludedLabels` | `legendary`, `mythical`, `ultra_beast`, `paradox` | jamais acceptés |
| `excludedSpecies` / `allowedSpecies` | vides | exclusions ; si `allowedSpecies` n'est pas vide, seules ces espèces |
| `allowPc` | `true` | les Pokémon du PC peuvent être sacrifiés |
| `research` | M-01, M-02, M-03 | dossiers consommés à chaque Mewtwo (les 3 premiers : un par case de dossier) |
| `growthSeconds` | 300 | croissance dans la cuve |
| `growthClock` | `loaded` | `loaded` : seulement chunk chargé ; `world` : temps du monde |
| `spawn.level` / `spawn.properties` | 70 / `""` | le Mewtwo sauvage qui apparaît |
| `cinematic` | `true` | scène de l'éveil (sinon apparition directe) |

Les composants génétiques sont dans la recette `mewtwo.json`, pas ici. Le barème suit la forme du Pokémon : sans
pré-évolution mais avec des évolutions = `firstStage` ; avec les deux = `middleStage` ; avec une pré-évolution seulement =
`finalStage` ; ni l'une ni l'autre = `noEvolution`. Une valeur de `speciesPoints` passe avant.

## 4. `config/generations/core/cinematics/cinematics.json` (clés ajoutées)

| Clé | Défaut | Rôle |
|---|---|---|
| `sceneSeconds` | `{"mewtwo": 24}` | durée d'une scène qui a la sienne (18 à 30 s pour Mewtwo) ; les oiseaux gardent `durationSeconds` |
| `sceneRadius` | `{"mewtwo": 50}` | rayon des spectateurs d'une scène ; les oiseaux gardent `spectatorRadius` |
| `ambientRadius` | 50 | joueurs proches mais occupés (combat…) : ambiance seule de l'éveil, sans prise de caméra |

`cinematics_client.json` (`quality` : `LOW`, `MEDIUM`, `HIGH`) règle aussi les effets de la machine : bulles, étincelles,
fumée, détails du modèle. Il est lu une fois par partie pour la machine.

## 5. La machine multibloc

Cinq blocs, posés sur 3 × 3 au sol et 4 de haut, la console devant le joueur :

```
              arrière
     gauche         milieu           droite
     [Alimentation] [Réacteur]       [Analyseur]
     [Alimentation] [Chambre]        [Analyseur]
     [═══════════════ Console ═══════════════]
              avant (joueur)
```

| Bloc | Cases (l × h × p) | Recette d'établi (`data/generations_core/recipe/rks/`) |
|---|---|---|
| `rks_control_console` | 3 × 1 × 1 | 3 verre teinté noir, 2 blocs de fer, 2 répéteurs, 1 comparateur, 1 diamant |
| `rks_containment_chamber` | 1 × 4 × 1 | 4 verre, 2 blocs de fer, 1 larme de Ghast, 1 lingot de netherite, 1 seau d'eau |
| `rks_reactor` | 1 × 4 × 1 | 1 étoile du Nether, 4 blocs de redstone, 2 blocs de fer, 2 lingots de netherite |
| `rks_power_module` | 1 × 2 × 2 | 4 blocs de cuivre, 2 blocs de redstone, 2 lingots d'or, 1 comparateur |
| `rks_genetic_analyzer` | 1 × 2 × 2 | 1 longue-vue, 2 verre, 2 observateurs, 3 lingots de fer, 1 diamant |
| `rks_repair_kit` (objet) | — | 4 verre, 2 lingots de fer, 2 redstone, 1 lingot d'or |

- Poser la console d'abord. Un module posé à `assistedPlacementRange` blocs ou moins d'une console incomplète va à sa
  place, tourné dans le sens de la console, si toutes ses cases sont libres ; sinon il est posé normalement.
- Clic droit sur la console : le menu (et, machine incomplète, l'hologramme des modules manquants pendant
  `hologramSeconds`). Clic droit sur un autre module : le menu de sa console.
- La console vérifie sa structure à chaque pose ou retrait d'un module et au chargement ; complète, elle s'assemble en
  `assemblySeconds` puis dessine la machine entière. Un module retiré la remet « incomplète » (rien n'est perdu).
- La console garde l'énergie psychique dans son objet ; la chambre garde l'état « endommagée » dans le sien.
  Le contenu des cases tombe au sol quand on casse la console.
- Pendant la croissance, l'éveil et la réparation, les modules ne se cassent pas en survie (`protectModulesDuringExperiment`).
  Ils ne sont jamais poussés par un piston et résistent aux explosions (résistance de l'obsidienne).
- Les états : `INCOMPLETE` → `ASSEMBLING` → `IDLE` ⇄ `CRAFTING` / `ANALYSING` → `GROWING` → `AWAKENING` → `DAMAGED` →
  `REPAIRING` → `IDLE`. Un paquet n'est envoyé aux joueurs qu'aux changements d'état ; la progression est calculée par
  chaque client.
- Fabrications ordinaires : bouton « Lancer » ; ingrédients pris à la fin (cases verrouillées pendant la fabrication) ;
  un objet va dans la case de sortie, Type: Null, Magearna et Genesect apparaissent sauvages devant la machine, un fossile
  restauré va dans l'équipe (ou le PC) de celui qui a lancé.
- L'ancienne `rks_machine` reste (mondes existants, deux structures de l'Ultra-Space) : plus de recette ni de menu, un
  message au clic droit, son contenu tombe si on la casse.

## 6. Le protocole M2 (Mewtwo)

1. **Énergie psychique** : bouton « Sacrifier » ; le serveur envoie la liste des Pokémon Psy éligibles de l'équipe et du PC
   (18 par page) avec leurs points ; le joueur coche, voit le total et la perte au-delà de la réserve, confirme deux fois
   (avertissement si un chromatique est coché). Le serveur revérifie chaque Pokémon par son UUID (à lui, dans l'équipe ou
   le PC, pas d'œuf, pas exclu, joueur hors combat et hors échange, Pokémon non sorti, pas en pension) puis les retire et
   crédite en une fois. La réserve est plafonnée à `energyRequired`.
2. **Composants** dans les 9 cases (rien n'est pris avant le lancement).
3. **Analyser les échantillons** (énergie pleine + composants) : `analysisSeconds`, puis les 3 cases de dossiers s'ouvrent.
4. **Démarrer l'expérience M2** (dossiers M-01, M-02, M-03 dans leurs cases), confirmé : dernière vérification, puis
   composants, dossiers (un de chaque) et énergie pris d'un coup.
5. **Croissance** (`growthSeconds`) visible dans la cuve : remplissage (0–20 %), forme sombre dans un liquide trouble
   (20–45 %), corps qui grandit (45–70 %), Mewtwo presque adulte et activité cérébrale (70–95 %), instabilité (95–100 %).
   À 100 % sans joueur à 50 blocs (`sceneRadius`), la machine attend (« Éveil imminent »).
6. **Éveil** : la scène de 24 s pour les joueurs libres à portée (l'invocateur : celui qui a lancé s'il est là, sinon le
   plus proche), l'ambiance seule pour les joueurs occupés. Interrompue (invocateur parti…), Mewtwo apparaît quand même.
7. **Apparition** : un Mewtwo sauvage ordinaire (`spawn.level`, `spawn.properties`) devant la machine, sans combat, une
   seule fois (drapeau enregistré dans la console avant l'apparition, vérifié après un rechargement).
8. **Machine endommagée** : modèle brisé, menu bloqué (« ERREUR CRITIQUE ») ; le kit de réparation sur un module lance
   `repairSeconds` de réparation, puis la machine revient à `IDLE`.

Les dossiers ne s'obtiennent pas en survie (aucune recette, aucun butin, aucune quête) : `/give` ou la commande de test.

## 7. Commandes de test `/generations rks …`

Permission `generations_core.command.rks` (niveau 2). Sur la machine visée, sinon la console la plus proche (16 blocs).

| Commande | Effet |
|---|---|
| `give modules\|dossiers\|kit [joueur]` | les 5 modules, les 3 dossiers, un kit |
| `energy <0-100>` | l'énergie psychique |
| `prepare` | composants de Mewtwo et dossiers dans les cases **vides** seulement |
| `start` | l'expérience M2 tout de suite, rien n'est consommé |
| `progress <pourcentage>` / `speed <multiplicateur>` | la croissance |
| `cinematic` | la scène de l'éveil seule (test : ni Mewtwo, ni dégât) |
| `damage` / `repair` | état endommagé, réparation immédiate |
| `reset` | expérience annulée, rien rendu ni créé, cases intactes |
| `info` | l'état de la machine |

`/generations cinematic test mewtwo` joue aussi la scène, sans machine (une cuve est alors dessinée par la scène).

## 8. Outils et vérifications hors jeu

- `tools/rks_machine_model.py` : le maillage (`assets/generations_core/rks/rks_machine_mesh.json`, 8 038 triangles).
- `tools/RksArt.java` : l'atlas 512 × 512 de la machine, les 4 textures d'objets 128 × 128 et les aperçus
  (`tools/previews/rks_machine_previews.png`).
- `tools/RksBench.java` : assemblage dans les 4 orientations, machine d'états, barème sur toutes les espèces Psy de
  Cobblemon. `tools/CinematicBench.java` contrôle aussi la scène de Mewtwo et sa musique.

