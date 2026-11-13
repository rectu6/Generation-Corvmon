# Météos des Ultra-Mondes et Ultra-Combinaison

Phases 4.4 et 4.5. Chaque Ultra-Monde a sa propre météo, indépendante de celle de
l'Overworld. L'air n'y est respirable qu'avec l'Ultra-Combinaison complète, et ses pièces
peuvent recevoir des boucliers contre les météos dangereuses.

## Respirer dans les Ultra-Mondes

- Dans les sept Ultra-Mondes, il faut porter les **quatre pièces de l'Ultra-Combinaison**
  (Casque, Plastron, Jambières et Bottes Ultra, fabriqués avec des Lingots Z).
- Sans elles, l'air baisse : les bulles s'affichent, puis la suffocation fait perdre un cœur
  par seconde. Avec la combinaison complète, l'air revient peu à peu.
- Sous l'eau, la noyade normale continue. La Respiration aquatique et l'enchantement
  Respiration ne protègent pas (réglable).
- Après une réapparition ou une connexion dans un Ultra-Monde : 60 secondes de répit.
- L'Overworld, le Nether et l'End ne changent pas.

## Les météos

Chaque monde suit son propre cycle : une accalmie, puis une météo tirée au sort
(sans danger ou dangereuse), puis une accalmie… Une météo dangereuse est annoncée
une minute avant. Un message prévient au début et à la fin, et un indicateur en haut à
gauche montre la météo et votre situation.

| Monde | Sans danger | Dangereuse | Abri | Bouclier |
|---|---|---|---|---|
| Ultra-Jungle | Pluies tropicales | Canicule extrême | ombre (le feuillage compte), sous terre, dans l'eau | thermique |
| Ultra-Désert | Soleil intense | Tempête de sable | construction ou caverne | anti-sable |
| Ultra-Centrale | Surcharge énergétique | Orage électrique | toit plein (ni feuillage ni verre), hors de l'eau | isolant |
| Ultra-Cratère | Pluie de cendres | Éruption thermique | refuge fermé, sous terre, dans l'eau | volcanique |
| Ultra-Forêt | Brume dimensionnelle | Vents tranchants | bâtiment ou grotte | aérodynamique |
| Ultra-Abysse | Pluie cristalline | Brume toxique | espace clos, portes fermées (une caverne ouverte ne protège pas) | de filtration |
| Ultra-Gratte-Ciel | Obscurité dimensionnelle | Pluie acide | toit (pas de feuillage) | anticorrosion |

- Les dégâts commencent 30 secondes après le début de la météo ou après votre arrivée.
  Ceux de la canicule, de la tempête de sable, de l'éruption, des vents et de la pluie
  acide augmentent tant que vous restez exposé ; ceux de l'orage et de la brume tombent
  à intervalle régulier.
- Aucune météo ne détruit de bloc ni ne met le feu : les éclairs de l'orage sont visuels.
- Les effets marchent avec un shader : particules, voile coloré, pluie, éclairs, sons.
  Le brouillard peut être remplacé par celui du shader.

## Les boucliers

- Sept boucliers, un par monde. Chacun protège seulement de la météo dangereuse de son
  monde. Ils ne remplacent pas la combinaison pour respirer.
- **Fabrication** (établi) : 4 Lingots Z, 4 matériaux du monde et 1 pierre d'évolution.

  | Bouclier | Pierre | Matériau |
  |---|---|---|
  | Thermique | Pierre Plante | bûche d'Ultra-Jungle |
  | Anti-sable | Pierre Soleil | grès Ultra |
  | Isolant | Pierre Foudre | Pierre Charge |
  | Volcanique | Pierre Feu | Météorite |
  | Aérodynamique | Pierre Éclat | bûche Ultra-Sombre |
  | De filtration | Pierre Lune | Saphir |
  | Anticorrosion | Pierre Aube | Silicium |

- **Installation** (table de forge) : le bouclier dans la case du modèle, une pièce de
  l'Ultra-Combinaison dans la case de base, un Lingot Z dans la case d'ajout. La pièce garde
  ses enchantements, sa durabilité et son nom. Les sept boucliers peuvent aller sur la même pièce.
- **Protection** : il faut le bouclier sur les **quatre pièces** portées. L'indicateur
  affiche « Bouclier incomplet : 3/4 pièces » sinon.
- La description des pièces liste leurs boucliers ; JEI montre l'installation.

## Configuration (dossier `config/generations/core/ultra_space/`)

- `ultra_weather.json` (serveur) : par monde, l'accalmie (`calmMinutes`) ; par météo, le
  poids (`weight`), la durée (`durationMinutes`) et les dégâts (`damage` : `amount`, `max`,
  `rampPerMinute`, `intervalSeconds`). Le temps entre deux débuts n'est pas réglé : c'est
  la durée plus l'accalmie (valeurs par défaut : 50 + 10 = 60 minutes en moyenne, affiché
  dans le journal au chargement). Aussi : `transitionSeconds`, `warningSeconds`, `grace`,
  `shelters` (longueur des rayons, nombre de murs, profondeur, volume d'un espace clos).
- `ultra_suit.json` (serveur) : la respiration (`airLossPerTick`, `damage`,
  `respawnGraceSeconds`…) et la règle des boucliers (`requireAllPieces`, sinon
  `partialPerPiece`).
- `ultra_weather_client.json` (chaque joueur) : `particles`, `overlay`, `fog`,
  `viewSway` (0 retire l'ondulation de la vue due à la chaleur), `volume`, `indicator`, et
  les sons de chaque météo (identifiants de sons Minecraft ou d'un pack de ressources).
- `/ultrawarp reload` relit les fichiers du serveur ; les météos en cours gardent leurs horaires.

## Commandes de test (opérateurs)

- `/ultrawarp weather` : la météo des sept mondes (en cours, ou la suivante et quand).
- `/ultrawarp weather set <monde> <météo|clear> [minutes]`, `/ultrawarp weather next <monde>`.
- `/ultrawarp suit check` : pièces portées, air, boucliers complets.
- `/ultrawarp suit shield add|remove <bouclier>` sur la pièce tenue ; `/ultrawarp suit give`
  donne la combinaison avec les sept boucliers.
