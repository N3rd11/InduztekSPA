package cl.induztek.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Conserva la copia privada y reintenta el envío si difiere de la última foto confirmada. */
class FotoPerfilRepository(private val contexto: Context) {
    private companion object {
        const val MAX_BYTES = 512 * 1024L
        val cambios = Mutex()
    }

    private val estado = contexto.getSharedPreferences("fotos_sincronizadas", Context.MODE_PRIVATE)
    private fun archivo(usuarioId: String) = AtomicFile(File(contexto.filesDir, "foto_$usuarioId.jpg"))

    suspend fun cargar(usuarioId: String): Bitmap? = withContext(Dispatchers.IO) {
        cambios.withLock { leer(usuarioId)?.let(::decodificar) }
    }

    suspend fun sincronizar(usuario: UsuarioDemo, descargar: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        cambios.withLock {
            val local = leer(usuario.id)
            val confirmada = estado.getString(usuario.id, null)
            val huellaLocal = local?.let(::huella)
            val api = ServiciosRemotos.fotos(ConfigSync(contexto).acceso, usuario)
            if (local != null && huellaLocal != confirmada) {
                api.guardar(RequestBody.create(MediaType.get("image/jpeg"), local))
                estado.edit().putString(usuario.id, huellaLocal).apply()
                return@withLock if (descargar) decodificar(local) else null
            }
            if (!descargar) return@withLock null
            val respuesta = api.foto()
            if (!respuesta.isSuccessful) throw HttpException(respuesta)
            val remota = respuesta.body()?.use { cuerpo ->
                val origen = cuerpo.source()
                if (origen.request(MAX_BYTES + 1)) throw IOException("La foto remota supera el tamaño permitido")
                origen.readByteArray()
            } ?: return@withLock local?.let(::decodificar)
            val imagen = decodificar(remota)
            if (!remota.contentEquals(local)) {
                escribir(usuario.id) { it.write(remota) }
                estado.edit().putString(usuario.id, huella(remota)).apply()
            }
            imagen
        }
    }

    suspend fun importar(usuarioId: String, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Selecciona una imagen desde la galería" }
        val dimensiones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val entrada = contexto.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la imagen")
        entrada.use { BitmapFactory.decodeStream(it, null, dimensiones) }
        require(dimensiones.outWidth > 0 && dimensiones.outHeight > 0) { "El archivo no es una imagen compatible" }
        var muestra = 1
        while (maxOf(dimensiones.outWidth, dimensiones.outHeight) / muestra > 1024) muestra *= 2
        val imagen = contexto.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: error("No se pudo leer la imagen")
        val orientacion = contexto.contentResolver.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val matriz = Matrix().apply {
            when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        guardar(usuarioId, Bitmap.createBitmap(imagen, 0, 0, imagen.width, imagen.height, matriz, true))
    }

    suspend fun guardar(usuarioId: String, original: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val escala = minOf(1f, 512f / maxOf(original.width, original.height))
        val imagen = if (escala < 1f) Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true) else original
        cambios.withLock {
            escribir(usuarioId) { salida ->
                check(imagen.compress(Bitmap.CompressFormat.JPEG, 85, salida)) { "No se pudo convertir la fotografía" }
            }
        }
        imagen
    }

    private fun leer(usuarioId: String): ByteArray? {
        val foto = archivo(usuarioId)
        if (!foto.baseFile.exists() && !File(foto.baseFile.path + ".bak").exists()) return null
        return foto.readFully()
    }

    private fun decodificar(jpeg: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: throw IOException("La fotografía guardada no se puede leer")

    private fun huella(jpeg: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun escribir(usuarioId: String, contenido: (java.io.FileOutputStream) -> Unit) {
        val foto = archivo(usuarioId)
        val salida = foto.startWrite()
        try {
            contenido(salida)
            foto.finishWrite(salida)
        } catch (e: Exception) { foto.failWrite(salida); throw e }
    }
}
