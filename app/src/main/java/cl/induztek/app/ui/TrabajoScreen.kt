package cl.induztek.app.ui

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import cl.induztek.app.ui.components.*
import cl.induztek.app.viewmodel.FormularioEstado
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.induztek.app.data.*
import cl.induztek.app.model.*
import cl.induztek.app.viewmodel.TrabajoViewModel
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

@Composable
private fun formulario(vm: TrabajoViewModel, clave: String, inicial: Map<String, String> = emptyMap()): FormularioEstado =
    vm.formulario(clave, inicial)

@Composable
private fun Campo(f: FormularioEstado, clave: String, titulo: String, numero: Boolean = false, multilinea: Boolean = false, enabled: Boolean = true) {
    CampoInduztek(f[clave], { f.cambiar(clave, it) }, titulo, f.errores[clave],
        simbolo = when(clave) { "correo" -> Simbolo.CORREO; "lectura1", "lectura10", "tension", "resistencia", "corriente" -> Simbolo.ENERGIA; else -> Simbolo.CAMPO },
        teclado = when { numero -> KeyboardType.Decimal; clave == "correo" -> KeyboardType.Email; clave == "telefono" -> KeyboardType.Phone; else -> KeyboardType.Text },
        multilinea = multilinea, enabled = enabled)
}
@Composable
private fun Selector(titulo: String, valor: String, opciones: List<Pair<String, String>>, enabled: Boolean = true, error: String? = null, cambiar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { abierto = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            IconoInduztek(if (error == null) Simbolo.CATALOGO else Simbolo.ERROR)
            Spacer(Modifier.width(8.dp))
            Text("$titulo: ${opciones.find { it.first == valor }?.second ?: "Seleccionar"} ▾")
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, texto) -> DropdownMenuItem(text = { Text(texto) }, onClick = { cambiar(id); abierto = false }) }
            if (opciones.isEmpty()) DropdownMenuItem(text = { Text("No hay opciones disponibles") }, enabled = false, onClick = {})
        }
    }
}

@Composable
private fun Elegir(f: FormularioEstado, clave: String, titulo: String, opciones: List<Pair<String, String>>, enabled: Boolean = true) {
    Selector(titulo, f[clave], opciones, enabled, f.errores[clave]) { f.cambiar(clave, it) }
    f.errores[clave]?.let { Text("⚠ $titulo: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun Accion(vm: TrabajoViewModel, texto: String, click: () -> Unit) {
    Button(onClick = click, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) { Text(texto) }
}

@Composable
private fun Ficha(titulo: String, detalle: String, textoBoton: String = "Abrir", click: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detalle)
            if (click != null) TextButton(onClick = click) { Text(textoBoton) }
        }
    }
}

@Composable
fun PantallaTrabajo(vm: TrabajoViewModel, seccion: String) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    fun ir(destino: String, id: String = "") {
        nav.navigate(if (destino in setOf("visitas", "catalogos", "historial", "nuevaVisita")) destino else "$destino/${id.ifBlank { "nuevo" }}") { launchSingleTop = true }
    }
    @Composable fun Contenido(contenido: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AvisoInduztek(vm.error, error = true)
        AvisoInduztek(vm.mensaje)
        if (vm.ocupado || vm.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.cargando) { Text("Abriendo registros locales…"); return@Column }
        if (entrada?.destination?.route != null && entrada?.destination?.route != seccion) {
            TextButton(onClick = { nav.navigateUp() }) { IconoInduztek(Simbolo.VOLVER); Text("Volver a la pantalla anterior") }
        }
        NavHost(nav, startDestination = seccion, modifier = Modifier.weight(1f)) {
            composable("catalogos") { Contenido { Catalogos(vm) { destino,id -> ir(destino,id) } } }
            listOf("cliente","instalacion","equipo","instrumento").forEach { tipo ->
                composable("$tipo/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { back ->
                    val id = back.arguments?.getString("id").orEmpty().let { if (it == "nuevo") "" else it }
                    Contenido { EditorCatalogo(vm, tipo, id) { nav.navigateUp() } }
                }
            }
            composable("visitas") { Contenido { Visitas(vm, { ir("nuevaVisita") }) { ir("visita",it) } } }
            composable("nuevaVisita") { Contenido { NuevaVisita(vm) { id ->
                nav.navigate("visita/$id") { popUpTo("nuevaVisita") { inclusive = true } }
            } } }
            composable("visita/{id}") { back ->
                val id = back.arguments?.getString("id").orEmpty()
                Contenido { DetalleVisita(vm,id, { ir("protocolo",it) }, { ir("acta",id) }) }
            }
            composable("acta/{id}") { back -> Contenido {
                vm.estado.visitas.find { it.id == back.arguments?.getString("id") }?.let { ActaScreen(it, vm.usuario) }
            } }
            composable("protocolo/{id}") { back ->
                val id = back.arguments?.getString("id").orEmpty()
                Contenido { DetalleProtocolo(vm, id) { pruebaId -> nav.navigate("prueba/$id/$pruebaId") } }
            }
            composable("prueba/{id}/{pruebaId}") { back -> Contenido {
                EditorPrueba(vm, back.arguments?.getString("id").orEmpty(), back.arguments?.getString("pruebaId")) { nav.navigateUp() }
            } }
            composable("historial") { Contenido { Historial(vm) { ir("protocolo",it) } } }
        }
    }
}
@Composable
private fun Catalogos(vm: TrabajoViewModel, abrir: (String, String) -> Unit) {
    var categoria by rememberSaveable { mutableStateOf("equipo") }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var clasificacion by rememberSaveable { mutableStateOf("") }
    val s = vm.estado
    Text("Catálogos", style = MaterialTheme.typography.headlineSmall)
    Selector("Ver", categoria, listOf("cliente" to "Clientes", "instalacion" to "Instalaciones", "equipo" to "Equipos", "instrumento" to "Instrumentos")) { categoria = it }
    OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar por nombre, TAG, serie o instalación") }, modifier = Modifier.fillMaxWidth())
    if (categoria == "equipo") Selector("Clasificación", clasificacion, listOf("" to "Todas", "PRIMARIO" to "Primario", "SECUNDARIO" to "Secundario")) { clasificacion = it }
    val puedeEditar = if (categoria == "instrumento") Permisos.instrumentos(vm.usuario) else Permisos.registrar(vm.usuario)
    if (puedeEditar) Accion(vm, "Agregar ${categoria.replaceFirstChar { it.uppercase() }}") { abrir(categoria, "") }
    when (categoria) {
        "cliente" -> s.clientes.filter { "${it.nombre} ${it.rut}".contains(busqueda, true) }.forEach { c ->
            Ficha(c.nombre, "${c.rut}\n${c.giro}\n${c.contacto} · ${c.correo}", "Editar", if (puedeEditar) ({ abrir("cliente", c.id) }) else null)
        }
        "instalacion" -> s.instalaciones.filter { "${it.nombre} ${it.comuna}".contains(busqueda, true) }.forEach { i ->
            Ficha(i.nombre, "${s.clientes.find { it.id == i.clienteId }?.nombre}\n${i.ubicacion} · ${i.comuna}\n${i.observaciones}", "Editar", if (puedeEditar) ({ abrir("instalacion", i.id) }) else null)
        }
        "equipo" -> s.equipos.filter { e ->
            val i = s.instalaciones.find { it.id == e.instalacionId }
            "${e.tag} ${e.serie} ${i?.nombre}".contains(busqueda, true) && (clasificacion.isBlank() || TipoEquipo.valueOf(e.tipo).clasificacion == clasificacion)
        }.forEach { e ->
            Ficha("${e.tag} · ${TipoEquipo.valueOf(e.tipo).titulo}", "${s.instalaciones.find { it.id == e.instalacionId }?.nombre}\n${e.marca} ${e.modelo} · Serie ${e.serie}\n${e.placa}", "Editar", if (puedeEditar) ({ abrir("equipo", e.id) }) else null)
        }
        "instrumento" -> s.instrumentos.filter { "${it.nombre} ${it.serie}".contains(busqueda, true) }.forEach { i ->
            val calibraciones = s.calibraciones.filter { it.instrumentoId == i.id }
            val ultima = calibraciones.maxByOrNull { it.fecha }
            val vence = ultima?.vencimiento?.let(LocalDate::parse)
            val estado = when { vence == null -> "Sin calibración"; vence < LocalDate.now() -> "Calibración vencida"; vence <= LocalDate.now().plusDays(30) -> "Vence en 30 días o menos"; else -> "Calibración vigente" }
            Ficha(i.nombre, "${i.marca} ${i.modelo} · Serie ${i.serie}\n$estado\n${calibraciones.joinToString("\n") { "${it.fecha} → ${it.vencimiento}" }}", "Editar / agregar calibración", if (puedeEditar) ({ abrir("instrumento", i.id) }) else null)
        }
    }
}

@Composable
private fun EditorCatalogo(vm: TrabajoViewModel, tipo: String, id: String, volver: () -> Unit) {
    val s = vm.estado
    val c = s.clientes.find { it.id == id }; val i = s.instalaciones.find { it.id == id }
    val e = s.equipos.find { it.id == id }; val instrumento = s.instrumentos.find { it.id == id }
    val clave = "$tipo-${id.ifBlank { "nuevo" }}"
    val inicial = when (tipo) {
        "cliente" -> mapOf("nombre" to c?.nombre.orEmpty(), "rut" to c?.rut.orEmpty(), "giro" to c?.giro.orEmpty(), "contacto" to c?.contacto.orEmpty(), "telefono" to c?.telefono.orEmpty(), "correo" to c?.correo.orEmpty())
        "instalacion" -> mapOf("nombre" to i?.nombre.orEmpty(), "cliente" to i?.clienteId.orEmpty(), "ubicacion" to i?.ubicacion.orEmpty(), "comuna" to i?.comuna.orEmpty(), "region" to i?.region.orEmpty(), "observaciones" to i?.observaciones.orEmpty())
        "equipo" -> mapOf("instalacion" to e?.instalacionId.orEmpty(), "tag" to e?.tag.orEmpty(), "tipo" to (e?.tipo ?: "TRANSFORMADOR"), "marca" to e?.marca.orEmpty(), "modelo" to e?.modelo.orEmpty(), "serie" to e?.serie.orEmpty(), "anio" to e?.anio.orEmpty(), "placa" to e?.placa.orEmpty(), "maximo" to (e?.maxContactos?.toString() ?: ""), "observaciones" to e?.observaciones.orEmpty())
        else -> mapOf("nombre" to instrumento?.nombre.orEmpty(), "marca" to instrumento?.marca.orEmpty(), "modelo" to instrumento?.modelo.orEmpty(), "serie" to instrumento?.serie.orEmpty(), "fecha" to LocalDate.now().toString(), "vence" to "")
    }
    val f = formulario(vm, clave, inicial + ("id" to id.ifBlank { UUID.randomUUID().toString() }))
    Text("${if (id.isBlank()) "Agregar" else "Editar"} $tipo", style = MaterialTheme.typography.headlineSmall)
    Text("Los campos se conservan localmente como borrador.", style = MaterialTheme.typography.bodySmall)
    when (tipo) {
        "cliente" -> { Campo(f, "nombre", "Razón social *"); Campo(f, "rut", "RUT *"); Campo(f, "giro", "Giro"); Campo(f, "contacto", "Contacto"); Campo(f, "telefono", "Teléfono"); Campo(f, "correo", "Correo") }
        "instalacion" -> {
            Elegir(f, "cliente", "Cliente *", s.clientes.map { it.id to it.nombre }, enabled = id.isBlank()); Campo(f, "nombre", "Nombre *")
            Campo(f, "ubicacion", "Ubicación"); Campo(f, "comuna", "Comuna"); Campo(f, "region", "Región"); Campo(f, "observaciones", "Observaciones de acceso", multilinea = true)
        }
        "equipo" -> {
            Elegir(f, "instalacion", "Instalación *", s.instalaciones.map { it.id to it.nombre }, enabled = id.isBlank()); Campo(f, "tag", "TAG *")
            Elegir(f, "tipo", "Tipo *", TipoEquipo.entries.map { it.name to "${it.titulo} · ${it.clasificacion}" }, enabled = id.isBlank())
            Campo(f, "marca", "Marca"); Campo(f, "modelo", "Modelo"); Campo(f, "serie", "Número de serie"); Campo(f, "anio", "Año de fabricación", true)
            Campo(f, "placa", "Datos de placa: potencia, tensiones y corriente", multilinea = true, enabled = id.isBlank())
            if (f["tipo"] in setOf("INTERRUPTOR", "CELDA_MT")) Campo(f, "maximo", "Máximo de contactos de placa (µΩ) *", true, enabled = id.isBlank())
            if (id.isNotBlank()) Text("La instalación, tipo y referencias de placa se conservan para proteger el historial.", style = MaterialTheme.typography.bodySmall)
            Campo(f, "observaciones", "Observaciones", multilinea = true)
        }
        "instrumento" -> {
            Campo(f, "nombre", "Instrumento *"); Campo(f, "marca", "Marca"); Campo(f, "modelo", "Modelo"); Campo(f, "serie", "Número de serie *")
            Text("Se agrega una calibración al historial; las anteriores se conservan.")
            Campo(f, "fecha", "Fecha de calibración (AAAA-MM-DD) *"); Campo(f, "vence", "Vencimiento (AAAA-MM-DD) *")
        }
    }
    Accion(vm, "Guardar $tipo") {
        if (vm.validarCatalogo(tipo, id, f)) vm.accion("$tipo guardado localmente") {
            when (tipo) {
                "cliente" -> cliente(vm.usuario, ClienteEntity(f["id"], f["nombre"], f["rut"], f["giro"], f["contacto"], f["telefono"], f["correo"]))
                "instalacion" -> instalacion(vm.usuario, InstalacionEntity(f["id"], f["cliente"], f["nombre"], f["ubicacion"], f["comuna"], f["region"], f["observaciones"]))
                "equipo" -> {
                    if (f["tipo"] in setOf("INTERRUPTOR", "CELDA_MT")) require(f["maximo"].isNotBlank()) { "Falta máximo de contactos de placa" }
                    equipo(vm.usuario, EquipoEntity(f["id"], f["instalacion"], f["tag"], f["tipo"], f["marca"], f["modelo"], f["serie"], f["anio"], f["placa"], f["maximo"].takeIf { it.isNotBlank() }?.let(ReglasProtocolos::numero), f["observaciones"]))
                }
                "instrumento" -> instrumento(vm.usuario, InstrumentoEntity(f["id"], f["nombre"], f["marca"], f["modelo"], f["serie"]), CalibracionEntity(id(), f["id"], f["fecha"], f["vence"]))
            }
            vm.guardarBorrador(clave, emptyMap()); volver()
        }
    }
}

@Composable
private fun Visitas(vm: TrabajoViewModel, nueva: () -> Unit, abrir: (String) -> Unit) {
    Text("Visitas y actas", style = MaterialTheme.typography.headlineSmall)
    if (Permisos.registrar(vm.usuario)) Accion(vm, "Crear visita") { nueva() }
    val visitas = vm.estado.visitas.filter { vm.usuario.rol != Rol.CLIENTE || it.cierre != null }
    if (visitas.isEmpty()) Text("No hay visitas disponibles.")
    visitas.forEach { v ->
        val ps = vm.estado.protocolos.filter { it.visitaId == v.id }
        val observados = ps.count { ProtocolosRepository.global(vm.estado, it.id) == "OBSERVADO" }
        val rechazados = ps.count { ProtocolosRepository.global(vm.estado, it.id) == "RECHAZADO" }
        Ficha(v.ot, "${vm.estado.instalaciones.find { it.id == v.instalacionId }?.nombre}\n${v.fecha} · ${if (v.cierre == null) "Abierta" else "Cerrada"}\n${ps.size} equipos · $observados observados · $rechazados rechazados") { abrir(v.id) }
    }
}

@Composable
private fun NuevaVisita(vm: TrabajoViewModel, abrir: (String) -> Unit) {
    LaunchedEffect(vm) { vm.actualizarPersonal() }
    val f = formulario(vm, "nueva-visita", mapOf("id" to UUID.randomUUID().toString(), "fecha" to LocalDate.now().toString(), "tipo" to "SOLO_PRUEBAS", "participantes" to if (vm.usuario.rol in setOf(Rol.TECNICO, Rol.INGENIERO)) vm.usuario.id else ""))
    Text("Nueva visita", style = MaterialTheme.typography.headlineSmall)
    Campo(f, "ot", "Número de OT *")
    Elegir(f, "instalacion", "Instalación *", vm.estado.instalaciones.map { it.id to "${it.nombre} · ${vm.estado.clientes.find { c -> c.id == it.clienteId }?.nombre}" })
    Campo(f, "fecha", "Fecha (AAAA-MM-DD) *")
    val personalActivo = vm.personal.filter { it.activo }
    val ingenieros = personalActivo.filter { it.rol == Rol.INGENIERO }
    Elegir(f, "ingeniero", "Ingeniero a cargo *", ingenieros.map { it.id to it.nombre })
    if (ingenieros.isEmpty()) Text("Se necesita un ingeniero activo en el directorio para crear la visita.")
    Elegir(f, "tipo", "Tipo de visita", listOf("SOLO_PRUEBAS" to "Solo pruebas", "PRUEBAS_Y_MANT" to "Pruebas y mantenimiento"))
    Text("Participantes de terreno")
    personalActivo.filter { it.rol in setOf(Rol.TECNICO, Rol.INGENIERO) }.forEach { u ->
        val elegidos = f["participantes"].split(',').filter { it.isNotBlank() }.toSet()
        Row { Checkbox(u.id in elegidos, { marcado -> f.cambiar("participantes", (if (marcado) elegidos + u.id else elegidos - u.id).joinToString(",")) }); Text(u.nombre, Modifier.padding(top = 12.dp)) }
    }
    f.errores["participantes"]?.let { AvisoInduztek("Participantes: $it", error = true) }
    Accion(vm, "Guardar visita offline") {
        if (vm.validarVisita(f)) vm.accion("Visita guardada") {
            visita(vm.usuario, VisitaEntity(f["id"], f["instalacion"], f["ot"], f["fecha"], f["ingeniero"], f["tipo"], vm.usuario.id), f["participantes"].split(','))
            vm.guardarBorrador("nueva-visita", emptyMap()); abrir(f["id"])
        }
    }
}

@Composable
private fun DetalleVisita(vm: TrabajoViewModel, id: String, abrirProtocolo: (String) -> Unit, abrirActa: () -> Unit) {
    val s = vm.estado
    val v = s.visitas.find { it.id == id } ?: return
    val protocolos = s.protocolos.filter { it.visitaId == id }
    Text(v.ot, style = MaterialTheme.typography.headlineSmall)
    Text("${s.instalaciones.find { it.id == v.instalacionId }?.nombre} · ${v.fecha}")
    Text("Ingeniero: ${vm.nombrePersonal(v.ingenieroId)}")
    if (v.cierre == null) {
        s.instalaciones.find { it.id == v.instalacionId }?.let { ClimaVisita(it.comuna) }
    }
    if (v.cierre == null && Permisos.registrar(vm.usuario)) {
        val f = formulario(vm, "crear-protocolo-$id")
        Elegir(f, "equipo", "Equipo", s.equipos.filter { e -> e.instalacionId == v.instalacionId && protocolos.none { it.equipoId == e.id } }.map { it.id to "${it.tag} · ${TipoEquipo.valueOf(it.tipo).titulo}" })
        if (Permisos.revisar(vm.usuario) && f["equipo"].isNotBlank()) Elegir(f, "reemplaza", "Reemplaza a (opcional)", listOf("" to "Ninguno") + s.protocolos.filter { it.equipoId == f["equipo"] && it.estado == "FORMALIZADO" }.map { it.id to it.numero })
        Accion(vm, "Iniciar protocolo del equipo") {
            if (f.requerir("equipo")) vm.accion("Protocolo creado en Borrador") {
                val p = protocolo(vm.usuario, id, f["equipo"], f["reemplaza"].ifBlank { null }); abrirProtocolo(p)
            }
        }
    }
    protocolos.forEach { p -> Ficha("${s.equipos.find { it.id == p.equipoId }?.tag} · ${tituloEstado(vm.usuario, p)}", "${p.numero}\nResultado: ${ProtocolosRepository.global(s, p.id)}") { abrirProtocolo(p.id) } }
    if (v.cierre == null && Permisos.cerrarVisita(vm.usuario)) {
        HorizontalDivider(); Text("Cierre y constancia de recepción", style = MaterialTheme.typography.titleLarge)
        val f = formulario(vm, "recepcion-$id")
        Campo(f, "nombre", "Nombre de quien recibe *"); Campo(f, "rut", "RUT del receptor *"); Campo(f, "cargo", "Cargo *")
        Accion(vm, "Cerrar visita y generar acta") {
            if (f.requerir("nombre", "rut", "cargo")) vm.accion("Visita cerrada; acta disponible sin conexión") { cerrar(vm.usuario, id, f["nombre"], f["rut"], f["cargo"]); abrirActa() }
        }
    }
    if (v.cierre != null) Accion(vm, "Ver y entregar acta") { abrirActa() }
}

private fun tituloEstado(usuario: UsuarioDemo, p: ProtocoloEntity) =
    if (usuario.rol == Rol.CLIENTE && p.estado in setOf("REGISTRADO_TERRENO", "OBSERVADO")) "Pendiente de formalización" else EstadoProtocolo.valueOf(p.estado).titulo

@Composable
private fun DetalleProtocolo(vm: TrabajoViewModel, id: String, editarPrueba: (String) -> Unit) {
    val s = vm.estado
    val p = s.protocolos.find { it.id == id } ?: return
    val e = s.equipos.first { it.id == p.equipoId }
    Text("${e.tag} · ${tituloEstado(vm.usuario, p)}", style = MaterialTheme.typography.headlineSmall)
    Text(p.numero, style = MaterialTheme.typography.bodySmall)
    Text("${DateFormat.getDateTimeInstance().format(Date(p.fecha))}\nResponsable: ${vm.nombrePersonal(p.autorId)}")
    Text("Resultado global: ${ProtocolosRepository.global(s, id)}", fontWeight = FontWeight.Bold)
    s.protocolos.find { it.reemplazaId == p.id }?.let { Text("Reemplazado por nº ${it.numero}") }
    p.reemplazaId?.let { reemplaza -> Text("Reemplaza a ${s.protocolos.find { it.id == reemplaza }?.numero ?: reemplaza}") }
    val f = formulario(vm, "datos-$id", mapOf("temperatura" to (p.temperatura?.toString() ?: ""), "humedad" to (p.humedad?.toString() ?: ""), "mantenimiento" to p.mantenimiento, "comentario" to p.comentario))
    val editable = ReglasProtocolos.editable(vm.usuario, p.autorId, EstadoProtocolo.valueOf(p.estado))
    var pruebaAEliminar by rememberSaveable(id) { mutableStateOf<String?>(null) }
    if (editable) {
        Campo(f, "temperatura", "Temperatura ambiente (°C) *", true); Campo(f, "humedad", "Humedad relativa (%) *", true)
        if (s.visitas.first { it.id == p.visitaId }.tipo == "PRUEBAS_Y_MANT") Campo(f, "mantenimiento", "Mantenimiento realizado por equipo *", multilinea = true)
        Campo(f, "comentario", "Observaciones del equipo", multilinea = true)
        Accion(vm, "Guardar datos del protocolo") {
            val campos = listOf("temperatura", "humedad") + if (s.visitas.first { it.id == p.visitaId }.tipo == "PRUEBAS_Y_MANT") listOf("mantenimiento") else emptyList()
            if (f.requerir(*campos.toTypedArray())) vm.accion("Datos guardados") { datos(vm.usuario, id, ReglasProtocolos.numero(f["temperatura"]), ReglasProtocolos.numero(f["humedad"]), f["mantenimiento"], f["comentario"]) }
        }
        Accion(vm, "Registrar o corregir pruebas") { editarPrueba("nueva") }
        Accion(vm, "Pasar a Registrado en terreno") { vm.accion("Registrado en terreno; pendiente de formalización") { transicion(vm.usuario, id, EstadoProtocolo.REGISTRADO_TERRENO, "") } }
    } else Text("Ambiente: ${p.temperatura} °C / ${p.humedad} % HR\n${p.mantenimiento}\n${p.comentario}")
    Text("Pruebas y mediciones", style = MaterialTheme.typography.titleLarge)
    val pruebas = s.pruebas.filter { it.protocoloId == id }.sortedWith(compareBy({ it.tipo }, { it.elemento }, { it.momento }))
    if (pruebas.isEmpty()) Text("Todavía no hay pruebas. Registra cada elemento aplicable o justifica que no se realizó.")
    pruebas.forEach { t ->
        val medidas = s.mediciones.filter { it.pruebaId == t.id }.joinToString("\n") { "${it.codigo}: ${it.valor} ${it.unidad}" }
        val instru = s.instrumentos.find { it.id == t.instrumentoId }
        Ficha("${TipoPrueba.valueOf(t.tipo).titulo} · ${t.elemento} · ${t.momento}", if (!t.realizada) "No realizada: ${t.motivo}" else "$medidas\n${t.detalle}\n${t.resultado ?: "Pendiente de evaluación manual"}\nInstrumento: ${instru?.nombre} · ${instru?.serie}\n${t.comentario}")
        if (editable) {
            Row {
                TextButton(enabled = !vm.ocupado, onClick = { editarPrueba(t.id) }) { Text("Editar prueba") }
                TextButton(enabled = !vm.ocupado, onClick = { pruebaAEliminar = t.id }) { Text("Eliminar prueba") }
            }
        }
        val manual = t.tipo == "DEVANADOS" || t.tipo == "AISLACION" && e.tipo in setOf("INTERRUPTOR", "CELDA_MT")
        if (manual && t.realizada && Permisos.revisar(vm.usuario) && p.estado in setOf("BORRADOR", "OBSERVADO", "REGISTRADO_TERRENO")) {
            val evaluacion = formulario(vm, "evaluacion-${t.id}", mapOf("resultado" to (t.resultado ?: "APROBADO"), "comentario" to t.comentario))
            Elegir(evaluacion, "resultado", "Resultado manual", ResultadoPrueba.entries.map { it.name to it.name })
            Campo(evaluacion, "comentario", "Justificación del ingeniero *", multilinea = true)
            Accion(vm, "Guardar evaluación de ${t.elemento}") { if (evaluacion.requerir("comentario")) vm.accion("Evaluación guardada") { evaluarManual(vm.usuario, t.id, ResultadoPrueba.valueOf(evaluacion["resultado"]), evaluacion["comentario"]) } }
        }
    }
    if (editable) pruebas.find { it.id == pruebaAEliminar }?.let { seleccion ->
        AlertDialog(
            onDismissRequest = { pruebaAEliminar = null },
            title = { Text("Eliminar prueba de ${seleccion.elemento}") },
            text = { Text("Se quitará la prueba y sus mediciones de este protocolo.") },
            dismissButton = { TextButton(onClick = { pruebaAEliminar = null }) { Text("Cancelar") } },
            confirmButton = {
                TextButton(onClick = {
                    pruebaAEliminar = null
                    vm.accion("Prueba eliminada") {
                        eliminarPrueba(vm.usuario, id, seleccion.id)
                        vm.guardarBorrador("medicion-$id-${seleccion.tipo}-${seleccion.elemento}-${seleccion.momento}", emptyMap())
                        vm.guardarBorrador("evaluacion-${seleccion.id}", emptyMap())
                    }
                }) { Text("Eliminar") }
            }
        )
    }
    Text("Comparación histórica", style = MaterialTheme.typography.titleLarge)
    val comparaciones = ProtocolosRepository.comparaciones(s, p)
    if (comparaciones.isEmpty()) Text("Registra mediciones para comparar con protocolos anteriores.")
    comparaciones.forEach { c ->
        Text("${c.etiqueta}: ${c.actual} ${c.unidad}\n" + if (c.anterior == null) "Sin historial previo" else "Anterior: ${c.anterior} ${c.unidad} · ${c.variacion?.let { "%.1f %%".format(it) } ?: "Variación no calculable"}${if (c.significativa) " · Variación significativa" else ""}", color = if (c.significativa) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
    if (Permisos.revisar(vm.usuario) && p.estado !in setOf("FORMALIZADO", "ANULADO")) {
        val revision = formulario(vm, "revision-$id")
        Campo(revision, "motivo", "Comentario de revisión / motivo", multilinea = true)
        if (p.estado == "REGISTRADO_TERRENO") {
            Accion(vm, "Formalizar protocolo") { vm.accion("Protocolo formalizado y bloqueado") { transicion(vm.usuario, id, EstadoProtocolo.FORMALIZADO, revision["motivo"]) } }
            Accion(vm, "Devolver con observaciones") { if (revision.requerir("motivo")) vm.accion("Protocolo observado") { transicion(vm.usuario, id, EstadoProtocolo.OBSERVADO, revision["motivo"]) } }
        }
        Accion(vm, "Anular con motivo") { if (revision.requerir("motivo")) vm.accion("Protocolo anulado") { transicion(vm.usuario, id, EstadoProtocolo.ANULADO, revision["motivo"]) } }
    }
    Text("Trazabilidad", style = MaterialTheme.typography.titleLarge)
    s.historial.filter { it.protocoloId == id }.forEach { h ->
        val motivo = h.motivo.removePrefix("Dato ficticio del Anexo 9; ").replaceFirstChar { it.uppercase() }
        Text("${EstadoProtocolo.valueOf(h.nuevo).titulo} · ${vm.nombrePersonal(h.usuarioId)}\n${DateFormat.getDateTimeInstance().format(Date(h.fecha))} · $motivo")
    }
}

@Composable
private fun EditorPrueba(vm: TrabajoViewModel, protocoloId: String, pruebaId: String?, volver: () -> Unit) {
    val s = vm.estado
    val p = s.protocolos.find { it.id == protocoloId } ?: return
    val equipo = TipoEquipo.valueOf(s.equipos.first { it.id == p.equipoId }.tipo)
    val seleccion = s.pruebas.find { it.id == pruebaId && it.protocoloId == protocoloId }
    if (pruebaId != "nueva" && seleccion == null) {
        Text("La prueba ya no está disponible.")
        return
    }
    var tipo by rememberSaveable(protocoloId, pruebaId) { mutableStateOf(seleccion?.tipo ?: ReglasProtocolos.tipos(equipo).first().name) }
    var elemento by rememberSaveable(tipo, pruebaId) { mutableStateOf(seleccion?.elemento ?: ReglasProtocolos.elementos(equipo, TipoPrueba.valueOf(tipo)).first()) }
    var momento by rememberSaveable(pruebaId) { mutableStateOf(seleccion?.momento ?: "UNICA") }
    if (seleccion == null) {
        Text("Registrar prueba", style = MaterialTheme.typography.headlineSmall)
        Selector("Prueba", tipo, ReglasProtocolos.tipos(equipo).map { it.name to it.titulo }) { tipo = it }
        Selector("Elemento", elemento, ReglasProtocolos.elementos(equipo, TipoPrueba.valueOf(tipo)).map { it to it }) { elemento = it }
        if (s.visitas.first { it.id == p.visitaId }.tipo == "PRUEBAS_Y_MANT") Selector("Momento", momento, listOf("UNICA" to "Única", "PREVIA" to "Previa al mantenimiento", "POSTERIOR" to "Posterior al mantenimiento")) { momento = it }
    } else {
        Text("Editar prueba", style = MaterialTheme.typography.headlineSmall)
        Text("${TipoPrueba.valueOf(tipo).titulo} · $elemento · $momento")
    }
    val anterior = s.pruebas.find { it.protocoloId == p.id && it.tipo == tipo && it.elemento == elemento && it.momento == momento }
    val clave = "medicion-${p.id}-$tipo-$elemento-$momento"
    val inicial = mapOf("realizada" to (anterior?.realizada?.toString() ?: "true"), "motivo" to anterior?.motivo.orEmpty(), "instrumento" to anterior?.instrumentoId.orEmpty(), "comentario" to anterior?.comentario.orEmpty(), "opera" to (anterior?.opera?.toString() ?: "true"), "unidad" to "MΩ") + s.mediciones.filter { it.pruebaId == anterior?.id }.associate { it.codigo to it.valor.toString() }
    val f = formulario(vm, clave, inicial)
    Elegir(f, "realizada", "Situación", listOf("true" to "Realizada", "false" to "No realizada"))
    val realizada = f["realizada"] == "true"
    if (!realizada) Campo(f, "motivo", "Motivo de no realización *", multilinea = true) else {
        val fecha = Instant.ofEpochMilli(p.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
        Elegir(f, "instrumento", "Instrumento *", s.instrumentos.map { i ->
            val vigente = s.calibraciones.any { it.instrumentoId == i.id && LocalDate.parse(it.fecha) <= fecha && LocalDate.parse(it.vencimiento) >= fecha }
            i.id to "${i.nombre} · ${i.serie} · ${if (vigente) "vigente" else "SIN calibración vigente"}"
        })
        if (tipo == "OPERACION_RELE") Elegir(f, "opera", "¿El relé operó?", listOf("true" to "Sí", "false" to "No"))
        if (tipo == "AISLACION") Elegir(f, "unidad", "Unidad de lecturas", listOf("MΩ" to "MΩ", "GΩ" to "GΩ (se convierte a MΩ al guardar)"))
        if (tipo != "OPERACION_RELE" || f["opera"] == "true") ReglasProtocolos.parametros(equipo, TipoPrueba.valueOf(tipo)).forEach { param ->
            val unidad = if (param.codigo.startsWith("lectura")) f["unidad"] else param.unidad
            Campo(f, param.codigo, "${param.titulo} ($unidad) *", true)
        }
        if (tipo == "DEVANADOS" || tipo == "AISLACION" && equipo in setOf(TipoEquipo.CELDA_MT, TipoEquipo.INTERRUPTOR)) Text("El ingeniero asignará el resultado manualmente antes de formalizar.")
    }
    Campo(f, "comentario", "Comentario de la prueba", multilinea = true)
    vm.previsualizar(equipo, TipoPrueba.valueOf(tipo), f, s.equipos.first { it.id == p.equipoId }.maxContactos)?.let { evaluacion ->
        Ficha("Resultado calculado · vista previa", "${evaluacion.detalle}\n${evaluacion.resultado?.name ?: "Requiere evaluación manual"}")
    }
    Accion(vm, "Guardar prueba offline") {
        val parametros = if (realizada && (tipo != "OPERACION_RELE" || f["opera"] == "true")) ReglasProtocolos.parametros(equipo, TipoPrueba.valueOf(tipo)) else emptyList()
        val campos = parametros.map { it.codigo } + if (!realizada) listOf("motivo") else listOf("instrumento")
        if (vm.validarPrueba(f, campos, p.fecha)) vm.accion("Prueba y resultado guardados") {
            val valores = parametros.associate { param -> param.codigo to ReglasProtocolos.numero(f[param.codigo]) * if (param.codigo.startsWith("lectura") && f["unidad"] == "GΩ") 1000 else 1 }
            prueba(vm.usuario, PruebaEntity(anterior?.id ?: id(), protocoloId, tipo, elemento, momento, realizada, f["motivo"], f["instrumento"].ifBlank { null }, null, "", f["comentario"], f["opera"] != "false"), valores)
            vm.guardarBorrador(clave, emptyMap()); volver()
        }
    }
}

@Composable
private fun Historial(vm: TrabajoViewModel, abrir: (String) -> Unit) {
    val s = vm.estado
    var equipoId by rememberSaveable { mutableStateOf("") }
    var estado by rememberSaveable { mutableStateOf("") }
    var cliente by rememberSaveable { mutableStateOf("") }
    var tecnico by rememberSaveable { mutableStateOf("") }
    Text("Protocolos e historial", style = MaterialTheme.typography.headlineSmall)
    Selector("Equipo", equipoId, listOf("" to "Todos") + s.equipos.map { it.id to "${it.tag} · ${s.instalaciones.find { i -> i.id == it.instalacionId }?.nombre}" }) { equipoId = it }
    Selector("Estado", estado, listOf("" to "Todos") + EstadoProtocolo.entries.filter { vm.usuario.rol != Rol.CLIENTE || it !in setOf(EstadoProtocolo.BORRADOR, EstadoProtocolo.ANULADO) }.map { it.name to it.titulo }) { estado = it }
    if (vm.usuario.rol != Rol.CLIENTE) {
        Selector("Cliente", cliente, listOf("" to "Todos") + s.clientes.map { it.id to it.nombre }) { cliente = it }
        Selector("Responsable", tecnico, listOf("" to "Todos") + vm.personal.map { it.id to it.nombre }) { tecnico = it }
    }
    val protocolos = s.protocolos.filter { (equipoId.isBlank() || it.equipoId == equipoId) && (estado.isBlank() || it.estado == estado) && (cliente.isBlank() || s.clienteDeProtocolo(it) == cliente) && (tecnico.isBlank() || it.autorId == tecnico) }.sortedByDescending { it.fecha }
    if (protocolos.isEmpty()) Text("No hay protocolos para estos filtros.")
    val pendientes = protocolos.count { it.estado !in setOf("FORMALIZADO", "ANULADO") }
    Text("${protocolos.size} protocolos · $pendientes pendientes de formalizar")
    protocolos.forEach { p ->
        val fecha = Instant.ofEpochMilli(p.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
        val atrasado = p.estado !in setOf("FORMALIZADO", "ANULADO") && ReglasProtocolos.atrasado(fecha, LocalDate.now())
        Ficha("${s.equipos.find { it.id == p.equipoId }?.tag} · ${tituloEstado(vm.usuario, p)}", "${p.numero}\n$fecha · ${ProtocolosRepository.global(s, p.id)}${if (atrasado) "\nAtrasado: más de 2 días hábiles (lunes a viernes)" else ""}") { abrir(p.id) }
    }
}

@Composable
internal fun ActaScreen(visita: VisitaEntity, usuario: UsuarioDemo) {
    val contexto = LocalContext.current
    var copiaRemota by remember(visita.id, visita.acta) { mutableStateOf<String?>(null) }
    val textoActa = remember(visita.acta, copiaRemota) { Actas.paraEntrega(copiaRemota ?: visita.acta) }
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf("") }
    var consultando by remember { mutableStateOf(false) }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openOutputStream(uri)?.use { salida ->
                        val documento = PdfDocument()
                        try {
                            val paint = Paint().apply { textSize = 10f }
                            val lineas = textoActa.lines().flatMap { linea ->
                                val resultado = mutableListOf<String>(); var resto = linea
                                while (resto.length > 90) { var corte = resto.lastIndexOf(' ', 90); if (corte < 1) corte = 90; resultado.add(resto.take(corte)); resto = resto.drop(corte).trimStart() }
                                resultado + resto
                            }
                            lineas.chunked(48).forEachIndexed { indice, pagina ->
                                val page = documento.startPage(PdfDocument.PageInfo.Builder(595, 842, indice + 1).create())
                                pagina.forEachIndexed { n, linea -> page.canvas.drawText(linea, 32f, 40f + n * 15f, paint) }
                                documento.finishPage(page)
                            }
                            documento.writeTo(salida)
                        } finally { documento.close() }
                    } ?: error("No se pudo abrir el documento")
                }
                mensaje = "PDF guardado"
            } catch (e: Exception) { mensaje = "No se pudo guardar el PDF: ${e.message}" }
        }
    }
    Text("Acta de terreno", style = MaterialTheme.typography.headlineSmall)
    OutlinedButton(enabled = !consultando, onClick = {
        consultando = true
        scope.launch {
            try {
                val id = visita.id
                val copia = ServiciosRemotos.actas(ConfigSync(contexto).actas, usuario).acta(id)
                require(copia.esValida(id)) { "Acta inválida" }
                copiaRemota = copia.texto
                mensaje = "Copia consultada en el servidor de actas."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mensaje = if (error is HttpException && error.code() == 404) "El acta aún no está disponible en el servidor."
                    else "No se pudo consultar el servidor de actas. Puedes usar la copia local."
            } finally {
                consultando = false
            }
        }
    }) { Text(if (consultando) "Consultando acta…" else "Consultar copia sincronizada") }
    Button(onClick = { guardarPdf.launch("Acta-${visita.ot}.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("Guardar acta en PDF") }
    OutlinedButton(onClick = {
        try { contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Acta ${visita.ot}"); putExtra(Intent.EXTRA_TEXT, textoActa) }, "Entregar acta al cliente")) }
        catch (e: Exception) { mensaje = "No hay una aplicación disponible para compartir" }
    }, modifier = Modifier.fillMaxWidth()) { Text("Compartir acta de terreno") }
    if (mensaje.isNotBlank()) Text(mensaje)
    Text(textoActa)
}
