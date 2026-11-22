package cl.induztek.app.data

import cl.induztek.app.model.ValidacionesFormulario
import cl.induztek.app.model.Rol
import cl.induztek.app.sync.AltaCliente
import cl.induztek.app.sync.CuentasApi
import cl.induztek.app.sync.EstadoCuenta

class CuentasRepository(private val api: CuentasApi) {
    suspend fun listar() = api.cuentas()

    suspend fun crear(alta: AltaCliente) = api.alta(alta.copy(
        nombre = alta.nombre.trim(), correo = alta.correo.trim().lowercase(),
        rut = alta.rut.trim(), clienteId = alta.clienteId.trim()
    ))

    suspend fun cambiarEstado(id: String, activo: Boolean, rol: Rol, empresa: String?) =
        api.estado(id, EstadoCuenta(activo, rol, empresa))

    fun validar(alta: AltaCliente): Map<String, String> {
        val errores = ValidacionesFormulario.cuenta(alta.nombre, alta.correo, alta.rut, alta.clave)
        return if (alta.rol == Rol.CLIENTE && alta.clienteId.isBlank()) errores + ("empresa" to "Selecciona una empresa") else errores
    }
}
