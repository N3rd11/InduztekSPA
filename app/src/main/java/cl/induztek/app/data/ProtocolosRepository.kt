package cl.induztek.app.data

import androidx.room.withTransaction
import cl.induztek.app.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

class ProtocolosRepository(private val db: InduztekDatabase) {
    val dao = db.mvpDao()
    private val gson = Gson()
    fun id(): String = UUID.randomUUID().toString()
    val cambios = dao.cambios().map { cargar() }
    suspend fun iniciar() = db.withTransaction { Semillas.cargar(dao) }

    suspend fun cargar(): EstadoLocal = db.withTransaction {
        EstadoLocal(dao.clientes(), dao.instalaciones(), dao.equipos(), dao.instrumentos(), dao.calibraciones(), dao.visitas(), dao.participantes(), dao.protocolos(), dao.pruebas(), dao.mediciones(), dao.historial())
    }

    private suspend fun encolar(u: UsuarioDemo, tipo: String, entidadId: String, payload: Any) {
        dao.encolar(PendienteSyncEntity(id(), u.id, tipo, entidadId, gson.toJson(payload), System.currentTimeMillis()))
    }
    private fun registro(u: UsuarioDemo) = require(Permisos.registrar(u)) { "No tienes permiso para registrar" }

    suspend fun cliente(u: UsuarioDemo, c: ClienteEntity) = db.withTransaction {
        registro(u)
        require(c.nombre.isNotBlank() && ReglasProtocolos.rutValido(c.rut)) { "Razón social y RUT válido son obligatorios" }
        val normalizado = c.copy(rut = c.rut.replace(".", "").replace("-", "").uppercase().let { it.dropLast(1) + "-" + it.last() })
        require(dao.clientes().none { it.id != c.id && it.rut.replace(".", "").replace("-", "") == normalizado.rut.replace("-", "") }) { "Ya existe un cliente con ese RUT" }
        dao.guardar(normalizado); encolar(u, "CLIENTE", c.id, normalizado)
    }

    suspend fun instalacion(u: UsuarioDemo, i: InstalacionEntity) = db.withTransaction {
        registro(u)
        require(i.nombre.isNotBlank() && dao.clientes().any { it.id == i.clienteId }) { "Selecciona cliente y nombre de instalación" }
        require(dao.instalaciones().none { it.id == i.id && it.clienteId != i.clienteId }) { "Una instalación existente no puede cambiar de empresa" }
        dao.guardar(i); encolar(u, "INSTALACION", i.id, i)
    }

    suspend fun equipo(u: UsuarioDemo, e: EquipoEntity) = db.withTransaction {
        registro(u)
        require(e.tag.isNotBlank() && dao.instalaciones().any { it.id == e.instalacionId }) { "Selecciona instalación y TAG" }
        TipoEquipo.valueOf(e.tipo)
        val normalizado = e.copy(tag = e.tag.trim().uppercase())
        require(dao.equipos().none { it.id != e.id && it.instalacionId == e.instalacionId && it.tag.equals(normalizado.tag, true) }) { "El TAG ya existe en esta instalación; utiliza el equipo registrado" }
        require(e.maxContactos == null || e.maxContactos.isFinite() && e.maxContactos > 0) { "Máximo de contactos inválido" }
        val previo = dao.equipos().find { it.id == e.id }
        require(previo == null || (previo.tipo == e.tipo && previo.instalacionId == e.instalacionId && previo.maxContactos == e.maxContactos && previo.placa == e.placa)) { "Un equipo existente conserva su instalación, tipo y referencias de placa" }
        dao.guardar(normalizado); encolar(u, "EQUIPO", e.id, normalizado)
    }

    suspend fun instrumento(u: UsuarioDemo, i: InstrumentoEntity, c: CalibracionEntity) = db.withTransaction {
        require(Permisos.instrumentos(u)) { "Solo el jefe técnico gestiona instrumentos" }
        require(i.nombre.isNotBlank() && i.serie.isNotBlank()) { "Nombre y serie obligatorios" }
        require(LocalDate.parse(c.fecha) <= LocalDate.parse(c.vencimiento)) { "La calibración no puede vencer antes de su emisión" }
        require(c.instrumentoId == i.id) { "Calibración inválida" }
        require(dao.instrumentos().none { it.id != i.id && it.serie == i.serie }) { "Número de serie duplicado" }
        dao.guardar(i); dao.guardar(c); encolar(u, "INSTRUMENTO", i.id, mapOf("instrumento" to i, "calibracion" to c))
    }

    suspend fun visita(u: UsuarioDemo, v: VisitaEntity, participantes: List<String>) = db.withTransaction {
        registro(u)
        require(v.ot.isNotBlank() && dao.instalaciones().any { it.id == v.instalacionId }) { "Selecciona instalación y número de OT" }
        LocalDate.parse(v.fecha)
        require(UsuariosDemo.todos.any { it.id == v.ingenieroId && it.rol == Rol.INGENIERO && it.activo }) { "Asigna un ingeniero a cargo" }
        require(v.tipo in setOf("SOLO_PRUEBAS", "PRUEBAS_Y_MANT")) { "Tipo de visita inválido" }
        require(participantes.isNotEmpty() && participantes.all { id -> UsuariosDemo.todos.any { it.id == id && it.rol in setOf(Rol.TECNICO, Rol.INGENIERO) } }) { "Selecciona participantes de terreno" }
        require(dao.visitas().none { it.id == v.id || it.ot == v.ot }) { "La OT ya está registrada" }
        val nueva = v.copy(creadorId = u.id, cierre = null, recepcion = null, acta = "")
        dao.guardar(nueva)
        participantes.distinct().forEach { dao.guardar(ParticipanteEntity(v.id, it)) }
        encolar(u, "VISITA", v.id, mapOf("visita" to nueva, "participantes" to participantes.distinct()))
    }

    suspend fun protocolo(u: UsuarioDemo, visitaId: String, equipoId: String, reemplaza: String? = null): String = db.withTransaction {
        registro(u)
        val s = cargar()
        val visita = s.visitas.first { it.id == visitaId }
        require(visita.cierre == null) { "La visita está cerrada" }
        require(s.equipos.any { it.id == equipoId && it.instalacionId == visita.instalacionId }) { "El equipo no pertenece a la instalación" }
        require(s.protocolos.none { it.visitaId == visitaId && it.equipoId == equipoId }) { "Este equipo ya tiene protocolo en la visita" }
        if (reemplaza != null) require(Permisos.revisar(u) && s.protocolos.any { it.id == reemplaza && it.equipoId == equipoId && it.estado == "FORMALIZADO" }) { "Solo se reemplaza un formalizado del mismo equipo" }
        val id = id()
        val p = ProtocoloEntity(id, "PT-${LocalDate.now().year}-$id", visitaId, equipoId, u.id, System.currentTimeMillis(), reemplazaId = reemplaza)
        val h = HistorialEstadoEntity(id(), id, null, p.estado, u.id, p.fecha, "Creación")
        dao.guardar(p); dao.guardar(h)
        encolar(u, "PROTOCOLO", id, mapOf("protocolo" to p, "historial" to h))
        id
    }

    private suspend fun editable(u: UsuarioDemo, id: String): ProtocoloEntity {
        val p = dao.protocolos().first { it.id == id }
        require(ReglasProtocolos.editable(u, p.autorId, EstadoProtocolo.valueOf(p.estado))) { "No puedes modificar este protocolo" }
        return p
    }

    suspend fun datos(u: UsuarioDemo, id: String, temperatura: Double, humedad: Double, mantenimiento: String, comentario: String) = db.withTransaction {
        val p = editable(u, id)
        require(temperatura.isFinite() && temperatura in -80.0..100.0 && humedad.isFinite() && humedad in 0.0..100.0) { "Temperatura (-80 a 100 °C) o humedad (0 a 100 %) inválida" }
        val nuevo = p.copy(temperatura = temperatura, humedad = humedad, mantenimiento = mantenimiento, comentario = comentario, revision = p.revision + 1)
        dao.guardar(nuevo); encolar(u, "DATOS_PROTOCOLO", id, mapOf("protocolo" to nuevo, "revisionEsperada" to p.revision))
    }

    suspend fun prueba(u: UsuarioDemo, entrada: PruebaEntity, valores: Map<String, Double>) = db.withTransaction {
        val p = editable(u, entrada.protocoloId)
        val s = cargar()
        val equipo = s.equipos.first { it.id == p.equipoId }
        val tipoEquipo = TipoEquipo.valueOf(equipo.tipo)
        val tipo = TipoPrueba.valueOf(entrada.tipo)
        require(tipo in ReglasProtocolos.tipos(tipoEquipo) && entrada.elemento in ReglasProtocolos.elementos(tipoEquipo, tipo)) { "Prueba o elemento no aplicable" }
        require(entrada.momento in setOf("UNICA", "PREVIA", "POSTERIOR")) { "Momento inválido" }
        if (entrada.momento != "UNICA") require(s.visitas.first { it.id == p.visitaId }.tipo == "PRUEBAS_Y_MANT") { "La visita no contempla mantenimiento" }
        val anterior = s.pruebas.find { it.protocoloId == p.id && it.tipo == entrada.tipo && it.elemento == entrada.elemento && it.momento == entrada.momento }
        require(s.pruebas.none { it.protocoloId == p.id && it.tipo == entrada.tipo && it.elemento == entrada.elemento && (it.momento == "UNICA") != (entrada.momento == "UNICA") }) { "No mezcles medición única con previa/posterior del mismo elemento" }
        val resultado = if (!entrada.realizada) {
            require(entrada.motivo.isNotBlank()) { "Indica el motivo de la prueba no realizada" }
            EvaluacionElectrica(null, "No realizada: ${entrada.motivo}")
        } else {
            val fechaPrueba = java.time.Instant.ofEpochMilli(p.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            require(s.instrumentos.any { it.id == entrada.instrumentoId }) { "Selecciona un instrumento" }
            require(s.calibraciones.any { it.instrumentoId == entrada.instrumentoId && LocalDate.parse(it.fecha) <= fechaPrueba && LocalDate.parse(it.vencimiento) >= fechaPrueba }) { "El instrumento no tiene calibración vigente a la fecha de la prueba" }
            ReglasProtocolos.evaluar(tipoEquipo, tipo, valores, equipo.maxContactos, entrada.opera)
        }
        val nueva = entrada.copy(id = anterior?.id ?: entrada.id, instrumentoId = if (entrada.realizada) entrada.instrumentoId else null, resultado = resultado.resultado?.name, detalle = resultado.detalle)
        val medidas = if (entrada.realizada) ReglasProtocolos.parametros(tipoEquipo, tipo).mapNotNull { param -> valores[param.codigo]?.let { MedicionEntity(nueva.id, param.codigo, it, param.unidad) } } else emptyList()
        dao.guardar(nueva); dao.reemplazarMediciones(nueva.id); medidas.forEach { dao.guardar(it) }
        dao.guardar(p.copy(revision = p.revision + 1))
        encolar(u, "PRUEBA", p.id, mapOf("prueba" to nueva, "mediciones" to medidas, "revisionEsperada" to p.revision))
    }

    suspend fun evaluarManual(u: UsuarioDemo, pruebaId: String, resultado: ResultadoPrueba, comentario: String) = db.withTransaction {
        require(Permisos.revisar(u) && comentario.isNotBlank()) { "Requiere ingeniero y justificación" }
        val s = cargar()
        val prueba = s.pruebas.first { it.id == pruebaId }
        val p = s.protocolos.first { it.id == prueba.protocoloId }
        require(p.estado in setOf("REGISTRADO_TERRENO", "BORRADOR", "OBSERVADO") && prueba.realizada) { "Protocolo bloqueado o prueba no realizada" }
        val equipo = s.equipos.first { it.id == p.equipoId }
        require(prueba.tipo == "DEVANADOS" || prueba.tipo == "AISLACION" && equipo.tipo in setOf("INTERRUPTOR", "CELDA_MT")) { "Esta prueba tiene evaluación automática" }
        val nueva = prueba.copy(resultado = resultado.name, comentario = comentario)
        dao.guardar(nueva); dao.guardar(p.copy(revision = p.revision + 1))
        encolar(u, "EVALUACION", p.id, mapOf("prueba" to nueva, "revisionEsperada" to p.revision))
    }

    suspend fun transicion(u: UsuarioDemo, id: String, estado: EstadoProtocolo, motivo: String) = db.withTransaction {
        val s = cargar()
        val p = s.protocolos.first { it.id == id }
        ReglasProtocolos.validarTransicion(u, p.autorId, EstadoProtocolo.valueOf(p.estado), estado, motivo)
        val pruebas = efectivas(s, p.id)
        if (estado == EstadoProtocolo.REGISTRADO_TERRENO) {
            require(p.temperatura != null && p.humedad != null) { "Guarda temperatura y humedad" }
            val equipo = TipoEquipo.valueOf(s.equipos.first { it.id == p.equipoId }.tipo)
            for (tipo in ReglasProtocolos.tipos(equipo)) for (elemento in ReglasProtocolos.elementos(equipo, tipo))
                require(pruebas.any { it.tipo == tipo.name && it.elemento == elemento }) { "Falta registrar o justificar ${tipo.titulo}: $elemento" }
            require(pruebas.any { it.realizada }) { "Se requiere al menos una prueba realizada" }
            if (s.visitas.first { it.id == p.visitaId }.tipo == "PRUEBAS_Y_MANT") require(p.mantenimiento.isNotBlank()) { "Describe el mantenimiento por equipo" }
        }
        if (estado == EstadoProtocolo.FORMALIZADO) {
            require(s.visitas.first { it.id == p.visitaId }.cierre != null) { "Primero cierra la visita y entrega el acta" }
            require(pruebas.any { it.realizada } && pruebas.filter { it.realizada }.all { it.resultado != null }) { "Faltan resultados manuales del ingeniero" }
            require(comparaciones(s, p).none { it.significativa } || motivo.isNotBlank()) { "Comenta la variación significativa antes de formalizar" }
        }
        val nuevo = p.copy(estado = estado.name, revision = p.revision + 1)
        val h = HistorialEstadoEntity(id(), id, p.estado, estado.name, u.id, System.currentTimeMillis(), motivo)
        dao.guardar(nuevo); dao.guardar(h)
        encolar(u, "ESTADO", id, mapOf("protocolo" to nuevo, "historial" to h, "revisionEsperada" to p.revision))
    }

    suspend fun cerrar(u: UsuarioDemo, id: String, nombre: String, rut: String, cargo: String) = db.withTransaction {
        require(Permisos.cerrarVisita(u)) { "Solo técnico o ingeniero cierra visitas" }
        val s = cargar()
        val v = s.visitas.first { it.id == id }
        require(v.cierre == null) { "La visita ya está cerrada" }
        require(nombre.isNotBlank() && cargo.isNotBlank() && ReglasProtocolos.rutValido(rut)) { "Nombre, cargo y RUT válido del receptor son obligatorios" }
        val ps = s.protocolos.filter { it.visitaId == id }
        require(ps.isNotEmpty() && ps.all { it.estado in setOf("REGISTRADO_TERRENO", "ANULADO") }) { "Todos los protocolos deben estar registrados en terreno o anulados" }
        val ahora = System.currentTimeMillis()
        val nueva = v.copy(cierre = ahora, receptor = nombre, receptorRut = rut, receptorCargo = cargo, recepcion = ahora)
        val cerrada = nueva.copy(acta = Actas.generar(s, nueva))
        dao.guardar(cerrada); encolar(u, "CIERRE", id, cerrada)
    }

    companion object {
        fun efectivas(s: EstadoLocal, id: String) = s.pruebas.filter { it.protocoloId == id }
            .groupBy { it.tipo to it.elemento }.values.map { grupo ->
                grupo.firstOrNull { it.momento == "POSTERIOR" && it.realizada }
                    ?: grupo.firstOrNull { it.momento == "UNICA" && it.realizada }
                    ?: grupo.firstOrNull { it.realizada }
                    ?: grupo.first()
            }

        fun global(s: EstadoLocal, id: String): String {
            val realizadas = efectivas(s, id).filter { it.realizada }
            if (realizadas.any { it.resultado == null }) return "Pendiente de evaluación"
            return ReglasProtocolos.global(realizadas.mapNotNull { it.resultado?.let(ResultadoPrueba::valueOf) })?.name ?: "Pendiente de evaluación"
        }

        fun comparaciones(s: EstadoLocal, p: ProtocoloEntity): List<Comparacion> {
            val anteriores = s.protocolos.filter { it.equipoId == p.equipoId && it.fecha < p.fecha && it.estado !in setOf("BORRADOR", "ANULADO") }.sortedByDescending { it.fecha }
            return efectivas(s, p.id).filter { it.realizada }.flatMap { actual ->
                val previa = anteriores.firstNotNullOfOrNull { a -> efectivas(s, a.id).find { it.tipo == actual.tipo && it.elemento == actual.elemento && it.realizada } }
                s.mediciones.filter { it.pruebaId == actual.id && it.codigo !in setOf("tension", "tap", "teorica", "ajuste", "esperado") }.map { m ->
                    val anterior = s.mediciones.find { it.pruebaId == previa?.id && it.codigo == m.codigo }
                    Comparacion("${actual.tipo} ${actual.elemento} · ${m.codigo}", m.valor, anterior?.valor, anterior?.let { ReglasProtocolos.variacion(m.valor, it.valor) }, m.unidad)
                }
            }
        }
    }
}

data class Comparacion(val etiqueta: String, val actual: Double, val anterior: Double?, val variacion: Double?, val unidad: String) {
    val significativa get() = variacion != null && abs(variacion) > 25
}
