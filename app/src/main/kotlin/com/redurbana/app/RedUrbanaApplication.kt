package com.redurbana.app

import android.app.Application
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
class RedUrbanaApplication : Application()
