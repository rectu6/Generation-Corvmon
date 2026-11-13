# Ultra-Mondes

Traverser un portail pendant l'[Ultra Warp Ride](ultra-warp-ride.md) t'emmène dans un
Ultra-Monde : une vraie dimension, la même pour tous les joueurs du serveur, qui garde
ce que tu y construis. La couleur du portail donne le monde :

| Portail | Monde |
|---|---|
| Vert | Ultra-Jungle |
| Orange | Ultra-Désert |
| Jaune | Ultra-Centrale |
| Gris argenté | Ultra-Cratère |
| Violet | Ultra-Forêt |
| Bleu | Ultra-Abysse |
| Rouge sombre | Ultra-Gratte-Ciel |

Chaque monde a **cinq biomes** à explorer :

| Monde | Biomes |
|---|---|
| Ultra-Jungle | Jungle géante, Jungle marécageuse, Jungle montagneuse, Jungle bioluminescente (rare), Jungle des ruines naturelles |
| Ultra-Désert | Désert blanc, Désert cristallin, Canyon d'ivoire, Oasis extraterrestre (rare), Désert de verre (rare) |
| Ultra-Centrale | Plaines conductrices, Forêt de câbles, Montagnes électriques, Champs de cristaux (rare), Vallée magnétique |
| Ultra-Cratère | Plaines aux cratères, Grand cratère, Forêt de flèches, Champs de fumerolles, Plateaux jaunes |
| Ultra-Forêt | Forêt émeraude brumeuse, Jardin zen, Bosquet aux fleurs d'or, Forêt origami, Vallée des feuilles tranchantes (rare) |
| Ultra-Abysse (souterrain) | Cavernes cristallines, Forêt fongique, Lacs souterrains, Abîmes rocheux, Grottes luminescentes (rare) |
| Ultra-Gratte-Ciel (ville envahie) | Centre-ville envahi, Quartier résidentiel, Parc urbain, Ruines effondrées, Zone industrielle |

Les bâtiments, ressources propres, météos et Pokémon arriveront plus tard.

## L'arrivée

- Après le portail, l'écran reste blanc le temps que le monde se charge : tu ne bouges
  pas et rien ne peut te blesser.
- Tu arrives debout sur un sol solide, à l'air libre, loin de la lave, du vide, des
  précipices et des constructions des autres joueurs. Les joueurs n'arrivent pas tous
  au même endroit.
- Tu es protégé quelques secondes après l'arrivée. Ton Solgaleo ou ton Lunala reste
  dans ton équipe.
- Si tu te déconnectes pendant le passage, il reprend à ta prochaine connexion.
- Si le monde est fermé par le serveur, tu restes où tu étais.

## Tester (opérateurs)

`/ultrawarp test biome <biome>` t'emmène dans un biome précis (par exemple
`generations_core:ultra_desert/oasis`), de façon sûre. F3 affiche le biome où tu es.

## Revenir

Pour l'instant, un opérateur revient avec `/ultrawarp test return` (au point d'où il
était parti). Le vrai retour se fera avec Solgaleo ou Lunala, par la roue
d'interaction.

## Structures

Chaque Ultra-Monde a une grande structure emblématique, rare (environ une tous les
600 blocs), et quatre petits sites bien plus fréquents, chacun en deux variantes. Pas de
coffres à butin.

| Monde | Grande structure | Petits sites |
|---|---|---|
| Ultra-Cratère | base lunaire abandonnée (`ultra_crater/lunar_base`) | mini-station, hangar, poste d'observation, abri |
| Ultra-Jungle | temple végétal (`ultra_jungle/temple`) | camp, observatoire dans l'arbre, ruines, cabane |
| Ultra-Désert | palais cristallin enfoui (`ultra_desert/palace`) | avant-poste, sanctuaire enseveli, station d'observation, ruines |
| Ultra-Centrale | centrale énergétique (`ultra_central/plant`) | sous-station, station de maintenance, tour relais, abri technique |
| Ultra-Forêt | sanctuaire origami (`ultra_forest/sanctuary`) | petit sanctuaire, pavillon de méditation, tour en bois, pont de jardin |
| Ultra-Abysse | laboratoire cristallin (`ultra_abyss/lab`) | petit labo, station d'analyse, poste de forage, plateforme d'observation |
| Ultra-Gratte-Ciel | complexe de tours en ruine (`ultra_skyscraper/complex`) | station-service, petit immeuble effondré, poste de sécurité, entrepôt |

`/locate structure generations_core:<id>` les trouve. Dans l'Abysse, chaque structure
démarre dans une caverne (un sol, de l'air au-dessus et autour), jamais dans la roche
pleine. Dans le Gratte-Ciel, la tour principale du complexe a douze étages visitables
(une échelle dans le noyau central), et les pâtés de maisons de la ville laissent la
place aux structures.

## Ressources

On trouve en minant les minerais classiques (un peu moins que dans l'Overworld), avec une
spécialité par monde, et un biome où elle est un peu plus riche :

| Monde | Spécialité | Aussi |
|---|---|---|
| Ultra-Jungle | charbon, fer | saphir, Pierre Plante, Pierre Eau |
| Ultra-Désert | or | cristal, rubis, Pierre Soleil |
| Ultra-Centrale | redstone | cristal, silicium, Pierre Foudre (pas de cuivre) |
| Ultra-Cratère | diamant | rubis, météorite, Pierre Feu |
| Ultra-Forêt | fer | Pierre Plante, Pierre Éclat |
| Ultra-Abysse | lapis, redstone | cristal, saphir, Pierre Nuit, Pierre Eau, Pierre Lune |
| Ultra-Gratte-Ciel | fer, charbon | silicium, Pierre Nuit, Pierre Aube |

Les pierres évolutives sont rares. L'**Ultrite** (débris d'Ultrite) se trouve dans les
sept mondes, très profond, aussi rare que les débris antiques du Nether : pioche en
diamant, puis four pour obtenir un fragment d'Ultrite.

Les grands cristaux, pics et plaques des paysages sont décoratifs (verre, glace,
prismarine) : les miner ne rapporte rien de précieux.

L'Ultra-Abysse est éclairé par une lueur diffuse (des lumières invisibles dans l'air des
cavernes), visible aussi avec un shader.

## Configuration (serveur)

`config/generations/core/ultra_space/ultra_worlds.json` : ouvrir ou fermer chaque
monde, la recherche des points d'arrivée, la protection, les options de test.
`ultra_resources.json` (même dossier) : les minerais de chaque monde. Voir
`docs/ultra-warp-ride.md` (phase 4.1) pour chaque option.
