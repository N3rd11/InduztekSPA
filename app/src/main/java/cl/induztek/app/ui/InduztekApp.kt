package cl.induztek.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import cl.induztek.app.RegistroScreen
import cl.induztek.app.model.Permisos
import cl.induztek.app.model.Rol
import cl.induztek.app.navigation.Destinos
import cl.induztek.app.ui.components.*
import cl.induztek.app.viewmodel.*
import cl.induztek.app.data.CuentasRepository
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InduztekApp(sesion: SesionViewModel = viewModel()) {
    val accesos = recordarAccesosMultimedia()
    val usuario = sesion.usuario
    // Una sesión nueva recibe una pila nueva: Atrás nunca regresa a otro perfil.
    key(usuario?.id, usuario?.rol, usuario?.clienteId) {
        var registroEnviado by rememberSaveable { mutableStateOf(false) }
        val nav = rememberNavController()
        val entrada by nav.currentBackStackEntryAsState()
        val ruta = entrada?.destination?.route ?: if (usuario == null) Destinos.ACCESO else Destinos.INICIO
        val app = LocalContext.current.applicationContext as Application
        val trabajo: TrabajoViewModel? = if (usuario != null) viewModel(key = "trabajo-${usuario.id}-${usuario.rol}-${usuario.clienteId}", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = TrabajoViewModel(app, usuario) as T
        }) else null
        val perfil: PerfilViewModel? = if (usuario != null) viewModel(key = "perfil-${usuario.id}", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = PerfilViewModel(app, usuario.id) as T
        }) else null
        val portal: PortalClienteViewModel? = if (usuario?.rol == Rol.CLIENTE) viewModel(
            key = "portal-${usuario.id}-${usuario.clienteId}",
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = PortalClienteViewModel(app, usuario) as T
            }
        ) else null
        LaunchedEffect(portal) { portal?.actualizar() }
        val titulos = mapOf(Destinos.INICIO to if (portal != null) "Portal de clientes" else "Induztek · Terreno",
            Destinos.PERFIL to "Mi perfil", Destinos.VISITAS to if (portal != null) "Trabajos" else "Visitas y actas",
            Destinos.HISTORIAL to "Protocolos e historial", Destinos.CATALOGOS to if (portal != null) "Mis equipos" else "Catálogos",
            Destinos.TRABAJO_CLIENTE + "/{id}" to "Detalle del trabajo",
            Destinos.ACTA_CLIENTE + "/{id}" to "Acta de terreno", Destinos.REGISTRO to "Mediciones anteriores",
            Destinos.SINCRONIZACION to "Sincronización", Destinos.CUENTAS to "Administración de cuentas",
            Destinos.CAMBIAR_CUENTA to "Cambiar cuenta", Destinos.AGREGAR_CUENTA to "Agregar otra cuenta")
        val secciones = if (portal != null) listOf(
            Triple(Destinos.INICIO, "Inicio", Simbolo.INICIO),
            Triple(Destinos.VISITAS, "Trabajos", Simbolo.VISITA),
            Triple(Destinos.CATALOGOS, "Mis equipos", Simbolo.CATALOGO)
        ) else listOf(
            Triple(Destinos.INICIO, "Inicio", Simbolo.INICIO),
            Triple(Destinos.VISITAS, "Visitas", Simbolo.VISITA),
            Triple(Destinos.HISTORIAL, if (usuario?.rol == Rol.INGENIERO) "Revisión" else "Historial", Simbolo.HISTORIAL),
            Triple(Destinos.CATALOGOS, "Catálogos", Simbolo.CATALOGO)
        ) + if (usuario?.rol == Rol.JEFE_TECNICO) listOf(Triple(Destinos.CUENTAS, "Cuentas", Simbolo.PERFIL)) else emptyList()
        fun ir(destino: String) { nav.navigate(destino) { launchSingleTop = true } }
        fun abrirTrabajoCliente(id: String) = ir("${Destinos.TRABAJO_CLIENTE}/${Uri.encode(id)}")
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
                if (usuario != null && (ruta == Destinos.PERFIL || secciones.any { it.first == ruta })) NavigationBar {
                    secciones.forEach { (destino,titulo,icono) ->
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
                composable(Destinos.ACCESO) {
                    LoginScreen(sesion::acceder, { ir(Destinos.REGISTRO_CUENTA) }, { ir(Destinos.CONEXION) }, registroEnviado)
                }
                if (usuario == null) {
                    composable(Destinos.REGISTRO_CUENTA) {
                        RegistroCuentaScreen({ nav.popBackStack() }) {
                            registroEnviado = true
                            nav.popBackStack()
                        }
                    }
                    composable(Destinos.CONEXION) { ConfiguracionConexionScreen { nav.popBackStack() } }
                }
                if (usuario != null && trabajo != null && perfil != null) {
                    composable(Destinos.INICIO) {
                        if (portal != null) {
                            PantallaCliente(portal, Destinos.INICIO, ::abrirTrabajoCliente,
                                verTrabajos = { ir(Destinos.VISITAS) }, verEquipos = { ir(Destinos.CATALOGOS) },
                                cambiarCuenta = { ir(Destinos.CAMBIAR_CUENTA) },
                                verSincronizacion = { ir(Destinos.SINCRONIZACION) }, cerrarSesion = sesion::cerrar)
                            return@composable
                        }
                        LaunchedEffect(trabajo) { trabajo.actualizarPersonal() }
                        InicioScreen(usuario, { ir(Destinos.REGISTRO) }, sesion::cerrar) {
                            val s = trabajo.estado
                            Text(when (usuario.rol) {
                                Rol.TECNICO -> "Trabajo en terreno"
                                Rol.INGENIERO -> "Revisión de protocolos"
                                Rol.JEFE_TECNICO -> "Supervisión y administración"
                                Rol.CLIENTE -> "Documentos de mi empresa"
                            }, style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ResumenPanel("${s.visitas.count { it.cierre == null }}", "Visitas abiertas", Modifier.weight(1f))
                                val protocolos = when (usuario.rol) {
                                    Rol.TECNICO -> s.protocolos.count { it.autorId == usuario.id && it.estado in setOf("BORRADOR", "OBSERVADO") } to "Mis borradores y observados"
                                    Rol.INGENIERO -> s.protocolos.count { it.estado == "REGISTRADO_TERRENO" } to "Para revisar"
                                    Rol.JEFE_TECNICO -> s.protocolos.count { it.estado !in setOf("FORMALIZADO", "ANULADO") } to "Pendientes"
                                    Rol.CLIENTE -> s.visitas.count { it.cierre != null } to "Actas de mi empresa"
                                }
                                ResumenPanel(protocolos.first.toString(), protocolos.second, Modifier.weight(1f))
                            }
                            if (trabajo.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
                            AvisoInduztek(trabajo.error, error = true)
                            AccesoPanel("Visitas y actas de terreno", "Registra el trabajo y entrega el acta", Simbolo.VISITA) { ir(Destinos.VISITAS) }
                            AccesoPanel("Protocolos e historial por equipo", if (Permisos.revisar(usuario)) "Revisa, compara y formaliza" else "Estados y evolución de mediciones", Simbolo.HISTORIAL) { ir(Destinos.HISTORIAL) }
                            AccesoPanel("Clientes, equipos e instrumentos", if (Permisos.instrumentos(usuario)) "Administra catálogos y calibraciones" else "Instalaciones y equipos identificados", Simbolo.CATALOGO) { ir(Destinos.CATALOGOS) }
                            if (usuario.rol == Rol.JEFE_TECNICO) {
                                AccesoPanel("Administrar cuentas", "Solicitudes, roles y acceso", Simbolo.PERFIL) { ir(Destinos.CUENTAS) }
                            }
                            TextButton(onClick = { ir(Destinos.CAMBIAR_CUENTA) }) { Text("Cambiar cuenta") }
                            TextButton(onClick = { ir(Destinos.SINCRONIZACION) }) { Text("Sincronización y conexión") }
                        }
                    }
                    composable(Destinos.PERFIL) { PerfilScreen(usuario, perfil, accesos) }
                    composable(Destinos.CAMBIAR_CUENTA) {
                        CambiarCuentaScreen(usuario, ConfigSync(app), { ir(Destinos.AGREGAR_CUENTA) }, sesion::acceder)
                    }
                    composable(Destinos.AGREGAR_CUENTA) {
                        LoginScreen(onAcceder = {
                            if (it == usuario) nav.popBackStack()
                            else sesion.acceder(it)
                        })
                    }
                    if (portal != null) {
                        listOf(Destinos.VISITAS, Destinos.CATALOGOS).forEach { seccion ->
                            composable(seccion) { PantallaCliente(portal, seccion, ::abrirTrabajoCliente) }
                        }
                        val idTrabajo = listOf(navArgument("id") { type = NavType.StringType })
                        composable("${Destinos.TRABAJO_CLIENTE}/{id}", arguments = idTrabajo) { entradaTrabajo ->
                            val id = entradaTrabajo.arguments?.getString("id")
                            val detalle = portal.datos?.trabajos?.find { it.visita.id == id }
                            DetalleTrabajoCliente(detalle) {
                                detalle?.let { ir("${Destinos.ACTA_CLIENTE}/${Uri.encode(it.visita.id)}") }
                            }
                        }
                        composable("${Destinos.ACTA_CLIENTE}/{id}", arguments = idTrabajo) { entradaActa ->
                            val id = entradaActa.arguments?.getString("id")
                            val trabajoCliente = portal.datos?.trabajos?.find { it.visita.id == id && it.tieneActa }
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (trabajoCliente == null) Text("El acta ya no está disponible.")
                                else ActaScreen(trabajoCliente.visita, usuario)
                            }
                        }
                    } else {
                        listOf(Destinos.VISITAS, Destinos.CATALOGOS, Destinos.HISTORIAL).forEach { seccion ->
                            composable(seccion) { PantallaTrabajo(trabajo, seccion) }
                        }
                    }
                    composable(Destinos.REGISTRO) {
                        if (Permisos.registrar(usuario)) {
                            val registro: RegistroViewModel = viewModel(
                                key = "registro-${usuario.id}",
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>) =
                                        RegistroViewModel(app, usuario.id) as T
                                }
                            )
                            RegistroScreen(registro)
                        } else Text("Acceso de solo consulta")
                    }
                    composable(Destinos.SINCRONIZACION) { SincronizacionScreen(trabajo) }
                    if (usuario.rol == Rol.JEFE_TECNICO) {
                        composable(Destinos.CUENTAS) {
                            val cuentas: CuentasViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    CuentasViewModel(CuentasRepository(ServiciosRemotos.cuentas(ConfigSync(app).acceso, usuario))) as T
                            })
                            CuentasScreen(cuentas, trabajo.estado.clientes, usuario.id)
                        }
                    }
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
