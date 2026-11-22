package cl.induztek.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.induztek.app.data.PortalClienteDatos
import cl.induztek.app.data.datosPortalCliente
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant

class PortalClienteViewModel(application: Application, val usuario: UsuarioDemo) : AndroidViewModel(application) {
    var datos by mutableStateOf<PortalClienteDatos?>(null)
        private set
    var consultando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var actualizado by mutableStateOf<Instant?>(null)
        private set

    fun actualizar() {
        if (consultando) return
        consultando = true
        error = null
        viewModelScope.launch {
            try {
                val empresa = requireNotNull(usuario.clienteId?.takeIf { it.isNotBlank() })
                val config = ConfigSync(getApplication())
                datos = coroutineScope {
                    // La consulta del cliente se construye con lo publicado por los servicios.
                    val catalogo = async { ServiciosRemotos.crear(config.catalogo, usuario).snapshot() }
                    val protocolos = async { ServiciosRemotos.crear(config.protocolos, usuario).snapshot() }
                    datosPortalCliente(empresa, catalogo.await(), protocolos.await())
                }
                actualizado = Instant.now()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is HttpException && e.code() in setOf(401, 403)) {
                    datos = null
                    actualizado = null
                }
                error = if (datos == null) "No pudimos consultar los trabajos. Intenta actualizar."
                    else "No pudimos actualizar. Estás viendo la última consulta."
            } finally {
                consultando = false
            }
        }
    }
}
