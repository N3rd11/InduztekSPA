# InduztekSPA · Evaluación Parcial 2

Aplicación Android académica para Induztek Ingeniería SpA: registro de pruebas eléctricas en terreno, acta inmediata y revisión posterior de protocolos. Todos los datos son ficticios. El acceso por roles es simulado y **no constituye un sistema de seguridad real**.

## Equipo

- Integrante 1: **[NOMBRE Y APELLIDO]**
- Integrante 2: **[NOMBRE Y APELLIDO]**
- Sección / equipo: **[COMPLETAR]**
- Docente: **[COMPLETAR]**
- GitHub entregado / commit final: **[URL Y COMMIT]**
- Trello o equivalente: **[URL DEL TABLERO COMPARTIDO]**

No se han inventado nombres, tareas compartidas ni historial de participación. Completar las evidencias reales siguiendo [Colaboración y entrega](docs/COLABORACION_Y_ENTREGA.md).

## Implementado para la Parcial 2

- Login simulado con diez cuentas ficticias y cuatro perfiles: Técnico, Ingeniero, Jefe técnico y Cliente. Errores por campo y selección opcional de usuario para autocompletar.
- Panel con accesos y datos locales, navegación con `NavHost`/`NavController`, rutas con identificadores, barra inferior y retorno entre pantallas. Logout separa las sesiones.
- Formularios reutilizables con íconos propios, teclado apropiado, validación central y errores por campo: correo, RUT, fechas, año, valores finitos positivos, tap entero, ambiente, duplicados y calibración vigente.
- Clientes, instalaciones, equipos e instrumentos con historial de calibración. Solo el jefe técnico administra instrumentos; el cliente consulta su propia empresa.
- Visitas, participantes, ingeniero asignado, protocolos por equipo y pruebas para los cinco tipos de equipos del caso. Mediciones previas/posteriores en mantenimiento.
- Cálculos de IP, TTR, contactos y operación del relé; vista previa y persistencia del resultado. Devanados y aislación de celda/interruptor requieren evaluación manual según lo documentado.
- Historial, filtros, comparación con mediciones anteriores, variación significativa y cinco estados. La formalización es manual; un protocolo formalizado permanece bloqueado.
- Acta de terreno por visita, constancia de recepción, texto compartible y exportación PDF sin conexión.
- Cámara y galería en **Mi perfil**. Avatar privado por usuario, actualizado también arriba a la derecha en el inicio; cancelación sin perder foto y recuperación al reabrir la app. En el primer arranque se solicitan cámara y fotos según la versión de Android. Se admite acceso a fotos seleccionadas o rechazo, sin bloquear el login. El perfil permite revisar permisos y abrir Ajustes; el selector de imágenes puede usarse sin acceso general a la galería.
- Logo proporcionado por el usuario en el acceso, encabezado del inicio e ícono de la app. El PNG original se conserva en `app/src/main/res/drawable-nodpi/logo_induztek.png` sin modificar su contenido.
- Paleta fija azul y negro, transiciones entre rutas, despliegue animado de cuentas, avisos y actualización del avatar.
- Room/SQLite para registros y borradores. Se conserva la pantalla de aislación y la tabla anterior. Room sigue en versión 2, con migración aditiva 1→2, sin migración destructiva.

## Ejecutar

1. Abrir la carpeta raíz en Android Studio y sincronizar Gradle. Se verificó con Android Studio 2026.2.1, su JBR 25, Gradle 9.6 y SDK 37.
2. Iniciar un emulador o conectar un teléfono con Android 8 / API 26 o superior y depuración USB.
3. Seleccionar la configuración **app** y pulsar Run. La aplicación funciona sin backend. El emulador se ve en **Running Devices**.
4. Abrir **Seleccionar usuario** para autocompletar un usuario o introducir una de estas cuentas. Todas usan **1234**.

| Perfil | Correo |
| --- | --- |
| Técnico | `mgodoy@induztek.ejemplo` |
| Ingeniero | `acontreras@induztek.ejemplo` |
| Jefe técnico | `respinoza@induztek.ejemplo` |
| Cliente Envasadora | `mfuentes@envpacifico.ejemplo` |

Las diez cuentas precargadas proceden del Anexo 9. No hay registro público ni recuperación de contraseña. Agregar un cliente al catálogo no crea automáticamente una cuenta de acceso.

Si el editor marca `ComposableFunction1` aunque Gradle compile, reanalizar/sincronizar el proyecto y comprobar el error real de Build. No borrar Room ni desinstalar la app para resolver errores del editor.

## Demostración funcional

1. Entrar como técnico y abrir **Visitas y actas de terreno**. Crear OT nueva en Planta Molienda, asignando ingeniero y participante.
2. Iniciar protocolo de `REL-50/51-A`, guardar temperatura y humedad.
3. Registrar operación de relé `50/51`, instrumento de inyección secundaria vigente, ajuste 5 A, corriente 5,1 A, esperado 100 ms y medido 104 ms. La vista previa indica Aprobado.
4. Guardar y pasar a **Registrado en terreno**. Volver a la visita y cerrar con receptor ficticio Marcela Fuentes Rojas, RUT `14.226.791-8`, cargo Jefa de mantenimiento.
5. Consultar/compartir el acta o guardarla como PDF. El acta dice **Pendiente de formalización**.
6. Entrar como ingeniero, abrir el historial y formalizar explícitamente. El protocolo deja de admitir cambios.
7. Entrar como Marcela y consultar únicamente Envasadora del Pacífico: TR-01 de Planta Lampa, sus protocolos y comparación histórica. Esta cuenta no ve Planta Molienda, de otra empresa.
8. Abrir **Mi perfil**, capturar con cámara, elegir de galería y cancelar una selección; comprobar que se conserva la imagen.

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
  sync/          integración opcional ya existente
```

La UI presenta estado y envía acciones. Los ViewModels gestionan sesión y edición; las reglas de entrada no dependen de Compose y las reglas de negocio se vuelven a comprobar en el repositorio. Las operaciones sobre protocolos y su cola se guardan transaccionalmente. La foto conserva la ruta `foto_<usuario>.jpg` mediante escritura atómica.

[Requisitos y decisiones del caso](docs/TRAZABILIDAD_MVP.md) · [Rúbrica y guía de defensa](docs/ENTREGA_PARCIAL2.md) · [Técnicas de las referencias del profesor](docs/REFERENCIAS_PROFESOR.md).

## Compilación y pruebas

Desde PowerShell, en la raíz (si no hay otro JDK configurado):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug :app:testValidacionUnitTest :app:lintDebug
./gradlew.bat :app:connectedValidacionAndroidTest
```

La segunda orden necesita un dispositivo/emulador. Usa la variante `validacion`, con paquete independiente `cl.induztek.app.validacion`, para que la instrumentación no utilice la instalación ni Room de la app habitual. La suite offline excluye las pruebas anotadas `RequiereBackend`. Hay pruebas de validación de campos, reglas y límites eléctricos, mantenimiento, roles, migración con datos previos, recorrido offline con reapertura, sincronización simulada, navegación, cuatro perfiles y fotografía por cámara/galería. Los resultados y límites de las pruebas nativas se registran en [evidencias](docs/evidencias/).

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Informes: `app/build/reports/tests/testValidacionUnitTest/`, `app/build/reports/androidTests/connected/validacion/` y `app/build/reports/lint-results-debug.html`.

## Backend conservado · etapa posterior

Los módulos Spring Boot y Retrofit preexistentes se mantienen como integración opcional. No se necesitan para defender la persistencia local de la Parcial 2.

```powershell
./scripts/IniciarBackend.cmd
```

El script compila e inicia `catalog-service` (8081) y `protocol-service` (8082), configura `adb reverse` y conserva datos en `backend/data`. Requiere Java 17 o superior; se verificó previamente con JBR 25. Logs en `backend/logs`. Si hay varios dispositivos, ejecutar los túneles con `adb -s <serial> reverse tcp:8081 tcp:8081` y lo mismo para 8082.

En **Sincronización y conexión → Configurar conexión** se pueden cambiar las direcciones. Por defecto usa `http://127.0.0.1:8081/` y `http://127.0.0.1:8082/` mediante ADB. HTTP solo se permite en debug; release requiere HTTPS. El servidor usa cuentas ficticias con BCrypt y HTTP Basic. La sincronización no formaliza automáticamente ni descarta cambios sin confirmación. La interfaz muestra mensajes de conexión sin excepciones ni instrucciones de desarrollo; el acceso sigue siendo simulado y los datos de muestra permanecen documentados aquí.

APIs existentes: `GET /api/session` en catálogos; `GET /api/snapshot` y `POST /api/events` en ambos servicios. Eventos con ID idempotente, autor y revisión esperada; respuestas 403 por permiso, 409 por conflicto y 422 por regla. El backend guarda documentos JSON en H2 persistente: es una implementación simplificada del MER.

Pruebas opcionales de backend, con servicios y túneles activos:

```powershell
./gradlew.bat -p backend test :catalog-service:bootJar :protocol-service:bootJar
./gradlew.bat :app:connectedValidacionAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=' '-Pandroid.testInstrumentationRunnerArguments.backendIntegration=true'
```

## Pendientes reales

- Nombres del equipo, enlaces, actividad auténtica en GitHub/Trello y entrega AVA. **No se ha publicado ningún cambio de esta etapa**.
- Prueba de cámara física y proveedores de galería en otros dispositivos; los tests automatizados usan respuestas nativas controladas.
- Gestión dinámica de personal/cuentas (RF-22 deseable), conciliación de conflictos entre dispositivos, PDF del protocolo formalizado y promedio de tiempo de formalización.
- Endurecimiento y despliegue del backend. No se afirma haber implementado todas las entidades normalizadas del MER ni seguridad de producción.

Las referencias comprimidas del profesor se conservan localmente, excluidas del repositorio; se aplicaron sus técnicas con diseño, nombres y flujos propios de Induztek.
