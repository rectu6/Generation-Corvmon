# Pêche

La Canne, la Super Canne et la Méga Canne de Generations sont des **cannes
Cobblemon** : elles pêchent des Pokémon comme les cannes de Cobblemon (appâts,
mini-jeu, Pokémon selon le biome, l'heure et la météo).

## Ce qui change selon la canne

Comme dans Pixelmon, la canne limite ce qu'on peut pêcher, et le niveau suit celui
de ton équipe :

| Canne | Flotteur | Rareté maximale | Niveau des Pokémon pêchés |
|---|---|---|---|
| Canne | Poké Ball | commun | environ ½ de la moyenne de ton équipe, entre 5 et 20 |
| Super Canne | Super Ball | rare | environ ¾, entre 15 et 45 |
| Méga Canne | Hyper Ball | ultra-rare | environ ta moyenne, 30 minimum |

À ± 3 niveaux près. Avec une équipe vide, le niveau est le minimum de la canne.

## L'endroit compte

Les Pokémon viennent des données de pêche de Cobblemon, qui dépendent du biome :
eau froide (océans gelés, taïga), eau chaude (océans tièdes, jungle), rivières,
marais, profondeurs… Un océan froid et un océan chaud ne donnent pas les mêmes
Pokémon.

## La Canne Rubis

Elle ne change pas : c'est un objet spécial de Generations, qui garde son propre
fonctionnement.

## Pour les développeurs

- Les cannes sont des `PokerodItem` de Cobblemon, définies par
  `data/generations_core/pokerods/*.json` (Poké Ball de leur flotteur).
- `mixin/PokerodItemMixin` leur garde leur nom (Cobblemon nomme toutes ses cannes
  « Poké Canne »).
- `world/fishing/GenerationsFishing` : rareté par l'événement `SPAWN_BUCKET_CHOSEN`,
  niveau par `BOBBER_SPAWN_POKEMON_POST`.
