package cl.induztek.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

suspend fun ubicacionAproximada(contexto: Context): Location? {
    if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) return null
    val gestor = contexto.getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(gestor)) return null
    val preferidos = if (Build.VERSION.SDK_INT >= 31) {
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    } else {
        listOf(LocationManager.NETWORK_PROVIDER)
    }
    val habilitados = gestor.getProviders(true)
    val ejecutor = ContextCompat.getMainExecutor(contexto)
    for (proveedor in preferidos.filter { it in habilitados }) {
        val resultado = withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine<Location?> { continuacion ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val cancelacion = CancellationSignal()
                    continuacion.invokeOnCancellation { cancelacion.cancel() }
                    gestor.getCurrentLocation(proveedor, cancelacion, ejecutor) { continuacion.resume(it) }
                } else {
                    @Suppress("DEPRECATION")
                    val cancelacion = androidx.core.os.CancellationSignal()
                    continuacion.invokeOnCancellation { cancelacion.cancel() }
                    @Suppress("DEPRECATION")
                    LocationManagerCompat.getCurrentLocation(gestor, proveedor, cancelacion, ejecutor) {
                        continuacion.resume(it)
                    }
                }
            }
        }
        if (resultado != null) return resultado
    }
    return null
}
