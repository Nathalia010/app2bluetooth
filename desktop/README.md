# Juntos Bluetooth para Windows 0.1.1

Aplicación nativa en C# y Windows Forms para Windows 10/11 con .NET Framework 4.8. No sustituye ni modifica el APK de Android. Portable: extrae **todo** el ZIP en una carpeta y abre `JuntosBluetooth.exe`, conservando NAudio.dll a su lado. No requiere instalación ni permisos de administrador. El ejecutable no tiene firma comercial.

## Uso

1. Abre **Configuración de Bluetooth** desde Juntos y conecta los dos audífonos.
2. Selecciona sus salidas en Dispositivo 1 y Dispositivo 2. Se enumeran las salidas activas de Windows, incluidas salidas USB, HDMI y altavoces. Una salida disponible no confirma que esté reproduciendo audio.
3. Despliega **Volumen por dispositivo** para ajustar cada salida. La casilla **Sonar** utiliza el silencio nativo de Windows; al reactivarla conserva el nivel anterior. Estos controles cambian el volumen de la salida para todas las aplicaciones que la utilizan.
4. Para compartir música, haz que tu reproductor utilice el **dispositivo 1** (elige la salida en los ajustes de sonido de Windows o del reproductor). Luego pulsa **Compartir audio del 1 al 2**. La aplicación captura la mezcla que se reproduce en la primera salida y envía una copia a la segunda mediante WASAPI. No modifica la salida predeterminada de Windows.
5. **Detener audio compartido** termina la copia. Cambiar dispositivos, actualizar la lista o desconectar una salida también la detiene. Cerrar Juntos termina la copia; minimizar a la bandeja la mantiene.

No se captura el micrófono, no se escriben grabaciones ni se transmite audio por Internet. Se copia todo el sonido de la salida 1, incluidas otras aplicaciones y avisos que suenen por ella. Contenido protegido, modo exclusivo, perfiles de llamada, controladores y hardware Bluetooth pueden impedir la captura o reproducción. Los audífonos pueden tener distintas latencias y producir eco: no se garantiza sincronización. Silenciar o cambiar el volumen del origen puede influir en la captura según el controlador. Esta versión no muestra batería ni implementa widgets de Windows.

El tema y los identificadores de las dos salidas se guardan en `%LOCALAPPDATA%\JuntosBluetooth\preferences.xml`. No inicia automáticamente con Windows. La primera versión no está firmada ni ha sido probada reproduciendo audio en dos dispositivos físicos.

## Compilar

Se utiliza el compilador incluido con .NET Framework 4.x. Dependencia: NAudio 1.10.0, paquete oficial de NuGet, biblioteca net35 compatible con .NET Framework 4.8, licencia MIT incluida. Restauración desde la raíz del repositorio en PowerShell:

```powershell
New-Item -ItemType Directory -Force desktop/lib
Invoke-WebRequest https://api.nuget.org/v3-flatcontainer/naudio/1.10.0/naudio.1.10.0.nupkg -OutFile desktop/lib/naudio.zip
Expand-Archive desktop/lib/naudio.zip desktop/lib/naudio -Force
.\desktop\build.ps1
```

La salida portable se genera en `desktop/dist/JuntosBluetooth-Windows-0.1.1.zip`. `JuntosBluetooth.exe --self-test` comprueba identidad de rutas, prevención de bucle, límite del búfer y preferencias, sin abrir salidas de audio. Escribe los resultados en `test-output/results.txt` junto al ejecutable. `--preview` genera una vista con datos simulados y no opera dispositivos.

Documentación de WASAPI: https://learn.microsoft.com/en-us/windows/win32/coreaudio/loopback-recording

Hecho por Manuel Martinez y Karen Martinez. © 2026. Todos los derechos reservados sobre el código propio. NAudio © Mark Heath y colaboradores, con su licencia independiente en NAudio-LICENSE.txt.
