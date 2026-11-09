# Modèles 3D et shaders

Les Pokémon et blocs 3D de Generations utilisent leur propre moteur de rendu
(RareCandy). Corvemon corrige ses problèmes avec les shaders (Iris, testé avec
Complementary Unbound) et l'accélère. Détails techniques :
[iris-rendering-fix.md](../iris-rendering-fix.md).

## Ce qui marche maintenant

| Avant | Maintenant |
|---|---|
| Modèles 3D invisibles avec un shader | Visibles |
| Modèles transparents de près (on voyait à travers) | Opaques |
| Pas d'ombre avec un shader | Ombres projetées, dont celles des grands Pokémon |
| Éclairage figé | Éclairés par le soleil ou la lune, comme le décor |
| Pas de réaction aux coups | Clignotent en rouge quand le joueur les frappe (pas en combat) |
| Grands Pokémon (Arceus, Giratina) qui disparaissaient en levant la tête | Toujours affichés |
| Distributeurs et PC de table transparents | Opaques, seule la vitre est transparente |
| Erreur OpenGL de texture dans le log | Corrigée |
| Plantage en retirant le pack de modèles 3D | Corrigé |
| Modèles gardés en mémoire (lag qui augmente) | Mémoire libérée |

## Performances

Mesuré sur une scène d'environ 20 Pokémon avec Complementary :

| Situation | Images/s |
|---|---|
| Avant les corrections | 30 à 40 |
| Après, ombres jusqu'à 32 blocs | environ 70 |
| Sans les ombres d'entités du shader | environ 90 |
| Shader sans modèles 3D | environ 118 |

Les ombres des Pokémon loin de la caméra utilisent un modèle simplifié, et au-delà
de 32 blocs les Pokémon ne projettent plus d'ombre (réglable).

## Réglages utiles

- Dans Complementary, **Ombres des entités** (Performance) coupe les ombres des
  Pokémon, comme celles des autres entités.
- Côté mod : `client.shaderShadows`, `client.shaderShadowDistance`,
  `client.shaderSunLighting` (voir [Configuration](configuration.md)).
