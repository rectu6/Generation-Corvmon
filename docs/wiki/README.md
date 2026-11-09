# Wiki Corvemon

Corvemon est une version modifiée de **Generations-Core** pour **Cobblemon 1.8.4**
(Minecraft 1.21.1, Fabric). Ce wiki décrit tout ce qui a été ajouté ou corrigé par
rapport au Generations d'origine.

## Pour les joueurs

| Page | Contenu |
|---|---|
| [Raids Dynamax](raids-dynamax.md) | Antres naturels, multijoueur, boucliers, ondes de choc, capture, récompenses |
| [Dynamax](dynamax.md) | Dynamaxer ses Pokémon en combat, effet visuel |
| [Interface de combat](interface-combat.md) | Interface de combat HD façon Épée et Bouclier, météo |
| [Méga-évolution](mega-evolution.md) | Animation en combat et hors combat |
| [Objets](objets.md) | Repousses, Multi Exp, Boîte Noigrume, objets Vœu |
| [Pêche](peche.md) | Canne, Super Canne, Méga Canne façon Pixelmon |
| [Pokewand](pokewand.md) | Outil d'administration : équipes, PC, édition de Pokémon |
| [Modèles 3D et shaders](rendu-3d.md) | Ce qui a été corrigé pour Iris, ombres, performances |

## Pour les administrateurs et développeurs

| Page | Contenu |
|---|---|
| [Configuration](configuration.md) | Toutes les options ajoutées et leurs valeurs |
| [Configuration des raids](configuration-raids.md) | Les 6 fichiers JSON des raids |
| [Développement](developpement.md) | Compiler, tester, organisation du code |
| [Corrections de données](../corrections-donnees.md) | CT des coffres, ultra pillars, spawns |
| [Rendu Iris en détail](../iris-rendering-fix.md) | Notes techniques du rendu |

## Résumé des ajouts

- **Raids Dynamax** : antres naturels sur terre et sur l'eau, boss chaque matin
  selon le biome, raids de 1 à 6 étoiles jusqu'à 4 joueurs (PNJ pour les places
  libres), boucliers, ondes de choc, encouragements, explosion en cas de défaite,
  capture et récompenses par joueur, tout réglable en JSON.
- **Dynamax** : quand un Pokémon Dynamaxe en combat (Poignet Dynamax), il devient
  géant, rouge, avec les nuages Dynamax au-dessus de la tête.
- **Interface de combat** façon Épée et Bouclier, en haute définition.
- **Méga-évolution** : animation arc-en-ciel avec le symbole Méga, en combat et
  hors combat.
- **Repousses**, **Multi Exp** et **Boîte Noigrume** fonctionnels.
- **Cannes à pêche** de Generations transformées en cannes Cobblemon, avec paliers
  de rareté et de niveau.
- **Pokewand** : éditeur d'équipe et de PC réservé aux opérateurs.
- **Modèles 3D** : visibles avec les shaders, ombres, éclairage par le soleil,
  clignotement rouge quand on les frappe, grands Pokémon toujours affichés,
  meilleures performances.
