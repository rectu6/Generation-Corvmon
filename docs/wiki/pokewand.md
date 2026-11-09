# Pokewand

Outil d'administration **réservé aux opérateurs** (niveau 2, comme `/give`). Le
serveur vérifie la permission à chaque action : un joueur normal qui le ramasse ne
peut rien en faire.

## Utilisation

- **Clic droit** : ton équipe.
- **Clic droit sur un joueur** : son équipe.

L'écran montre les **6 emplacements** avec le modèle 3D, le nom, le niveau et le
nombre d'attaques de chaque Pokémon.

- **Ouvrir son PC** : le PC du joueur s'ouvre, tu peux y déplacer ses Pokémon comme
  dans le tien.
- **Actualiser** : relit l'équipe.

## Éditer un Pokémon

Clic sur un emplacement (vide pour en ajouter un). Aperçu 3D à gauche.

| Champ | Détail |
|---|---|
| Espèce | Recherche : « gir » propose ce qui commence par gir (Giratina, Girafarig…), puis ce qui contient gir |
| Forme | Bouton qui fait défiler les formes de l'espèce |
| Aspects / textures | Liste séparée par des virgules (variantes, textures) |
| Chromatique, Sexe | Boutons |
| Niveau, Bonheur | Nombres |
| Nature, Talent, Objet tenu, Poké Ball | Champs avec suggestions |
| Surnom | Texte |
| 4 attaques | Champs avec suggestions |
| IV (0-31) et EV (0-252) | Six cases chacun : PV, Atq, Déf, ASp, DSp, Vit |

**Un champ vide garde la valeur actuelle.** Pour un nouveau Pokémon, Cobblemon
génère alors les valeurs manquantes (IV au hasard, nature, attaques de son niveau).

- **Enregistrer** applique les changements.
- **Supprimer** retire le Pokémon (deuxième clic pour confirmer).
- Chaque modification est écrite dans le log du serveur (qui a modifié quoi).

## Pour les développeurs

- Serveur : `world/pokewand/PokeWand` (permissions, édition, PC),
  `PokeWandPokemon` (données échangées).
- Réseau : `network/packets/pokewand/` (`S2CPokeWandPartyPacket`,
  `C2SPokeWandActionPacket`).
- Écrans : `client/screen/pokewand/` (`PokeWandPartyScreen`, `PokeWandEditScreen`,
  `SuggestionField` pour la recherche).
