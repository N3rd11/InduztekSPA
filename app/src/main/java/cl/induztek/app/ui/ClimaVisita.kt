package cl.induztek.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cl.induztek.app.data.ClimaComuna
import cl.induztek.app.data.ClimaRepository
import cl.induztek.app.data.ubicacionAproximada
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.round

@Composable
fun ClimaVisita(comuna: String) {
    val contexto = LocalContext.current
    var clima by remember(comuna) { mutableStateOf<ClimaComuna?>(null) }
    var mensaje by remember(comuna) { mutableStateOf<String?>(null) }
    var cargando by remember(comuna) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun consultar(sinDatos: String, cargar: suspend () -> ClimaComuna?) {
        cargando = true
        mensaje = null
        clima = null
        scope.launch {
            try {
                clima = cargar()
                if (clima == null) mensaje = sinDatos
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mensaje = "No se pudo consultar el clima. Puedes seguir trabajando en la visita."
            } finally {
                cargando = false
            }
        }
    }

    fun consultarAqui() = consultar("Ubicación o clima no disponibles. Revisa la ubicación del dispositivo.") {
        val lugar = ubicacionAproximada(contexto) ?: return@consultar null
        ClimaRepository.externo.consultarCoordenadas(
            round(lugar.latitude * 100) / 100,
            round(lugar.longitude * 100) / 100
        )
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) consultarAqui()
        else mensaje = "Autoriza la ubicación aproximada para consultar el clima donde estás."
    }
    if (comuna.isNotBlank()) {
        OutlinedButton(enabled = !cargando, onClick = {
            consultar("No hay datos meteorológicos para esta comuna.") { ClimaRepository.externo.consultar(comuna) }
        }) { Text("Consultar clima de $comuna") }
    }
    OutlinedButton(enabled = !cargando, onClick = {
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) consultarAqui()
        else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }) { Text("Consultar clima con mi ubicación") }
    if (cargando) Text("Consultando clima…")
    mensaje?.let { Text(it) }
    clima?.let {
        Text("${it.localidad} · ${it.temperatura} °C · ${it.humedad} % HR")
        Text(
            "Open-Meteo · ${it.hora.replace('T', ' ')} (hora local) · Referencia para planificar la visita",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
