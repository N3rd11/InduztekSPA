package cl.induztek.app.model

import java.time.LocalDate

/** Reglas de entrada compartidas por los ViewModels; no dependen de Compose. */
object ValidacionesFormulario {
    private val positivos = setOf("lectura1", "lectura10", "tension", "teorica", "medida", "tap", "resistencia", "ajuste", "corriente", "esperado", "tiempo", "maximo")
    private val longitudes = mapOf("nombre" to 120, "rut" to 12, "correo" to 120, "telefono" to 20, "tag" to 30, "ot" to 20)

    fun correoValido(valor: String) = valor.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

    fun campo(clave: String, valor: String): String? {
        if (valor.isBlank()) return null
        val n = valor.trim().replace(',', '.').toDoubleOrNull()
        return when {
            longitudes[clave]?.let { valor.length > it } == true -> "Máximo ${longitudes.getValue(clave)} caracteres"
            clave == "rut" && !ReglasProtocolos.rutValido(valor) -> "RUT inválido; revisa el dígito verificador"
            clave == "correo" && !correoValido(valor) -> "Ingresa un correo válido, por ejemplo nombre@empresa.cl"
            clave == "telefono" && !valor.matches(Regex("[+0-9() \\-]{7,20}")) -> "Ingresa un teléfono de 7 a 20 caracteres"
            clave in setOf("fecha", "vence") && runCatching { LocalDate.parse(valor) }.isFailure -> "Usa una fecha válida con formato AAAA-MM-DD"
            clave == "anio" && (valor.toIntOrNull() == null || valor.toInt() !in 1900..LocalDate.now().year) -> "Año entre 1900 y ${LocalDate.now().year}"
            clave in positivos && (n == null || !n.isFinite() || n <= 0) -> "Ingresa un número mayor que cero"
            clave == "tap" && n != null && n % 1.0 != 0.0 -> "La posición del tap debe ser un entero"
            clave == "temperatura" && (n == null || !n.isFinite() || n !in -80.0..100.0) -> "Temperatura entre −80 y 100 °C"
            clave == "humedad" && (n == null || !n.isFinite() || n !in 0.0..100.0) -> "Humedad entre 0 y 100 %"
            else -> null
        }
    }

    fun validar(valores: Map<String, String>, requeridos: Set<String>): Map<String, String> = buildMap {
        requeridos.filter { valores[it].isNullOrBlank() }.forEach { put(it, "Campo obligatorio") }
        valores.forEach { (clave, valor) ->
            val medicionOmitida = clave in positivos && (valores["realizada"] == "false" || valores["opera"] == "false")
            if (!medicionOmitida) campo(clave, valor)?.let { put(clave, it) }
        }
        val fecha = runCatching { LocalDate.parse(valores["fecha"]) }.getOrNull()
        val vence = runCatching { LocalDate.parse(valores["vence"]) }.getOrNull()
        if (fecha != null && vence != null && vence < fecha) put("vence", "El vencimiento debe ser igual o posterior a la calibración")
    }

    fun login(correo: String, clave: String) = buildMap {
        if (correo.isBlank()) put("correo", "Ingresa tu correo")
        else if (!correoValido(correo)) put("correo", "Ingresa un correo válido")
        if (clave.isBlank()) put("clave", "Ingresa tu contraseña")
    }
}
