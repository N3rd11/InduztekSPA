
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
    val rol: Rol,
    val rut: String = "",
    val clienteId: String? = null,
    val activo: Boolean = true
)

object UsuariosDemo {

    val todos = listOf(
        UsuarioDemo(
            "tecnico1",
            "Matías Godoy Herrera",
            "mgodoy@induztek.ejemplo",
            Rol.TECNICO, "17.284.519-3"
        ),
        UsuarioDemo(
            "ingeniero1",
            "Andrea Contreras Silva",
            "acontreras@induztek.ejemplo",
            Rol.INGENIERO, "15.906.334-8"
        ),
        UsuarioDemo(
            "jefe1",
            "Ricardo Espinoza Lagos",
            "respinoza@induztek.ejemplo",
            Rol.JEFE_TECNICO, "13.871.905-7"
        ),
        UsuarioDemo(
            "cliente1",
            "Marcela Fuentes Rojas",
            "mfuentes@envpacifico.ejemplo",
            Rol.CLIENTE,
            "14.226.791-8", "cliente-pacifico"
        ),
        UsuarioDemo("tecnico2", "Javiera Tapia Muñoz", "jtapia@induztek.ejemplo", Rol.TECNICO, "19.412.087-7"),
        UsuarioDemo("tecnico3", "Carlos Rondón Pérez", "crondon@induztek.ejemplo", Rol.TECNICO, "26.104.583-4"),
        UsuarioDemo("tecnico4", "Diego Valenzuela Ríos", "dvalenzuela@induztek.ejemplo", Rol.TECNICO, "20.137.648-3"),
        UsuarioDemo("ingeniero2", "Sebastián Morales Gutiérrez", "smorales@induztek.ejemplo", Rol.INGENIERO, "16.553.270-8"),
        UsuarioDemo("cliente2", "Tomás Leiva Araya", "tleiva@losaromos.ejemplo", Rol.CLIENTE, "18.390.452-3", "cliente-clinica"),
        UsuarioDemo("cliente3", "Patricio Navarro Soto", "pnavarro@cerroalto.ejemplo", Rol.CLIENTE, "12.645.308-6", "cliente-minera")
    )

    fun validar(
        correo: String,
        clave: String
    ): UsuarioDemo? {

        if (clave != "1234") return null

        return todos.find {
            it.activo && it.correo.equals(
                correo.trim(),
                ignoreCase = true
            )
        }
    }
}
