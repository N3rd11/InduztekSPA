package cl.induztek.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Conserva la ruta original. Solo accede a la imagen elegida y guarda una copia privada. */
class FotoPerfilRepository(private val contexto: Context) {
    private fun archivo(usuarioId: String) = AtomicFile(File(contexto.filesDir, "foto_$usuarioId.jpg"))

    suspend fun cargar(usuarioId: String): Bitmap? = withContext(Dispatchers.IO) {
        val foto = archivo(usuarioId)
        if (!foto.baseFile.exists() && !File(foto.baseFile.path + ".bak").exists()) null
        else foto.openRead().use { BitmapFactory.decodeStream(it) ?: error("La fotografía guardada no se puede leer") }
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
        val foto = archivo(usuarioId)
        val salida = foto.startWrite()
        try {
            check(imagen.compress(Bitmap.CompressFormat.JPEG, 85, salida)) { "No se pudo convertir la fotografía" }
            foto.finishWrite(salida)
        } catch (e: Exception) { foto.failWrite(salida); throw e }
        imagen
    }
}
