package cl.induztek.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import cl.induztek.app.data.ubicacionAproximada
import cl.induztek.app.sync.AvisosSincronizacion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ServiciosNativosTest {
    private val contexto get() = RuntimeEnvironment.getApplication()

    @Test fun ubicacionRespetaPermisoYAjustesAntesDeConsultar() = runBlocking {
        val gestor = contexto.getSystemService(LocationManager::class.java)!!
        val simulador = shadowOf(gestor)
        assertNull(ubicacionAproximada(contexto))
        shadowOf(contexto).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        simulador.setLocationEnabled(false)
        assertNull(ubicacionAproximada(contexto))
        simulador.setLocationEnabled(true)
        simulador.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        simulador.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = -33.61
            longitude = -70.58
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
        val consulta = async(start = CoroutineStart.UNDISPATCHED) { ubicacionAproximada(contexto) }
        shadowOf(Looper.getMainLooper()).idle()
        val ubicacion = consulta.await()
        assertEquals(-33.61, ubicacion!!.latitude, 0.0)
        assertEquals(-70.58, ubicacion.longitude, 0.0)
    }

    @Test fun avisosSonOpcionalesYLaSincronizacionManualConfirmaElEstado() {
        val gestor = contexto.getSystemService(NotificationManager::class.java)!!
        val simulador = shadowOf(gestor)
        AvisosSincronizacion.completada(contexto, 1)
        assertTrue(simulador.allNotifications.isEmpty())
        AvisosSincronizacion.configurar(contexto, true)
        AvisosSincronizacion.completada(contexto, 0)
        assertTrue(simulador.allNotifications.isEmpty())
        AvisosSincronizacion.completada(contexto, 0, manual = true)
        assertEquals("Tus datos están al día con el servidor.",
            simulador.allNotifications.single().extras.getCharSequence(Notification.EXTRA_TEXT))
        AvisosSincronizacion.completada(contexto, 1)
        assertEquals("Se envió 1 cambio al servidor.",
            simulador.allNotifications.single().extras.getCharSequence(Notification.EXTRA_TEXT))
        AvisosSincronizacion.completada(contexto, 3)
        assertEquals("Se enviaron 3 cambios al servidor.",
            simulador.allNotifications.single().extras.getCharSequence(Notification.EXTRA_TEXT))
        AvisosSincronizacion.configurar(contexto, false)
        assertTrue(simulador.allNotifications.isEmpty())
        simulador.setNotificationsEnabled(false)
        AvisosSincronizacion.configurar(contexto, true)
        AvisosSincronizacion.completada(contexto, 1)
        assertTrue(simulador.allNotifications.isEmpty())
    }
}
