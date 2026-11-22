package cl.induztek.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cl.induztek.app.MainActivity
import cl.induztek.app.R

object AvisosSincronizacion {
    private const val CANAL = "sincronizacion"
    private const val AVISO = 1

    private fun preferencias(contexto: Context) =
        contexto.getSharedPreferences("avisos_sincronizacion", Context.MODE_PRIVATE)

    fun preferidos(contexto: Context) = preferencias(contexto).getBoolean("activados", false)

    fun configurar(contexto: Context, activo: Boolean) {
        preferencias(contexto).edit().putBoolean("activados", activo).apply()
        if (activo) crearCanal(contexto)
        else contexto.getSystemService(NotificationManager::class.java)?.cancel(AVISO)
    }

    private fun crearCanal(contexto: Context): NotificationManager? {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return null
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, "Sincronización", NotificationManager.IMPORTANCE_DEFAULT)
        )
        return gestor
    }

    fun completada(contexto: Context, cambios: Int, manual: Boolean = false) {
        if ((!manual && cambios == 0) || !preferidos(contexto)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val gestor = crearCanal(contexto) ?: return
        if (!gestor.areNotificationsEnabled()) return
        val abrir = PendingIntent.getActivity(
            contexto, 0, Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val detalle = when (cambios) {
            0 -> "Tus datos están al día con el servidor."
            1 -> "Se envió 1 cambio al servidor."
            else -> "Se enviaron $cambios cambios al servidor."
        }
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_aviso_sincronizacion)
            .setContentTitle("Sincronización completada")
            .setContentText(detalle)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            gestor.notify(AVISO, aviso)
        } catch (_: SecurityException) {
            // Android puede revocar el permiso entre la comprobación y la publicación.
        }
    }
}
