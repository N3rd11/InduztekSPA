package cl.induztek.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.induztek.app.data.*
import cl.induztek.app.model.*
import cl.induztek.app.sync.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TrabajoViewModel(
    application: Application,
    val usuario: UsuarioDemo,
    private val programarSync: (Application, String) -> Unit = ProgramarSync::ejecutar
) : AndroidViewModel(application) {
    private val mutex = Mutex()
    private val gson = Gson()
    private val directorio = application.getSharedPreferences("personal-terreno", Context.MODE_PRIVATE)
    var personal by mutableStateOf(
        if (usuario.rol == Rol.CLIENTE) emptyList() else runCatching {
            gson.fromJson(directorio.getString(usuario.id, null), Array<Persona>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    )
        private set
    val repo = ProtocolosRepository(InduztekDatabase.obtener(application)) { personal }
    var estado by mutableStateOf(EstadoLocal())
        private set
    var cargando by mutableStateOf(true)
        private set
    var ocupado by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val borradores = mutableMapOf<String, Map<String, String>>()
    private val formularios = mutableMapOf<String, FormularioEstado>()

    fun formulario(clave: String, inicial: Map<String, String>) = formularios.getOrPut(clave) {
        FormularioEstado(inicial + borrador(clave)) { guardarBorrador(clave, it) }
    }

    suspend fun actualizarPersonal() {
        if (usuario.rol == Rol.CLIENTE || ServiciosRemotos.cuentaEnMemoria(usuario.id) == null) return
        val config = ConfigSync(getApplication())
        try {
            val actualizado = ServiciosRemotos.cuentas(config.acceso, usuario).personal()
            personal = actualizado
            directorio.edit().putString(usuario.id, gson.toJson(actualizado)).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Se puede seguir usando el último directorio descargado durante un corte.
        }
    }

    fun nombrePersonal(id: String): String =
        personal.firstOrNull { it.id == id }?.nombre ?: UsuariosDemo.todos.firstOrNull { it.id == id }?.nombre ?: id

    init {
        viewModelScope.launch {
            try {
                repo.iniciar()
                val tipo = object : TypeToken<Map<String, String>>() {}.type
                repo.dao.borradores().filter { it.id.startsWith("${usuario.id}:") }.forEach {
                    borradores[it.id.substringAfter(':')] = gson.fromJson(it.contenido, tipo)
                }
                estado = filtrar(repo.cargar())
                cargando = false
                programarSync(application, usuario.id)
                repo.cambios.collect { estado = filtrar(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "No se pudo abrir el almacenamiento: ${e.message}"
                cargando = false
            }
        }
    }

    private fun filtrar(s: EstadoLocal): EstadoLocal {
        if (usuario.rol != Rol.CLIENTE) return s
        val instalaciones = s.instalaciones.filter { it.clienteId == usuario.clienteId }
        val visitas = s.visitas.filter { v -> instalaciones.any { it.id == v.instalacionId } }
        val protocolos = s.protocolos.filter { p -> visitas.any { it.id == p.visitaId } && ReglasProtocolos.visible(usuario, usuario.clienteId.orEmpty(), EstadoProtocolo.valueOf(p.estado)) }
        val pruebas = s.pruebas.filter { t -> protocolos.any { it.id == t.protocoloId } }
        return s.copy(clientes = s.clientes.filter { it.id == usuario.clienteId }, instalaciones = instalaciones,
            equipos = s.equipos.filter { e -> instalaciones.any { it.id == e.instalacionId } }, visitas = visitas,
            protocolos = protocolos, pruebas = pruebas, mediciones = s.mediciones.filter { m -> pruebas.any { it.id == m.pruebaId } },
            historial = s.historial.filter { h -> protocolos.any { it.id == h.protocoloId } }, participantes = s.participantes.filter { a -> visitas.any { it.id == a.visitaId } })
    }

    fun borrador(clave: String) = borradores[clave].orEmpty()

    fun validarCatalogo(tipo: String, id: String, f: FormularioEstado): Boolean {
        val requeridos = when(tipo) {
            "cliente" -> listOf("nombre", "rut")
            "instalacion" -> listOf("cliente", "nombre")
            "equipo" -> listOf("instalacion", "tag", "tipo") + if (f["tipo"] in setOf("INTERRUPTOR", "CELDA_MT")) listOf("maximo") else emptyList()
            else -> listOf("nombre", "serie", "fecha", "vence")
        }
        f.requerir(*requeridos.toTypedArray())
        fun rut(valor: String) = valor.replace(".", "").replace("-", "").trim().uppercase()
        if (tipo == "cliente" && estado.clientes.any { it.id != id && rut(it.rut) == rut(f["rut"]) }) f.error("rut", "Ya existe un cliente con este RUT")
        if (tipo == "equipo" && estado.equipos.any { it.id != id && it.instalacionId == f["instalacion"] && it.tag.equals(f["tag"].trim(), true) }) f.error("tag", "Este TAG ya existe en la instalación")
        if (tipo == "instrumento" && estado.instrumentos.any { it.id != id && it.serie == f["serie"] }) f.error("serie", "El número de serie ya está registrado")
        return f.errores.isEmpty()
    }

    fun validarVisita(f: FormularioEstado): Boolean {
        f.requerir("ot", "instalacion", "fecha", "ingeniero", "participantes")
        if (estado.visitas.any { it.ot == f["ot"] }) f.error("ot", "Esta orden de trabajo ya está registrada")
        return f.errores.isEmpty()
    }

    fun validarPrueba(f: FormularioEstado, campos: List<String>, fecha: Long): Boolean {
        f.requerir(*campos.toTypedArray())
        if (f["realizada"] == "true" && f["instrumento"].isNotBlank()) {
            val dia = Instant.ofEpochMilli(fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            if (estado.calibraciones.none { it.instrumentoId == f["instrumento"] && LocalDate.parse(it.fecha) <= dia && LocalDate.parse(it.vencimiento) >= dia })
                f.error("instrumento", "La calibración no está vigente a la fecha de la prueba")
        }
        return f.errores.isEmpty()
    }

    fun previsualizar(equipo: TipoEquipo, tipo: TipoPrueba, f: FormularioEstado, maximo: Double?): EvaluacionElectrica? {
        if (f["realizada"] == "false") return null
        return runCatching {
            val valores = ReglasProtocolos.parametros(equipo,tipo).mapNotNull { p ->
                f[p.codigo].takeIf { it.isNotBlank() }?.let { p.codigo to ReglasProtocolos.numero(it) * if (p.codigo.startsWith("lectura") && f["unidad"] == "GΩ") 1000 else 1 }
            }.toMap()
            ReglasProtocolos.evaluar(equipo, tipo, valores, maximo, f["opera"] != "false")
        }.getOrNull()
    }

    fun guardarBorrador(clave: String, valores: Map<String, String>) {
        borradores[clave] = valores
        if (valores.isEmpty()) formularios.remove(clave)
        viewModelScope.launch {
            mutex.withLock {
                try { repo.dao.guardar(BorradorFormularioEntity("${usuario.id}:$clave", gson.toJson(valores))) }
                catch (e: Exception) { if (e is CancellationException) throw e; error = "No se guardó el borrador: ${e.message}" }
            }
        }
    }

    fun accion(confirmacion: String, bloque: suspend ProtocolosRepository.() -> Unit) {
        if (ocupado) return
        ocupado = true
        mensaje = null; error = null
        viewModelScope.launch {
            try {
                mutex.withLock { repo.bloque(); estado = filtrar(repo.cargar()) }
                mensaje = confirmacion
                programarSync(getApplication(), usuario.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: "No se pudo completar la operación"
            } finally { ocupado = false }
        }
    }
}
