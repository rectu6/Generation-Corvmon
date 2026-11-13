# Ultra Warp Ride : notes techniques

Système Ultra-Espace, développé par phases. Ce document décrit ce qui est codé et
comment les phases suivantes s'y brancheront.

## Phase 1 : accès depuis l'autel céleste

Un clic droit sur l'autel céleste (main vide ou non) ouvre le menu « Ultra Warp
Ride ». Ce menu affiche la face actuelle de l'autel : Soleil, qui demande Solgaleo, ou
Lune, qui demande Lunala. « Commencer le voyage » crée une session de test. Il n'y a
pas encore de tunnel, de dimension, de portail ni de rencontre.

### Fichiers

| Fichier (`common/.../common/`) | Rôle |
|---|---|
| `world/ultraspace/UltraWarpVariant.kt` | `SUN` / `MOON`, espèce requise, lue dans l'état `IS_SUN` du bloc |
| `world/ultraspace/UltraWarpSession.kt` | Une session : joueur, variante, autel (dimension + position), début, origine, étape, raisons de fin |
| `world/ultraspace/UltraWarpSessions.kt` | Le gestionnaire unique : seul endroit qui crée ou termine une session |
| `world/ultraspace/UltraWarpRide.kt` | Ouverture du menu, vérifications, évènements, résultats |
| `world/ultraspace/UltraSpaceConfig.kt` | `config/generations/core/ultra_space/ultra_warp_ride.json` |
| `network/packets/ultraspace/UltraWarpPackets.kt` | `S2CUltraWarpPacket` (menu), `C2SUltraWarpActionPacket` (START / END) |
| `network/packets/ultraspace/UltraWarpPacketHandlers.kt` | Réception des deux paquets |
| `client/screen/ultraspace/UltraWarpRideScreen.kt` | Le menu (boîte à outils HD des raids) |
| `commands/UltraWarpCommand.kt` | `/ultrawarp` : sessions de test, liste, sessions simulées |
| `mixin/CommandsMixin.java` | Enregistre les commandes du mod là où Minecraft construit les siennes |
| `world/level/block/shrines/CelestialAltarBlock.kt` | `useWithoutItem` ouvre le menu ; le ticker jour/nuit n'a pas changé |

Tout est dans `common`, sans code propre à un loader : les paquets passent par
`GenerationsNetwork`, les évènements par les `PlatformEvents` de Cobblemon et les
commandes par un mixin commun. Seul Fabric est compilé et testé.

### Solo et multijoueur : un seul système

- Le gestionnaire `UltraWarpSessions` vit sur le **serveur logique** : le serveur
  intégré en solo (et quand le monde est ouvert en LAN), le serveur dédié en
  multijoueur. Même code, même limite, mêmes vérifications.
- La config est lue par ce serveur : à chaque ouverture d'un monde en solo, à chaque
  démarrage d'un serveur dédié. Le client ne la lit jamais, il reçoit la limite dans
  le paquet du menu. C'est donc toujours la config du serveur qui compte.
- Le client ne lit jamais le gestionnaire. En solo, il partage la JVM du serveur
  intégré : un accès direct y marcherait, puis casserait sur un serveur dédié.
- L'écran n'est référencé que par le gestionnaire de paquet client, enregistré
  seulement côté client (comme pour les raids et Cobblemon).
- Quitter un monde en solo arrête le serveur intégré : toutes les sessions sont
  terminées (`SERVER_STOPPING`) avant le monde suivant.
- Le menu ne met pas le jeu en pause en solo, le serveur intégré continue de répondre.

### Ce que le serveur vérifie

Le client n'envoie que la position de l'autel, l'action et la face qu'il a affichée.
À chaque « Commencer le voyage », le serveur revérifie dans cet ordre (la première
condition manquante donne le message) :

1. le joueur n'a pas déjà une session (`ALREADY_TRAVELLING`) ;
2. `maxSessions` n'est pas 0 (`DISABLED`) ;
3. le joueur est en vie et pas spectateur (`UNAVAILABLE`), et pas en combat
   (`IN_BATTLE`) ;
4. le joueur peut atteindre le bloc (`canInteractWithBlock`, avant de lire le bloc pour
   ne jamais charger un chunk lointain), et c'est bien un autel céleste (`TOO_FAR`) ;
5. la face de l'autel, lue maintenant, est celle que le menu montrait, sinon le menu se
   met à jour (`VARIANT_CHANGED`) ;
6. Solgaleo ou Lunala est dans l'équipe : `player.party()`, espèce comparée par
   identifiant `cobblemon:solgaleo` / `cobblemon:lunala`, K.O. accepté (`MISSING_POKEMON`) ;
7. il reste une place, vérifié par le gestionnaire au moment de créer la session (`NO_SLOT`).

Les points 1 à 3 sont communs à l'autel et à la commande de test (`refusal`). Les
paquets sont traités sur le thread serveur, donc deux clics rapides ne peuvent pas
créer deux sessions. Le menu ignore aussi un second clic tant que le serveur n'a pas
répondu.

### Sessions

- En mémoire côté serveur, par UUID de joueur, sans sauvegarde. La phase 1 ne prend
  ni objet ni Pokémon et ne déplace pas le joueur, donc rien à perdre ou à dupliquer,
  et un redémarrage libère toutes les places.
- Un joueur par session, `maxSessions` à la fois (6 par défaut), pas de file d'attente.
- Origine : `ALTAR` (le menu), `COMMAND` (`/ultrawarp test start`) ou `SIMULATED`
  (place occupée sans joueur, pour tester la limite seul).
- Fin, toujours par `UltraWarpSessions.end(joueur, raison)` :
  - `FINISHED` : bouton « Terminer le voyage » ou `/ultrawarp test end` ;
  - `CANCELLED` : le joueur meurt (évènement `PLAYER_DEATH`, priorité la plus basse,
    ignoré si un autre mod annule la mort) ;
  - `DISCONNECTED` : déconnexion, plus une vérification chaque seconde au cas où
    (les sessions simulées n'ont pas de joueur et ne sont pas concernées) ;
  - `SERVER_STOPPING` : arrêt du serveur, y compris quitter un monde en solo.
- Fermer le menu garde la session : un clic sur n'importe quel autel rouvre l'écran
  du voyage en cours.
- Chaque début et chaque fin sont écrits dans le log du serveur avec le nombre de
  sessions.

### Commande de test

`/ultrawarp`, niveau d'opérateur 2. En solo, il faut que les commandes soient
autorisées (« Autoriser les commandes » à la création du monde, ou « Ouvrir au LAN »
avec les commandes).

| Commande | Effet |
|---|---|
| `/ultrawarp test start sun\|moon [joueurs]` | Session de test sans autel ni Pokémon requis ; l'écran « Voyage en cours » s'ouvre. Même limite et mêmes refus que l'autel (déjà en voyage, en combat, plus de place…) |
| `/ultrawarp test end [joueurs]` | Termine le voyage et ferme l'écran |
| `/ultrawarp sessions` | Liste les voyages : joueur, face, origine, durée, et `x/max` |
| `/ultrawarp test simulate <n>` | Ajoute jusqu'à `n` sessions simulées, tant qu'il reste des places |
| `/ultrawarp test clear` | Retire les sessions simulées |

Sans argument, `start` et `end` visent le joueur qui tape la commande.

## Phase 2.1 : le voyage dans le tunnel

Après « Commencer le voyage » (ou `/ultrawarp test start`), le joueur voit Solgaleo
ou Lunala voler dans un tunnel 3D, lui-même assis dessus, caméra à la troisième
personne. Il quitte avec Échap (confirmation), revient dans le monde à l'autel, et
peut recommencer.

### Le choix technique

- **Les modèles 3D ne s'affichent que dans le rendu du monde.** Dans les interfaces,
  Generations montre des sprites 40×40 de ses Pokémon : un écran ne pouvait donc pas
  montrer Solgaleo en 3D. Le voyage est dessiné pendant le rendu du monde, au moment
  où RareCandy dessine déjà ses modèles (compatible Iris comme le reste).
- **Une scène côté client, en tapis roulant.** Le joueur reste à l'autel côté serveur.
  Côté client, la caméra de Minecraft est placée à un point vide très au-dessus du
  monde (l'ancre, fixe) et tournée comme la caméra du voyage ; tout est dessiné par
  rapport à elle. C'est le tunnel qui défile : aucun bloc, aucun chunk, aucune
  téléportation, rien à sauvegarder.
- **Rien n'est modifié durablement.** Caméra, champ de vision, brouillard, HUD : les
  mixins n'agissent que pendant le voyage de ce joueur. À la fin (bouton, mort,
  déconnexion, erreur de rendu), l'image suivante est celle de Minecraft.

### Serveur : l'autorité

- La session passe à l'étape `RIDE` avec une **graine** (aléatoire, ou fixée par
  `test.fixedSeed`), le **tick de départ** (temps de jeu du serveur) et les
  **réglages du trajet** de sa config. Il les envoie au client
  (`S2CUltraWarpRideStartPacket`), avec l'apparence du Solgaleo/Lunala de l'équipe
  (shiny…).
- La distance parcourue vaut `vitesse × (temps − départ) / 20` des deux côtés
  (`UltraWarpSession.distance`) : les orbes, obstacles et portails des phases
  suivantes pourront être placés et vérifiés par le serveur sur le même tunnel
  (`UltraWarpTunnel`, sans code client).
- Toutes les fins passent par `UltraWarpSessions.end`, qui prévient le client
  (`S2CUltraWarpRideStopPacket`) sauf s'il est parti.
- Pendant le voyage, le joueur n'est pas blessé dans le monde (`ServerPlayerMixin`),
  sauf par ce qui passe l'invulnérabilité (`/kill`, le vide) ; sa mort termine le
  voyage.
- `test.maxDurationSeconds` termine le voyage tout seul (0 = sans fin).

### Le tunnel (`world/ultraspace/ride/UltraWarpTunnel.kt`)

- Des points tous les `segmentLength` blocs ; chacun tourne un peu par rapport au
  précédent vers une courbe tirée au hasard tous les `curveChangeSegments` points
  (lissée, pente bornée par `maxPitch`) ; une courbe de Catmull-Rom les relie.
- Le repère « haut » est le haut du monde rendu perpendiculaire au trajet : la pente
  restant modérée, il ne se retourne jamais.
- Le rayon gonfle et rétrécit (`radiusVariation`) et ondule autour du tunnel.
- Seule une fenêtre d'anneaux est construite à chaque image, de juste derrière la
  caméra jusqu'à `viewDistance` devant (au plus 85 % de la distance de rendu).

### Rendu côté client

| Fichier | Rôle |
|---|---|
| `client/ultraspace/UltraWarpRideClient.kt` | État du voyage, position du Pokémon et de la caméra à chaque image, début et fin |
| `client/ultraspace/UltraWarpTunnelRenderer.kt` | Parois tourbillonnantes, couche lumineuse, traînées, lumière au bout, extrémités fermées |
| `client/ultraspace/UltraWarpRiderRenderer.kt` | Solgaleo/Lunala (modèle RareCandy, pose `FLY` = `flywalk`) et le joueur assis (son skin) |
| `client/ultraspace/UltraWarpClientConfig.kt` | `ultra_warp_ride_client.json` |
| `client/screen/ultraspace/UltraWarpTravelScreen.kt` | Écran transparent : prend les touches (aucun clic n'atteint le monde), Échap pour quitter |
| `mixin/client/CameraMixin.java` | Caméra à l'ancre, orientation du voyage (avec le roulis), détachée (pas de main, Iris compris) |
| `mixin/client/GameRendererMixin.java` | Champ de vision du voyage, pas de balancement ni de main |
| `mixin/client/FogRendererMixin.java` | Brouillard sombre du tunnel |
| `mixin/client/LevelRendererMixin.java` | Dessine le voyage avant et après la passe RareCandy ; pas de pluie |
| `mixin/client/GuiMixin.java` | Pas de HUD pendant le voyage |
| `tools/ultra_warp_textures.py` | Textures `textures/ultra_space/` (parois, couche lumineuse, traînée, lueur) |

- **Caméra** : sur le trajet, `distance` blocs derrière le Pokémon et `height` au-dessus
  (proportionnels à la taille du modèle avec `autoScale`), elle regarde `lookAhead`
  blocs devant lui, suit les virages avec un lissage (`smoothing`), s'incline dans les
  courbes (`roll`, `maxRoll`), tremble légèrement (`shake`) ; le champ de vision
  s'élargit avec la vitesse (`fovBoost`).
- **Pokémon** : le modèle RareCandy du pack de ressources, dessiné comme une entité
  mais sans entité, dans sa pose de vol ; il oscille un peu et s'incline dans les
  virages. Sa hauteur (mesurée par RareCandy, `modelHeight`) place le joueur et la
  caméra.
- **Joueur** : son skin sur le modèle de joueur vanilla, en position assise.
- **Si l'écran du voyage est remplacé** (menu pause après avoir quitté la fenêtre), il
  revient dès qu'il n'y a plus d'écran. En solo, le menu pause arrête le temps du
  serveur intégré : le voyage se met en pause avec lui.

### Configuration

Serveur, `ultra_warp_ride.json` (relu au démarrage et par `/ultrawarp reload`, réécrit
avec toutes les options) :

| Option | Défaut | Effet |
|---|---|---|
| `maxSessions` | `6` | Voyages en même temps, 0 = désactivé |
| `speed` | `24` | Vitesse initiale, en blocs par seconde |
| `tunnel.segmentLength` | `16` | Distance entre deux points du trajet |
| `tunnel.curveIntensity` | `8` | Force des courbes (degrés de virage par point) |
| `tunnel.curveChangeSegments` | `6` | Points avant une nouvelle courbe |
| `tunnel.maxPitch` | `22` | Pente maximale (degrés) |
| `tunnel.radius` | `13` | Rayon du tunnel (9 en phase 2.1) |
| `tunnel.radiusVariation` | `0.2` | Variation du rayon (0.25 en phase 2.1) |
| `test.fixedSeed` | `0` | Graine fixe (0 = un tunnel différent à chaque voyage) |
| `test.maxDurationSeconds` | `0` | Fin automatique (0 = jamais) |

Client, `ultra_warp_ride_client.json` (relu à chaque début de voyage) :

- `camera` : voir la phase 2.2 ;
- `visual` : `viewDistance`, `sides`, `ringSpacing`, `textureLength`, `swirlSpeed`,
  `twist`, `streaks`, `brightness`, `glowLayer`, `endGlow`, `fadeStart`, `fadeEnd`,
  couleurs `wallColor`, `glowColor`, `streakColor`, `endColor`, `fogColor` ;
- `rider` : voir la phase 2.2 ;
- `dev` : `hideHud`, `debugInfo` (graine, distance, vitesse, FPS), `showTunnel`,
  `showRider`.

### Limites connues

- Les autres joueurs voient le voyageur immobile à l'autel.
- Avec un pack de shaders, le brouillard et l'éclairage du tunnel sont ceux du pack :
  le tunnel s'assombrit lui-même au loin, mais l'aspect exact est à vérifier en jeu.
- Pas de son propre au voyage.
- Multijoueur : chaque client ne dessine que son voyage ; pas encore testé avec
  plusieurs joueurs sur un serveur.

## Phase 2.2 : pilotage et caméra guidée

Le joueur pilote son Pokémon dans le tunnel (l'avancée reste automatique) ; la caméra
est guidée par le tunnel et n'en sort plus, avec Solgaleo comme avec Lunala.

### Pourquoi Lunala sortait du tunnel (phase 2.1)

Mesuré sur les modèles du pack `Generation 7.zip` (positions du GLB au repos × l'échelle
1,5 de leur `config.json`) :

| | Largeur | Hauteur | Longueur | Centre de la boîte |
|---|---|---|---|---|
| Lunala (ailes déployées) | 13,3 | 7,6 | 1,4 | 3,8 au-dessus des pieds |
| Solgaleo | 3,5 | 5,5 | 10,7 | 2,7 au-dessus des pieds |

Le tunnel de la phase 2.1 (rayon 9 ± 25 %, ondulations ± 9 %) descendait à 6,1.

1. **Le modèle était accroché par ses pieds sur l'axe** : tout le corps au-dessus de
   l'axe. Lunala montait à 7,6 et le bout de ses ailes à environ 8 blocs de l'axe ; dès
   que le tunnel se resserrait, ou dans l'inclinaison des virages, elle traversait la
   paroi. Solgaleo, compact, débordait rarement.
2. **La caméra grandissait avec le modèle, sans limite liée au tunnel** (`autoScale`) :
   pour Lunala, 7,6 blocs au-dessus de l'axe et 22,7 derrière, donc hors du tunnel dès
   que le rayon passait sous 7,6 ; à 22,7 blocs, la ligne de vue traversait aussi les
   parois dans les montées et descentes. Pour Solgaleo : 5,4 de haut, toujours dedans.
3. **La mesure utilisée était fausse** : la « taille » de RareCandy (`dimensions`) est
   la plus grande coordonnée positive de chaque axe au repos, pas l'étendue du modèle
   (Solgaleo y fait 4,0 de long au lieu de 10,7), et ignore les battements d'ailes.

### Ce qui corrige le problème

- **Le point piloté est le centre du modèle** (le milieu de sa boîte), plus ses pieds :
  `rider.<sun|moon>.centerX/Y/Z` du client, mesurés par `tools/ultra_warp_envelope.py`.
- **La place que prend le Pokémon est un disque** dans la section du tunnel :
  `riders.<sun|moon>.hitRadius` du serveur (Solgaleo 4,7, Lunala 7,9) = rayon qui
  contient largeur et hauteur, plus ce qu'ajoute sa longueur quand il s'incline de 15°
  au plus. Un disque reste le même quand le Pokémon s'incline sur le côté ou bat des
  ailes ; ses inclinaisons de pilotage sont bornées à 15° côté client.
- **Échelle d'ajustement** (`UltraWarpRideLimits.riderScale`) : le Pokémon prend au plus
  `maxFill` (65 %) du rayon le plus étroit du tunnel, sinon il est rendu plus petit,
  une fois pour tout le voyage. Avec le tunnel par défaut (rayon 13 ± 20 %, au plus
  étroit 9,5) : Solgaleo ×1, Lunala ×0,78.
- **Zone autorisée** : le centre du Pokémon reste à moins de
  `rayon des parois ici − rayon du Pokémon − wallMargin` de l'axe, où « rayon des
  parois ici » est le plus petit autour de l'anneau (`UltraWarpTunnel.coreRadius`).

### Pilotage (`client/ultraspace/UltraWarpSteering.kt`)

- Touches de déplacement de Minecraft (selon les options : ZQSD, WASD…) et flèches :
  avant = monter, arrière = descendre, gauche/droite = de côté, dans les axes du
  tunnel (son « droite » et son « haut » à cet endroit). `controls.invertVertical`
  inverse haut et bas.
- Vitesse jusqu'à `steering.maxSpeed` avec `acceleration` tant qu'une touche est
  tenue, retour à l'arrêt avec `deceleration`.
- Près du bord, la vitesse vers l'extérieur est freinée sur les derniers blocs ; si le
  tunnel se resserre, le Pokémon est ramené doucement à l'intérieur, jamais plus loin
  que 80 % de la marge (donc jamais dans la paroi). Dans les virages, les limites sont
  calculées dans le repère du tunnel à l'endroit du Pokémon.
- Inclinaison visuelle : sur le côté selon la vitesse latérale (`rider.maxBank`), nez
  vers le haut ou le bas selon la vitesse verticale (`maxPitch`), léger virage vers le
  mouvement (`maxYaw`), en plus de l'inclinaison des virages.
- **Bases du boost et de l'esquive** : Sprint (boost) et Saut (esquive) sont lus,
  envoyés au serveur et affichés en débogage, sans effet pour l'instant.

### Caméra guidée (`client/ultraspace/UltraWarpCamera.kt`)

- Sur le trajet du tunnel, `camera.distance` × la taille du Pokémon derrière lui
  (bornée par `minDistance` et `maxDistance`), `height` × sa taille au-dessus, et
  `follow` (50 %) de son déplacement en travers.
- Son écart à l'axe est limité au rayon des parois à son endroit moins `wallMargin`,
  avant et après le lissage : elle ne peut pas sortir du tunnel.
- Ligne de vue vérifiée en cinq points vers le Pokémon : si une paroi s'interpose (virage
  serré, montée, descente), la caméra se rapproche vite, puis recule lentement.
- Orientation toujours tirée du tunnel : elle vise `lookAhead` blocs devant le
  Pokémon, avec le « haut » du tunnel, inclinée dans les virages et un peu avec le
  Pokémon ; lissage (`smoothing`, `positionSmoothing`).

### Côté serveur (`UltraWarpRide.onState`)

- Le client envoie sa position en travers du tunnel et ses touches de boost et
  d'esquive `sync.statesPerSecond` fois par seconde (`C2SUltraWarpRideStatePacket`).
- Le serveur reconstruit le même tunnel (graine), calcule la zone autorisée à la
  distance du moment (son temps à lui) avec les mêmes limites ; une position au-delà
  (plus `sync.tolerance`) est ramenée dans la zone et comptée comme écart (visible dans
  `/ultrawarp sessions`). Une position par tick au plus.
- La dernière position acceptée est gardée dans la session pour les orbes et obstacles
  des phases suivantes.

### Configuration ajoutée

Serveur (`ultra_warp_ride.json`, version 2 : un fichier plus ancien resté aux défauts
de la phase 2.1 passe au rayon 13 et à la variation 0,2) :

| Option | Défaut | Effet |
|---|---|---|
| `steering.maxSpeed` | `10` | Vitesse latérale et verticale maximale (blocs/s) |
| `steering.acceleration` | `6` | Montée en vitesse (par seconde) |
| `steering.deceleration` | `4` | Ralentissement quand on relâche (par seconde) |
| `riders.sun.hitRadius` | `4.7` | Place de Solgaleo (mesurée) |
| `riders.moon.hitRadius` | `7.9` | Place de Lunala (mesurée) |
| `riders.maxFill` | `0.65` | Part maximale du rayon le plus étroit |
| `riders.wallMargin` | `0.6` | Marge entre le Pokémon et les parois |
| `sync.statesPerSecond` | `5` | Positions envoyées par seconde |
| `sync.tolerance` | `1` | Dépassement accepté avant de compter un écart |

Client (`ultra_warp_ride_client.json`, version 2 : les sections `camera` et `rider`
d'un fichier plus ancien repartent des défauts, leur sens a changé) :

- `camera` : `distance` et `height` (en tailles de Pokémon), `minDistance`,
  `maxDistance`, `follow`, `wallMargin`, `lookAhead`, `smoothing`, `positionSmoothing`,
  `roll`, `maxRoll`, `fov`, `fovBoost`, `shake` ;
- `rider` : `showPlayer`, `sun` et `moon` (`centerX/Y/Z`, `seatY`, `seatZ` : centre et
  selle en blocs depuis l'origine du modèle), `maxBank`, `maxPitch`, `maxYaw` (15° au
  plus), `bob` ;
- `controls` : `arrowKeys`, `invertVertical`.

### Mesurer un autre modèle

```bash
python tools/ultra_warp_envelope.py lunala.pk --jar fabric/build/libs/Generations-Core-fabric-1.2.8-1.8.4+irisfix.jar
```

Donne le centre (config client) et le `hitRadius` (config serveur) à partir du `.pk`.

## Phases 2.3 et 2.4 : le mini-jeu

Énergie, orbes, obstacles et collisions, distance en années-lumière, difficulté
progressive, événements spéciaux et défaite. Le serveur décide de tout (en solo, le
serveur intégré) ; chaque joueur a son voyage à lui.

### Pilotage vertical

La phase 2.2 traitait le Pokémon comme un disque (rayon 4,7 pour Solgaleo, 6,2 pour
Lunala une fois réduite) dans toutes les directions : vers le bas, il réservait son
envergure et l'inclinaison de sa longueur en plus de sa vraie hauteur. Désormais
(`UltraWarpRideLimits`) :

- sa forme de jeu est une **ellipse** (`riders.<sun|moon>.halfWidth`, `halfHeight`,
  `halfLength`, mesurés par `tools/ultra_warp_envelope.py`, plus `flapAllowance` en
  haut et en bas pour les ailes de Lunala) ;
- la place qu'il prend vers une direction dépend de cette direction et de son
  inclinaison réelle (côté, nez, virage) : incliné, l'aile qui descend compte ;
- marges séparées vers le **plancher**, les **côtés** et le **plafond**
  (`limits.floorMargin` 0,3, `sideMargin` 0,6, `ceilingMargin` 0,6), freinage sur
  `limits.softness` (0,75) ;
- le serveur valide avec l'ellipse sans inclinaison (le plus de place) plus la
  tolérance ; la caméra guidée ne change pas.

### Le parcours (`world/ultraspace/ride/UltraWarpCourse.kt`, commun)

- Le tunnel est découpé en tranches de `objects.slotLength` blocs. Chaque tranche tire
  ses objets de la graine et de son numéro, avec les chances du palier de difficulté à
  cet endroit : orbe orange, orbe bleue, débris, arc électrique, anomalie
  gravitationnelle. Le client et le serveur font exactement le même parcours, sans rien
  s'envoyer (angles en `StrictMath`).
- Placement dans la section du tunnel, toujours dans les parois : les orbes orange là
  où le Pokémon peut aller, les dangers là où ils peuvent le toucher. Les arcs vont de
  paroi à paroi, décalés du centre.
- **Jamais d'impasse** : après les dangers d'une tranche, il doit rester une place libre
  pour le Pokémon, atteignable depuis celle de la tranche précédente à la vitesse de
  pilotage ; sinon des dangers sont retirés.
- Rien sur les `objects.clearStart` premiers blocs.

### Collisions (`UltraWarpCollision`, décidées par le serveur)

- Volumes de jeu uniquement : le Pokémon est son ellipse × `riders.hitboxScale` (0,8,
  un peu indulgent) et `halfLength` le long du tunnel ; les objets sont des boules de
  leur rayon (orbes orange agrandies de `objects.pickupScale` pour les ramasser), les
  arcs des segments de leur épaisseur.
- Le client envoie sa position 20 fois par seconde avec sa distance le long du
  tunnel. Le serveur garde cette distance dans ce que la latence permet
  (`sync.latencySeconds`), la position dans les limites, puis teste le trajet entre la
  position précédente et celle-ci (rien n'est raté entre deux envois).
- Chaque objet ne compte qu'une fois ; après un dégât, rien d'autre ne fait mal pendant
  `energy.invulnerableSeconds`.
- Le client montre aussitôt l'effet (même parcours, mêmes tests) ; l'énergie affichée
  vient toujours du serveur.

### Énergie, distance, difficulté (`UltraWarpGameplay`, serveur)

- Énergie : `energy.start` sur `energy.max`, `-drainPerSecond` par seconde, `+orb`,
  `-spark`, `-debris`, `-arc`. À 0 : défaite.
- Distance : le serveur l'intègre à chaque tick avec la vitesse du moment et l'envoie 10
  fois par seconde (avec la vitesse et l'énergie) ; le client la suit en douceur.
  Années-lumière = blocs × `lightYearsPerBlock` (0,05).
- **Difficulté** : `difficulty.tiers`, une liste de paliers par années-lumière
  (vitesse ×, chance de chaque objet par tranche, force des anomalies, fréquence des
  événements), mélangés linéairement entre deux paliers (`UltraWarpDifficulty`) : rien
  ne saute. La vitesse change de `difficulty.speedAcceleration` blocs/s par seconde.
- **Enregistrement** : à la fin de chaque voyage, la distance va dans
  `UltraWarpRecords` (sauvegardé avec le monde : dernière, record, nombre de voyages,
  total) et dans le log ; le joueur voit sa distance et son record.

### Événements

| Événement | Effet | Réglage (`events.*`) |
|---|---|---|
| Tempête dimensionnelle | Tunnel rouge et magenta, éclairs, tremblement ; dangers × `strength` sur sa portion | `storm` |
| Courant de vitesse | Tube d'anneaux cyan ; voler dedans multiplie la vitesse par `strength` | `current` |
| Anomalie dimensionnelle | Dérive du pilotage (`strength` × la force du palier) et balancement de la caméra, toujours dans les limites | `anomaly` |
| Necrozma | Ultra Necrozma passe, dans une lumière aveuglante ; rien à combattre ni capturer (aucune entité) ; une fois par voyage | `necrozma` |

Chacun : `enabled`, `chancePerMinute` (× les événements du palier), `minSeconds`,
`maxSeconds`, `minLightYears`, `cooldownSeconds`, `strength`. Tempêtes et courants
sont des portions du tunnel annoncées `events.leadDistance` blocs à l'avance (plus
loin que ce que le client a construit), pour que les deux côtés construisent le même
parcours dessus.

### Défaite

1. Énergie à 0 : la session passe à l'étape `DEFEAT` (une seule fois) ; plus de
   collisions, la vitesse tombe.
2. Le client perd le contrôle : le Pokémon décroche en tournoyant, le tunnel se ternit,
   un flash blanc-violet puis le noir, « ÉNERGIE ÉPUISÉE ».
3. Après `defeat.seconds`, le serveur tue le joueur avec le type de dégâts
   `generations_core:ultra_warp` (« s'est perdu dans l'Ultra-Brèche ») : il traverse
   l'invulnérabilité, l'armure et le totem. Mort et réapparition normales de Minecraft
   (l'inventaire tombe à l'autel, sauf `keepInventory`) ; les Pokémon restent dans
   l'équipe.
4. La mort termine la session (`DEFEATED`), libère la place, enregistre la distance.
   Si un autre mod empêche la mort, la session se termine quand même 3 s plus tard.
5. Se déconnecter pendant la défaite n'y échappe pas : la mort est appliquée à la
   déconnexion. Échap ne permet pas de quitter ; `/ultrawarp test end` oui.

### HUD (`UltraWarpTravelScreen`)

Jauge d'énergie en haut à gauche (orange sous 50 %, rouge clignotante sous 25 %),
années-lumière en haut à droite, lueur rouge ou orange sur les bords (choc,
collecte), nom de l'événement en haut au milieu pendant 3 s, éclairs, éblouissement
de Necrozma, et la défaite.

### Modes de difficulté

La difficulté se choisit dans la config du serveur : `mode` est l'id du mode utilisé
par tous les voyages (`normal` par défaut), parmi la liste `modes`. Le menu de l'autel
l'affiche seulement. Si `mode` ne correspond à aucun mode, le premier de la liste est
pris (avertissement dans le log). Changer de mode : modifier `mode`, puis
`/ultrawarp reload` (les voyages suivants l'utilisent). Chaque mode multiplie les
paliers pour tout le voyage :

| Mode | Vitesse | Orbes orange | Dangers | Anomalies | Événements | Perte/s | Dégâts |
|---|---|---|---|---|---|---|---|
| `easy` (Facile) | ×0,85 | ×1,3 | ×0,6 | ×0,6 | ×0,6 | ×0,6 | ×0,6 |
| `normal` | ×1 | ×1 | ×1 | ×1 | ×1 | ×1 | ×1 |
| `hard` (Difficile) | ×1,15 | ×0,85 | ×1,4 | ×1,3 | ×1,3 | ×1,3 | ×1,3 |
| `extreme` (Extrême) | ×1,3 | ×0,7 | ×1,9 | ×1,6 | ×1,6 | ×1,6 | ×1,6 |

« Dangers » : orbes bleues, débris, arcs, anomalies gravitationnelles. Les chances
restent entre 0 et 100 %. On peut ajouter, modifier ou retirer des modes ; leur nom
vient des fichiers de langue (`generations_core.ultra_warp.mode.<id>`), sinon c'est
leur id. Le mode s'affiche dans le menu et sous les années-lumière ; les records sont
gardés par mode. `/ultrawarp test start sun <mode>` lance un voyage d'essai dans un
autre mode sans changer la config.

### Les chances dans le log

- Au démarrage du serveur et à chaque `/ultrawarp reload` : pour chaque mode actif,
  ses multiplicateurs, l'énergie (perte, gains, dégâts avec le mode), chaque palier
  (vitesse ; chance de chaque objet par tranche et nombre moyen pour 100 blocs) et
  chaque événement (fréquence par minute au premier et au dernier palier, durée,
  distance minimale, pause, force).
- Au début de chaque voyage : la même description pour le mode choisi, avec la graine.
- À chaque palier atteint : ses valeurs, mode compris.
- À chaque événement : sa distance, sa durée, sa force et sa fréquence à ce moment.
- À la fin : la distance, le mode, les orbes prises, les chocs, les écarts.

Avec `dev.debugInfo` côté client, l'écran affiche aussi les chances du moment.

### Commandes de test

| Commande | Effet |
|---|---|
| `/ultrawarp test start sun\|moon [mode] [joueurs]` | Lance un voyage dans ce mode |
| `/ultrawarp test end` | Quitte sans mourir, même pendant la défaite |
| `/ultrawarp test energy <valeur>` | Fixe l'énergie (0 : défaite) |
| `/ultrawarp test distance <AL>` | Saute à ces années-lumière (leur difficulté) |
| `/ultrawarp test event storm\|current\|anomaly\|necrozma` | Lance l'événement tout de suite |

Option `test.invincible` : pas de perte d'énergie.

### Fichiers ajoutés

`ride/UltraWarpCourse.kt`, `ride/UltraWarpCollision.kt`, `ride/UltraWarpDifficulty.kt`,
`ride/UltraWarpRideState.kt`, `UltraWarpGameplay.kt`, `UltraWarpRecords.kt` (serveur et
commun) ; `client/ultraspace/UltraWarpObjectRenderer.kt`, `UltraWarpSounds.kt` ;
paquets `S2CUltraWarpRideUpdatePacket`, `S2CUltraWarpRideEventPacket` ;
`data/generations_core/damage_type/ultra_warp.json` et ses tags ; textures `ring`,
`bolt`, `crystal`, `vortex`.

### Pas encore fait (phases suivantes)

Portails de destination, Ultra-Mondes, spawns d'arrivée, chances de shiny, Ultra-Chimères,
fissures. Le boost et l'esquive ont leurs touches et leur transport, pas encore d'effet.

## Phase 3 : portails dimensionnels

Des portails colorés apparaissent pendant le voyage ; s'y laisser aspirer termine le
voyage avec succès (résultat `SUCCESS`). Leur **couleur** (la destination) et leur
**niveau de rareté** (leur apparence) sont tirés séparément : toutes les combinaisons
existent. Les Ultra-Mondes viennent à la phase 4 : pour l'instant, un écran montre la
destination, le niveau et la distance, et le joueur est de retour dans l'Overworld.

### Le serveur décide (`UltraWarpPortals`)

- Il place les portails `portals.leadDistance` blocs devant (plus loin que ce que le
  client a affiché), à partir de `firstLightYears`, un tous les `minSpacing` à
  `maxSpacing` blocs, `maxVisible` au plus devant le joueur.
- Position dans la section du tunnel, dans le repère local (donc dans les virages),
  au centre ou sur un côté, dans les limites du Pokémon ; il reste toujours un passage
  libre à côté de toutes les ouvertures (aucun portail n'est obligatoire). Rien du
  parcours (orbes, dangers) sur les `clearBefore` blocs avant un portail, des deux
  côtés.
- Destination tirée selon les poids `destinations`, niveau tiré dans la table
  `rarity` des années-lumière du portail : le client reçoit ce tirage
  (`S2CUltraWarpPortalPacket`) et dessine exactement cela ; rien n'est décidé par le
  client.
- **Convergence** : avec la chance `convergenceChance`, `convergenceMin` à
  `convergenceMax` portails à la même distance, leurs ouvertures écartées ; si deux
  étaient traversées à la fois, celle dont le centre était le plus proche l'emporte,
  puis le plus petit identifiant. Une seule destination est retenue.
- Chaque portail est écrit dans le log : destination, niveau, distance, table de
  rareté et tirage.

### Activation : traverser l'ouverture

La première version attirait le Pokémon (zone de 6 blocs autour du centre et 50 devant,
voyage ralenti, attraction de plus en plus forte) : on était presque toujours aspiré.
Désormais, comme pour une orbe, mais volontairement :

- un portail **n'attire pas et ne ralentit rien** : la trajectoire ne change pas près
  de lui ;
- seul compte le **centre du Pokémon** (le milieu de son corps, le point piloté), pas
  sa grande forme : les ailes de Lunala qui touchent les anneaux ne font rien ;
- il doit **traverser l'ouverture** : entre deux positions acceptées par le serveur, le
  plan du portail est franchi, et à ce moment le centre est à moins de
  `triggerFraction` (0,8) du rayon de l'ouverture dessinée (le tourbillon, pas les
  anneaux ni la lumière autour) ;
- le test se fait sur le trajet entre deux positions (20 par seconde), donc aucune
  traversée n'est manquée, même à grande vitesse ;
- frôler le portail ou passer dans ses anneaux ne déclenche rien ;
- c'est le serveur qui décide (`UltraWarpPortals.cross`, appelé avec chaque position
  acceptée) ; puis la traversée cinématique existante, et `SUCCESS`.

### La traversée et la fin

- Étape `CAPTURE` : plus d'énergie, de collisions ni de pilotage ; Échap ne quitte pas
  (la commande `test end` oui). Le Pokémon est attiré au centre et y entre, la caméra
  suit, le champ de vision s'élargit, le portail grandit jusqu'à remplir l'écran, le
  tunnel prend sa couleur, puis le blanc ; durée `cinematicSeconds`.
- Le serveur termine alors la session avec `SUCCESS` (une seule fois), libère la place
  et enregistre le résultat ; le client affiche l'écran de fin.

### Résultat enregistré (`UltraWarpOutcome`)

À **chaque** fin de voyage, le serveur écrit, avec ses seules données : l'identifiant
du voyage, le joueur, le Pokémon (espèce, shiny), la difficulté, les années-lumière, le
résultat (`SUCCESS`, `DEFEATED`, `CANCELLED`, `FINISHED`, `DISCONNECTED`…), et pour un
succès le portail (identifiant, destination, couleur, niveau). Les 20 derniers de
chaque joueur sont sauvegardés avec le monde (`UltraWarpRecords`) et chacun est écrit
dans le log. C'est ce que la phase 4 utilisera (arrivée dans la destination).

### Rendu (`UltraWarpPortalRenderer`)

Tout en lumière additive (pas de problème de profondeur avec les parois), debout en
travers du tunnel à sa place, dans la couleur de la destination :

| Niveau | Forme |
|---|---|
| 1 Simple | Ouverture en tourbillon (trois couches l'une derrière l'autre : la profondeur), cœur lumineux, bord fin, étincelles aspirées |
| 2 Un anneau | + un anneau 3D (un tore maillé) incliné qui tourne ; plus d'étincelles |
| 3 Deux anneaux | + un deuxième tore tournant en sens inverse, autre inclinaison ; arcs électriques |
| 4 Exceptionnel | Tores arc-en-ciel, couronne de pétales d'énergie irisés en deux couches tournant en sens opposés, halo multicolore qui pulse, beaucoup d'étincelles |

`portals.levels` règle pour chaque niveau : nombre d'anneaux (0 à 3), de pétales,
d'étincelles, vitesse des anneaux, éclat, taille.

### Préparation des rencontres (`encounters`)

Trois tables **indépendantes**, par années-lumière et niveau de portail (1 à 4) :
chance de rencontrer une Ultra-Chimère, chance shiny d'un Pokémon ordinaire, chance
shiny d'une Ultra-Chimère. Elles sont chargées et vérifiées mais pas encore
utilisées : la phase des rencontres les lira (une chance shiny de 90 % pour un Pokémon
ordinaire ne dit rien de celle d'une Ultra-Chimère).

### Configuration (`portals`)

| Option | Défaut | Effet |
|---|---|---|
| `enabled` | `true` | Portails actifs |
| `firstLightYears` | `15` | Distance du premier portail |
| `minSpacing`, `maxSpacing` | `300`, `600` | Blocs entre deux portails (ou groupes) |
| `maxVisible` | `3` | Portails devant le joueur au plus |
| `leadDistance` | `320` | Où le serveur les place devant |
| `viewDistance` | `300` | D'où le client les voit (fondu) |
| `radius` | `3.2` | Taille de l'ouverture |
| `triggerFraction` | `0.8` | Part du rayon de l'ouverture que le centre du Pokémon doit traverser |
| `clearBefore` | `24` | Blocs sans objets du parcours avant un portail |
| `cinematicSeconds` | `3` | Durée de la traversée |
| `convergenceChance`, `convergenceMin`, `convergenceMax` | `0.15`, `2`, `3` | Convergences |
| `destinations` | 7 | `id`, `color`, `weight`, `ultraBeast`, `dimension` (pour la phase 4) |
| `rarity` | 6 paliers | `fromLightYears` et 4 poids ; vérifiés (jamais négatifs, jamais tous à 0, le niveau 1 garde 1 % au moins) |
| `levels` | 4 | Apparence de chaque niveau |

Avec `lightYearsPerBlock` à 0,05, 1 000 années-lumière font 20 000 blocs : les tables
au-delà de 1 000 AL se testent avec `/ultrawarp test distance`.

### Commandes de test

| Commande | Effet |
|---|---|
| `/ultrawarp test portal <destination\|random> <1-4> [nombre]` | Portails de ce niveau, bientôt devant |
| `/ultrawarp test convergence` | Plusieurs portails ensemble |
| `/ultrawarp test distance <AL>` | Saute aux années-lumière voulues (autre table de rareté) |

## Phase 4.1 : les Ultra-Mondes et la téléportation

Traverser un portail mène maintenant dans une **vraie dimension Minecraft**, une par
destination. Les sept mondes sont persistants et partagés par tous les joueurs (jamais
un monde par voyage). Leur terrain est provisoire : les paysages viendront ensuite.

### Les dimensions (données du mod)

Écrites par `tools/ultra_worlds.py` dans `common/src/main/resources/data/generations_core` :

| Fichier | Contenu |
|---|---|
| `dimension/<id>.json` | Générateur `minecraft:noise`, un biome fixe, nos `noise_settings` |
| `dimension_type/<id>.json` | Ciel, heure (fixe pour certains), hauteur -64 à 320, lits utilisables, pas de raids |
| `worldgen/biome/<id>.json` | Couleurs, pas de pluie (donc pas de météo), aucun mob, aucune feature |
| `worldgen/noise_settings/<id>.json` | Le relief de l'Overworld 1.21.1 (son routeur de bruit, copié tel quel) et une surface propre au monde |
| `tags/block/ultra_arrival_built.json` | Blocs de construction de joueurs (planches, verre, laine, portes, lits…) |
| `tags/block/ultra_arrival_unsafe.json` | Blocs dangereux (lave, magma, cactus, feu, neige poudreuse…) |

| Monde | id | Surface | Ciel |
|---|---|---|---|
| Ultra-Jungle | `ultra_jungle` | herbe vert vif / terre | jour normal |
| Ultra-Désert | `ultra_desert` | `ultra_sand` / `ultra_sandstone`, pas de mer | midi fixe |
| Ultra-Centrale | `ultra_central` | pierre lisse | nuit fixe, brouillard jaune |
| Ultra-Cratère | `ultra_crater` | tuf | jour |
| Ultra-Forêt | `ultra_forest` | calcite | jour |
| Ultra-Abysse | `ultra_abyss` | prismarine sombre | nuit fixe |
| Ultra-Gratte-Ciel | `ultra_skyscraper` | blackstone poli / deepslate | nuit fixe, brouillard rouge sombre |

- Le routeur de bruit vanilla vient du générateur de données officiel de Minecraft,
  lancé en local sur le jar serveur du cache Loom :
  `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --server --output gen`,
  puis `python tools/ultra_worlds.py gen/data/minecraft`.
- **Une graine par monde** (`ChunkMapMixin`) : avec le routeur de l'Overworld et la graine
  de la sauvegarde, les sept mondes auraient tous le relief exact de l'Overworld. Leur
  générateur reçoit la graine de la sauvegarde mélangée à l'id du monde
  (`UltraWorlds.terrainSeed`) : relief différent, toujours le même pour une sauvegarde.
- **Pas d'avertissement « paramètres expérimentaux »** (`WorldDimensionsMixin`) :
  Minecraft le montre dès qu'une sauvegarde a plus de trois dimensions ; nos sept mondes
  sont retirés de ce compte, toute autre dimension ajoutée le déclenche toujours.
- Vérifié hors jeu : les fichiers ont été chargés avec le chargeur de registres de
  Minecraft lui-même (`RegistryDataLoader`, comme à l'ouverture d'un monde) et le relief
  échantillonné : de 29 à 66 % de terres selon le monde, sol entre -3 et 213.

**Les identifiants ne changent jamais** (`UltraWorlds.IDS`) : les sauvegardes les
nomment (`dimensions/generations_core/<id>`). Minecraft prend toujours la version du mod
plutôt que la copie du `level.dat` : une sauvegarde existante reçoit les mondes, et un
changement de terrain s'appliquera aux nouveaux chunks seulement (raccords) ; pour les
paysages définitifs, il faudra effacer ces dossiers du monde de test, jeu fermé. Si le
mod est retiré, Fabric ignore les dimensions inconnues du `level.dat` et le monde
s'ouvre quand même.

### Portail → monde

- Chaque destination mène au monde de `portals.destinations[].dimension`
  (`ultra_warp_ride.json`), qui doit nommer l'un des sept mondes ; sinon le monde de
  même id que la destination (avertissement dans le log). Le niveau de rareté ne
  change rien à la destination.
- Un monde fermé (`ultra_worlds.json`) ou absent : aucun portail n'y mène
  (`hidePortalsOfDisabledWorlds`), sauf ceux de la commande de test ; si on en traverse
  un quand même, le joueur reste à l'autel, le résultat est `SUCCESS` avec l'arrivée
  `UNAVAILABLE`.

### La téléportation (`UltraWarpTransfers`, serveur)

Un transfert par joueur, chacun avec ses tickets de chunks, à part des sessions :

1. **Capture** : le monde de la destination commence à charger (pendant les 3 s de
   la cinématique), autour du point d'arrivée choisi.
2. **Fin de la cinématique** : la session se termine en `SUCCESS` (place libérée), le
   résultat est enregistré avec l'arrivée `PENDING`. Le client quitte le tunnel (caméra
   normale, plus de monture) et garde un **écran blanc** (`UltraWarpTransferScreen`), sans
   touches. Le joueur, toujours à l'autel, reste protégé.
3. Les chunks chargés (`getChunkNow` sur tout le carré du ticket), le serveur cherche
   une place sûre (`UltraArrivalSearch`) et la revérifie juste avant.
4. **Arrivée** : `stopRiding()`, Pokémon sortis rappelés (Cobblemon le fait aussi au
   changement de dimension), point de départ enregistré, `teleportTo` sur le haut exact
   du bloc, vitesse nulle, chute à 0 ; vérifié ensuite (un autre mod peut refuser).
5. **Protection** : `protection.seconds` (5 s) sans dégâts (sauf `/kill`), la chute est
   remise à 0 à chaque tick.
6. Le paquet `S2CUltraWarpTransferPacket` (ARRIVED), envoyé après le changement de
   dimension : le client affiche l'écran de fin une fois l'écran de chargement de
   Minecraft fermé.

Le joueur ne bouge qu'une fois la place chargée et vérifiée : il ne peut pas tomber
pendant le chargement. Le Pokémon reste dans l'équipe : rien n'est retiré au joueur.

### Les points d'arrivée (`UltraArrivalPoints`, sauvegardés avec chaque monde)

- `arrival.points` (8) points en spirale jusqu'à `arrival.spread` (1 200) blocs, placés
  depuis la graine, le monde et le numéro du point : reproductibles.
- Près de la place idéale, le **générateur du monde** dit où il y a de la terre (pas
  d'eau au-dessus, au-dessus de la mer, pas trop pentu), sans charger de chunk :
  l'ancre du point, gardée.
- Le point d'un voyage est tiré à partir de l'id du voyage, puis décalé de `scatter`
  (6) blocs : les joueurs ne tombent pas tous sur le même bloc. La place sûre trouvée
  est gardée, avec le nombre d'arrivées. Un point refusé attend `retryMinutes` (10).
- **Règles** (`UltraArrivalSearch.rules`, de la moins chère à la plus chère) : pas de
  vide, sol plein et solide sans fluide, deux blocs libres, ciel visible, pas de
  précipice de plus de `maxDrop` (3) blocs autour, pas de lave/feu/magma/cactus à moins
  de `hazardRadius`, pas d'autre joueur à moins de `playerRadius`, **pas de construction**
  (bloc-entité ou bloc de `#generations_core:ultra_arrival_built`) à moins de
  `buildRadius` (8). Les phases suivantes ajouteront leurs règles à cette liste
  (protection autour des points : `protectionRadius` est déjà enregistré, mods de claims).
- **Secours**, dans l'ordre : les points, `freshTries` (3) places neuves, puis selon
  `fallback` une plateforme 5×5 (posée seulement dans l'air, un fluide ou une plante,
  jamais à la place d'un bloc solide) ou le joueur reste (`return`).

### Déconnexion, arrêt, mort

- Déconnexion ou arrêt du serveur pendant la cinématique ou l'attente : le résultat est
  `SUCCESS`, l'arrivée `PENDING` gardée dans `UltraWarpTravellers` (sauvegarde). À la
  connexion suivante (le joueur est toujours à l'autel), le transfert reprend : écran
  blanc, chargement, arrivée. Une seule fois : l'id du voyage (les résultats sont
  remplacés, pas ajoutés).
- Mort pendant l'attente (seulement `/kill`) : pas d'arrivée (`FAILED`).
- `/ultrawarp test end` pendant la cinématique : pas d'arrivée.

### Données gardées pour les rencontres

`UltraWarpOutcome` gagne : `distance` (blocs), `world`, `arrival` (`PENDING`, `ARRIVED`,
`PLATFORM`, `UNAVAILABLE`, `FAILED`), `arrivalPoint` et la position. Les anciens
résultats se lisent toujours. `UltraWarpTravellers` garde pour chaque joueur : `home`
(d'où il est parti, pas remplacé d'un Ultra-Monde à l'autre), `pending`, et `stay` : le
voyage qui l'a amené là (destination, niveau du portail, années-lumière), que la phase
des rencontres lira.

### Retour provisoire

`/ultrawarp test return` : le même transfert sûr vers `home` (la position exacte
d'abord, même sous un toit), sinon le point de réapparition, sinon le spawn de
l'Overworld (plateforme en dernier recours). Le retour définitif passera par la roue
d'interaction de Solgaleo ou Lunala.

### Configuration (`ultra_worlds.json`)

| Option | Défaut | Effet |
|---|---|---|
| `worlds.<id>.enabled` | `true` | Monde ouvert aux arrivées |
| `hidePortalsOfDisabledWorlds` | `true` | Aucun portail vers un monde fermé |
| `arrival.points`, `spread`, `minRadius` | `8`, `1200`, `64` | Points d'arrivée et leur étendue |
| `arrival.scatter` | `6` | Décalage de chaque joueur autour du point |
| `arrival.landSearch` | `256` | Rayon de recherche de terre (générateur, sans chunk) |
| `arrival.searchRadius` | `24` | Rayon de recherche d'une place sûre (chunks chargés) |
| `arrival.maxDrop`, `edgeRadius` | `3`, `2` | Précipices |
| `arrival.hazardRadius` | `2` | Distance aux blocs dangereux |
| `arrival.buildRadius` | `8` | Distance aux constructions (0 = pas vérifié) |
| `arrival.requireSky` | `true` | Ciel visible |
| `arrival.playerRadius` | `4` | Distance aux autres joueurs |
| `arrival.loadTimeoutSeconds` | `20` | Attente du chargement d'une place |
| `arrival.retryMinutes` | `10` | Attente d'un point refusé |
| `arrival.freshTries` | `3` | Places neuves après les points |
| `arrival.fallback`, `platformBlock` | `platform`, `minecraft:stone` | Secours |
| `protection.seconds` | `5` | Protection à l'arrivée |
| `returnHome.searchRadius` | `8` | Recherche autour du point de départ |
| `test.logSearch` | `true` | Chaque recherche dans le log |

### Commandes de test

| Commande | Effet |
|---|---|
| `/ultrawarp test arrive <monde> [joueurs]` | Le même transfert sûr, sans voyage |
| `/ultrawarp test return [joueurs]` | Retour au point de départ |
| `/ultrawarp worlds` | Les sept mondes : ouverts, chargés, joueurs, points |
| `/ultrawarp worlds points <monde>` | Les points d'arrivée (idéal, ancre, place sûre, arrivées, refus) |
| `/ultrawarp worlds prepare <monde>` | Cherche tous les points maintenant (aussi depuis la console) |

### Pas encore fait

Paysages, bâtiments, ressources, météo, spawns, Ultra-Chimères, Pokémon d'arrivée,
fissures, retour par la roue d'interaction. Le multijoueur n'a pas été testé avec deux
clients ; NeoForge n'est ni compilé ni vérifié.

## Phase 4.2 : génération des sept Ultra-Mondes (35 biomes)

Les terrains provisoires de la 4.1 sont remplacés par une vraie génération procédurale,
faite par Minecraft lui-même chunk par chunk, avec ses outils de 1.21.1 : biomes,
noise settings, density functions, surface rules, configured et placed features.
Les identifiants des sept dimensions ne changent pas.

### Comment c'est construit (`tools/ultra_worlds.py` + `tools/ultra_worlds_defs.py`)

- **Biomes** : une source `multi_noise` par monde, 5 biomes placés par le climat
  (température, humidité, continentalité, érosion, bizarrerie). Chaque biome a une
  « boîte » de climat ; la première qui correspond l'emporte, le dernier biome prend le
  reste. Les frontières suivent les courbes de bruit (pas de bords droits). Taille des
  régions : `region_scale` du monde (1,6 : régions 1,6 fois plus grandes que l'Overworld).
- **Relief** : le routeur de l'Overworld (de l'Abysse : celui des grottes vanilla) avec
  les fonctions du monde (`density_function/<monde>/offset, depth, factor, jaggedness,
  sloped_cheese, temperature, vegetation`). Le relief propre d'un biome (dunes, canyons,
  vallées, cratères géants, crêtes, aplanissement) est multiplié par un **masque calculé
  sur le même climat** que sa boîte : il est exactement là où est le biome, et s'efface
  doucement à ses bords.
- **Surfaces** : une règle par biome (`minecraft:biome`), avec pentes raides, plaques par
  bruit et strates par altitude (canyon d'ivoire).
- **Features** : types vanilla configurés avec nos blocs (arbres à tronc géant, palétuviers,
  cerisiers, chênes noirs, fungus géants, champignons géants, colonnes de basalte, deltas
  de lave, géodes, lacs, sources, plaques, filons, rochers…) et trois types du mod, là où
  le vanilla n'a rien : `generations_core:crater`, `crystal_spike`, `twisted_column`, plus
  la density function `generations_core:craters` (cratères géants : Minecraft n'a pas de
  bruit cellulaire). Code : `world/ultraspace/worlds/gen/UltraWorldgen.kt`.
- L'ordre des features d'un monde est un ordre total par étape : pas de crash
  « Feature order cycle ».
- Les 7 biomes uniques de la 4.1 restent enregistrés (non générés) pour les chunks déjà
  sauvegardés.

### Vérifications hors jeu (sans serveur)

Un petit programme charge les données avec le chargeur de registres de Minecraft
(`RegistryDataLoader`, comme à l'ouverture d'un monde), appelle `ChunkGenerator.validate()`
(ordre des features) et dessine une carte de chaque monde (biomes, relief, eau). Parts
mesurées sur 24 000 × 24 000 blocs :

| Monde | Biomes (part) |
|---|---|
| Ultra-Jungle | jungle géante 48 %, montagneuse 17 %, ruines naturelles 15 %, marécageuse 14 %, bioluminescente 6 % |
| Ultra-Désert | désert blanc 54 %, canyon d'ivoire 19 %, cristallin 12 %, oasis 8 %, désert de verre 6 % |
| Ultra-Centrale | plaines conductrices 41 %, vallée magnétique 20 %, forêt de câbles 18 %, montagnes électriques 16 %, champs de cristaux 5 % |
| Ultra-Cratère | plaines aux cratères 34 %, grand cratère 19 %, forêt de flèches 17 %, plateaux jaunes 16 %, champs de fumerolles 14 % |
| Ultra-Forêt | forêt émeraude brumeuse 34 %, bosquet aux fleurs d'or 20 %, jardin zen 19 %, forêt origami 18 %, vallée des feuilles tranchantes 9 % |
| Ultra-Abysse | cavernes cristallines 35 %, forêt fongique 27 %, abîmes rocheux 15 %, lacs souterrains 14 %, grottes luminescentes 9 % |
| Ultra-Gratte-Ciel | quartier résidentiel 27 %, centre-ville envahi 26 %, ruines effondrées 19 %, parc urbain 14 %, zone industrielle 13 % |

Ce qui ne se voit qu'en jeu : les surfaces, les features (arbres, cristaux…), les
raccords entre chunks.

### Blocs

Vanilla et Generations ensemble, choisis par aspect et rôle ; aucun nouveau bloc. Les
feuillages sont vanilla, teintés par la couleur `foliage_color` du biome (le mod n'a pas
de feuilles). Correctif : `crystal_block`, `crystal_light`, `ultra_crystal`, `silicon_block`,
`sapphire_*`, `ruby_*` (propriétés du fer, « bon outil requis ») ajoutés à
`mineable/pickaxe` : avant, la pioche ne les récupérait pas.

### Ultra-Abysse

Monde souterrain : plafond de bedrock vers y = 128, grandes cavernes reliées, pas de
lumière du ciel. L'eau n'existe que dans les lacs souterrains (ailleurs, le bas est
rempli), avec des îlots ; les abîmes rocheux ont des puits verticaux.

### Refonte d'après les images (Ultra-Cratère, Ultra-Forêt, Ultra-Gratte-Ciel)

Les biomes de ces trois mondes ont été refaits pour ressembler aux images de référence
(USUM pour le Cratère et la Forêt, une ville envahie par la végétation pour le
Gratte-Ciel). Aucun nouveau bloc ; trois types de features du mod en plus.

- **Ultra-Cratère** (ciel orangé, herbe vanilla teintée en jaune par le biome) : plaines
  aux cratères (petits cratères au fond de mousse, sentiers de mousse sinueux : bande
  étroite du bruit `minecraft:gravel`), grand cratère (fond de terre brune sous
  y = 48 avec des braises de magma, anneau de mousse jusqu'à y = 54), forêt de flèches
  (cônes de terre cuite rouge-brun), champs de fumerolles, plateaux jaunes (falaises en
  strates de terre cuite). Arches noires (`twisted_column` court, arche vers le sol).
- **Ultra-Forêt** (ciel et brouillard verts) : jardin zen, forêt émeraude brumeuse (troncs
  sombres, feuilles de sapin qui gardent leur vert), bosquet aux fleurs d'or (arbres de
  type cerisier, feuilles de chêne teintées en jaune), forêt origami et vallée des feuilles
  tranchantes gardées. Rochers pointus moussus partout.
- **Ultra-Gratte-Ciel** (cycle jour/nuit normal, brume grise) : centre-ville envahi,
  quartier résidentiel, parc urbain, ruines effondrées, zone industrielle. La ville est
  posée chunk par chunk par `generations_core:city_block`.

Leurs parts mesurées sont dans le tableau plus haut.

**Features** (`world/ultraspace/worlds/gen/UltraWorldgen.kt`, toutes limitées à leur
région de génération) :

- `zen_garden` : cercle de sable blanc (poudre de béton) ratissé en anneaux de calcite
  autour de 1 à 3 rochers pointus moussus, bord de mousse ; seulement sur sol à peu près
  plat, les troncs restent.
- `steam_vent` : feu de camp « de signal » (sur une botte de foin, la fumée monte haut)
  enfoncé dans le sol dans un anneau de roche, tache de terre autour. La fumée ne
  traverse pas les blocs : toujours à l'air libre.
- `city_block` (un par chunk, placé au coin du chunk sans `in_square`) : rues de 3 blocs
  sur les bords nord et ouest de chaque chunk (pointillé au milieu, trottoir), donc un
  quadrillage continu d'un biome à l'autre ; sur l'îlot de 11 × 11 : un immeuble (palette
  murs / angles / fenêtres / planchers, fenêtres en grille, planchers tous les 4 blocs,
  vitrines et entrée, toit-jardin avec arbres ou cheminée qui fume, lianes, vitres
  brisées, trous, sommet effondré dans les ruines) ou un jardin (herbe, bassin, arbres).
  Ignore les chunks sous l'eau.

**Arrivées** :

- Nouvelle règle « sur le terrain » : le point doit être au plus 3 blocs au-dessus de la
  hauteur du terrain donnée par le générateur (`getBaseHeight`), donc jamais sur un toit,
  une flèche ou une colonne (pas sous un plafond : l'Abysse garde sa règle).
- Tag par monde `#generations_core:ultra_arrival_built/<monde>` quand il existe :
  l'Ultra-Gratte-Ciel ne compte pas verre, briques, briques de pierre et pierre taillée
  comme constructions de joueur (la ville en est faite) ; lits, portes, coffres,
  panneaux, torches... comptent toujours.
- Les feux de camp ne comptent plus comme « construction » (block entity) : fumerolles,
  cheminées et feux de rue en ont. Ils restent dangereux (`ultra_arrival_unsafe`).

**Surfaces** : les plaques par bruit sont maintenant essayées du seuil le plus haut au
plus bas (dans l'autre ordre, la première prenait tout et les suivantes n'apparaissaient
jamais) ; des bandes (min, max) servent aux sentiers.

**Anciens biomes** de ces trois mondes : restent enregistrés (sans features, non
générés, hors tags) pour les chunks déjà sauvegardés ; leur nom s'affiche avec
« (ancien) ».

### Arrivées

- Tag `#generations_core:ultra_space/no_arrival` : lacs souterrains ; refusés par la
  recherche et par la prédiction des ancres.
- Ultra-Abysse : la recherche prend le sol de la plus haute salle de la colonne (au moins
  4 blocs d'air, au-dessus des lacs), demande une vraie salle autour ; la prédiction des
  ancres lit la colonne du générateur ; le secours pose une plateforme dans une salle, ou
  creuse une petite pièce.
- Précipices : vérifiés en descendant chaque colonne voisine (vaut aussi sous un plafond).

### Tags pour les spawns et la météo

`#generations_core:ultra_space` (les 35), `#generations_core:ultra_space/<monde>` (5 par
monde), thèmes `#generations_core:ultra_space/is_<thème>` : `forested`, `watery`, `arid`,
`mountainous`, `crystalline`, `electric`, `volcanic`, `barren`, `luminous`, `underground`,
`industrial`, `urban`, `rare`, et `#generations_core:ultra_space/has_ruins` (prêt pour les bâtiments de la 4.3). Nos biomes ne sont
dans aucun tag vanilla ou Cobblemon : pas de spawns ni de features d'autres mods.

### Test

| Commande | Effet |
|---|---|
| `/ultrawarp test biome <biome> [joueurs]` | Va dans ce biome (le plus proche, trouvé comme `/locate biome`), par le transfert sûr |
| `/locate biome generations_core:<monde>/<biome>` | Vanilla, dans la dimension |
| F3 | Le biome sous les pieds |

### Sauvegardes

Les chunks déjà générés avec le terrain provisoire de la 4.1 **ne sont pas remplacés** :
raccords avec les nouveaux. Pour repartir propre, effacer les dossiers
`dimensions/generations_core/*` du monde, jeu fermé (jamais automatiquement).

## Phase 4.3.2 (lot 1) : ressources des Ultra-Mondes et décors sans farming

Avant cette phase, aucun minerai ne se générait dans les Ultra-Mondes : les minerais de
Generations et les pierres de Cobblemon ne visent que l'Overworld (sélecteurs de biomes),
et nos biomes ne sont dans aucun de leurs tags.

### Ressources (`config/generations/core/ultra_space/ultra_resources.json`)

Chaque monde a une feature `generations_core:<monde>/resources`
(`generations_core:ultra_resources`, étape des minerais de ses 5 biomes, posée au coin du
chunk sans modificateur). À chaque chunk, elle lit la config (`UltraResourcesConfig`) et
pose les veines du monde avec les générateurs de veines de Minecraft (`minecraft:ore`,
`minecraft:scattered_ore` pour l'Ultrite). Lue au démarrage du serveur et par
`/ultrawarp reload` : un changement vaut pour les chunks générés ensuite, jamais pour
ceux qui existent. Rien ne se régénère. Tirage déterministe (le hasard du chunk, donc de
la seed).

Racine : `enabled`, `maxBiomeFactor` (plafond du facteur d'un biome, 1,5), `rock` (par
monde : ce que le minerai « pierre » remplace), `deepRock` (ce que le minerai
« deepslate » remplace : deepslate et tuf), `resources`.

| Option d'une ressource | Rôle |
|---|---|
| `enabled` | Activer ou non |
| `block`, `deepBlock` | Le minerai dans la roche du monde, et dans le deepslate / le tuf (vide : le même) |
| `size` | Taille de la veine |
| `scattered` | Blocs isolés (comme les débris antiques) au lieu d'une veine |
| `airExposureDiscard` | Chance qu'un bloc touchant l'air soit retiré (1 : jamais visible dans une grotte) |
| `tries`, `chance` | Veines tentées par chunk (une fraction est une chance), chance que le chunk y ait droit |
| `minY`, `maxY`, `shape` | Bande d'altitude, `uniform` ou `triangle` (le plus au milieu) |
| `worlds` | Facteur par monde ; monde absent ou à 0 : aucun |
| `altitudes` | Une autre bande dans certains mondes |
| `biomes` | Facteur d'un biome (`"ultra_crater/steam_fields": 1.25`) à la place de 1 : un seul par biome, jamais cumulé, plafonné |
| `onlyBiomes` | Si non vide : seulement dans ces biomes |
| `replace` | Blocs ou `#tags` remplaçables ; vide : la roche du monde |

Valeurs par défaut (37 entrées) :

- **Minerais vanilla** : les tailles, nombres et altitudes de l'Overworld 1.21.1
  (`coal_upper`, `coal_lower`, `iron_upper`, `iron_middle`, `iron_small`, `gold`,
  `gold_lower`, `redstone`, `redstone_lower`, `lapis`, `lapis_buried`, `diamond`,
  `diamond_medium`, `diamond_large`, `diamond_buried`), facteur **0,7** partout, **1** dans
  le monde qui favorise le minerai, **×1,25** dans un biome spécialisé :

| Monde | Favorisé (1) | Biomes spécialisés (×1,25) |
|---|---|---|
| Ultra-Jungle | charbon, fer | charbon : jungle géante ; fer : jungle montagneuse |
| Ultra-Désert | or | or : désert blanc ; lapis : oasis |
| Ultra-Centrale | redstone | redstone : plaines conductrices ; fer : vallée magnétique |
| Ultra-Cratère | diamant | diamant : champs de fumerolles, grand cratère ; charbon : plaines aux cratères ; or : plateaux jaunes |
| Ultra-Forêt | fer | fer : vallée des feuilles tranchantes ; charbon : forêt origami ; lapis : jardin zen |
| Ultra-Abysse | lapis, redstone | lapis : abîmes rocheux ; redstone : forêt fongique |
| Ultra-Gratte-Ciel | fer, charbon | fer : zone industrielle ; charbon : quartier résidentiel |

- **Émeraude** : facteur 0,3, seulement dans les biomes montagneux (jungle montagneuse,
  canyon d'ivoire, montagnes électriques, plateaux jaunes).
- **Cuivre** : facteur 0,25, **aucun dans l'Ultra-Centrale** (un bloc de cuivre = 9 lingots =
  des Poké Balls).
- **Gemmes de Generations** (`crystal`, `ruby`, `sapphire` + `_buried`, veines de 4 ;
  `silicon` ; `meteorite`) : cristal dans le Désert, la Centrale et l'Abysse ; rubis dans le
  Cratère et le Désert ; saphir dans l'Abysse et la Jungle ; silicium dans la Centrale et le
  Gratte-Ciel ; météorite dans le Cratère seulement. Méga-gemmes, téra-éclats et cristaux Z
  restent réservés à l'Overworld.
- **Pierres évolutives** (minerais de Cobblemon `cobblemon:<pierre>_stone_ore`, veines de
  3, 2 tentatives par chunk contre 16 dans un biome favorisé de l'Overworld) : Plante
  (Jungle, Forêt), Eau (Jungle, Abysse), Soleil (Désert), Foudre (Centrale), Feu (Cratère),
  Éclat (Forêt), Nuit (Abysse, Gratte-Ciel), Lune (Abysse), Aube (Gratte-Ciel) ; `ice_stone`
  est listée sans monde (à activer si voulu).
- **Ultrite** (`ultrite_large`, `ultrite_small`) : dans les sept mondes, blocs isolés jamais
  au contact de l'air, en profondeur (y −56 à −40 surtout, quelques-uns jusqu'à y 40).

Limite : sans nouveau bloc, un minerai dans le grès ultra, la pierre chargée ou la roche
de l'Abysse garde sa texture pierre (ou deepslate dans le tuf de l'Ultra-Cratère).

### Mesure hors jeu

`ProbeResources` (banc d'essai, sans serveur) fait tourner la feature sur une fausse roche
pleine (sol à y 70, deepslate sous y 0) biome par biome, et les features de minerais de
l'Overworld de Minecraft sur la même roche. Blocs de minerai par chunk, 60 chunks par
biome :

| | Overworld | Ultra-Mondes |
|---|---|---|
| Charbon | 123 | 81 à 94 (66–76 %), 116 à 130 favorisé, jusqu'à 152 (124 %) dans le biome spécialisé |
| Fer | 87 | 60 à 63 (70 %), 86 à 89 favorisé, 107 à 109 (125 %) spécialisé |
| Or | 31 | 20 à 23 (67 %), 30 à 31 favorisé, 37 (120 %) spécialisé |
| Redstone | 45 | 28 à 34 (67 %), 43 à 44 favorisé, 55 à 56 (123 %) spécialisé |
| Lapis | 29 | 19 à 21 (70 %), 29 favorisé, 37 (127 %) spécialisé |
| Diamant | 28 | 19 à 23 (72 %), 27 à 30 favorisé, 36 à 37 (130 %) spécialisé |
| Émeraude (montagnes) | 2,9 | 0,7 à 1,0 |
| Cuivre | 109 | 24 à 29 (25 %), 0 dans l'Ultra-Centrale |
| Cristal, rubis, saphir | — | 9 à 13 dans leurs mondes |
| Ultrite | débris antiques : 2,0 dans de la netherrack pleine | 1,0 à 1,7 |

Les valeurs absolues valent pour cette roche pleine (pas de grottes) : c'est le rapport à
l'Overworld qui compte. Chaque pierre évolutive est à environ un bloc de minerai par
chunk dans ses mondes (0,6 à 1,3), l'Ultrite entre 1,0 et 1,7.

### Ultrite minable

`ultrite_debris` existait mais ne donnait rien (ni table de butin, ni tag de pioche, ni
recette). Ajouté : table de butin (il se donne lui-même), tags `mineable/pickaxe` et
`needs_diamond_tool`, cuisson au four et au haut fourneau en `ultrite_remnant` (comme les
débris antiques en fragments de Netherite). Le lingot reste 4 remnants + 4 lingots Z.

### Décors sans farming

Règle écrite en tête de `tools/ultra_worlds_defs.py` : les grandes formations ne rendent
rien de précieux.

| Où | Avant | Maintenant |
|---|---|---|
| Ultra-Désert : pics, géodes | `crystal_block`, `ultra_crystal`, améthyste et grappes | verre, verre teinté, `mirror_glass`, petits bourgeons (ne donnent rien) |
| Ultra-Centrale : champ de cristaux | `crystal_block`, `ultra_crystal`, froglight | verre teinté, pierre chargée lumineuse |
| Ultra-Centrale : plaques au sol | `oxidized_copper` | prismarine sombre, terre cuite cyan (`verdigris_patches`) |
| Ultra-Abysse : cristaux, pics, veines | `crystal_block`, `ultra_crystal`, `water_quartz`, lanternes de mer, froglights | verre teinté, glace compactée, prismarine, shroomlight |
| Ultra-Jungle : pics lumineux | froglights | shroomlight, verre teinté |
| Ultra-Gratte-Ciel : toits et murs industriels | cuivre oxydé | prismarine sombre, briques de prismarine, terre cuite verte |

`crystal_light` reste en petite touche pour la lumière : il ne se retransforme pas en
cristaux. Les formes et la génération ne changent pas.

### Lumière de l'Ultra-Abysse (shaders)

Un shader ignore la lumière ambiante du monde (`ambient_light` du `dimension_type`) :
seule la lumière émise par des blocs compte pour lui, et l'Abysse y était très sombre.
Feature `ultra_abyss/cavern_light` dans les cinq biomes, en dernière étape : 5 à 9 blocs
de lumière invisibles de Minecraft (`minecraft:light`, niveau 15) par chunk, dans l'air
des salles entre y −20 et 118. Rien à miner : en survie on ne peut ni les voir, ni les
frapper, ni les ramasser ; poser un bloc dessus les remplace. En dernier, parce qu'un bloc
de lumière n'est pas de l'air : les features qui cherchent de l'air s'arrêteraient dessus.
Sans shader, l'Abysse est aussi plus clair qu'avant.

## Phase 4.3.1 (lot 2) : structures, monde pilote Ultra-Cratère

### Comment c'est fait

Structures **jigsaw** de Minecraft, entièrement en données, assemblées et posées par
Minecraft lui-même chunk par chunk à partir de la seed (`/locate structure` et
`/place structure` fonctionnent). Le mod ne contenait aucun template : ils sont
**écrits par un outil**, rien n'est construit en jeu.

- `tools/ultra_structures.py` : écrit les templates `.nbt` (format des structures de
  Minecraft 1.21.1, NBT écrit à la main, sans bibliothèque), les pools jigsaw, les
  structures, les ensembles et les tags de biomes. Vérifie que chaque connecteur trouve,
  dans son pool, une pièce portant le connecteur qu'il demande. Avec un dossier en
  argument, dessine un aperçu PNG de chaque template (vue de dessus, plan, deux façades).
- `tools/ultra_structures_<monde>.py` : les plans d'un monde, décrits en code (murs,
  salles, coupoles, voûtes, escaliers, mobilier, portes).
- Un template : couche 0 dans le sol (Minecraft pose la couche 1 d'une pièce jigsaw sur le
  premier bloc libre), sols à y 0, salles à partir de y 1 ; une case non définie garde le
  terrain. Vitres, barreaux, murets : leurs raccords sont calculés par l'outil (Minecraft
  ne met pas à jour les formes des blocs d'une pièce jigsaw).

Données écrites : `structure/<monde>/…nbt`, `worldgen/template_pool/<monde>/…`,
`worldgen/structure/<monde>/…`, `worldgen/structure_set/<monde>/…`,
`tags/worldgen/biome/has_structure/<monde>/…`.

### Fréquences (`config/generations/core/ultra_space/ultra_structures.json`)

Les ensembles utilisent le placement `generations_core:ultra_spread`
(`UltraSpreadPlacement`, qui étend le « random spread » vanilla : `/locate` et les zones
d'exclusion le traitent comme tel). Ses nombres sont lus dans la config
(`UltraStructuresConfig`), par ensemble :

| Option | Rôle |
|---|---|
| `enabled` (racine) | Aucune structure des Ultra-Mondes dans les nouveaux chunks |
| `sets.<ensemble>.enabled` | Activer ou non cet ensemble |
| `sets.<ensemble>.spacing` | Un essai par carré de ce nombre de chunks (16 blocs par chunk) |
| `sets.<ensemble>.separation` | Écart minimal entre deux essais, en chunks |

Les valeurs par défaut sont celles des JSON `structure_set` ; quelles structures un
ensemble contient et leurs poids restent dans ces JSON (modifiables par datapack). Lue au
démarrage et par `/ultrawarp reload` ; comme tout réglage de génération, un changement ne
vaut que pour les chunks générés ensuite.

### Ultra-Cratère

**Base lunaire abandonnée** (`generations_core:ultra_crater/lunar_base`, ensemble
`ultra_crater/landmark`, un essai par 36 chunks soit 576 blocs ; plaines aux cratères,
grand cratère, champs de fumerolles ; `beard_thin` : le terrain s'ajuste sous les
pièces). Un cœur octogonal à deux niveaux (pont de contrôle vitré, mât, radôme) d'où
partent des tubes vers des modules : laboratoire, quartiers, entrepôt, serre sous voûte de
verre, dôme d'observation avec télescope, hangar ouvert avec rover, réacteur, jonction à
quatre portes, module éventré. Tubes courts, longs, vitrés, effondrés, et tubes-escaliers
qui montent de 4 blocs (modules à d'autres niveaux). Les sas donnent dehors sur une aire
d'atterrissage, un mât d'antenne ou un champ de panneaux solaires. Une porte qui ne mène à
rien est fermée par une baie vitrée ; le cœur a toujours un sas.

Connecteurs : une porte de module s'appelle `door` et demande un `tube_end` au pool des
couloirs ; un bout de tube s'appelle `tube_end` et demande une `door` au pool des modules.
Portes de 3 × 3, connecteur en bas au milieu.

**Petites structures** (ensemble `ultra_crater/sites`, un essai par 11 chunks soit 176
blocs, à 8 chunks au moins d'une base), deux variantes chacune :

| Structure | Variantes | Biomes |
|---|---|---|
| `station` : mini-station sur pieds, échelle sous la capsule, antenne | intacte, écrasée au sol et éventrée | les cinq |
| `hangar` : hangar voûté ouvert, caisses, rover | intact, toit effondré | plaines, grand cratère, fumerolles |
| `post` : poste d'observation sur pylônes, télescope, parabole | intact, parabole tombée | plaines, forêt de flèches, plateaux |
| `shelter` : abri bas en pierre volcanique, deux pièces | simple, avec réserve | les cinq |

Blocs : marbres blanc et gris, sols du métro, pierre volcanique, caisses, lanternes et
`crystal_light` de Generations, verre, quartz et béton vanilla. Aucun bloc de fer ni de
cuivre, aucun coffre à butin. Les meubles de Generations (bureau, PC, télé…) ne sont pas
utilisés : ce sont des modèles sur plusieurs blocs, trop risqués à poser dans un template.

**Décor autour des structures** : les features du mod (cratères, flèches, arches,
fumerolles, jardins zen) ne se posent plus dans un chunk qui contient une structure
(`structureAt` dans `UltraWorldgen.kt`) : elles passent après les structures et auraient
creusé une base ou traversé ses toits.

### Vérification hors jeu

`ProbeStructures` (banc d'essai, sans serveur) charge les registres comme un monde, fait
lire chaque template par le chargeur de Minecraft, puis assemble une structure avec le
code jigsaw de Minecraft (`findValidGenerationPoint`) et pose ses pièces dans un faux
niveau pour en tirer une image. Résultat : 29 templates lus, 5 structures, 2 ensembles,
11 pools ; bases de 11 à 30 pièces (jusqu'à 180 blocs de long), aucun connecteur resté
ouvert, toutes les variantes des petites structures obtenues. Non vérifiable ainsi :
l'allure en jeu et l'ajustement du terrain (`beard_thin`), qui se fait à la génération
des chunks.

### Tester

| Commande | Effet |
|---|---|
| `/locate structure generations_core:ultra_crater/lunar_base` | La base la plus proche (dans l'Ultra-Cratère) |
| `/place structure generations_core:ultra_crater/lunar_base` | En pose une sur place, même dans un monde déjà généré (sans ajustement du terrain) |
| `/place template generations_core:ultra_crater/base/hangar` | Pose un seul template |

### Pour les phases suivantes

- `UltraWarpSession.Stage` : `TEST`, `RIDE`, `DEFEAT`, `CAPTURE`. Depuis la phase 4.1,
  `UltraWarpTransfers` mène le joueur dans la dimension de `destinations[].dimension`
  après un résultat `SUCCESS`.
- `UltraWarpSession.tick()` est appelé chaque seconde. `onEnd(raison)` est appelé
  une fois, quelle que soit la fin : c'est là que se fera le retour du joueur
  (position et dimension de l'autel sont gardées dans la session).
- Les sessions devront être sauvegardées dès qu'une étape déplacera le joueur ou lui
  prendra quelque chose, pour qu'un crash ne le laisse pas dans l'Ultra-Espace.
- Les orbes, obstacles et portails utiliseront la position acceptée par le serveur
  (`UltraWarpSession.offsetX/Y`) et le même tunnel ; le boost et l'esquive ont déjà
  leurs touches et leur transport.
