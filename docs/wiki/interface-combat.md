# Interface de combat

L'interface de combat de Cobblemon est remplacée par une interface dans le style de
Pokémon Épée et Bouclier, en **haute définition**. Ce sont nos propres dessins
(aucune image du jeu) :

- les formes (panneaux, boutons, barres, icônes) sont dessinées à quatre fois leur
  taille à l'écran puis réduites en douceur ;
- le texte utilise une vraie police lissée du système (Bahnschrift, Segoe UI ou
  Arial) au lieu de la police en pixels de Minecraft ;
- les Pokémon des vignettes sont rendus à deux fois la résolution de l'écran puis
  réduits : leurs bords sont lisses.

Option `client.swshBattleUi` (voir [Configuration](configuration.md)) : `false`
remet l'interface de Cobblemon.

## Les cadres des Pokémon

**Combat simple ou double :**

- **Ton Pokémon**, en bas à gauche (juste au-dessus de la barre d'objets quand
  l'écran est étroit) : portrait, nom, sexe, niveau (« N.52 »), barre de PV verte,
  jaune puis rouge, PV exacts (« 161/161 ») et statut (PAR, BRU…).
- **Le Pokémon adverse**, en haut à droite : la même chose sans les PV exacts. Une
  petite Poké Ball après son nom indique un Pokémon sauvage déjà capturé.

Les cadres glissent hors de l'écran et reviennent quand un Pokémon est changé.

**Raid Dynamax :**

- Le boss en **barre de boss** en haut au centre : son nom en rouge, les étoiles du
  raid dessous, une longue barre de PV rose-rouge, puis le tour (« Tour 3/10 ») suivi
  du **bouclier Dynamax** quand il est levé (une case violette par coup qu'il peut
  encore encaisser) et, dès le premier K.O., le compteur « K.O. » (une case rouge par
  Pokémon K.O., sur 4).
- Le HUD de raid n'apparaît que dans le combat du raid : un combat normal juste après
  garde l'interface simple.
- **Les dresseurs alliés** en haut à gauche, en petit : portrait de leur Pokémon,
  nom, barre de PV, « K.O. » quand ils attendent leur retour.
- Ton Pokémon en bas à gauche, comme en combat simple.

## Météo et terrains

Une petite icône ronde apparaît pour chaque effet en cours, avec le nombre de tours
restants : en haut au centre en combat simple, à côté de la barre du boss en raid.

| Icône | Effet |
|---|---|
| Soleil orange | Zénith |
| Soleil rouge | Soleil intense (Groudon primal) |
| Gouttes bleues | Pluie |
| Gouttes bleu foncé | Pluie battante (Kyogre primal) |
| Traits de sable | Tempête de sable |
| Flocon bleu clair | Grêle, neige |
| Vent turquoise | Turbulence (Rayquaza méga) |
| Éclair jaune | Champ Électrifié |
| Feuille verte | Champ Herbu |
| Nuage rose | Champ Brumeux |
| Œil violet | Champ Psychique |

## Le journal du combat

À droite, juste au-dessus du menu : les derniers messages (qui attaque, avec quoi,
l'efficacité, les K.O., les actions des dresseurs en raid). Les plus anciens
s'estompent. Il remplace le journal de Cobblemon.

## Le menu d'action

Une colonne de boutons arrondis en bas à droite, chacun avec son icône :

| Bouton | Icône | Quand |
|---|---|---|
| Combat | Étoile rouge | Toujours |
| Pokémon | Poké Ball verte | Si ton Pokémon n'est pas piégé |
| Capture | Sac orange | Contre un Pokémon sauvage seul |
| Fuite | Flèches bleues | Contre un Pokémon sauvage seul |
| Abandon | Drapeau gris | Contre un dresseur ou un joueur |

Le bouton survolé devient noir, avec une flèche à sa gauche. Le bouton retour
(annuler le dernier choix) reste sous le menu.

## Les attaques

Les quatre attaques en colonne, chacune dans la **couleur de son type**, avec
l'icône du type, le nom et les PP (en jaune quand il en reste la moitié, en rouge à
zéro). Sous les attaques : le bouton retour et, s'ils sont disponibles, les
boutons **Dynamax**, **Méga-évolution**, **Capacité Z** et
**Téracristallisation**.

## Changer de Pokémon

Ton équipe en colonne de cartes à droite : portrait, nom, sexe, niveau, barre de
PV et PV. Un Pokémon K.O. ou déjà au combat est grisé et ne peut pas être envoyé
(sauf pour Second Souffle, qui ranime un Pokémon K.O.).

La confirmation d'abandon et le choix de la cible en combat double gardent les
écrans de Cobblemon.

## Pour les développeurs

- Boîte à outils HD (`client/screen/hd/`) :
  - `HdText` : texte en police TrueType du système, dessiné par Java (AWT) à la
    résolution de l'écran, une texture par texte, en cache ;
  - `HdGui` : textures HD (`textures/gui/hd/`), panneaux 9-slice, barres, étoiles ;
  - `HdModel` : rendu d'un modèle dans un framebuffer à deux fois la résolution
    puis réduit (anticrénelage) ;
  - `HdButton` : bouton arrondi.
- `client/screen/battle/SwshBattleHud` : cadres, barre de boss, alliés, météo,
  journal ; dessiné par `mixin/client/BattleOverlayMixin` à la place des tuiles de
  Cobblemon, dont les animations de glissement continuent (`trackTile`).
  `BattleMessagePaneMixin` cache le journal de Cobblemon.
- `SwshActionSelection`, `SwshSwitchSelection` : menu d'action et choix du Pokémon
  (mêmes règles que Cobblemon), installés par `mixin/client/BattleGUIMixin`.
- `SwshBattleUi` et `mixin/client/MoveTileMixin` : boutons d'attaque.
- Textures : `tools/hd_textures.py` (interfaces) et `tools/swsh_textures.py`
  (boutons du menu), formes en champs de distance sans bibliothèque :
  `python tools/hd_textures.py` pour les refaire.
- La météo et les terrains viennent du suivi de Generations
  (`BattleConditionsOverlay.currentConditionsData`).
