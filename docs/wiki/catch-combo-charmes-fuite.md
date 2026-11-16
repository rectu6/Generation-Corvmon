# Catch Combo, charmes, chances Shiny, fuite instantanée, performances (build 54)

Plan validé : `project Corvemon\COMBO-FUITE-PERF-PLAN.md`. Toutes les valeurs sont provisoires et réglables.

## Chances Shiny (`world/shiny/`)

- `ShinyOdds` est le seul endroit qui calcule une chance Shiny. Il s'abonne à `SHINY_CHANCE_CALCULATION`
  de Cobblemon en dernier (`Priority.LOWEST`), donc après les autres mods.
- Ordre d'application :
  1. taux de base de Cobblemon (`shinyRate`, 1 sur 8192) et modificateurs des autres mods ;
  2. bonus des sources enregistrées (Charme Chroma, Catch Combo de l'espèce du combo), cumulés
     **additivement** : multiplicateur = 1 + Σ(bonus − 1) (option `multiplicative` : Π bonus) ;
  3. plafond du multiplicateur (×6 par défaut, désactivable) ;
  4. chance spéciale de l'Ultra Warp Ride : jamais diminuée ; les bonus y ajoutent base × leur surplus,
     `p = p_table + p_base × min(plafond − 1, Σ(bonus − 1))`. Exemple : 1/4096 avec Charme ×2 et combo ×2,5 → 1/1820.
- Restent à part : Ultra-Chimères naturelles, raids, appâts de pêche de Cobblemon.
- Config `config/generations/core/shiny/shiny_odds.json` : `enabled`, `stacking`, `capEnabled`, `maxMultiplier`,
  `ultraWarpRideBonuses`, `logShiny` (chaque calcul avec bonus écrit dans le journal).

## Charmes (`world/charm/`)

| Charme | Effet | Valeur (`charms/charms.json`) |
|---|---|---|
| Charme Chroma (`shiny_charm`) | bonus Shiny | ×2 |
| Charme Exp (`exp_charm`) | EXP de combat | ×1,5 |
| Charme Stabilité (`catching_charm`) | capture critique | ×2 |
| Charme Insigne (`mark_charm`) | `applyPotentialMarks` (rien ne l'appelle dans Cobblemon 1.8.4) | ×3 |
| Charme Scintillant (`glimmering_charm`) | quantités des récompenses de raid Dynamax | ×1,5 (fraction tirée) |
| Charme Ovale (`oval_charm`) | aucun (reproduction plus tard) | — |
| Méga-Charme (`mega_charm`) | Gemme Sésame | — |
| Vieux Grigri (`old_charm`) | objet de collection | — |

Un charme compte dans l'étui ou dans l'inventaire (`countInInventory`) ; deux du même ne cumulent pas.
Aucune recette ni récompense : créatif ou `/give`.

## Étui à charmes

- Bouton (icône d'étui, `gui/charm_case_button.png`) dans l'inventaire de survie, à droite du cadre du joueur, au niveau
  de la première rangée de l'artisanat ; il suit l'inventaire quand le livre de recettes s'ouvre.
- Menu au style d'un coffre : une rangée de 8 cases, une par charme (ordre de `Charm`), puis une rangée de 4 objets de combat :
  objet Méga (un seul objet du tag `key_stones` : Gemme Sésame, Méga-Anneau, Méga-Bracelet…), `dynamax_bands`, `tera_orbs`,
  `z_rings`. Chaque case ne prend que son objet, un seul, et montre sa silhouette quand elle est vide, comme les cases d'armure
  (`item/charm_case/empty_*.png`, objet en gris). Textures faites par `tools/charm_case_icons.py`.
- Une sauvegarde d'une autre disposition est rangée par type d'objet au chargement ; ce qui ne trouve pas de case va dans
  l'inventaire (ou à la connexion s'il est plein).
- Sauvegardé dans les données du joueur (`generations_core:charm_case`), recopié à la réapparition, gardé à la mort
  (`keepOnDeath`, sinon lâché comme l'inventaire si `keepInventory` est faux).
- Les objets clés de l'étui comptent pour la Méga-Évolution, le Dynamax, la Téracristallisation et les capacités Z.

## Catch Combo (`world/catchcombo/`)

- Capturer la même espèce à la suite. État en mémoire, perdu à la déconnexion. Paliers par défaut (à partir de N captures) :
  1–10 aucun bonus ; 11 → Shiny ×1,15 ; 21 → ×1,3 et 1 IV à 31 ; 31 → ×1,5, 1 IV ; 41 → ×1,75, 2 IV ; 51 → ×2, 2 IV ;
  76 → ×2,25, 3 IV ; 101 et plus → ×2,5, 3 IV (maximum ; le compteur continue sans bonus de plus).
- Ruptures (chacune désactivable) : autre espèce (le combo repart à 1), K.O. d'un sauvage ou d'un boss, combat sauvage
  terminé sans issue, combat contre un joueur, déconnexion. Pas de rupture : rencontre, fuite, balle ratée, dimension.
- Bonus au moment du spawn, sur les spawns du spawner du joueur du combo (pêche comprise) ; IV jamais baissés, appliqués une fois ;
  le spawn boosté est réservé 60 s à ce joueur. Légendaires, fabuleux et Ultra-Chimères exclus par défaut.
- Config `catch_combo/catch_combo.json`. Commandes (OP) : `/catchcombo info|set|reset|odds|simulate|reload`.
- HUD en haut à gauche (`catch_combo_client.json`), masqué en combat ; touche « Afficher/masquer le Catch Combo »
  (non assignée, catégorie Generations).

## Fuite instantanée (`battle/flee/`)

- Le bouton Fuite termine tout de suite un combat sauvage à un seul joueur (pas les raids ; boss et rencontre Ultra selon la config),
  par la branche de fuite de Cobblemon (résultat `FLED`, sauvage soigné et laissé dans le monde).
- Pièges (Regard Noir, Marque Ombre, Piège Sable, Étreinte…) lus dans l'état de Showdown du tour : « Impossible de fuir ! ».
  Boule Fumée tenue et talent Fuite passent outre.
- Formule `always` (défaut) ou `speed` (formule des jeux, une tentative par tour). Config `battle/flee.json`.

## Performances (`client/screen/hd/HdText`, `client/perf/`)

- Les polices Java sont préparées sur un fil d'arrière-plan à la fin du chargement du client ; un écran HD ouvert avant attend le reste.
- Polices en cache, pixels lus en bloc, au plus 6 ms de nouveaux textes par image (le reste à l'image suivante), cache vidé au
  changement d'échelle et au rechargement des ressources.
- Option `client.profileFirstUse` (`config/generations/core/main.json`, désactivée) : ouvertures d'écran, images > 50 ms,
  délai de l'interface de combat, durée de `BattleRegistry.startBattle` côté serveur, tout dans le journal avec `[first-use]`.
- La chauffe de Showdown n'est pas faite : Cobblemon ignore un combat inconnu mais écrit une ligne de journal par message ;
  à décider après la mesure de `startBattle` en jeu.
