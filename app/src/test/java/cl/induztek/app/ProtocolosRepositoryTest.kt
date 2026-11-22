package cl.induztek.app

import androidx.room.Room
import cl.induztek.app.data.*
import cl.induztek.app.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProtocolosRepositoryTest {
    private val tecnico = UsuariosDemo.todos.first { it.rol == Rol.TECNICO }
    private val ingeniero = UsuariosDemo.todos.first { it.rol == Rol.INGENIERO }
    private val jefe = UsuariosDemo.todos.first { it.rol == Rol.JEFE_TECNICO }

    private fun ejecutar(bloque: suspend ProtocolosRepository.() -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InduztekDatabase::class.java).build()
        try {
            val repo = ProtocolosRepository(db)
            repo.iniciar()
            repo.bloque()
        } finally {
            db.close()
        }
    }

    private suspend fun denegado(mensaje: String, operacion: suspend () -> Unit) {
        try {
            operacion()
            fail("Se esperaba el rechazo: $mensaje")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(mensaje))
        }
    }

    @Test fun catalogosConservanIdentidadEHistorialDeCalibracion() = ejecutar {
        val cliente = ClienteEntity("c-nuevo", "Cliente de prueba", "12.345.678-5")
        cliente(tecnico, cliente)
        assertEquals("12345678-5", cargar().clientes.first { it.id == cliente.id }.rut)
        denegado("Ya existe un cliente") { cliente(tecnico, cliente.copy(id = "c-duplicado")) }
        val planta = InstalacionEntity("i-nueva", cliente.id, "Planta de prueba")
        instalacion(tecnico, planta)
        denegado("cambiar de empresa") { instalacion(tecnico, planta.copy(clienteId = "cliente-pacifico")) }
        val equipo = EquipoEntity("e-nuevo", planta.id, " tra-1 ", "TRANSFORMADOR", placa = "500 kVA")
        equipo(tecnico, equipo)
        assertEquals("TRA-1", cargar().equipos.first { it.id == equipo.id }.tag)
        denegado("ya existe") { equipo(tecnico, equipo.copy(id = "e-duplicado")) }
        denegado("referencias de placa") { equipo(tecnico, equipo.copy(placa = "600 kVA")) }
        val medidor = InstrumentoEntity("i-medidor", "Medidor de prueba", "", "", "UNICO")
        val cal = CalibracionEntity("cal-nueva", medidor.id, "2026-01-01", "2027-01-01")
        denegado("Solo el jefe técnico") { instrumento(tecnico, medidor, cal) }
        denegado("antes de su emisión") { instrumento(jefe, medidor, cal.copy(vencimiento = "2025-12-31")) }
        instrumento(jefe, medidor, cal)
        instrumento(jefe, medidor, cal.copy(id = "cal-renovada", fecha = "2027-01-01", vencimiento = "2028-01-01"))
        assertEquals(2, cargar().calibraciones.count { it.instrumentoId == medidor.id })
    }

    @Test fun borraPruebaConMedicionesYRechazaEdicionAjena() = ejecutar {
        val visita = VisitaEntity("v", "molienda", "OT-REPO", LocalDate.now().toString(), ingeniero.id, "SOLO_PRUEBAS", tecnico.id)
        visita(tecnico, visita, listOf(tecnico.id))
        denegado("OT ya está registrada") { visita(tecnico, visita, listOf(tecnico.id)) }
        val protocolo = protocolo(tecnico, visita.id, "rel50")
        denegado("ya tiene protocolo") { protocolo(tecnico, visita.id, "rel50") }
        denegado("humedad") { datos(tecnico, protocolo, 20.0, 130.0, "", "") }
        datos(tecnico, protocolo, 20.0, 50.0, "", "")
        val entrada = PruebaEntity("t", protocolo, "OPERACION_RELE", "50/51", "UNICA", true, "", "inyeccion", null, "")
        val valores = mapOf("ajuste" to 5.0, "corriente" to 5.1, "esperado" to 100.0, "tiempo" to 104.0)
        denegado("Selecciona un instrumento") { prueba(tecnico, entrada.copy(instrumentoId = null), valores) }
        prueba(tecnico, entrada, valores)
        assertTrue(cargar().mediciones.any { it.pruebaId == entrada.id })
        val otroTecnico = UsuariosDemo.todos.first { it.rol == Rol.TECNICO && it.id != tecnico.id }
        denegado("No puedes modificar") { eliminarPrueba(otroTecnico, protocolo, entrada.id) }
        denegado("ya no está disponible") { eliminarPrueba(tecnico, protocolo, "ausente") }
        eliminarPrueba(tecnico, protocolo, entrada.id)
        val datos = cargar()
        assertFalse(datos.pruebas.any { it.id == entrada.id })
        assertFalse(datos.mediciones.any { it.pruebaId == entrada.id })
        assertEquals(3L, datos.protocolos.first { it.id == protocolo }.revision)
        assertEquals("ELIMINAR_PRUEBA", dao.pendientes(tecnico.id).last().tipo)
        denegado("Falta registrar") { transicion(tecnico, protocolo, EstadoProtocolo.REGISTRADO_TERRENO, "") }
    }

    @Test fun ingenieroEvaluaPruebaManualYMantieneElOrigen() = ejecutar {
        val visita = VisitaEntity("manual", "lampa", "OT-MANUAL", LocalDate.now().toString(), ingeniero.id, "PRUEBAS_Y_MANT", tecnico.id)
        visita(tecnico, visita, listOf(tecnico.id))
        val protocolo = protocolo(tecnico, visita.id, "tr01")
        val previa = PruebaEntity("previa", protocolo, "DEVANADOS", "AT-U", "PREVIA", true, "", "meg5", null, "")
        prueba(tecnico, previa, mapOf("resistencia" to 10.0))
        assertEquals("Pendiente de evaluación", ProtocolosRepository.global(cargar(), protocolo))
        denegado("No mezcles") { prueba(tecnico, previa.copy(id = "unica", momento = "UNICA"), mapOf("resistencia" to 10.0)) }
        denegado("Requiere ingeniero") { evaluarManual(tecnico, previa.id, ResultadoPrueba.APROBADO, "Revisión") }
        evaluarManual(ingeniero, previa.id, ResultadoPrueba.OBSERVADO, "Revisión de devanado")
        assertEquals("OBSERVADO", ProtocolosRepository.global(cargar(), protocolo))
        val posterior = previa.copy(id = "posterior", momento = "POSTERIOR", realizada = false, motivo = "Sin acceso")
        prueba(tecnico, posterior, emptyMap())
        assertEquals(previa.id, ProtocolosRepository.efectivas(cargar(), protocolo).single().id)
        denegado("no realizada") { evaluarManual(ingeniero, posterior.id, ResultadoPrueba.APROBADO, "Revisión") }
    }
}
