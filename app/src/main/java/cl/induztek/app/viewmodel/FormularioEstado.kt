package cl.induztek.app.viewmodel

import androidx.compose.runtime.*
import cl.induztek.app.model.ValidacionesFormulario

/** Estado de edición propiedad del ViewModel, con borrador persistido en Room. */
class FormularioEstado(inicial: Map<String, String>, private val persistir: (Map<String, String>) -> Unit) {
    var valores by mutableStateOf(inicial)
        private set
    var errores by mutableStateOf(emptyMap<String, String>())
        private set
    private var requeridos = emptySet<String>()
    operator fun get(campo: String) = valores[campo].orEmpty()
    fun cambiar(campo: String, valor: String) {
        valores = valores + (campo to valor)
        errores = ValidacionesFormulario.validar(valores, requeridos)
        persistir(valores)
    }
    fun requerir(vararg campos: String): Boolean {
        requeridos = campos.toSet()
        errores = ValidacionesFormulario.validar(valores, requeridos)
        return errores.isEmpty()
    }
    fun error(campo: String, mensaje: String) { errores = errores + (campo to mensaje) }
}
