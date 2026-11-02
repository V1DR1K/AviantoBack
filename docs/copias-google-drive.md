# Copias de seguridad en Google Drive

Avianto puede guardar cada copia de la base (clientes, motos, fichas, pagos y fotos) en tu Google Drive. No hace falta crear nada en la consola de Google: la conexión es un **script de Google Apps Script** que publicás una sola vez desde tu propia cuenta. Las copias quedan en la carpeta **«Avianto Backups»** de tu Drive.

## Qué necesitás

- La cuenta de Google donde querés guardar las copias (idealmente la del taller).
- Avianto abierto con un usuario de **Administración**.
- Internet en la PC donde está instalado el sistema.

## Vincular Google Drive (una sola vez, unos 5 minutos)

Todo se hace en **Gestión → Administración → Copias de seguridad**, paso 2, tarjeta **Google Drive**.

1. Tocá **Copiar script**. Avianto copia el código al portapapeles. El código contiene la clave de conexión: no lo compartas.
2. Entrá a [script.google.com](https://script.google.com) con la cuenta de Google donde querés guardar las copias. Tocá **Nuevo proyecto**, borrá el contenido que aparezca y pegá el código (Ctrl+V).
3. En la barra de arriba, elegí la función **probar** y tocá **Ejecutar**. Google pide permisos para ver y crear archivos en tu Drive: tocá **Revisar permisos**, elegí tu cuenta y aceptá.
   - Si aparece **«Google no verificó esta aplicación»**, tocá **Configuración avanzada** y después la opción para continuar. Es un script tuyo, que solo Avianto usa; la advertencia aparece porque no pasó por la verificación pública de Google.
4. Tocá **Implementar** → **Nueva implementación**. En el tipo, elegí **Aplicación web** y configurá:
   - **Ejecutar como:** Yo
   - **Quién tiene acceso:** Cualquier persona
5. Tocá **Implementar**. Google muestra una **URL de la aplicación web** que termina en `/exec`. Copiala.
6. Volvé a Avianto, pegá la URL en el campo **URL de la aplicación web** y tocá **Vincular y probar**.

Si todo salió bien, Avianto muestra «Conectado como <tu cuenta>» y la carpeta «Avianto Backups». Desde ese momento, la casilla **En Google Drive** del paso 1 se habilita.

> **Acceso «Cualquier persona»:** es obligatorio para que Avianto pueda llamar al script. No significa que cualquiera vea tus copias: el script solo acepta pedidos que llevan la clave de Avianto, y los archivos quedan en tu Drive.

## Cambiar o actualizar el script

Si Avianto cambia el código del script (por ejemplo, después de una actualización), hay que reemplazarlo:

1. En Avianto, paso 2, tocá **Cambiar script** y después **Copiar script**. Si el código de Avianto no cambió, la clave es la misma y no hace falta tocar nada más.
2. En script.google.com, abrí el proyecto, reemplazá todo el código por el copiado y guardá (Ctrl+S).
3. Tocá **Implementar** → **Administrar implementaciones** → el lápiz **Editar** → **Versión: Nueva versión** → **Implementar**.

La URL **no cambia** con una nueva versión. En cambio, **Nueva implementación** genera otra URL: si hacés eso, hay que pegar la nueva URL en Avianto con **Cambiar script** → **Vincular y probar**.

## Cómo se guardan y se borran las copias

- Cada copia se llama `avianto-AAAAMMDDTHHMMSSZ.sql.gz` y pesa lo mismo que la copia local (hoy, unos cientos de KB).
- En Drive se conservan las copias de los últimos días que elegiste en **Conservar las copias** (7 a 365). Siempre quedan al menos las **3 más recientes**.
- Las copias vencidas no se borran de golpe: van a la **papelera de Drive**, desde donde se pueden recuperar durante 30 días.
- El script solo toca archivos que empiezan con `avianto-` dentro de la carpeta «Avianto Backups».

## Límites de Google Apps Script

- Cada copia debe pesar **menos de 35 MB**. Si el taller crece hasta ese punto, Avianto lo informa en el resultado de la copia y hay que pasar a otra forma de guardarla.
- Cada ejecución del script puede tardar hasta **6 minutos**.
- Google aplica cuotas diarias a las cuentas personales. Con una copia por día no se acercan a ellas.

## Desvincular

En el paso 2 tocá **Desvincular cuenta**. Avianto olvida la URL y la clave, y la casilla de Drive vuelve a deshabilitarse. Las copias que ya están en Drive no se borran. El script queda en tu cuenta de Google: si ya no lo vas a usar, podés borrar el proyecto en script.google.com.

## Solución de problemas

| Mensaje | Qué hacer |
|---|---|
| **La clave no coincide con la de Avianto** | El script tiene una clave vieja. Tocá **Cambiar script** → **Copiar script**, reemplazá el código en script.google.com y publicá una **Nueva versión** (ver arriba). |
| **Google no devolvió la respuesta del script…** | Revisá la implementación: **Quién tiene acceso** debe ser **Cualquier persona**, y la URL debe terminar en `/exec` (no en `/dev`). |
| **La dirección tiene que ser la de la aplicación web…** | Pegá la URL que aparece en **Implementar → Administrar implementaciones**, la que empieza con `https://script.google.com/macros/s/` y termina en `/exec`. |
| **No se pudo conectar con el script** | Verificá internet en la PC y probá de nuevo. Si persiste, revisá que el proyecto siga existiendo en script.google.com. |
| **Google pide autorizar otra vez** | Abrí el proyecto, elegí la función **probar**, tocá **Ejecutar** y aceptá los permisos. |
| **La copia pesa más de 35 MB** | Ver «Límites» arriba. Si la copia también se guarda en esta PC, no se pierde: solo falla el envío a Drive. |
| **Las copias en Drive dejaron de aparecer** | Revisá la carpeta «Avianto Backups» de la cuenta vinculada (no la de otra cuenta de Google) y el historial de copias en Avianto. |

## Nota para quien mantiene la instalación

La copia en Drive la sube el backend con `POST` a la URL del script, con la clave en el cuerpo del pedido (no en la URL). Apps Script responde con un `302` hacia el resultado: el backend lo lee con un `GET` explícito. Solo se aceptan URLs `https://script.google.com/macros/s/…/exec`. El código del script vive en `AviantoBack/src/main/resources/drive/avianto-backup.gs` y el backend le inserta la clave en `GET /api/configuracion/backups/drive/script`.
