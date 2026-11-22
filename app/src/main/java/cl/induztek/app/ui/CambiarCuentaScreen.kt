package cl.induztek.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos
import cl.induztek.app.ui.components.AvisoInduztek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun CambiarCuentaScreen(actual: UsuarioDemo, config: ConfigSync, onAgregar: () -> Unit, onCambiar: (UsuarioDemo) -> Unit) {
    var cuentas by remember { mutableStateOf(ServiciosRemotos.cuentasEnMemoria()) }
    var verificando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cuenta activa", style = MaterialTheme.typography.titleMedium)
        Text("${actual.nombre} · ${actual.rol.titulo()}")
        cuentas.filter { it.id != actual.id }.forEach { cuenta ->
            OutlinedCard(enabled = !verificando, modifier = Modifier.fillMaxWidth(), onClick = {
                verificando = true
                error = null
                scope.launch {
                    try {
                        onCambiar(ServiciosRemotos.reactivar(config, cuenta.id))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: HttpException) {
                        if (e.code() == 401 || e.code() == 403) {
                            ServiciosRemotos.cerrarSesion(cuenta.id)
                            cuentas = ServiciosRemotos.cuentasEnMemoria()
                            error = "La cuenta necesita iniciar sesión nuevamente."
                        } else {
                            error = "Usuarios no pudo validar la cuenta. Intenta nuevamente."
                        }
                    } catch (_: Exception) {
                        error = "No se pudieron verificar los servicios. Intenta nuevamente."
                    } finally {
                        verificando = false
                    }
                }
            }) {
                Column(Modifier.padding(16.dp)) {
                    Text(cuenta.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(cuenta.rol.titulo())
                }
            }
        }
        if (verificando) LinearProgressIndicator(Modifier.fillMaxWidth())
        AvisoInduztek(error, error = true)
        Button(enabled = !verificando, onClick = onAgregar, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar otra cuenta")
        }
        Text("Cada cuenta se verifica al cambiar. Las sesiones se conservan mientras la app sigue abierta.")
    }
}
