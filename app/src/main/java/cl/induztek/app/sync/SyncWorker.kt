package cl.induztek.app.sync

import android.content.Context
import androidx.work.*
import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.model.UsuariosDemo
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class SyncWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val usuario = UsuariosDemo.todos.find { it.id == inputData.getString("usuario") && it.activo } ?: return Result.failure()
        val config = ConfigSync(applicationContext)
        return try {
            val cantidad = Sincronizador(InduztekDatabase.obtener(applicationContext),
                ServiciosRemotos.crear(config.catalogo, usuario), ServiciosRemotos.crear(config.protocolos, usuario)).sincronizar(usuario)
            config.estado(usuario.id, "Sincronización completada: $cantidad cambios enviados")
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            config.estado(usuario.id, "Pendiente de sincronización: ${detalleError(e)}. Los cambios locales se conservan.")
            if (e is HttpException && e.code() in setOf(401, 403, 422)) Result.failure() else Result.retry()
        }
    }
}

class ConfigSync(contexto: Context) {
    private val prefs = contexto.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
    var catalogo: String
        get() = prefs.getString("catalogo", "http://127.0.0.1:8081/")!!
        set(valor) { prefs.edit().putString("catalogo", valor).apply() }
    var protocolos: String
        get() = prefs.getString("protocolos", "http://127.0.0.1:8082/")!!
        set(valor) { prefs.edit().putString("protocolos", valor).apply() }
    fun estado(usuarioId: String, mensaje: String) {
        prefs.edit().putString("estado-$usuarioId", mensaje).putInt("formato-estado-$usuarioId", 1).apply()
    }
    fun estado(usuarioId: String): String {
        if (!prefs.contains("estado-$usuarioId")) return "Sin conexión al servidor todavía"
        // Las versiones anteriores guardaban mensajes técnicos de excepciones.
        if (prefs.getInt("formato-estado-$usuarioId", 0) < 1)
            return "Sincronización pendiente. Los cambios locales permanecen guardados."
        return prefs.getString("estado-$usuarioId", "Sin conexión al servidor todavía")!!
    }
}

object ProgramarSync {
    fun ejecutar(contexto: Context, usuarioId: String) {
        val solicitud = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf("usuario" to usuarioId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(contexto).enqueueUniqueWork("sync-$usuarioId", ExistingWorkPolicy.APPEND_OR_REPLACE, solicitud)
    }
}
