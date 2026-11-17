# La Légende de Johto — configuration et commandes

Plan : `project Corvemon/JOHTO-PLAN.md` (décisions D1 à D18 validées). Tout est dans `config/generations/core/johto/`.

## Mise en place (admin, en jeu)

1. Activer le pack de ressources **« Generation 2 »** (D1) : sans lui, Raikou, Entei et Suicune n'ont pas de modèle.
2. Construire la Tour Cendrée et la Tour Carillon (le mod ne génère rien et ne charge aucun chunk).
3. Poser les statues avec les objets `raikou_statue`, `entei_statue`, `suicune_statue`, puis, en regardant chacune :
   `/generations johto place statue raikou|entei|suicune`.
4. Au centre de la Tour Cendrée, regard tourné vers l'endroit d'où l'histoire se regarde :
   `/generations johto place history [hauteur] [rayon]` (taille de la tour fantôme).
5. Pour chaque bête, au centre de sa zone d'épreuve : `/generations johto place zone <bête> circle <rayon>`
   (ou `box <de> <à>`). Entei : une arène de 13 blocs au plus dans la zone.
6. Points d'apparition sûrs : `/generations johto place spawn <bête|hooh> add` (plusieurs possibles ; le plus proche à 128 blocs est pris).
7. En haut de la Tour Carillon, en regardant le bloc de l'autel (conseillé : `ho_oh_shrine`, devenu décor) :
   `/generations johto place altar`.
8. `/generations johto place check` puis `place list`.

## Progression d'un joueur

- Premier clic sur une statue : l'histoire (50 s). Revue : accroupi + clic droit main vide.
- Clic droit avec une **Cendre Sacrée** : confirmation, la Cendre est retirée de la même case, la statue s'éveille (20 s) et la bête fuit.
- Entrer dans la zone de la bête : compte à rebours de 5 s (sortir l'annule), puis l'épreuve (3 manches).
- Échec, mort, déconnexion, sortie de zone trop longue : délai de 20 min en temps réel, reprise à la manche ratée, sans nouvelle Cendre.
- Réussite : souvenir (objet, lié au joueur), rencontre (22 s), un vrai Pokémon sauvage (niveau `beastLevel`), une seule fois par session.
- Avec les trois souvenirs : clic droit sur l'autel, « INVOQUER HO-OH », confirmation ; les souvenirs sont retirés tous ensemble, la scène (30 s), un vrai Ho-Oh sauvage.
- Une scène déjà vue se passe en maintenant **Espace** 1 s (`allowSkipSeen`) ; ce qui suit a lieu quand même.

## `johto.json`

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | true | tout activer |
| `cooldownMinutes` | 20 | délai après un échec (temps réel) |
| `countdownSeconds` | 5 | compte à rebours dans la zone |
| `outsideSeconds` | 10 | hors zone avant l'échec |
| `trialMaxMinutes` | 15 | durée max d'une épreuve |
| `maxLatencyMs` | 600 | latence prise en compte dans les fenêtres |
| `allowSkipSeen` | true | passer une scène déjà vue |
| `memoriesBound` | true | souvenirs utilisables par leur gagnant seulement |
| `respectCaughtLimits` | false | limites de capture de Generations |
| `spectatorRadius` | 32 | joueurs proches qui voient rencontres et invocation (sans caméra) |
| `beastLevel`, `beastProperties` | 50, "" | les bêtes sauvages |
| `hoOhLevel`, `hoOhProperties` | 70, "" | Ho-Oh |
| `testMode` | true | (réservé) |
| `raikou` | traces 5, rayon 2,6, interception 2,8 blocs / 3 s, annonce 1,3 s, 3 ratés, 150 s par manche | Course du Tonnerre |
| `entei` | arène 13, endurance 100, touche 20, annonce 1,3 s, sceau 3 s, attaque toutes les 2,6 s, 150 s | Jugement des Flammes |
| `suicune` | trouvée à 3 blocs, 4 à 6 notes, 6 erreurs, harmonie 3 réussites / 5 ratés, ±350 ms, 240 s | Eaux de la Renaissance |

`johto_places.json` est écrit par les commandes `place` (lisible à la main).

## Commandes (`generations_core.command.johto`, niveau 2)

- `place dimension | history [h] [r] | statue <bête> | unstatue | altar | zone <bête> circle <r> | zone <bête> box <de> <à> | zone <bête> clear | spawn <id> add|clear | test <rayon>|clear | list | check`
- `test history | awaken <bête> | trial <bête> [manche] | encounter <bête> [real] | summon [real]` (rien consommé ni sauvegardé ; `real` fait apparaître un Pokémon de test)
- `fail [joueur]`, `info [joueur]`, `reset <joueur> [bête|all|history|hooh]`, `cooldown clear <bête> [joueur]`, `give memory <bête|all> [joueur]`, `open <bête> [joueur]`, `reload`
- `giveup` (sans permission) : abandonner son épreuve.
