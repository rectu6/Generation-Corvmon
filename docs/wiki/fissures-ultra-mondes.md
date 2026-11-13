# Fissures et retour à l'Overworld

Phase 6. Des fissures dimensionnelles s'ouvrent de temps en temps dans les Ultra-Mondes :
en les traversant sur Solgaleo ou Lunala, on passe dans un autre Ultra-Monde. La roue
d'interaction de Solgaleo et de Lunala propose aussi « Retour à l'Overworld ».

## Monter Solgaleo et Lunala

- Solgaleo et Lunala sont montables avec la monte de Cobblemon : vol (`air/jet` pour
  Solgaleo, `air/bird` pour Lunala) et marche (`land/horse`). Sortir le Pokémon,
  s'accroupir + clic droit, « Monter ».
- La hauteur du siège sur leurs modèles se règle dans `ultra_rifts_client.json` (`seats`,
  en blocs au-dessus des pieds du modèle).

## Les fissures

- **Où** : près des joueurs qui explorent (48 à 112 blocs), jamais près des points
  d'arrivée, jamais à moins de 192 blocs l'une de l'autre, jamais dans une structure ni à
  travers un bloc, toujours dans des chunks déjà chargés. Sous le ciel, elles flottent
  8 à 24 blocs au-dessus du sol ; dans l'Ultra-Abysse, au milieu d'une grande caverne
  (14 blocs de haut au moins).
- **Combien** : au plus 3 par monde, en général une seule près d'un joueur ; un essai
  toutes les 45 s par monde habité, avec 35 % de chance. Elles durent 5 à 10 minutes.
- **Où elles mènent** : un autre Ultra-Monde, ouvert, tiré à leur ouverture (fixe ensuite).
  Leur couleur est celle de ce monde (les couleurs des portails, `ultra_warp_ride.json`).
- **Partagées** : tous les joueurs du monde les voient ; le serveur décide de tout.
- **Rendu** : une déchirure verticale aux bords lumineux où court une pulsation d'énergie,
  un cœur sombre, des couches qui s'enfoncent (profondeur), des arcs, un halo, des
  particules aspirées et un bourdonnement de près. Ouverture, fermeture et effondrement
  animés. Rien n'est dessiné au-delà de 160 blocs ; les détails seulement à moins de 48.

## Traverser

- Il faut **monter et diriger** Solgaleo ou Lunala (le sien, dans son équipe).
- Le passage compte quand le joueur franchit le **milieu de l'ouverture** (70 % de sa
  taille) et que sa monture est dans l'ouverture : frôler un bord, passer à côté ou y
  mettre une aile ne déclenche rien. Aucune attraction.
- Une fissure ne sert **qu'une fois** : le premier qui passe la prend, elle s'effondre
  pour tout le monde.
- Le passage : un fondu vers la couleur du monde de destination (pas le tunnel), puis
  l'arrivée **au sol, à pied**, dans un endroit sûr (la recherche d'arrivée des
  Ultra-Mondes : d'abord près des mêmes coordonnées, puis les points d'arrivée, puis une
  plateforme de secours). Solgaleo ou Lunala retourne dans sa Poké Ball et reste dans
  l'équipe. 5 s de protection.
- **Jamais de récompense du voyage** : pas de rencontre, pas de bonus shiny, pas
  d'Ultra-Chimère, pas d'années-lumière ni de niveau de portail. Les spawns naturels
  continuent normalement.

## Retour à l'Overworld

- Dans un Ultra-Monde : sortir Solgaleo ou Lunala, s'accroupir + clic droit, choisir
  « Retour à l'Overworld » dans la roue.
- Le serveur vérifie : le Pokémon est le sien, dans son équipe, à moins de 8 blocs, pas
  de combat, pas déjà en voyage, 10 s entre deux demandes.
- On revient au **point de départ du vrai Ultra Warp Ride** (enregistré quand le voyage
  commence, jamais remplacé par une fissure ni par un passage entre Ultra-Mondes, gardé
  après une déconnexion ou un redémarrage). S'il n'est plus sûr : un point sûr à côté,
  sinon le point de réapparition, sinon le spawn de l'Overworld, sinon une plateforme au
  spawn. Jamais dans le vide.
- Si le voyage était parti du Nether, on y retourne ; `overworldOnly` force l'Overworld.

## Déconnexions, morts, redémarrages

- Une fissure ouverte est sauvegardée avec son temps restant ; une fissure prise ne
  revient jamais.
- Une déconnexion pendant un passage ou un retour : il est fini à la reconnexion,
  protégé. Une mort : le passage est annulé.
- Si le passage échoue (monde fermé, aucun endroit sûr), le joueur reste où il est, sur
  sa monture, et le reprend en main.

## Configuration

`config/generations/core/ultra_space/ultra_rifts.json` (serveur) :

| Partie | Options |
|---|---|
| `rifts` | `enabled`, `dimensions`, `attemptIntervalSeconds`, `spawnChance`, `triesPerAttempt`, `maxPerDimension`, `maxNearPlayer`, `nearPlayerRadius`, `lifetimeSeconds` (`min`, `max`), `minDistanceBetween`, `spawnDistance`, `arrivalPointClearance`, `heightAboveGround`, `minY`, `maxY`, `cavernMinHeight`, `width`, `height`, `sizeVariation`, `clearanceMargin`, `innerFraction`, `maxStepPerTick`, `destinationWeights`, `allowSameDimension`, `singleUse`, `mounts`, `transitionSeconds`, `arrival` (`coordinates` ou `points`) |
| `wheelReturn` | `enabled`, `species`, `maxDistance`, `refuseInBattle`, `transitionSeconds`, `cooldownSeconds`, `overworldOnly` |
| `test` | `logRifts` (chaque essai, refus, traversée dans le journal) |

La recherche d'arrivée, la protection et la plateforme de secours restent dans
`ultra_worlds.json`. `ultra_rifts_client.json` (client) : `viewDistance`,
`detailDistance`, `particleDistance`, `particlesPerSecond`, `distortion`, `sounds`,
`glow`, `showTriggerZone` (montre la zone à traverser), `seats`.

## Commandes (opérateurs)

| Commande | Effet |
|---|---|
| `/ultrawarp rift create <monde> [secondes]` | Ouvre une fissure devant soi, vers ce monde |
| `/ultrawarp rift list` / `info` | Les fissures ouvertes / la plus proche : id, position, destination, temps restant |
| `/ultrawarp rift expire <nearest\|all\|id>` | Referme |
| `/ultrawarp rift cross [nearest\|id]` | Traverse sans monture (test du passage) |
| `/ultrawarp rift spawn` | Essaie une ouverture naturelle près de soi, dit pourquoi elle échoue |
| `/ultrawarp rift diagnose` | Réglages, fissures et dernier essai par monde, état de sa monture |
| `/ultrawarp home` | Le point de retour enregistré, les passages en attente |
| `/ultrawarp home return` | Le retour de la roue, sans le Pokémon |

## Ultra Warp Ride avec un shader

Avec un pack de shaders, le voyage est maintenant dessiné après l'image finale du pack,
comme sans pack : le brouillard, la nuit, la lumière des blocs et le ciel du pack ne
touchent plus le tunnel. Option `visual.drawOverShaders` de `ultra_warp_ride_client.json`
(désactivable si besoin).
