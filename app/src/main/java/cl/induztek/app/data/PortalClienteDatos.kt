package cl.induztek.app.data

import cl.induztek.app.model.EstadoProtocolo
import cl.induztek.app.model.TipoPrueba

enum class AvanceCliente(val titulo: String) {
    EN_CURSO("Visita en curso"),
    CERRADA("Visita cerrada"),
    EN_REVISION("En revisión técnica"),
    FORMALIZADA("Protocolos formalizados")
}

data class ProtocoloCliente(
    val numero: String,
    val equipo: String?,
    val estado: EstadoProtocolo,
    val pruebasRealizadas: List<String>,
    val resultado: String?
)

data class TrabajoCliente(
    val visita: VisitaEntity,
    val instalacion: String,
    val protocolos: List<ProtocoloCliente>
) {
    val tieneActa: Boolean get() = (visita.cierre ?: 0) > 0 && visita.acta.isNotBlank()
    val avance: AvanceCliente
        get() = when {
            (visita.cierre ?: 0) <= 0 -> AvanceCliente.EN_CURSO
            protocolos.isEmpty() -> AvanceCliente.CERRADA
            protocolos.all { it.estado == EstadoProtocolo.FORMALIZADO } -> AvanceCliente.FORMALIZADA
            else -> AvanceCliente.EN_REVISION
        }
}

data class EquipoCliente(val equipo: EquipoEntity, val instalacion: String)
data class PortalClienteDatos(val empresa: String?, val trabajos: List<TrabajoCliente>, val equipos: List<EquipoCliente>)

fun datosPortalCliente(clienteId: String, catalogo: EstadoLocal, protocolos: EstadoLocal): PortalClienteDatos {
    val instalaciones = catalogo.instalaciones.filter { it.clienteId == clienteId }.associateBy { it.id }
    val equipos = catalogo.equipos.filter { it.instalacionId in instalaciones }.associateBy { it.id }
    val visibles = protocolos.protocolos.filter {
        it.estado in setOf("REGISTRADO_TERRENO", "OBSERVADO", "FORMALIZADO")
    }.groupBy { it.visitaId }
    val trabajos = protocolos.visitas.filter { it.instalacionId in instalaciones }
        .sortedWith(compareByDescending<VisitaEntity> { it.fecha }.thenBy { it.ot })
        .map { visita ->
            val resumenes = visibles[visita.id].orEmpty().sortedBy { it.numero }.map { protocolo ->
                val estado = EstadoProtocolo.valueOf(protocolo.estado)
                val pruebas = ProtocolosRepository.efectivas(protocolos, protocolo.id)
                    .filter { it.realizada }
                    .map { prueba -> TipoPrueba.entries.find { it.name == prueba.tipo }?.titulo ?: prueba.tipo }
                    .distinct()
                ProtocoloCliente(protocolo.numero, equipos[protocolo.equipoId]?.tag, estado, pruebas,
                    if (estado == EstadoProtocolo.FORMALIZADO) ProtocolosRepository.global(protocolos, protocolo.id) else null)
            }
            TrabajoCliente(visita, instalaciones.getValue(visita.instalacionId).nombre, resumenes)
        }
    return PortalClienteDatos(
        catalogo.clientes.firstOrNull { it.id == clienteId }?.nombre,
        trabajos,
        equipos.values.sortedBy { it.tag }.map { EquipoCliente(it, instalaciones.getValue(it.instalacionId).nombre) }
    )
}
