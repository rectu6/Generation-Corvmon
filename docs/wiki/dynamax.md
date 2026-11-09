# Dynamax

Version Corvemon du Dynamax, inspirée du fonctionnement de CobblemonMegaShowdown
mais écrite pour Corvemon (aucun code repris), avec les objets de Generations.

## En combat

Avec un **Poignet Dynamax** de Generations dans l'inventaire, le bouton Dynamax
apparaît en combat (géré par Cobblemon et Showdown) :

- une fois par combat, pendant **trois tours** ;
- les attaques deviennent des **Capacités Dynamax** (Capacités Gigamax pour un
  Pokémon qui a le Facteur Gigamax) ;
- ses PV augmentent selon son **Niveau Dynamax** (de ×1,5 au niveau 0 à ×2 au
  niveau 10) ;
- avec le **Facteur Gigamax**, il prend sa **forme Gigamax** si son espèce en a une.

Corvemon ajoute l'apparence, comme dans CobblemonMegaShowdown :

- le Pokémon **grandit** en deux secondes jusqu'à une **hauteur fixe**, quelle que
  soit son espèce : 5 blocs en combat, 9 blocs pour un boss de raid (un Pichu
  devient aussi géant qu'un Wailord) ;
- il devient **rouge** : sa couleur est teintée de rouge, avec une lueur rose-rouge
  qui pulse, sur tous les modèles (ceux de Cobblemon et les modèles 3D de
  Generations) ;
- des **nuages Dynamax** rouges flottent au-dessus de sa tête : trois traînées de
  bulles qui s'enroulent autour de lui, tournent lentement et ondulent ;
- à la fin du Dynamax ou du combat, il reprend sa taille et ses couleurs.

Ça marche dans tous les combats : sauvages, dresseurs, joueurs et raids. Le
Pokémon d'un [raid](raids-dynamax.md) est lui aussi Dynamaxé.

## Niveau Dynamax et Facteur Gigamax

- Le **Niveau Dynamax** (0 à 10) s'affiche en barre rouge dans le **résumé** du
  Pokémon (page des statistiques), comme le bonheur.
- Le **Facteur Gigamax** n'existe que pour les espèces qui ont une forme Gigamax
  (Dracaufeu, Ronflex, Pikachu, Évoli, Shifours, etc.).

## Objets Dynamax de Generations

Clic droit avec l'objet, puis choix du Pokémon dans l'équipe (comme les bonbons de
Cobblemon) :

| Objet | Effet | Obtention |
|---|---|---|
| Poignet Dynamax | Permet de Dynamaxer en combat | Objet de Generations |
| Bonbon Dynamax | Niveau Dynamax +1 (jusqu'à 10) | Raids 3 étoiles et plus |
| Maxi Soupe | Donne ou retire le Facteur Gigamax (rend le bol) | 3 Maxi Champi + 1 bol |
| Maxi Miel | Ranime un Pokémon K.O. avec tous ses PV | Recette de Generations |
| Maxi Champi | Ingrédient de la Maxi Soupe | Recette de Generations |

## Pokewand

Le [Pokewand](pokewand.md) permet aussi de régler le Niveau Dynamax et le Facteur
Gigamax d'un Pokémon.

## Réglages

`raids.dynamaxHeight` (5 blocs) et `raids.bossHeight` (9 blocs) dans
`config/generations/core/main.json`. Le niveau maximum est celui de Cobblemon
(`maxDynamaxLevel`, 10).

## Pour les développeurs

- `battle/DynamaxLevel` : Niveau Dynamax (`dmaxLevel` de Cobblemon, envoyé à
  Showdown) et sa barre du résumé (caractéristique globale `dynamax_level`,
  `data/generations_core/global_species_features/dynamax_level.json`), synchronisés
  au début de chaque combat ; Facteur Gigamax (`gmaxFactor`).
- `world/item/DynamaxItems` : Bonbon Dynamax, Maxi Soupe, Maxi Miel
  (`PokemonSelectingItem` de Cobblemon).
- `battle/DynamaxVisuals` : agrandissement progressif (`scaleModifier`, calculé
  depuis la hitbox de l'espèce pour atteindre la hauteur voulue), aspect forcé
  `dynamax` (synchronisé aux clients).
- `client/render/DynamaxCloudRenderer` (appelé par `mixin/client/PokemonRendererMixin`) :
  teinte rouge des modèles Cobblemon (`red/green/blue` du `PosableModel`) et
  nuages en 3D, des sphères ombrées construites dans le code
  (texture `textures/entity/dynamax_cloud.png`).
- `mixin/showdown/DynamaxStartInstructionMixin` et `DynamaxEndInstructionMixin` :
  détectent les messages Showdown `-start ... Dynamax` et `-end ... Dynamax`.
- Lueur rouge des modèles 3D de Generations : `RareCandyBone` lit l'aspect `dynamax`, `Pipelines` (uniform
  `overlayColor`) mélange un rouge qui pulse dans `animated.fs.glsl`.
- Forme Gigamax en combat : le changement de forme de Showdown passe par
  `GenerationsInstructionProcessor.processFormeChangeInstruction` (aspect `gmax`).
