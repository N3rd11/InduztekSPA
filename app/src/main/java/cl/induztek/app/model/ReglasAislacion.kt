package cl.induztek.app.model

enum class ResultadoPrueba {
    APROBADO,
    OBSERVADO,
    RECHAZADO
}

data class EvaluacionAislacion(
    val indice: Double,
    val resultado: ResultadoPrueba
)

object ReglasAislacion {

    fun evaluar(
        lectura1: Double,
        lectura10: Double
    ): EvaluacionAislacion {

        require(
            lectura1.isFinite() &&
                    lectura10.isFinite() &&
                    lectura1 > 0 &&
                    lectura10 > 0
        ) {
            "Las lecturas deben ser positivas"
        }

        val ip = lectura10 / lectura1
        require(ip.isFinite()) { "El índice calculado está fuera de rango" }

        val resultado = when {
            lectura1 < 100 || ip < 1.0 ->
                ResultadoPrueba.RECHAZADO

            ip < 2.0 ->
                ResultadoPrueba.OBSERVADO

            else ->
                ResultadoPrueba.APROBADO
        }

        return EvaluacionAislacion(
            indice = ip,
            resultado = resultado
        )
    }
}
