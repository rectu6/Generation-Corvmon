# Refonte des raids Dynamax — audit et architecture

Document de travail des étapes 1 et 2 du cahier des charges « Refonte du système de
raids Dynamax ». Versions : Minecraft 1.21.1, Fabric, Cobblemon 1.8.4,
Generations-Core 1.2.8 (Corvemon).

## Étape 1 — Audit de l'existant

### Ce qui existe et fonctionne (testé en jeu)

| Domaine | Implémentation | Fichiers |
|---|---|---|
| Bloc antre, faisceau rouge/violet/doré | Propriété `beam`, rendu de balise | `world/level/block/raid/RaidDenBlock`, `RaidDenBlockEntity`, `client/render/block/entity/RaidDenBeamRenderer` |
| Génération des antres | `simple_block` + `rarity_filter` 48, terre ferme seulement | `data/.../worldgen/configured_feature`, `placed_feature/raid_den.json`, `GenerationsFabricBiomemodifiers`, `neoforge/biome_modifier` |
| Activation par objets Vœu | Puce 1–2★, Morceau 3–4★, Étoile 5★ (1 % 6★) | `RaidDenBlock.useItemOn`, `RaidManager.activate`, recettes `wishing_*`, `raid_den` |
| Choix du Pokémon | Total des statistiques de base + légendaires à 6★ | `RaidManager.pickSpecies` |
| Salon | Silhouette, étoiles, types, 4 participants, choix équipe/PC | `RaidLobbyScreen`, `RaidPickerScreen`, `S2CRaidLobbyPacket` |
| Combat | Combat simple Cobblemon, copie soignée, boss Dynamax géant | `RaidManager.start` |
| Règles dans Showdown | Alliés PNJ, cible du boss parmi 4, attaques de zone, K.O. → 1 tour d'absence puis retour, 4 K.O. / 10 tours, PV ×3 à ×8, immunité peur et K.O. en un coup | `resources/generations_core/showdown/raid.js`, `RaidShowdown`, `RaidInstruction` |
| PNJ | 3 dresseurs (modèle « standard » de Cobblemon) et leurs Pokémon | `RaidAllies`, `data/.../npcs/raid_ally.json` |
| Fin | Menu de capture (1 essai), animation, récompenses fixes, anti-perte | `RaidResultScreen`, `RaidManager.onResultAction` |
| Isolation partielle | Boss non capturable en combat, pas de butin, PNJ non combattables, entités non sauvegardées | `RaidManager.onBallHit/onBattleStarting`, `LOOT_DROPPED`, `mixin/EntityMixin` |
| Interface HD | Cadres, barre de boss, alliés, météo, journal | `client/screen/battle/*`, `client/screen/hd/*` |

### API réellement disponibles (vérifiées dans Cobblemon 1.8.4)

- `ShowdownInterpreter.registerUpdateInstructionParser` : nos lignes `|gcraid|`.
- Contexte GraalJS de Cobblemon (`GraalShowdownService.context`, via réflexion) :
  `battleMap` et les objets `Battle` de Showdown accessibles.
- `AIBattleActor.onChoiceRequested()` est `open` : un acteur boss peut retenir sa
  décision → synchronisation des tours entre plusieurs combats.
- `PokemonBattleActor(fleeDistance = -1)` : pas de fuite automatique à distance.
- `BattleRegistry.startBattle`, `BattlePokemon.safeCopyOf`, `PlayerBattleActor`.
- `CobblemonEvents` : `BATTLE_VICTORY/FLED/FAINTED/STARTED_PRE`,
  `THROWN_POKEBALL_HIT`, `LOOT_DROPPED` (annulable).
- Tags de biomes Cobblemon `cobblemon:is_ocean`, `is_mountain`, `is_forest`,
  `is_desert`, `is_snowy`, `is_swamp`… (couvrent aussi les biomes moddés via `c:`).
- `NPCClasses`, `NPCEntity`, `PokemonEntity`, `PlayPosableAnimationPacket`.
- Pas d'API Cobblemon de « raid » : Showdown ne connaît pas le 4 contre 1.

### Décision par fonctionnalité

| Fonctionnalité | Décision |
|---|---|
| Bloc, faisceau, rendu | Conservé |
| Génération `simple_block` | **Remplacée** par une feature `generations_core:raid_den` (terre + eau, espacement) |
| Recettes antre et Vœux | **Supprimées** |
| Activation par Vœux | **Supprimée** (objets conservés, sans usage, pas de crash) |
| Sélection par statistiques | **Remplacée** par les tables par biome |
| Salon, choix équipe/PC, copie soignée | Conservés, étendus au multijoueur |
| Règles Showdown (alliés, K.O., limites) | Conservées, étendues (boucliers, attaques multiples, ondes de choc, encouragements) |
| Défaite « l'antre garde son raid » | **Remplacée** : explosion, antre inactif |
| Capture à taux codés en dur | **Remplacée** par `capture.json` |
| Récompenses fixes | **Remplacées** par `rewards.json` |
| Interface HD | Conservée, complétée (bouclier, encouragement) |

## Étape 2 — Architecture

### Configuration

Dossier `config/generations/core/raids/`, fichiers créés avec les valeurs par
défaut au premier lancement, relus au démarrage du serveur. Une erreur dans un
fichier est écrite dans les logs et le fichier concerné garde ses valeurs par
défaut (pas de crash). `main.json` garde l'apparence (hauteurs) ; ses anciennes
valeurs de raid (`bossHealth`, `legendaryChance`, taux de capture) servent à
initialiser les nouveaux fichiers (migration).

| Fichier | Contenu |
|---|---|
| `dens.json` | Génération, rareté, distance minimale, eau, réapparition quotidienne, poids des étoiles, poids des légendaires, objet de l'antre, fuite |
| `tiers.json` | Par étoile : niveaux, PV ×, IV parfaits, shiny, boucliers, attaques max, ondes de choc, niveau et IA des PNJ |
| `pokemon.json` | Tables par biome (tags ou identifiants), espèce, aspects/forme, étoiles, poids, niveaux, Gigamax ; table de secours ; légendaires 6★ |
| `battle.json` | Bouclier (seuils, réduction, coups multiples, Max), onde de choc (effets), encouragements (effets et probabilités), délais du salon et des tours |
| `capture.json` | Taux par étoile, multiplicateurs des Poké Balls, Master Ball, éligibilité |
| `rewards.json` | Tirages par étoile, tables par étoile et par type, quantités, poids |

Code : `world/raid/config/RaidConfig` (lecture, validation, valeurs par défaut).

### Antres

- `world/raid/den/RaidDenFeature` : feature enregistrée (`Registries.FEATURE`),
  placement à espacement déterministe (comme les structures) : grille de cellules
  calculée depuis `minDistanceBetweenDens` et `denSpawnRarity`, une position
  possible par cellule → pas de doublon ni d'antres collés, sans état partagé entre
  les threads de génération. Terre ferme, et surface de l'eau si `allowWaterDens`.
- `world/raid/den/RaidDenRegistry` (`SavedData` de l'Overworld) : chaque antre
  connu (position, biome, raid en cours : boss sérialisé, étoiles, combat en
  cours, dernier jour traité). Les antres s'enregistrent au chargement de leur
  bloc : les antres existants sont repris.
- Réapparition : à `denRespawnTime` de chaque jour Minecraft, une seule tentative
  par antre inactif (`denRespawnChance`), même chunk déchargé ; le faisceau se met
  à jour au chargement du chunk.
- Le boss est **généré une fois** (espèce, niveau, IV, nature, talent, shiny) et
  sauvegardé : quitter/rejoindre ne change rien.

### Combat

- Un « raid » serveur (`RaidFight`) par antre : 4 places, joueurs puis PNJ.
- Chaque joueur réel a son propre combat Cobblemon (son Pokémon contre une copie du
  boss) ; Showdown ne gère pas le 4 contre 1. L'état partagé (PV du boss,
  bouclier, K.O., tour) est tenu par le serveur et recopié dans chaque combat :
  - un acteur boss (`RaidBossActor`) retient son choix jusqu'à ce que tous les
    joueurs aient choisi → les tours avancent ensemble ;
  - les combats sont résolus l'un après l'autre dans le même tick, l'état partagé
    étant recopié avant et relu après chacun ;
  - les autres participants apparaissent comme alliés dans chaque combat.
- `raid.js` reçoit les nouveaux réglages : bouclier, attaques supplémentaires, onde
  de choc, encouragements.

### Fin

- Défaite : séquence d'explosion (particules, symbole, éclair) sans dégâts ; boss
  supprimé, antre inactif jusqu'à la prochaine réapparition.
- Victoire : un menu par joueur éligible (1 tentative), récompenses tirées une fois
  et gardées côté serveur (`SavedData`) jusqu'à leur remise : pas de perte ni de
  doublon après crash ou reconnexion.

## Corrections après le premier test en jeu (solo)

- **Tour bloqué, barre du boss vide.** Le boss était créé avec `PokemonEntity(level, pokemon)`,
  sans `pokemon.state = SentOutState(entity)` (ce que fait `Pokemon.sendOut`). Sans cet état,
  `pokemon.entity` vaut null : `InitializeInstruction` ne met pas le boss sur le terrain,
  le client le reçoit sans Pokémon actif (barre vide) et Cobblemon ne demande jamais son
  choix au boss. L'attaque du joueur partait seule, Showdown attendait le boss pour
  toujours. Les copies du boss des autres joueurs pointent sur la même entité.
- **Choix du boss.** L'attaque prévue n'avait pas de cible : Cobblemon la refusait et
  l'IA choisissait à la place. `RaidBossActor` vise maintenant l'adversaire, garde la
  requête tenue, et chaque cas finit par une réponse (attaque prévue, IA, choix par
  défaut de Showdown). Filet de sécurité dans `RaidFight` : si Showdown attend le boss
  alors que Cobblemon ne lui demande rien pendant 3 s, `>p2 default` est envoyé et le log
  l'écrit (« Raid: … waited for the boss »). `GenerationsRaid.waiting(battle, side)`
  dit si Showdown attend encore un camp.
- **PV au départ.** Le boss est soigné avant le combat et `start` envoie `-sethp` après
  avoir multiplié ses PV : la barre part de 100 %.
- **Entités restées dans le monde (duplication).** Les Pokémon de Cobblemon redéfinissent
  `shouldBeSaved` sans l'appeler : le boss, les Pokémon des PNJ et la copie du Pokémon du
  joueur étaient sauvegardés et restaient après une déconnexion (la copie pouvait être
  combattue et capturée). `EntityMixin` refuse aussi `saveAsPassenger`, et toute entité
  de raid (tag `generations_raid`, clé `generations_raid_boss` ou
  `generations_raid_copy`) dont le raid n'est plus en cours est retirée (`RaidManager.isStray`).
- **HUD de raid dans un combat normal.** `S2CRaidHudPacket` porte l'identifiant du combat :
  le HUD de raid ne s'affiche que dans ce combat.
- **Rouge et nuages Dynamax.** Le client lit les aspects synchronisés de l'entité
  (`PokemonEntity.aspects`) : ceux du Pokémon client ne sont que ceux de son apparition,
  d'où le rouge visible seulement après rechargement. Les nuages prennent la hauteur et
  la largeur du modèle 3D dessiné (`RareCandyBone`), la hitbox sinon.
