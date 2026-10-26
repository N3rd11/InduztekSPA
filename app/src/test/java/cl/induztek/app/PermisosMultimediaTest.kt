package cl.induztek.app

import android.Manifest
import cl.induztek.app.model.AccesoFotos
import cl.induztek.app.model.PermisosMultimedia as P
import org.junit.Assert.*
import org.junit.Test

class PermisosMultimediaTest {
    @Test fun androidOchoADoceSolicitaLecturaYCamara() {
        assertEquals(setOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE), P.pendientes(26) { false }.toSet())
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), P.permisosFotos(32))
    }
    @Test fun androidTreceSolicitaSoloFotosSinVideos() {
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), P.permisosFotos(33))
        assertEquals(2, P.pendientes(33) { false }.size)
    }
    @Test fun androidCatorcePermiteSeleccionParcial() {
        assertTrue(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in P.permisosFotos(34))
        val concedido: (String) -> Boolean = { it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        assertEquals(AccesoFotos.SELECCIONADAS, P.estado(34, concedido).fotos)
        assertEquals(listOf(Manifest.permission.CAMERA), P.pendientes(34, concedido))
    }
    @Test fun permisosCompletosNoSeVuelvenASolicitar() {
        val concedido: (String) -> Boolean = { it in setOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES) }
        assertTrue(P.pendientes(37, concedido).isEmpty())
        assertTrue(P.estado(37, concedido).camara)
        assertEquals(AccesoFotos.COMPLETO, P.estado(37, concedido).fotos)
    }
    @Test fun accesoRechazadoSeRepresentaSinDarPermisosFalsos() {
        assertFalse(P.estado(37) { false }.camara)
        assertEquals(AccesoFotos.NINGUNO, P.estado(37) { false }.fotos)
    }
    @Test fun permisoAntiguoNoConcedeAccesoEnVersionModerna() {
        val concedido: (String) -> Boolean = { it == Manifest.permission.READ_EXTERNAL_STORAGE }
        assertEquals(AccesoFotos.COMPLETO, P.estado(32, concedido).fotos)
        assertEquals(AccesoFotos.NINGUNO, P.estado(33, concedido).fotos)
    }
}
