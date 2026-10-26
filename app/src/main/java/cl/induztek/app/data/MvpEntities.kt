package cl.induztek.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "clientes", indices = [Index(value = ["rut"], unique = true)])
data class ClienteEntity(@PrimaryKey val id: String, val nombre: String, val rut: String, val giro: String = "", val contacto: String = "", val telefono: String = "", val correo: String = "")

@Entity(tableName = "instalaciones")
data class InstalacionEntity(@PrimaryKey val id: String, val clienteId: String, val nombre: String, val ubicacion: String = "", val comuna: String = "", val region: String = "", val observaciones: String = "")

@Entity(tableName = "equipos", indices = [Index(value = ["instalacionId", "tag"], unique = true)])
data class EquipoEntity(@PrimaryKey val id: String, val instalacionId: String, val tag: String, val tipo: String, val marca: String = "", val modelo: String = "", val serie: String = "", val anio: String = "", val placa: String = "", val maxContactos: Double? = null, val observaciones: String = "")

@Entity(tableName = "instrumentos", indices = [Index(value = ["serie"], unique = true)])
data class InstrumentoEntity(@PrimaryKey val id: String, val nombre: String, val marca: String, val modelo: String, val serie: String)

@Entity(tableName = "calibraciones")
data class CalibracionEntity(@PrimaryKey val id: String, val instrumentoId: String, val fecha: String, val vencimiento: String)

@Entity(tableName = "visitas", indices = [Index(value = ["ot"], unique = true)])
data class VisitaEntity(@PrimaryKey val id: String, val instalacionId: String, val ot: String, val fecha: String, val ingenieroId: String, val tipo: String, val creadorId: String, val cierre: Long? = null, val receptor: String = "", val receptorRut: String = "", val receptorCargo: String = "", val recepcion: Long? = null, val acta: String = "")

@Entity(tableName = "participantes", primaryKeys = ["visitaId", "usuarioId"])
data class ParticipanteEntity(val visitaId: String, val usuarioId: String)

@Entity(tableName = "protocolos", indices = [Index(value = ["numero"], unique = true), Index(value = ["visitaId", "equipoId"], unique = true)])
data class ProtocoloEntity(@PrimaryKey val id: String, val numero: String, val visitaId: String, val equipoId: String, val autorId: String, val fecha: Long, val temperatura: Double? = null, val humedad: Double? = null, val estado: String = "BORRADOR", val mantenimiento: String = "", val comentario: String = "", val reemplazaId: String? = null, val revision: Long = 0)

@Entity(tableName = "pruebas", indices = [Index(value = ["protocoloId", "tipo", "elemento", "momento"], unique = true)])
data class PruebaEntity(@PrimaryKey val id: String, val protocoloId: String, val tipo: String, val elemento: String, val momento: String, val realizada: Boolean, val motivo: String, val instrumentoId: String?, val resultado: String?, val detalle: String, val comentario: String = "", val opera: Boolean = true)

@Entity(tableName = "mediciones", primaryKeys = ["pruebaId", "codigo"])
data class MedicionEntity(val pruebaId: String, val codigo: String, val valor: Double, val unidad: String)

@Entity(tableName = "historial_estados")
data class HistorialEstadoEntity(@PrimaryKey val id: String, val protocoloId: String, val anterior: String?, val nuevo: String, val usuarioId: String, val fecha: Long, val motivo: String)

/** Cada envío es inmutable, con ID idempotente y autor. Se elimina solo tras confirmación remota. */
@Entity(tableName = "pendientes_sync")
data class PendienteSyncEntity(@PrimaryKey val id: String, val usuarioId: String, val tipo: String, val entidadId: String, val payload: String, val fecha: Long)

@Entity(tableName = "borradores_formulario")
data class BorradorFormularioEntity(@PrimaryKey val id: String, val contenido: String)

data class EstadoLocal(
    val clientes: List<ClienteEntity> = emptyList(), val instalaciones: List<InstalacionEntity> = emptyList(),
    val equipos: List<EquipoEntity> = emptyList(), val instrumentos: List<InstrumentoEntity> = emptyList(),
    val calibraciones: List<CalibracionEntity> = emptyList(), val visitas: List<VisitaEntity> = emptyList(),
    val participantes: List<ParticipanteEntity> = emptyList(), val protocolos: List<ProtocoloEntity> = emptyList(),
    val pruebas: List<PruebaEntity> = emptyList(), val mediciones: List<MedicionEntity> = emptyList(),
    val historial: List<HistorialEstadoEntity> = emptyList()
) {
    fun clienteDeInstalacion(id: String) = instalaciones.find { it.id == id }?.clienteId
    fun clienteDeProtocolo(p: ProtocoloEntity) = visitas.find { it.id == p.visitaId }?.let { clienteDeInstalacion(it.instalacionId) }
}
