# Fonctionnalités ajoutées

## Repousses

`RepelItem`, effet `RepelEffect` (`GenerationsMobEffects.REPEL`).

| Objet | Rayon | Durée |
|---|---|---|
| Repousse | 10 blocs | 3 min |
| Super Repousse | 15 blocs | 5 min |
| Repousse Max | 25 blocs | 8 min |

- L'effet s'affiche avec son minuteur (sans particules). Toutes les 10 ticks, les
  Pokémon sauvages du rayon (hors combat) reçoivent une destination à l'opposé
  du joueur, pour la navigation et pour le cerveau de Cobblemon (`WALK_TARGET`).
- Aucun Pokémon n'apparaît dans le rayon : `CobblemonEvents.ENTITY_SPAWN` est
  annulé, comme pour l'épouvantail. La pêche n'est pas concernée.
- Un Repousse plus faible que l'effet actif est refusé et n'est pas consommé.

## Multi Exp (`exp_all`)

- `ExpAllItem` : clic droit pour l'activer ou le désactiver (composant
  `generations_core:active`, l'objet brille quand il est actif).
- Actif dans l'inventaire : les Pokémon qui n'ont pas combattu gagnent 50 % de
  l'expérience (`GenerationsInstructionProcessor.grantExpAll`). Les Multi Exp de
  Cobblemon ne donnent plus rien à ces Pokémon (`PokemonBattleMixin`).
- Corrigé au passage : un Pokémon ayant combattu avec un Multi Exp Cobblemon
  perdait son expérience, et lors d'une capture les Pokémon ayant combattu ne
  gagnaient rien.

## Boîte Noigrume

`ApricornBoxItem` : sac de 27 cases qui n'accepte que le tag
`cobblemon:apricorns`, contenu stocké dans l'objet (`minecraft:container`).

Contre la duplication (`GenericChestContainer`, utilisé aussi par le Walkmon, la
Flûte et la selle de Sylveroy) :

- la case de l'objet ouvert est verrouillée, et les touches 1-9 et F ne peuvent
  plus la déplacer (elles contournaient le verrou) ;
- le contenu n'est écrit que si la boîte est toujours dans la même main ;
- le menu refuse les clics et se ferme dès que la boîte quitte la main ;
- le client reçoit le verrou et le filtre (`ContainerFilter`) pour afficher les
  mêmes cases que le serveur.

## Animation de Méga-évolution

`MegaEvolutionAnimation` : l'animation d'évolution de Cobblemon raccourcie à
4,5 s, aux couleurs arc-en-ciel
(`assets/generations_core/bedrock/generic/animations/mega_evolution.animation.json`,
`bedrock/particles/mega_*.particle.json`). En combat, le combat attend
l'animation et la forme Méga apparaît à l'explosion (3,4 s), pendant que la
lumière cache le Pokémon.

- Symbole Méga (`textures/particle/mega_symbol.png`, copie de l'icône de la roue
  d'interaction) au-dessus du Pokémon au début et à l'explosion, et petits
  symboles qui montent pendant la lumière. Les particules Snowstorm de Cobblemon
  lisent leurs textures dans l'atlas des particules (`textures/particle/`), d'où
  le chemin `generations_core:textures/particles/mega_symbol` dans le JSON.
- Hors combat, la Méga-évolution de la roue d'interaction
  (`GensInteractPokemonHandler`) joue la même animation : le Pokémon s'arrête et
  prend sa forme Méga à l'explosion ; les demandes pendant l'animation sont
  ignorées.

## Pokewand (`pokemon_wand`)

Outil réservé aux opérateurs (niveau 2), vérifié par le serveur à chaque action
(`world/pokewand/PokeWand`).

- Clic droit : ton équipe ; clic droit sur un joueur : la sienne
  (`PokeWandPartyScreen`, modèles 3D par `ModelWidget` de Cobblemon).
- Un emplacement ouvre `PokeWandEditScreen` : espèce avec recherche (« gir »
  propose ce qui commence par gir, puis ce qui le contient), forme, aspects,
  chromatique, sexe, niveau, nature, talent, 4 attaques, IV, EV, objet tenu,
  Poké Ball, surnom, bonheur, avec aperçu 3D. Un champ vide garde la valeur
  actuelle ; un nouveau Pokémon reçoit alors les valeurs générées par Cobblemon.
- Données échangées : `PokeWandPokemon` (`S2CPokeWandPartyPacket`,
  `C2SPokeWandActionPacket`). Espèce, forme, chromatique, sexe, niveau, nature et
  talent passent par `PokemonProperties`, comme `/pokeedit` ; le reste est
  appliqué directement. Chaque modification est écrite dans le log du serveur.
- « Ouvrir son PC » : le PC du joueur est envoyé et lié à l'opérateur
  (`PCLinkManager`), qui peut y déplacer les Pokémon comme dans le sien.

## Cannes à pêche

Canne, Super Canne et Méga Canne sont des cannes Cobblemon (`PokerodItem`,
définitions dans `data/generations_core/pokerods/`) : flotteur Poké Ball, Super
Ball ou Hyper Ball, appâts, Pokémon selon le biome, l'heure et la météo
(données de pêche de Cobblemon). `PokerodItemMixin` leur garde leur nom.

`GenerationsFishing` ajoute des paliers comme dans Pixelmon :

| Canne | Rareté maximale | Niveau |
|---|---|---|
| Canne | commun | moyenne de l'équipe × 0,5, entre 5 et 20 |
| Super Canne | rare | moyenne × 0,75, entre 15 et 45 |
| Méga Canne | ultra-rare | moyenne × 1, 30 minimum |

La rareté passe par `SPAWN_BUCKET_CHOSEN` (cause `FishingSpawnCause`), le niveau
par `BOBBER_SPAWN_POKEMON_POST` (± 3 niveaux). La Canne Rubis ne change pas.

## Repousses en multijoueur

Tout est calculé par le serveur : l'effet se sauvegarde avec le joueur, la fuite
ne concerne que les joueurs sous Repousse (toutes les 10 ticks, dans leur rayon),
et chaque apparition vérifie les joueurs du monde. Un joueur sous Repousse empêche
aussi les apparitions pour ses voisins proches ; le lait annule l'effet.
