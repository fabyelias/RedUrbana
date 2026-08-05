package com.redurbana.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificación real de Android (bandeja del sistema) de "bajate en la
 * próxima parada" — se dispara desde [LiveMapViewModel] mientras esa
 * pantalla sigue viva (sin Foreground Service todavía, ver
 * `RedUrbanaApplication`: no sobrevive si el proceso muere en background).
 *
 * El canal ([CHANNEL_ID]) se crea una sola vez en `RedUrbanaApplication`,
 * no acá — un canal solo se puede crear una vez por app y :app es quien
 * controla ese ciclo de vida.
 */
@Singleton
class TripArrivalNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyApproachingStop(stopName: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Estás llegando")
            .setContentText("Bajate en $stopName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "trip_arrival"
        private const val NOTIFICATION_ID = 1001
    }
}
