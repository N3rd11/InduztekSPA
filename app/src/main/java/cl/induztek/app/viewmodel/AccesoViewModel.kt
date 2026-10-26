package cl.induztek.app.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import cl.induztek.app.model.*

class AccesoViewModel(private val guardado: SavedStateHandle) : ViewModel() {
    var correo by mutableStateOf(guardado.get<String>("correo").orEmpty())
        private set
    // La contraseña no se persiste en disco ni en SavedStateHandle.
    var clave by mutableStateOf("")
        private set
    var errores by mutableStateOf(emptyMap<String, String>())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun correo(valor: String) { correo = valor; guardado["correo"] = valor; errores = errores - "correo"; error = null }
    fun clave(valor: String) { clave = valor; errores = errores - "clave"; error = null }
    fun elegir(usuario: UsuarioDemo) { correo(usuario.correo); clave("1234") }
    fun ingresar(): UsuarioDemo? {
        errores = ValidacionesFormulario.login(correo, clave)
        if (errores.isNotEmpty()) return null
        val usuario = UsuariosDemo.validar(correo.trim(), clave)
        error = if (usuario == null) "Correo o contraseña incorrectos." else null
        if (usuario != null) clave("")
        return usuario
    }
}
