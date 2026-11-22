package cl.induztek.app

import androidx.lifecycle.SavedStateHandle
import cl.induztek.app.model.UsuariosDemo
import cl.induztek.app.sync.ServicioNoDisponible
import cl.induztek.app.viewmodel.AccesoViewModel
import cl.induztek.app.viewmodel.FormularioEstado
import cl.induztek.app.viewmodel.SesionViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException

class ViewModelsTest {
    @Test fun accesoValidaYRecuperaSoloElCorreo() = runBlocking {
        val estado = SavedStateHandle()
        val acceso = AccesoViewModel(estado)
        assertNull(acceso.ingresar { _, _ -> error("No debe consultar con campos vacíos") })
        assertTrue(acceso.errores.containsKey("correo"))
        val usuario = UsuariosDemo.todos.first()
        acceso.correo(usuario.correo)
        acceso.clave("incorrecta")
        assertNull(acceso.ingresar { _, _ -> throw ConnectException() })
        assertEquals("Conéctate al servidor para iniciar sesión.", acceso.error)
        acceso.clave("PruebaInduztek")
        assertNull(acceso.error)
        assertEquals(usuario, acceso.ingresar { cuenta, clave ->
            assertEquals(usuario.correo, cuenta)
            assertEquals("PruebaInduztek", clave)
            usuario
        })
        assertEquals("", acceso.clave)
        val recreado = AccesoViewModel(estado)
        assertEquals(usuario.correo, recreado.correo)
        assertEquals("", recreado.clave)
    }

    @Test fun accesoExigeServidorInclusoParaUnPerfilInterno() = runBlocking {
        val usuario = UsuariosDemo.todos.first()
        val acceso = AccesoViewModel(SavedStateHandle())
        acceso.correo(usuario.correo)
        acceso.clave("1234")
        assertNull(acceso.ingresar { _, _ ->
            throw HttpException(Response.error<Unit>(401, ResponseBody.create(null, "")))
        })
        assertEquals("Correo o contraseña incorrectos.", acceso.error)
        assertNull(acceso.ingresar { _, _ -> null })
        assertEquals("No se pudo validar el perfil de esta cuenta.", acceso.error)
        assertNull(acceso.ingresar { _, _ -> throw ConnectException() })
        assertEquals("Conéctate al servidor para iniciar sesión.", acceso.error)
        assertNull(acceso.ingresar { _, _ -> throw ServicioNoDisponible("Actas", ConnectException()) })
        assertTrue(acceso.error.orEmpty().contains("Actas"))
        assertFalse(acceso.verificando)
    }

    @Test fun sesionSinCredencialRemotaEnMemoriaNoSeRestauraDesdeElIdentificador() {
        val estado = SavedStateHandle()
        val sesion = SesionViewModel(estado)
        assertNull(sesion.usuario)
        val usuario = UsuariosDemo.todos.first()
        assertThrows(IllegalArgumentException::class.java) { sesion.acceder(usuario.copy(activo = false)) }
        sesion.acceder(usuario)
        assertEquals(usuario, sesion.usuario)
        assertNull(SesionViewModel(estado).usuario)
        sesion.cerrar()
        assertNull(sesion.usuario)
        assertNull(SesionViewModel(estado).usuario)
    }

    @Test fun clienteNuevoSeAceptaDesdeServidorYSinRedNoAbreSesionNueva() = runBlocking {
        val vm = AccesoViewModel(SavedStateHandle())
        val cuenta = UsuariosDemo.todos.first { it.clienteId != null }
            .copy(id = "nueva", correo = "nueva@induztek.ejemplo")
        vm.correo(cuenta.correo)
        vm.clave("ClaveDemo9")
        assertEquals(cuenta, vm.ingresar { _, _ -> cuenta })
        vm.clave("ClaveDemo9")
        assertNull(vm.ingresar { _, _ -> throw ConnectException() })
        assertEquals("Conéctate al servidor para iniciar sesión.", vm.error)
    }

    @Test fun formularioPersisteCambiosYPresentaErroresDeCampo() {
        val guardados = mutableListOf<Map<String, String>>()
        val f = FormularioEstado(emptyMap()) { guardados += it }
        assertFalse(f.requerir("nombre"))
        assertEquals("", f["nombre"])
        f.cambiar("nombre", "Planta de prueba")
        assertTrue(f.requerir("nombre"))
        assertEquals(mapOf("nombre" to "Planta de prueba"), guardados.single())
        f.error("nombre", "Ya existe")
        assertEquals("Ya existe", f.errores["nombre"])
        f.cambiar("nombre", "Otra planta")
        assertTrue(f.errores.isEmpty())
    }
}
