# Développement

## Dépôt

- Code : ce dépôt (branche `main`), basé sur Generations-Core 1.8 pour Cobblemon
  1.8.4 (`gradle.properties` : `cobblemon_version=1.8.4+1.21.1`).
- Version du mod : `1.2.8-1.8.4+irisfix`.
- Kotlin et Java, mixins dans `common/src/main/java/.../mixin`, rendu RareCandy
  2.14.2 inclus (`libs/`).

## Compiler

Avec 16 Go de RAM, les réglages par défaut (8 Go pour Gradle, 4 Go pour Kotlin) font
planter la compilation :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat :fabric:remapJar --no-scan --no-parallel "-Dorg.gradle.jvmargs=-Xmx5G" -Pkotlin.compiler.execution.strategy=in-process
```

Le JAR est dans `fabric/build/libs/`. Une compilation prend 5 à 8 minutes.

- Ne pas compiler pendant que le jeu tourne (mémoire).
- Ne pas remplacer le JAR dans `mods/` pendant que le jeu tourne (plantage au
  chargement de classes).

## Tester

- Instance Modrinth de test avec Cobblemon 1.8.4, Iris, Sodium et Complementary.
- Test GPU isolé du shader : `tools/render-regression/`.
- Le réglage `client.traceOpenGlErrors` donne la pile d'appels des erreurs OpenGL.

## Organisation du code

| Dossier (`common/src/main/java/generations/gg/generations/core/generationscore/common/`) | Contenu |
|---|---|
| `client/render/rarecandy/` | Rendu des modèles 3D, ombres, compatibilité Iris |
| `battle/` | Expérience, Méga-évolution, Dynamax |
| `world/raid/`, `world/level/block/raid/` | Raids Dynamax |
| `world/pokewand/`, `client/screen/pokewand/` | Pokewand |
| `world/fishing/` | Paliers de pêche |
| `world/effect/` | Effet Repousse |
| `world/container/` | Conteneurs d'objets (Boîte Noigrume, Walkmon…) |
| `mixin/` | Modifications de Minecraft et Cobblemon |

## Outils pratiques

- Lire le code de Cobblemon : `javap` sur le JAR remappé par Loom
  (`common/build/loom-cache/remapped_working/`).
- Les modèles `.pk` de RareCandy sont des archives 7z : on peut les ouvrir et les
  refaire avec commons-compress (inclus dans le JAR du mod).
- Les particules (Snowstorm) de Cobblemon prennent leurs textures dans l'atlas des
  particules (`textures/particle/`).

## Pièges connus

- `NativeImage.read(byte[])` copie l'image sur la petite pile mémoire de LWJGL
  (64 Ko) : utiliser un `InputStream`, sinon les grandes textures font planter le
  chargement des modèles.
- Kotlin : ajouter un paramètre après un paramètre lambda final casse les appels
  qui le passent entre accolades (garder `onClose` en dernier dans
  `GenericContainer.openScreen`).
