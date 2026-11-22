package cl.induztek.app.data

import cl.induztek.app.model.UsuariosDemo
import java.text.DateFormat
import java.util.Date

object Actas {
    private const val pieActa = "Esta acta es preliminar y no sustituye al protocolo formalizado."

    fun paraEntrega(texto: String): String = texto.lineSequence().joinToString("\n") { linea ->
        if (linea == "Datos ficticios para uso académico. $pieActa") pieActa else linea
    }

    fun generar(s: EstadoLocal, visita: VisitaEntity, personal: Map<String, String> = emptyMap()): String = buildString {
        fun nombre(id: String) = personal[id] ?: UsuariosDemo.todos.find { it.id == id }?.nombre ?: id
        val instalacion = s.instalaciones.first { it.id == visita.instalacionId }
        val cliente = s.clientes.first { it.id == instalacion.clienteId }
        appendLine("INDUZTEK INGENIERÍA SpA")
        appendLine("ACTA DE PRUEBA EN TERRENO")
        appendLine("PENDIENTE DE FORMALIZACIÓN")
        appendLine("${visita.ot} · ${visita.fecha}")
        appendLine("Cliente: ${cliente.nombre} · RUT ${cliente.rut}")
        appendLine("Instalación: ${instalacion.nombre}")
        appendLine("Ingeniero a cargo: ${nombre(visita.ingenieroId)}")
        for (p in s.protocolos.filter { it.visitaId == visita.id && it.estado != "ANULADO" }) {
            val equipo = s.equipos.first { it.id == p.equipoId }
            appendLine("\nProtocolo: ${p.numero}\nEquipo: ${equipo.tag} · ${equipo.tipo} · ${equipo.marca} · Serie ${equipo.serie}")
            appendLine("Responsable: ${nombre(p.autorId)}")
            appendLine("Ambiente: ${p.temperatura} °C / ${p.humedad} % HR")
            appendLine("Resultado global: ${ProtocolosRepository.global(s, p.id)}")
            if (p.mantenimiento.isNotBlank()) appendLine("Mantenimiento: ${p.mantenimiento}")
            if (p.comentario.isNotBlank()) appendLine("Observaciones: ${p.comentario}")
            for (prueba in s.pruebas.filter { it.protocoloId == p.id }.sortedWith(compareBy({ it.tipo }, { it.elemento }, { it.momento }))) {
                appendLine("${prueba.tipo} · ${prueba.elemento} · ${prueba.momento}")
                if (!prueba.realizada) appendLine("No realizada: ${prueba.motivo}") else {
                    for (m in s.mediciones.filter { it.pruebaId == prueba.id }) appendLine("  ${m.codigo}: ${m.valor} ${m.unidad}")
                    val instrumento = s.instrumentos.find { it.id == prueba.instrumentoId }
                    appendLine("  Instrumento: ${instrumento?.nombre} · Serie ${instrumento?.serie}")
                    appendLine("  ${prueba.detalle} · ${prueba.resultado ?: "Pendiente de evaluación del ingeniero"}")
                }
            }
        }
        appendLine("\nRecibe: ${visita.receptor}\nRUT: ${visita.receptorRut}\nCargo: ${visita.receptorCargo}")
        appendLine("Recepción: ${visita.recepcion?.let { DateFormat.getDateTimeInstance().format(Date(it)) }}")
        appendLine(pieActa)
    }
}
