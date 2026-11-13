# Ultra Warp Ride

Le voyage vers l'Ultra-Espace part de l'**autel céleste** : monté sur Solgaleo ou
Lunala, tu traverses un tunnel dimensionnel aussi loin que ton énergie le permet.
Traverse un portail : il t'emmène dans l'un des sept [Ultra-Mondes](ultra-mondes.md).
Leurs Pokémon arriveront dans une prochaine version.

## Le mini-jeu

- **Énergie** (en haut à gauche) : elle baisse d'elle-même. Les **orbes orange** en
  redonnent ; les **orbes bleues électriques**, les **débris** violets et les **arcs
  électriques** en prennent. À 0, tu perds le contrôle, ton Pokémon décroche et tu te
  perds dans l'Ultra-Brèche : c'est une vraie mort (réapparition normale ; ton
  inventaire tombe à l'autel sauf avec `keepInventory`). Tes Pokémon restent dans ton
  équipe.
- **Anomalies gravitationnelles** (tourbillons) : elles attirent ou repoussent ton
  Pokémon.
- **Années-lumière** (en haut à droite) : la distance parcourue. Plus tu vas loin, plus
  ça va vite et plus il y a de dangers. Ta distance et ton record s'affichent à la fin.
- **Événements** : tempête dimensionnelle (tunnel rouge, plus de dangers), courant de
  vitesse (vole dans le tube d'anneaux pour accélérer), anomalie dimensionnelle (le
  pilotage dérive), et très rarement… Necrozma.
- Il y a toujours un passage libre.
- **Portails** : des ouvertures colorées apparaissent. La **couleur** donne la
  destination (vert Ultra-Jungle, orange Ultra-Désert, jaune Ultra-Centrale, gris
  argenté Ultra-Cratère, violet Ultra-Forêt, bleu Ultra-Abysse, rouge sombre
  Ultra-Gratte-Ciel). L'**apparence** donne la rareté : simple, un anneau, deux
  anneaux, ou exceptionnel (anneaux arc-en-ciel et couronne de pétales). Plus tu vas
  loin, plus les portails rares sont fréquents. Un portail n'attire pas : pour y
  entrer, traverse son ouverture (le tourbillon au centre) avec ton Pokémon ; le
  frôler ou passer dans ses anneaux ne fait rien. Une fois dedans, ton voyage est
  réussi. Parfois plusieurs portails apparaissent ensemble : à toi de choisir.
- **Difficulté** : choisie par le serveur dans sa config (Facile, Normal, Difficile,
  Extrême ou d'autres), affichée dans le menu de l'autel. Elle change la vitesse, les
  orbes, les dangers, les événements, la perte d'énergie et les dégâts. Les records
  sont comptés par difficulté.

## Lancer un voyage

1. Fais un clic droit sur un autel céleste, avec ou sans objet en main.
2. Le menu montre la face de l'autel :
   - **Autel du Soleil** le jour : il faut **Solgaleo** dans ton équipe ;
   - **Autel de la Lune** la nuit : il faut **Lunala** dans ton équipe.
3. « Commencer le voyage » lance le voyage, « Annuler » ferme le menu.

Le menu indique si le Pokémon est dans ton équipe et combien de places sont libres.
Le serveur revérifie tout au moment du clic : il refuse avec un message si le
Pokémon n'est plus là, si tu es en combat, trop loin de l'autel, si l'autel a changé
de face entre-temps ou s'il n'y a plus de place.

## Pendant le voyage

- Tu vois ton Pokémon (avec son apparence, shiny compris) voler dans le tunnel, toi
  assis dessus, la caméra derrière vous. Le tunnel change à chaque voyage.
- **Piloter** : tes touches de déplacement (ZQSD, WASD… selon tes options) ou les
  flèches. Avant = monter, arrière = descendre, gauche et droite = de côté. Ton Pokémon
  accélère et ralentit en douceur, s'incline dans ses manœuvres et ne peut pas sortir
  du tunnel ; la caméra suit le tunnel et ne passe jamais à travers les parois.
- Sprint et Saut sont réservés au futur boost et à la future esquive (sans effet pour
  l'instant).
- **Échap** demande si tu veux quitter le voyage ; « Quitter » (ou Entrée) te ramène
  dans le monde, à l'autel. Tu peux recommencer aussitôt.
- Dans le monde, ton personnage reste à l'autel et ne prend pas de dégâts.
- Le voyage s'arrête aussi si tu te déconnectes ou si tu meurs.

## Règles

- Un seul voyage à la fois par joueur, un joueur par voyage.
- 6 voyages en même temps au plus sur le serveur (réglable), sans file d'attente.
- Un Solgaleo ou Lunala K.O. compte. Necrozma Crinière du Couchant ou Ailes de
  l'Aurore ne compte pas.

## Solo et multijoueur

Le même système fonctionne en solo et sur un serveur dédié : c'est toujours le
serveur (le serveur intégré en solo) qui gère les voyages et la limite.

## Tester seul (opérateurs)

Il faut les commandes autorisées dans le monde.

| Commande | Effet |
|---|---|
| `/ultrawarp test start sun` ou `moon` `[mode]` | Lance le voyage sans autel ni Solgaleo/Lunala, dans ce mode |
| `/ultrawarp test end` | Termine ton voyage, sans mourir |
| `/ultrawarp test portal <destination\|random> <1-4>` | Fait apparaître un portail de ce niveau |
| `/ultrawarp test convergence` | Fait apparaître plusieurs portails ensemble |
| `/ultrawarp test energy <valeur>` | Fixe ton énergie (0 : défaite) |
| `/ultrawarp test distance <AL>` | Saute à ces années-lumière |
| `/ultrawarp test event storm`, `current`, `anomaly`, `necrozma` | Lance l'événement |
| `/ultrawarp reload` | Relit la config du serveur (vitesse, tunnel…) |
| `/ultrawarp sessions` | Liste les voyages en cours et les places prises |
| `/ultrawarp test arrive <monde>` | T'emmène dans un Ultra-Monde sans voyage |
| `/ultrawarp test return` | Te ramène d'où tu étais parti |
| `/ultrawarp worlds` | Les Ultra-Mondes et leurs points d'arrivée |
| `/ultrawarp test simulate <n>` | Occupe `n` places avec des sessions sans joueur, pour tester la limite seul |
| `/ultrawarp test clear` | Retire ces sessions simulées |

On peut ajouter des noms de joueurs après `start` et `end` pour viser d'autres
joueurs.

## Configuration

`config/generations/core/ultra_space/ultra_warp_ride.json`, relu à chaque démarrage
du serveur (en solo : à chaque ouverture d'un monde) :

| Option | Défaut | Effet |
|---|---|---|
| `maxSessions` | `6` | Voyages en même temps sur le serveur (0 à 64). `0` désactive l'Ultra Warp Ride |
| `speed` | `24` | Vitesse du voyage, en blocs par seconde |
| `tunnel.*` | | Longueur des segments, force et fréquence des courbes, pente, rayon (13) et sa variation (0,2) |
| `test.fixedSeed` | `0` | Toujours le même tunnel quand ce n'est pas 0 |
| `test.maxDurationSeconds` | `0` | Le voyage s'arrête tout seul après ce temps (0 = jamais) |
| `steering.*` | | Vitesse, accélération et ralentissement du pilotage |
| `riders.*` | | Taille de Solgaleo et Lunala (forme de jeu), part maximale du tunnel, indulgence des collisions |
| `limits.*` | | Marges vers le plancher, les côtés, le plafond |
| `energy.*` | | Énergie maximale et de départ, perte par seconde, gains et dégâts |
| `objects.*` | | Taille des objets, longueur des tranches, début sans rien, anomalies, courants |
| `difficulty.tiers` | | Paliers par années-lumière : vitesse, chances des objets, anomalies, événements |
| `mode` | `normal` | Difficulté de tous les voyages (l'id d'un des modes) |
| `modes` | | Les modes de difficulté et leurs multiplicateurs |
| `portals.*` | | Portails : fréquence, taille, attraction, convergences, destinations, tables de rareté, apparence des niveaux |
| `encounters.*` | | Préparé pour les rencontres (pas encore utilisé) |
| `events.*` | | Chaque événement : activé, chance, durée, distance minimale, pause, force |
| `defeat.seconds` | `3` | Durée de la défaite avant la fin |
| `lightYearsPerBlock` | `0.05` | Années-lumière par bloc |
| `test.invincible` | `false` | Pas de perte d'énergie (essais) |

Toutes les chances de chaque objet et événement sont écrites dans le log du serveur
(au démarrage, au début de chaque voyage, à chaque palier et à chaque événement).

L'apparence (caméra, champ de vision, couleurs, effets) se règle pour chaque joueur
dans `config/generations/core/ultra_space/ultra_warp_ride_client.json`, relu à chaque
voyage.

Détails techniques : [notes Ultra Warp Ride](../ultra-warp-ride.md).
