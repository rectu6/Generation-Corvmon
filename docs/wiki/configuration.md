# Configuration

Fichier : `config/generations/core/main.json` (créé au premier lancement). Seules
les options ajoutées par Corvemon sont listées ici.

## Section `client`

| Option | Défaut | Effet |
|---|---|---|
| `shaderShadows` | `true` | Avec un shader, les modèles 3D projettent des ombres |
| `shaderShadowDistance` | `32` | Distance (blocs) au-delà de laquelle ils n'en projettent plus (0 = sans limite) |
| `shaderSunLighting` | `true` | Avec un shader, les modèles 3D sont éclairés par le soleil ou la lune |
| `traceOpenGlErrors` | `false` | Diagnostic : écrit dans le log l'origine des erreurs OpenGL (un peu plus lent) |
| `swshBattleUi` | `true` | Interface de combat façon Épée et Bouclier en HD (`false` : celle de Cobblemon) |

## Section `raids` (apparence)

| Option | Défaut | Effet |
|---|---|---|
| `dynamaxHeight` | `5.0` | Hauteur (blocs) d'un Pokémon qui Dynamaxe en combat, quelle que soit son espèce |
| `bossHeight` | `9.0` | Hauteur (blocs) du Pokémon d'un raid |

Les règles des raids (antres, difficultés, boucliers, capture, récompenses…) sont
dans `config/generations/core/raids/` : voir
[Configuration des raids](configuration-raids.md). Les anciennes options
`bossHealth`, `legendaryChance`, `catchChance5Stars` et `catchChance6Stars` ne
servent plus qu'à initialiser ces fichiers.

## Fichiers de données

| Fichier | Contenu |
|---|---|
| `data/generations_core/worldgen/placed_feature/raid_den.json` | Génération des antres (la rareté est dans `raids/dens.json`) |
| `data/generations_core/pokerods/*.json` | Flotteur des cannes Generations |

On peut les remplacer avec un datapack sans recompiler le mod.
