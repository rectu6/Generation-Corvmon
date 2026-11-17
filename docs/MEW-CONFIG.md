# Mew : zones, chasse, mini-jeu et configuration

Documentation de l'obtention de Mew (plan : `MEW-PLAN.md` hors du dépôt), builds 59 (zones, chasse) et 60
(fausses apparitions, mini-jeu, cinématique finale, vrai Mew, commandes complètes). Mew n'apparaît plus naturellement (`data/cobblemon/spawn_pool_world/0151_mew.json`
a `"enabled": false`) : il ne vient que des îles.

Fichiers dans `config/generations/core/mew/`, créés au premier démarrage avec les valeurs par défaut, relus
par `/generations mew reload`. Une valeur hors limites est ramenée dans ses limites ; un fichier illisible
garde les valeurs par défaut (message dans le journal, jamais de plantage).

## 1. Zones (`mew_zones.json`)

```json
{
  "version": 1,
  "zones": [
    { "id": "ile_ouest", "enabled": false, "dimension": "minecraft:overworld",
      "shape": "box", "minX": -8200, "minZ": 3100, "maxX": -8020, "maxZ": 3280, "minY": -64, "maxY": 320,
      "biomes": ["#minecraft:is_forest", "#cobblemon:is_jungle", "minecraft:flower_forest"] },
    { "id": "ile_nord", "enabled": false, "dimension": "minecraft:overworld",
      "shape": "circle", "centerX": 1500, "centerZ": -9000, "radius": 100, "biomes": [] }
  ]
}
```

| Champ | Rôle |
|---|---|
| `id` | identifiant unique : minuscules, chiffres, `_`, `-`, `.` (64 caractères au plus) |
| `enabled` | `false` : zone gardée mais ignorée (les deux exemples fournis le sont) |
| `dimension` | `minecraft:overworld` par défaut ; `minecraft:` est ajouté s'il manque |
| `shape` | `box` (rectangle) ou `circle` (cercle) |
| `minX`, `minZ`, `maxX`, `maxZ` | rectangle, en coordonnées de blocs, les deux bords compris (inversés : remis dans l'ordre) |
| `centerX`, `centerZ`, `radius` | cercle (rayon de 0 à 100 000 blocs) |
| `minY`, `maxY` | facultatifs, pour les deux formes : hauteur permise (bords compris) |
| `biomes` | identifiants (`minecraft:jungle`) ou tags (`#minecraft:is_forest`) ; **liste vide = tous les biomes** |

- La chasse compte seulement si le joueur est **dans une zone et dans un biome autorisé** par cette zone.
  Les zones peuvent se chevaucher : il suffit qu'une seule convienne.
- Le temps passé sur **n'importe quelle île** alimente le **même compteur**.
- Une zone mal écrite (forme inconnue, rayon manquant, identifiant refusé ou en double) est **ignorée mais
  laissée telle quelle** dans le fichier, avec un avertissement dans le journal ; `zone list` l'affiche
  « ignorée (… à corriger) ».

### Commandes (opérateurs, permission `generations_core.command.mew`, niveau 2)

| Commande | Effet |
|---|---|
| `/generations mew zone test [rayon]` | zone de test en cercle autour de soi (48 blocs par défaut, 4 à 512), tous les biomes, **en mémoire** (perdue au redémarrage) ; la refaire la remplace |
| `/generations mew zone add <id> <rayon>` | vraie zone en cercle autour de soi (4 à 10 000 blocs), tous les biomes, **écrite dans le fichier** (compléter `biomes` à la main si besoin, puis `zone reload`) |
| `/generations mew zone remove <id>` | retire une zone de test ou du fichier |
| `/generations mew zone list` | toutes les zones : état, forme, dimension, biomes |
| `/generations mew zone here` | biome sous les pieds, zones qui contiennent la position, biome accepté ou non, et si la chasse compte ici maintenant |
| `/generations mew zone reload` | relit `mew_zones.json` (les zones de test restent) |

## 2. Chasse (`mew.json`, sections `hunt` et `afk`)

```json
"hunt": {
  "enabled": true,
  "checkSeconds": 30,
  "firstDrawHours": 12.0,
  "drawEveryHours": 2.0,
  "chanceTable": [
    { "hours": 12.0, "percent": 1.2 }, { "hours": 20.0, "percent": 2.4 }, { "hours": 30.0, "percent": 7.8 },
    { "hours": 40.0, "percent": 17.8 }, { "hours": 48.0, "percent": 30.0 }
  ],
  "guaranteeHours": 50.0
},
"afk": {
  "enabled": true, "afkSeconds": 300, "minMoveBlocks": 3.0, "stillMinutes": 15.0,
  "fishingExempt": true, "battleExempt": true, "ridingExempt": true
}
```

- **Compteur individuel** : chaque joueur connecté est vérifié toutes les `checkSeconds` (5 à 600), à un
  instant qui lui est propre (décalé par son UUID : environ 7 vérifications par seconde pour 200 joueurs,
  rien les autres ticks). Si les conditions sont remplies (zone, biome, non AFK, vivant, pas spectateur), le
  temps écoulé depuis sa vérification précédente est ajouté, **au plus le double de l'intervalle**. Le
  compteur avance en ticks de serveur : un jeu en pause (solo) ne compte pas. Hors ligne, rien n'avance.
- **Tirages** : le premier à `firstDrawHours` de présence active, puis toutes les `drawEveryHours`, tous
  avant `guaranteeHours` ; à `guaranteeHours`, la rencontre est **certaine**. La chance d'un tirage est
  lue dans `chanceTable` (heures → pourcentage), **en ligne droite entre deux points**, la première valeur
  avant le tableau, la dernière après. Ce sont des chances par tentative.
- **Barème par défaut** (barème B du plan, D1, points vérifiés par `tools/MewBench.java`) : tirages à 12, 14
  … 48 h puis garantie à 50 h ; **moyenne 35,5 h, médiane 36 h, 10 % des chasses vont jusqu'à la
  garantie**. Les points du plan (2,6 % à 20 h, 8,4 % à 30 h, 18,6 % à 40 h) venaient d'une courbe lisse ;
  reliés en lignes droites ils donnaient 35,0 h et 9,2 % : ils ont été ajustés à 2,4 / 7,8 / 17,8 %.
- **Déblocage** : un tirage réussi met la chasse en `UNLOCKED`, **sauvegardée tout de suite** ; plus aucun
  tirage ensuite. Le défi commence dès que le joueur est libre (§ 4).
- Modifier le tableau ou le calendrier puis `reload` : le prochain tirage de chaque chasse suit le nouveau
  calendrier (strictement après son compteur actuel).

### Règle AFK (`afk`)

La présence compte si **(a)** le joueur a fait quelque chose dans les `afkSeconds` dernières secondes, **et**
**(b)** il s'est déplacé d'au moins `minMoveBlocks` blocs dans les `stillMinutes` dernières minutes.

- (a) « faire quelque chose » = une action que Minecraft compte dans son minuteur d'inactivité
  (`ServerPlayer.getLastActionTime()` : clics, utilisation d'objet, interaction, chat, changement d'objet
  en main, s'accroupir ou courir, menus), **ou** un changement de position, de regard ou de tour de combat
  entre deux vérifications. Vérifié dans Minecraft 1.21.1 : **marcher et tourner la tête ne remettent pas
  à zéro** le minuteur vanilla, d'où ces échantillons pris à chaque vérification.
- (b) mesure le déplacement réel depuis un point d'ancrage (aller-retour sur moins de 3 blocs = immobile).
  **Dispenses** de (b), chacune désactivable : pêche (bouchon lancé, `fishingExempt`), combat Cobblemon
  (`battleExempt`), monture (`ridingExempt`). Un combat dont les tours avancent compte aussi pour (a) ; un
  combat qui attend son joueur, non.
- Exemples : regarder autour de soi ou rester sur place un moment ne coupe rien avant 15 minutes ; rester
  planté des heures avec un « anti-AFK » ne compte pas ; pêcher au même endroit compte.
- `"enabled": false` : toute présence dans une zone compte.
- Rien n'est sauvegardé sur la position : ces échantillons restent en mémoire, environ 40 octets par joueur
  connecté.

### Sauvegarde

`SavedData` `generations_mew_hunt` de l'Overworld (`<monde>/data/generations_mew_hunt.dat`), un enregistrement
d'environ 60 octets par joueur déjà entré dans une zone : état, heures actives, prochain tirage, tirages faits,
compteurs réservés aux étapes suivantes, chasses terminées. Écrit par la sauvegarde automatique du monde et à
l'arrêt, et **tout de suite** au déblocage et à `reset`.

### Commandes de chasse

| Commande | Effet |
|---|---|
| `/generations mew hunt get [joueur]` | heures actives, état, prochain tirage et sa chance |
| `/generations mew hunt set <heures> [joueur]` | **vraie progression** : compteur fixé, prochain tirage = le premier dû à partir de là (aucun tirage fait) |
| `/generations mew hunt add <heures> [joueur]` | idem en ajoutant (négatif possible, jamais sous 0) |
| `/generations mew draw [joueur]` | **tirage forcé sur la vraie progression**, avec la chance des heures actuelles (100 % au-delà de la garantie) ; affiche chance et nombre tiré ; le calendrier ne bouge pas ; refusé hors chasse |
| `/generations mew info [joueur]` | état, compteur, tirages, prochain tirage, zone actuelle, activité (actif, inactif, immobile) |
| `/generations mew reset [joueur]` | **vraie progression** : nouvelle chasse à 0 h (garde le nombre de chasses terminées) |
| `/generations mew reload` | relit `mew.json` et `mew_zones.json` |

## 3. Fausses apparitions (`manifestations`)

```json
"manifestations": {
  "enabled": true, "chancePercent": 10.0, "intervalMinutes": 15.0, "duringCooldown": true,
  "types": { "a": {"enabled": true, "weight": 3, "seconds": 3.0}, "b": {...}, "c": {...}, "d": {...} }
}
```

- Pour un joueur éligible (zone, biome, actif) en chasse ou en attente après un échec (`duringCooldown`,
  D12), hors combat, cinématique, voyage Ultra-Warp ou mini-jeu : un tirage de `chancePercent` toutes les
  `intervalMinutes` **actives** ; le premier créneau de chaque joueur est tiré au hasard. Type choisi par
  `weight` parmi les types activés ; `seconds` : sa durée.
- A : traversée d'une trouée à 10–16 blocs dans le regard (±45°, départ, milieu et arrivée visibles depuis les
  yeux, au-dessus des herbes, sur une ligne libre), regard, petit cri, bouffée rose. B : cri derrière le joueur,
  puis Mew est là où il se retourne (5 à 8 blocs, place libre et visible), le fixe et se dissout en particules
  psychiques qui restent quelques secondes. C : survol 5 à 8 blocs au-dessus des arbres et du relief du trajet
  (carte des hauteurs), à travers le regard, traînée, éclair de téléportation. D : apparaît devant le joueur
  (place libre et visible), le fixe, tourne autour de lui avec une traînée, rit, disparaît.
- **Visibilité (build 61, HOOH-PLAN.md § 4)** : durées 6 / 7 / 7 / 6 s (`mew.json` passe en version 2 et ne change
  une durée que si elle avait gardé l'ancienne valeur 3 / 6 / 5 / 5) ; Mew plus grand, en pleine lumière, aura rose
  et bleue, traînées plus larges qui s'effacent en 1,5 s, signature sonore (carillon aigu et rire) à chaque
  apparition, sons plus forts (24 à 32 blocs) ; **le spectacle attend le regard** : tant que Mew n'a pas été au
  centre de l'écran (≈ 20°) pendant 0,6 s, ses moments clés ralentissent (×0,3, 3 s de plus au plus). La
  fréquence, les poids, les tirages et le mini-jeu sont **inchangés**.
- **Un seul paquet** au joueur concerné (type, graine, points, durée) ; tout le reste est calculé par son
  client : aucune entité, rien d'attrapable ni de frappable, la caméra jamais prise. Les autres joueurs ne
  voient rien (D10).

## 4. Le jeu de Mew (`minigame`, `retry`)

```json
"minigame": { "rounds": [1, 2, 3], "maxMinutes": 6.0, "illusions": 5, "hideMinBlocks": 10.0, "hideMaxBlocks": 20.0,
  "courseMinPoints": 6, "courseMaxPoints": 8, "courseMinBlocks": 8.0, "courseMaxBlocks": 14.0, "catchBlocks": 5.0,
  "hintStrength": 1.0, "gameRadius": 24.0, "leaveSeconds": 20, "interactBlocks": 4.5 },
"retry": { "retryMinutes": 45, "disconnectFails": true }
```

- **Début** : après le tirage réussi, à la première vérification où le joueur est libre (vivant, au sol ou en
  vol normal, ni dans l'eau ni dans la lave, ni en selle, ni en combat, cinématique ou voyage) ; sinon
  « Mew vous attend… » et nouvel essai 30 s plus tard. La rencontre n'est jamais perdue. Scène d'ouverture
  de 6,5 s dessinée par le seul client du joueur (caméra libre, D4), titre « Mew veut jouer avec vous ! ».
- **Aire de jeu** : cercle de `gameRadius` blocs autour du point de départ, chunks chargés seulement ;
  positions sûres cherchées localement (32 essais au plus par position).
- **Interaction** (D5) : clic gauche ou droit sur l'illusion visée à `interactBlocks` (4,5) blocs ; le clic
  est envoyé au serveur à la place de son action normale, à ce moment seulement. Le serveur revérifie la
  session, le numéro d'étape (un clic périmé est ignoré), la cible, la distance aux yeux et la cadence.
- **Manche 1 — Retrouve-moi** : une cachette à `hideMinBlocks`–`hideMaxBlocks` blocs, la meilleure de 32
  candidates par la couverture (troncs, feuilles, roche, plantes) et hors de vue. Indices : balancement,
  motes discrètes, carillon toutes les 6 à 8 s ; à 45 s plus de motes, à 90 s une traînée de motes vers Mew,
  à 150 s il sort la tête et rit (toutes les 10 s). `hintStrength` 2 = indices deux fois plus tôt.
- **Manche 2 — Le vrai ou l'illusion** : `illusions` copies en arc à 5–8 blocs. La vraie : animation plus
  lente et décalée, respiration différente, halo faible visible de près, note claire quand on la vise
  (leurres : note sourde). Erreur : le leurre part en fumée en riant ; 1re et 2e erreur : halo renforcé ;
  3e : copies rebattues (nouvelle vraie). **Jamais d'échec** dans cette manche (D7).
- **Manche 3 — Attrape-moi** : parcours de `courseMinPoints`–`courseMaxPoints` points (8–14 blocs entre
  deux) dans l'aire de jeu ; Mew part quand le joueur est à `catchBlocks` (5) blocs (distance mesurée toutes
  les 5 ticks), vole ou se téléporte, nargue. Bloqué 40 s : il revient un peu vers le joueur. Au dernier
  point il reste et brille : un clic, c'est gagné.
- **Fin** : temps `maxMinutes` écoulé (compté après l'ouverture, chrono discret dans la barre d'action),
  abandon (`/generations mew giveup`), déconnexion ou arrêt (D6, `disconnectFails`), mort, changement de
  dimension, sortie de l'aire ou de l'île plus de `leaveSeconds` s (un avertissement d'abord).
- **Échec** : attente de `retryMinutes` (1 à 600) minutes **de présence active sur une île**, comptées par les
  mêmes vérifications que la chasse, sauvegardées (reprend après une déconnexion). Puis Mew revient (3 s,
  « Mew revient vous provoquer ! ») et les trois manches recommencent ; **aucun nouveau tirage**, tentatives
  illimitées. `disconnectFails: false` : une déconnexion ou un arrêt relance le défi sans attente. Un défi en
  cours lors d'un plantage compte comme un échec au démarrage suivant.

## 5. Cinématique finale, vrai Mew, capture (`spawn`, `lostMew`, `releaseTimeoutMinutes`)

```json
"spawn": { "level": 50, "properties": "", "cinematic": true, "cinematicRetrySeconds": 60 },
"lostMew": "reset", "releaseTimeoutMinutes": 60
```

- Défi gagné : état `WON`, sauvegardé, puis la scène `mew` du moteur de cinématiques (24 s, réglable de 20 à
  25 par `sceneSeconds.mew` de `cinematics/cinematics.json` ; joueurs libres à 32 blocs, `sceneRadius.mew`) :
  Mew penche la tête, s'envole en traînées roses et bleues autour du joueur, tourbillon psychique, six
  illusions qui convergent et fusionnent, sphère d'énergie, cri à 18 s avec une onde, descente douce.
  Musique et ambiance synthétisées. Options d'accessibilité et qualité : `cinematics_client.json`.
- Scène refusée (autre scène proche, joueur occupé) : nouvel essai toutes les 5 s pendant
  `cinematicRetrySeconds`, puis Mew apparaît sans elle (D9). `cinematic: false` : sans scène.
- **Le vrai Mew**, une seule fois (`WON` → `RELEASED`, sauvegardé **avant** l'apparition) :
  `cobblemon:mew level=<level> <properties>`, chromatique au taux normal de Cobblemon, marqué
  `generations_mew_hunt` = UUID du chasseur (données persistantes du Pokémon), Pokémon sauvage normal (ni
  combat ni capture automatique). Chasseur parti pendant la scène ou serveur arrêté : pas d'apparition,
  `WON` est gardé et la scène se rejoue à une vérification suivante.
- **Capturé par son chasseur** : nouvelle chasse à 0 h, nouveau calendrier, compteur de chasses terminées +1,
  marque retirée.
- **Mew perdu** (capturé par un autre joueur, K.O. en combat, ou pas capturé `releaseTimeoutMinutes` après
  sa sortie) : `lostMew` `"reset"` (recommandé, D8 : on recommence la chasse, pas d'apparition gratuite) ou
  `"retry"` (attente `retryMinutes` puis nouveau défi : une apparition de plus, assumée).

## 6. Côté client (`mew_client.json`)

`particleIntensity` (0 à 2, 0 = aucune), `volume` (0 à 1), `manifestationVisuals` (`false` : les fausses
apparitions s'entendent sans se voir), `hud` (chrono du défi), et depuis le build 61 : `manifestationScale` (taille
de Mew pendant une fausse apparition, 1 à 2, 1,35), `aura` (halo rose et bleu), `glowThroughLeaves` (faible lueur
visible à travers le feuillage), `waitForGaze` (le spectacle attend le regard). Rendu léger après le monde (avec ou sans
shader), sans post-traitement ; les modèles sont dessinés dans la passe du monde.

## 7. Toutes les commandes (`/generations mew …`)

| Commande | Effet |
|---|---|
| `zone test\|add\|remove\|list\|here\|reload` | § 1 |
| `hunt get\|set\|add`, `draw`, `info`, `reset`, `reload` | § 2 (`info` montre aussi le défi en cours) |
| `manifest <a\|b\|c\|d> [joueur]` | **test** : une fausse apparition maintenant |
| `minigame [manche]` | **test** : le défi sur une copie en mémoire (à partir de la manche donnée) ; gagné, il joue la cinématique de test |
| `cinematic` / `cinematic real` | **test** : la scène seule (rien n'apparaît) / la vraie, depuis `WON` |
| `spawn` / `spawn real` | **test** : un Mew non marqué / le vrai, depuis `WON` |
| `unlock [joueur]` | **vraie progression** : rencontre débloquée (le défi commence dès que le joueur est libre, dans une zone) |
| `fail [joueur]` | **vraie progression** : le défi en cours compte comme échoué |
| `cooldown <minutes> [joueur]` | **vraie progression** : attente restante (en `COOLDOWN`) ; 0 = Mew revient tout de suite |
| `giveup` | joueur, sans permission : abandonner son défi |

Mode test (`testMode` de `mew.json`, vrai par défaut) : sans lui, `manifest`, `minigame`, `cinematic` et
`spawn` de test sont refusés. Les commandes de test ne touchent jamais la vraie progression.
