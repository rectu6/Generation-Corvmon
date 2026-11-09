# Méga-évolution

## Animation

Quand un Pokémon méga-évolue, une animation de 4,5 secondes se joue, inspirée de
celle des jeux et de l'évolution de Cobblemon (raccourcie, 12 secondes à l'origine) :

1. Le **symbole Méga** apparaît au-dessus du Pokémon et l'énergie se rassemble.
2. Le Pokémon disparaît dans une **lumière arc-en-ciel**, avec des rayons, des
   étincelles et de petits symboles Méga qui montent.
3. **Explosion** : le Pokémon apparaît sous sa forme Méga, le grand symbole revient.

Sons : activation de balise, résonance d'améthyste, scintillement de feu
d'artifice.

## En combat

Le combat attend la fin de l'animation, et la forme Méga apparaît exactement à
l'explosion (3,4 s), cachée par la lumière juste avant. Le message de Méga-évolution
s'affiche ensuite. Méga, Méga X et Méga Y sont concernées.

## Hors combat

Generations permet de méga-évoluer un Pokémon hors combat avec la **roue
d'interaction** (le Pokémon doit tenir sa Méga-Gemme). La même animation se joue :
le Pokémon s'arrête et prend sa forme Méga à l'explosion. Un nouveau clic pendant
l'animation est ignoré.

## Pour les développeurs

- `battle/MegaEvolutionAnimation` : envoie l'animation
  `q.bedrock_stateful('mega_evolution', 'mega_evolution', ...)` aux joueurs
  proches (même mécanisme que l'évolution de Cobblemon).
- Animation : `assets/generations_core/bedrock/generic/animations/mega_evolution.animation.json`.
- Particules : `bedrock/particles/mega_*.particle.json` (copies des particules
  d'évolution de Cobblemon, minutage compressé, couleurs arc-en-ciel) et
  `mega_symbol` / `mega_symbols_rising` (texture `textures/particle/mega_symbol.png`).
- Combat : `GenerationsInstructionProcessor.processFormeChangeInstruction` attend
  l'animation avec `dispatchWaiting`. Hors combat : `GensInteractPokemonHandler`.
