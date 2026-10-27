# Preparación para Google Play — Juntos Bluetooth 0.7.2

Estado: preparación técnica local. No se ha subido ni publicado en Play Console y no se garantiza aprobación. Aplicación Android `com.karen.juntos`, versionCode 29, targetSdk 36. No afecta a la versión de Windows.

## Entregables

- `app/build/outputs/bundle/release/app-release.aab`: bundle de publicación firmado, sin modo depuración.
- `app/build/outputs/apk/debug/juntos-0.7.2.apk`: APK de prueba que conserva la firma anterior para instalar sobre las versiones de pruebas.
- `publication/privacy.html`: política para alojar como página pública HTTPS. Contacto: knathaliabiscunda@gmail.com. También está disponible dentro de Configuración → Privacidad y soporte.
- Publicidad: espacio reservado, sin SDK, tráfico publicitario ni ingresos. Todavía no hay anuncios reales ni identificador publicitario. Antes de incorporar AdMob se necesitan sus identificadores, integración y pruebas de consentimiento, actualización de privacidad y de las declaraciones en Play Console.

## Firma y copia de seguridad

La clave de carga de Play es independiente de la del prototipo. Los archivos privados `.signing/play-upload.p12` y `.signing/play-upload.properties` están excluidos de Git. Guarda una copia segura de ambos fuera de este equipo; el archivo properties contiene contraseñas y no se debe compartir. El archivo `.signing/play-upload-certificate.pem` solo contiene el certificado público.

La tarea `verifyPlaySigning` impide usar el certificado de depuración en release. La compilación debug conserva la identidad instalada. Si el paquete ya existe en una cuenta de Play, usa la clave de carga registrada allí; no intentes reemplazarla con esta sin seguir el proceso de Play Console. Para una app nueva, configura Play App Signing. La app instalada desde Play tendrá la firma de publicación y puede no instalarse encima del prototipo; conserva lo necesario antes de desinstalar la versión de prueba.

Comando local, con JAVA_HOME y ANDROID_HOME configurados como en el README:

```powershell
.\gradlew.bat :app:bundleRelease :app:lintRelease --offline --no-daemon
```

## Pasos pendientes en Play Console

1. Crear/verificar la cuenta y crear la aplicación con el paquete definitivo. Subir primero el AAB a una pista de prueba interna y completar los requisitos de prueba aplicables a la cuenta.
2. Publicar `privacy.html` en una URL HTTPS pública y estable, accesible sin iniciar sesión. Registrar esa URL en Play Console. El archivo local por sí solo no cumple el requisito de URL pública.
3. Completar ficha, correo de soporte, categoría, clasificación de contenido, audiencia, acceso a la app, publicidad y Seguridad de los datos conforme al comportamiento real del artefacto enviado. No hay cuentas ni inicio de sesión; los equipos físicos son necesarios para probar los controles Bluetooth. En esta versión no existe SDK publicitario ni envío automático de datos. El procesamiento local y el contacto voluntario por correo están explicados en la política. Revisar de nuevo al integrar cualquier SDK.
4. Adjuntar icono de tienda, gráfico destacado y capturas reales de la versión enviada. Describir solo funciones verificadas: hasta dos equipos visibles, controles cuando Android los expone y widget de batería cuando el fabricante comunica el nivel. No prometer reproducción simultánea universal ni volumen independiente en dispositivos incompatibles.
5. Declarar el servicio `connectedDevice` en el apartado correspondiente y aportar el vídeo exigido de la función real. Ahora el usuario lo activa expresamente desde Configuración → Mostrar aviso en la barra y puede detenerlo con Ocultar aviso. Esta función observa conexiones y ofrece controles de rutas compatibles; no afirmar que transfiere música continuamente. La aceptación de este uso queda sujeta a la evaluación de Google; si no cumple su caso permitido habrá que rediseñar esa función antes de publicar.
6. Probar permisos denegados, reconexión, controles reales, widgets, tema, tamaños de letra, sonido de prueba y servicio en segundo plano en teléfonos físicos. Revisar el informe previo al lanzamiento de Play. Confirmar derechos sobre el audio de prueba y todos los recursos distribuidos.

## Texto sugerido para la ficha

Nombre: Juntos Bluetooth

Descripción breve: Consulta dos equipos Bluetooth, sus controles disponibles y widgets de batería.

Descripción: Juntos Bluetooth reúne hasta dos dispositivos en una pantalla sencilla. Abre los ajustes Bluetooth, consulta las conexiones, ajusta el volumen cuando Android permite controlarlo y utiliza un widget compacto con batería si el equipo comparte ese dato. Incluye tema claro y oscuro, sonido de prueba, diagnóstico y controles opcionales en la notificación. La selección de salidas y la reproducción simultánea dependen del teléfono, del sistema y de los accesorios; la app no amplía los límites Bluetooth del fabricante. Esta versión incluye un espacio reservado para publicidad, sin anuncios activos.

## Fuentes oficiales revisadas

- API de destino: https://support.google.com/googleplay/android-developer/answer/11926878
- Firma: https://developer.android.com/studio/publish/app-signing
- Privacidad y Seguridad de los datos: https://support.google.com/googleplay/android-developer/answer/10144311
- Servicios en primer plano: https://support.google.com/googleplay/android-developer/answer/13392821

Revisadas el 26 de octubre de 2026. Los requisitos y formularios que muestre la cuenta de Play Console deben comprobarse al realizar el envío.
