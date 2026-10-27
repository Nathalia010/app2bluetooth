$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $project '.signing'
$keyFile = Join-Path $folder 'play-upload.p12'
$properties = Join-Path $folder 'play-upload.properties'
if ((Test-Path $keyFile) -or (Test-Path $properties)) {
    if ((Test-Path $keyFile) -and (Test-Path $properties)) { Write-Output 'La clave de carga ya existe; se conserva.'; exit 0 }
    throw 'Configuración de firma incompleta. Restaura los archivos originales; no se sobrescribe ninguna clave.'
}
New-Item -ItemType Directory -Force $folder | Out-Null
$jdk = (Get-ChildItem (Join-Path $project '.tools/jdk17') -Directory | Select-Object -First 1).FullName
$random = New-Object byte[] 32
$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
$generator.GetBytes($random)
$generator.Dispose()
$password = [Convert]::ToBase64String($random)
$env:JUNTOS_GENERATED_UPLOAD_PASSWORD = $password
try {
    & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -storetype PKCS12 -keystore $keyFile -alias juntos-upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Juntos Bluetooth' -storepass:env JUNTOS_GENERATED_UPLOAD_PASSWORD -keypass:env JUNTOS_GENERATED_UPLOAD_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la clave de carga.' }
    $content = "storeFile=.signing/play-upload.p12`nstorePassword=$password`nkeyAlias=juntos-upload`nkeyPassword=$password`n"
    [IO.File]::WriteAllText($properties, $content, (New-Object Text.UTF8Encoding($false)))
    & (Join-Path $jdk 'bin/keytool.exe') -exportcert -rfc -keystore $keyFile -alias juntos-upload -storepass:env JUNTOS_GENERATED_UPLOAD_PASSWORD -file (Join-Path $folder 'play-upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo exportar el certificado público.' }
    Write-Output 'Clave de carga y configuración creadas en .signing. Guarda una copia segura; no las compartas ni las publiques.'
} finally { Remove-Item Env:JUNTOS_GENERATED_UPLOAD_PASSWORD -ErrorAction SilentlyContinue; $password = $null; $content = $null }
