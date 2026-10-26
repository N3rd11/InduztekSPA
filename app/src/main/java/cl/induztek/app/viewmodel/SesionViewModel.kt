package cl.induztek.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.model.UsuariosDemo

class SesionViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var usuario by mutableStateOf(UsuariosDemo.todos.find { it.id == savedState.get<String>("usuario") && it.activo })
        private set

    fun acceder(valor: UsuarioDemo) {
        require(valor.activo) { "Usuario desactivado" }
        usuario = valor
        savedState["usuario"] = valor.id
    }

    fun cerrar() {
        usuario = null
        savedState["usuario"] = null
    }
}
