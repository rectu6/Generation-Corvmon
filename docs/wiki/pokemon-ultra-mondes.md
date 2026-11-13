# Pokémon des Ultra-Mondes

Phase 5. Chaque Ultra-Monde a sa population, qui change avec la météo, ses Ultra-Chimères
très rares, et chaque Ultra Warp Ride réussi offre une rencontre à l'arrivée.

## Populations

- Chaque biome généré (35) a ses espèces : communes, peu communes, rares, avec des niveaux
  propres à chaque monde (de 20-30 dans la Jungle à 30-40 dans l'Abysse et le Gratte-Ciel).
- Les Pokémon apparaissent par le système de spawn de Cobblemon : ils se combattent et se
  capturent normalement. Les espèces d'eau naissent dans l'eau.
- Fichiers : `data/generations_core/spawn_pool_world/ultra_space/<monde>/<biome>.json`
  (format Cobblemon, générés par `tools/ultra_spawns.py`).

## Météos

- Une météo sans danger favorise certaines espèces (poids × 3) et en défavorise d'autres.
- Une météo dangereuse fait davantage : les spawns rares deviennent vraiment plus fréquents
  (bucket rare × 2,5, peu commun × 1,5 dans `ultra_spawns.json`) et une espèce n'apparaît
  que pendant elle : Tritox et Pyrax (canicule), Embrylex (tempête de sable), Motisma (orage
  électrique), Aflamanoir (éruption), Hachécateur (vents tranchants), Floréclat (brume
  toxique), Salarsen (pluie acide).
- À la fin de la météo, les nouveaux spawns redeviennent normaux ; les Pokémon déjà là restent.
- Le shiny ne change pas. Les boucliers de la combinaison n'influencent pas les spawns.
- Fichiers : `data/generations_core/spawn_rules/ultra_space/<météo>.json` (règles de spawn
  Cobblemon) ; ils lisent la météo par la fonction MoLang `world.ultra_weather`.

## Ultra-Chimères sauvages

- Mouscoto (Jungle), Cancrelove (Désert), Câblifère (Centrale), Bamboiselle et Pierroteknik
  (Cratère), Katagami (Forêt), Zéroïd et Vémini (Abysse), Engloutyran et Ama-Ama
  (Gratte-Ciel), niveaux 55-65. Les secondes sont un peu plus rares que la première.
- Environ 1 spawn sur 15 000 dans un monde est une Ultra-Chimère ; une seule vivante par
  monde, et 60 minutes entre deux. Leur shiny naturel a son propre taux (1/8 192).
- Les Ultra-Chimères de l'End restent comme avant.

## Rencontre après l'Ultra Warp Ride

- Après un voyage réussi, quelques secondes après l'arrivée, un Pokémon sauvage du monde
  et du biome d'arrivée apparaît près du joueur (jamais un légendaire). Il se combat ou se
  capture normalement, ou s'ignore.
- Trois tirages indépendants, selon les années-lumière et le niveau du portail
  (`ultra_warp_ride.json`, partie `encounters`) :
  1. Ultra-Chimère ou non : de 1 % (portail simple) à 25 % (portail exceptionnel au-delà
     de 8 000 AL) ;
  2. shiny d'un Pokémon ordinaire : jusqu'à 90 % (portail exceptionnel, 8 000 AL) ;
  3. shiny d'une Ultra-Chimère : 1/4 096, sans bonus.
- Une seule rencontre par voyage : elle est tirée une fois et sauvegardée avant d'apparaître.
  Une reconnexion ne la retire pas ; si elle ne trouve pas de place, elle réessaie.
- Pendant 3 minutes, seul le voyageur peut lui lancer une Poké Ball ou la combattre.
- Réglages : `ultra_encounters.json` (délai, rayon, priorité, niveaux, Ultra-Chimères par
  monde, espèces par biome si l'on veut remplacer celles des spawns).

## Commandes de test (opérateurs)

- `/checkspawn` (Cobblemon) : les chances des Pokémon là où l'on est, météo comprise ;
  avec `/ultrawarp weather set <monde> <météo> 5`.
- `/ultrawarp spawns` : la météo qui agit ici, les buckets, les Ultra-Chimères de chaque monde.
- `/ultrawarp encounter simulate <AL> <niveau 1-4> [tirages]` : les taux obtenus et configurés.
- `/ultrawarp encounter test <AL> <niveau>` : une vraie rencontre près de soi.
- `/ultrawarp encounter pending` : les rencontres en attente et les dernières.
