package cl.induztek.app.model

import android.Manifest

enum class AccesoFotos { NINGUNO, SELECCIONADAS, COMPLETO }
data class EstadoPermisosMultimedia(val camara: Boolean, val fotos: AccesoFotos)

/** Diferencias de permisos Android 8–12, 13 y 14+. No requiere estado de la UI. */
object PermisosMultimedia {
    fun permisosFotos(sdk: Int): List<String> = when {
        sdk >= 34 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        sdk >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun estado(sdk: Int, concedido: (String) -> Boolean): EstadoPermisosMultimedia {
        val fotos = when {
            sdk >= 33 && concedido(Manifest.permission.READ_MEDIA_IMAGES) -> AccesoFotos.COMPLETO
            sdk >= 34 && concedido(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> AccesoFotos.SELECCIONADAS
            sdk < 33 && concedido(Manifest.permission.READ_EXTERNAL_STORAGE) -> AccesoFotos.COMPLETO
            else -> AccesoFotos.NINGUNO
        }
        return EstadoPermisosMultimedia(concedido(Manifest.permission.CAMERA), fotos)
    }

    fun pendientes(sdk: Int, concedido: (String) -> Boolean): List<String> = buildList {
        val actual = estado(sdk, concedido)
        if (!actual.camara) add(Manifest.permission.CAMERA)
        if (actual.fotos == AccesoFotos.NINGUNO) addAll(permisosFotos(sdk))
    }
}
