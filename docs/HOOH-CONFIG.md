# Le Passage de l'Arc-en-ciel (Ho-Oh) : règles, configuration, commandes

Plan : `HOOH-PLAN.md` (dossier du projet). Build 61. Ho-Oh n'est **pas capturable** pendant ce passage : aucune
entité, aucun Pokémon créé. Tout ce qui se voit (Ho-Oh, l'arc-en-ciel, la lumière, la plume) est dessiné par le
client du joueur concerné à partir de quelques paquets.

## 1. Déroulement

1. **Tirage rare** (serveur) pendant l'exploration normale, voir § 2.
2. **Signal et passage** : scène `ho_oh` du moteur de cinématiques (13 s, caméra prise, joueur invulnérable) :
   cri venu du ciel, lumière dorée, braises rouges, orange et or, arc-en-ciel qui se forme, Ho-Oh qui sort de
   la lumière, pique, passe au-dessus du joueur au ralenti (cri, rafale, éclat), vire et repart. Si la scène ne
   peut pas se lancer (option `cinematic` à `false`, autre scène), Ho-Oh passe sans prendre la caméra et un
   arc-en-ciel apparaît 6 s dans le ciel.
3. **Poursuite** (45 à 90 s, contrôle rendu) : Ho-Oh vole devant, 18 à 26 blocs au-dessus des arbres, le long
   d'un chemin calculé au fur et à mesure dans les chunks déjà chargés (terrain marchable, pas d'eau profonde,
   pas de falaise de plus de 10 blocs). Sa vitesse suit le joueur : ralentit au-delà de 28 blocs, tourne en
   cercle et attend au-delà de 44. Indices : direction et distance dans la barre d'action, halo doré visible de
   loin, traînée arc-en-ciel de 3 s, colonne de lumière sous lui quand le joueur est loin, ailes et cris
   positionnés.
4. **Fin** : après la durée tirée, Ho-Oh tourne au-dessus de son dernier point ; s'en approcher à 16 blocs →
   il ralentit et crie, une **plume** tombe en tournoyant (7 s, traînée dorée, lumière pulsée) près du joueur,
   puis Ho-Oh monte et disparaît. S'approcher de la plume (1,9 bloc) la donne. Échec : plus de 64 blocs pendant
   12 s, mort, déconnexion, changement de dimension, 150 s écoulées → rien.

## 2. Rareté (`config/generations/core/hooh/ho_oh_passage.json`)

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | tirages naturels |
| `chancePercent` | 0,75 | chance de chaque tirage |
| `checkIntervalMinutes` | 10 | un tirage toutes les N minutes de **jeu actif** (AFK exclu) |
| `minActiveHours` | 2 | jeu actif minimum depuis le dernier passage du joueur |
| `cooldownHours` | 24 | heures **réelles** minimum entre deux passages d'un joueur |
| `maxConcurrent` | 3 | passages en même temps sur le serveur |
| `dimensions` | `minecraft:overworld` | dimensions permises |
| `excludedBiomes` | `#minecraft:is_ocean` | biomes ou tags interdits |
| `cinematic` | `true` | la scène de 13 s (sinon passage sans caméra) |
| `scale` | 1,6 | taille de Ho-Oh pendant la poursuite (la scène est écrite pour 1,6) |
| `pursuit` | voir § 1 | `minSeconds` 45, `maxSeconds` 90, `speed` 5, `followRadius` 28, `waitRadius` 44, `lostRadius` 64, `lostSeconds` 12, `finishRadius` 16, `timeoutSeconds` 150, `altitudeMin` 18, `altitudeMax` 26 |
| `afk` | comme Mew | règle AFK (`afkSeconds`, `minMoveBlocks`, `stillMinutes`, dispenses) |
| `log` | `true` | chaque tirage et passage dans `latest.log` |

Avec ces valeurs : moyenne **24,1 h** de jeu actif, médiane 17,3 h, 13,3 % avant 5 h, 11,4 % après 50 h (le premier tirage a lieu dès les 2 h atteintes)
(`/generations hooh odds` recalcule avec les valeurs du fichier). Pas de garantie.

Un tirage n'a lieu que si le passage peut se voir : ciel visible au-dessus du joueur, au sol (ou sur une
monture au sol), hors de l'eau et de la lave, pas en vol plané, pas en combat, en échange, en voyage
Ultra-Warp, dans une autre cinématique ni dans le défi de Mew. Sinon le tirage est perdu (pas de rattrapage).

## 3. La plume et les Cendres

- Objet `generations_core:ho_oh_sacred_feather` (« Plume sacrée de Ho-Oh »), épique, empilable par 16, résiste
  au feu et à la lave, sans recette ni butin. Texture 128 × 128 dessinée par `tools/HoOhFeatherArt.java`
  (aperçu : `tools/previews/ho_oh_sacred_feather_preview.png`).
- **Une plume par passage réussi**, inscrite dans la sauvegarde (`generations_ho_oh_passage`) **avant**
  l'annonce, avec le numéro du passage : jamais donnée deux fois ; une déconnexion ou un plantage avant le
  ramassage → donnée à la connexion suivante. Inventaire plein : au sol, réservée au joueur.
- La RKS Machine en fait une Cendre Sacrée (voir `RKS-CONFIG.md` § 3 bis).

## 4. Côté client (`config/generations/core/hooh/ho_oh_client.json`)

`particleIntensity` (0 à 2), `volume` (0 à 1), `hints` (direction dans la barre d'action), `trailSeconds`
(durée de la traînée arc-en-ciel, 1 à 6). La qualité et l'accessibilité de la scène sont celles de
`cinematics/cinematics_client.json`.

## 5. Commandes (`/generations hooh …`, permission `generations_core.command.hooh`, niveau 2)

| Commande | Effet |
|---|---|
| `trigger [joueur]` | passage complet en test (compteurs intacts, plume donnée si réussite) |
| `flight [joueur]` | la scène seule (Ho-Oh repart ensuite) |
| `pursuit [joueur]` | la poursuite sans la scène |
| `feather drop [joueur]` | seulement la chute d'une plume devant le joueur |
| `feather give [joueur]` | une plume dans l'inventaire (aussi `/generations rks give feather`) |
| `draw [joueur]` | un vrai tirage maintenant (passage réel s'il réussit) |
| `why [joueur]` | ce qui empêche un passage ici et maintenant |
| `info [joueur]` | jeu actif, prochain tirage, délai, compteurs, plume en attente, passage en cours |
| `active <heures> [joueur]` | fixe le jeu actif depuis le dernier passage |
| `odds` | réglages, moyenne, médiane, parts avant 5 h / 24 h et après 50 h |
| `status` | passages en cours sur le serveur |
| `stop [joueur]`, `reset [joueur]`, `reload` | arrêter, remettre les compteurs à zéro, relire le fichier |

## 6. Performances

Un échantillon par joueur toutes les 30 s (créneau tiré de son UUID : 200 joueurs ≈ 7 par seconde, quelques
comparaisons chacun). Une session de poursuite : une distance toutes les 5 ticks, ~10 lectures de carte des
hauteurs par nouveau point (un toutes les 5 à 7 s). Réseau : un paquet par changement (départ, points, vitesse,
plume, fin), jamais par tick. Aucun chunk chargé pour l'événement.
