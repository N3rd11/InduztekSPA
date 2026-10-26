package cl.induztek.app.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

enum class EstadoProtocolo(val titulo: String) {
    BORRADOR("Borrador"), REGISTRADO_TERRENO("Registrado en terreno"),
    OBSERVADO("Observado"), FORMALIZADO("Formalizado"), ANULADO("Anulado")
}

enum class TipoEquipo(val titulo: String, val clasificacion: String) {
    TRANSFORMADOR("Transformador", "PRIMARIO"), INTERRUPTOR("Interruptor", "PRIMARIO"),
    CELDA_MT("Celda MT", "PRIMARIO"), CABLE("Cable de poder", "PRIMARIO"),
    RELE("Relé de protección", "SECUNDARIO")
}

enum class TipoPrueba(val titulo: String) {
    AISLACION("Resistencia de aislación"), TTR("Relación de transformación"),
    DEVANADOS("Resistencia de devanados"), CONTACTOS("Resistencia de contactos"),
    OPERACION_RELE("Operación de relé")
}

data class Parametro(val codigo: String, val titulo: String, val unidad: String)
data class EvaluacionElectrica(val resultado: ResultadoPrueba?, val detalle: String)

object ReglasProtocolos {
    fun tipos(equipo: TipoEquipo): List<TipoPrueba> = when (equipo) {
        TipoEquipo.TRANSFORMADOR -> listOf(TipoPrueba.AISLACION, TipoPrueba.TTR, TipoPrueba.DEVANADOS)
        TipoEquipo.INTERRUPTOR, TipoEquipo.CELDA_MT -> listOf(TipoPrueba.AISLACION, TipoPrueba.CONTACTOS)
        TipoEquipo.CABLE -> listOf(TipoPrueba.AISLACION)
        TipoEquipo.RELE -> listOf(TipoPrueba.OPERACION_RELE)
    }

    fun elementos(equipo: TipoEquipo, prueba: TipoPrueba): List<String> = when (prueba) {
        TipoPrueba.AISLACION -> when (equipo) {
            TipoEquipo.TRANSFORMADOR -> listOf("AT-BT", "AT-T", "BT-T")
            else -> listOf("Fase U", "Fase V", "Fase W")
        }
        TipoPrueba.TTR -> listOf("H1-X1", "H2-X2", "H3-X3")
        TipoPrueba.DEVANADOS -> listOf("AT-U", "AT-V", "AT-W", "BT-U", "BT-V", "BT-W")
        TipoPrueba.CONTACTOS -> listOf("Polo 1", "Polo 2", "Polo 3")
        TipoPrueba.OPERACION_RELE -> listOf("50/51")
    }

    fun parametros(equipo: TipoEquipo, prueba: TipoPrueba): List<Parametro> = when (prueba) {
        TipoPrueba.AISLACION -> buildList {
            add(Parametro("tension", "Tensión de prueba", "kV"))
            add(Parametro("lectura1", "Lectura 1 minuto", "MΩ"))
            if (equipo in setOf(TipoEquipo.TRANSFORMADOR, TipoEquipo.CABLE))
                add(Parametro("lectura10", "Lectura 10 minutos", "MΩ"))
        }
        TipoPrueba.TTR -> listOf(Parametro("teorica", "Relación teórica", ""), Parametro("medida", "Relación medida", ""), Parametro("tap", "Posición del tap", ""))
        TipoPrueba.DEVANADOS -> listOf(Parametro("resistencia", "Resistencia", "mΩ"))
        TipoPrueba.CONTACTOS -> listOf(Parametro("resistencia", "Resistencia", "µΩ"))
        TipoPrueba.OPERACION_RELE -> listOf(Parametro("ajuste", "Corriente de ajuste", "A"), Parametro("corriente", "Corriente de operación", "A"), Parametro("esperado", "Tiempo esperado", "ms"), Parametro("tiempo", "Tiempo medido", "ms"))
    }

    fun numero(texto: String): Double {
        val valor = texto.trim().replace(',', '.').toDoubleOrNull()
        require(valor != null && valor.isFinite()) { "Ingresa un número finito válido" }
        return valor
    }

    fun evaluar(equipo: TipoEquipo, prueba: TipoPrueba, valores: Map<String, Double>, maxContactos: Double?, opera: Boolean = true): EvaluacionElectrica {
        require(prueba in tipos(equipo)) { "La prueba no corresponde al equipo" }
        if (prueba == TipoPrueba.OPERACION_RELE && !opera)
            return EvaluacionElectrica(ResultadoPrueba.RECHAZADO, "El relé no operó")
        for (p in parametros(equipo, prueba)) {
            val v = valores[p.codigo]
            require(v != null && v.isFinite() && v > 0) { "${p.titulo}: debe ser un número positivo" }
            if (p.codigo == "tap") require(v % 1.0 == 0.0) { "El tap debe ser entero" }
        }
        return when (prueba) {
            TipoPrueba.AISLACION -> if ("lectura10" in valores) {
                val e = ReglasAislacion.evaluar(valores.getValue("lectura1"), valores.getValue("lectura10"))
                EvaluacionElectrica(e.resultado, "IP: %.2f".format(e.indice))
            } else EvaluacionElectrica(null, "Sin criterio automático definido para aislación de celda/interruptor; requiere evaluación del ingeniero")
            TipoPrueba.TTR -> clasificar(abs(valores.getValue("medida") - valores.getValue("teorica")) / valores.getValue("teorica") * 100, 0.5, 1.0, "Error TTR")
            TipoPrueba.CONTACTOS -> {
                require(maxContactos != null && maxContactos.isFinite() && maxContactos > 0) { "Falta el máximo de contactos de placa (µΩ)" }
                clasificar(valores.getValue("resistencia") / maxContactos * 100, 100.0, 120.0, "Porcentaje del máximo de placa")
            }
            TipoPrueba.OPERACION_RELE -> clasificar(abs(valores.getValue("tiempo") - valores.getValue("esperado")) / valores.getValue("esperado") * 100, 5.0, 10.0, "Diferencia de tiempo")
            TipoPrueba.DEVANADOS -> EvaluacionElectrica(null, "Resultado manual por ingeniero (RN-08c)")
        }
    }

    private fun clasificar(valor: Double, aprobado: Double, observado: Double, etiqueta: String): EvaluacionElectrica {
        require(valor.isFinite()) { "Resultado fuera de rango" }
        return EvaluacionElectrica(when {
            valor <= aprobado + 1e-9 -> ResultadoPrueba.APROBADO
            valor <= observado + 1e-9 -> ResultadoPrueba.OBSERVADO
            else -> ResultadoPrueba.RECHAZADO
        }, "$etiqueta: %.2f %%".format(valor))
    }

    fun validarTransicion(usuario: UsuarioDemo, autor: String, desde: EstadoProtocolo, hacia: EstadoProtocolo, motivo: String) {
        require(usuario.activo && usuario.rol != Rol.CLIENTE) { "No tienes permiso" }
        require(desde != EstadoProtocolo.FORMALIZADO && desde != EstadoProtocolo.ANULADO) { "El protocolo está bloqueado" }
        when (hacia) {
            EstadoProtocolo.REGISTRADO_TERRENO -> {
                require(desde in setOf(EstadoProtocolo.BORRADOR, EstadoProtocolo.OBSERVADO)) { "Transición no permitida" }
                require(usuario.rol != Rol.TECNICO || usuario.id == autor) { "Solo puedes registrar tus protocolos" }
            }
            EstadoProtocolo.FORMALIZADO, EstadoProtocolo.OBSERVADO -> {
                require(Permisos.revisar(usuario) && desde == EstadoProtocolo.REGISTRADO_TERRENO) { "Se requiere revisión de ingeniero o jefe técnico" }
            }
            EstadoProtocolo.ANULADO -> require(Permisos.revisar(usuario)) { "Solo ingeniero o jefe técnico puede anular" }
            else -> error("Transición no permitida")
        }
        if (hacia in setOf(EstadoProtocolo.OBSERVADO, EstadoProtocolo.ANULADO))
            require(motivo.isNotBlank()) { "El motivo es obligatorio" }
    }

    fun editable(usuario: UsuarioDemo, autor: String, estado: EstadoProtocolo) =
        Permisos.registrar(usuario) && estado in setOf(EstadoProtocolo.BORRADOR, EstadoProtocolo.OBSERVADO) &&
            (usuario.rol != Rol.TECNICO || usuario.id == autor)

    fun visible(usuario: UsuarioDemo, clienteId: String, estado: EstadoProtocolo) =
        Permisos.empresa(usuario, clienteId) && (usuario.rol != Rol.CLIENTE || estado !in setOf(EstadoProtocolo.BORRADOR, EstadoProtocolo.ANULADO))

    fun variacion(actual: Double, anterior: Double): Double? =
        if (anterior == 0.0) null else ((actual - anterior) / abs(anterior) * 100).takeIf { it.isFinite() }

    fun global(resultados: List<ResultadoPrueba>): ResultadoPrueba? = resultados.maxByOrNull { it.ordinal }

    fun rutValido(texto: String): Boolean {
        val rut = texto.replace(".", "").replace("-", "").trim().uppercase()
        if (!rut.matches(Regex("[0-9]{7,8}[0-9K]"))) return false
        val cuerpo = rut.dropLast(1)
        if (cuerpo.all { it == '0' }) return false
        val suma = cuerpo.reversed().mapIndexed { i, c -> c.digitToInt() * (2 + i % 6) }.sum()
        val dv = when (val n = 11 - suma % 11) { 11 -> "0"; 10 -> "K"; else -> n.toString() }
        return rut.last().toString() == dv
    }

    fun atrasado(fecha: LocalDate, hoy: LocalDate): Boolean {
        var limite = fecha
        var dias = 0
        while (dias < 2) { limite = limite.plusDays(1); if (limite.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) dias++ }
        return hoy.isAfter(limite)
    }
}
