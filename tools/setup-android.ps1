param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [switch]$AcceptAndroidLicenses
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path (Join-Path $JavaHome 'bin\javac.exe')) -or
    -not (Test-Path (Join-Path $JavaHome 'bin\jlink.exe'))) {
    throw 'JavaHome debe apuntar a un JDK completo con javac y jlink, no a un JRE.'
}
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = Join-Path $projectRoot '.tools'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $sdkRoot
$env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-home'
$env:ANDROID_USER_HOME = Join-Path $toolsRoot 'android-user'
$manager = Join-Path $sdkRoot 'cmdline-tools\19.0\bin\sdkmanager.bat'
if (-not (Test-Path $manager)) {
    New-Item -ItemType Directory -Force $toolsRoot | Out-Null
    [xml]$repository = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-1.xml').Content
    $package = $repository.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;19.0']")
    if ($null -eq $package) { throw 'No se encontraron las herramientas oficiales del SDK.' }
    $archive = $package.SelectSingleNode("./*[local-name()='archives']/*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']")
    $url = $archive.SelectSingleNode("./*[local-name()='url']").InnerText
    $checksumNode = $archive.SelectSingleNode("./*[local-name()='checksum']")
    $algorithm = $checksumNode.GetAttribute('type').Replace('-', '').ToUpperInvariant()
    if (-not $algorithm) { $algorithm = 'SHA1' }
    $zip = Join-Path $toolsRoot 'commandlinetools.zip'
    Invoke-WebRequest ('https://dl.google.com/android/repository/' + $url) -OutFile $zip
    if ((Get-FileHash $zip -Algorithm $algorithm).Hash -ne $checksumNode.InnerText.Trim()) {
        throw 'El archivo del SDK no coincide con su checksum oficial.'
    }
    $unpack = Join-Path $toolsRoot 'sdk-unpack'
    Expand-Archive -LiteralPath $zip -DestinationPath $unpack -Force
    New-Item -ItemType Directory -Force (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
    $moveSource = [IO.Path]::GetFullPath((Join-Path $unpack 'cmdline-tools'))
    $moveTarget = [IO.Path]::GetFullPath((Join-Path $sdkRoot 'cmdline-tools\19.0'))
    $allowedRoot = [IO.Path]::GetFullPath($toolsRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $moveSource.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        -not $moveTarget.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Las rutas del SDK deben estar dentro de .tools del proyecto.'
    }
    Move-Item -LiteralPath $moveSource -Destination $moveTarget
}
if ($AcceptAndroidLicenses) {
    1..100 | ForEach-Object { 'y' } | & $manager "--sdk_root=$sdkRoot" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
} else {
    & $manager "--sdk_root=$sdkRoot" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
}
if ($LASTEXITCODE -ne 0) { throw 'No se completó la instalación del SDK.' }
$sdkProperty = $sdkRoot.Replace('\', '/').Replace(':', '\:')
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$sdkProperty" -Encoding ascii
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'La compilación o la revisión de Android falló.' }
} finally { Pop-Location }
