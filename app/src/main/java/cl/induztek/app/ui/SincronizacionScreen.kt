package cl.induztek.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.data.FotoPerfilRepository
import cl.induztek.app.sync.*
import cl.induztek.app.viewmodel.TrabajoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun SincronizacionScreen(vm: TrabajoViewModel) {
    val contexto = LocalContext.current
    val config = remember { ConfigSync(contexto) }
    var mensaje by remember { mutableStateOf(config.estado(vm.usuario.id)) }
    var pendientes by remember { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var avisos by remember { mutableStateOf(AvisosSincronizacion.preferidos(contexto)) }
    var mostrarAjustes by remember { mutableStateOf(false) }
    val permisoAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        AvisosSincronizacion.configurar(contexto, concedido)
        avisos = concedido
        mostrarAjustes = !concedido
    }
    val scope = rememberCoroutineScope()
    suspend fun contar() { pendientes = vm.repo.dao.pendientes(vm.usuario.id).size }
    LaunchedEffect(Unit) { contar() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sincronización", style = MaterialTheme.typography.headlineSmall)
        Text("$pendientes cambios de tu cuenta pendientes de envío")
        Text("El trabajo en terreno se guarda en este dispositivo. Los envíos se reintentan cuando hay conexión.")
        Button(enabled = !ocupado, onClick = {
            ocupado = true
            scope.launch {
                try {
                    val cantidad = Sincronizador(InduztekDatabase.obtener(contexto), ServiciosRemotos.crear(config.catalogo, vm.usuario), ServiciosRemotos.crear(config.protocolos, vm.usuario)).sincronizar(vm.usuario)
                    FotoPerfilRepository(contexto).sincronizar(vm.usuario)
                    mensaje = "Sincronización completada: $cantidad cambios enviados"
                    AvisosSincronizacion.completada(contexto, cantidad, manual = true)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mensaje = "${detalleError(e)}. Los cambios locales permanecen guardados."
                }
                finally { contar(); config.estado(vm.usuario.id, mensaje); ocupado = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (ocupado) "Sincronizando…" else "Sincronizar ahora") }
        if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(mensaje)
        OutlinedButton(onClick = { scope.launch { contar(); mensaje = config.estado(vm.usuario.id) } }) { Text("Actualizar estado") }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(avisos, role = Role.Switch) { activo ->
                if (activo && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AvisosSincronizacion.configurar(contexto, activo)
                    avisos = activo
                    mostrarAjustes = activo && !NotificationManagerCompat.from(contexto).areNotificationsEnabled()
                }
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notificar al sincronizar", Modifier.weight(1f))
            Switch(checked = avisos, onCheckedChange = null)
        }
        if (mostrarAjustes) {
            TextButton(onClick = {
                contexto.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName))
            }) { Text("Abrir ajustes de notificaciones") }
        }
    }
}

@Composable
fun ConfiguracionConexionScreen(onVolver: () -> Unit) {
    val contexto = LocalContext.current
    val config = remember(contexto) { ConfigSync(contexto) }
    var acceso by rememberSaveable { mutableStateOf(config.acceso) }
    var catalogo by rememberSaveable { mutableStateOf(config.catalogo) }
    var protocolos by rememberSaveable { mutableStateOf(config.protocolos) }
    var actas by rememberSaveable { mutableStateOf(config.actas) }
    var error by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Configurar conexión", style = MaterialTheme.typography.headlineSmall)
        Text("Usa direcciones HTTPS para servidores remotos.")
        OutlinedTextField(acceso, { acceso = it }, label = { Text("Servidor de usuarios") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(catalogo, { catalogo = it }, label = { Text("Servidor de catálogos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(protocolos, { protocolos = it }, label = { Text("Servidor de protocolos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(actas, { actas = it }, label = { Text("Servidor de actas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error) Text("Revisa las cuatro direcciones.", color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            try {
                config.guardarDirecciones(acceso, catalogo, protocolos, actas)
                ServiciosRemotos.cerrarTodas()
                onVolver()
            } catch (_: IllegalArgumentException) {
                error = true
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar conexión") }
        TextButton(onClick = onVolver) { Text("Volver al inicio de sesión") }
    }
}
