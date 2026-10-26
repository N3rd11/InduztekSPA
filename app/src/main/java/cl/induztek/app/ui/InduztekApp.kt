package cl.induztek.app.ui

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import cl.induztek.app.RegistroScreen
import cl.induztek.app.model.Permisos
import cl.induztek.app.model.Rol
import cl.induztek.app.navigation.Destinos
import cl.induztek.app.ui.components.*
import cl.induztek.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InduztekApp(sesion: SesionViewModel = viewModel()) {
    val accesos = recordarAccesosMultimedia()
    val usuario = sesion.usuario
    // Una sesión nueva recibe una pila nueva: Atrás nunca regresa a otro perfil.
    key(usuario?.id) {
        val nav = rememberNavController()
        val entrada by nav.currentBackStackEntryAsState()
        val ruta = entrada?.destination?.route ?: if (usuario == null) Destinos.ACCESO else Destinos.INICIO
        val app = LocalContext.current.applicationContext as Application
        val trabajo: TrabajoViewModel? = if (usuario != null) viewModel(key = "trabajo-${usuario.id}", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = TrabajoViewModel(app, usuario) as T
        }) else null
        val perfil: PerfilViewModel? = if (usuario != null) viewModel(key = "perfil-${usuario.id}", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = PerfilViewModel(app, usuario.id) as T
        }) else null
        val titulos = mapOf(Destinos.INICIO to "Induztek · Terreno", Destinos.PERFIL to "Mi perfil", Destinos.VISITAS to "Visitas y actas",
            Destinos.HISTORIAL to "Protocolos e historial", Destinos.CATALOGOS to "Catálogos", Destinos.REGISTRO to "Mediciones anteriores", Destinos.SINCRONIZACION to "Conexión opcional")
        fun ir(destino: String) { nav.navigate(destino) { launchSingleTop = true } }
        Scaffold(
            topBar = {
                if (usuario != null) TopAppBar(title = { Text(titulos[ruta] ?: "Induztek") }, navigationIcon = {
                    if (ruta != Destinos.INICIO) IconButton(onClick = { nav.navigateUp() }) { IconoInduztek(Simbolo.VOLVER, "Volver") }
                    else LogoInduztek(modifier = Modifier.padding(start = 12.dp, end = 8.dp), tamano = 36.dp)
                }, actions = {
                    if (ruta != Destinos.PERFIL) IconButton(onClick = { ir(Destinos.PERFIL) }) {
                        val foto = perfil?.foto
                        if (foto == null) IconoInduztek(Simbolo.PERFIL, "Mi perfil")
                        else Image(foto.asImageBitmap(), "Mi perfil", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape).testTag("foto-inicio"))
                    }
                })
            },
            bottomBar = {
                if (usuario != null && ruta in listOf(Destinos.INICIO, Destinos.VISITAS, Destinos.HISTORIAL, Destinos.CATALOGOS, Destinos.PERFIL)) NavigationBar {
                    listOf(Triple(Destinos.INICIO,"Inicio",Simbolo.INICIO), Triple(Destinos.VISITAS,"Visitas",Simbolo.VISITA),
                        Triple(Destinos.HISTORIAL,"Historial",Simbolo.HISTORIAL), Triple(Destinos.CATALOGOS,"Catálogos",Simbolo.CATALOGO)).forEach { (destino,titulo,icono) ->
                        NavigationBarItem(selected = ruta == destino, onClick = {
                            nav.navigate(destino) { popUpTo(Destinos.INICIO) { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, icon = { IconoInduztek(icono) }, label = { Text(titulo) })
                    }
                }
            }
        ) { padding ->
            NavHost(navController = nav, startDestination = if (usuario == null) Destinos.ACCESO else Destinos.INICIO,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                enterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 } },
                exitTransition = { fadeOut(tween(120)) }, popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) }) {
                composable(Destinos.ACCESO) { LoginScreen(onAcceder = sesion::acceder) }
                if (usuario != null && trabajo != null && perfil != null) {
                    composable(Destinos.INICIO) {
                        InicioScreen(usuario, { ir(Destinos.REGISTRO) }, sesion::cerrar) {
                            val s = trabajo.estado
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ResumenPanel("${s.visitas.count { it.cierre == null }}", "Visitas abiertas", Modifier.weight(1f))
                                ResumenPanel("${s.protocolos.count { it.estado !in setOf("FORMALIZADO", "ANULADO") }}", "Pendientes", Modifier.weight(1f))
                            }
                            if (trabajo.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
                            AvisoInduztek(trabajo.error, error = true)
                            AccesoPanel("Visitas y actas de terreno", if (usuario.rol == Rol.CLIENTE) "Respaldos recibidos de tu empresa" else "Registra el trabajo y entrega el acta", Simbolo.VISITA) { ir(Destinos.VISITAS) }
                            AccesoPanel("Protocolos e historial por equipo", if (Permisos.revisar(usuario)) "Revisa, compara y formaliza" else "Estados y evolución de mediciones", Simbolo.HISTORIAL) { ir(Destinos.HISTORIAL) }
                            AccesoPanel("Clientes, equipos e instrumentos", if (Permisos.instrumentos(usuario)) "Administra catálogos y calibraciones" else "Instalaciones y equipos identificados", Simbolo.CATALOGO) { ir(Destinos.CATALOGOS) }
                            TextButton(onClick = { ir(Destinos.SINCRONIZACION) }) { Text("Sincronización y conexión") }
                        }
                    }
                    composable(Destinos.PERFIL) { PerfilScreen(usuario, perfil, accesos) }
                    listOf(Destinos.VISITAS,Destinos.CATALOGOS,Destinos.HISTORIAL).forEach { seccion ->
                        composable(seccion) { PantallaTrabajo(trabajo, seccion) }
                    }
                    composable(Destinos.REGISTRO) { if (Permisos.registrar(usuario)) RegistroScreen() else Text("Acceso de solo consulta") }
                    composable(Destinos.SINCRONIZACION) { SincronizacionScreen(trabajo) }
                }
            }
        }
    }
}

@Composable
private fun ResumenPanel(valor: String, etiqueta: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) { Text(valor, style = MaterialTheme.typography.headlineMedium); Text(etiqueta, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun AccesoPanel(titulo: String, detalle: String, simbolo: Simbolo, click: () -> Unit) {
    OutlinedCard(onClick = click, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconoInduztek(simbolo)
            Column(Modifier.weight(1f)) { Text(titulo, style = MaterialTheme.typography.titleMedium); Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
