
package cl.induztek.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import cl.induztek.app.model.ReglasAislacion
import cl.induztek.app.model.EvaluacionAislacion
import cl.induztek.app.model.ValidacionesFormulario

import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.data.RegistroAislacionEntity

import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class RegistroViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val dao = InduztekDatabase
        .obtener(application)
        .registroDao()

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
                error = "No se pudo consultar el historial: ${e.message}"
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
        errores = ValidacionesFormulario.validar(mapOf("lectura1" to lectura1, "lectura10" to lectura10), setOf("lectura1", "lectura10"))
        if (errores.isNotEmpty()) { error = "Revisa los campos indicados"; evaluacion = null; return }
        val uno = lectura1.replace(",", ".").toDoubleOrNull()
        val diez = lectura10.replace(",", ".").toDoubleOrNull()

        if (uno == null || diez == null) {
            error = "Ingresa valores numéricos válidos"
            evaluacion = null
            return
        }

        try {
            evaluacion = ReglasAislacion.evaluar(uno, diez)
            error = null
        } catch (e: IllegalArgumentException) {
            evaluacion = null
            error = e.message
        }
    }

    fun guardar() {

        if (guardando) return

        if (evaluacion == null) {
            calcular()
        }

        val resultado = evaluacion ?: return

        val uno = lectura1.replace(",", ".").toDoubleOrNull()
            ?: return

        val diez = lectura10.replace(",", ".").toDoubleOrNull()
            ?: return

        guardando = true
        viewModelScope.launch {
            try {
                dao.insertar(
                    RegistroAislacionEntity(
                        equipoTag = "TR-01",
                        instalacion = "Planta Lampa",
                        lectura1 = uno,
                        lectura10 = diez,
                        indice = resultado.indice,
                        resultado = resultado.resultado.name
                    )
                )

                mensaje = "Medición guardada correctamente"
                error = null

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "No se pudo guardar: ${e.message}"
                mensaje = null
            } finally {
                guardando = false
            }
        }
    }
}
