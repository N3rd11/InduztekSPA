package cl.induztek.app

import cl.induztek.app.model.*
import org.junit.Assert.*
import org.junit.Test

class PermisosTest {
    @Test fun matrizDeLosCuatroRoles() {
        for (rol in Rol.entries) {
            val usuario = UsuarioDemo("id", "Prueba", "prueba@induztek.ejemplo", rol, clienteId = "a")
            assertEquals(rol != Rol.CLIENTE, Permisos.registrar(usuario))
            assertEquals(rol == Rol.INGENIERO || rol == Rol.JEFE_TECNICO, Permisos.revisar(usuario))
            assertEquals(rol == Rol.TECNICO || rol == Rol.INGENIERO, Permisos.cerrarVisita(usuario))
            assertEquals(rol == Rol.JEFE_TECNICO, Permisos.instrumentos(usuario))
            assertTrue(Permisos.empresa(usuario, "a"))
            assertEquals(rol != Rol.CLIENTE, Permisos.empresa(usuario, "b"))
        }
    }
}
