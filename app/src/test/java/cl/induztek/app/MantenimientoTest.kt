package cl.induztek.app

import cl.induztek.app.data.*
import org.junit.Assert.*
import org.junit.Test

class MantenimientoTest {
    @Test fun posteriorRealizadaSustituyePreviaPeroLaOmitidaNoOcultaUnResultadoReal() {
        val previa = PruebaEntity("previa", "p", "CONTACTOS", "Polo 1", "PREVIA", true, "", "i", "RECHAZADO", "")
        val posterior = previa.copy(id = "posterior", momento = "POSTERIOR", resultado = "APROBADO")
        val s = EstadoLocal(pruebas = listOf(previa, posterior))
        assertEquals("APROBADO", ProtocolosRepository.global(s, "p"))
        val omitida = posterior.copy(realizada = false, resultado = null, motivo = "Término de ventana")
        assertEquals("RECHAZADO", ProtocolosRepository.global(s.copy(pruebas = listOf(previa, omitida)), "p"))
        assertEquals(2, s.pruebas.size)
        val sinEvaluar = posterior.copy(id = "manual", elemento = "Polo 2", resultado = null)
        assertEquals("Pendiente de evaluación", ProtocolosRepository.global(s.copy(pruebas = listOf(posterior, sinEvaluar)), "p"))
    }
}
