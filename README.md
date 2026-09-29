# StorageCleaner

**Desarrollado por Enmanuel Gil · OptiSuite** — Android 8.0+ · gratis · sin anuncios · código abierto · **sin permiso de Internet**

Qué ocupa tu almacenamiento, con los **mismos datos que Ajustes > Almacenamiento**, y qué puedes borrar sin perder nada: APK de apps ya instaladas, archivos duplicados, archivos grandes y descargas antiguas.

**Descarga:** [StorageCleaner.apk](https://github.com/EnMaNueL-G/StorageCleaner/releases/latest/download/StorageCleaner.apk)

> ⚠️ **Si tienes la 1.0.0 instalada, desinstálala antes.** La 1.0.0 se publicó firmada con una clave de pruebas; desde la 1.1.0 se firma con la clave de publicación de OptiSuite y Android no deja instalar una encima de la otra.

---

## Qué hace

### 📊 Resumen
Espacio usado, libre y total, y **en qué se va**: apps y sus datos, caché, fotos, vídeos, audio, otros archivos y sistema. Las cifras salen del mismo servicio de Android que usa Ajustes (StorageStatsManager) y se suman app por app, igual que Ajustes.

### 📱 Apps
Cuánto ocupa cada app (instalación, datos y caché), ordenado por tamaño o por caché. Al tocar una app se abre su ficha para **borrar su caché** o sus datos.

### 🗂️ Archivos
Busca en el almacenamiento y agrupa:
- **Instaladores APK**: dice si la app ya está instalada (con esa versión o una más nueva). Esos vienen marcados: borrarlos no quita la app.
- **Duplicados exactos** (mismo contenido, comparado con SHA-256): se conserva siempre una copia (la de la cámara o la más antigua) y se marcan las demás.
- **Archivos grandes** (100 MB o más), para que los revises tú.
- **Descargas antiguas** (más de 90 días sin tocar).

Nada se borra sin que lo marques y lo confirmes. El borrado es definitivo (no hay papelera) y la app te avisa si has marcado **todas** las copias de un duplicado.

### 🧹 Caché
- **Android 11+ con acceso a archivos:** abre la limpieza de caché del propio Android, que vacía la caché que las apps guardan en el almacenamiento compartido. La app mide y te dice cuánto se liberó de verdad.
- **Sin ese permiso:** pide a Android el máximo espacio posible; Android borra caché solo si lo considera necesario, y la app te dice si no se ha borrado nada.

---

## Límites honestos
- **Ninguna app puede vaciar la caché interna de las demás** en Android 6 o superior sin root. La 1.0.0 lo intentaba, fallaba en silencio y aun así decía «caché limpiada». La caché interna de cada app se borra desde su ficha (pestaña Apps).
- Borrar caché **no acelera el móvil**: las apps la vuelven a crear. Sirve para ganar espacio cuando te falta.
- La carpeta `Android/data` de otras apps no se revisa: Android no deja entrar ahí.

## Permisos
| Permiso | Para qué |
|---|---|
| Acceso de uso (lo concedes tú) | Reparto del almacenamiento y tamaño de cada app |
| Acceso a todos los archivos (Android 11+, lo concedes tú) | Buscar APK, duplicados y archivos grandes; limpieza de caché de Android |
| Almacenamiento (Android 8-10) | Lo mismo en versiones antiguas |
| Ver apps instaladas | Lista de apps y saber si un APK ya está instalado |

Sin Internet, sin notificaciones y sin nada funcionando en segundo plano.

---

## Cambios

### v1.1.0
- **Reparto real** del almacenamiento (apps, caché, fotos, vídeos, audio, otros, sistema) con unidades como Ajustes (1 GB = 1000 MB).
- Nueva pestaña **Apps** con tamaño y caché de cada app. La 1.0.0 consultaba siempre el volumen equivocado.
- Nueva pestaña **Archivos**: APK sobrantes (sabe si la app ya está instalada), duplicados exactos, archivos grandes y descargas antiguas, con borrado confirmado.
- Limpieza de caché **honesta**: usa lo que Android permite y dice cuánto se liberó de verdad (la 1.0.0 decía «limpiada» aunque no hiciera nada).
- Quitados los permisos que no se usaban (matar procesos, servicio en primer plano, arranque, notificaciones, limpiar caché de sistema). APK de 15,9 MB a menos de 1 MB.
- Firmada con la clave de publicación (hay que desinstalar la 1.0.0).

### v1.0.0
- Versión inicial.

---

## Compilar
Android Studio (JDK 17) · `gradlew assembleRelease` · pruebas: `gradlew testReleaseUnitTest`.

## Apoya el proyecto
- **Binance Pay ID:** `1165745950`
- **USDT (BSC · BEP-20):** `0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08`

© 2026 Enmanuel Gil · OptiSuite — [github.com/EnMaNueL-G](https://github.com/EnMaNueL-G)
