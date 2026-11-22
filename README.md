# ⚡ InduztekSPA

InduztekSPA es una aplicación móvil desarrollada en Android Studio para mejorar el registro de pruebas eléctricas que realiza Induztek Ingeniería SpA en terreno.

El proyecto busca reemplazar los registros en papel por un sistema digital donde los técnicos puedan ingresar mediciones, consultar equipos y mantener un historial de los trabajos realizados.

El objetivo es facilitar el trabajo de técnicos e ingenieros, organizando la información de cada prueba y permitiendo consultar los protocolos de los equipos.

Aplicación Android académica para Induztek Ingeniería SpA. Registra pruebas eléctricas en terreno, emite un acta y permite revisar los protocolos. Usa datos ficticios y valida los permisos de las cuentas conectadas en el servidor.

La aplicación usa Kotlin, Jetpack Compose y Material 3, con arquitectura MVVM y almacenamiento local Room sobre SQLite.

## Entrega Parcial 4 · DSY1105

| Entregable | Archivo |
| --- | --- |
| Aplicación Android | [APK firmado](Docs/entrega/InduztekSPA-Parcial4.apk) · [Captura en el emulador](Docs/evidencias/apk-acta-remota.png) |
| Firma digital | [Verificación del certificado](Docs/evidencias/firma-apk.txt) |
| Pauta y demostración | [Guía de entrega Parcial 4](Docs/ENTREGA_PARCIAL4.md) |
| Pruebas | [Evidencia del emulador](Docs/evidencias/pruebas-emulador.txt) · [Cobertura JaCoCo](Docs/evidencias/cobertura-logica.txt) |

La app usa cuatro microservicios Spring Boot. Para abrir una sesión, Android verifica la cuenta con Usuarios y confirma la misma identidad en Catálogos, Protocolos y Actas. Tras iniciar sesión, Room conserva los borradores y la cola de cambios si se pierde la conexión. En una visita abierta se puede consultar el clima referencial de su comuna mediante Open-Meteo.

### Funcionalidades

- Registrar visitas, protocolos y mediciones en terreno con Room y sincronizarlos con el backend.
- Revisar y formalizar protocolos según el rol. El portal Cliente muestra los trabajos de su empresa, las pruebas realizadas, el estado de formalización y las actas publicadas.
- Solicitar una cuenta cliente y gestionar su aprobación, empresa asignada y permisos desde Jefe técnico.
- Consultar el clima de la comuna o de la ubicación aproximada del teléfono mediante Open-Meteo y Retrofit.
- Usar cámara, selector de fotos, ubicación aproximada y notificaciones de sincronización en Android. Las fotos de perfil se sincronizan por cuenta.

| Componente | Endpoints |
| --- | --- |
| Catálogos, puerto 8081 | `GET /api/servicios/catalogos`, `GET /api/session`, `GET /api/snapshot`, `GET /api/clientes/{id}`, `POST /api/events` |
| Protocolos, puerto 8082 | `GET /api/servicios/protocolos`, `GET /api/snapshot`, `POST /api/events`, `GET /api/actas/{visitaId}` |
| Usuarios, puerto 8083 (módulo `access-service`) | `GET /api/session`, `POST /api/ingresos`, `POST /api/registro`, `GET /api/perfil/foto`, `PUT /api/perfil/foto`, `GET /api/personal`, `GET /api/cuentas`, `POST /api/cuentas`, `PATCH /api/cuentas/{id}/permisos` |
| Actas, puerto 8084 | `GET /api/servicios/actas`, `GET /api/actas/{visitaId}` |
| Open-Meteo por HTTPS | `GET /v1/search` en `geocoding-api.open-meteo.com`, `GET /v1/forecast` en `api.open-meteo.com` |

### Registro y activación de una cuenta cliente

1. En **Registrarse**, completa el nombre, correo, RUT y contraseña. Usuarios guarda la solicitud pendiente.
2. Ingresa como jefe técnico y abre **Administrar cuentas**. Verifica la identidad y la empresa de la persona antes de elegir una empresa existente y pulsar **Aprobar y activar**. Usuarios verifica la empresa en Catálogos. El alta directa desde esta pantalla también sigue disponible.
3. Ingresa con la cuenta nueva. Su perfil y sus catálogos quedan limitados a la empresa asignada. El jefe técnico puede desactivarla desde la misma pantalla.

Usuarios guarda las cuentas con hashes BCrypt, los ingresos confirmados y la foto JPEG de cada cuenta. Los servicios consultan a Usuarios para validar credenciales activas. HTTP Basic se usa con HTTPS en un despliegue remoto. La variante académica admite HTTP en loopback para ADB. La app conserva la credencial remota en memoria durante la sesión.

`POST /api/registro` recibe `nombre`, `correo`, `rut` y `clave`. Responde `201` si guarda la solicitud, `409` por duplicado o `422` por datos inválidos. La app aprueba la cuenta con `PATCH /api/cuentas/{id}/permisos` y el cuerpo `{"rol":"CLIENTE","activo":true,"clienteId":"id-de-la-empresa"}`. Para desactivarla, conserva el rol y la empresa y envía `activo` en `false`.

### Administración y cambio de cuenta

El jefe técnico puede crear cuentas de los cuatro roles en **Administrar cuentas** y editar el rol, la empresa y el estado de otras cuentas. Su propio acceso se protege contra cambios desde esa sesión. Los clientes necesitan una empresa. Los técnicos e ingenieros activos aparecen en el selector de personal de la visita. Protocolos confirma esos perfiles con Usuarios al sincronizar la visita.

Para presentar las cuatro vistas, entra con una cuenta de cada rol usando **Cambiar cuenta → Agregar otra cuenta**. Luego selecciona las cuentas autenticadas desde **Cambiar cuenta**. La app vuelve a verificar cada sesión en los cuatro servicios y conserva las credenciales únicamente en memoria hasta que se cierra el proceso. Asigna una contraseña propia a cada cuenta al crearla.

Consulta el [diagrama de servicios y esquemas](Docs/ENTREGA_PARCIAL4.md#microservicios-propios) para seguir las peticiones del backend.

Desde la raíz en Windows:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./scripts/IniciarBackend.cmd
./gradlew.bat :app:createValidacionUnitTestCoverageReport
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/GenerarApkEntrega.ps1
```

El [APK firmado](Docs/entrega/InduztekSPA-Parcial4.apk) se instala como `cl.induztek.app.entrega` y permite probar los cuatro servicios locales mediante ADB. La llave académica `.jks` queda en `%LOCALAPPDATA%\InduztekSPA\FirmaParcial4`. Consulta la [guía de entrega](Docs/ENTREGA_PARCIAL4.md) para reproducir la compilación, revisar la cobertura y preparar la evidencia grupal.

### Base MySQL en Laragon

Ejecuta [IniciarBackendLaragon.bat](IniciarBackendLaragon.bat) con MySQL de Laragon en `127.0.0.1:3306`. Si MySQL está apagado, el BAT abre Laragon y espera a que pulses **Start All**. Durante la primera configuración crea los esquemas y copia las bases H2 que encuentre con los servicios de esta copia detenidos.

El respaldo de H2 se guarda en `%LOCALAPPDATA%\InduztekSPA\Laragon`. Windows protege allí la credencial del usuario MySQL de los servicios. Si root tiene clave, ejecuta el BAT con `-PedirClaveRoot`. Si Laragon está fuera de `C:\laragon`, indica su carpeta con `-RutaLaragon "D:\Laragon"`.

### Arranque del backend en otro PC con Windows

Copia la carpeta del proyecto. Para compilar el backend necesitas **JDK 17 o posterior** y conexión para descargar Gradle y sus dependencias en la primera ejecución. Los BAT buscan Java en `JAVA_HOME`, Android Studio y `PATH`. Si ya tienes los cuatro JAR en `backend/*/build/libs`, puedes usar `-SinCompilar`.

| BAT | Función |
| --- | --- |
| [IniciarBackendLocal.bat](IniciarBackendLocal.bat) | Inicia los cuatro servicios con H2 persistente en `backend/data`. |
| [IniciarBackendLaragon.bat](IniciarBackendLaragon.bat) | Configura MySQL de Laragon y arranca los cuatro servicios. |
| [IniciarMicroservicios.bat](IniciarMicroservicios.bat) | Usa Laragon si este usuario de Windows ya lo configuró. En las demás instalaciones arranca con H2. |
| [ConfigurarJefeTecnico.bat](ConfigurarJefeTecnico.bat) | Crea el acceso inicial o cambia la clave de `respinoza@induztek.ejemplo`. El BAT pide la clave al ejecutarse. |
| [DetenerMicroservicios.bat](DetenerMicroservicios.bat) | Detiene los servicios de esta copia antes de cambiar de base o apagar el backend. |

El arranque normal usa los puertos **8081–8084**. Si están ocupados, usa `-PuertoBase 18081` al arrancar y repite ese argumento para detener los servicios o configurar el jefe. Con Android SDK Platform-Tools y un emulador o teléfono USB autorizado, el script ajusta ADB reverse para los puertos de la app. En una base nueva, ejecuta **ConfigurarJefeTecnico.bat** antes de probar el ingreso. Si usaste **IniciarBackendLocal.bat** en un PC que también tiene Laragon configurado, agrega `-Base H2` al configurar la clave.

Android sigue hablando con Spring Boot. Un ingreso validado registra una fila en `ingresos` y una foto de perfil sincronizada queda en `fotos_perfil`. Las fotos guardadas en el dispositivo se suben al iniciar sesión con su cuenta. Las visitas, protocolos y mediciones nuevas se envían con la cola de sincronización de Room. Las mediciones antiguas del formulario independiente se conservan localmente porque esos registros carecen de cuenta asociada.

## Equipo

- Integrante identificado en el historial del proyecto: **Francisco Gutierrez**.
- Datos de entrega por confirmar: demás integrantes, sección o número de equipo y docente.
- Repositorio público: [N3rd11/InduztekSPA](https://github.com/N3rd11/InduztekSPA).
- Trello: [InduztekSPA](https://trello.com/b/gePUFvO5/induztekspa)

Completar los datos del equipo y vincular los commits y tarjetas reales en la [guía de entrega](Docs/ENTREGA_PARCIAL4.md).

## Implementado para la Parcial 2

- Cuatro perfiles con permisos diferenciados. Técnico, Ingeniero, Jefe técnico y Cliente.
- Panel con accesos y datos locales, navegación con `NavHost`/`NavController`, rutas con identificadores, barra inferior y retorno entre pantallas. Logout separa las sesiones.
- Formularios reutilizables con íconos propios, teclado apropiado, validación central y errores por campo: correo, RUT, fechas, año, valores finitos positivos, tap entero, ambiente, duplicados y calibración vigente.
- Clientes, instalaciones, equipos e instrumentos con historial de calibración. Solo el jefe técnico administra instrumentos; el cliente consulta su propia empresa.
- Visitas, participantes, ingeniero asignado, protocolos por equipo y pruebas para los cinco tipos de equipos del caso. Mediciones previas/posteriores en mantenimiento.
- Cálculos de IP, TTR, contactos y operación del relé; vista previa y persistencia del resultado. Devanados y aislación de celda/interruptor requieren evaluación manual según lo documentado.
- Historial, filtros, comparación con mediciones anteriores, variación significativa y cinco estados. La formalización es manual; un protocolo formalizado permanece bloqueado.
- Acta de terreno por visita, constancia de recepción, texto compartible y exportación PDF sin conexión.
- Cámara y galería en **Mi perfil**. Avatar privado por usuario, actualizado también arriba a la derecha en el inicio; cancelación sin perder foto y recuperación al reabrir la app. En el primer arranque se solicitan cámara y fotos según la versión de Android. Se admite acceso a fotos seleccionadas o rechazo, sin bloquear el login. El perfil permite revisar permisos y abrir Ajustes; el selector de imágenes puede usarse sin acceso general a la galería.
- Logo proporcionado por el usuario en el acceso, encabezado del inicio e ícono de la app. El PNG original se conserva en `app/src/main/res/drawable-nodpi/logo_induztek.png` sin modificar su contenido.
- Paleta fija azul y negro, transiciones entre rutas, avisos y actualización del avatar.
- Room/SQLite para registros y borradores. Se conserva la pantalla de aislación y la tabla anterior. Room sigue en versión 2, con migración aditiva 1→2, sin migración destructiva.

## Ejecutar

1. Abrir la carpeta raíz en Android Studio y sincronizar Gradle. Se verificó con Android Studio 2026.2.1, su JBR 25, Gradle 9.6 y SDK 37.
2. Iniciar un emulador o conectar un teléfono con Android 8 / API 26 o superior y depuración USB.
3. Hacer doble clic en [IniciarMicroservicios.bat](IniciarMicroservicios.bat) y pulsar Run en la configuración **app**. El script espera a que respondan los cuatro servicios y configura los túneles ADB si hay un dispositivo activo.
4. Ingresar el correo y la contraseña asignados. **Configurar conexión**, en la pantalla de acceso, permite cambiar las direcciones de los cuatro servidores.

Antes de arrancar Usuarios por primera vez, asigna la contraseña inicial del jefe técnico en la misma sesión de PowerShell. La cuenta usa el correo `respinoza@induztek.ejemplo`. La contraseña debe tener al menos ocho caracteres y un máximo de 72 bytes.

También puedes hacer doble clic en [ConfigurarJefeTecnico.bat](ConfigurarJefeTecnico.bat). Pide la clave nueva dos veces, reinicia Usuarios si está activo y comprueba que la cuenta acepte la clave.

```powershell
$env:INDUZTEK_CLAVE_JEFE1 = [Net.NetworkCredential]::new('', (Read-Host 'Contraseña del jefe técnico' -AsSecureString)).Password
./scripts/IniciarBackend.cmd
Remove-Item Env:INDUZTEK_CLAVE_JEFE1
```

Las contraseñas iniciales de los perfiles internos se configuran del mismo modo con `INDUZTEK_CLAVE_TECNICO1` para `mgodoy@induztek.ejemplo` e `INDUZTEK_CLAVE_INGENIERO1` para `acontreras@induztek.ejemplo`. Usuarios conserva el hash aunque se retire la variable. Si Usuarios ya estaba ejecutándose, reinícialo con las variables definidas para aplicar el alta inicial o rotar las contraseñas.

Si el editor marca `ComposableFunction1` aunque Gradle compile, reanalizar/sincronizar el proyecto y comprobar el error real de Build. No borrar Room ni desinstalar la app para resolver errores del editor.

## Demostración funcional

1. Entrar como técnico y abrir **Visitas y actas de terreno**. Crear OT nueva en Planta Molienda, asignando ingeniero y participante.
2. Iniciar protocolo de `REL-50/51-A`, guardar temperatura y humedad.
3. Registrar operación de relé `50/51`, instrumento de inyección secundaria vigente, ajuste 5 A, corriente 5,1 A, esperado 100 ms y medido 104 ms. La vista previa indica Aprobado.
4. Guardar y pasar a **Registrado en terreno**. Volver a la visita y cerrar con receptor ficticio Marcela Fuentes Rojas, RUT `14.226.791-8`, cargo Jefa de mantenimiento.
5. Consultar/compartir el acta o guardarla como PDF. El acta dice **Pendiente de formalización**.
6. Entrar como ingeniero, abrir el historial y formalizar explícitamente. El protocolo deja de admitir cambios.
7. Entrar con una cuenta cliente aprobada para la empresa de la visita. Desde **Trabajos**, buscar la OT, revisar las pruebas y consultar el acta sincronizada. **Mis equipos** muestra el catálogo de esa empresa.
8. Abrir **Mi perfil** para probar la cámara y el selector de fotos. En una visita abierta, pulsar **Consultar clima con mi ubicación**. En **Sincronización y conexión**, activar **Notificar al sincronizar** y comprobar el aviso de Android.

Para transformadores, interruptores, celdas y cables, registrar cada elemento aplicable o justificar **No realizada**. Debe haber al menos una prueba realizada. La evaluación manual pendiente no se presenta como aprobación automática.

## Arquitectura

```text
app/src/main/java/cl/induztek/app/
  navigation/    destinos del flujo
  ui/            pantallas y grafos de navegación
    components/  campos, avisos e íconos compartidos
    theme/       colores, tipografía y archivo AccesoScreen conservado
  viewmodel/     sesión, acceso, perfil, trabajo, formulario y registro anterior
  model/         permisos, reglas eléctricas y validaciones de entrada
  data/          Room, migración, repositorios, semillas ficticias y actas
  sync/          clientes Retrofit, configuración y sincronización
```

La UI presenta estado y envía acciones. Los ViewModels gestionan sesión y edición; las reglas de entrada no dependen de Compose y las reglas de negocio se vuelven a comprobar en el repositorio. Las operaciones sobre protocolos y su cola se guardan transaccionalmente. La foto conserva la ruta `foto_<usuario>.jpg` mediante escritura atómica.

## Compilación y pruebas

| Suite | Herramientas | Último resultado verificado |
| --- | --- | --- |
| Unitarias de Android | JUnit 4 y Robolectric | 47 aprobadas |
| Backend | JUnit 5 y Spring Boot Test | 14 aprobadas |
| Instrumentación básica | AndroidJUnitRunner, Compose UI Test y Espresso | 4 aprobadas |
| Cobertura de líneas de lógica | JaCoCo | 81,39 % (1076 de 1322 líneas) |

Desde PowerShell, en la raíz (si no hay otro JDK configurado):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug :app:testValidacionUnitTest :app:lintDebug
./gradlew.bat :app:createValidacionUnitTestCoverageReport
./gradlew.bat -p backend test
./gradlew.bat :app:connectedValidacionAndroidTest
```

La orden `connectedValidacionAndroidTest` necesita un dispositivo o emulador. Usa el paquete independiente `cl.induztek.app.validacion`. La suite básica prueba el acceso y registro sin servidor. Las pruebas anotadas `RequiereBackend` cubren navegación autenticada, sincronización, perfiles y fotografía por cámara o galería. La ejecución completa de esa suite sigue pendiente.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Informes: `app/build/reports/tests/testValidacionUnitTest/`, `app/build/reports/androidTests/connected/validacion/` y `app/build/reports/lint-results-debug.html`.

## Backend y sincronización

La app guarda los cambios en Room y los envía a los módulos Spring Boot con Retrofit al conectarse.

```powershell
./scripts/IniciarBackend.cmd
```

El script compila e inicia los cuatro servicios en los puertos 8081 a 8084, configura `adb reverse` y guarda los datos en H2 bajo `backend/data` o en MySQL de Laragon según el BAT elegido. Los servicios se ejecutan desde copias de los JAR en `backend/runtime`. Si hay varios dispositivos, selecciona uno mediante `ANDROID_SERIAL`. Los logs están en `backend/logs`.

Desde el inicio de sesión, abre **Configurar conexión** para editar las cuatro direcciones. Por defecto se usa `127.0.0.1` con ADB. La variante `entrega` permite HTTP en loopback y usa HTTPS para servicios externos. La sincronización conserva los cambios en conflicto y la formalización requiere una acción del ingeniero o jefe técnico.

Catálogos y Protocolos reciben eventos con ID idempotente, autor y revisión esperada. Responden con 403 por permiso, 409 por conflicto y 422 por regla. Catálogos, Protocolos y Usuarios usan bases H2 o esquemas MySQL independientes. Actas consulta a Protocolos para obtener la copia sincronizada.

Para la instrumentación de integración usa una base de prueba e inicia Usuarios con `INDUZTEK_DEMO=true`. Este modo carga las cuentas ficticias que usa el runner. En la ejecución normal queda desactivado.

```powershell
./gradlew.bat -p backend test bootJar
./gradlew.bat :app:connectedValidacionAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=' '-Pandroid.testInstrumentationRunnerArguments.backendIntegration=true'
```

## Pendientes reales

- Confirmar los datos del equipo, vincular las contribuciones reales en GitHub y Trello y completar la entrega en AVA.
- Prueba de cámara física y proveedores de galería en otros dispositivos; los tests automatizados usan respuestas nativas controladas.
- Conciliación manual de conflictos entre dispositivos, PDF del protocolo formalizado y promedio de tiempo de formalización.
- Endurecimiento y despliegue del backend. No se afirma haber implementado todas las entidades normalizadas del MER ni seguridad de producción.

Las referencias comprimidas del profesor se conservan localmente, excluidas del repositorio; se aplicaron sus técnicas con diseño, nombres y flujos propios de Induztek.
