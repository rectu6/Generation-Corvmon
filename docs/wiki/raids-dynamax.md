# Raids Dynamax

Inspirés des raids de Pokémon Épée et Bouclier et des mécaniques des raids de
Pixelmon, écrits pour Corvemon (aucun code repris). **Pas de téléportation** : le
Pokémon du raid surgit au-dessus de son antre, géant, et le combat se joue sur
place. Tous les réglages sont dans `config/generations/core/raids/` (voir
[Configuration des raids](configuration-raids.md)).

## Les antres

Les antres Dynamax **apparaissent seuls dans le monde** (Overworld), sur la terre
ferme et à la **surface de l'eau** (océans, rivières, lacs), environ un tous les 48
chunks, jamais à moins de 96 blocs l'un de l'autre. **Ils ne se fabriquent plus**
et un antre cassé ne redonne rien (réglable par les administrateurs).

L'antre est un modèle 3D : un cercle de rochers d'environ 3 blocs autour d'une fosse
sombre (le bloc de l'antre est la fosse, au centre).

- **Inactif** : la fosse est noire, pas de lumière.
- **Actif** : la fosse s'illumine et un faisceau monte vers le ciel, de la même
  couleur :

| Faisceau | Raid |
|---|---|
| Rouge | 1 à 5 étoiles |
| Doré | 6 étoiles, légendaire |

### L'apparition des boss

Plus besoin d'objet : **chaque matin** (lever du soleil), chaque antre inactif a
**75 % de chances** de recevoir un nouveau boss. Une seule tentative par antre et
par jour, même si personne n'est à côté ; un antre actif ne reçoit pas de second
boss. Un antre découvert pour la première fois tente aussitôt sa chance.

Le boss est **tiré une fois pour toutes** à son apparition (espèce, niveau, IV,
nature, talent, shiny) et sauvegardé avec le monde : quitter, revenir ou redémarrer
le serveur ne le change pas.

### Les objets Vœu

Utilisé sur un antre **inactif** (clic droit), un objet Vœu y fait apparaître un raid
tout de suite, et il est consommé (pas en créatif) :

| Objet | Raid |
|---|---|
| Puce Vœu | 1★ ou 2★ |
| Morceau Vœu | 3★ ou 4★ |
| Étoile Vœu | 5★, ou 6★ légendaire (1 % de chances) |

La difficulté est tirée selon les poids habituels, dans ces limites (réglables dans
`dens.json`). Sur un antre actif, l'objet n'est pas utilisé : le salon s'ouvre.
Leurs recettes et celle de l'antre restent supprimées.

## Les niveaux de raid

| Étoiles | Chance | Niveau | IV parfaits | Shiny | Bouclier | Attaques/tour |
|---|---|---|---|---|---|---|
| ★ | 42 % | 15–20 | 1 | 1/4096 | – | 1 |
| ★★ | 27 % | 25–30 | 2 | 1/4096 | – | 1 |
| ★★★ | 18 % | 35–40 | 3 | 1/2048 | 2 segments | 2 max |
| ★★★★ | 9 % | 45–50 | 4 | 1/1024 | 3 segments | 2 max |
| ★★★★★ | 3,5 % | 55–65 | 5 | 1/1024 | 4 segments | 3 max |
| ★★★★★★ | 0,5 % | 70–75 | 5 | 1/512 | 5 segments | 3 max |

Les raids 6★ sont réservés aux **légendaires et fabuleux**.

### Le Pokémon dépend du biome

Chaque biome a sa table : Pokémon Eau et marins dans les océans, Roche, Sol et
Acier en montagne, Plante et Insecte en forêt, Sol, Roche et Feu au désert, Glace
dans la neige, Poison et Eau dans les marais, Spectre dans les forêts sombres,
Fée dans les prairies fleuries, Feu près des volcans, etc. Certains peuvent être
**Gigamax** (Lokhlass, Krabboss, Ectoplasma, Ronflex…). Les légendaires des raids
6★ suivent aussi leur milieu (Kyogre en mer, Groudon au désert, Artikodin dans la
neige…). Une table de secours couvre les biomes sans entrée.

## Le salon

Clic droit sur un antre actif : le salon s'ouvre, en haute définition.

- À gauche : les étoiles, les types et la **silhouette noire** du Pokémon.
- À droite : les **4 places** : jusqu'à **4 joueurs**, les places libres sont prises
  par des **dresseurs PNJ** (leurs Pokémon viennent de la table du biome, au niveau
  du raid). Chaque joueur voit le Pokémon choisi par les autres et s'ils sont prêts.
- **Changer de Pokémon** : ton équipe ou ton PC (un seul Pokémon).
- **Prêt !** : le raid démarre quand tous les joueurs sont prêts, ou à la fin du
  compte à rebours (60 s).
- **Quitter** (ou Échap) : tu quittes le salon. S'éloigner de plus de 16 blocs
  aussi.

Chaque Pokémon combat en **copie soignée** : PV, PP et statuts du raid ne restent
pas, et le raid ne donne **pas d'expérience** (les récompenses en donnent).

## Le combat

Le boss surgit au-dessus de l'antre, **géant** (9 blocs), **rouge**, avec les
nuages Dynamax. Les dresseurs PNJ et leurs Pokémon se placent à côté du premier
joueur.

Chaque joueur a son combat (Cobblemon n'a pas de combat à 4 contre 1), mais tout est
**partagé** : les PV du boss, son bouclier, le tour, le compteur de K.O. Les tours
avancent **ensemble** : le tour se joue quand tous les joueurs ont choisi (60 s
maximum, sinon une attaque est choisie pour toi). Le journal de chacun raconte ce
que font les autres.

Déroulement d'un tour :

- Tu choisis ton attaque. **Pas de changement de Pokémon.**
- Le boss attaque **jusqu'à 1, 2 ou 3 fois** selon la difficulté. Son IA choisit
  attaques et cibles parmi les 4 participants selon les types, l'efficacité, les
  PV et les talents (plus fine aux hautes difficultés), sans s'acharner. Ses
  attaques de zone (Séisme, Surf…) touchent **tout le monde**.
- Les dresseurs PNJ attaquent le boss à la fin du tour.
- Le boss résiste à la peur et aux attaques K.O. en un coup.

### Le bouclier Dynamax (3★ et plus)

À **50 % de ses PV**, le boss lève un **bouclier** de 2 à 5 segments (jauge violette
au-dessus de sa barre) :

- les dégâts qu'il subit sont **réduits de 75 %** ;
- chaque attaque réussie **brise 1 segment**, une **capacité Max 2** (quelle que soit
  sa puissance) ; Gardomax et les attaques ratées n'en brisent pas ; une attaque à
  coups multiples compte une fois ;
- les **capacités de statut** contre lui échouent ;
- au dernier segment, le bouclier **éclate** et les dégâts redeviennent normaux.

Les capacités Max sont donc précieuses : Dynamaxe-toi au bon moment (Poignet
Dynamax, voir [Dynamax](dynamax.md)).

### Les ondes de choc (3★ et plus)

De temps en temps, le boss libère une **onde de choc** rouge et violette :
les **changements de statistiques** des participants sont annulés, leurs
**talents** sont neutralisés deux tours, et le boss **guérit de son statut**. Plus
fréquentes aux hautes difficultés, jamais deux tours de suite, en nombre limité.

### K.O. et encouragements

Un Pokémon K.O. **passe le tour suivant** puis **revient avec tous ses PV**. Pendant
ce tour, son dresseur peut **encourager** l'équipe (une fenêtre s'ouvre, 8 s) : au
hasard, une **statistique** des alliés monte, leurs **statuts** sont soignés, ils
**récupèrent des PV**, ou rien. Les PNJ encouragent d'eux-mêmes.

### Défaite

Le raid est perdu au bout de **4 K.O. cumulés** ou de **10 tours**. Le boss se fige,
brille, l'énergie Dynamax rouge et violette converge sur lui, un symbole Dynamax de
lumière apparaît, puis une **explosion** visuelle : il disparaît et le faisceau
s'éteint. L'explosion ne fait **aucun dégât** et ne touche à rien. Le raid est
**terminé** : l'antre redevient inactif jusqu'à un prochain matin. Si tous les
joueurs fuient ou se déconnectent, c'est pareil.

### Isolation

Le boss et les Pokémon des PNJ ne peuvent être ni frappés, ni combattus, ni capturés
par d'autres joueurs, et ne laissent aucun objet. Deux raids proches restent
indépendants.

## Après la victoire

Chaque joueur encore dans le raid a **son menu** (en haute définition), avec sa
propre **tentative de capture** et ses propres **récompenses** :

1. **Capturer ?** Une seule Poké Ball : 100 % jusqu'à 4★, 70 % à 5★, 35 % à 6★ (de
   base), modifiés par la Poké Ball ; la Master Ball réussit toujours. Le Pokémon
   garde son espèce, sa forme, ses IV, sa nature, son talent et son statut shiny ;
   il va dans l'équipe ou au PC. Si l'équipe et le PC sont pleins, la Poké Ball n'est
   pas lancée (ni perdue) : libère une place et réessaie. Tant que la capture n'est
   pas décidée (lancer ou passer), Échap ne ferme pas le menu.
2. **Récompenses** : tirées une fois à la victoire selon la difficulté et les types
   du boss (Bonbons Exp., Bonbons Dynamax, Super Bonbons, vitamines, PP Plus,
   Capsules Talent, Patchs Talent, Capsules d'Argent et d'Or, menthes, Maxi
   Champis, objets et ressources du type : Charbon pour Feu, Eau Mystique pour Eau,
   lingots pour Acier…). Elles sont gardées côté serveur jusqu'à ce que tu les
   prennes : fermer le menu, te déconnecter ou un crash ne les fait pas perdre, et
   on ne peut pas les prendre deux fois. Le menu se rouvre à la connexion, ou en
   cliquant sur n'importe quel antre, s'il reste quelque chose ; un nouveau raid ne
   peut pas commencer avant.

Sauvegarde : les résultats sont enregistrés avec le monde, en même temps que les
inventaires et les équipes. Le boss, les Pokémon des PNJ et la copie de ton Pokémon
ne sont jamais enregistrés, et ceux qu'un arrêt brutal laisserait dans le monde
disparaissent d'eux-mêmes : rien ne peut être capturé ou combattu en double.

## Pour les développeurs

Architecture détaillée : [`docs/raids-refonte.md`](../raids-refonte.md).

- Configuration : `world/raid/config/` (`RaidConfig`, tables `RaidPokemonTables`,
  `RaidRewards`).
- Modèle 3D de l'antre : `client/render/block/entity/RaidDenModel` (maillage
  `assets/generations_core/raid_den/raid_den_mesh.json`, texture
  `textures/block/raid_den/den_model.png`), converti depuis `tools/models/raid_den.bmd`
  par `python tools/raid_den_model.py` ; dessiné par `RaidDenRenderer` (antre et
  faisceau) et `RaidDenItemRenderer` (objet).
- Antres : `world/raid/den/` : `RaidDenFeature` (génération à espacement
  déterministe), `RaidDenRegistry` (`SavedData` : antres, boss sauvegardé, jour
  traité), `RaidDens` (réapparition quotidienne, génération du boss, faisceau).
- Combat : `world/raid/fight/` : `RaidLobby`, `RaidFight` (un combat par joueur,
  synchronisés), `RaidBossActor` (le boss attend le tour commun) ;
  `world/raid/RaidManager` (événements, salons, combats).
- Règles dans Showdown : `resources/generations_core/showdown/raid.js`, piloté par
  `RaidShowdown` (`start`, `plan`, `apply`, `state`, `cheer`, `leave`, `finish`).
- Fin : `RaidEffects` (explosion), `RaidResults` (`SavedData` des résultats).
- Écrans : `client/screen/raid/` (salon, choix, encouragement, fin), HUD dans
  `client/screen/battle/SwshBattleHud`.
- Tester les règles hors du jeu : le script se charge dans Node avec le Showdown
  extrait par Cobblemon ; un combat seul et deux combats synchronisés y ont été
  joués.
