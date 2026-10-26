package cl.induztek.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.induztek.app.data.FotoPerfilRepository
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

    init { ejecutar(null) { foto = fotos.cargar(usuarioId) } }

    private fun ejecutar(confirmacion: String?, accion: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true; mensaje = null; error = null
        viewModelScope.launch {
            try { accion(); mensaje = confirmacion }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "No se pudo guardar la fotografía" }
            finally { ocupado = false }
        }
    }

    fun desdeCamara(imagen: Bitmap?) {
        if (imagen == null) { mensaje = "Captura cancelada; se conserva tu foto"; return }
        ejecutar("Fotografía guardada") { foto = fotos.guardar(usuarioId, imagen) }
    }

    fun desdeGaleria(uri: Uri?) {
        if (uri == null) { mensaje = "Selección cancelada; se conserva tu foto"; return }
        ejecutar("Imagen de galería guardada") { foto = fotos.importar(usuarioId, uri) }
    }

    fun noDisponible(recurso: String) { error = "No se pudo abrir $recurso. Comprueba que exista una aplicación compatible." }
    fun permisoCamaraDenegado() { error = "Permiso de cámara denegado. Puedes permitirlo en Ajustes o elegir una imagen de la galería." }
}
