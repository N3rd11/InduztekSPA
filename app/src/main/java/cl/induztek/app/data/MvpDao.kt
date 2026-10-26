package cl.induztek.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MvpDao {
    @Query("SELECT * FROM pendientes_sync ORDER BY fecha, rowid") suspend fun todosPendientes(): List<PendienteSyncEntity>
    @Query("SELECT * FROM borradores_formulario") suspend fun borradores(): List<BorradorFormularioEntity>
    @Upsert suspend fun guardar(valor: BorradorFormularioEntity)
    @Query("SELECT (SELECT count(*) FROM clientes)+(SELECT count(*) FROM instalaciones)+(SELECT count(*) FROM equipos)+(SELECT count(*) FROM instrumentos)+(SELECT count(*) FROM calibraciones)+(SELECT count(*) FROM visitas)+(SELECT count(*) FROM participantes)+(SELECT count(*) FROM protocolos)+(SELECT count(*) FROM pruebas)+(SELECT count(*) FROM mediciones)+(SELECT count(*) FROM historial_estados)")
    fun cambios(): Flow<Int>
    @Query("SELECT * FROM clientes ORDER BY nombre") suspend fun clientes(): List<ClienteEntity>
    @Query("SELECT * FROM instalaciones ORDER BY nombre") suspend fun instalaciones(): List<InstalacionEntity>
    @Query("SELECT * FROM equipos ORDER BY tag") suspend fun equipos(): List<EquipoEntity>
    @Query("SELECT * FROM instrumentos ORDER BY nombre") suspend fun instrumentos(): List<InstrumentoEntity>
    @Query("SELECT * FROM calibraciones ORDER BY fecha DESC") suspend fun calibraciones(): List<CalibracionEntity>
    @Query("SELECT * FROM visitas ORDER BY fecha DESC") suspend fun visitas(): List<VisitaEntity>
    @Query("SELECT * FROM participantes") suspend fun participantes(): List<ParticipanteEntity>
    @Query("SELECT * FROM protocolos ORDER BY fecha DESC") suspend fun protocolos(): List<ProtocoloEntity>
    @Query("SELECT * FROM pruebas") suspend fun pruebas(): List<PruebaEntity>
    @Query("SELECT * FROM mediciones") suspend fun mediciones(): List<MedicionEntity>
    @Query("SELECT * FROM historial_estados ORDER BY fecha") suspend fun historial(): List<HistorialEstadoEntity>
    @Query("SELECT * FROM pendientes_sync WHERE usuarioId = :usuarioId ORDER BY fecha, rowid") suspend fun pendientes(usuarioId: String): List<PendienteSyncEntity>
    @Query("DELETE FROM pendientes_sync WHERE id = :id") suspend fun confirmarEnvio(id: String)
    @Query("DELETE FROM mediciones WHERE pruebaId = :id") suspend fun reemplazarMediciones(id: String)
    @Upsert suspend fun guardar(valor: ClienteEntity)
    @Upsert suspend fun guardar(valor: InstalacionEntity)
    @Upsert suspend fun guardar(valor: EquipoEntity)
    @Upsert suspend fun guardar(valor: InstrumentoEntity)
    @Upsert suspend fun guardar(valor: CalibracionEntity)
    @Upsert suspend fun guardar(valor: VisitaEntity)
    @Upsert suspend fun guardar(valor: ParticipanteEntity)
    @Upsert suspend fun guardar(valor: ProtocoloEntity)
    @Upsert suspend fun guardar(valor: PruebaEntity)
    @Upsert suspend fun guardar(valor: MedicionEntity)
    @Upsert suspend fun guardar(valor: HistorialEstadoEntity)
    @Insert suspend fun encolar(valor: PendienteSyncEntity)
}
