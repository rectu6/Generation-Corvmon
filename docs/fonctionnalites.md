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
