package cl.induztek.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Solo crea tablas nuevas. No modifica ni elimina registros_aislacion. */
object MigracionMvp : Migration(1, 2) {
    val sentencias = listOf(
        "CREATE TABLE IF NOT EXISTS clientes (id TEXT NOT NULL PRIMARY KEY, nombre TEXT NOT NULL, rut TEXT NOT NULL, giro TEXT NOT NULL, contacto TEXT NOT NULL, telefono TEXT NOT NULL, correo TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_clientes_rut ON clientes(rut)",
        "CREATE TABLE IF NOT EXISTS instalaciones (id TEXT NOT NULL PRIMARY KEY, clienteId TEXT NOT NULL, nombre TEXT NOT NULL, ubicacion TEXT NOT NULL, comuna TEXT NOT NULL, region TEXT NOT NULL, observaciones TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS equipos (id TEXT NOT NULL PRIMARY KEY, instalacionId TEXT NOT NULL, tag TEXT NOT NULL, tipo TEXT NOT NULL, marca TEXT NOT NULL, modelo TEXT NOT NULL, serie TEXT NOT NULL, anio TEXT NOT NULL, placa TEXT NOT NULL, maxContactos REAL, observaciones TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_equipos_instalacionId_tag ON equipos(instalacionId, tag)",
        "CREATE TABLE IF NOT EXISTS instrumentos (id TEXT NOT NULL PRIMARY KEY, nombre TEXT NOT NULL, marca TEXT NOT NULL, modelo TEXT NOT NULL, serie TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_instrumentos_serie ON instrumentos(serie)",
        "CREATE TABLE IF NOT EXISTS calibraciones (id TEXT NOT NULL PRIMARY KEY, instrumentoId TEXT NOT NULL, fecha TEXT NOT NULL, vencimiento TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS visitas (id TEXT NOT NULL PRIMARY KEY, instalacionId TEXT NOT NULL, ot TEXT NOT NULL, fecha TEXT NOT NULL, ingenieroId TEXT NOT NULL, tipo TEXT NOT NULL, creadorId TEXT NOT NULL, cierre INTEGER, receptor TEXT NOT NULL, receptorRut TEXT NOT NULL, receptorCargo TEXT NOT NULL, recepcion INTEGER, acta TEXT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_visitas_ot ON visitas(ot)",
        "CREATE TABLE IF NOT EXISTS participantes (visitaId TEXT NOT NULL, usuarioId TEXT NOT NULL, PRIMARY KEY(visitaId, usuarioId))",
        "CREATE TABLE IF NOT EXISTS protocolos (id TEXT NOT NULL PRIMARY KEY, numero TEXT NOT NULL, visitaId TEXT NOT NULL, equipoId TEXT NOT NULL, autorId TEXT NOT NULL, fecha INTEGER NOT NULL, temperatura REAL, humedad REAL, estado TEXT NOT NULL, mantenimiento TEXT NOT NULL, comentario TEXT NOT NULL, reemplazaId TEXT, revision INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_protocolos_numero ON protocolos(numero)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_protocolos_visitaId_equipoId ON protocolos(visitaId, equipoId)",
        "CREATE TABLE IF NOT EXISTS pruebas (id TEXT NOT NULL PRIMARY KEY, protocoloId TEXT NOT NULL, tipo TEXT NOT NULL, elemento TEXT NOT NULL, momento TEXT NOT NULL, realizada INTEGER NOT NULL, motivo TEXT NOT NULL, instrumentoId TEXT, resultado TEXT, detalle TEXT NOT NULL, comentario TEXT NOT NULL, opera INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_pruebas_protocoloId_tipo_elemento_momento ON pruebas(protocoloId,tipo,elemento,momento)",
        "CREATE TABLE IF NOT EXISTS mediciones (pruebaId TEXT NOT NULL, codigo TEXT NOT NULL, valor REAL NOT NULL, unidad TEXT NOT NULL, PRIMARY KEY(pruebaId,codigo))",
        "CREATE TABLE IF NOT EXISTS historial_estados (id TEXT NOT NULL PRIMARY KEY, protocoloId TEXT NOT NULL, anterior TEXT, nuevo TEXT NOT NULL, usuarioId TEXT NOT NULL, fecha INTEGER NOT NULL, motivo TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS pendientes_sync (id TEXT NOT NULL PRIMARY KEY, usuarioId TEXT NOT NULL, tipo TEXT NOT NULL, entidadId TEXT NOT NULL, payload TEXT NOT NULL, fecha INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS borradores_formulario (id TEXT NOT NULL PRIMARY KEY, contenido TEXT NOT NULL)"
    )
    override fun migrate(db: SupportSQLiteDatabase) { sentencias.forEach(db::execSQL) }
}
