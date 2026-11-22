package cl.induztek.app.sync

import androidx.room.withTransaction
import cl.induztek.app.data.*
import cl.induztek.app.model.UsuarioDemo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Confirmaciones individuales permiten reintentar después de un corte sin duplicar registros. */
class Sincronizador(private val db: InduztekDatabase, private val catalogo: SyncApi, private val protocolos: SyncApi) {
    companion object { private val mutex = Mutex() }
    suspend fun sincronizar(usuario: UsuarioDemo): Int = mutex.withLock {
        require(catalogo.sesion().get("id").asString == usuario.id) { "El servidor identificó una cuenta diferente" }
        val dao = db.mvpDao()
        var enviados = 0
        for (evento in dao.pendientes(usuario.id)) {
            val servicio = if (evento.tipo in setOf("CLIENTE", "INSTALACION", "EQUIPO", "INSTRUMENTO")) catalogo else protocolos
            val confirmacion = servicio.enviar(evento)
            require(confirmacion.id == evento.id) { "Confirmación remota inválida; el cambio se conserva localmente" }
            db.withTransaction { dao.confirmarEnvio(evento.id) }
            enviados++
        }
        val c = catalogo.snapshot()
        val p = protocolos.snapshot()
        incorporar(c.copy(visitas = p.visitas, participantes = p.participantes, protocolos = p.protocolos,
            pruebas = p.pruebas, mediciones = p.mediciones, historial = p.historial))
        enviados
    }

    private suspend fun incorporar(remoto: EstadoLocal) = db.withTransaction {
        val dao = db.mvpDao()
        val pendientes = dao.todosPendientes()
        val ids = pendientes.map { it.entidadId }.toSet()
        val protocolosPendientes = pendientes.filter {
            it.tipo in setOf("PROTOCOLO", "DATOS_PROTOCOLO", "PRUEBA", "ELIMINAR_PRUEBA", "EVALUACION", "ESTADO")
        }.map { it.entidadId }.toSet()
        remoto.clientes.filter { it.id !in ids }.forEach { dao.guardar(it) }
        remoto.instalaciones.filter { it.id !in ids }.forEach { dao.guardar(it) }
        remoto.equipos.filter { it.id !in ids }.forEach { dao.guardar(it) }
        remoto.instrumentos.filter { it.id !in ids }.forEach { dao.guardar(it) }
        remoto.calibraciones.filter { it.instrumentoId !in ids }.forEach { dao.guardar(it) }
        remoto.visitas.filter { it.id !in ids }.forEach { dao.guardar(it) }
        remoto.participantes.forEach { dao.guardar(it) }
        val actuales = dao.protocolos().associateBy { it.id }
        val adoptados = remoto.protocolos.filter { it.id !in protocolosPendientes && (actuales[it.id]?.revision ?: -1) <= it.revision }
        adoptados.forEach { dao.guardar(it) }
        val idsAdoptados = adoptados.map { it.id }.toSet()
        val pruebas = remoto.pruebas.filter { it.protocoloId in idsAdoptados }
        val idsPruebas = pruebas.map { it.id }.toSet()
        dao.pruebas().filter { it.protocoloId in idsAdoptados && it.id !in idsPruebas }.forEach {
            dao.reemplazarMediciones(it.id)
            dao.quitarPrueba(it.id)
        }
        pruebas.forEach { dao.guardar(it); dao.reemplazarMediciones(it.id) }
        remoto.mediciones.filter { it.pruebaId in idsPruebas }.forEach { dao.guardar(it) }
        remoto.historial.filter { it.protocoloId in idsAdoptados }.forEach { dao.guardar(it) }
    }
}
