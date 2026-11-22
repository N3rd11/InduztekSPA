package cl.induztek.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cl.induztek.app.data.AvanceCliente
import cl.induztek.app.data.TrabajoCliente
import cl.induztek.app.model.EstadoProtocolo
import cl.induztek.app.model.TipoEquipo
import cl.induztek.app.navigation.Destinos
import cl.induztek.app.ui.components.AvisoInduztek
import cl.induztek.app.viewmodel.PortalClienteViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fechaCliente = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CL"))
private fun fechaVisita(valor: String) = runCatching { LocalDate.parse(valor).format(fechaCliente) }.getOrDefault(valor)

@Composable
internal fun PantallaCliente(
    vm: PortalClienteViewModel,
    seccion: String,
    abrirTrabajo: (String) -> Unit,
    verTrabajos: () -> Unit = {},
    verEquipos: () -> Unit = {},
    cambiarCuenta: () -> Unit = {},
    verSincronizacion: () -> Unit = {},
    cerrarSesion: () -> Unit = {}
) {
    val datos = vm.datos
    var busqueda by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("portal-cliente-lista"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (seccion == Destinos.INICIO) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Documentos de mi empresa", style = MaterialTheme.typography.headlineSmall)
                        datos?.empresa?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            } else {
                Text(if (seccion == Destinos.VISITAS) "Trabajos de mi empresa" else "Equipos de mi empresa",
                    style = MaterialTheme.typography.headlineSmall)
            }
        }
        item { EstadoConsultaCliente(vm) }
        if (datos != null) when (seccion) {
            Destinos.INICIO -> {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IndicadorCliente(datos.trabajos.count { it.avance == AvanceCliente.EN_CURSO },
                            "Visitas en curso", Modifier.weight(1f))
                        IndicadorCliente(datos.trabajos.count { it.tieneActa },
                            "Actas disponibles", Modifier.weight(1f))
                    }
                }
                item {
                    Text("Trabajos recientes", style = MaterialTheme.typography.titleLarge)
                    if (datos.trabajos.isEmpty()) Text("Aún no hay trabajos publicados para tu empresa.")
                }
                items(datos.trabajos.take(3), key = { it.visita.id }) { trabajo ->
                    TarjetaTrabajoCliente(trabajo) { abrirTrabajo(trabajo.visita.id) }
                }
                item {
                    Button(onClick = verTrabajos, modifier = Modifier.fillMaxWidth()) { Text("Ver todos los trabajos") }
                    OutlinedButton(onClick = verEquipos, modifier = Modifier.fillMaxWidth()) { Text("Ver mis equipos") }
                }
            }
            Destinos.VISITAS -> {
                item {
                    OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar por orden o instalación") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                val trabajos = datos.trabajos.filter {
                    it.visita.ot.contains(busqueda.trim(), ignoreCase = true) || it.instalacion.contains(busqueda.trim(), ignoreCase = true)
                }
                if (trabajos.isEmpty()) item {
                    Text(if (datos.trabajos.isEmpty()) "Aún no hay trabajos publicados para tu empresa."
                        else "No encontramos trabajos con esa búsqueda.")
                }
                items(trabajos, key = { it.visita.id }) { trabajo ->
                    TarjetaTrabajoCliente(trabajo) { abrirTrabajo(trabajo.visita.id) }
                }
            }
            Destinos.CATALOGOS -> {
                if (datos.equipos.isEmpty()) item { Text("Aún no hay equipos publicados para tu empresa.") }
                items(datos.equipos, key = { it.equipo.id }) { equipo ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(equipo.equipo.tag, style = MaterialTheme.typography.titleMedium)
                            Text(equipo.instalacion)
                            TipoEquipo.entries.find { it.name == equipo.equipo.tipo }?.let { Text(it.titulo) }
                            val modelo = listOf(equipo.equipo.marca, equipo.equipo.modelo)
                                .filter { it.isNotBlank() }.joinToString(" ")
                            if (modelo.isNotBlank()) Text(modelo, style = MaterialTheme.typography.bodyMedium)
                            if (equipo.equipo.serie.isNotBlank()) {
                                Text("Serie ${equipo.equipo.serie}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        if (seccion == Destinos.INICIO) item {
            TextButton(onClick = cambiarCuenta) { Text("Cambiar cuenta") }
            TextButton(onClick = verSincronizacion) { Text("Sincronización y conexión") }
            TextButton(onClick = cerrarSesion) { Text("Cerrar sesión") }
        }
    }
}

@Composable
private fun EstadoConsultaCliente(vm: PortalClienteViewModel) {
    if (vm.consultando) LinearProgressIndicator(Modifier.fillMaxWidth())
    vm.actualizado?.let {
        val fecha = it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es-CL")))
        Text("Última consulta al servidor · $fecha", style = MaterialTheme.typography.bodySmall)
    }
    AvisoInduztek(vm.error, error = true)
    TextButton(onClick = vm::actualizar, enabled = !vm.consultando) { Text("Actualizar datos") }
}

@Composable
private fun IndicadorCliente(valor: Int, etiqueta: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(valor.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(etiqueta, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TarjetaTrabajoCliente(trabajo: TrabajoCliente, abrir: () -> Unit) {
    OutlinedCard(onClick = abrir, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(trabajo.instalacion, style = MaterialTheme.typography.titleMedium)
            Text("${trabajo.visita.ot} · ${fechaVisita(trabajo.visita.fecha)}", style = MaterialTheme.typography.bodyMedium)
            EstadoTrabajoCliente(trabajo.avance)
        }
    }
}

@Composable
private fun EstadoTrabajoCliente(avance: AvanceCliente) {
    val colores = MaterialTheme.colorScheme
    Surface(
        color = if (avance == AvanceCliente.FORMALIZADA) colores.primaryContainer else colores.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(avance.titulo, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun DetalleTrabajoCliente(trabajo: TrabajoCliente?, abrirActa: () -> Unit) {
    if (trabajo == null) {
        Text("Este trabajo ya no está disponible.", Modifier.padding(20.dp))
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(trabajo.instalacion, style = MaterialTheme.typography.headlineSmall)
        Text("${trabajo.visita.ot} · Visita del ${fechaVisita(trabajo.visita.fecha)}")
        when (trabajo.visita.tipo) {
            "SOLO_PRUEBAS" -> Text("Pruebas eléctricas")
            "PRUEBAS_Y_MANT" -> Text("Pruebas y mantenimiento")
        }
        EstadoTrabajoCliente(trabajo.avance)
        if (trabajo.tieneActa) {
            Button(onClick = abrirActa, modifier = Modifier.fillMaxWidth()) { Text("Ver acta de terreno") }
        }
        Text("Protocolos y resultados", style = MaterialTheme.typography.titleLarge)
        if (trabajo.protocolos.isEmpty()) Text("Aún no hay protocolos publicados para este trabajo.")
        trabajo.protocolos.forEach { protocolo ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(protocolo.equipo ?: protocolo.numero, style = MaterialTheme.typography.titleMedium)
                    if (protocolo.equipo != null) Text(protocolo.numero, style = MaterialTheme.typography.bodySmall)
                    Text(when (protocolo.estado) {
                        EstadoProtocolo.FORMALIZADO -> "Formalizado"
                        EstadoProtocolo.OBSERVADO -> "En revisión técnica"
                        else -> "Pendiente de formalización"
                    }, style = MaterialTheme.typography.labelLarge)
                    if (protocolo.pruebasRealizadas.isNotEmpty()) {
                        Text("Pruebas realizadas", style = MaterialTheme.typography.labelLarge)
                        protocolo.pruebasRealizadas.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    protocolo.resultado?.let { resultado ->
                        Text("Resultado: ${resultado.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }}")
                    }
                }
            }
        }
    }
}
