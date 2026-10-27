# Juntos Bluetooth

## Versión de PC

La versión nativa para Windows 10 y 11 está en [desktop/README.md](desktop/README.md). Incluye dos salidas, volumen y silencio por salida, tema claro/oscuro, bandeja del sistema y copia local del audio de una salida a otra mediante WASAPI. El ZIP portable se genera con `desktop/build.ps1`. Es una aplicación C# independiente; el proyecto Android se conserva. La reproducción simultánea requiere validación con dos equipos físicos y puede presentar diferencias de latencia.

Versión 0.7.2 nativa para Android 8.0 o posterior. Interfaz en español, sin cuentas ni anuncios activos. Encabezado superior: «JUNTOS BLUETOOTH», con el estilo pequeño verde original y sin título grande debajo.

En 0.7.1, el cuadro de créditos incluye aportes voluntarios y un botón que copia la llave de Nu `@KMB212`. El aporte no desbloquea funciones y la app no procesa pagos.

En 0.7.0 se reserva un espacio marcado «Publicidad» después de los volúmenes y antes del sonido de prueba. No hay SDK publicitario. Se añade privacidad y soporte dentro de Configuración, reglas de exclusión de preferencias en copias de seguridad y activación explícita del servicio de notificación. La configuración release usa una clave de carga independiente de la firma de pruebas. Consulta [la preparación para Google Play](publication/PLAY_STORE.md) y [la política para publicar](publication/privacy.html). El AAB preparado aún necesita pruebas en dispositivos y la revisión de Play Console.

En 0.6.5 se permite redimensionar el widget horizontal y verticalmente, partiendo de 2 columnas × 1 fila y con mínimos de 110 × 56 dp. El escritorio convierte estas medidas a su rejilla. Se conserva la cápsula compacta centrada dentro del espacio asignado.

En 0.6.4 el widget solicita un tamaño fijo de 2 × 1 y desactiva el redimensionamiento. Reduce el ancho mínimo y los márgenes para que entren los dos iconos en una rejilla estrecha. El launcher determina las medidas finales; vuelve a añadir los widgets antiguos para aplicar la nueva configuración.

En 0.6.3 el widget móvil solicita 2 × 1 celdas y utiliza una cápsula de 56 dp: únicamente dos iconos con arcos de batería, sin encabezado, engranaje, números ni barra inferior. Conserva el fondo degradado y la configuración de color/opacidad. El borde del fondo abre los ajustes, también disponibles desde la reconfiguración del escritorio cuando la admite. La información de batería se conserva para accesibilidad; sin lectura el arco queda gris. El tamaño real de las celdas depende del launcher y puede requerir volver a añadir el widget existente.

En 0.6.2 se elimina definitivamente la tercera posición: modelo fijo de dos salidas, dos equipos guardados y dos filas de notificación. Se borran los datos de la antigua tercera posición al actualizar su modelo y abrir la app. Esto no elimina emparejamientos Bluetooth del teléfono. Las referencias históricas a una tercera posición reservada ya no aplican.

## Widget compacto (0.6.1)

La cápsula mantiene 88 dp de alto aunque el escritorio asigne una celda mayor. Solicita un espacio de 3 × 1 y permite reducir también la altura. Tiene fondo degradado, título en una sola línea y engranaje dentro del margen. Dos arcos con degradado rodean los iconos: verde para niveles normales y cálido para batería baja. Solo se muestra el número, sin «Batería» ni porcentaje; el guion indica lectura no disponible. Nombres, estado y hora permanecen en las descripciones de accesibilidad; se retiran los textos extra de la vista. La vista previa usa la misma disposición compacta. Los widgets existentes pueden conservar el espacio anterior del launcher: redimensionarlos o quitarlos y añadirlos de nuevo permite solicitar el tamaño nuevo.

## Dos dispositivos, créditos y widget (0.6.0)

La pantalla, las barras de volumen, los equipos guardados visibles y la notificación muestran dos posiciones. La tercera capacidad permanece en el modelo y en los recursos para una versión futura; no aparece en la interfaz. Un equipo de la antigua tercera posición puede ocupar una posición visible libre. El contador de la notificación corresponde a las dos posiciones visibles.

Al final de la pantalla, «Hecho con cariño · Créditos» abre los nombres Manuel Martinez y Karen Martinez, la versión y © 2026 con reserva de derechos.

El widget «Juntos · Dos dispositivos» tiene una cápsula redondeada con acabado degradado, dos iconos de equipos, estado de conexión, batería y hora de la última lectura. Se añade desde el botón de la app o desde el selector Widgets del escritorio. El engranaje abre una vista previa con cuatro colores de fondo y tres opacidades (40 %, 70 %, 100 %), guardadas por instancia. Cancelar no cambia los ajustes. El launcher puede ofrecer reconfiguración al mantener pulsado; eliminar siempre corresponde al escritorio.

La batería es un dato opcional recibido del sistema (`android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`), cuya disponibilidad depende del fabricante. No se usan APIs ocultas por reflexión ni se inventan porcentajes. Solo se asocia por identidad Bluetooth exacta; una ruta sin esa identidad muestra «Batería —». Las lecturas caducan y se borran al perder la conexión. El widget se actualiza desde la app y su servicio, además de solicitar actualización periódica de 30 minutos al sistema. No garantiza observación continua cuando el servicio está apagado; muestra la hora de lectura y permite abrir Juntos para actualizar. No representa volumen ni garantiza reproducción simultánea.

Las descripciones históricas de tres posiciones que siguen corresponden a versiones anteriores.

En 0.5.12 las posiciones vacías indican «Abrir configuración de Bluetooth»: tocar la fila o el signo + abre directamente los ajustes Bluetooth. El desplegable «Volumen por dispositivo» tiene un encabezado compacto sin fondo de botón y empieza cerrado; tocarlo muestra u oculta los controles.

## Cambios de conexión y volumen (0.5.11)

La marca de la lista principal ahora indica conexión Bluetooth, no selección ni reproducción. Todos los equipos conectados pueden mostrarla. Las posiciones vacías muestran «Conectar nuevo dispositivo» y abren los ajustes Bluetooth al tocarlas. Al desconectarse, se elimina la identidad y el nombre de esa posición, también en el modelo compartido con la notificación. Las marcas de selección de sonido de la notificación siguen dependiendo de la confirmación de Android.

«Volumen por dispositivo» comienza contraído y muestra las barras al pulsar su título. Dentro están los botones Activar/Silenciar cuando Android permite controlar la salida. La actualización de conexiones conserva el estado desplegado mientras permanece abierta la pantalla. Esto no habilita reproducción simultánea en teléfonos que no ofrecen esa capacidad a la app. Las referencias posteriores a marcas de sonido en la pantalla principal describen el comportamiento anterior a 0.5.11.

En 0.5.10, el botón de tema se encuentra en la esquina superior derecha: 🌙 cambia a oscuro y ☀️ a claro. Conserva la preferencia y tiene una descripción accesible. La guía de volumen incluye la alternativa de Xiaomi en Bluetooth → ajustes del equipo → Audio, si está disponible, y distingue apagar Volumen absoluto de activar Inhabilitar volumen absoluto.

## Guía de ajustes Bluetooth (0.5.9)

En **Configuración → Ajustar volumen Bluetooth** hay pasos para habilitar opciones de desarrollador, inhabilitar volumen absoluto y reconectar los equipos, además de información sobre audio dual del fabricante. Cada paso pide confirmación antes de abrir Ajustes y permite cancelar. No aparece automáticamente ni abre el selector multimedia. Si el fabricante bloquea el acceso directo, se explica cómo buscar el ajuste manualmente.

La confirmación autoriza abrir la pantalla, no concede permisos especiales: el usuario debe cambiar el interruptor en el sistema. Juntos no modifica ni afirma verificar el volumen absoluto. Separar el volumen físico del accesorio no garantiza reproducción simultánea ni barras independientes en la app. La app ya usa MediaRouter2 y conserva sus límites de control.

## Pantalla sencilla, modo claro y oscuro

La pantalla muestra tres posiciones para equipos Bluetooth, con filas numeradas, icono, nombre y selección de sonido. Hasta que se conecte un equipo, las tres barras permanecen grises y en 0 %. Debajo hay tres barras verticales juntas, **Audio 1–3**, que corresponden a esas filas. Las posiciones se comparten con la notificación y se conservan por identificador exacto de ruta al reordenarse las salidas. Una posición vacía o desconectada aparece gris y en 0 %. Si desaparece un equipo, su posición queda libre para la próxima conexión. Con más de tres rutas se priorizan las seleccionadas; el selector del sistema permite consultar todas.

Las barras disponibles muestran porcentaje: azul hasta 50 %, amarillo hasta 75 % y rojo por encima de 75 %. No hay barra general inferior. Las barras sin control individual permanecen grises y muestran un guion cuando el nivel físico es desconocido. Una salida conocida y no seleccionada muestra 0 %, sin modificar el volumen de otras salidas. La pantalla completa se desplaza para admitir letra grande y pantallas pequeñas. Configuración y Vincular dispositivo están juntas, y se conservan ayuda y sonido de prueba.

Cuando hay una única salida cuyo volumen se puede controlar, quitar la marca guarda su nivel y solicita volumen cero, sin desconectarla del Bluetooth. La marca se quita solo al observar el cero real. Volver a marcar solicita el nivel anterior y espera confirmación; un nivel anterior de cero se conserva como cero. La memoria usa el identificador exacto de ruta y una escala relativa, y se comparte entre pantalla y notificación. Una solicitud rechazada no inventa una marca, y un cambio físico de volumen puede quitar el silencio guardado. No se silencia una sesión de grupo como si fuera un dispositivo individual. Durante llamadas no se modifica el volumen multimedia desde estos controles.

**Limitación pendiente:** la API pública de la app no permite asignar volúmenes independientes a dos o tres miembros Bluetooth de una sesión de grupo. `RoutingController.setVolume()` modifica el volumen de la sesión. `MediaRouter2.setRouteVolume()` requiere un router proxy y permisos especiales que esta app no tiene. En esos casos la casilla queda deshabilitada y tocar la fila solo muestra un mensaje dentro de la app. No se abre ningún panel ni ajuste al tocar dispositivos. No se promete independencia ni restauración por miembro cuando el sistema no ofrece ese control.


Toca **🌙 / ☀️** en la esquina superior derecha para cambiar el tema. La elección se guarda. La primera apertura sigue el aspecto del sistema. Los equipos guardados de las versiones anteriores se conservan en **Configuración → Equipos guardados**; no ocupan la lista de conexiones por el solo hecho de estar guardados.

**Las marcas no son una prueba de que un equipo esté sonando o de que los demás estén silenciados.** Se consultan las rutas del controlador y, desde Android 13, las salidas previstas para atributos multimedia mediante getAudioDevicesForAttributes. Las identidades Bluetooth se comparan por dirección exacta; un nombre nunca autoriza un cambio de ruta ni confirma selección. Cuando hay varias conexiones Bluetooth, una ruta del sistema sin control individual se muestra sin confirmar y no ofrece un volumen como si fuera exclusivo. Si el sistema publica la identidad física de una salida multimedia prevista, esa fila puede mostrar el chulito, aunque el control individual siga sin estar disponible. Una fila sin confirmación muestra un guion. Sin control directo o sin equipo conectado, la casilla está deshabilitada. Tocar una fila no abre pantallas externas. Se escuchan los cambios de equipos y reproducción para actualizar la consulta. El control de volumen local puede afectar todas las salidas que el fabricante duplique: no es volumen independiente por audífono.

## Aviso en la barra del celular

Permite **Notificaciones** cuando Android lo solicite. La app puede mantener un servicio visible de tipo `connectedDevice` para observar las conexiones y rutas, sin buscar equipos ni capturar audio en segundo plano. El aviso mantiene el icono de audífonos mientras el servicio está activo. Contraído muestra **Juntos activo** y una indicación para desplegarlo. Al expandirlo muestra las mismas tres posiciones que la pantalla, con nombre, icono, estado de selección y volumen. Los botones **− / +** ajustan el nivel de la salida cuando Android lo permite; la barra es un indicador, porque RemoteViews no admite SeekBar. Sin control individual se muestra un guion y se ocultan esos botones. Las posiciones desconectadas y desactivadas muestran 0 %. La casilla comparte las reglas de selección, silencio y recuperación de volumen de la pantalla principal; la selección solo cambia cuando Android la confirma. **Seleccionado** no garantiza reproducción audible. Las acciones identifican cada ruta de forma estable para evitar que una notificación antigua cambie otro equipo tras reordenarse la lista. Incluye las acciones **Conectar dispositivo** (ajustes Bluetooth directamente) y **Ocultar aviso**. También puedes activarlo o desactivarlo desde **Configuración**.

La marca ✓ refleja una ruta seleccionada o una salida física prevista con identidad confirmada; no garantiza exclusividad ni reproducción audible. La notificación usa las mismas casillas cuadradas de la app, con un área de 56 × 40 dp; también se puede tocar el nombre, el icono o el estado de cada equipo. Cuando Android permite añadir o quitar esa ruta, la solicitud se hace desde el aviso sin abrir la app y se comprueba el resultado. Sin control directo, tocar el nombre, icono o estado solo muestra un mensaje en la notificación. Las casillas deshabilitadas y los avisos no abren pantallas externas. Durante llamadas se informa en el aviso que el audio se elige desde la llamada. No se infiere una marca por coincidencia de nombres, no se inventa batería y no se confunden conexiones con reproducción. El aspecto de la notificación sigue el sistema, independientemente del tema elegido dentro de la app. El diseño es común; reproducir en dos o tres salidas sigue dependiendo de cada teléfono.

El aviso se actualiza por eventos de Bluetooth y del controlador multimedia, cambios de la memoria de volumen compartida y avisos de cambios de ajustes del sistema, sin sondeos continuos ni reinicio automático al arrancar el celular. Si el usuario lo oculta, se detiene el servicio y sus observadores. El sistema o el usuario pueden detener el servicio. La app funciona sin el aviso si el permiso se deniega; Configuración permite volver a habilitarlo, incluso cuando se necesita abrir los permisos del sistema.

Referencias: [notificaciones personalizadas](https://developer.android.com/develop/ui/views/notifications/custom-notification), [permiso de notificaciones](https://developer.android.com/develop/ui/views/notifications/notification-permission), [servicio connectedDevice](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device).

## Conectar un dispositivo nuevo

**Vincular dispositivo** abre directamente `Settings.ACTION_BLUETOOTH_SETTINGS`, sin búsqueda ni cuadro intermedio dentro de Juntos. La acción **Conectar dispositivo** de la notificación abre los mismos ajustes directamente. Al volver a la app, se consultan las conexiones de audio existentes.

Desde 0.5.8 se retiraron el botón Elegir salida de audio, el panel de contenido multimedia y las aperturas de ese panel al tocar filas o casillas. Las solicitudes directas de silencio, selección y volumen se conservan cuando Android las permite. Las salidas sin control individual muestran un mensaje, sin abrir otras pantallas.


## Actualizar la app instalada

Descarga el nuevo APK en el celular, abre el archivo y confirma **Actualizar** en Android. Conserva instalada la versión anterior: la actualización sustituye su código y conserva los dispositivos guardados. Descargar el archivo por sí solo no inicia una instalación automática.

La versión 0.5.8 usa el mismo identificador `com.karen.juntos`, el certificado de las versiones anteriores y un `versionCode` superior (14). Se preservó la clave original del prototipo en `.signing/juntos.keystore`, fuera de `.tools`, para que limpiar las herramientas no cambie la firma. La tarea `verifyUpdateSigning` comprueba su certificado antes de compilar y detiene la compilación si falta o cambia la clave. No la reemplaces ni la generes de nuevo para una actualización.

Para cada próxima entrega, aumenta `versionCode` y actualiza `versionName` en `app/build.gradle`, conservando `applicationId` y firma. Se sigue distribuyendo el APK de depuración con la clave original del prototipo. Guarda una copia privada de `.signing`: no está en Git y una nueva copia del repositorio necesitará restaurarla para compilar. No hay un servicio de descargas ni instalaciones silenciosas.

Referencias: [firma y actualización de aplicaciones](https://developer.android.com/studio/publish/app-signing), [numeración de versiones](https://developer.android.com/studio/publish/versioning).

## Funciones

- Buscar dispositivos Bluetooth Classic en un cuadro con una lista de altura limitada que se desliza de forma independiente. **Vincular dispositivo** está junto a Configuración y **Abrir ajustes de Bluetooth** se conserva dentro de **Configuración**.
- Mostrar equipos emparejados, aunque podrían estar apagados o fuera de alcance. Los perfiles públicos A2DP, manos libres y LE Audio permiten mostrar conexiones de audio confirmadas por Android. Una conexión de perfil no confirma que esté sonando música o que haya una llamada activa.
- Mostrar el tipo anunciado por Bluetooth: audífonos/manos libres, parlante, TV o pantalla, audio de carro, computador y otros. Si el equipo no informa su clase, se indica que el tipo no se pudo identificar; no se adivina por su nombre.
- Guardar hasta tres equipos como **Dispositivo 1**, **Dispositivo 2** y **Dispositivo 3**, sin duplicados. Al elegir un cuarto se ofrece reemplazar uno, sin bloquear el emparejamiento con otros equipos. Quitar o reemplazar solo modifica la lista de la app.
- Solicitar el emparejamiento mediante el diálogo de Android.
- Abrir los ajustes del teléfono para conectar las salidas de audio.
- **Comprobar sonido · 34 s** reproduce un audio incorporado con una composición instrumental original de estilo ascensor y una voz que dice «prueba» al principio y a la mitad. Permite detenerlo, muestra el tiempo restante y no necesita elegir canciones, instalar reproductores o acceder a archivos. Se detiene al salir o perder el foco de audio y no se inicia durante una llamada detectada por Android.
- Ayuda para solucionar conexiones del TV, reloj y otros equipos; instrucciones para seleccionar el dispositivo de una llamada desde Samsung o desde la propia app de llamadas.

Se retiraron **Elegir canción**, **Reproducir prueba**, el selector de archivos y las consultas de apps instaladas de la versión anterior.

## Audio en varios dispositivos

**Guardar tres equipos no significa conectar ni reproducir audio en los tres.** El emparejamiento tampoco confirma una conexión de audio. La app usa las API públicas de Android y no puede habilitar audio simultáneo en cualquier celular y cualquier par de audífonos.

En algunos Samsung Galaxy, Dual Audio permite dos salidas Bluetooth desde el panel de salida multimedia. Otras posibilidades, como compartir LE Audio/Auracast, dependen de la implementación del teléfono y de los receptores. Esta versión no implementa emisiones Auracast ni garantiza sincronización entre equipos. No hay detección automática que certifique compatibilidad con audio compartido.

El sonido incorporado usa las salidas que Android tenga activas. La selección en la app es una lista guardada y no modifica las salidas de reproducción. Para Spotify, YouTube u otra app, usa sus controles de reproducción y los controles de salida del sistema. No se captura ni se retransmite el audio de otras apps.

## Llamadas y micrófono

Esta app no realiza llamadas ni sustituye Teléfono o WhatsApp. No se implementan selectores que aparenten cambiar el micrófono o la salida de llamadas de otras apps. Android permite a las apps de comunicación elegir la salida de sus propias comunicaciones con `setCommunicationDevice`, y el sistema elige la entrada asociada; la app que controla el modo de llamada tiene prioridad. Esta API no permite prometer salida de un audífono y micrófono de otro para llamadas normales.

La sección **Audio de llamadas** explica cómo seleccionar un dispositivo desde una llamada en Samsung y cómo revisar la opción de llamadas de un reloj. No requiere permisos de micrófono, contactos ni registro de llamadas. Fuentes: [AudioManager y selección de dispositivo](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)), [cambiar el dispositivo durante una llamada en Samsung](https://www.samsung.com/sg/support/mobile-devices/how-to-manage-calls-when-multiple-bluetooth-devices-are-connected/).

Fuentes oficiales: [permisos Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [búsqueda de dispositivos](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices), [enrutamiento de audio de Android](https://source.android.com/docs/core/audio/combined-audio-routing), [Dual Audio en Samsung](https://www.samsung.com/us/support/answer/ANS10003436/).

## Abrir y compilar

1. Abre esta carpeta como proyecto en Android Studio compatible con AGP 9.0.1.
2. Configura un JDK 17 o posterior compatible con Gradle 9.1 (Java 25 también está admitido).
3. Instala Android SDK Platform 36 y Build Tools 36.0.0 mediante SDK Manager.
4. Deja que Android Studio cree `local.properties` con `sdk.dir`, o configura `ANDROID_HOME`.
5. Conecta un celular Android con depuración USB y ejecuta la app desde Android Studio.

Desde PowerShell, con Java y el SDK configurados:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. El wrapper descarga Gradle en la primera ejecución. El proyecto no incluye claves de publicación ni un APK de producción.

También se incluye `tools/setup-android.ps1` para preparar el SDK dentro de `.tools` en Windows y ejecutar compilación y lint. Recibe `-JavaHome` con la ruta de un JDK compatible. El parámetro opcional `-AcceptAndroidLicenses` acepta las licencias del SDK de Google; úsalo solo si estás de acuerdo con ellas. El script descarga las herramientas desde Google y verifica el checksum publicado. `.tools` y `local.properties` quedan excluidos de Git.

## Probar en el celular

Para 0.5.1: actualiza sin desinstalar; permite Bluetooth y notificaciones; conecta y desconecta equipos; comprueba que la lista cambia por sí sola y que el altavoz del teléfono se distingue de los equipos Bluetooth. Despliega el aviso y prueba cada casilla, la fila completa y las acciones Elegir salida / Ocultar aviso. Verifica que silenciar o quitar una salida no marque un éxito si el sistema no confirmó el cambio. Comprueba denegar permisos, concederlos desde ajustes, canal de notificaciones deshabilitado, Bluetooth apagado, regreso desde segundo plano, llamada activa y parada del servicio. Revisa los temas claro y oscuro, nombres largos, letra grande, orientación horizontal y conservación de los equipos guardados. Comprueba que Vincular dispositivo está junto a Configuración, que la barra cambia el volumen de la única salida seleccionada cuando el sistema lo permite y que no controla falsamente un miembro de un grupo. Las opciones concretas de Dual Audio necesitan comprobarse en Samsung. Las casillas con control directo actúan desde el aviso; cuando falta ese control se abre el selector del celular al tocarlas.

Si ya están vinculados, empieza por **Elegir salida de audio**, marca los equipos en el selector real y comprueba físicamente dónde suena la música. En el S23 Ultra hay que verificar altavoz del celular, cada audífono por separado y ambos a la vez, cambio de volumen y cancelación. Comprueba también abrir el selector con el sonido de 34 s iniciado, el aviso si el sistema rechaza abrirlo y la ayuda de llamadas durante una llamada. Esta parte aún no se ha probado en el teléfono del usuario.

1. Pulsa **Buscar dispositivos** y permite dispositivos cercanos. En Android 8 a 11 se requiere permiso de ubicación y tener activada la ubicación del teléfono.
2. Aparece un cuadro. Pon unos audífonos o un parlante en modo de emparejamiento y espera a que aparezca. Desliza la lista dentro del cuadro y toca el equipo.
3. Toca el equipo para guardarlo en el primer espacio libre y solicita emparejarlo si hace falta.
4. Repite con otro equipo; puedes guardar un máximo de tres. Al elegir otro cuando estén llenos, selecciona cuál reemplazar. Esto no lo desempareja ni lo desconecta del sistema.
5. Abre **Ajustes de Bluetooth** y conecta los equipos. Si tu celular admite compartir audio, activa las salidas desde el panel multimedia del teléfono.
6. Cierra el cuadro con **Listo**, regresa a la app y pulsa **Comprobar sonido · 34 s**. Comprueba físicamente qué equipos suenan, si se escucha la voz «prueba» y si hay desfase.

Comprueba también: permisos denegados y concedidos desde ajustes; Bluetooth apagado durante la búsqueda; emparejamiento cancelado; selección de tres, reemplazo y duplicados; rotación de pantalla; salida y regreso a la app; y una llamada entrante durante el sonido.

Para la versión 0.3.0: comprueba una lista larga de resultados sin perder acceso a los ajustes; deslizamiento del cuadro y actualización mientras llegan resultados; detener, volver a buscar y cerrar; equipo guardado pero apagado frente a uno conectado para audio; reloj conectado solo por datos; conectar/desconectar audífonos desde ajustes; tipo mostrado en la selección guardada; fin automático del audio a los 34 s y detención manual. No se ha confirmado la causa del bloqueo de conexión reportado por el usuario: se necesita el modelo de celular, el equipo adicional y el mensaje del sistema. Las nuevas etiquetas evitan confundir equipos guardados con conexiones de audio.

La búsqueda termina tras unos 20 segundos y se cancela al salir de la pantalla. No se realiza búsqueda en segundo plano. Los equipos BLE que no anuncian descubrimiento Classic no aparecen en esta versión. Las selecciones se guardan solo en el almacenamiento privado de la app y no se suben a ningún servidor.

## Diagnóstico de selección en Xiaomi

Se reportó en el POCO X8 Pro que suenan dos equipos aunque solo aparece una casilla marcada y no se pueden marcar las otras. Sigue pendiente resolver el control individual en ese teléfono. En Configuración → Diagnóstico de audio → Copiar informe se obtiene modelo, versión de Android, cantidades de conexiones, rutas seleccionables/deseleccionables y salidas multimedia previstas. El informe no contiene nombres ni direcciones Bluetooth y no se envía automáticamente. Con música sonando en ambos equipos, copiarlo permite distinguir información parcial de rutas de un control público no disponible.

Referencia: [salidas multimedia previstas](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)).

## Validación de esta versión

Para 0.5.4 se ejecutó tools/test-bluetooth-lifecycle.ps1 contra BluetoothAudioState de producción con un entorno Java simulado. Pasaron las regresiones de callbacks reentrantes al cerrar, cierre repetido, callbacks tardíos después de reiniciar y desconexión durante actualización. La limpieza ahora separa las referencias antes de cerrarlas y serializa callbacks en el hilo principal con una generación por sesión. Esto aborda el ConcurrentModificationException reportado al detener MainActivity y DeviceNotificationService. Se añadió barra de volumen multimedia independiente de las capacidades de selección y se retiraron las barras individuales deshabilitadas. Las casillas desconocidas no se marcan por defecto como si se hubiera activado audio. El apagado individual en el POCO sigue sin resolverse. Compilación y lint sin errores; pendiente prueba física de cierre y volumen.

Para 0.5.3 se compilaron assembleDebug y lintDebug sin errores. Se mantiene com.karen.juntos, la firma original y versionCode 9. Se añadió consulta complementaria de salidas multimedia y diagnóstico, y se evita presentar una ruta del sistema sin control individual como selección física exclusiva cuando hay varias conexiones Bluetooth. No se han ejecutado pruebas en un celular o emulador. La lectura completa del audio duplicado y el control de casillas en el POCO X8 Pro requieren el informe y una prueba física; no se consideran resueltos.

Para 0.5.2 se ejecutaron :app:assembleDebug y :app:lintDebug sin errores. Se conserva el certificado original, com.karen.juntos y versionCode 8. Se retiró el selector automático de las filas/casillas, se añadió el control de volumen multimedia local y se amplió la clasificación de iconos. No se ha probado en un teléfono o emulador: la selección simultánea y el volumen independiente no se consideran resueltos cuando el sistema no ofrece esos controles.

Para 0.5.1 se ejecutaron `:app:assembleDebug` y `:app:lintDebug` sin errores (quedan advertencias). Se comprobaron la firma original, el paquete `com.karen.juntos` y `versionCode` 7. La compatibilidad de las API de rutas se limita según versión: controlador desde API 30, tipos de ruta y selector desde API 34, rutas transferibles del controlador desde API 35. No se han hecho aún pruebas de ejecución o capturas en un celular o emulador: hay que validar físicamente el aviso persistente, sus casillas, los controles de volumen y ambos temas. No se considera verificada la selección directa de Dual Audio en el S23 Ultra.

Para 0.4.0 se compilaron el APK y lint sin errores usando el SDK 36; la llamada pública del selector está protegida por la comprobación Android 14/API 34. La firma coincide con la original y el paquete conserva `com.karen.juntos` con `versionCode` 5. El aspecto del panel, la selección de dos salidas y el cambio de salida durante reproducción todavía requieren una prueba física en Samsung.

Para 0.3.1 se ejecutaron `:app:assembleDebug` y `:app:lintDebug` sin errores. Se verificaron las firmas de los APK 0.3.0 y 0.3.1 con `apksigner`: sus certificados SHA-256 son idénticos. `aapt` confirmó el mismo paquete `com.karen.juntos` y el aumento de `versionCode` de 3 a 4. La conservación efectiva de datos al actualizar queda pendiente de comprobar en el celular, sin desinstalar la versión anterior.

Se generó el APK de depuración 0.3.0 con `:app:assembleDebug` y se ejecutó `:app:lintDebug` sin errores. El WAV incorporado se verificó como PCM mono de 22.050 Hz y 16 bits, con duración de 34 segundos. Se verifican firma y metadatos del APK con las herramientas del SDK. Lint conserva advertencias, entre ellas las de textos que aún no se han extraído a recursos para traducción. No se ha probado todavía en un teléfono físico: búsqueda, conexiones, audio compartido, voces reproducidas y comportamiento en llamadas necesitan pruebas con equipos reales.

El audio se incluye en `app/src/main/res/raw/prueba_ascensor.wav`. `tools/generate-test-audio.ps1` permite regenerarlo con Windows PowerShell 5.1 y una voz española de Windows instalada. La composición se sintetiza localmente; no se descarga música de terceros. El script no es necesario para compilar o usar la app porque el audio ya está incluido.

## Regresión del panel de volumen (0.5.7)

Después de compilar, ejecuta `powershell -File tools/test-output-controls.ps1`. Comprueba confirmación de silencio a cero, restauración compartida entre pantalla y notificación, escalas de volumen, aislamiento por identificador, nivel previo cero, solicitudes vencidas, cambio físico de volumen, tres posiciones vacías, numeración estable, desconexión y prioridad de nuevas rutas seleccionadas. Usa las clases reales compiladas con un almacén de preferencias en memoria. No sustituye una prueba física de sonido en el celular.

Verifica en el teléfono: tres barras juntas; posiciones grises antes de conectar; marca para silenciar una salida controlable; nivel recuperado desde la notificación y desde la pantalla; cambio de volumen con botones físicos; dos equipos conectados y acceso al selector cuando no hay control individual. No considerar aprobada la independencia de dos Bluetooth sin comprobar físicamente niveles distintos.

Referencias: [volumen de sesión](https://developer.android.com/reference/android/media/MediaRouter2.RoutingController#setVolume(int)) y [restricciones del volumen por ruta](https://developer.android.com/reference/android/media/MediaRouter2#setRouteVolume(android.media.MediaRoute2Info,int)).

Para 0.5.7 se completaron :app:assembleDebug y :app:lintDebug (0 errores, 25 advertencias), la verificación del certificado original y tools/test-output-controls.ps1. Las pruebas de estado pasaron. No se ejecutó en teléfono ni emulador; quedan pendientes la revisión visual en pantalla real y las pruebas físicas de sonido, volumen y selección desde la notificación.


## Cambio de navegación 0.5.8

El panel multimedia ya no se invoca desde la app ni desde la notificación. Para conectar un equipo nuevo se abren directamente los ajustes de Bluetooth. Las comprobaciones de versiones anteriores que mencionan abrir el selector multimedia describen el comportamiento anterior.

En 0.7.2, Android usa el logo proporcionado como icono adaptativo. Windows 0.1.1 lo incorpora en el ejecutable, la ventana y la bandeja. El original se conserva en assets/logo-original.png; tools/create-app-icons.ps1 genera los formatos de cada plataforma y publication/play-store-icon.png (512 × 512).
