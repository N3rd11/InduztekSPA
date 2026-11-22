package cl.induztek.app

import cl.induztek.app.model.ValidacionesFormulario as V
import cl.induztek.app.viewmodel.FormularioEstado
import org.junit.Assert.*
import org.junit.Test

class ValidacionesFormularioTest {
    @Test fun loginDevuelveErroresPorCampoSinAutenticarDatosInvalidos() {
        assertEquals(setOf("correo", "clave"), V.login("", "").keys)
        assertTrue(V.login("mgodoy@induztek.ejemplo", "1234").isEmpty())
        assertTrue(V.login("correo incorrecto", "1234").containsKey("correo"))
        assertTrue(V.login("nombre@empresa..cl", "1234").containsKey("correo"))
        assertTrue(V.login("nombre@empresa.cl", "ñ".repeat(37)).containsKey("clave"))
    }
    @Test fun rutYContactoSeValidanAunqueSeanOpcionales() {
        assertNull(V.campo("rut", "76.482.915-8"))
        assertNotNull(V.campo("rut", "76.482.915-9"))
        assertNotNull(V.campo("correo", "ana@"))
        assertNotNull(V.campo("telefono", "abcdefghi"))
        assertNotNull(V.campo("telefono", "+()- - -"))
        assertNull(V.campo("telefono", "+56 9 0000 0101"))
        assertNull(V.campo("correo", ""))
    }
    @Test fun fechaInvalidaOVencimientoAnteriorSeAsocianASuCampo() {
        assertNotNull(V.campo("fecha", "2026-02-30"))
        assertEquals(setOf("vence"), V.validar(mapOf("fecha" to "2026-10-25", "vence" to "2026-01-01"), emptySet()).keys)
    }
    @Test fun numerosFinitosPositivosYTapEntero() {
        for (valor in listOf("0", "-2", "NaN", "Infinity", "texto")) assertNotNull(valor, V.campo("lectura1", valor))
        assertNull(V.campo("lectura1", "100,5"))
        assertNotNull(V.campo("tap", "1.5"))
        assertNull(V.campo("tap", "2"))
    }
    @Test fun condicionesAmbientalesAdmitenLimitesYRechazanFueraDeRango() {
        assertNull(V.campo("temperatura", "-80")); assertNull(V.campo("temperatura", "100"))
        assertNotNull(V.campo("temperatura", "101"))
        assertNull(V.campo("humedad", "0")); assertNull(V.campo("humedad", "100"))
        assertNotNull(V.campo("humedad", "100.1"))
    }
    @Test fun pruebaNoRealizadaNoSeBloqueaPorMedicionesOcultas() {
        assertTrue(V.validar(mapOf("realizada" to "false", "lectura1" to "mal", "motivo" to "Sin corte autorizado"), setOf("motivo")).isEmpty())
        assertTrue(V.validar(mapOf("opera" to "false", "tiempo" to "mal"), emptySet()).isEmpty())
    }
    @Test fun formularioConservaValoresYActualizaErroresAlCorregir() {
        var persistido = emptyMap<String, String>()
        val f = FormularioEstado(mapOf("nombre" to "Planta")) { persistido = it }
        assertFalse(f.requerir("nombre", "rut"))
        f.cambiar("rut", "76.482.915-8")
        assertTrue(f.errores.isEmpty())
        assertEquals("Planta", persistido["nombre"])
        assertEquals("76.482.915-8", persistido["rut"])
    }
    @Test fun textosConLimitesYAnioSeValidan() {
        assertNotNull(V.campo("tag", "X".repeat(31)))
        assertNotNull(V.campo("anio", "1899"))
        assertNull(V.campo("anio", "2020"))
    }
}
