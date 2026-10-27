$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$javaDirectory = (Get-ChildItem (Join-Path $projectRoot '.tools/jdk17') -Directory | Select-Object -First 1).FullName
$androidJar = Join-Path $projectRoot '.tools/android-sdk/platforms/android-36/android.jar'
$resourceJar = Join-Path $projectRoot 'app/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar'
$productionClasses = Join-Path $projectRoot 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
$testClasses = Join-Path $projectRoot 'app/build/output-controls-test'
if (-not (Test-Path (Join-Path $productionClasses 'com/karen/juntos/OutputPanel.class'))) {
    throw 'Build :app:assembleDebug before running these regression checks.'
}
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$testSource = Join-Path $PSScriptRoot 'java/com/karen/juntos/OutputControlsRegression.java'
$classPath = $productionClasses + [IO.Path]::PathSeparator + $androidJar + [IO.Path]::PathSeparator + $resourceJar
& (Join-Path $javaDirectory 'bin/javac.exe') -encoding UTF-8 -cp $classPath -d $testClasses $testSource
if ($LASTEXITCODE -ne 0) { throw 'Output control regression compilation failed.' }
& (Join-Path $javaDirectory 'bin/java.exe') -cp ($testClasses + [IO.Path]::PathSeparator + $classPath) com.karen.juntos.OutputControlsRegression
if ($LASTEXITCODE -ne 0) { throw 'Output control regression failed.' }
