
package cl.induztek.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import cl.induztek.app.model.ReglasAislacion
import cl.induztek.app.model.EvaluacionAislacion
import cl.induztek.app.model.ValidacionesFormulario

import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.data.RegistroAislacionEntity
import cl.induztek.app.data.PendienteSyncEntity
import cl.induztek.app.sync.ProgramarSync
import com.google.gson.Gson
import java.util.UUID

import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class RegistroViewModel(
    application: Application,
    private val usuarioId: String? = null
) : AndroidViewModel(application) {

    private val db = InduztekDatabase.obtener(application)
    private val dao = db.registroDao()

    var lectura1 by mutableStateOf("")
        private set

    var lectura10 by mutableStateOf("")
        private set

    var evaluacion by mutableStateOf<EvaluacionAislacion?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var mensaje by mutableStateOf<String?>(null)
        private set

    var historial by mutableStateOf(
        emptyList<RegistroAislacionEntity>()
    )
        private set

    var guardando by mutableStateOf(false)
        private set

    var errores by mutableStateOf(emptyMap<String, String>())
        private set

    init {
        viewModelScope.launch {
            try {
                dao.obtenerTodos().collect { registros -> historial = registros }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "No se pudo consultar el historial de mediciones."
            }
        }
    }

    fun cambiarLectura1(valor: String) {
        lectura1 = valor
        errores = errores - "lectura1"
        evaluacion = null
        error = null
        mensaje = null
    }

    fun cambiarLectura10(valor: String) {
        lectura10 = valor
        errores = errores - "lectura10"
        evaluacion = null
        error = null
        mensaje = null
    }

    fun calcular() {
        prepararRegistro()
    }

    private fun prepararRegistro(): RegistroAislacionEntity? {
        val lecturas = mapOf("lectura1" to lectura1, "lectura10" to lectura10)
        errores = ValidacionesFormulario.validar(lecturas, lecturas.keys)
        if (errores.isNotEmpty()) {
            error = "Revisa los campos indicados"
            evaluacion = null
            return null
        }

        val uno = lectura1.trim().replace(',', '.').toDouble()
        val diez = lectura10.trim().replace(',', '.').toDouble()
        return try {
            val resultado = ReglasAislacion.evaluar(uno, diez)
            evaluacion = resultado
            error = null
            RegistroAislacionEntity(
                equipoTag = "TR-01", instalacion = "Planta Lampa", lectura1 = uno, lectura10 = diez,
                indice = resultado.indice, resultado = resultado.resultado.name
            )
        } catch (e: IllegalArgumentException) {
            evaluacion = null
            error = e.message
            null
        }
    }

    fun guardar() {
        if (guardando) return
        val registro = prepararRegistro() ?: return
        guardando = true
        viewModelScope.launch {
            try {
                db.withTransaction {
                    dao.insertar(registro)
                    if (usuarioId != null) {
                        val id = UUID.randomUUID().toString()
                        db.mvpDao().encolar(PendienteSyncEntity(id, usuarioId, "MEDICION_AISLACION", id,
                            Gson().toJson(registro), registro.fechaHora))
                    }
                }
                usuarioId?.let { ProgramarSync.ejecutar(getApplication<Application>(), it) }
                mensaje = "Medición guardada correctamente"
                error = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "No se pudo guardar la medición. Intenta nuevamente."
                mensaje = null
            } finally {
                guardando = false
            }
        }
    }
}
