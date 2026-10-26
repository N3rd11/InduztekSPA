package cl.induztek.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.induztek.app.model.EstadoPermisosMultimedia
import cl.induztek.app.model.PermisosMultimedia

data class AccesosMultimedia(val estado: EstadoPermisosMultimedia, val solicitar: () -> Unit)

/** El rechazo no bloquea el login y no se vuelve a insistir en cada arranque. */
@Composable
fun recordarAccesosMultimedia(): AccesosMultimedia {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    fun concedido(permiso: String) = contexto.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED
    fun actual() = PermisosMultimedia.estado(Build.VERSION.SDK_INT, ::concedido)
    var estado by remember(contexto) { mutableStateOf(actual()) }
    val solicitar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { estado = actual() }
    fun pedir() {
        val faltantes = PermisosMultimedia.pendientes(Build.VERSION.SDK_INT, ::concedido)
        if (faltantes.isNotEmpty()) solicitar.launch(faltantes.toTypedArray())
        else estado = actual()
    }
    DisposableEffect(ciclo, contexto) {
        val observador = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_RESUME) estado = actual() }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    LaunchedEffect(contexto) {
        val preferencias = contexto.getSharedPreferences("permisos_multimedia", Context.MODE_PRIVATE)
        if (!preferencias.getBoolean("solicitud_inicial_realizada", false)) {
            preferencias.edit().putBoolean("solicitud_inicial_realizada", true).apply()
            pedir()
        }
    }
    return AccesosMultimedia(estado, ::pedir)
}
