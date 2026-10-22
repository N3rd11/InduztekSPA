
package cl.induztek.app.model

enum class Rol {
    TECNICO,
    INGENIERO,
    JEFE_TECNICO,
    CLIENTE
}

data class UsuarioDemo(
    val id: String,
    val nombre: String,
    val correo: String,
    val rol: Rol
)

object UsuariosDemo {

    val todos = listOf(
        UsuarioDemo(
            "tecnico1",
            "Matías Godoy Herrera",
            "mgodoy@induztek.ejemplo",
            Rol.TECNICO
        ),
        UsuarioDemo(
            "ingeniero1",
            "Andrea Contreras Silva",
            "acontreras@induztek.ejemplo",
            Rol.INGENIERO
        ),
        UsuarioDemo(
            "jefe1",
            "Ricardo Espinoza Lagos",
            "respinoza@induztek.ejemplo",
            Rol.JEFE_TECNICO
        ),
        UsuarioDemo(
            "cliente1",
            "Marcela Fuentes Rojas",
            "mfuentes@envpacifico.ejemplo",
            Rol.CLIENTE
        )
    )

    fun validar(
        correo: String,
        clave: String
    ): UsuarioDemo? {

        if (clave != "1234") return null

        return todos.find {
            it.correo.equals(
                correo.trim(),
                ignoreCase = true
            )
        }
    }
}
