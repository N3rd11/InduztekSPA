package cl.induztek.app.sync

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

fun detalleError(e: Exception): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "No se pudo validar tu cuenta en el servidor"
        403 -> "Tu perfil no tiene permiso para realizar esta operación"
        409 -> "Hay cambios en el servidor que requieren revisión antes de sincronizar"
        400, 422 -> "El servidor no pudo aceptar los datos. Revisa los registros pendientes"
        in 500..599 -> "El servidor no está disponible. Intenta nuevamente más tarde"
        else -> "No se pudo completar la sincronización con el servidor"
    }
    is SocketTimeoutException -> "El servidor tardó demasiado en responder. Intenta nuevamente"
    is IOException -> "No se pudo conectar con el servidor. Revisa tu conexión"
    is IllegalArgumentException -> "Revisa las direcciones en Configurar conexión"
    else -> "No se pudo completar la sincronización. Intenta nuevamente"
}
