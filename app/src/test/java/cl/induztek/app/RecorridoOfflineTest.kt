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
class RecorridoOfflineTest {
    @Test fun registroActaRevisionYBloqueoSinConexion() = runBlocking {
        val contexto = RuntimeEnvironment.getApplication()
        val nombre = "recorrido-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(contexto, InduztekDatabase::class.java, nombre).allowMainThreadQueries().build()
        try {
            val repo = ProtocolosRepository(db)
            repo.iniciar()
            val tecnico = UsuariosDemo.todos.first { it.id == "tecnico1" }
            val ingeniero = UsuariosDemo.todos.first { it.id == "ingeniero1" }
            val cliente = UsuariosDemo.todos.first { it.id == "cliente1" }
            val visita = VisitaEntity("nueva", "molienda", "OT-PRUEBA", LocalDate.now().toString(), ingeniero.id, "SOLO_PRUEBAS", tecnico.id)
            repo.visita(tecnico, visita, listOf(tecnico.id))
            val id = repo.protocolo(tecnico, visita.id, "rel50")
            repo.datos(tecnico, id, 20.0, 50.0, "", "Prueba offline")
            repo.prueba(tecnico, PruebaEntity("medicion", id, "OPERACION_RELE", "50/51", "UNICA", true, "", "inyeccion", null, ""), mapOf("ajuste" to 5.0, "corriente" to 5.1, "esperado" to 100.0, "tiempo" to 104.0))
            repo.transicion(tecnico, id, EstadoProtocolo.REGISTRADO_TERRENO, "")
            assertEquals("APROBADO", ProtocolosRepository.global(repo.cargar(), id))
            try { repo.transicion(tecnico, id, EstadoProtocolo.FORMALIZADO, ""); fail("El técnico no debe formalizar") } catch (_: IllegalArgumentException) { }
            repo.cerrar(tecnico, visita.id, "Marcela Fuentes", "14.226.791-8", "Jefa de mantenimiento")
            val acta = repo.cargar().visitas.first { it.id == visita.id }.acta
            assertTrue(acta.contains("PENDIENTE DE FORMALIZACIÓN"))
            assertTrue(acta.contains("REL-50/51-A"))
            assertTrue(acta.contains("Marcela Fuentes"))
            repo.transicion(ingeniero, id, EstadoProtocolo.FORMALIZADO, "Revisado")
            try { repo.datos(ingeniero, id, 30.0, 60.0, "", "Cambio"); fail("Formalizado inmutable") } catch (_: IllegalArgumentException) { }
            try { repo.cliente(cliente, ClienteEntity("ilegal", "Empresa", "76.482.915-8")); fail("Cliente solo consulta") } catch (_: IllegalArgumentException) { }
            assertTrue(repo.dao.pendientes(tecnico.id).isNotEmpty())
            assertEquals(2, repo.cargar().historial.count { it.protocoloId == id && it.nuevo in setOf("REGISTRADO_TERRENO", "FORMALIZADO") })
            db.close()
            val reabierta = Room.databaseBuilder(contexto, InduztekDatabase::class.java, nombre).allowMainThreadQueries().build()
            try {
                val persistido = ProtocolosRepository(reabierta).cargar()
                assertEquals("FORMALIZADO", persistido.protocolos.first { it.id == id }.estado)
                assertEquals(acta, persistido.visitas.first { it.id == visita.id }.acta)
                assertTrue(reabierta.mvpDao().pendientes(tecnico.id).isNotEmpty())
            } finally { reabierta.close() }
        } finally { db.close() }
    }
}
