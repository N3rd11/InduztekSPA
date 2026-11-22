package cl.induztek.app.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import cl.induztek.app.data.CuentasRepository
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.model.Rol
import cl.induztek.app.sync.AltaCliente
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class CuentasViewModel(private val repositorio: CuentasRepository) : ViewModel() {
    var cuentas by mutableStateOf(emptyList<UsuarioDemo>())
        private set
    var errores by mutableStateOf(emptyMap<String, String>())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ocupado by mutableStateOf(false)
        private set

    suspend fun cargar() { ejecutar { cuentas = repositorio.listar() } }

    suspend fun crear(alta: AltaCliente): Boolean {
        if (ocupado) return false
        errores = repositorio.validar(alta)
        if (errores.isNotEmpty()) return false
        return ejecutar {
            cuentas = cuentas + repositorio.crear(alta)
        }
    }

    suspend fun cambiarEstado(cuenta: UsuarioDemo, empresa: String? = null) {
        modificar(cuenta, cuenta.rol, empresa ?: cuenta.clienteId, !cuenta.activo)
    }

    suspend fun modificar(cuenta: UsuarioDemo, rol: Rol, empresa: String?, activo: Boolean): Boolean = ejecutar {
        val guardada = repositorio.cambiarEstado(cuenta.id, activo, rol, empresa)
        cuentas = cuentas.map { if (it.id == guardada.id) guardada else it }
    }

    private suspend fun ejecutar(operacion: suspend () -> Unit): Boolean {
        if (ocupado) return false
        ocupado = true
        error = null
        try {
            operacion()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            error = when (e.code()) {
                401, 403 -> "La cuenta no tiene permiso para realizar este cambio."
                409 -> "Ya existe una cuenta con esos datos o la cuenta cambió. Actualiza la lista."
                422 -> "Revisa los datos y confirma que la empresa esté sincronizada."
                else -> "No se pudo completar la operación en el servidor de usuarios."
            }
        } catch (_: Exception) {
            error = "No se pudo conectar con el servidor de usuarios."
        } finally {
            ocupado = false
        }
        return false
    }
}
