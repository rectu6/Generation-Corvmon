# Correctif de rendu Iris — Corvemon / Fabric 1.21.1

## État

Correctif candidat, compilé contre Cobblemon `1.8.4+1.21.1`. À valider en jeu.

Base : branche `1.8` de Generations-Core, commit
`8deafca5b3254b19a10ab9580f3d2a0132bda855` (`1.2.8-1.8.2`). La version
publiée `1.2.8-1.8.4` n'a pas de source publique ; ses shaders sont identiques
à ceux de ce commit. `gradle.properties` cible donc Cobblemon `1.8.4+1.21.1`
avec la version `1.2.8-1.8.4+irisfix`.

Instance de test : Iris `1.8.14-beta.1+mc1.21.1`, Sodium `0.8.13+mc1.21.1`,
pack `ComplementaryUnbound_r5.9.3`.

Le moteur réellement embarqué est `libs/RareCandy-2.14.2.jar`. Le dépôt
RareCandy actuel a une API différente : ne pas remplacer ce JAR par lui.

## Cause principale : RareCandy dessinait dans une image effacée par Iris

RareCandy dessine les modèles en OpenGL brut, à deux moments de
`LevelRenderer.renderLevel` (`LevelRendererMixin`) : avant `DebugRenderer.render`
(matériaux opaques), puis avant les nuages (matériaux transparents).

Avec un pack de shaders, Iris dessine le monde dans ses propres images
(`colortex*`), puis écrit le résultat final dans l'image principale de
Minecraft. Entre deux de ses programmes, Iris laisse l'image principale active
(`ExtendedShader.clear()` appelle `getMainRenderTarget().bindWrite(false)`).
RareCandy écrivait donc sa couleur dans une image qu'Iris écrase à la fin, alors
que sa profondeur restait dans `depthtex0`, partagé avec cette image principale.
Le pack voyait une surface sans couleur propre : un modèle fantôme, translucide.

`IrisFramebufferCompat` lie désormais, pendant le dessin RareCandy, l'image
qu'Iris utilise pour les entités (`writingToBeforeTranslucent` ou
`writingToAfterTranslucent` selon la phase, comme `ExtendedShader`). Seule la
sortie 0 (couleur principale du pack) est écrite ; les autres cibles du pack
(normales, données de matériau) ne reçoivent pas de valeurs indéfinies. Iris
est facultatif : tout passe par réflexion, et sans Iris, sans pack actif ou avec
une version d'Iris incompatible, le comportement d'origine est conservé.

## Défauts secondaires corrigés

1. Le fragment shader multipliait les quatre canaux de la couleur par la
   lumière, y compris l'alpha. Seuls les canaux RGB reçoivent maintenant
   l'éclairage ; l'alpha du matériau et les découpes restent préservés.
2. `LightTextureMixin` lie directement la texture d'éclairage à l'emplacement
   demandé, sans modifier aussi l'emplacement 2 des shaders Minecraft.
3. Les matériaux opaques désactivent explicitement le mélange alpha.
4. RareCandy 2.14.2 dessinait les matériaux transparents dans le passage SOLID
   puis dans TRANSPARENT. `GenerationsGLModel` réserve chaque matériau à son
   passage ; la transparence n'écrit plus la profondeur.
5. `disableDepthTest` et `disableCull` modifiaient par erreur l'état du mélange
   alpha enregistré par `RenderSystemMixin`.
6. Le programme OpenGL et les textures 0 à 5 sont restaurés après RareCandy,
   y compris le cache de programme d'Iris (`_glUseProgram`), dans des blocs
   `finally`.

## Ombres des modèles RareCandy (à valider en jeu)

Iris construit la carte d'ombres en redessinant entités et blocs-entités depuis
le soleil, via le pipeline de sommets de Minecraft et le programme d'ombres du
pack. RareCandy dessine avec son propre programme OpenGL, hors de ce pipeline :
ses modèles n'arrivaient jamais dans la carte d'ombres. Chaque pack déforme
cette carte à sa façon, donc dessiner RareCandy directement dedans ne marcherait
pas d'un pack à l'autre.

- `RareCandyShadowMesh` relit les sommets d'un maillage depuis le GPU (RareCandy
  libère sa copie après l'envoi) et les anime sur le processeur, avec la même
  formule que `animated.vs.glsl`.
- `RareCandyShadows` détecte la passe d'ombres (`IrisApi.isRenderingShadowPass`,
  par réflexion) ; `CompiledModel.render` y écrit le modèle dans les tampons
  d'ombres d'Iris (`RenderType.entityCutoutNoCull` avec la texture diffuse, pour
  les découpes alpha), au lieu de le mettre dans la file RareCandy.
- `EntityRenderDispatcherMixin` expose les tampons de l'entité en cours, car
  `RareCandyBone` ne reçoit qu'un `VertexConsumer`.
- La pose « vue du soleil » écrite par les appelants dans l'instance est remise à
  la pose normale après la passe : RareCandy continue de dessiner une instance
  quelques instants après sa dernière utilisation.
- Seuls les matériaux opaques (`BlendType.None`) projettent une ombre.
- Réglage `client.shaderShadows` (activé par défaut) dans la config Generations.

## Optimisations du rendu RareCandy (à mesurer en jeu)

- **Uniformes** (`UniformCache.kt`, `Pipelines.kt`) : RareCandy renvoyait les
  ~45 uniformes avant chaque maillage dessiné. Un programme GL garde ses valeurs
  entre deux dessins : pendant une passe (`RareCandyPass.begin/end`), les valeurs
  par image (caméra, brouillard, lumières, numéros de texture) ne partent qu'une
  fois, et les valeurs par instance (pose, os, lumière, teinte, téra) une fois
  par instance. Hors passe, tout est envoyé comme avant.
- **Os** (`BoneUploader`) : `Uniform.uploadMat4fs` faisait un appel
  `glUniformMatrix4fv` par os (jusqu'à 220) pour chaque maillage ; le tableau
  part maintenant en un seul appel.
- **Animation** (`RareCandyAnimation.kt`) : `Animation.getFrameTransform`
  renvoie un tableau unique par animation, partagé par tous les Pokémon qui la
  jouent : tous étaient dessinés avec la pose calculée en dernier. Chaque
  instance copie maintenant la pose dans son propre tableau, et ne la recalcule
  pas si l'animation et le temps n'ont pas changé (passe d'ombres puis passe
  normale dans la même image).
- **Ombres** : la copie CPU d'un maillage est relue depuis le GPU la première
  fois qu'il projette une ombre (rien en mémoire sans pack de shaders), stockée
  sur ~30 octets par sommet au lieu de 52, et libérée avec le modèle. Le coût
  principal est l'écriture des sommets dans les tampons de Minecraft, où Iris
  traite chaque sommet et chaque quad : les sommets proches sont fusionnés
  (grille de 40 cases, par os principal), les triangles voisins sont assemblés
  en vrais quads (deux fois moins de sommets), et au-delà de 12 blocs une copie
  plus grossière (grille de 16) est utilisée. Au-delà de
  `client.shaderShadowDistance` blocs (32 par défaut, 0 = sans limite), les
  modèles ne projettent plus d'ombre. Mesuré avec Complementary (scène d'une
  vingtaine de Pokémon 3D) : 66 fps avec les ombres d'entités à 48 blocs, 70 à
  32 blocs, 90 sans ombres d'entités.
- `ModelRegistry.tick` ne crée plus de collections temporaires à chaque image.

## Éclairage par le soleil (validé en jeu)

Minecraft éclaire les entités avec deux directions fixes ; un pack de shaders les
éclaire avec son soleil. RareCandy dessine hors des programmes du pack, ses
modèles gardaient donc l'éclairage fixe : le côté tourné vers le soleil n'était
pas plus clair que l'autre.

- `RareCandyLighting` calcule, une fois par image, la direction que Iris donne
  au pack (`CelestialUniforms.getShadowLightPositionInWorldSpace`) : rotation du
  ciel vanilla, inclinée par le `sunPathRotation` du pack (lu par réflexion,
  `IrisFramebufferCompat.sunPathRotation`), le soleil le jour et la lune la nuit.
  L'éclairage est donc cohérent avec les ombres du décor.
- Couleur : blanche à midi, orangée près de l'horizon, bleutée et plus faible la
  nuit. Intensité nulle à l'horizon, réduite sous la pluie.
- `animated.vs.glsl` (`getLighting`) mélange l'éclairage d'origine et celui du
  soleil (ambiant 0,45 + direct 0,75) selon cette intensité et la lumière du
  ciel au niveau du modèle : sous terre ou à l'intérieur, l'éclairage d'origine
  revient.
- Uniquement avec un pack actif ; réglage `client.shaderSunLighting` (activé par
  défaut). Coût négligeable : un calcul par sommet sur le GPU.

## Fuite de textures et crash au changement de pack

- **Fuite** : `ModelLoader.readImages` enregistre toutes les images d'un modèle
  sous `<modèle>-<image>`, mais `Material.close` (RareCandy 2.14.2) libère deux
  fois la diffuse et jamais l'émission, ni les images qu'aucun matériau
  n'utilise. Les modèles étant déchargés 5 s après avoir quitté l'écran puis
  rechargés, chaque passage laissait des textures en mémoire (RAM et VRAM).
  `CompiledModel.delete` libère maintenant toutes les textures du modèle
  (`GenerationsTextureLoader.removeModelTextures`). Un modèle rechargé ne crée
  plus de doublon jamais libéré, et la table des textures est partagée sans
  risque entre les threads de chargement et le rendu (`ConcurrentHashMap`).
- **Crash** : retirer le pack de ressources des modèles 3D pendant que des
  entités les utilisent faisait planter `ModelRegistry.tick`
  (`FileNotFoundException` sur le `.pk`). Le modèle est maintenant ignoré et
  réessayé au prochain rechargement des ressources.

## Limites connues

- Les modèles RareCandy gardent leur propre éclairage : le pack ne recalcule pas
  leur éclairage comme pour les entités vanilla, et l'anti-crénelage temporel
  (TAA) du pack ne décale pas leurs sommets.
- Les ombres coûtent du processeur (sommets animés sur CPU pour chaque modèle
  dans la zone d'ombre) et de la mémoire (copie des sommets, avec un pack actif).
- La première ombre d'un maillage le relit depuis le GPU : une courte attente,
  une seule fois par modèle chargé.

## Compilation

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat :fabric:remapJar --no-scan --no-parallel "-Dorg.gradle.jvmargs=-Xmx5G" -Pkotlin.compiler.execution.strategy=in-process
```

Le JAR se trouve dans `fabric/build/libs/`. Avec 16 Go de RAM, les réglages par
défaut du projet (8 Go pour Gradle et 4 Go pour Kotlin) font planter la
compilation.

Le test GPU isolé de Codex (`tools/render-regression/run.ps1`) couvre le shader
et le découpage des passes, pas l'intégration avec Iris.

## Essai en jeu

Comparer dans la même scène, avec un Pokémon et une statue ou un bloc RareCandy :

1. Shaders désactivés, puis Complementary activé.
2. Jour, nuit, sous l'eau, derrière du verre.
3. Interface du PC et de l'équipe (rendu hors monde, non concerné par Iris).
4. Activation/désactivation des shaders et rechargement des ressources (F3+T).
