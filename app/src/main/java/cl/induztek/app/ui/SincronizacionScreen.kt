package cl.induztek.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.sync.*
import cl.induztek.app.viewmodel.TrabajoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun SincronizacionScreen(vm: TrabajoViewModel) {
    val contexto = LocalContext.current
    val config = remember { ConfigSync(contexto) }
    var catalogo by rememberSaveable { mutableStateOf(config.catalogo) }
    var protocolos by rememberSaveable { mutableStateOf(config.protocolos) }
    var mensaje by remember { mutableStateOf(config.estado(vm.usuario.id)) }
    var pendientes by remember { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var mostrarConfiguracion by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun contar() { pendientes = vm.repo.dao.pendientes(vm.usuario.id).size }
    LaunchedEffect(Unit) { contar() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sincronización", style = MaterialTheme.typography.headlineSmall)
        Text("$pendientes cambios de tu cuenta pendientes de envío")
        Text("El trabajo en terreno se guarda en este dispositivo. Los envíos se reintentan cuando hay conexión.")
        TextButton(enabled = !ocupado, onClick = { mostrarConfiguracion = !mostrarConfiguracion }) {
            Text(if (mostrarConfiguracion) "Ocultar configuración" else "Configurar conexión")
        }
        AnimatedVisibility(mostrarConfiguracion) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(catalogo, { catalogo = it }, label = { Text("Servidor de catálogos") }, enabled = !ocupado, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(protocolos, { protocolos = it }, label = { Text("Servidor de protocolos") }, enabled = !ocupado, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(enabled = !ocupado, onClick = {
            ocupado = true
            scope.launch {
                try {
                    config.catalogo = catalogo.trim(); config.protocolos = protocolos.trim()
                    val cantidad = Sincronizador(InduztekDatabase.obtener(contexto), ServiciosRemotos.crear(config.catalogo, vm.usuario), ServiciosRemotos.crear(config.protocolos, vm.usuario)).sincronizar(vm.usuario)
                    mensaje = "Sincronización completada: $cantidad cambios enviados"
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
    }
}
