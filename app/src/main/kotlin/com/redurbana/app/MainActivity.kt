package com.redurbana.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.core.ui.theme.RedUrbanaTheme
import com.redurbana.app.navigation.RedUrbanaNavHost
import dagger.hilt.android.AndroidEntryPoint

private const val PERMISSION_PREFS_NAME = "redurbana_permissions"
private const val KEY_LOCATION_PERMISSION_ASKED = "location_permission_asked"

/**
 * Único punto de entrada de Compose. Toda la navegación vive en
 * RedUrbanaNavHost — MainActivity no conoce ninguna feature directamente.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RedUrbanaTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RedUrbanaColors.BackgroundPrimary),
                    color = RedUrbanaColors.BackgroundPrimary,
                ) {
                    RequestLocationPermissionOnce()
                    RedUrbanaNavHost()
                }
            }
        }
    }
}

/**
 * Pide ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION + (API 33+)
 * POST_NOTIFICATIONS una única vez en toda la vida de la instalación (el
 * flag en SharedPreferences se guarda apenas se dispara el pedido, sin
 * importar si el usuario acepta o rechaza). No bloquea el uso de la app:
 * sin el permiso de ubicación, el opt-in de "Colaborar con la comunidad" en
 * Ajustes simplemente no va a poder activarse; sin el de notificaciones,
 * el aviso de "bajate en la próxima parada" (TripArrivalNotifier) no se
 * muestra — el resto de la app funciona igual.
 */
@Composable
private fun RequestLocationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {},
    )
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PERMISSION_PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyAsked = prefs.getBoolean(KEY_LOCATION_PERMISSION_ASKED, false)
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyAsked && !alreadyGranted) {
            prefs.edit().putBoolean(KEY_LOCATION_PERMISSION_ASKED, true).apply()
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            launcher.launch(permissions.toTypedArray())
        }
    }
}
