# Les manifestations Éon (fragments Enigma) et les scènes de Latias et Latios

Plan : `EON-PLAN.md` (dossier du projet). Build 66. Les recettes (4 fragments → 1 éclat, 4 éclats → 1 Mystécristal),
la charge de la pierre (100 Pokémon Psy ou Dragon vaincus), le choix Latias/Latios, le niveau 65, la capture et les
limites de capture sont **inchangés**.

## 1. Obtenir un fragment

1. **Explorer** : seul compte le temps d'exploration réelle (§ 2). Un tirage après 8 min, puis toutes les 8 min.
2. **Tirage** : 33 % (× 0,6 dans un biome qui a donné l'un de vos 4 derniers fragments). Pas de garantie.
3. **Signal** (6 s) : vibration lumineuse, motes roses et bleues, spirale, son psychique, cri lointain, à 14–22 blocs,
   dans votre champ de vision, sur un sol marchable visible de vos yeux.
4. **Anomalie** : sphère rouge et bleue ; elle part quand vous la regardez ou vous approchez (10 s au plus).
5. **Poursuite** (60 à 90 s) : elle avance à 3,6 blocs/s (marche : 4,3), accélère 3 fois à 5,2 (course : 5,6),
   tourne, passe sous les arbres, saute les petits obstacles ; 2 illusions (des copies partent de côté : seule la vraie
   laisse des **empreintes**). À plus de 16 blocs elle ralentit, à plus de 22 elle vous attend ; à plus de 28 blocs pendant
   8 s, elle se dissipe. Indices dans la barre d'action, colonne de lumière quand vous êtes loin.
6. **Noyau** : elle s'arrête ; **clic gauche ou droit** sur le noyau (4,5 blocs, 30 s). Le clic ne casse rien.
7. **Stabilisation** (2,5 s) : l'énergie se condense, le fragment vient à vous : **1 Fragment Énigme**.

Échecs (rien n'est donné) : perdue de vue, noyau éteint, mort, déconnexion, téléportation, changement de dimension,
150 s écoulées. La manifestation n'est visible que par le joueur concerné.

## 2. Ce qui compte comme exploration

Un échantillon toutes les 30 s par joueur. Il compte si : joueur actif (règle AFK de Mew), dimension et biome permis,
**8 blocs** parcourus au moins, pas de téléportation (déplacement bien plus grand que la marche réelle), hors région en
délai, pas de combat, scène, voyage Ultra-Warp, défi de Mew, passage de Ho-Oh, épreuve de Johto, échange, sommeil.
Un tirage dû attend encore : **2 min** d'affilée (une téléportation, une mort, un changement de dimension remettent à zéro),
**400 blocs** parcourus dans l'intervalle et **5 cases de 64 blocs** non visitées pendant l'intervalle précédent (tourner en
rond ou faire des allers-retours ne suffit pas).

**Délais** : la région (carré de 384 blocs) qui a donné un fragment attend 6 h réelles, et aucun tirage à moins de 500 blocs
de vos 4 dernières récompenses de ces 6 h. Fenêtres glissantes : les 16 fragments restent toujours accessibles, même
après avoir vu tous les biomes, sur une carte préconstruite (rien ne dépend de la génération de chunks).

## 3. Configuration (`config/generations/core/eon/eon.json`)

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | vrai | manifestations naturelles |
| `legacyBiomeFragments` | faux | l'ancien fragment par nouveau biome de Generations |
| `chancePercent` | 33 | chance d'un tirage |
| `firstCheckMinutes`, `checkIntervalMinutes` | 8, 8 | minutes d'exploration avant le 1er tirage, entre deux |
| `minPresenceMinutes` | 2 | exploration d'affilée avant un tirage |
| `minSampleBlocks`, `minIntervalBlocks`, `minIntervalCells` | 8, 400, 5 | déplacement réel, anti-cercles |
| `dimensions` | `minecraft:overworld` | dimensions permises |
| `biomes`, `excludedBiomes` | vide (tous), océans et rivières | ids ou tags (`#minecraft:is_forest`), ceux des autres mods compris |
| `repeatBiomeFactor`, `biomeMemory` | 0,6, 4 | diversité des biomes |
| `regionSize`, `regionCooldownHours` | 384, 6 | délai d'une région récompensée |
| `minRewardDistance`, `rewardMemory` | 500, 4 | distance aux dernières récompenses |
| `maxConcurrent` | 16 | manifestations en même temps (plein : le tirage attend) |
| `manifestation` | § 1 | `signalSeconds` 6, `gazeSeconds` 10, `pursuitMinSeconds` 60, `pursuitMaxSeconds` 90, `speed` 3,6, `dashSpeed` 5,2, `dashes` 3, `illusions` 2, `followRadius` 16, `lostRadius` 28, `lostSeconds` 8, `coreSeconds` 30, `interactRadius` 4,5, `maxSeconds` 150, `startDistanceMin` 14, `startDistanceMax` 22 |
| `afk` | comme Mew | `afkSeconds`, `minMoveBlocks`, `stillMinutes`, dispenses |
| `log` | vrai | tirages et manifestations dans `latest.log` |

Avec ces valeurs (simulation `tools/eon_odds_sim.py`, recalculée par `/generations eon odds`) : joueur typique
**11,2 h** d'exploration (80 % entre 8,6 et 13,9 h), habile 9,6 h, peu habile 14,7 h ; environ 1,7 manifestation par heure.

Côté client (`eon/eon_client.json`) : `particleIntensity` (0 à 2), `volume`, `hints`, `trailSeconds` (1 à 6),
`footprintSeconds` (2 à 12), `glowThroughLeaves`.

## 4. Sauvegarde et sécurité

`SavedData` `generations_eon` : compteurs par joueur, récompenses récentes, fragments en attente, invocations en attente.
Le clic sur le noyau est revérifié par le serveur (session, phase, distance, dimension ; un clic par 5 ticks) ; le
fragment est **inscrit et sauvegardé avant** l'animation, donné une seule fois (numéro de session) ; déconnexion ou
plantage → donné à la connexion suivante. Aucune entité, aucun bloc, aucun chunk chargé : tout est dessiné par le client.

## 5. La Pierre Enigma et les scènes

Dialogue `eon_spawn.json` (déroulé d'origine, textes FR/EN) : « Aucun disponible » teste maintenant Latias et Latios ; la
dernière page appelle `q.generations_eon_summon(q.player, 'latias level=65')`, qui revérifie la pierre chargée en main,
la retire (1, comme avant), puis joue **« Le Mirage Écarlate »** (Latias, 23 s) ou **« Le Gardien Azur »** (Latios, 23 s) ;
un seul Pokémon sauvage apparaît à la fin, à un point sûr 3 à 6 blocs devant le joueur, tourné vers lui. Joueurs à
48 blocs : la scène dans leur monde sans caméra prise. Sans scène possible (combat, autre scène, `eonCinematic` faux) :
apparition d'origine immédiate. Coupure (déconnexion, mort) : le Pokémon attend le joueur et apparaît devant lui 3 s
après son retour. `cinematics.json` : `eonCinematic`, `eonSpectatorRadius`, `sceneSeconds.latias|latios` (20 à 25).

## 6. Commandes (`/generations eon …`, permission `generations_core.command.eon`, niveau 2)

| Commande | Effet |
|---|---|
| `trigger [joueur]` | manifestation réelle maintenant (fragment si réussie) |
| `test [joueur]`, `pursuit [joueur]` | manifestation de test (aucune récompense ni compteur) ; poursuite sans le signal |
| `succeed [joueur]`, `fail [joueur]` | passer au noyau ; échouer |
| `give [n] [joueur]`, `stone [joueur]` | fragments ; Mystécristal chargé |
| `info [joueur]`, `why [joueur]` | progression, délais, chance ici ; ce qui bloque ici |
| `cooldown clear [joueur]`, `draw [joueur]`, `reset [joueur]` | effacer les délais ; tirage réel ; compteurs à zéro |
| `odds`, `status`, `summons`, `reload` | réglages et durées ; sessions ; invocations en attente ; relire |
| `/generations cinematic latias` / `latios` | la scène seule : rien consommé, aucun Pokémon (aussi `test latias`) |
| `/generations cinematic latias real` / `latios real` | invocation complète sans pierre : un seul Pokémon niveau 65 |
