param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MinecraftLibraries = "$env:APPDATA/.minecraft/libraries",
    [string]$LwjglVersion = '3.3.1'
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to a JDK 21 installation or pass -JavaHome.' }
$repo = (Resolve-Path "$PSScriptRoot/../..").Path
$jars = @("$repo/libs/RareCandy-2.14.2.jar", "$MinecraftLibraries/org/joml/joml/1.10.5/joml-1.10.5.jar")
foreach ($module in @('lwjgl', 'lwjgl-opengl', 'lwjgl-glfw')) {
    $jars += "$MinecraftLibraries/org/lwjgl/$module/$LwjglVersion/$module-$LwjglVersion.jar"
    $jars += "$MinecraftLibraries/org/lwjgl/$module/$LwjglVersion/$module-$LwjglVersion-natives-windows.jar"
}
foreach ($jar in $jars) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing test dependency: $jar" }
}
$output = Join-Path $repo 'build/render-regression'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$classpath = $jars -join ';'
Push-Location $repo
try {
    & "$JavaHome/bin/javac.exe" -cp $classpath -d $output 'common/src/main/java/generations/gg/generations/core/generationscore/common/client/render/rarecandy/loading/GenerationsGLModel.java' "$PSScriptRoot/RenderRegression.java"
    if ($LASTEXITCODE -ne 0) { throw 'Regression test compilation failed' }
    $original = git show '8deafca5b3254b19a10ab9580f3d2a0132bda855:common/src/main/resources/assets/generations_core/shaders/animated.fs.glsl'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read baseline fragment shader' }
    $baseline = Join-Path $output 'upstream.fs.glsl'
    [IO.File]::WriteAllText($baseline, ($original -join "`n"))
    & "$JavaHome/bin/java.exe" -cp "$output;$classpath" RenderRegression $baseline
    if ($LASTEXITCODE -ne 0) { throw 'GPU regression failed' }
} finally {
    Pop-Location
}
