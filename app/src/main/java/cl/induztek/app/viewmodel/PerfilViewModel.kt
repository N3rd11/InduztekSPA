package cl.induztek.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.induztek.app.data.FotoPerfilRepository
import cl.induztek.app.sync.ProgramarSync
import cl.induztek.app.sync.ServiciosRemotos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PerfilViewModel(application: Application, private val usuarioId: String) : AndroidViewModel(application) {
    private val fotos = FotoPerfilRepository(application)
    var foto by mutableStateOf<Bitmap?>(null)
        private set
    var ocupado by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        ejecutar {
            foto = fotos.cargar(usuarioId)
            try {
                ServiciosRemotos.cuentaEnMemoria(usuarioId)?.let { foto = fotos.sincronizar(it, descargar = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (foto != null) ProgramarSync.ejecutar(application, usuarioId)
            }
            null
        }
    }

    private fun ejecutar(accion: suspend () -> String?) {
        if (ocupado) return
        ocupado = true; mensaje = null; error = null
        viewModelScope.launch {
            try { mensaje = accion() }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "No se pudo guardar la fotografía" }
            finally { ocupado = false }
        }
    }

    fun desdeCamara(imagen: Bitmap?) {
        if (imagen == null) { mensaje = "Captura cancelada; se conserva tu foto"; return }
        ejecutar { foto = fotos.guardar(usuarioId, imagen); enviar("Fotografía guardada") }
    }

    fun desdeGaleria(uri: Uri?) {
        if (uri == null) { mensaje = "Selección cancelada; se conserva tu foto"; return }
        ejecutar { foto = fotos.importar(usuarioId, uri); enviar("Imagen de galería guardada") }
    }

    private suspend fun enviar(confirmacion: String): String {
        val usuario = ServiciosRemotos.cuentaEnMemoria(usuarioId)
            ?: return confirmacion
        return try {
            fotos.sincronizar(usuario)
            confirmacion
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ProgramarSync.ejecutar(getApplication<Application>(), usuarioId)
            "Fotografía guardada en el dispositivo. Envío pendiente."
        }
    }

    fun noDisponible(recurso: String) { error = "No se pudo abrir $recurso. Comprueba que exista una aplicación compatible." }
    fun permisoCamaraDenegado() { error = "Permiso de cámara denegado. Puedes permitirlo en Ajustes o elegir una imagen de la galería." }
}
