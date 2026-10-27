$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$library = Join-Path $root 'lib/naudio/lib/net35/NAudio.dll'
if (-not (Test-Path $library)) { throw 'Falta NAudio 1.10.0. Consulta README.md para restaurarlo desde NuGet.' }
$out = Join-Path $root 'dist/JuntosBluetooth-0.1.1'
New-Item -ItemType Directory -Force $out | Out-Null
& $compiler /nologo /target:winexe /platform:anycpu /optimize+ /utf8output "/win32icon:$root/assets/juntos.ico" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Xml.dll /r:System.Xml.Linq.dll /r:System.Core.dll "/r:$library" "/out:$out/JuntosBluetooth.exe" (Join-Path $root 'JuntosBluetooth.cs')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar Juntos Bluetooth.' }
Copy-Item -LiteralPath $library -Destination $out
Copy-Item -LiteralPath (Join-Path $root 'NAudio-LICENSE.txt') -Destination $out
Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination $out
Copy-Item -LiteralPath (Join-Path $root 'JuntosBluetooth.exe.config') -Destination $out
$files = @('JuntosBluetooth.exe', 'JuntosBluetooth.exe.config', 'NAudio.dll', 'NAudio-LICENSE.txt', 'README.md') | ForEach-Object { Join-Path $out $_ }
Compress-Archive -LiteralPath $files -DestinationPath (Join-Path $root 'dist/JuntosBluetooth-Windows-0.1.1.zip') -Force
Write-Output "Creado: $out/JuntosBluetooth.exe"
