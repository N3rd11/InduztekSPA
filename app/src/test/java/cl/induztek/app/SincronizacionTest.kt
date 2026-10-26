package cl.induztek.app

import androidx.room.Room
import cl.induztek.app.data.*
import cl.induztek.app.model.UsuariosDemo
import cl.induztek.app.sync.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import android.content.Context
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SincronizacionTest {
    @Test fun erroresDeRedNoExponenDireccionesNiMensajesTecnicos() {
        assertEquals("No se pudo conectar con el servidor. Revisa tu conexión",
            detalleError(IOException("Failed to connect to /127.0.0.1:8081")))
        assertTrue(detalleError(SocketTimeoutException("timeout")).contains("tardó demasiado"))
        assertEquals("Revisa las direcciones en Configurar conexión",
            detalleError(IllegalArgumentException("Invalid URL: dirección interna")))
        assertEquals("No se pudo completar la sincronización. Intenta nuevamente",
            detalleError(IllegalStateException("Detalles internos del servidor")))
    }

    @Test fun erroresHttpDistinguenPermisosConflictosYDatosInvalidos() {
        fun error(codigo: Int) = detalleError(HttpException(Response.error<Unit>(codigo, ResponseBody.create(null, "Detalles internos"))))
        assertTrue(error(401).contains("validar tu cuenta"))
        assertTrue(error(403).contains("no tiene permiso"))
        assertTrue(error(409).contains("requieren revisión"))
        assertTrue(error(422).contains("Revisa los registros"))
        assertTrue(error(503).contains("no está disponible"))
        assertFalse(error(422).contains("Detalles internos"))
    }

    @Test fun estadoAntiguoNoMuestraExcepcionesYConservaConfiguracionYOtrosUsuarios() {
        val contexto = RuntimeEnvironment.getApplication() as Context
        val prefs = contexto.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
        prefs.edit().putString("estado-anterior", "Failed to connect to /127.0.0.1:8081")
            .putString("catalogo", "http://servidor-empresa:8081/").commit()
        val config = ConfigSync(contexto)
        assertEquals("Sincronización pendiente. Los cambios locales permanecen guardados.", config.estado("anterior"))
        assertEquals("http://servidor-empresa:8081/", config.catalogo)
        assertEquals("Sin conexión al servidor todavía", config.estado("otro"))
        val mensaje = "No se pudo conectar con el servidor. Revisa tu conexión"
        config.estado("anterior", mensaje)
        assertEquals(mensaje, ConfigSync(contexto).estado("anterior"))
    }

    private class ApiFicticia(var falla: Boolean) : SyncApi {
        val enviados = mutableListOf<PendienteSyncEntity>()
        override suspend fun sesion() = JsonObject().apply { addProperty("id", "tecnico1") }
        override suspend fun snapshot() = EstadoLocal()
        override suspend fun enviar(evento: PendienteSyncEntity): Confirmacion {
            if (falla) throw IOException("Sin señal")
            enviados.add(evento)
            return Confirmacion(evento.id)
        }
    }
    @Test fun corteNoPierdeCambiosYConfirmacionNoBorraDatosLocales() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InduztekDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = ProtocolosRepository(db); repo.iniciar()
            val u = UsuariosDemo.todos.first { it.id == "tecnico1" }
            repo.cliente(u, ClienteEntity("prueba-sync", "Empresa de prueba", "12.345.678-5"))
            val api = ApiFicticia(true)
            try { Sincronizador(db, api, api).sincronizar(u); fail("Debe informar el corte") } catch (_: IOException) { }
            assertEquals(1,repo.dao.pendientes(u.id).size)
            api.falla = false
            assertEquals(1,Sincronizador(db,api,api).sincronizar(u))
            assertTrue(repo.dao.pendientes(u.id).isEmpty())
            assertTrue(repo.cargar().clientes.any { it.id == "prueba-sync" })
            assertEquals("CLIENTE",api.enviados.single().tipo)
        } finally { db.close() }
    }
}
