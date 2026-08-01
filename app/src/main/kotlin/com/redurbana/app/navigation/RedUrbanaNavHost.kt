package com.redurbana.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.redurbana.app.more.ComingSoonScreen
import com.redurbana.app.more.MoreMenuScreen
import com.redurbana.core.ui.components.BottomNavBar
import com.redurbana.core.ui.components.BottomNavItem
import com.redurbana.core.ui.navigation.AppRoute
import com.redurbana.feature.alerts.AlertsScreen
import com.redurbana.feature.dashboard.DashboardScreen
import com.redurbana.feature.map.LiveMapScreen
import com.redurbana.feature.settings.SettingsScreen
import com.redurbana.feature.stops.StopsScreen
import com.redurbana.feature.vehicledetail.VehicleDetailScreen

/**
 * Único NavHost de la app. Es el único archivo de todo el proyecto que
 * conoce simultáneamente a más de una feature — cada feature, por su lado,
 * solo conoce AppRoute (en core-ui) para poder navegar sin importar a las demás.
 */
@Composable
fun RedUrbanaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val selectedItem = when {
        backStackEntry?.destination?.hasRoute(AppRoute.LiveMap::class) == true -> BottomNavItem.MAP
        backStackEntry?.destination?.hasRoute(AppRoute.Lines::class) == true -> BottomNavItem.LINES
        backStackEntry?.destination?.hasRoute(AppRoute.More::class) == true -> BottomNavItem.MORE
        backStackEntry?.destination?.hasRoute(AppRoute.Stops::class) == true -> BottomNavItem.MORE
        backStackEntry?.destination?.hasRoute(AppRoute.Favorites::class) == true -> BottomNavItem.MORE
        backStackEntry?.destination?.hasRoute(AppRoute.Alerts::class) == true -> BottomNavItem.MORE
        backStackEntry?.destination?.hasRoute(AppRoute.History::class) == true -> BottomNavItem.MORE
        backStackEntry?.destination?.hasRoute(AppRoute.Settings::class) == true -> BottomNavItem.MORE
        else -> BottomNavItem.HOME
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selected = selectedItem,
                onItemSelected = { item ->
                    val route: AppRoute = when (item) {
                        BottomNavItem.HOME -> AppRoute.Dashboard
                        BottomNavItem.MAP -> AppRoute.LiveMap
                        BottomNavItem.LINES -> AppRoute.Lines
                        BottomNavItem.MORE -> AppRoute.More
                    }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCenterButtonClick = {
                    navController.navigate(AppRoute.LiveMap) { launchSingleTop = true }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Dashboard,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<AppRoute.Dashboard> {
                DashboardScreen()
            }

            composable<AppRoute.LiveMap> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LiveMapScreen(
                        onVehicleClick = { routeId, vehicleId ->
                            navController.navigate(AppRoute.VehicleDetail(routeId, vehicleId))
                        },
                    )
                }
            }

            composable<AppRoute.VehicleDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<AppRoute.VehicleDetail>()
                // La pantalla se muestra superpuesta al mapa (mismo look de
                // "tarjeta flotante" que la referencia); un ModalBottomSheet
                // real de Material3 se puede envolver acá sin tocar
                // VehicleDetailScreen en sí (recibe rutas, no UI de shell).
                Column(modifier = Modifier.fillMaxSize()) {
                    VehicleDetailScreen(
                        onFollowClick = { /* la cámara ya reacciona sola vía FollowedVehicleController */ },
                        onViewRouteClick = { /* TODO: navegar a detalle de línea completo */ },
                        onShareClick = { /* TODO: Intent nativo de compartir */ },
                    )
                }
            }

            composable<AppRoute.Lines> {
                // TODO: feature-lines (roadmap paso siguiente)
                ComingSoonScreen(title = "Líneas")
            }

            composable<AppRoute.More> {
                MoreMenuScreen(
                    onNavigateToStops = { navController.navigate(AppRoute.Stops) },
                    onNavigateToFavorites = { navController.navigate(AppRoute.Favorites) },
                    onNavigateToAlerts = { navController.navigate(AppRoute.Alerts) },
                    onNavigateToHistory = { navController.navigate(AppRoute.History) },
                    onNavigateToSettings = { navController.navigate(AppRoute.Settings) },
                )
            }

            composable<AppRoute.Stops> {
                StopsScreen()
            }

            composable<AppRoute.Favorites> {
                // TODO: feature-favorites (roadmap paso siguiente) — requiere domain-favorites + Room.
                ComingSoonScreen(title = "Favoritos")
            }

            composable<AppRoute.Alerts> {
                AlertsScreen()
            }

            composable<AppRoute.History> {
                // TODO: feature-history (roadmap paso siguiente).
                ComingSoonScreen(title = "Historial")
            }

            composable<AppRoute.Settings> {
                SettingsScreen()
            }
        }
    }
}
