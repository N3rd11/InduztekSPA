package cl.induztek.app

import cl.induztek.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReglasProtocolosTest {
    private val tecnico = UsuariosDemo.todos.first { it.rol == Rol.TECNICO }
    private val ingeniero = UsuariosDemo.todos.first { it.rol == Rol.INGENIERO }

    @Test fun limitesDeAislacion() {
        assertEquals(ResultadoPrueba.RECHAZADO, ReglasAislacion.evaluar(99.0, 300.0).resultado)
        assertEquals(ResultadoPrueba.OBSERVADO, ReglasAislacion.evaluar(100.0, 100.0).resultado)
        assertEquals(ResultadoPrueba.APROBADO, ReglasAislacion.evaluar(100.0, 200.0).resultado)
        assertEquals(1.8, ReglasAislacion.evaluar(6100.0, 10980.0).indice, 0.001)
    }
    @Test fun limitesTtrContactosYRele() {
        fun ttr(m: Double) = ReglasProtocolos.evaluar(TipoEquipo.TRANSFORMADOR, TipoPrueba.TTR, mapOf("teorica" to 100.0, "medida" to m, "tap" to 3.0), null).resultado
        assertEquals(ResultadoPrueba.APROBADO, ttr(100.5))
        assertEquals(ResultadoPrueba.OBSERVADO, ttr(101.0))
        assertEquals(ResultadoPrueba.RECHAZADO, ttr(101.1))
        assertEquals(ResultadoPrueba.OBSERVADO, ReglasProtocolos.evaluar(TipoEquipo.TRANSFORMADOR, TipoPrueba.TTR, mapOf("teorica" to 30.0, "medida" to 30.3, "tap" to 3.0), null).resultado)
        assertEquals(ResultadoPrueba.OBSERVADO, ReglasProtocolos.evaluar(TipoEquipo.INTERRUPTOR, TipoPrueba.CONTACTOS, mapOf("resistencia" to 72.0), 60.0).resultado)
        assertEquals(ResultadoPrueba.RECHAZADO, ReglasProtocolos.evaluar(TipoEquipo.RELE, TipoPrueba.OPERACION_RELE, emptyMap(), null, false).resultado)
    }
    @Test fun tecnicoNoFormalizaYFormalizadoEsInmutable() {
        assertThrows(IllegalArgumentException::class.java) { ReglasProtocolos.validarTransicion(tecnico, tecnico.id, EstadoProtocolo.REGISTRADO_TERRENO, EstadoProtocolo.FORMALIZADO, "") }
        ReglasProtocolos.validarTransicion(ingeniero, tecnico.id, EstadoProtocolo.REGISTRADO_TERRENO, EstadoProtocolo.FORMALIZADO, "")
        assertThrows(IllegalArgumentException::class.java) { ReglasProtocolos.validarTransicion(ingeniero, tecnico.id, EstadoProtocolo.FORMALIZADO, EstadoProtocolo.ANULADO, "Error") }
        assertFalse(ReglasProtocolos.editable(tecnico, "otro", EstadoProtocolo.BORRADOR))
    }
    @Test fun rutVariacionYPlazo() {
        assertTrue(ReglasProtocolos.rutValido("76.482.915-8"))
        assertFalse(ReglasProtocolos.rutValido("76.482.915-0"))
        assertEquals(-37.7551, ReglasProtocolos.variacion(6100.0, 9800.0)!!, 0.001)
        assertFalse(ReglasProtocolos.atrasado(LocalDate.of(2026,10,23), LocalDate.of(2026,10,27)))
        assertTrue(ReglasProtocolos.atrasado(LocalDate.of(2026,10,23), LocalDate.of(2026,10,28)))
        assertEquals(1.25, ReglasProtocolos.numero("1,25"), 0.0)
        assertThrows(IllegalArgumentException::class.java) { ReglasProtocolos.numero("NaN") }
    }
}
