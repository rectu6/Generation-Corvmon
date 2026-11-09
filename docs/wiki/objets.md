# Objets

## Repousses

Clic droit pour l'utiliser. L'effet « Repousse » s'affiche avec son minuteur.

| Objet | Rayon | Durée |
|---|---|---|
| Repousse | 10 blocs | 3 min |
| Super Repousse | 15 blocs | 5 min |
| Repousse Max | 25 blocs | 8 min |

- Les Pokémon sauvages du rayon **s'enfuient** (pas ceux en combat).
- **Aucun Pokémon n'apparaît** dans le rayon. La pêche n'est pas bloquée.
- Utiliser un Repousse plus faible que celui en cours est refusé, l'objet n'est
  pas consommé. Le même ou un plus fort renouvelle l'effet.
- Le lait annule l'effet, comme une potion.
- Multijoueur : un joueur sous Repousse empêche aussi les apparitions pour les
  joueurs juste à côté de lui.

## Multi Exp

Clic droit pour l'**activer** ou le **désactiver** (il brille quand il est actif).
Actif et dans l'inventaire :

- les Pokémon qui ont combattu gagnent leur expérience normale ;
- les autres Pokémon de l'équipe gagnent **50 %** de l'expérience ;
- les Multi Exp de Cobblemon (objet tenu) ne donnent rien de plus.

Marche aussi pour les captures.

## Boîte Noigrume

Clic droit pour l'ouvrir : un sac de **27 cases** qui n'accepte que les Noigrumes.
L'infobulle indique combien elle en contient. Elle est protégée contre la
duplication : on ne peut pas la déplacer pendant qu'elle est ouverte (ni à la
souris, ni avec les touches 1 à 9 ou F), et elle se ferme si elle quitte la main.

## Objets Vœu

| Objet | Rôle | Fabrication |
|---|---|---|
| Puce Vœu | Raid 1 ou 2 étoiles | Améthyste + Redstone + Poudre lumineuse → 2 |
| Morceau Vœu | Raid 3 ou 4 étoiles | 4 Puces + 1 Émeraude |
| Étoile Vœu | Raid 5 étoiles (1 % : légendaire 6 étoiles) | 4 Morceaux + 1 Diamant |

Voir [Raids Dynamax](raids-dynamax.md).

## Pour les développeurs

- Repousses : `world/item/RepelItem`, effet `world/effect/RepelEffect`.
- Multi Exp : `world/item/ExpAllItem`, `battle/ExpAllCalculator`,
  `mixin/PokemonBattleMixin`, `GenerationsInstructionProcessor.grantExpAll`.
- Boîte Noigrume : `world/item/ApricornBoxItem`, conteneur
  `world/container/GenericChestContainer` (aussi utilisé par le Walkmon, la Flûte et
  la selle de Sylveroy, qui profitent des mêmes protections).
- Objets Vœu : `world/item/WishingItem`.
