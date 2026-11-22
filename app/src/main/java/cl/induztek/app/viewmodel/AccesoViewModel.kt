package cl.induztek.app.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import cl.induztek.app.model.*
import cl.induztek.app.sync.ServicioNoDisponible
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

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
    var verificando by mutableStateOf(false)
        private set

    fun correo(valor: String) { correo = valor; guardado["correo"] = valor; errores = errores - "correo"; error = null }
    fun clave(valor: String) { clave = valor; errores = errores - "clave"; error = null }
    suspend fun ingresar(verificar: suspend (String, String) -> UsuarioDemo?): UsuarioDemo? {
        if (verificando) return null
        errores = ValidacionesFormulario.login(correo, clave)
        if (errores.isNotEmpty()) return null
        val correoNormalizado = correo.trim()
        verificando = true
        error = null
        val contrasena = clave
        try {
            val cuenta = verificar(correoNormalizado, contrasena)?.takeIf { it.activo }
            if (cuenta != null) clave("")
            else error = "No se pudo validar el perfil de esta cuenta."
            return cuenta
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServicioNoDisponible) {
            error = "No se pudo conectar con ${e.servicio}. Revisa la conexión e intenta nuevamente."
        } catch (e: HttpException) {
            error = if (e.code() in setOf(401, 403)) "Correo o contraseña incorrectos." else "El servicio de acceso no está disponible."
        } catch (_: IOException) {
            error = "Conéctate al servidor para iniciar sesión."
        } catch (e: Exception) {
            error = "Revisa la configuración del servicio de acceso e intenta nuevamente."
        } finally {
            verificando = false
        }
        return null
    }
}
