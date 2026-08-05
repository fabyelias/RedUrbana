package com.redurbana.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.redurbana.feature.map.TripArrivalNotifier
import dagger.hilt.android.HiltAndroidApp

/**
 * NOTA sobre LocationReporter (data-crowdsourcing): a propósito NO se arranca
 * acá. Reportar ubicación en background de forma sostenida requiere un
 * Foreground Service con notificación visible (restricción de Android desde
 * API 26+, y más estricta aún en versiones recientes) — arrancarlo desde
 * Application.onCreate() sin eso se mata apenas la app pasa a background, y
 * además sería engañoso para el usuario (ubicación corriendo sin indicación
 * visible). Queda como TODO explícito: crear un Foreground Service liviano
 * que llame a LocationReporter.start(), arrancado/detenido junto con el
 * toggle de "Colaborar con la comunidad" en Ajustes.
 */
@HiltAndroidApp
class RedUrbanaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createTripArrivalNotificationChannel()
    }

    /** Un canal solo se puede crear una vez por app — TripArrivalNotifier (feature-map) solo posta a él. */
    private fun createTripArrivalNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TripArrivalNotifier.CHANNEL_ID,
            "Aviso de llegada",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa cuando estás por llegar a la parada de bajada de tu viaje."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
