package cl.induztek.app

import androidx.test.platform.app.InstrumentationRegistry
import cl.induztek.app.data.*
import cl.induztek.app.model.*
import cl.induztek.app.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Ejecutar con -Pandroid.testInstrumentationRunnerArguments.backendIntegration=true y ambos servicios activos. */
@RequiereBackend
class RetrofitIntegrationTest {
    @Test fun roomRetrofitActaYFormalizacionConLosDosServicios() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("backendIntegration") == "true")
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val db = InduztekDatabase.obtener(contexto)
        val repo = ProtocolosRepository(db); repo.iniciar()
        val tecnico = UsuariosDemo.todos.first { it.id == "tecnico1" }
        val ingeniero = UsuariosDemo.todos.first { it.id == "ingeniero1" }
        val visitaId = UUID.randomUUID().toString()
        repo.visita(tecnico, VisitaEntity(visitaId, "molienda", "OT-TEST-$visitaId", LocalDate.now().toString(), ingeniero.id, "SOLO_PRUEBAS", tecnico.id), listOf(tecnico.id))
        val protocoloId = repo.protocolo(tecnico, visitaId, "rel50")
        repo.datos(tecnico, protocoloId, 20.0, 50.0, "", "Prueba de integración ficticia")
        repo.prueba(tecnico, PruebaEntity(UUID.randomUUID().toString(), protocoloId, "OPERACION_RELE", "50/51", "UNICA", true, "", "inyeccion", null, ""), mapOf("ajuste" to 5.0, "corriente" to 5.1, "esperado" to 100.0, "tiempo" to 104.0))
        repo.transicion(tecnico, protocoloId, EstadoProtocolo.REGISTRADO_TERRENO, "")
        repo.cerrar(tecnico, visitaId, "Marcela Fuentes Rojas", "14.226.791-8", "Jefa de Mantenimiento")
        val host = InstrumentationRegistry.getArguments().getString("backendHost") ?: "127.0.0.1"
        fun api(u: UsuarioDemo, puerto: Int) = ServiciosRemotos.crear("http://$host:$puerto/",u)
        Sincronizador(db,api(tecnico,8081),api(tecnico,8082)).sincronizar(tecnico)
        assertTrue(repo.dao.pendientes(tecnico.id).isEmpty())
        assertEquals("REGISTRADO_TERRENO",api(tecnico,8082).snapshot().protocolos.first { it.id == protocoloId }.estado)
        repo.transicion(ingeniero, protocoloId, EstadoProtocolo.FORMALIZADO, "Revisión explícita del ingeniero")
        Sincronizador(db,api(ingeniero,8081),api(ingeniero,8082)).sincronizar(ingeniero)
        assertEquals("FORMALIZADO",api(ingeniero,8082).snapshot().protocolos.first { it.id == protocoloId }.estado)
        assertTrue(repo.cargar().visitas.first { it.id == visitaId }.acta.contains("PENDIENTE DE FORMALIZACIÓN"))
    }
}
