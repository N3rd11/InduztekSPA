package cl.induztek.app.model

/** Matriz del Anexo 7. Se valida también al ejecutar cada operación. */
object Permisos {
    fun registrar(usuario: UsuarioDemo) = usuario.activo && usuario.rol != Rol.CLIENTE
    fun revisar(usuario: UsuarioDemo) = usuario.activo && usuario.rol in setOf(Rol.INGENIERO, Rol.JEFE_TECNICO)
    fun cerrarVisita(usuario: UsuarioDemo) = usuario.activo && usuario.rol in setOf(Rol.TECNICO, Rol.INGENIERO)
    fun instrumentos(usuario: UsuarioDemo) = usuario.activo && usuario.rol == Rol.JEFE_TECNICO
    fun empresa(usuario: UsuarioDemo, clienteId: String) = usuario.activo &&
        (usuario.rol != Rol.CLIENTE || usuario.clienteId == clienteId)
}
