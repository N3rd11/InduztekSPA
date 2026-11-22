package cl.induztek.app

import cl.induztek.app.data.CuentasRepository
import cl.induztek.app.model.Rol
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.sync.*
import cl.induztek.app.viewmodel.CuentasViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CuentasTest {
    private val alta = AltaCliente(" Persona ficticia ", " TEST@induztek.ejemplo ", "12.345.678-5", "DemoClave9", "empresa")
    private val cuenta = UsuarioDemo("remota", "Persona ficticia", "test@induztek.ejemplo", Rol.CLIENTE, clienteId = "empresa")

    @Test fun validaNormalizaGuardaYCambiaEstado() = runBlocking {
        var enviada: AltaCliente? = null
        val api = object : CuentasApi {
            override suspend fun cuentas() = emptyList<UsuarioDemo>()
            override suspend fun personal() = emptyList<cl.induztek.app.model.Persona>()
            override suspend fun alta(alta: AltaCliente): UsuarioDemo { enviada = alta; return cuenta }
            override suspend fun estado(id: String, estado: EstadoCuenta): UsuarioDemo {
                assertEquals(cuenta.id, id)
                assertFalse(estado.activo)
                return cuenta.copy(activo = estado.activo, rol = estado.rol, clienteId = estado.clienteId)
            }
        }
        val repo = CuentasRepository(api)
        assertTrue(repo.validar(alta.copy(correo = "nombre:alias@induztek.ejemplo")).containsKey("correo"))
        val vm = CuentasViewModel(repo)
        vm.cargar()
        assertFalse(vm.crear(alta.copy(rut = "12.345.678-0", clave = "corta", clienteId = "")))
        assertEquals(setOf("rut", "clave", "empresa"), vm.errores.keys)
        assertNull(enviada)
        assertTrue(vm.crear(alta))
        assertEquals(alta.copy(nombre = "Persona ficticia", correo = "test@induztek.ejemplo"), enviada)
        assertEquals(cuenta, vm.cuentas.single())
        vm.cambiarEstado(cuenta)
        assertFalse(vm.cuentas.single().activo)
    }

    @Test fun duplicadoNoApareceComoCuentaGuardada() = runBlocking {
        val api = object : CuentasApi {
            override suspend fun cuentas() = emptyList<UsuarioDemo>()
            override suspend fun personal() = emptyList<cl.induztek.app.model.Persona>()
            override suspend fun alta(alta: AltaCliente): UsuarioDemo =
                throw HttpException(Response.error<Unit>(409, ResponseBody.create(null, "")))
            override suspend fun estado(id: String, estado: EstadoCuenta) = cuenta
        }
        val vm = CuentasViewModel(CuentasRepository(api))
        assertFalse(vm.crear(alta))
        assertTrue(vm.cuentas.isEmpty())
        assertTrue(vm.error.orEmpty().contains("Ya existe una cuenta"))
        assertFalse(vm.ocupado)
    }
}
