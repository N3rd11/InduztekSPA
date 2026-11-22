package cl.induztek.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import cl.induztek.app.data.*
import cl.induztek.app.model.*
import cl.induztek.app.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import retrofit2.HttpException

/** Ejecutar con -Pandroid.testInstrumentationRunnerArguments.backendIntegration=true y los cuatro servicios activos. */
@RequiereBackend
class RetrofitIntegrationTest {
    @Test fun exigeUnServicioDeActasDisponibleAntesDeAbrirSesion() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("backendIntegration") == "true")
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ConfigSync(contexto)
        val prefs = contexto.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
        val actas = prefs.getString("actas", null)
        val tecnico = UsuariosDemo.todos.first()
        ServiciosRemotos.cerrarSesion(tecnico.id)
        try {
            check(prefs.edit().putString("actas", "http://127.0.0.1:1/").commit())
            val fallo = runCatching { ServiciosRemotos.autenticar(config, tecnico.correo, "1234") }.exceptionOrNull()
            assertEquals("Actas", (fallo as? ServicioNoDisponible)?.servicio)
            assertNull(ServiciosRemotos.cuentaEnMemoria(tecnico.id))
        } finally {
            check(prefs.edit().putString("actas", actas).commit())
        }
    }

    @Test fun accesoSyncActaYFormalizacionConLosCuatroServicios() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("backendIntegration") == "true")
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(contexto, InduztekDatabase::class.java).build()
        val dbLector = Room.inMemoryDatabaseBuilder(contexto, InduztekDatabase::class.java).build()
        val config = ConfigSync(contexto)
        try {
            val repo = ProtocolosRepository(db); repo.iniciar()
            val tecnico = UsuariosDemo.todos.first { it.id == "tecnico1" }
            val ingeniero = UsuariosDemo.todos.first { it.id == "ingeniero1" }
            val visitaId = UUID.randomUUID().toString()
            repo.visita(tecnico, VisitaEntity(visitaId, "molienda", "OT-TEST-$visitaId", LocalDate.now().toString(), ingeniero.id, "SOLO_PRUEBAS", tecnico.id), listOf(tecnico.id))
            val protocoloId = repo.protocolo(tecnico, visitaId, "rel50")
            repo.datos(tecnico, protocoloId, 20.0, 50.0, "", "Prueba de integración ficticia")
            val prueba = PruebaEntity(UUID.randomUUID().toString(), protocoloId, "OPERACION_RELE", "50/51", "UNICA", true, "", "inyeccion", null, "")
            val valores = mapOf("ajuste" to 5.0, "corriente" to 5.1, "esperado" to 100.0, "tiempo" to 104.0)
            repo.prueba(tecnico, prueba, valores)
            assertEquals(tecnico.id, ServiciosRemotos.autenticar(config, tecnico.correo, "1234").id)
            val rechazo = runCatching { ServiciosRemotos.autenticar(config, tecnico.correo, "incorrecta") }.exceptionOrNull()
            assertEquals(401, (rechazo as? HttpException)?.code())
            fun api(u: UsuarioDemo, puerto: Int) = ServiciosRemotos.crear(if (puerto == 8081) config.catalogo else config.protocolos, u)
            val remoto = api(tecnico, 8082)
            val emisor = Sincronizador(db, api(tecnico, 8081), remoto)
            val receptor = Sincronizador(dbLector, api(tecnico, 8081), remoto)
            emisor.sincronizar(tecnico)
            receptor.sincronizar(tecnico)
            assertTrue(dbLector.mvpDao().pruebas().any { it.id == prueba.id })
            repo.prueba(tecnico, prueba.copy(comentario = "Medición corregida"), valores + ("tiempo" to 102.0))
            emisor.sincronizar(tecnico)
            assertEquals(102.0, remoto.snapshot().mediciones.first { it.pruebaId == prueba.id && it.codigo == "tiempo" }.valor, 0.0)
            repo.eliminarPrueba(tecnico, protocoloId, prueba.id)
            emisor.sincronizar(tecnico)
            receptor.sincronizar(tecnico)
            assertFalse(dbLector.mvpDao().pruebas().any { it.id == prueba.id })
            assertFalse(dbLector.mvpDao().mediciones().any { it.pruebaId == prueba.id })
            repo.prueba(tecnico, prueba.copy(id = UUID.randomUUID().toString()), valores)
            repo.transicion(tecnico, protocoloId, EstadoProtocolo.REGISTRADO_TERRENO, "")
            repo.cerrar(tecnico, visitaId, "Marcela Fuentes Rojas", "14.226.791-8", "Jefa de Mantenimiento")
            emisor.sincronizar(tecnico)
            assertTrue(repo.dao.pendientes(tecnico.id).isEmpty())
            val urlActas = config.actas
            val copia = ServiciosRemotos.actas(urlActas, tecnico).acta(visitaId)
            assertTrue(copia.esValida(visitaId))
            assertEquals(repo.cargar().visitas.first { it.id == visitaId }.acta, copia.texto)
            val clienteAjeno = UsuariosDemo.todos.first { it.id == "cliente1" }
            ServiciosRemotos.autenticar(config, clienteAjeno.correo, "1234")
            val denegado = runCatching { ServiciosRemotos.actas(urlActas, clienteAjeno).acta(visitaId) }.exceptionOrNull()
            assertEquals(404, (denegado as? HttpException)?.code())
            assertEquals("REGISTRADO_TERRENO",api(tecnico,8082).snapshot().protocolos.first { it.id == protocoloId }.estado)
            repo.transicion(ingeniero, protocoloId, EstadoProtocolo.FORMALIZADO, "Revisión explícita del ingeniero")
            ServiciosRemotos.autenticar(config, ingeniero.correo, "1234")
            Sincronizador(db,api(ingeniero,8081),api(ingeniero,8082)).sincronizar(ingeniero)
            assertEquals("FORMALIZADO",api(ingeniero,8082).snapshot().protocolos.first { it.id == protocoloId }.estado)
            assertTrue(repo.cargar().visitas.first { it.id == visitaId }.acta.contains("PENDIENTE DE FORMALIZACIÓN"))
        } finally {
            listOf("tecnico1", "ingeniero1", "cliente1").forEach(ServiciosRemotos::cerrarSesion)
            db.close()
            dbLector.close()
        }
    }
}
